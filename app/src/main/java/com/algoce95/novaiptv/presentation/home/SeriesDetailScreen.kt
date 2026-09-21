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
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.presentation.tv.TvFocusShape
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress
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

    val seasonFocus = remember { FocusRequester() }
    val firstEpisodeFocus = remember { FocusRequester() }
    val targetEpisodeFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

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

    fun sortedSeasons(): List<String> =
        Parsers.sortSeasonKeys(episodesMap(seriesData).keys.toList())

    fun seasonForEpisode(episodes: Map<String, List<JSONObject>>, episodeId: String): String? {
        for ((season, list) in episodes) {
            for (item in list) {
                val id = item.opt("id")?.toString() ?: item.opt("stream_id")?.toString()
                if (id == episodeId) return season
            }
        }
        return null
    }

    fun currentEpisodes(): List<JSONObject> {
        val season = selectedSeason ?: return emptyList()
        val raw = episodesMap(seriesData)[season] ?: return emptyList()
        return raw
    }

    fun allEpisodes(): List<JSONObject> {
        val map = episodesMap(seriesData)
        val result = mutableListOf<JSONObject>()
        for (season in sortedSeasons()) {
            result.addAll(map[season] ?: emptyList())
        }
        return result
    }

    fun episodeTitle(ep: JSONObject): String {
        val raw = ep.opt("title")?.toString() ?: ep.opt("name")?.toString().orEmpty()
        if (raw.isNotBlank()) return raw
        val num = ep.opt("episode_num")?.toString()
            ?: ep.opt("episode_number")?.toString().orEmpty()
        return "Episodio $num"
    }

    fun episodeStreamUrl(ep: JSONObject): String {
        val id = ep.opt("id")?.toString() ?: ep.opt("stream_id").toString()
        val ext = ep.opt("container_extension")?.toString() ?: "mp4"
        return requireNotNull(AppContainer.api).episodeStreamUrl(
            id.toIntOrNull() ?: 0,
            ext,
        )
    }

    fun saveLastWatched(epId: Int, title: String) {
        scope.launch {
            AppContainer.prefs.setString("last_episode_${series.seriesId}", "$epId|$title")
            lastEpisodeId = epId
            lastEpisodeTitle = title
            sessionEpisodeId = epId.toString()
        }
    }

    fun playEpisode(ep: JSONObject) {
        val epId = ep.opt("id")?.toString() ?: ep.opt("stream_id")?.toString()
        val parsedId = epId?.toIntOrNull() ?: return
        val epTitle = episodeTitle(ep)
        val playable = allEpisodes().filter {
            (it.opt("id")?.toString() ?: it.opt("stream_id")?.toString())?.toIntOrNull() != null
        }
        val queue = playable.map {
            val itemId = it.opt("id")?.toString() ?: it.opt("stream_id").toString()
            QueueItem(
                streamUrl = episodeStreamUrl(it),
                title = "${series.title} - ${episodeTitle(it)}",
                episodeId = itemId,
            )
        }
        val streamUrl = episodeStreamUrl(ep)
        var queueIndex = queue.indexOfFirst { it.streamUrl == streamUrl }
        if (queueIndex < 0) queueIndex = 0
        scope.launch {
            AppContainer.prefs.pushHistory("series:${series.seriesId}")
            saveLastWatched(parsedId, epTitle.toString())
            AppContainer.playerSession = PlayerSession(
                items = queue,
                index = queueIndex,
                title = "${series.title} - $epTitle",
                progressId = "series:${series.seriesId}",
                isLive = false,
                poster = series.logo,
                onQueueIndexChanged = { index ->
                    if (index in playable.indices) {
                        val changed = playable[index]
                        val changedId = (changed.opt("id")?.toString()
                            ?: changed.opt("stream_id")?.toString())?.toIntOrNull()
                        if (changedId != null) {
                            saveLastWatched(changedId, episodeTitle(changed).toString())
                        }
                    }
                },
            )
            onPlay()
        }
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
            if (initialEpisodeId != null && target != null && selected != null &&
                seasonForEpisode(episodes, target) != null
            ) {
                targetEpisodeFocus.requestFocus()
            }
        } catch (_: Exception) {
            isLoading = false
        }
    }

    val info = seriesData?.optJSONObject("info")
    val rawPlot = info?.opt("plot")?.toString() ?: info?.opt("description")?.toString()
    val plot = if (!rawPlot.isNullOrBlank()) rawPlot else "Sin descripción disponible"
    val seasons = sortedSeasons()
    val episodes = currentEpisodes()
    val target = targetEpisodeId()
    val episodeColumns = if (LocalConfiguration.current.screenWidthDp >= 900) 2 else 1
    val gridState = rememberLazyGridState()

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.panel)
                            .height(52.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { season ->
                            val selected = season == selectedSeason
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedSeason = season
                                    headerCompact = true
                                    scope.launch { firstEpisodeFocus.requestFocus() }
                                },
                                label = { Text("T$season") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.accent,
                                    containerColor = AppColors.focusFill,
                                ),
                                modifier = Modifier
                                    .tvFocusScale(selected, 1.04f)
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
                                                scope.launch { firstEpisodeFocus.requestFocus() }
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                headerCompact = true
                                                scope.launch { firstEpisodeFocus.requestFocus() }
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
                            items(episodes.size) { index ->
                                val ep = episodes[index]
                                val epId = (ep.opt("id")?.toString()
                                    ?: ep.opt("stream_id")).toString()
                                val isTarget = target != null && epId == target
                                EpisodeCard(
                                    episode = ep,
                                    episodeTitle = episodeTitle(ep),
                                    isLastWatched = isTarget,
                                    focusNode = when {
                                        isTarget -> targetEpisodeFocus
                                        index == 0 -> firstEpisodeFocus
                                        else -> null
                                    },
                                    onArrowUp = if (index == 0) {
                                        { seasonFocus.requestFocus() }
                                    } else {
                                        null
                                    },
                                    onGainFocus = { headerCompact = true },
                                    onPlay = { playEpisode(ep) },
                                )
                            }
                        }
                        if (initialEpisodeId != null && target != null) {
                            LaunchedEffect(episodes) {
                                val index = episodes.indexOfFirst {
                                    ((it.opt("id")?.toString() ?: it.opt("stream_id")).toString()) == target
                                }
                                if (index >= 0) gridState.animateScrollToItem(index)
                            }
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
                    width = if (compact) 72.dp else 140.dp,
                    height = if (compact) 102.dp else 198.dp,
                )
                .clip(RoundedCornerShape(13.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = series.logo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = AppColors.amber, modifier = Modifier.size(48.dp))
                },
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = series.title,
                color = Color.White,
                fontSize = if (compact) 17.sp else 24.sp,
                fontWeight = FontWeight.W800,
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
                        fontSize = 12.sp,
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
                    color = bodyTextColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 5,
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
    focusNode: FocusRequester?,
    onArrowUp: (() -> Unit)?,
    onGainFocus: () -> Unit = {},
    onPlay: () -> Unit,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    LaunchedEffect(focused) {
        if (focused) onGainFocus()
    }
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
            .height(150.dp)
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
            .then(if (focusNode != null) Modifier.focusRequester(focusNode) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(onTap = onPlay)
            .onPreviewKeyEvent { event ->
                if (event.isKeyDown() && event.key == Key.DirectionUp && onArrowUp != null) {
                    onArrowUp()
                    true
                } else {
                    false
                }
            }
            .tvFocusScale(focused),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
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
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(28.dp))
                        if (epNum.isNotEmpty()) {
                            Text(text = "Ep. $epNum", color = Color.White.copy(alpha = 0.22f), fontSize = 10.sp)
                        }
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLastWatched) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AppColors.amber, modifier = Modifier
                        .padding(end = 4.dp)
                        .size(14.dp))
                }
                Text(
                    text = if (epNum.isNotEmpty()) "$epNum. $episodeTitle" else episodeTitle,
                    color = if (isLastWatched) AppColors.amber else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (plot.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plot,
                    color = subtleTextColor(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLastWatched) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Último visualizado",
                    color = AppColors.amber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Icon(
            Icons.Filled.PlayCircleOutline,
            contentDescription = null,
            tint = if (focused) AppColors.accent else Color.White.copy(alpha = 0.14f),
            modifier = Modifier
                .padding(end = 8.dp)
                    .size(24.dp),
        )
    }
}
