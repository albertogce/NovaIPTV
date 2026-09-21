package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.faintTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.data.model.VodMovie
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tryRequestFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress

/** Paridad con `SearchResultsScreen` de Flutter (grupos por tipo). */
@Composable
fun SearchResultsScreen(
    query: String,
    onPlayMovie: (VodMovie) -> Unit,
    onPlaySeries: (Series) -> Unit,
    onPlay: () -> Unit,
) {
    val snapshot = remember { AppContainer.searchSnapshot }
    val client = AppContainer.api
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.ink) {
        if (client == null || (snapshot.channels.isEmpty() && snapshot.movies.isEmpty() && snapshot.series.isEmpty())) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sin resultados para \"$query\"",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W700,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Prueba con otro texto.",
                        color = subtleTextColor(),
                    )
                }
            }
            return@Surface
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            if (snapshot.channels.isNotEmpty()) {
                item {
                    ResultSectionTitle(
                        title = "Canales en Vivo (${snapshot.channels.size})",
                        icon = Icons.Filled.LiveTv,
                    )
                }
                items(
                    count = snapshot.channels.size,
                    key = { "ch${snapshot.channels[it].channelId}" },
                ) { index ->
                    val channel = snapshot.channels[index]
                    ResultTile(
                        title = channel.channelName,
                        subtitle = channel.currentEpgTitle.ifEmpty { null },
                        focusRequester = if (index == 0) firstFocus else null,
                        leading = {
                            ChannelThumb(channel = channel)
                        },
                        onTap = {
                            val items = snapshot.channels.map {
                                QueueItem(
                                    streamUrl = client.liveStreamUrl(it.channelId),
                                    title = it.channelName,
                                )
                            }
                            var queueIndex = snapshot.channels.indexOfFirst {
                                it.channelId == channel.channelId
                            }
                            if (queueIndex < 0) queueIndex = 0
                            AppContainer.playerSession = PlayerSession(
                                items = items,
                                index = queueIndex,
                                title = items[queueIndex].title,
                                progressId = null,
                                isLive = true,
                            )
                            onPlay()
                        },
                    )
                }
            }
            if (snapshot.movies.isNotEmpty()) {
                item {
                    ResultSectionTitle(
                        title = "Películas (${snapshot.movies.size})",
                        icon = Icons.Filled.Movie,
                    )
                }
                items(
                    count = snapshot.movies.size,
                    key = { "mv${snapshot.movies[it].movieId}" },
                ) { index ->
                    val movie = snapshot.movies[index]
                    ResultTile(
                        title = movie.title,
                        subtitle = if (movie.year > 0) movie.year.toString() else null,
                        focusRequester = if (index == 0 && snapshot.channels.isEmpty()) firstFocus else null,
                        leading = { PosterThumb(url = movie.logo, fallback = Icons.Filled.Movie, title = movie.title, type = PosterType.MOVIE) },
                        onTap = { onPlayMovie(movie) },
                    )
                }
            }
            if (snapshot.series.isNotEmpty()) {
                item {
                    ResultSectionTitle(
                        title = "Series (${snapshot.series.size})",
                        icon = Icons.Outlined.Tv,
                    )
                }
                items(
                    count = snapshot.series.size,
                    key = { "se${snapshot.series[it].seriesId}" },
                ) { index ->
                    val series = snapshot.series[index]
                    ResultTile(
                        title = series.title,
                        subtitle = null,
                        focusRequester = if (index == 0 && snapshot.channels.isEmpty() && snapshot.movies.isEmpty()) {
                            firstFocus
                        } else {
                            null
                        },
                        leading = { PosterThumb(url = series.logo, fallback = Icons.Outlined.Tv, title = series.title, type = PosterType.SERIES) },
                        onTap = { onPlaySeries(series) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.pink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultTile(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onTap: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    ListItem(
        headlineContent = {
            Text(text = title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = subtitle?.let { { Text(text = it, color = subtleTextColor(), fontSize = 12.sp) } },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = faintTextColor())
        },
        colors = ListItemDefaults.colors(
            containerColor = if (focused) AppColors.searchTileFocus else AppColors.searchTile,
        ),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onTap)
            .tvFocusScale(focused, 1.02f),
    )
}

@Composable
private fun ChannelThumb(channel: LiveChannel) {
    RemoteImage(
        url = channel.channelLogo,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = 40.dp, height = 40.dp),
        error = {
            Icon(Icons.Filled.LiveTv, contentDescription = null, tint = subtleTextColor(), modifier = Modifier.size(32.dp))
        },
    )
}

@Composable
private fun PosterThumb(url: String, fallback: ImageVector, title: String, type: PosterType) {
    RemoteImage(
        url = url,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 40.dp, height = 56.dp),
        fallbackTitle = title,
        fallbackType = type,
        error = {
            Icon(fallback, contentDescription = null, tint = AppColors.amber, modifier = Modifier.size(32.dp))
        },
    )
}
