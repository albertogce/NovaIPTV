package com.algoce95.novaiptv.data.model

import org.json.JSONObject

/** Paridad con los modelos de `lib/data/models` de Flutter (mismas claves de caché). */
data class LiveChannel(
    val channelId: Int,
    val channelName: String,
    val channelLogo: String,
    val groupTitle: String,
    val categoryId: String,
    val currentEpgTitle: String = "",
    val isFavorite: Boolean = false,
) {
    fun toCacheJson() = JSONObject()
        .put("stream_id", channelId)
        .put("name", channelName)
        .put("stream_icon", channelLogo)
        .put("category_name", groupTitle)
        .put("category_id", categoryId)
        .put("epg_title", currentEpgTitle)
        .put("is_favorite", isFavorite)
}

data class LiveCategory(
    val categoryId: String,
    val categoryName: String,
) {
    fun toCacheJson() = JSONObject()
        .put("category_id", categoryId)
        .put("category_name", categoryName)
}

data class VodMovie(
    val movieId: Int,
    val title: String,
    val logo: String,
    val category: String,
    val categoryId: String,
    val year: Int,
    val url: String,
) {
    fun toCacheJson() = JSONObject()
        .put("stream_id", movieId)
        .put("name", title)
        .put("stream_icon", logo)
        .put("category_name", category)
        .put("category_id", categoryId)
        .put("year", year)
        .put("url", url)
}

data class VodCategory(
    val categoryId: String,
    val categoryName: String,
) {
    fun toCacheJson() = JSONObject()
        .put("category_id", categoryId)
        .put("category_name", categoryName)
}

data class Series(
    val seriesId: Int,
    val title: String,
    val logo: String,
    val category: String,
    val categoryId: String,
    val year: Int,
) {
    fun toCacheJson() = JSONObject()
        .put("series_id", seriesId)
        .put("name", title)
        .put("cover", logo)
        .put("category_name", category)
        .put("category_id", categoryId)
        .put("year", year)
}

data class SeriesCategory(
    val categoryId: String,
    val categoryName: String,
) {
    fun toCacheJson() = JSONObject()
        .put("category_id", categoryId)
        .put("category_name", categoryName)
}

data class EpgProgram(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
) {
    fun isLive(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs in startMs until endMs

    fun progress(nowMs: Long = System.currentTimeMillis()): Double {
        if (!isLive(nowMs) || endMs <= startMs) return 0.0
        return ((nowMs - startMs).toDouble() / (endMs - startMs))
            .coerceIn(0.0, 1.0)
    }
}

data class WatchProgress(
    val id: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
    val episodeId: String? = null,
    val poster: String? = null,
) {
    val fraction: Double
        get() = if (durationMs <= 0) {
            0.0
        } else {
            (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        }

    fun toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("position", positionMs)
        .put("duration", durationMs)
        .put("updatedAt", java.time.Instant.ofEpochMilli(updatedAtMs).toString())
        .apply { if (episodeId != null) put("episodeId", episodeId) }
        .apply { if (!poster.isNullOrEmpty()) put("poster", poster) }
}
