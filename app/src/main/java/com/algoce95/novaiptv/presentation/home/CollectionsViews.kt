package com.algoce95.novaiptv.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algoce95.novaiptv.presentation.tv.EmptyState
import com.algoce95.novaiptv.presentation.tv.NovaButton
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.TvFocusShape
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.WatchProgress
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.presentation.tv.MarqueeText
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.presentation.tv.requestFocusReady
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.launch

/** Paridad con la vista `ActiveView.favorites` (rejilla + reordenado). */
@Composable
fun FavoritesView(
    items: List<LiveChannel>,
    onTap: (LiveChannel) -> Unit,
    onLongPress: (LiveChannel) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    focusRequesters: MutableMap<Any, FocusRequester>,
    focusSignal: Pair<Any?, Int>?,
    client: com.algoce95.novaiptv.data.api.XtreamApiClient,
    density: Int,
    cardScale: Float,
) {
    GridContentView(
        items = items,
        itemKey = { it.channelId },
        columns = 0,
        itemContent = { LiveChannelCard(channel = it, client = client) },
        onTap = onTap,
        onLongPress = onLongPress,
        onReorder = onReorder,
        showReorderControls = true,
        onMoveItem = onMoveItem,
        focusRequesters = focusRequesters,
        focusSignal = focusSignal,
        density = density,
        cardScale = cardScale,
    )
}

/** Paridad con la vista `ActiveView.history`. */
@Composable
fun HistoryView(
    entries: List<HistoryEntry>,
    onTap: (HistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "Tu historial está vacío",
            hint = "Cuando reproduzcas algo, aparecerá aquí.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    GridContentView(
        items = entries,
        itemKey = { "${it.type}:${it.id}" },
        columns = 0,
        itemContent = { HistoryCard(entry = it) },
        onTap = onTap,
    )
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            RemoteImage(
                url = entry.logo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = entry.title,
                fallbackType = if (entry.type == "movie") PosterType.MOVIE else PosterType.SERIES,
                error = {
                    Icon(
                        if (entry.type == "movie") Icons.Filled.Movie else Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = AppColors.amber,
                    )
                },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.badgeDark)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (entry.type == "movie") "PELÍCULA" else "SERIE",
                    color = bodyTextColor(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
        MarqueeText(
            text = entry.title,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
        )
    }
}

/**
 * "Seguir viendo" en dos carruseles, series y películas. El almacén guarda lo
 * más reciente al frente, así que el extremo izquierdo de cada fila es lo
 * último que se ha estado viendo.
 */
@Composable
fun ContinueWatchingView(
    items: List<WatchProgress>,
    imageFor: (WatchProgress) -> String,
    onTap: (WatchProgress) -> Unit,
    onLongPress: (WatchProgress) -> Unit,
    onColorKey: ((Int) -> Boolean)? = null,
) {
    val series = items.filterNot { it.id.startsWith("movie:") }
    val movies = items.filter { it.id.startsWith("movie:") }
    val seriesState = rememberLazyListState()
    val moviesState = rememberLazyListState()
    val rows = buildList {
        if (series.isNotEmpty()) add(Triple("Series", series, seriesState))
        if (movies.isNotEmpty()) add(Triple("Películas", movies, moviesState))
    }

    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "Nada para continuar",
            hint = "Empieza una película o un episodio y aparecerá aquí.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val nodes = remember { mutableStateMapOf<String, FocusRequester>() }
    val scope = rememberCoroutineScope()

    // Movimiento explícito: la escala de foco infla los límites del ítem y
    // Compose descarta al vecino en la búsqueda lateral; en vertical, además,
    // el nodo destino puede estar fuera del carrusel y hay que desplazarlo.
    fun focusCell(row: Int, index: Int) {
        val target = rows.getOrNull(row) ?: return
        val list = target.second
        val pos = index.coerceIn(0, list.lastIndex)
        val id = list[pos].id
        scope.launch {
            target.third.scrollToItem(pos)
            nodes.getOrPut(id) { FocusRequester() }.requestFocusReady()
        }
    }

    // Al entrar y tras quitar un progreso (pulsación larga), el foco vuelve a
    // la primera tarjeta: si no, se queda huérfano con la tarjeta borrada.
    LaunchedEffect(items) { focusCell(0, 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.isKeyDown() && onColorKey?.invoke(event.nativeKeyEvent.keyCode) == true
            },
    ) {
        rows.forEachIndexed { rowIndex, row ->
            ContinueRow(
                title = row.first,
                items = row.second,
                state = row.third,
                nodes = nodes,
                imageFor = imageFor,
                onTap = onTap,
                onLongPress = onLongPress,
                onMoveTo = ::focusCell,
                rowIndex = rowIndex,
                isLastRow = rowIndex == rows.lastIndex,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ContinueRow(
    title: String,
    items: List<WatchProgress>,
    state: LazyListState,
    nodes: MutableMap<String, FocusRequester>,
    imageFor: (WatchProgress) -> String,
    onTap: (WatchProgress) -> Unit,
    onLongPress: (WatchProgress) -> Unit,
    onMoveTo: (Int, Int) -> Unit,
    rowIndex: Int,
    isLastRow: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cardWidth = maxHeight * 0.66f
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, progress ->
                    val focus = rememberTvFocus()
                    val node = nodes.getOrPut(progress.id) { FocusRequester() }
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .fillMaxHeight()
                            .focusRequester(node)
                            .focusable(interactionSource = focus.interaction)
                            .tvPress(
                                onTap = { onTap(progress) },
                                onLongPress = { onLongPress(progress) },
                            )
                            .onPreviewKeyEvent { event ->
                                if (!event.isKeyDown()) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> if (index > 0) {
                                        onMoveTo(rowIndex, index - 1); true
                                    } else {
                                        false
                                    }
                                    Key.DirectionRight -> if (index < items.lastIndex) {
                                        onMoveTo(rowIndex, index + 1); true
                                    } else {
                                        false
                                    }
                                    Key.DirectionUp -> if (rowIndex > 0) {
                                        onMoveTo(rowIndex - 1, index); true
                                    } else {
                                        false
                                    }
                                    Key.DirectionDown -> if (!isLastRow) {
                                        onMoveTo(rowIndex + 1, index); true
                                    } else {
                                        false
                                    }
                                    else -> false
                                }
                            },
                    ) {
                        ContinueWatchingCard(
                            progress = progress,
                            imageUrl = imageFor(progress),
                            focused = focus.focused,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    progress: WatchProgress,
    imageUrl: String,
    focused: Boolean,
) {
    val done = progress.fraction >= .95
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) AppColors.mint else Color.White.copy(alpha = 0.08f),
        ),
        shape = TvFocusShape,
        modifier = Modifier
            .fillMaxSize()
            .tvFocusScale(focused),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // El póster ocupa lo que dejan los textos: en el carrusel manda la
            // altura de la fila, así que el recorte es preferible a desbordar.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                    error = {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = subtleTextColor(),
                            modifier = Modifier.size(52.dp),
                        )
                    },
                )
            }
        MarqueeText(
            text = progress.title,
            style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (done) {
                    "Completado · ver siguiente"
                } else {
                    val mins = progress.positionMs / 60_000
                    val total = progress.durationMs / 60_000
                    "$mins min de $total min"
                },
                color = if (done) AppColors.mint else subtleTextColor(),
                fontSize = 11.sp,
                fontWeight = if (done) FontWeight.W700 else FontWeight.Normal,
            )
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (done) AppColors.mint else AppColors.accent,
                trackColor = Color.White.copy(alpha = 0.10f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** Fila de error con reintento (paridad con el estado de error del inicio). */
@Composable
fun CatalogErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = AppColors.expired,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = subtleTextColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(20.dp))
        NovaButton(label = "Reintentar", icon = Icons.Filled.Refresh, onClick = onRetry)
    }
}
