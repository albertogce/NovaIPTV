package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.data.model.VodMovie
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Paridad con `MovieDetailScreen` de Flutter. */
@Composable
fun MovieDetailScreen(movie: VodMovie, onPlay: () -> Unit) {
    var vodInfo by remember(movie.movieId) { mutableStateOf<JSONObject?>(null) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(movie.movieId) {
        playFocus.requestFocus()
        try {
            vodInfo = requireNotNull(AppContainer.api).getVodInfo(movie.movieId)
        } catch (_: Exception) {
            // vodInfo queda nulo; la URL usa el fallback mp4.
        }
    }

    fun streamUrl(): String {
        val info = vodInfo
        val ext = info?.optJSONObject("movie_data")?.opt("container_extension")?.toString()
            ?: info?.optJSONObject("info")?.opt("container_extension")?.toString()
            ?: "mp4"
        return requireNotNull(AppContainer.api).movieStreamUrl(movie.movieId, ext)
    }

    fun play() {
        AppContainer.playerSession = PlayerSession(
            items = listOf(QueueItem(streamUrl(), movie.title)),
            index = 0,
            title = movie.title,
            progressId = "movie:${movie.movieId}",
            isLive = false,
            poster = movie.logo,
            // El historial se escribe al arrancar la reproducción, no al pulsar.
            onPlaybackStarted = {
                CoroutineScope(Dispatchers.IO).launch {
                    AppContainer.prefs.pushHistory("movie:${movie.movieId}")
                }
            },
        )
        onPlay()
    }

    val info = vodInfo?.optJSONObject("info")
    val plot = info?.opt("plot")?.toString()
        ?: info?.opt("description")?.toString()
        ?: "Sin descripción disponible"
    val genre = info?.opt("genre")?.toString() ?: movie.category
    val rating = info?.opt("rating")?.toString()
        ?: info?.opt("rating_5rating")?.toString().orEmpty()
    val director = info?.opt("director")?.toString().orEmpty()

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.posterSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = movie.logo,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        fallbackTitle = movie.title,
                        fallbackType = PosterType.MOVIE,
                        error = {
                            Icon(Icons.Filled.Movie, contentDescription = null, tint = AppColors.amber, modifier = Modifier.size(80.dp))
                        },
                    )
                }
                Spacer(Modifier.width(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (movie.year > 0) MetaChip("${movie.year}")
                        if (genre.isNotEmpty()) MetaChip(genre)
                        if (rating.isNotEmpty()) MetaChip(rating, withStar = true)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (director.isNotEmpty()) {
                        Text(text = "Director: $director", color = bodyTextColor(), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = plot,
                        color = bodyTextColor(),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(28.dp))
                    val playFocusState = rememberTvFocus()
                    val playFocused = playFocusState.focused
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                2.dp,
                                if (playFocused) Color.White else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (playFocused) {
                                    Modifier.shadow(
                                        16.dp,
                                        RoundedCornerShape(8.dp),
                                        ambientColor = AppColors.pink.copy(alpha = 0.5f),
                                        spotColor = AppColors.pink.copy(alpha = 0.5f),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .focusRequester(playFocus)
                            .focusable(interactionSource = playFocusState.interaction)
                            .tvPress(fireOnDown = true, onTap = ::play)
                            .tvFocusScale(playFocused, 1.04f),
                    ) {
                        Button(
                            onClick = ::play,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playFocused) AppColors.salmon else AppColors.movieButtonDark,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .padding(horizontal = 28.dp, vertical = 16.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "REPRODUCIR PELÍCULA",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, withStar: Boolean = false) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (withStar) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = AppColors.amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text = text, color = Color.White)
        }
    }
}
