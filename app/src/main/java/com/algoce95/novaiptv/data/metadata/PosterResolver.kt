package com.algoce95.novaiptv.data.metadata

import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.presentation.tv.PosterClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Tipo de contenido para elegir el endpoint/entidad de búsqueda correctos. */
enum class PosterType { MOVIE, SERIES }

/**
 * Resuelve un póster de respaldo para el contenido que el panel IPTV devuelve
 * sin cartelera. Estrategia: si el usuario configuró una API key de TMDB se
 * intenta ahí primero (mejor cobertura/calidad) y, si no, se usa la búsqueda de
 * iTunes (sin necesidad de registro). Las peticiones van a estos servicios,
 * NUNCA al panel IPTV, y se cachean para no repetir consultas.
 */
object PosterResolver {
    private const val UA = "NovaIPTV/1.0"
    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

    // Caché en memoria de la sesión: url o "" (fallo conocido). Evita reconsultar.
    private val memory = ConcurrentHashMap<String, String>()

    // Deduplicación de peticiones simultáneas para la misma clave.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    /** Devuelve una URL de póster o null si no se encontró nada. */
    suspend fun resolve(rawTitle: String, type: PosterType): String? {
        val (clean, year) = cleanTitle(rawTitle)
        if (clean.isBlank()) return null
        val memKey = memKey(clean, year, type)

        memory[memKey]?.let { return it.ifBlank { null } }

        val prefsKey = prefsKey(memKey)
        val persisted = readPref(prefsKey)
        if (persisted != null) {
            memory[memKey] = persisted
            return persisted.ifBlank { null }
        }

        val deferred = CompletableDeferred<String?>()
        val existing = inFlight.putIfAbsent(memKey, deferred)
        if (existing != null) return existing.await()

        return try {
            val url = lookup(clean, year, type)
            memory[memKey] = url.orEmpty()
            if (url != null) persistHit(prefsKey, url)
            deferred.complete(url)
            url
        } catch (e: CancellationException) {
            deferred.cancel()
            throw e
        } catch (_: Exception) {
            memory[memKey] = ""
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(memKey)
        }
    }

    /** Vacía la caché (p. ej. tras añadir una key de TMDB para reintentar). */
    suspend fun clearCache() {
        memory.clear()
        val prefs = prefsOrNull() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val index = prefs.getStringList(PrefsStore.Keys.POSTER_FALLBACK_INDEX) ?: emptyList()
                index.forEach { prefs.remove(it) }
                prefs.remove(PrefsStore.Keys.POSTER_FALLBACK_INDEX)
            }
        }
    }

    private suspend fun lookup(clean: String, year: Int?, type: PosterType): String? {
        val tmdbKey = readPref(PrefsStore.Keys.TMDB_API_KEY)?.trim().orEmpty()
        if (tmdbKey.isNotEmpty()) {
            searchTmdb(clean, year, type, tmdbKey)?.let { return it }
        }
        return searchItunes(clean, year, type)
    }

    private suspend fun searchTmdb(
        clean: String,
        year: Int?,
        type: PosterType,
        apiKey: String,
    ): String? {
        val path = if (type == PosterType.MOVIE) "movie" else "tv"
        val sb = StringBuilder("https://api.themoviedb.org/3/search/")
            .append(path)
            .append("?api_key=").append(enc(apiKey))
            .append("&language=es-ES&include_adult=false&query=").append(enc(clean))
        if (year != null) {
            if (type == PosterType.MOVIE) {
                sb.append("&year=").append(year)
            } else {
                sb.append("&first_air_date_year=").append(year)
            }
        }
        val body = httpGet(sb.toString()) ?: return null
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val poster = o.optString("poster_path")
                if (poster.isNotEmpty() && poster != "null") {
                    return "$TMDB_IMAGE_BASE$poster"
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun searchItunes(clean: String, year: Int?, type: PosterType): String? {
        val entity = if (type == PosterType.MOVIE) "movie" else "tvShow"
        val media = if (type == PosterType.MOVIE) "movie" else "tvShow"
        val url = "https://itunes.apple.com/search?country=ES&media=$media&entity=$entity" +
            "&limit=6&term=" + enc(clean)
        val body = httpGet(url) ?: return null
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            var firstArt: String? = null
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val art = o.optString("artworkUrl100")
                if (art.isEmpty() || art == "null") continue
                val big = art.replace("100x100bb", "600x600bb")
                if (firstArt == null) firstArt = big
                if (year != null) {
                    val release = o.optString("releaseDate")
                    if (release.contains(year.toString())) return big
                }
            }
            firstArt
        }.getOrNull()
    }

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", UA).build()
            PosterClient.okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    // --- Limpieza de títulos -------------------------------------------------

    private val NOISE = Regex(
        "(?i)\\b(" +
            "2160p|1440p|1080p|720p|480p|4k|uhd|fullhd|fhd|hdrip|hdtv|hd|sd|" +
            "x264|x265|h264|h265|hevc|avc|av1|vp9|xvid|divx|" +
            "aac|ac3|eac3|dts|truehd|atmos|dd5[.\\s]?1|dd2[.\\s]?0|" +
            "10bit|8bit|hdr10\\+?|hdr|dovi|dv|" +
            "remux|bluray|blu[.\\s-]?ray|brrip|bdrip|webrip|web[.\\s-]?dl|web|dvdrip|dvd|" +
            "cam|ts|telesync|screener|scr|proper|repack|extended|unrated|imax|remastered|" +
            "multi|dual|subs|sub|vo|vose|vos|espanol|español|castellano|latino|dolby|nl|eng|esp" +
            ")\\b",
    )
    private val YEAR = Regex("\\b(19|20)\\d{2}\\b")
    private val BRACKETS = Regex("\\[[^\\]]*\\]|\\([^)]*\\)|\\{[^}]*\\}")
    private val SEPARATORS = Regex("[._|]+")
    private val TRIM_EDGES = Regex("^[\\s\\-–—:,.]+|[\\s\\-–—:,.]+$")

    /** Devuelve (título limpio, año?) a partir del nombre crudo del panel. */
    internal fun cleanTitle(raw: String): Pair<String, Int?> {
        var s = raw
        s = BRACKETS.replace(s, " ")
        s = SEPARATORS.replace(s, " ")

        var year: Int? = null
        val years = YEAR.findAll(s).toList()
        if (years.isNotEmpty()) {
            val match = years.last()
            val without = s.removeRange(match.range)
            // Solo quitar el año si queda un título con sentido (protege "2012").
            if (without.trim().length >= 2) {
                year = match.value.toIntOrNull()
                s = without
            }
        }

        s = NOISE.replace(s, " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        s = TRIM_EDGES.replace(s, "").replace(Regex("\\s+"), " ").trim()

        if (s.length < 2) {
            // El filtrado se comió el título: usar el original recortado.
            s = raw.trim()
        }
        return s to year
    }

    // --- Caché persistente ---------------------------------------------------

    private fun memKey(clean: String, year: Int?, type: PosterType): String =
        "${type.name}|${clean.lowercase()}|${year ?: ""}"

    private fun prefsKey(memKey: String): String =
        PrefsStore.Keys.POSTER_FALLBACK_PREFIX + Integer.toHexString(memKey.hashCode())

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun prefsOrNull(): PrefsStore? =
        runCatching { AppContainer.prefs }.getOrNull()

    private suspend fun readPref(key: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { prefsOrNull()?.getString(key) }.getOrNull()
        }

    private suspend fun persistHit(prefsKey: String, url: String) {
        val prefs = prefsOrNull() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                prefs.setString(prefsKey, url)
                val index = (prefs.getStringList(PrefsStore.Keys.POSTER_FALLBACK_INDEX) ?: emptyList())
                    .toMutableSet()
                if (index.add(prefsKey)) {
                    prefs.setStringList(PrefsStore.Keys.POSTER_FALLBACK_INDEX, index.toList())
                }
            }
        }
    }
}
