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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.presentation.tv.NovaButton
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.NovaShapes
import com.algoce95.novaiptv.core.theme.NovaType
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
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
        Box(modifier = Modifier.fillMaxSize()) {
            // Arte ambiental de fondo, tenido bajo el velo: la ficha se lee
            // como el contenido, no como un formulario sobre negro.
            Box(modifier = Modifier.fillMaxSize().alpha(0.22f)) {
                RemoteImage(
                    url = movie.logo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    fallbackTitle = movie.title,
                    fallbackType = PosterType.MOVIE,
                    error = { },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to AppColors.ink.copy(alpha = 0.72f),
                            0.45f to AppColors.ink.copy(alpha = 0.92f),
                            1f to AppColors.ink,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(width = 236.dp, height = 344.dp)
                            .shadow(18.dp, NovaShapes.tile)
                            .clip(NovaShapes.tile)
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
                                Icon(
                                    Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = AppColors.amber,
                                    modifier = Modifier.size(80.dp),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.width(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = movie.title,
                            style = NovaType.display.copy(color = Color.White),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (movie.year > 0) MetaChip("${movie.year}")
                            if (genre.isNotEmpty()) MetaChip(genre)
                            if (rating.isNotEmpty()) MetaChip(rating, withStar = true)
                        }
                        Spacer(Modifier.height(18.dp))
                        if (director.isNotEmpty()) {
                            Text(
                                text = "Director: $director",
                                style = NovaType.body,
                                color = subtleTextColor(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = plot,
                            style = NovaType.body.copy(lineHeight = 22.sp),
                            color = bodyTextColor(),
                        )
                        Spacer(Modifier.height(30.dp))
                        val playFocusState = rememberTvFocus()
                        NovaButton(
                            label = "Reproducir película",
                            icon = Icons.Filled.PlayArrow,
                            onClick = ::play,
                            modifier = Modifier
                                .focusRequester(playFocus)
                                .focusable(interactionSource = playFocusState.interaction)
                                .tvPress(fireOnDown = true, onTap = ::play)
                                .tvFocusScale(playFocusState.focused, 1.04f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, withStar: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(NovaShapes.pill)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), NovaShapes.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withStar) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = AppColors.amber,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = NovaType.meta.copy(color = Color.White))
    }
}
