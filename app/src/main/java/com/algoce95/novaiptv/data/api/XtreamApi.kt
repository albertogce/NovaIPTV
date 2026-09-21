package com.algoce95.novaiptv.data.api

import com.algoce95.novaiptv.core.utils.UrlNormalizer
import com.algoce95.novaiptv.data.model.EpgProgram
import com.algoce95.novaiptv.data.model.LiveCategory
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.data.model.SeriesCategory
import com.algoce95.novaiptv.data.model.StreamingParsers
import com.algoce95.novaiptv.data.model.VodCategory
import com.algoce95.novaiptv.data.model.VodMovie
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paridad con `lib/data/api/xtream_api_client.dart` de Flutter:
 * mismos endpoints, URLs de stream, tiempos de espera y mensajes de error.
 */
class XtreamRequestException(
    val operation: String,
    val kind: String,
    val statusCode: Int?,
    val detail: String,
) : Exception(detail) {
    override fun toString(): String {
        val status = if (statusCode == null) "" else " | HTTP $statusCode"
        return "No se pudo cargar \"$operation\"\n" +
            "Tipo: $kind$status\n" +
            "Detalle: $detail"
    }
}

internal interface XtreamService {
    @GET("player_api.php")
    suspend fun get(@QueryMap params: Map<String, String>): Response<String>

    /** Cuerpos grandes: se leen en streaming, nunca como String entero. */
    @Streaming
    @GET("player_api.php")
    suspend fun getStream(@QueryMap params: Map<String, String>): Response<ResponseBody>
}

class XtreamApiClient internal constructor(
    baseUrl: String,
    val username: String,
    val password: String,
    private val service: XtreamService,
    private val http: OkHttpClient,
) {
    val baseUrl: String = UrlNormalizer.normalizeUrl(baseUrl)

    /** Cierra conexiones pooled ociosas (una sola conexión de streaming a la vez). */
    fun evictIdleConnections() {
        http.connectionPool.evictAll()
    }

    /**
     * Libera por completo los recursos de red del cliente: hilos del dispatcher,
     * conexiones pooled y caché. Se usa al sustituirlo por otro (cambio de
     * credenciales) para que no queden conexiones abiertas más allá de la activa.
     */
    fun shutdown() {
        runCatching { http.dispatcher.executorService.shutdown() }
        runCatching { http.connectionPool.evictAll() }
        runCatching { http.cache?.close() }
    }

    companion object {
        private const val TAG = "NovaIPTV"
        private const val CONNECT_TIMEOUT_S = 20L
        private const val READ_TIMEOUT_S = 60L

        fun create(baseUrl: String, username: String, password: String): XtreamApiClient {
            val normalized = UrlNormalizer.normalizeUrl(baseUrl)
            val http = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("$normalized/")
                .client(http)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            return XtreamApiClient(
                baseUrl = normalized,
                username = username,
                password = password,
                service = retrofit.create(XtreamService::class.java),
                http = http,
            )
        }
    }

    /** URL de reproducción de un canal en directo (`.ts`). */
    fun liveStreamUrl(channelId: Int) = "$baseUrl/live/$username/$password/$channelId.ts"

    /** URL de reproducción de una película. [extension] viene de `get_vod_info`. */
    fun movieStreamUrl(movieId: Int, extension: String = "mp4") =
        "$baseUrl/movie/$username/$password/$movieId.$extension"

    /** URL de reproducción de un episodio de serie. */
    fun episodeStreamUrl(episodeId: Int, extension: String = "mp4") =
        "$baseUrl/series/$username/$password/$episodeId.$extension"

    // 1. Autenticación e Información del Servidor
    suspend fun getServerInfo(): JSONObject = getMap(
        "información del servidor",
        mapOf("username" to username, "password" to password),
    )

    // 2. Categorías de Canales en Vivo
    suspend fun getLiveCategories(): List<LiveCategory> = getList(
        "categorías de canales en vivo",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_live_categories",
        ),
        Parsers::parseLiveCategory,
    )

    // 2b. Canales en Vivo (listado completo)
    suspend fun getLiveStreams(
        categoryId: Int? = null,
        favoriteIds: Set<String> = emptySet(),
    ): List<LiveChannel> {
        val params = mutableMapOf(
            "username" to username,
            "password" to password,
            "action" to "get_live_streams",
        )
        if (categoryId != null) params["category_id"] = categoryId.toString()
        return getList("canales en vivo", params) { Parsers.parseLiveChannel(it, favoriteIds) }
    }

    // 3. Categorías de Películas (VOD)
    suspend fun getVodCategories(): List<VodCategory> = getList(
        "categorías de películas",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_vod_categories",
        ),
        Parsers::parseVodCategory,
    )

    // 3b. Lista de Películas (VOD)
    suspend fun getVodStreams(): List<VodMovie> = getList(
        "películas",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_vod_streams",
        ),
        Parsers::parseVodMovie,
    )

    // 3c. Ficha/Película Info
    suspend fun getVodInfo(vodId: Int): JSONObject = getMap(
        "información de película",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_vod_info",
            "vod_id" to vodId.toString(),
        ),
    )

    // 4. Categorías de Series
    suspend fun getSeriesCategories(): List<SeriesCategory> = getList(
        "categorías de series",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_series_categories",
        ),
        Parsers::parseSeriesCategory,
    )

    // 4b. Lista de Series
    suspend fun getSeries(): List<Series> = getList(
        "series",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_series",
        ),
        Parsers::parseSeries,
    )

    // 4c. Temporadas y Episodios de una Serie (puede ser grande: en streaming).
    suspend fun getSeriesInfo(seriesId: Int): JSONObject = withContext(Dispatchers.IO) {
        val params = mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_series_info",
            "series_id" to seriesId.toString(),
        )
        val response = try {
            service.getStream(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw transportError("información de serie", e)
        }
        if (!response.isSuccessful) {
            throw requestError("información de serie", response.code(), errorTextBlocking(response))
        }
        val body = response.body() ?: return@withContext JSONObject()
        try {
            body.byteStream().use { stream ->
                StreamingParsers.rebuildSeriesInfo(stream)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            JSONObject()
        } finally {
            try {
                body.close()
            } catch (_: Exception) {
            }
        }
    }

    // 4d. Corto EPG por canal
    suspend fun getShortEpg(streamId: Int): JSONObject = getMap(
        "guía EPG del canal",
        mapOf(
            "username" to username,
            "password" to password,
            "action" to "get_short_epg",
            "stream_id" to streamId.toString(),
        ),
    )

    /** EPG breve ya parseado (lo que usan las tarjetas y la guía). */
    suspend fun getShortEpgPrograms(streamId: Int): List<EpgProgram> {
        val data = getShortEpg(streamId)
        val raw = data.opt("epg_listings")
        if (raw !is JSONArray) return emptyList()
        return Parsers.objectList(raw).map(Parsers::parseEpgProgram)
    }

    private suspend fun getMap(
        operation: String,
        params: Map<String, String>,
    ): JSONObject {
        val body = request(operation, params)
        if (body == null) return JSONObject()
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private suspend fun <T> getList(
        operation: String,
        params: Map<String, String>,
        parse: (JSONObject) -> T,
    ): List<T> {
        // Todo (red + parseo en streaming) fuera del hilo principal: el cuerpo
        // se lee del socket a medida que se parsea.
        return withContext(Dispatchers.IO) {
            val response = try {
                service.getStream(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw transportError(operation, e)
            }
            if (!response.isSuccessful) {
                throw requestError(operation, response.code(), errorTextBlocking(response))
            }
            val body = response.body() ?: return@withContext emptyList()
            try {
                // Algunos paneles devuelven HTML con HTTP 200: detectarlo antes
                // de parsear para no tragarlo en silencio como lista vacía.
                val prefix = peekBodyPrefix(body)
                if (prefix != null) {
                    if (com.algoce95.novaiptv.BuildConfig.DEBUG) {
                        // Sin credenciales: solo forma y tamaño.
                        android.util.Log.d(
                            TAG,
                            "$operation: ${body.contentLength()} bytes, empieza por: " +
                                UrlNormalizer.sanitizeStreamUrl(prefix.take(300)),
                        )
                    }
                    if (prefix.trimStart().startsWith('<')) {
                        throw XtreamRequestException(
                            operation = operation,
                            kind = "respuesta HTTP no válida",
                            statusCode = response.code(),
                            detail = "El servidor devolvió una página de error. " +
                                "Inténtalo de nuevo más tarde.",
                        )
                    }
                }
                body.byteStream().use { stream ->
                    StreamingParsers.parseTopList(stream, parse)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamRequestException) {
                throw e
            } catch (e: EOFException) {
                // El servidor cortó la respuesta a mitad (panel sobrecargado?).
                throw XtreamRequestException(
                    operation = operation,
                    kind = "respuesta incompleta",
                    statusCode = response.code(),
                    detail = "El servidor cortó la respuesta. Inténtalo de nuevo más tarde.",
                )
            } catch (_: Exception) {
                // Cuerpo ilegible: sección vacía, como en Dart.
                emptyList()
            } finally {
                try {
                    body.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun errorTextBlocking(response: Response<*>): String? {
        return try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
    }

    /** Primeros bytes sin consumir el cuerpo (null si no se puede mirar). */
    private fun peekBodyPrefix(body: okhttp3.ResponseBody): String? {
        return try {
            val source = body.source()
            source.request(512)
            source.buffer.clone().readUtf8(512)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun request(
        operation: String,
        params: Map<String, String>,
    ): String? {
        val response = try {
            // La red nunca corre en el hilo principal.
            withContext(Dispatchers.IO) { service.get(params) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw transportError(operation, e)
        }
        if (!response.isSuccessful) {
            throw requestError(operation, response.code(), errorText(response))
        }
        return response.body()
    }

    private suspend fun errorText(response: Response<*>): String? {
        return try {
            withContext(Dispatchers.IO) { response.errorBody()?.string() }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestError(
        operation: String,
        statusCode: Int,
        errorBody: String?,
    ): XtreamRequestException = XtreamRequestException(
        operation = operation,
        kind = "respuesta HTTP no válida",
        statusCode = statusCode,
        detail = errorDetail(null, errorBody, null),
    )

    private fun transportError(operation: String, e: Exception): XtreamRequestException {
        val kind = when {
            e.message?.contains("canceled", ignoreCase = true) == true -> "petición cancelada"
            e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true ->
                "tiempo de conexión agotado"
            e is SocketTimeoutException -> "tiempo de recepción agotado"
            e is SSLException -> "certificado TLS no válido"
            e is UnknownHostException || e is ConnectException -> "error de conexión"
            e is java.io.IOException -> "error de red desconocido"
            else -> "error de red desconocido"
        }
        return XtreamRequestException(
            operation = operation,
            kind = kind,
            statusCode = null,
            detail = errorDetail(null, null, e.message),
        )
    }

    private fun errorDetail(data: Any?, text: String?, message: String?): String {
        if (data is Map<*, *>) {
            val msg = data["message"] ?: data["error"] ?: data["detail"]
            if (msg != null && msg.toString().trim().isNotEmpty()) {
                return UrlNormalizer.sanitizeStreamUrl(msg.toString())
            }
        }
        if (text != null && text.trim().isNotEmpty()) {
            val trimmed = text.trim()
            if (trimmed.startsWith('<')) {
                return "El servidor devolvió una página de error. " +
                    "Inténtalo de nuevo más tarde."
            }
            return UrlNormalizer.sanitizeStreamUrl(trimmed)
        }
        if (!message.isNullOrBlank()) {
            return UrlNormalizer.sanitizeStreamUrl(message)
        }
        return "El servidor no devolvió más información."
    }
}
