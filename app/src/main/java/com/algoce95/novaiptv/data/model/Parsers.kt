package com.algoce95.novaiptv.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Base64

/**
 * Parseo defensivo réplica de las factorías `fromJson` de Dart:
 * ids que llegan como int o String, claves alternativas y valores nulos.
 */
object Parsers {
    private const val REPLACEMENT_CHAR = '\uFFFD'

    /** Bajo este largo, un token sin espacios se considera título en claro. */
    private const val MIN_BASE64_TITLE = 12

    fun parseId(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    private fun parseYear(value: Any?): Int = parseId(value)

    /** Primera clave presente (aunque valga "") convertida a String. */
    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.isNull(key)) return obj.opt(key)?.toString() ?: ""
        }
        return ""
    }

    private fun firstAny(obj: JSONObject, vararg keys: String): Any? {
        for (key in keys) {
            if (!obj.isNull(key)) return obj.opt(key)
        }
        return null
    }

    fun parseLiveChannel(obj: JSONObject, favoriteIds: Set<String> = emptySet()): LiveChannel {
        val id = parseId(firstAny(obj, "stream_id", "channel_id"))
        return LiveChannel(
            channelId = id,
            channelName = firstString(obj, "name", "channel_name"),
            channelLogo = firstString(obj, "stream_icon", "channel_logo", "logo", "icon"),
            groupTitle = firstString(obj, "category_name", "group_title"),
            categoryId = firstString(obj, "category_id"),
            currentEpgTitle = cleanEpgTitle(firstAny(obj, "epg_title", "title", "now_playing")),
            isFavorite = obj.opt("is_favorite") == true ||
                favoriteIds.contains(id.toString()),
        )
    }

    fun parseLiveCategory(obj: JSONObject) = LiveCategory(
        categoryId = firstString(obj, "category_id"),
        categoryName = firstString(obj, "category_name"),
    )

    fun parseVodMovie(obj: JSONObject, baseUrl: String? = null): VodMovie {
        var logo = firstString(obj, "stream_icon", "logo", "cover", "movie_image", "poster")
        
        // Fallback: Si el panel no da logo en el listado, intentamos la ruta estándar de Xtream
        if (logo.isEmpty() && baseUrl != null) {
            val id = parseId(firstAny(obj, "stream_id", "movie_id"))
            if (id > 0) {
                logo = "$baseUrl/screen/movie/$id.jpg"
            }
        }

        return VodMovie(
            movieId = parseId(firstAny(obj, "stream_id", "movie_id")),
            title = firstString(obj, "name", "title"),
            logo = logo,
            category = firstString(obj, "category_name", "category"),
            categoryId = firstString(obj, "category_id"),
            year = parseYear(firstAny(obj, "year")),
            url = firstString(obj, "url"),
        )
    }

    fun parseVodCategory(obj: JSONObject) = VodCategory(
        categoryId = firstString(obj, "category_id"),
        categoryName = firstString(obj, "category_name"),
    )

    fun parseSeries(obj: JSONObject) = Series(
        seriesId = parseId(firstAny(obj, "series_id")),
        title = firstString(obj, "name", "title"),
        logo = firstString(obj, "cover", "logo", "stream_icon", "series_image", "last_episode_icon"),
        category = firstString(obj, "category_name", "category"),
        categoryId = firstString(obj, "category_id"),
        year = parseYear(firstAny(obj, "year")),
    )

    fun parseSeriesCategory(obj: JSONObject) = SeriesCategory(
        categoryId = firstString(obj, "category_id"),
        categoryName = firstString(obj, "category_name"),
    )

    /**
     * Los paneles Xtream traen títulos y descripciones en base64, a veces con
     * saltos de línea (MIME) o sin el relleno `=`. El umbral de longitud protege
     * los títulos cortos sin espacios ("Lost", "Dark", "Seinfeld"), que son
     * secuencias base64 válidas y se corrompían al decodificarlas.
     */
    fun cleanEpgTitle(value: Any?): String {
        if (value == null || value === JSONObject.NULL) return ""
        val str = value.toString().trim()
        if (str.isEmpty()) return ""
        val token = str.filterNot { it.isWhitespace() }
        if (token.length < MIN_BASE64_TITLE || token.length != str.length && !str.contains('\n')) {
            return str
        }
        if (!token.all { it.isLetterOrDigit() || it in "+/=-_" }) return str
        val decoded = decodeBase64Tolerant(token) ?: return str
        // Se acepta si el resultado es texto imprimible; si sale basura
        // binaria, el original era un título legítimo. Los saltos de línea son
        // normales en las descripciones, así que no cuentan como control.
        if (decoded.isEmpty() || decoded.any {
                it == REPLACEMENT_CHAR || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t')
            }
        ) {
            return str
        }
        return decoded.whitespaceCollapsed()
    }

    /** Los párrafos del panel vienen con saltos: en una tarjeta se leen mejor en flujo. */
    private fun String.whitespaceCollapsed(): String = replace(Regex("\\s+"), " ").trim()

    /** Estándar → URL-safe, completando el relleno que falte. */
    private fun decodeBase64Tolerant(token: String): String? {
        val padded = token + "=".repeat((4 - token.length % 4) % 4)
        val body = padded.dropLast(padded.count { it == '=' })
            .padEnd(token.length + (4 - token.length % 4) % 4, 'A')
        return runCatching {
            String(Base64.getDecoder().decode(repad(body, padded)), Charsets.UTF_8).trim()
        }.recoverCatching {
            String(Base64.getUrlDecoder().decode(padded.replace('+', '-').replace('/', '_')), Charsets.UTF_8).trim()
        }.getOrNull()
    }

    private fun repad(body: String, padded: String): String = padded


    fun parseEpgProgram(obj: JSONObject) = EpgProgram(
        title = cleanEpgTitle(
            firstAny(obj, "title", "name") ?: "Sin título",
        ),
        description = cleanEpgTitle(firstAny(obj, "description", "desc")),
        startMs = parseEpgTime(firstAny(obj, "start", "start_timestamp", "start_time")),
        endMs = parseEpgTime(firstAny(obj, "end", "stop_timestamp", "stop_time")),
    )

    fun parseEpgTime(value: Any?): Long {
        fun ms(raw: Long) = if (raw < 100_000_000_000L) raw * 1000 else raw
        return when (value) {
            is Number -> ms(value.toLong())
            is String -> {
                val text = value.trim()
                text.toLongOrNull()?.let { return ms(it) }
                val iso = text.replaceFirst(' ', 'T')
                try {
                    LocalDateTime.parse(iso).atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (_: Exception) {
                    try {
                        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                }
            }
            else -> System.currentTimeMillis()
        }
    }

    /** Orden numérico de temporadas con resto alfabético (réplica de Dart). */
    fun sortSeasonKeys(keys: List<String>): List<String> = keys.sortedWith { a, b ->
        val numericA = a.toIntOrNull()
        val numericB = b.toIntOrNull()
        when {
            numericA != null && numericB != null -> numericA.compareTo(numericB)
            numericA != null -> -1
            numericB != null -> 1
            else -> a.compareTo(b)
        }
    }

    fun parseWatchProgress(obj: JSONObject): WatchProgress {
        val position = (obj.opt("position") as? Number)?.toLong() ?: 0L
        val duration = (obj.opt("duration") as? Number)?.toLong() ?: 0L
        val updatedAt = obj.opt("updatedAt")?.toString().orEmpty().let { text ->
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        return WatchProgress(
            id = obj.opt("id")?.toString() ?: "",
            title = obj.opt("title")?.toString() ?: "",
            positionMs = position,
            durationMs = duration,
            updatedAtMs = updatedAt,
            episodeId = obj.opt("episodeId")?.toString(),
            poster = obj.opt("poster")?.takeIf { it != JSONObject.NULL }?.toString(),
        )
    }

    /** JSONArray → lista de JSONObject (ignora elementos que no son objetos). */
    fun objectList(array: JSONArray): List<JSONObject> =
        (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }
}
