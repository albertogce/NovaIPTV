package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.NovaShapes
import com.algoce95.novaiptv.core.theme.NovaType
import com.algoce95.novaiptv.core.theme.faintTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.data.model.WatchProgress
import com.algoce95.novaiptv.presentation.tv.MarqueeText
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.TvKeys
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.presentation.tv.requestFocusReady
import com.algoce95.novaiptv.presentation.tv.tryRequestFocus
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress

/** Paridad con `HomeCenterDashboard` de Flutter, con hero y filas horizontales. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardView(
    totalChannels: Int,
    totalMovies: Int,
    totalSeries: Int,
    expiryText: String?,
    expiryColor: Color?,
    onSelectLive: () -> Unit,
    onSelectMovies: () -> Unit,
    onSelectSeries: () -> Unit,
    onSelectContinueWatching: () -> Unit,
    onSelectHistory: () -> Unit,
    onSelectSettings: () -> Unit,
    onRefresh: () -> Unit,
    globalSearchQuery: String,
    onSearchSubmitted: (String) -> Unit,
    recentlyAdded: List<RecentItem> = emptyList(),
    onSelectRecent: (RecentItem) -> Unit = {},
    continueWatching: List<WatchProgress> = emptyList(),
    progressImage: (WatchProgress) -> String = { it.poster.orEmpty() },
    onSelectProgress: (WatchProgress) -> Unit = {},
    searchSignal: Int = 0,
) {
    var showSearch by remember { mutableStateOf(false) }
    var draftQuery by remember { mutableStateOf(globalSearchQuery) }
    val heroFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val hero = heroEntry(continueWatching, recentlyAdded)

    // Al abrir, el foco en el héroe desplaza la vista hacia abajo y recorta la
    // cabecera. Devolvemos el scroll al inicio tras asentarse el foco. Sin VOD
    // no hay héroe, y pedir el foco a un nodo inexistente tumbaría la app.
    LaunchedEffect(Unit) {
        if (hero != null) heroFocus.tryRequestFocus()
        withFrameNanos { }
        scrollState.scrollTo(0)
    }

    // La tecla de color "Buscar" llega como señal externa: el diálogo vive aquí.
    LaunchedEffect(searchSignal) {
        if (searchSignal > 0) showSearch = true
    }

    if (showSearch) {
        TvSearchDialog(
            initialQuery = draftQuery,
            onDismiss = { showSearch = false },
            onSubmit = { query ->
                draftQuery = query
                showSearch = false
                if (query.isNotEmpty()) onSearchSubmitted(query)
            },
        )
    }

    // La spec por defecto en Android (pivot) CENTRA el elemento enfocado: al
    // mover el foco entre los botones, la vista salta hacia abajo. Con esta
    // spec mínima solo se desplaza lo justo para que el elemento sea visible.
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoViewSpec) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.ink, AppColors.dashboardGradEnd),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                DashboardHeader(
                    totalChannels = totalChannels,
                    totalMovies = totalMovies,
                    totalSeries = totalSeries,
                    expiryText = expiryText,
                    expiryColor = expiryColor,
                    onRefresh = onRefresh,
                    onSelectHistory = onSelectHistory,
                    onSelectSettings = onSelectSettings,
                )
                Spacer(Modifier.height(16.dp))
                if (hero != null) {
                    HeroCard(
                        entry = hero,
                        focusRequester = heroFocus,
                        autofocus = true,
                        onOpen = {
                            when (hero) {
                                is HeroEntry.Progress -> onSelectProgress(hero.progress)
                                is HeroEntry.Recent -> onSelectRecent(hero.item)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                SearchActionButton(
                    query = draftQuery,
                    onClick = { showSearch = true },
                )
                Spacer(Modifier.height(18.dp))
                QuickAccessRow(
                    totalChannels = totalChannels,
                    totalMovies = totalMovies,
                    totalSeries = totalSeries,
                    continueCount = continueWatching.size,
                    onSelectLive = onSelectLive,
                    onSelectMovies = onSelectMovies,
                    onSelectSeries = onSelectSeries,
                    onSelectContinueWatching = onSelectContinueWatching,
                )
                if (continueWatching.size > 1) {
                    Spacer(Modifier.height(26.dp))
                    HomeRow(title = "Seguir viendo") {
                        items(continueWatching.take(12), key = { it.id }) { progress ->
                            ContinueTile(
                                progress = progress,
                                imageUrl = progressImage(progress),
                                onClick = { onSelectProgress(progress) },
                            )
                        }
                    }
                }
                if (recentlyAdded.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    HomeRow(title = "Añadido recientemente") {
                        items(recentlyAdded, key = { "${it.type}:${it.id}" }) { item ->
                            RecentTile(
                                item = item,
                                onClick = { onSelectRecent(item) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Lo que abre el héroe: primero lo que se quedó a medias, si no, lo más nuevo. */
private sealed interface HeroEntry {
    data class Progress(val progress: WatchProgress) : HeroEntry
    data class Recent(val item: RecentItem) : HeroEntry
}

private fun heroEntry(
    progress: List<WatchProgress>,
    recent: List<RecentItem>,
): HeroEntry? = progress.firstOrNull()?.let { HeroEntry.Progress(it) }
    ?: recent.firstOrNull()?.let { HeroEntry.Recent(it) }

/**
 * Desplazamiento de "traer a vista" mínimo: 0 si ya es visible; si no, lo justo
 * para asomar el borde que falte (negativo arriba, positivo abajo).
 */
@OptIn(ExperimentalFoundationApi::class)
private object MinimalBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        if (offset >= 0f && end <= containerSize) return 0f
        // Más alto que el viewport: mejor dejar el borde superior donde está.
        if (offset < 0f && end > containerSize) return 0f
        return if (offset < 0f) offset else end - containerSize
    }
}

/** Cabecera: marca, contadores de biblioteca, caducidad y acciones. */
@Composable
private fun DashboardHeader(
    totalChannels: Int,
    totalMovies: Int,
    totalSeries: Int,
    expiryText: String?,
    expiryColor: Color?,
    onRefresh: () -> Unit,
    onSelectHistory: () -> Unit,
    onSelectSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NOVA",
            style = NovaType.sectionTitle.copy(color = Color.White, letterSpacing = 3.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "IPTV", style = NovaType.sectionTitle.copy(color = AppColors.accent))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryStat(Icons.Filled.LiveTv, totalChannels, "canales", AppColors.mint)
            LibraryStat(Icons.Outlined.Movie, totalMovies, "películas", AppColors.amber)
            LibraryStat(Icons.Outlined.Tv, totalSeries, "series", AppColors.accent)
        }
        Spacer(Modifier.width(14.dp))
        if (expiryText != null && expiryColor != null) {
            Text(
                text = expiryText,
                style = NovaType.caption.copy(fontWeight = FontWeight.W600),
                color = expiryColor,
                maxLines = 1,
            )
            Spacer(Modifier.width(14.dp))
        }
        HeaderIconButton(
            icon = Icons.Filled.Refresh,
            label = "Actualizar",
            onClick = onRefresh,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = Icons.Outlined.History,
            label = "Historial",
            onClick = onSelectHistory,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = Icons.Filled.Settings,
            label = "Ajustes",
            onClick = onSelectSettings,
        )
    }
}

/** Botón de cabecera (refresh/ajustes): enfoca con el patrón TV de la app. */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocus()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focus.focused) AppColors.focusFill else Color.Transparent)
            .border(
                width = if (focus.focused) 2.dp else 1.dp,
                color = if (focus.focused) AppColors.mint else Color.White.copy(alpha = 0.10f),
                shape = CircleShape,
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .tvFocusScale(focus.focused, 1.12f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (focus.focused) Color.White else subtleTextColor(),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LibraryStat(icon: ImageVector, value: Int, label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(NovaShapes.chip)
            .background(AppColors.panel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), NovaShapes.chip)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text = "$value", style = NovaType.subtitle.copy(color = Color.White))
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = NovaType.meta, color = faintTextColor())
    }
}

/**
 * Héroe: arte ambiental de fondo + carátula y acción principal. Es lo primero
 * que se ve al abrir la app, así que concentra lo más relevante.
 */
@Composable
private fun HeroCard(
    entry: HeroEntry,
    onOpen: () -> Unit,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val title: String
    val label: String
    val accent: Color
    val poster: String
    val fallbackType: PosterType
    val progress: Float?
    when (entry) {
        is HeroEntry.Progress -> {
            title = entry.progress.title
            label = "SEGUIR VIENDO"
            accent = AppColors.mint
            poster = entry.progress.poster.orEmpty()
            fallbackType = if (entry.progress.id.startsWith("movie:")) {
                PosterType.MOVIE
            } else {
                PosterType.SERIES
            }
            progress = entry.progress.fraction.toFloat()
        }
        is HeroEntry.Recent -> {
            title = entry.item.title
            label = "AÑADIDO RECIENTEMENTE"
            accent = if (entry.item.type == "movie") AppColors.amber else AppColors.accent
            poster = entry.item.logo
            fallbackType = if (entry.item.type == "movie") PosterType.MOVIE else PosterType.SERIES
            progress = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(252.dp)
            .shadow(if (focused) 24.dp else 10.dp, NovaShapes.tile)
            .clip(NovaShapes.tile)
            .background(
                Brush.linearGradient(
                    listOf(AppColors.cardGradStart, AppColors.cardGradEnd),
                ),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(
                autofocus = autofocus,
                ignoreInitialSelect = true,
                fireOnDown = true,
                onTap = onOpen,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
                shape = NovaShapes.tile,
            )
            .tvFocusScale(focused, 1.012f),
    ) {
        // El mismo arte, muy tenido, da profundidad al panel sin recortar la carátula.
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(0.20f),
        ) {
            RemoteImage(
                url = poster,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = title,
                fallbackType = fallbackType,
                error = { },
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(AppColors.cardGradEnd.copy(alpha = 0.86f), Color.Transparent),
                    ),
                ),
        )
        Row(
            modifier = Modifier.matchParentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(158.dp)
                    .fillMaxHeight()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.posterFallback),
            ) {
                RemoteImage(
                    url = poster,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    fallbackTitle = title,
                    fallbackType = fallbackType,
                )
            }
            Column(
                modifier = Modifier
                    .width(0.dp)
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 22.dp, end = 24.dp, top = 26.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = NovaType.badge.copy(color = accent, letterSpacing = 1.6.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title,
                    style = NovaType.display.copy(color = Color.White),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .width(240.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AppColors.mint,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                // Pastilla decorativa: el héroe entero es el destino de foco y
                // de la pulsación, así que el botón no necesita su propio foco.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (focused) AppColors.accentBright else AppColors.accent)
                        .border(
                            width = 2.dp,
                            color = if (focused) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ver ficha",
                        color = Color.White,
                        style = NovaType.subtitle.copy(fontWeight = FontWeight.W700),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessRow(
    totalChannels: Int,
    totalMovies: Int,
    totalSeries: Int,
    continueCount: Int,
    onSelectLive: () -> Unit,
    onSelectMovies: () -> Unit,
    onSelectSeries: () -> Unit,
    onSelectContinueWatching: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        QuickAccessTile(
            icon = Icons.Filled.LiveTv,
            label = "Canales en Vivo",
            subtitle = "$totalChannels canales",
            accent = AppColors.mint,
            onPressed = onSelectLive,
            modifier = Modifier.weight(1f),
        )
        QuickAccessTile(
            icon = Icons.Outlined.Movie,
            label = "Películas",
            subtitle = "$totalMovies títulos",
            accent = AppColors.amber,
            onPressed = onSelectMovies,
            modifier = Modifier.weight(1f),
        )
        QuickAccessTile(
            icon = Icons.Outlined.Tv,
            label = "Series",
            subtitle = "$totalSeries series",
            accent = AppColors.accent,
            onPressed = onSelectSeries,
            modifier = Modifier.weight(1f),
        )
        QuickAccessTile(
            icon = Icons.Outlined.History,
            label = "Seguir viendo",
            subtitle = if (continueCount > 0) "$continueCount en curso" else "nada pendiente",
            accent = AppColors.sky,
            onPressed = onSelectContinueWatching,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accent: Color,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = modifier
            .height(86.dp)
            .clip(NovaShapes.card)
            .background(if (focused) AppColors.mint.copy(alpha = 0.14f) else AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
                shape = NovaShapes.card,
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onPressed)
            .tvFocusScale(focused, 1.03f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = if (focused) 0.28f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = NovaType.cardTitle.copy(
                    color = Color.White,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.W600,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = NovaType.caption,
                color = faintTextColor(),
                maxLines = 1,
            )
        }
    }
}

/** Fila con título y carrusel horizontal de tarjetas. */
@Composable
private fun HomeRow(
    title: String,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = NovaType.sectionTitle.copy(color = Color.White))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun RecentTile(
    item: RecentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val accent = if (item.type == "movie") AppColors.amber else AppColors.accent
    Column(
        modifier = modifier
            .width(148.dp)
            .clip(NovaShapes.card)
            .background(AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
                shape = NovaShapes.card,
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .tvFocusScale(focused, 1.06f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .background(AppColors.posterFallback),
        ) {
            RemoteImage(
                url = item.logo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = item.title,
                fallbackType = if (item.type == "movie") PosterType.MOVIE else PosterType.SERIES,
                error = {
                    Icon(
                        if (item.type == "movie") Icons.Outlined.Movie else Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(34.dp),
                    )
                },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1f to AppColors.scrimBottom,
                        ),
                    ),
            )
            MarqueeText(
                text = item.title,
                style = NovaType.cardTitle.copy(color = Color.White, fontWeight = FontWeight.W700),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            )
        }
        Text(
            text = if (item.type == "movie") "Película" else "Serie",
            style = NovaType.caption,
            color = accent,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun ContinueTile(
    progress: WatchProgress,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val done = progress.fraction >= .95
    Column(
        modifier = modifier
            .width(236.dp)
            .clip(NovaShapes.card)
            .background(AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
                shape = NovaShapes.card,
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .tvFocusScale(focused, 1.04f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(AppColors.posterFallback),
        ) {
            RemoteImage(
                url = imageUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = progress.title,
                fallbackType = if (progress.id.startsWith("movie:")) {
                    PosterType.MOVIE
                } else {
                    PosterType.SERIES
                },
            )
            Text(
                text = progress.remainingLabel(),
                style = NovaType.badge.copy(color = Color.White),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AppColors.badgeDark)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        MarqueeText(
            text = progress.title,
            style = NovaType.cardTitle.copy(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        )
        LinearProgressIndicator(
            progress = { progress.fraction.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (done) AppColors.mint else AppColors.accent,
            trackColor = Color.White.copy(alpha = 0.10f),
            strokeCap = StrokeCap.Round,
        )
    }
}

private fun WatchProgress.remainingLabel(): String {
    if (fraction >= .95) return "Completado"
    return "${positionMs / 60_000} de ${durationMs / 60_000} min"
}

@Composable
private fun SearchActionButton(query: String, onClick: () -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NovaShapes.card)
            .background(if (focused) AppColors.accent else AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                shape = NovaShapes.card,
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .tvFocusScale(focused, 1.008f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = if (focused) Color.White else AppColors.mint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = query.ifEmpty { "Buscar en todo el contenido" },
            color = if (query.isEmpty()) subtleTextColor() else Color.White,
            style = NovaType.subtitle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TvSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val fieldFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    fun submit() {
        val q = query.trim()
        if (q.isNotEmpty()) onSubmit(q) else onDismiss()
    }

    LaunchedEffect(Unit) { fieldFocus.requestFocusReady() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.panel,
        shape = NovaShapes.tile,
        title = { Text("Buscar en tu biblioteca", color = Color.White) },
        text = {
            Column {
                Text(
                    "Escribe lo que buscas y pulsa OK para buscar.",
                    color = faintTextColor(),
                    style = NovaType.meta,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Canal, película o serie") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.mint)
                    },
                    singleLine = true,
                    shape = NovaShapes.card,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier
                        .focusRequester(fieldFocus)
                        .focusProperties { down = cancelFocus }
                        // Con teclado suave no siempre llega la acción de IME:
                        // OK sobre el campo debe buscar ya.
                        .onPreviewKeyEvent { event ->
                            if (event.isKeyDown() && TvKeys.isSelectKey(event.key)) {
                                submit()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        },
        dismissButton = {
            DialogButton(
                label = "Cancelar",
                primary = false,
                onClick = onDismiss,
                focusNode = cancelFocus,
                rightNode = searchFocus,
            )
        },
        confirmButton = {
            DialogButton(
                label = "Buscar",
                primary = true,
                onClick = { submit() },
                focusNode = searchFocus,
                leftNode = cancelFocus,
            )
        },
    )
}

/** Botón de diálogo con el lenguaje de foco de la app: los de Material no muestran el foco. */
@Composable
private fun DialogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusNode: FocusRequester,
    leftNode: FocusRequester? = null,
    rightNode: FocusRequester? = null,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused && primary -> AppColors.accentBright
                    focused -> AppColors.focusFill
                    primary -> AppColors.accent
                    else -> Color.Transparent
                },
            )
            .border(
                width = 2.dp,
                color = if (focused) {
                    Color.White.copy(alpha = 0.9f)
                } else if (primary) {
                    Color.Transparent
                } else {
                    Color.White.copy(alpha = 0.25f)
                },
                shape = shape,
            )
            .focusRequester(focusNode)
            .focusProperties {
                leftNode?.let { left = it }
                rightNode?.let { right = it }
            }
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = NovaType.subtitle.copy(fontWeight = FontWeight.W700),
        )
    }
}
