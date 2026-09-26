package com.algoce95.novaiptv.presentation.player

import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.data.api.XtreamApiClient
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.WatchProgress
import com.algoce95.novaiptv.presentation.home.pushHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reconstruye una sesión de reproducción a partir de un `WatchProgress` para
 * poder abrirlo desde fuera de las fichas (tarjeta "Seguir viendo" del inicio
 * de Android TV). Mismo juego de datos que `MovieDetailScreen` y
 * `SeriesDetailScreen`: URL resuelta con la extensión del panel y cola de
 * episodios ordenada por temporada. El propio reproductor ofrece continuar en
 * el punto guardado porque el `progressId` coincide.
 */
object ResumeLauncher {

    sealed interface Outcome {
        data class Play(val session: PlayerSession) : Outcome
        data object Unavailable : Outcome
    }

    suspend fun build(progressId: String): Outcome {
        val api = AppContainer.api ?: return Outcome.Unavailable.also {
            android.util.Log.w("NovaIPTV", "resume $progressId: sin sesión")
        }
        val progress = AppContainer.watchProgress.get(progressId) ?: return Outcome.Unavailable.also {
            android.util.Log.w("NovaIPTV", "resume $progressId: sin progreso guardado")
        }
        val session = runCatching {
            when {
                progressId.startsWith("movie:") -> movieSession(api, progress)
                progressId.startsWith("series:") -> seriesSession(api, progress)
                else -> null
            }
        }.onFailure {
            android.util.Log.w("NovaIPTV", "resume $progressId: $it")
        }.getOrNull()
        return session?.let { Outcome.Play(it) } ?: Outcome.Unavailable
    }

    private suspend fun movieSession(api: XtreamApiClient, progress: WatchProgress): PlayerSession? {
        val movieId = progress.id.substringAfter("movie:").toIntOrNull() ?: return null
        // Sin get_vod_info accesible se usa el mismo fallback mp4 que la ficha.
        val ext = runCatching {
            val info = api.getVodInfo(movieId)
            info.optJSONObject("movie_data")?.opt("container_extension")?.toString()
                ?: info.optJSONObject("info")?.opt("container_extension")?.toString()
                ?: "mp4"
        }.getOrDefault("mp4")
        return PlayerSession(
            items = listOf(QueueItem(api.movieStreamUrl(movieId, ext), progress.title)),
            index = 0,
            title = progress.title,
            progressId = progress.id,
            isLive = false,
            poster = progress.poster,
            onPlaybackStarted = {
                CoroutineScope(Dispatchers.IO).launch {
                    AppContainer.prefs.pushHistory(progress.id)
                }
            },
        )
    }

    private suspend fun seriesSession(api: XtreamApiClient, progress: WatchProgress): PlayerSession? {
        val seriesId = progress.id.substringAfter("series:").toIntOrNull() ?: return null
        val data = api.getSeriesInfo(seriesId)
        val raw = data.optJSONObject("episodes") ?: return null
        val bySeason = HashMap<String, List<JSONObject>>()
        for (key in raw.keys()) {
            (raw.opt(key) as? JSONArray)?.let { bySeason[key] = Parsers.objectList(it) }
        }
        val allEpisodes = Parsers.sortSeasonKeys(bySeason.keys.toList())
            .flatMap { bySeason[it] ?: emptyList() }
        fun episodeIdOf(ep: JSONObject): String =
            (ep.opt("id")?.toString() ?: ep.opt("stream_id")).toString()
        fun episodeTitleOf(ep: JSONObject): String {
            val raw = ep.opt("title")?.toString() ?: ep.opt("name")?.toString().orEmpty()
            if (raw.isNotBlank()) return raw
            val num = ep.opt("episode_num")?.toString()
                ?: ep.opt("episode_number")?.toString().orEmpty()
            return "Episodio $num"
        }
        val playable = allEpisodes.filter { episodeIdOf(it).toIntOrNull() != null }
        if (playable.isEmpty()) return null


        val stored = AppContainer.prefs
            .getString(PrefsStore.Keys.LAST_EPISODE_PREFIX + seriesId)
        val target = progress.episodeId ?: stored?.split("|")?.firstOrNull()
        var index = playable.indexOfFirst { episodeIdOf(it) == target }
        if (index < 0) index = 0
        // El progreso guarda el título como "Serie - Episodio"; se reutiliza el
        // prefijo para no depender del catálogo en caché.
        val seriesTitle = progress.title.substringBefore(" - ", progress.title)
        val queue = playable.map {
            val id = episodeIdOf(it)
            val ext = it.opt("container_extension")?.toString() ?: "mp4"
            QueueItem(
                streamUrl = api.episodeStreamUrl(id.toInt(), ext),
                title = "$seriesTitle - ${episodeTitleOf(it)}",
                episodeId = id,
            )
        }

        fun rememberWatched(episodeIndex: Int) {
            val ep = playable.getOrNull(episodeIndex) ?: return
            val epId = episodeIdOf(ep).toIntOrNull() ?: return
            CoroutineScope(Dispatchers.IO).launch {
                AppContainer.prefs.setString(
                    PrefsStore.Keys.LAST_EPISODE_PREFIX + seriesId,
                    "$epId|${episodeTitleOf(ep)}",
                )
                AppContainer.prefs.pushHistory(progress.id)
            }
        }

        return PlayerSession(
            items = queue,
            index = index.coerceIn(0, queue.lastIndex),
            title = queue[index.coerceIn(0, queue.lastIndex)].title,
            progressId = progress.id,
            isLive = false,
            poster = progress.poster,
            onPlaybackStarted = { item ->
                rememberWatched(playable.indexOfFirst { episodeIdOf(it) == item.episodeId })
            },
            onQueueIndexChanged = { rememberWatched(it) },
        )
    }
}
