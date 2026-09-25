package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.presentation.tv.EmptyState
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.NovaType
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.presentation.tv.TvFocusShape
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.presentation.tv.requestFocusReady
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Paridad con `SeriesDetailScreen` de Flutter. */
@Composable
fun SeriesDetailScreen(series: Series, initialEpisodeId: String?, onPlay: () -> Unit) {
    var seriesData by remember(series.seriesId) { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember(series.seriesId) { mutableStateOf(true) }
    var selectedSeason by remember(series.seriesId) { mutableStateOf<String?>(null) }
    var lastEpisodeId by remember(series.seriesId) { mutableStateOf<Int?>(null) }
    var lastEpisodeTitle by remember(series.seriesId) { mutableStateOf<String?>(null) }
    var sessionEpisodeId by remember(series.seriesId) { mutableStateOf<String?>(null) }
    // Con la temporada ya elegida o el foco en los episodios, la cabecera
    // pasa a modo compacto para dar altura útil a los capítulos.
    var headerCompact by remember(series.seriesId) { mutableStateOf(false) }
    // El episodio visto se busca una sola vez: repetir la búsqueda cada vez
    // que la rejilla se recomponía devolvía el foco al capítulo ya visto.
    var pendingTargetFocus by remember(series.seriesId) { mutableStateOf(false) }

    val seasonFocus = remember { FocusRequester() }
    val firstEpisodeFocus = remember { FocusRequester() }
    val targetEpisodeFocus = remember { FocusRequester() }
    val episodeFocuses = remember { mutableStateMapOf<Int, FocusRequester>() }
    val scope = rememberCoroutineScope()

    fun episodeFocus(index: Int): FocusRequester =
        episodeFocuses.getOrPut(index) { FocusRequester() }

    fun targetEpisodeId(): String? =
        sessionEpisodeId ?: initialEpisodeId ?: lastEpisodeId?.toString()

    fun episodesMap(data: JSONObject?): Map<String, List<JSONObject>> {
        val raw = data?.optJSONObject("episodes") ?: return emptyMap()
        val map = mutableMapOf<String, List<JSONObject>>()
        for (key in raw.keys()) {
            (raw.opt(key) as? org.json.JSONArray)?.let { array ->
                map[key] = Parsers.objectList(array)
            }
        }
        return map
    }

    fun seasonForEpisode(episodes: Map<String, List<JSONObject>>, episodeId: String): String? {
        for ((season, list) in episodes) {
            for (item in list) {
                val id = item.opt("id")?.toString() ?: item.opt("stream_id")?.toString()
                if (id == episodeId) return season
            }
        }
        return null
    }

    // Listas memoizadas: una instancia nueva en cada recomposición reinicia los
    // efectos de scroll y la rejilla salta de vuelta al episodio visto, lo que
    // impedía enfocar o pulsar el resto de capítulos.
    val episodesBySeason = remember(seriesData) { episodesMap(seriesData) }
    val seasonKeys = remember(episodesBySeason) {
        Parsers.sortSeasonKeys(episodesBySeason.keys.toList())
    }
    val allEpisodes = remember(episodesBySeason, seasonKeys) {
        seasonKeys.flatMap { episodesBySeason[it] ?: emptyList() }
    }

    fun episodeIdOf(ep: JSONObject): String =
        (ep.opt("id")?.toString() ?: ep.opt("stream_id")).toString()

    fun episodeTitle(ep: JSONObject): String {
        val raw = ep.opt("title")?.toString() ?: ep.opt("name")?.toString().orEmpty()
        if (raw.isNotBlank()) return raw
        val num = ep.opt("episode_num")?.toString()
            ?: ep.opt("episode_number")?.toString().orEmpty()
        return "Episodio $num"
    }

    fun episodeStreamUrl(ep: JSONObject): String {
        val id = ep.opt("id")?.toString() ?: ep.opt("stream_id")?.toString().orEmpty()
        val ext = ep.opt("container_extension")?.toString() ?: "mp4"
        return requireNotNull(AppContainer.api).episodeStreamUrl(
            id.toIntOrNull() ?: 0,
            ext,
        )
    }

    // El reproductor llama a esto cuando el capítulo ya suena, con esta
    // composición fuera de pila: el `scope` del composable está cancelado, así
    // que la escritura va en su propio hilo.
    fun saveLastWatched(epId: Int, title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppContainer.prefs.setString("last_episode_${series.seriesId}", "$epId|$title")
            AppContainer.prefs.pushHistory("series:${series.seriesId}")
        }
        lastEpisodeId = epId
        lastEpisodeTitle = title
        sessionEpisodeId = epId.toString()
    }

    fun playEpisode(ep: JSONObject) {
        val epId = ep.opt("id")?.toString() ?: ep.opt("stream_id")?.toString()
        if (epId?.toIntOrNull() == null) return
        val epTitle = episodeTitle(ep)
        val playable = allEpisodes.filter {
            (it.opt("id")?.toString() ?: it.opt("stream_id")?.toString())?.toIntOrNull() != null
        }
        val queue = playable.map {
            val itemId = it.opt("id")?.toString() ?: it.opt("stream_id")?.toString().orEmpty()
            QueueItem(
                streamUrl = episodeStreamUrl(it),
                title = "${series.title} - ${episodeTitle(it)}",
                episodeId = itemId,
            )
        }
        val streamUrl = episodeStreamUrl(ep)
        var queueIndex = queue.indexOfFirst { it.streamUrl == streamUrl }
        if (queueIndex < 0) queueIndex = 0
        AppContainer.playerSession = PlayerSession(
            items = queue,
            index = queueIndex,
            title = "${series.title} - $epTitle",
            progressId = "series:${series.seriesId}",
            isLive = false,
            poster = series.logo,
            // Historial y "último visto" solo cuando el capítulo suena de
            // verdad: con un fallo de códec el episodio no debe quedar marcado.
            onPlaybackStarted = { item ->
                val started = playable.firstOrNull { episodeIdOf(it) == item.episodeId }
                val startedId = started?.let { episodeIdOf(it).toIntOrNull() }
                if (started != null && startedId != null) {
                    saveLastWatched(startedId, episodeTitle(started))
                }
            },
            onQueueIndexChanged = { index ->
                if (index in playable.indices) {
                    val changed = playable[index]
                    val changedId = (changed.opt("id")?.toString()
                        ?: changed.opt("stream_id")?.toString())?.toIntOrNull()
                    if (changedId != null) {
                        saveLastWatched(changedId, episodeTitle(changed))
                    }
                }
            },
        )
        onPlay()
    }

    LaunchedEffect(series.seriesId) {
        val stored = AppContainer.prefs.getString("last_episode_${series.seriesId}")
        if (stored != null) {
            val parts = stored.split("|")
            if (parts.size >= 2) {
                lastEpisodeId = parts[0].toIntOrNull()
                lastEpisodeTitle = parts.subList(1, parts.size).joinToString("|")
            }
        }
        try {
            val data = requireNotNull(AppContainer.api).getSeriesInfo(series.seriesId)
            seriesData = data
            val episodes = run {
                val raw = data.optJSONObject("episodes") ?: JSONObject()
                val m = mutableMapOf<String, List<JSONObject>>()
                for (key in raw.keys()) {
                    (raw.opt(key) as? org.json.JSONArray)?.let { array ->
                        m[key] = Parsers.objectList(array)
                    }
                }
                m
            }
            val sorted = Parsers.sortSeasonKeys(episodes.keys.toList())
            var selected = sorted.firstOrNull()
            val target = sessionEpisodeId ?: initialEpisodeId ?: lastEpisodeId?.toString()
            if (target != null) {
                selected = seasonForEpisode(episodes, target) ?: selected
            }
            selectedSeason = selected
            isLoading = false
            // El foco debe entrar en la rejilla de episodios: si no se pide
            // aquí, se lo queda la fila de temporadas y la navegación parece
            // bloqueada (el chip consume arriba/abajo sin a dónde ir).
            if (selected != null) pendingTargetFocus = true
        } catch (_: Exception) {
            isLoading = false
        }
    }

    val info = seriesData?.optJSONObject("info")
    val rawPlot = info?.opt("plot")?.toString() ?: info?.opt("description")?.toString()
    val plot = if (!rawPlot.isNullOrBlank()) rawPlot else "Sin descripción disponible"
    val seasons = seasonKeys
    val episodes = remember(episodesBySeason, selectedSeason) {
        episodesBySeason[selectedSeason] ?: emptyList()
    }
    val target = targetEpisodeId()
    val episodeColumns = if (LocalConfiguration.current.screenWidthDp >= 900) 2 else 1
    val gridState = rememberLazyGridState()
    val seasonRowState = rememberLazyListState()

    // Cambiar de temporada deja la rejilla donde estaba: hay que volver arriba
    // antes de pedir el foco, o el nodo del primer episodio no está compuesto.
    suspend fun focusFirstEpisode() {
        gridState.scrollToItem(0)
        firstEpisodeFocus.requestFocusReady()
    }

    suspend fun focusSeasonRow() {
        seasonRowState.scrollToItem(0)
        seasonFocus.requestFocusReady()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.ink) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SeriesHeader(
                    series = series,
                    plot = plot.toString(),
                    lastEpisodeTitle = lastEpisodeTitle,
                    compact = headerCompact,
                )
                if (seasons.isNotEmpty()) {
                    LazyRow(
                        state = seasonRowState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.panel)
                            .height(52.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { season ->
                            val selected = season == selectedSeason
                            val chipFocus = rememberTvFocus()
                            FilterChip(
                                selected = selected,
                                interactionSource = chipFocus.interaction,
                                onClick = {
                                    selectedSeason = season
                                    headerCompact = true
                                    scope.launch { focusFirstEpisode() }
                                },
                                label = { Text("Temporada $season") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.accent,
                                    containerColor = AppColors.focusFill,
                                ),
                                modifier = Modifier
                                    .tvFocusScale(chipFocus.focused, 1.04f)
                                    .then(
                                        if (seasons.indexOf(season) == 0) {
                                            Modifier.focusRequester(seasonFocus)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .onPreviewKeyEvent { event ->
                                        if (!event.isKeyDown()) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                                selectedSeason = season
                                                headerCompact = true
                                                scope.launch { focusFirstEpisode() }
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                headerCompact = true
                                                scope.launch { focusFirstEpisode() }
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Box(modifier = Modifier.weight(1f)) {
                    if (episodes.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Movie,
                            title = "No hay episodios disponibles",
                            hint = "Prueba con otra temporada.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(episodeColumns),
                            state = gridState,
                            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(episodes.size, key = { "ep-$selectedSeason-$it" }) { index ->
                                val ep = episodes[index]
                                val isTarget = target != null && episodeIdOf(ep) == target
                                EpisodeCard(
                                    episode = ep,
                                    episodeTitle = episodeTitle(ep),
                                    isLastWatched = isTarget,
                                    // El primero y el visto suelen coincidir: si
                                    // no se adjuntan los dos, el chip de
                                    // temporada se queda sin destino de foco.
                                    focusNodes = buildList {
                                        add(episodeFocus(index))
                                        if (isTarget) add(targetEpisodeFocus)
                                        if (index == 0) add(firstEpisodeFocus)
                                    },
                                    // La escala de foco agranda el ítem y sus
                                    // límites solapan al vecino, así que la
                                    // búsqueda lateral de Compose la descarta:
                                    // el movimiento izquierda/derecha se mueve
                                    // a mano.
                                    onArrowLeft = if (index % episodeColumns != 0) {
                                        { scope.launch { episodeFocus(index - 1).requestFocusReady() } }
                                    } else {
                                        null
                                    },
                                    onArrowRight =
                                    if (index % episodeColumns != episodeColumns - 1 &&
                                        index + 1 < episodes.size
                                    ) {
                                        { scope.launch { episodeFocus(index + 1).requestFocusReady() } }
                                    } else {
                                        null
                                    },
                                    onArrowUp = if (index < episodeColumns) {
                                        { scope.launch { focusSeasonRow() } }
                                    } else {
                                        null
                                    },
                                    onPlay = { playEpisode(ep) },
                                )
                            }
                        }
                        // Una sola pasada: desplazar hasta el visto en cada
                        // recomposición devolvía el foco y bloqueaba el resto.
                        LaunchedEffect(pendingTargetFocus, episodes) {
                            if (!pendingTargetFocus || episodes.isEmpty()) return@LaunchedEffect
                            val index = episodes.indexOfFirst { episodeIdOf(it) == target }
                            headerCompact = true
                            gridState.scrollToItem(if (index >= 0) index else 0)
                            if (index >= 0) {
                                targetEpisodeFocus.requestFocusReady(maxAttempts = 12)
                            } else {
                                firstEpisodeFocus.requestFocusReady(maxAttempts = 12)
                            }
                            pendingTargetFocus = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(series: Series, plot: String, lastEpisodeTitle: String?, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (compact) 8.dp else 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.seriesHeadStart, AppColors.panel),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (compact) 76.dp else 168.dp,
                    height = if (compact) 108.dp else 236.dp,
                )
                .clip(RoundedCornerShape(13.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = series.logo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = series.title,
                fallbackType = PosterType.SERIES,
                error = {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = AppColors.amber, modifier = Modifier.size(48.dp))
                },
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = series.title,
                color = Color.White,
                style = if (compact) NovaType.title else NovaType.display,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (series.year > 0) {
                        SeriesMetaChip(icon = Icons.Filled.CalendarToday, label = series.year.toString())
                    }
                    if (series.category.isNotEmpty()) {
                        SeriesMetaChip(icon = Icons.Filled.LocalOffer, label = series.category)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (lastEpisodeTitle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.accent.copy(alpha = 0.15f))
                        .border(1.dp, AppColors.accent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Último visto: $lastEpisodeTitle",
                        color = Color.White,
                        style = NovaType.meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!compact) {
                Text(
                    text = plot,
                    style = NovaType.body,
                    color = bodyTextColor(),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeriesMetaChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.mint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text = label, color = bodyTextColor(), fontSize = 11.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun EpisodeCard(
    episode: JSONObject,
    episodeTitle: String,
    isLastWatched: Boolean,
    focusNodes: List<FocusRequester>,
    onArrowLeft: (() -> Unit)?,
    onArrowRight: (() -> Unit)?,
    onArrowUp: (() -> Unit)?,
    onPlay: () -> Unit,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val info = episode.optJSONObject("info")
    val epNum = (episode.opt("episode_num")?.toString()
        ?: episode.opt("episode_number")?.toString()).orEmpty()
    val plot = info?.opt("plot")?.toString() ?: info?.opt("description")?.toString().orEmpty()
    val thumbnail = info?.opt("movie_image")?.toString()
        ?: info?.opt("thumbnail")?.toString()
        ?: info?.opt("cover_big")?.toString()
        ?: episode.opt("movie_image")?.toString().orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(TvFocusShape)
            .background(if (focused) AppColors.episodeFocus else AppColors.panel)
            .border(
                width = if (focused || isLastWatched) 2.dp else 1.dp,
                color = when {
                    focused -> AppColors.accent
                    isLastWatched -> AppColors.amber
                    else -> Color.White.copy(alpha = 0.12f)
                },
                shape = TvFocusShape,
            )
            .then(focusNodes.fold<FocusRequester, Modifier>(Modifier) { m, node ->
                m.focusRequester(node)
            })
            .focusable(interactionSource = focus.interaction)
            .tvPress(onTap = onPlay)
            .onPreviewKeyEvent { event ->
                if (!event.isKeyDown()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> onArrowLeft?.let { it(); true } ?: false
                    Key.DirectionRight -> onArrowRight?.let { it(); true } ?: false
                    Key.DirectionUp -> onArrowUp?.let { it(); true } ?: false
                    else -> false
                }
            }
            .tvFocusScale(focused),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(184.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                .background(AppColors.thumbPlaceholder),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = thumbnail,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.size(28.dp),
                        )
                        if (epNum.isNotEmpty()) {
                            Text(
                                text = "Ep. $epNum",
                                color = Color.White.copy(alpha = 0.30f),
                                style = NovaType.caption,
                            )
                        }
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLastWatched) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = AppColors.amber,
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(15.dp),
                    )
                }
                Text(
                    text = if (epNum.isNotEmpty()) "$epNum. $episodeTitle" else episodeTitle,
                    color = if (isLastWatched) AppColors.amber else Color.White,
                    style = NovaType.subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (plot.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = plot,
                    style = NovaType.meta.copy(lineHeight = 16.sp),
                    color = subtleTextColor(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLastWatched) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ÚLTIMO VISUALIZADO",
                    style = NovaType.badge.copy(color = AppColors.amber),
                )
            }
        }
        Icon(
            Icons.Filled.PlayCircleOutline,
            contentDescription = null,
            tint = if (focused) AppColors.accent else Color.White.copy(alpha = 0.20f),
            modifier = Modifier
                .padding(end = 10.dp)
                    .size(26.dp),
        )
    }
}
