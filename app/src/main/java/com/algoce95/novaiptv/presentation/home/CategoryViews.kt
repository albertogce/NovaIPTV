package com.algoce95.novaiptv.presentation.home

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.bodyTextColor
import com.algoce95.novaiptv.core.theme.faintTextColor
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.api.XtreamApiClient
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.LiveCategory
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.presentation.tv.EmptyState
import com.algoce95.novaiptv.presentation.tv.MarqueeText
import com.algoce95.novaiptv.presentation.tv.NovaButton
import com.algoce95.novaiptv.presentation.tv.RemoteImage
import com.algoce95.novaiptv.presentation.tv.ShimmerBox
import com.algoce95.novaiptv.presentation.tv.TvFocusShape
import com.algoce95.novaiptv.presentation.tv.TvKeys
import com.algoce95.novaiptv.presentation.tv.isKeyDown
import com.algoce95.novaiptv.presentation.tv.requestFocusReady
import com.algoce95.novaiptv.presentation.tv.rememberTvFocus
import com.algoce95.novaiptv.presentation.tv.tvFocusItem
import com.algoce95.novaiptv.presentation.tv.tvFocusScale
import com.algoce95.novaiptv.presentation.tv.tvPress
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableLongStateOf
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.playback.LivePreview
import com.algoce95.novaiptv.core.playback.bindSharedSurface

/** Columnas según ancho con el ajuste de densidad (paridad con GridViewContentView). */
fun gridColumns(density: Int, widthDp: Int): Int {
    val base = when {
        widthDp >= 1200 -> 6
        widthDp >= 800 -> 5
        widthDp >= 600 -> 4
        else -> 2
    }
    return when (density) {
        1 -> if (base > 2) base - 1 else 2
        2 -> base + 1
        else -> base
    }
}

/**
 * Vista de categorías laterales + buscador + rejilla (paridad con
 * `CategoryContentView`; siempre en modo lateral como la app actual).
 */
@Composable
fun <C, T> CategoryContentView(
    categories: List<C>,
    selectedCategoryId: String?,
    getCatId: (C) -> String,
    getCatName: (C) -> String,
    items: List<T>,
    itemKey: (T) -> Any,
    onSelectCategory: (String?) -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onBack: () -> Unit,
    headerActions: @Composable RowScope.() -> Unit = {},
    itemContent: @Composable (T) -> Unit,
    onTap: (T) -> Unit,
    onLongPress: ((T) -> Unit)? = null,
    onReorder: ((Int, Int) -> Unit)? = null,
    showReorderControls: Boolean = false,
    onMoveItem: ((Int, Int) -> Unit)? = null,
    focusRequesters: MutableMap<Any, FocusRequester>? = null,
    focusSignal: Pair<Any?, Int>? = null,
    density: Int = 0,
    cardScale: Float = 1f,
) {
    val searchFocus = remember { FocusRequester() }
    val firstItemFocus = remember(selectedCategoryId) { FocusRequester() }
    val sidebarNodes = remember { mutableStateMapOf<String, FocusRequester>() }
    val sidebarState = rememberLazyListState()
    val sidebarScope = rememberCoroutineScope()
    var sidebarFocusedId by remember { mutableStateOf<String?>(null) }
    // Mientras se escribe en el buscador, la rejilla no debe robar el foco.
    val searchFocused = remember { mutableStateOf(false) }

    fun entryId(): String? = selectedCategoryId
        ?: categories.firstOrNull()?.let(getCatId)

    fun focusSelectedSidebar() {
        val target = entryId()
        sidebarFocusedId = target
        target?.let { id ->
            sidebarScope.launch {
                val index = categories.indexOfFirst { getCatId(it) == id }
                if (index >= 0) sidebarState.scrollToItem(index)
                sidebarNodes.getOrPut(id) { FocusRequester() }.requestFocusReady()
            }
        }
    }

    fun focusFirstItem() {
        val first = items.firstOrNull() ?: return
        val node = focusRequesters?.getOrPut(itemKey(first)) { FocusRequester() } ?: firstItemFocus
        sidebarScope.launch { node.requestFocusReady() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppColors.ink, AppColors.contentGradEnd),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = searchQuery,
                onQueryChanged = onSearchChanged,
                focusRequester = searchFocus,
                onBack = onBack,
                onFocusedChange = { searchFocused.value = it },
                onArrowDown = {
                    if (items.isNotEmpty()) {
                        focusFirstItem()
                    } else {
                        focusSelectedSidebar()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            headerActions()
        }
        Row(modifier = Modifier.fillMaxSize()) {
            CategorySidebar(
                categories = categories,
                state = sidebarState,
                selectedCategoryId = selectedCategoryId,
                getCatId = getCatId,
                getCatName = getCatName,
                onSelectCategory = {
                    onSelectCategory(it)
                    focusFirstItem()
                },
                focusNodeFor = { id -> sidebarNodes.getOrPut(id) { FocusRequester() } },
                focusedId = sidebarFocusedId,
                onFocused = { sidebarFocusedId = it },
                onExitRight = {
                    sidebarFocusedId = null
                    focusFirstItem()
                },
            )
                Box(modifier = Modifier.weight(1f)) {
                GridContentView(
                    items = items,
                    itemKey = itemKey,
                    columns = 0, // se recalcula dentro con el ancho real
                    itemContent = itemContent,
                    onTap = onTap,
                    onLongPress = onLongPress,
                    onReorder = onReorder,
                    showReorderControls = showReorderControls,
                    onMoveItem = onMoveItem,
                    focusRequesters = focusRequesters,
                    focusSignal = focusSignal,
                    density = density,
                    cardScale = cardScale,
                    firstItemFocus = firstItemFocus,
                    onAtLeftEdge = ::focusSelectedSidebar,
                    skipAutoFocus = { searchFocused.value },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onArrowDown: () -> Unit,
    onFocusedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text("Buscar...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.mint, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Limpiar", tint = subtleTextColor(), modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = AppColors.panel,
            unfocusedContainerColor = AppColors.panel,
            focusedBorderColor = AppColors.accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = AppColors.mint,
            cursorColor = AppColors.mint,
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = subtleTextColor(),
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusEvent { onFocusedChange(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (!event.isKeyDown()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.DirectionDown -> {
                        onArrowDown()
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionRight -> {
                        onArrowDown()
                        true
                    }
                    else -> false
                }
            },
    )
}

@Composable
private fun <C> CategorySidebar(
    categories: List<C>,
    state: androidx.compose.foundation.lazy.LazyListState,
    selectedCategoryId: String?,
    getCatId: (C) -> String,
    getCatName: (C) -> String,
    onSelectCategory: (String?) -> Unit,
    focusNodeFor: (String) -> FocusRequester,
    focusedId: String?,
    onFocused: (String) -> Unit,
    onExitRight: () -> Unit,
) {
    val width = if (LocalConfiguration.current.screenWidthDp < 700) 148.dp else 210.dp
    LazyColumn(
        state = state,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 6.dp, bottom = 20.dp, end = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.panel.copy(alpha = 0.92f)),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(categories, key = { getCatId(it) }) { category ->
            val id = getCatId(category)
            val selected = id == selectedCategoryId
            val focus = rememberTvFocus()
            val hasFocus = focusedId == id || focus.focused
            if (focus.focused) {
                LaunchedEffect(id) { onFocused(id) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            hasFocus -> AppColors.mint.copy(alpha = 0.16f)
                            selected -> AppColors.accent.copy(alpha = 0.14f)
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        width = if (hasFocus) 2.dp else 1.dp,
                        color = when {
                            hasFocus -> AppColors.mint
                            selected -> AppColors.accent.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .focusRequester(focusNodeFor(id))
                    .focusable(interactionSource = focus.interaction)
                    .onPreviewKeyEvent { event ->
                        if (event.isKeyDown()) {
                            when (event.key) {
                                Key.DirectionRight -> {
                                    onExitRight()
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    onSelectCategory(id)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .tvPress(fireOnDown = true, onTap = { onSelectCategory(id) })
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Label,
                    contentDescription = null,
                    tint = when {
                        hasFocus -> AppColors.mint
                        selected -> AppColors.salmon
                        else -> AppColors.mutedLabel
                    },
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = getCatName(category),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (hasFocus) Color.White else if (selected) AppColors.dimText else bodyTextColor(),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Rejilla con densidad, escala de texto, bordes de navegación y reordenado
 * (paridad con `GridViewContentView` + `_GridItem`).
 */
@Composable
fun <T> GridContentView(
    items: List<T>,
    itemKey: (T) -> Any,
    columns: Int,
    itemContent: @Composable (T) -> Unit,
    onTap: (T) -> Unit,
    onLongPress: ((T) -> Unit)? = null,
    onReorder: ((Int, Int) -> Unit)? = null,
    showReorderControls: Boolean = false,
    onMoveItem: ((Int, Int) -> Unit)? = null,
    focusRequesters: MutableMap<Any, FocusRequester>? = null,
    focusSignal: Pair<Any?, Int>? = null,
    density: Int = 0,
    cardScale: Float = 1f,
    firstItemFocus: FocusRequester? = null,
    onAtLeftEdge: (() -> Unit)? = null,
    onAtRightEdge: (() -> Unit)? = null,
    onColorKey: ((Int) -> Boolean)? = null,
    skipAutoFocus: () -> Boolean = { false },
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val autoFocus = remember { FocusRequester() }
    // Un nodo por posición: la escala de foco agranda los límites del ítem y
    // Compose descarta al vecino en la búsqueda lateral, así que izquierda y
    // derecha se mueven a mano (como en la rejilla de episodios).
    val localNodes = remember { mutableStateMapOf<Any, FocusRequester>() }

    fun focusNeighbor(from: Int, to: Int) {
        val target = items.getOrNull(to) ?: return
        scope.launch {
            localNodes.getOrPut(itemKey(target)) { FocusRequester() }.requestFocusReady()
        }
    }

    // En favoritos cada tarjeta usa su propio FocusRequester. No se puede
    // pedir el autofocus genérico porque no está asociado a ningún Card en
    // ese modo y Compose lo reporta como no inicializado.
    LaunchedEffect(items, focusRequesters) {
        if (items.isEmpty()) return@LaunchedEffect
        // Con el foco en el buscador, cada tecla cambia `items` y este efecto
        // arrastraría el foco a la primera tarjeta: se omite el autofocus.
        if (skipAutoFocus()) return@LaunchedEffect
        if (focusRequesters != null) {
            focusRequesters[itemKey(items.first())]?.requestFocusReady()
        } else {
            // Con focusRequesters nulo la primera tarjeta se adjunta a
            // firstItemFocus (o a autoFocus); pedir el autoFocus a secas
            // apuntaba a un nodo inexistente y el foco caía en el buscador.
            (firstItemFocus ?: autoFocus).requestFocusReady()
        }
    }

    LaunchedEffect(focusSignal) {
        val key = focusSignal?.first
        if (key != null && focusRequesters != null) {
            val index = items.indexOfFirst { itemKey(it) == key }
            if (index >= 0) {
                gridState.scrollToItem((index / columns.coerceAtLeast(1)) * columns.coerceAtLeast(1))
                focusRequesters[key]?.requestFocusReady()
            }
        }
    }

    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = "No hay contenido disponible",
            hint = "Prueba con otra categoría o revisa el texto de búsqueda.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Ajuste "Tamaño de tarjetas": escala solo el texto.
    val scaledDensity = LocalDensity.current.let {
        Density(it.density, fontScale = it.fontScale * cardScale)
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cols = if (columns > 0) {
                columns
            } else {
                gridColumns(density, maxWidth.value.toInt())
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 10.dp,
                    end = 12.dp,
                    bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        event.isKeyDown() && onColorKey?.invoke(event.nativeKeyEvent.keyCode) == true
                    },
            ) {
                gridItemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                    val isFirstItem = index == 0
                    val isLeftEdge = index % cols == 0
                    val isRightEdge = index % cols == cols - 1 || index == items.lastIndex
                    val isBottomEdge = index + cols >= items.size
                    val focus = rememberTvFocus()
                    val focused = focus.focused
                    // Un solo FocusRequester por item: el del mapa, el del
                    // primer item, o el autofocus local como último recurso.
                    val node = focusRequesters?.getOrPut(itemKey(item)) { FocusRequester() }
                        ?: if (isFirstItem) firstItemFocus ?: autoFocus else null
                    val localNode = localNodes.getOrPut(itemKey(item)) { FocusRequester() }
                    // Tarjeta de rejilla con el patrón TV unificado: relleno
                    // panel + borde de acento + escala animada. Sustituye al
                    // Card de Material (su relleno sólido de acento competía
                    // con el contenido y no escalaba al enfocar).
                    Box(
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier
                            .clip(TvFocusShape)
                            .background(AppColors.panel)
                            .then(if (node != null) Modifier.focusRequester(node) else Modifier)
                            .focusRequester(localNode)
                            .focusable(interactionSource = focus.interaction)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), TvFocusShape)
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) AppColors.mint else Color.Transparent,
                                shape = TvFocusShape,
                            )
                            .then(if (focused) Modifier.shadow(14.dp, TvFocusShape) else Modifier)
                            .tvPress(
                                autofocus = isFirstItem,
                                ignoreInitialSelect = true,
                                onTap = { onTap(item) },
                                onLongPress = onLongPress?.let { { it(item) } },
                            )
                            .onPreviewKeyEvent { event ->
                                if (!event.isKeyDown()) return@onPreviewKeyEvent false
                                val native = event.nativeKeyEvent
                                if (native.isShiftPressed || native.isCtrlPressed) {
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (index > 0) onReorder?.invoke(index, index - 1)
                                            return@onPreviewKeyEvent onReorder != null
                                        }
                                        Key.DirectionRight -> {
                                            if (index < items.lastIndex) onReorder?.invoke(index, index + 2)
                                            return@onPreviewKeyEvent onReorder != null
                                        }
                                    }
                                }
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (isLeftEdge) {
                                            onAtLeftEdge?.invoke()
                                            onAtLeftEdge != null
                                        } else {
                                            focusNeighbor(index, index - 1)
                                            true
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (isRightEdge) {
                                            onAtRightEdge?.invoke()
                                            onAtRightEdge != null
                                        } else {
                                            focusNeighbor(index, index + 1)
                                            true
                                        }
                                    }
                                    Key.DirectionDown -> isBottomEdge
                                    Key.ChannelUp, Key.PageUp -> {
                                        if (showReorderControls) {
                                            onMoveItem?.invoke(index, -1)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    Key.ChannelDown, Key.PageDown -> {
                                        if (showReorderControls) {
                                            onMoveItem?.invoke(index, 1)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                            .tvFocusScale(focused),
                    ) {
                        Box {
                            itemContent(item)
                            if (showReorderControls) {
                                Icon(
                                    Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .align(Alignment.TopStart),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Navegación TV optimizada para mando: categorías, canales y preview persistente. */
@OptIn(UnstableApi::class)
@Composable
fun LiveChannelBrowser(
    categories: List<LiveCategory>,
    selectedCategoryId: String?,
    items: List<LiveChannel>,
    onSelectCategory: (String?) -> Unit,
    onBack: () -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onOpenEpg: () -> Unit,
    client: XtreamApiClient,
    onPlay: (LiveChannel, List<LiveChannel>) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    favoriteChannels: List<LiveChannel>,
) {
    val channelFocuses = remember { mutableStateMapOf<Int, FocusRequester>() }
    val categoryFocus = remember { mutableStateMapOf<String, FocusRequester>() }
    var sidebarFocusedId by remember { mutableStateOf<String?>(null) }
    val focusScope = rememberCoroutineScope()
    // Estados hoisteados: antes de pedir el foco hay que asegurar que el nodo
    // está compuesto, y en una lista perezosa eso exige poder desplazarla.
    val channelListState = rememberLazyListState()
    val sidebarListState = rememberLazyListState()

    val context = LocalContext.current
    // El id previsualizado sobrevive a la navegación: al abrir la pantalla
    // completa se toma el reproductor de LivePreview y al volver se reanuda.
    var previewedId by remember { mutableStateOf(LivePreview.channelId) }
    val selected = remember(previewedId, items) {
        items.firstOrNull { it.channelId == previewedId }
            ?: LivePreview.channel?.takeIf { it.channelId == previewedId }
    }
    val exo = remember { LivePreview.player(context) }

    // Una sola conexión: el preview también debe cortar la red en segundo plano.
    DisposableEffect(Unit) {
        val onBackground = { LivePreview.onBackground() }
        val onForeground = { LivePreview.onForeground() }
        AppContainer.onAppBackgrounded = onBackground
        AppContainer.onAppForegrounded = onForeground
        onDispose {
            if (AppContainer.onAppBackgrounded === onBackground) AppContainer.onAppBackgrounded = null
            if (AppContainer.onAppForegrounded === onForeground) AppContainer.onAppForegrounded = null
            // El reproductor no se libera si la pantalla completa se lo llevó.
            LivePreview.releaseIfUnused(exo)
        }
    }

    LaunchedEffect(previewedId, selected) {
        val ch = selected
        when {
            // Volviendo de la pantalla completa el catálogo aún puede estar
            // cargando: el canal se resolverá cuando llegue la lista.
            ch == null && previewedId != null && items.isEmpty() -> Unit
            ch == null -> LivePreview.stop()
            else -> LivePreview.play(context, ch, client.liveStreamUrl(ch.channelId))
        }
    }

    val requestChannelFocus: () -> Unit = {
        sidebarFocusedId = null
        focusScope.launch {
            val firstChannel = items.firstOrNull() ?: return@launch
            channelListState.scrollToItem(0)
            channelFocuses.getOrPut(firstChannel.channelId) { FocusRequester() }
                .requestFocusReady()
        }
    }

    LaunchedEffect(selectedCategoryId) {
        // Volviendo de la pantalla completa se conserva el canal previsualizado.
        if (LivePreview.consumeRestore()) return@LaunchedEffect
        previewedId = null
        if (items.isNotEmpty() && sidebarFocusedId == null) {
            requestChannelFocus()
        }
    }

    val requestCategoryFocus: () -> Unit = {
        val targetId = selectedCategoryId ?: categories.firstOrNull()?.categoryId
        if (targetId != null) {
            sidebarFocusedId = targetId
            focusScope.launch {
                val index = categories.indexOfFirst { it.categoryId == targetId }
                if (index >= 0) sidebarListState.scrollToItem(index)
                categoryFocus.getOrPut(targetId) { FocusRequester() }.requestFocusReady()
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(AppColors.ink, AppColors.contentGradEnd)),
            ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(searchQuery, onSearchChanged, remember { FocusRequester() }, onBack, requestCategoryFocus, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            NovaButton(
                label = "EPG completo",
                icon = Icons.Filled.CalendarMonth,
                onClick = onOpenEpg,
            )
        }
        Row(Modifier.fillMaxSize()) {
        // Reutilizamos el estilo y navegación de la barra lateral existente.
        CategorySidebar(categories, sidebarListState, selectedCategoryId,
            { it.categoryId }, { it.categoryName },
            onSelectCategory = { id ->
                // Al elegir categoría desde el sidebar, el foco debe pasar al
                // primer canal; sin limpiar esto, el guard del efecto lo impide.
                sidebarFocusedId = null
                onSelectCategory(id)
            },
            focusNodeFor = { id -> categoryFocus.getOrPut(id) { FocusRequester() } },
            focusedId = sidebarFocusedId,
            onFocused = { sidebarFocusedId = it },
            onExitRight = {
                requestChannelFocus()
            }
        )
        val screenW = LocalConfiguration.current.screenWidthDp
        val channelColWidth = when {
            screenW >= 1280 -> 340.dp
            screenW >= 960 -> 300.dp
            else -> 260.dp
        }
        Column(Modifier.width(channelColWidth).fillMaxHeight().padding(8.dp)) {
            val catName = categories.firstOrNull { it.categoryId == selectedCategoryId }?.categoryName
                ?: categories.firstOrNull()?.categoryName
                ?: "Canales"
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = catName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${items.size} canales", color = subtleTextColor(), fontSize = 11.sp)
            }
            LazyColumn(state = channelListState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(items, key = { _, it -> it.channelId }) { index, channel ->
                val focus = rememberTvFocus()
                val hasFocus = focus.focused
                val active = selected?.channelId == channel.channelId
                val node = channelFocuses.getOrPut(channel.channelId) { FocusRequester() }
                Row(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            hasFocus -> AppColors.mint.copy(alpha = 0.18f)
                            active -> AppColors.accent.copy(alpha = 0.22f)
                            else -> AppColors.panel
                        }
                    )
                    .border(if (hasFocus) 2.dp else 1.dp, if (hasFocus) AppColors.mint else Color.Transparent, RoundedCornerShape(10.dp))
                    .focusRequester(node)
                    .focusable(interactionSource = focus.interaction)
                    .tvPress(
                        autofocus = index == 0,
                        ignoreInitialSelect = true,
                        fireOnDown = true,
                        onTap = {
                            if (active) {
                                // Ampliar sin reiniciar: autoriza que la pantalla
                                // completa use este mismo reproductor.
                                LivePreview.armHandoff()
                                onPlay(channel, items)
                            } else {
                                previewedId = channel.channelId
                            }
                        },
                        onLongPress = { onToggleFavorite(channel) }
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.isKeyDown()) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    requestCategoryFocus()
                                    true
                                }
                                Key.ChannelUp, Key.PageUp -> {
                                    onMoveItem(index, -1)
                                    true
                                }
                                Key.ChannelDown, Key.PageDown -> {
                                    onMoveItem(index, 1)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .tvFocusScale(hasFocus, 1.03f)
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(channel.channelLogo, Modifier.size(58.dp), ContentScale.Fit, error = { Icon(Icons.Filled.LiveTv, null, tint = bodyTextColor()) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        MarqueeText(
                            text = channel.channelName,
                            style = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Segunda línea: programa en emisión (viene en el listado, sin petición extra).
                        if (channel.currentEpgTitle.isNotEmpty()) {
                            Text(
                                text = channel.currentEpgTitle,
                                color = subtleTextColor(),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            }
        }
        val sel = selected
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            // La superficie existe desde el principio: si se monta al seleccionar,
            // el vídeo no tiene dónde renderizar en la primera previsualización.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            bindSharedSurface(exo)
                        }
                    },
                    update = { view -> view.player = exo },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                if (sel == null) {
                    Box(Modifier.fillMaxSize().background(AppColors.ink), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.panel.copy(alpha = 0.92f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.PlayCircleOutline,
                                contentDescription = null,
                                tint = AppColors.mint,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Pulsa Enter sobre un canal para previsualizarlo",
                                color = subtleTextColor(),
                                fontSize = 13.sp,
                            )
                        }
                    }
                } else {
                    // Cabecera del canal integrada sobre el vídeo: logo + nombre
                    // + categoría + estado en directo, bajo velo degradado.
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, AppColors.scrimBottom)),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RemoteImage(sel.channelLogo, Modifier.size(34.dp), ContentScale.Fit, error = { })
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                sel.channelName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                sel.groupTitle,
                                color = AppColors.mint,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(AppColors.dotGreen))
                            Spacer(Modifier.width(5.dp))
                            Text("EN DIRECTO", color = AppColors.dotGreen, fontSize = 10.sp, fontWeight = FontWeight.W700)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (sel != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectedChannelEpg(sel, client)
                }
            } else {
                LiveStartSuggestions(
                    favorites = favoriteChannels,
                    onSelect = { previewedId = it.channelId },
                )
            }
        }
        }
    }
}

/**
 * Hueco inferior de la columna derecha sin canal activo: atajo a los
 * favoritos del usuario (nada si no tiene).
 */
@Composable
private fun LiveStartSuggestions(
    favorites: List<LiveChannel>,
    onSelect: (LiveChannel) -> Unit,
) {
    val pool = favorites.distinctBy { it.channelId }.take(8)
    if (pool.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Tus favoritos",
            color = AppColors.mint,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pool, key = { _, ch -> ch.channelId }) { _, channel ->
                val focus = rememberTvFocus()
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focus.focused) AppColors.mint.copy(alpha = 0.16f) else AppColors.panel)
                        .border(2.dp, if (focus.focused) AppColors.mint else Color.Transparent, RoundedCornerShape(10.dp))
                        .focusable(interactionSource = focus.interaction)
                        .tvPress(fireOnDown = true, onTap = { onSelect(channel) })
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(channel.channelLogo, Modifier.size(34.dp), ContentScale.Fit, error = { Icon(Icons.Filled.LiveTv, null, tint = bodyTextColor(), modifier = Modifier.size(20.dp)) })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        channel.channelName,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedChannelEpg(channel: LiveChannel, client: XtreamApiClient) {
    var programs by remember(channel.channelId) { mutableStateOf(emptyList<com.algoce95.novaiptv.data.model.EpgProgram>()) }
    // Reloj para la barra de progreso del programa en emisión.
    var now by remember(channel.channelId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(channel.channelId) {
        programs = runCatching { client.getShortEpgPrograms(channel.channelId) }.getOrDefault(emptyList())
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val currentIndex = programs.indexOfFirst { it.isLive() }
    val current = if (currentIndex >= 0) programs[currentIndex] else null
    val upcoming = programs.filterIndexed { i, _ -> i != currentIndex }.take(4)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.panel.copy(alpha = .86f))
            .padding(14.dp),
    ) {
        Text("AHORA", color = AppColors.mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (current != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AppColors.epgLive)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${current.startMs.epgHM()} - ${current.endMs.epgHM()}",
                        color = AppColors.amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(92.dp),
                    )
                    Text(
                        current.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                val span = (current.endMs - current.startMs).coerceAtLeast(1L)
                val frac = ((now - current.startMs).toDouble() / span).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AppColors.dotGreen,
                    trackColor = Color.White.copy(alpha = .12f),
                )
            }
        } else {
            Text(
                if (programs.isEmpty()) "Sin guía EPG disponible" else "Nada en emisión ahora",
                color = subtleTextColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (upcoming.isNotEmpty()) {
            Text(
                "A CONTINUACIÓN",
                color = subtleTextColor(),
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            upcoming.forEach { program ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        program.startMs.epgHM(),
                        color = AppColors.amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(92.dp),
                    )
                    Text(
                        program.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun Long.epgHM(): String {
    val calendar = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = this@epgHM }
    return "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
}

/** Tarjeta de canal con EPG breve (paridad con `LiveChannelCard`). */
@Composable
fun LiveChannelCard(channel: LiveChannel, client: XtreamApiClient) {
    var epgTitle by remember(channel.channelId) {
        mutableStateOf(channel.currentEpgTitle.ifEmpty { null })
    }
    var loadingEpg by remember(channel.channelId) { mutableStateOf(false) }

    LaunchedEffect(channel.channelId) {
        if (channel.channelId <= 0) return@LaunchedEffect
        loadingEpg = true
        try {
            val programs = client.getShortEpgPrograms(channel.channelId)
            if (programs.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val title = (programs.firstOrNull { it.isLive(now) } ?: programs.first()).title
                if (title.isNotEmpty()) epgTitle = title
            }
        } catch (_: Exception) {
            // Se conserva el título del listado si la consulta falla.
        } finally {
            loadingEpg = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.cardGradStart, AppColors.cardGradEnd),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = channel.channelLogo,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ShimmerBox(modifier = Modifier.fillMaxSize())
                    },
                    error = {
                        Icon(
                            Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = bodyTextColor(),
                            modifier = Modifier.size(38.dp),
                        )
                    },
                )
            }
            MarqueeText(
                text = channel.channelName,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loadingEpg) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = AppColors.amber,
                    )
                } else {
                    Text(
                        text = epgTitle?.ifEmpty { "Sin guía EPG" } ?: "Sin guía EPG",
                        color = AppColors.mint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (channel.isFavorite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = AppColors.amber,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(18.dp),
            )
        }
    }
}

/** Póster de película/serie (paridad con `_PosterContent`). */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String,
    icon: ImageVector,
    metadata: String,
    accent: Color,
    fallbackType: PosterType = PosterType.MOVIE,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
            RemoteImage(
                url = imageUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fallbackTitle = title,
                fallbackType = fallbackType,
                error = {
                    PosterFallback(icon = icon)
                },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.badgeScrim)
                    .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                Text(
                    text = metadata,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
        MarqueeText(
            text = title,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, top = 8.dp, bottom = 9.dp),
        )
    }
}

@Composable
private fun PosterFallback(icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.posterFallback),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = faintTextColor(), modifier = Modifier.size(42.dp))
    }
}
