package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.faintTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.presentation.tv.MarqueeText
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress

/** Paridad con `HomeCenterDashboard` de Flutter. */
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
    onSelectSettings: () -> Unit,
    onRefresh: () -> Unit,
    globalSearchQuery: String,
    onSearchSubmitted: (String) -> Unit,
    recentlyAdded: List<RecentItem> = emptyList(),
    onSelectRecent: (RecentItem) -> Unit = {},
) {
    var showSearch by remember { mutableStateOf(false) }
    var draftQuery by remember { mutableStateOf(globalSearchQuery) }
    val firstButtonFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Al abrir, el foco en el primer botón desplaza la vista hacia abajo y
    // recorta la cabecera. Devolvemos el scroll al inicio tras asentarse el foco.
    LaunchedEffect(Unit) {
        firstButtonFocus.requestFocus()
        withFrameNanos { }
        scrollState.scrollTo(0)
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
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    HeaderIconButton(
                        icon = Icons.Filled.Refresh,
                        label = "Actualizar",
                        onClick = onRefresh,
                    )
                    Spacer(Modifier.width(10.dp))
                    HeaderIconButton(
                        icon = Icons.Filled.Settings,
                        label = "Ajustes",
                        onClick = onSelectSettings,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LibraryStat(Icons.Filled.LiveTv, totalChannels, "canales", AppColors.mint)
                    LibraryStat(Icons.Outlined.Movie, totalMovies, "películas", AppColors.amber)
                    LibraryStat(Icons.Outlined.Tv, totalSeries, "series", AppColors.accent)
                }
                Spacer(Modifier.height(18.dp))
                if (expiryText != null && expiryColor != null) {
                    Text(
                        text = expiryText,
                        color = expiryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                }
                SearchActionButton(
                    query = draftQuery,
                    onClick = { showSearch = true },
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Explorar biblioteca",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    MenuCenterButton(
                        icon = Icons.Filled.LiveTv,
                        label = "Canales en Vivo",
                        accent = AppColors.mint,
                        onPressed = onSelectLive,
                        focusRequester = firstButtonFocus,
                        autofocus = true,
                    )
                    MenuCenterButton(
                        icon = Icons.Filled.Movie,
                        label = "Películas",
                        accent = AppColors.amber,
                        onPressed = onSelectMovies,
                    )
                    MenuCenterButton(
                        icon = Icons.Outlined.Tv,
                        label = "Series",
                        accent = AppColors.accent,
                        onPressed = onSelectSeries,
                    )
                    MenuCenterButton(
                        icon = Icons.Filled.PlayCircleOutline,
                        label = "Seguir viendo",
                        accent = AppColors.mint,
                        onPressed = onSelectContinueWatching,
                    )
                }
                if (recentlyAdded.isNotEmpty()) {
                    Spacer(Modifier.height(30.dp))
                    RecentlyAddedRow(items = recentlyAdded, onSelect = onSelectRecent)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Desplazamiento de "traer a vista" mínimo: 0 si ya es visible; si no, el
 *  menor margen necesario (arriba o abajo). */
@OptIn(ExperimentalFoundationApi::class)
private object MinimalBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        if (offset >= 0f && end <= containerSize) return 0f
        if (offset < 0f && end > containerSize) return 0f
        val up = -offset
        val down = end - containerSize
        return if (up <= down) offset else down
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
                width = if (focus.focused) 2.dp else 0.dp,
                color = if (focus.focused) AppColors.mint else Color.Transparent,
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
private fun RecentlyAddedRow(items: List<RecentItem>, onSelect: (RecentItem) -> Unit) {    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Añadido recientemente",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { "${it.type}:${it.id}" }) { item ->
                RecentCard(item = item, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, onSelect: (RecentItem) -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    val accent = if (item.type == "movie") AppColors.amber else AppColors.accent
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) accent.copy(alpha = 0.22f) else AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = { onSelect(item) })
            .tvFocusScale(focused, 1.06f)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            RemoteImage(
                url = item.logo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = item.title,
                fallbackType = if (item.type == "movie") PosterType.MOVIE else PosterType.SERIES,
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppColors.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (item.type == "movie") Icons.Filled.Movie else Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AppColors.badgeDark)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = if (item.type == "movie") "PELÍCULA" else "SERIE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        MarqueeText(
            text = item.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LibraryStat(icon: ImageVector, value: Int, label: String, color: Color) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "$value", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700)
            Spacer(Modifier.width(5.dp))
            Text(text = label, color = faintTextColor())
        }
    }
}

@Composable
private fun SearchActionButton(query: String, onClick: () -> Unit) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (focused) AppColors.accent else AppColors.panel,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            )
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, onTap = onClick)
            .tvFocusScale(focused, 1.015f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = if (focused) Color.White else AppColors.mint,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = query.ifEmpty { "Buscar en todo el contenido" },
            color = if (query.isEmpty()) subtleTextColor() else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuCenterButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onPressed: () -> Unit,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
) {
    val focus = rememberTvFocus()
    val focused = focus.focused
    Column(
        modifier = Modifier
            .size(width = 176.dp, height = 148.dp)
            .shadow(if (focused) 22.dp else 0.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) accent.copy(alpha = 0.25f) else AppColors.panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = focus.interaction)
            .tvPress(fireOnDown = true, autofocus = autofocus, onTap = onPressed)
            .tvFocusScale(focused, 1.05f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LaunchedEffect(autofocus) {
            if (autofocus) focusRequester?.requestFocus()
        }
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (focused) accent.copy(alpha = 0.30f) else accent.copy(alpha = 0.13f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (focused) Color.White else accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.panel,
        title = { Text("Buscar en tu biblioteca", color = Color.White) },
        text = {
            Column {
                Text(
                    "Escribe lo que buscas o pulsa abajo para acceder a las acciones.",
                    color = faintTextColor(),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Canal, película o serie") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.mint) },
                    singleLine = true,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            ) { Text("Cancelar", color = Color.White) }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = query.trim()
                    if (q.isNotEmpty()) onSubmit(q) else onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AppColors.mint,
                ),
            ) {
                Text("Buscar", fontWeight = FontWeight.W700)
            }
        },
    )
}
