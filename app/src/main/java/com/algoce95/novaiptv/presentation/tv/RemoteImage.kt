package com.algoce95.novaiptv.presentation.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.network.httpHeaders
import coil3.network.NetworkHeaders
import coil3.request.crossfade
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.data.metadata.PosterResolver
import com.algoce95.novaiptv.data.metadata.PosterType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object PosterClient {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                },
            )
            .build()
    }

    private var _imageLoader: ImageLoader? = null

    /** Cierra conexiones pooled ociosas (una sola conexión de streaming a la vez). */
    fun evictIdleConnections() {
        okHttpClient.connectionPool.evictAll()
    }

    fun provideImageLoader(context: Context): ImageLoader {
        return _imageLoader ?: synchronized(this) {
            _imageLoader ?: ImageLoader.Builder(context)
                .components {
                    add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
                .build().also { _imageLoader = it }
        }
    }
}

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    fallbackTitle: String? = null,
    fallbackType: PosterType = PosterType.MOVIE,
    loading: @Composable BoxScope.() -> Unit = {
        ShimmerBox(modifier = Modifier.fillMaxSize())
    },
    error: @Composable BoxScope.() -> Unit = {
        InitialsFallback(title = fallbackTitle)
    },
) {
    val context = LocalContext.current
    val imageLoader = remember { PosterClient.provideImageLoader(context) }

    // Si el panel no trae cartelera, se intenta obtener de iTunes/TMDB usando el
    // título limpio. La consulta se cachea y solo ocurre con la tarjeta visible.
    var resolved by remember(url, fallbackTitle, fallbackType) { mutableStateOf(url) }
    LaunchedEffect(url, fallbackTitle, fallbackType) {
        resolved = if (url.isBlank() && !fallbackTitle.isNullOrBlank()) {
            PosterResolver.resolve(fallbackTitle, fallbackType) ?: ""
        } else {
            url
        }
    }

    Box(
        modifier = modifier
            .background(AppColors.posterFallback),
        contentAlignment = Alignment.Center,
    ) {
        if (resolved.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolved)
                    .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36").build())
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { loading() },
                error = { error() }
            )
        } else {
            error()
        }
    }
}

/** Iniciales del título sobre el color de fallback: sustituye al icono roto. */
@Composable
fun InitialsFallback(title: String?, modifier: Modifier = Modifier) {
    val initials = title
        ?.split(' ')
        ?.filter { it.isNotBlank() }
        ?.take(2)
        ?.joinToString("") { it.first().uppercase() }
        .orEmpty()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.posterFallback),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotEmpty()) {
            Text(
                text = initials,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
            )
        } else {
            Icon(
                Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
