package com.algoce95.novaiptv.presentation.home

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.PlayerSession
import com.algoce95.novaiptv.core.di.QueueItem
import com.algoce95.novaiptv.core.di.SearchSnapshot
import com.algoce95.novaiptv.core.di.vmFactory
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.core.theme.AppColors
import com.algoce95.novaiptv.core.theme.LocalHighContrast
import com.algoce95.novaiptv.core.theme.subtleTextColor
import com.algoce95.novaiptv.data.model.LiveCategory
import com.algoce95.novaiptv.data.metadata.PosterType
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.data.model.SeriesCategory
import com.algoce95.novaiptv.data.model.VodCategory
import com.algoce95.novaiptv.data.model.VodMovie
import com.algoce95.novaiptv.data.model.WatchProgress
import com.algoce95.novaiptv.presentation.nav.Routes
import com.algoce95.novaiptv.presentation.tv.TvKeys
import com.algoce95.novaiptv.presentation.tv.hideSystemBars
import com.algoce95.novaiptv.presentation.tv.showSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun LoadingTable(
    liveLoaded: Boolean,
    moviesLoaded: Boolean,
    seriesLoaded: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(top = 18.dp)
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        LoadingTableCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LiveTv,
            name = "Canales en vivo",
            loaded = liveLoaded,
        )
        LoadingTableCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Movie,
            name = "Películas",
            loaded = moviesLoaded,
        )
        LoadingTableCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Tv,
            name = "Series",
            loaded = seriesLoaded,
        )
    }
}

@Composable
private fun LoadingTableCell(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    loaded: Boolean,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.tileHeader)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.panel.copy(alpha = 0.92f))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loaded) {
                Text(
                    text = "Listo",
                    color = AppColors.mint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = AppColors.sky,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Cargando...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingSection(name: String, loaded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        if (loaded) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Listo",
                tint = AppColors.mint,
                modifier = Modifier.size(22.dp),
            )
        } else {
            CircularProgressIndicator(
                color = AppColors.sky,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = name,
            color = Color.White,
            modifier = Modifier.padding(start = 12.dp),
        )
        Text(
            text = if (loaded) "Listo" else "Cargando",
            color = if (loaded) AppColors.mint else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

/** Paridad con `HomeScreen` de Flutter (vistas, mando, favoritos, zapping). */
@SuppressLint("ContextCastToActivity")
@Composable
fun HomeScreen(navController: NavController) {
    val vm: HomeCatalogViewModel = viewModel(factory = vmFactory { HomeCatalogViewModel() })
    val state by vm.state.collectAsState()

    var activeView by rememberSaveable { mutableStateOf(ActiveView.HOME) }
    var liveCat by rememberSaveable { mutableStateOf<String?>(null) }
    var vodCat by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesCat by rememberSaveable { mutableStateOf<String?>(null) }
    var liveQuery by rememberSaveable { mutableStateOf("") }
    var movieQuery by rememberSaveable { mutableStateOf("") }
    var seriesQuery by rememberSaveable { mutableStateOf("") }
    var globalQuery by rememberSaveable { mutableStateOf("") }

    val favFocus = remember { mutableStateMapOf<Any, FocusRequester>() }
    var favFocusKey by remember { mutableStateOf<Any?>(null) }
    var favFocusTick by remember { mutableIntStateOf(0) }
    var progressList by remember { mutableStateOf(emptyList<WatchProgress>()) }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current as android.app.Activity


    // Paridad con SystemChrome de HomeScreen: inmersivo al entrar y salir.
    DisposableEffect(Unit) {
        hideSystemBars(activity)
        onDispose { showSystemBars(activity) }
    }

    fun loadProgress() {
        scope.launch { progressList = vm.continueWatching() }
    }

    LaunchedEffect(activeView) {
        vm.refreshDynamic()
        // Al salir de Películas o Series se reinicia su búsqueda: al volver
        // se entra con el contenido como la primera vez.
        if (activeView != ActiveView.MOVIES) movieQuery = ""
        if (activeView != ActiveView.SERIES) seriesQuery = ""
        if (activeView == ActiveView.CONTINUE_WATCHING || activeView == ActiveView.HOME) {
            loadProgress()
        }
    }

    // Refresco automático silencioso (equivale al Timer periódico). En segundo
    // plano no se pide nada para no dejar conexiones abiertas del panel.
    LaunchedEffect(Unit) {
        while (true) {
            val minutes = AppContainer.prefs
                .getString(PrefsStore.Keys.AUTO_REFRESH_MINUTES)?.toIntOrNull() ?: 0
            if (minutes > 0) {
                delay(minutes * 60_000L)
                if (AppContainer.inForeground) vm.silentRefresh()
            } else {
                delay(60_000L)
            }
        }
    }

    fun navigate(view: ActiveView) {
        activeView = view
    }

    fun openLiveChannels() {
        val hasFavorites = state.channels.any { it.isFavorite }
        liveCat = if (hasFavorites) {
            FAVORITES_CATEGORY_ID
        } else {
            state.liveCats.firstOrNull()?.categoryId
        }
        activeView = ActiveView.LIVE
    }

    fun playChannel(channel: LiveChannel, queue: List<LiveChannel>) {
        val items = queue.map {
            QueueItem(
                streamUrl = vm.client().liveStreamUrl(it.channelId),
                title = it.channelName,
                channelId = it.channelId,
            )
        }
        var index = queue.indexOfFirst { it.channelId == channel.channelId }
        if (index < 0) index = 0
        AppContainer.playerSession = PlayerSession(
            items = items,
            index = index,
            title = items[index].title,
            progressId = null,
            isLive = true,
        )
        navController.navigate(Routes.PLAYER)
    }

    fun playMovie(movie: VodMovie) {
        navController.navigate(Routes.movie(movie.toCacheJson().toString()))
    }

    fun playSeries(series: Series, initialEpisodeId: String? = null) {
        navController.navigate(Routes.series(series.toCacheJson().toString(), initialEpisodeId))
    }

    fun toggleFavorite(channel: LiveChannel) {
        scope.launch {
            val isFavNow = vm.toggleFavorite(channel)
            snackbar.showSnackbar(
                if (isFavNow) {
                    "Añadido a Favoritos: ${channel.channelName}"
                } else {
                    "Eliminado de Favoritos: ${channel.channelName}"
                },
            )
        }
    }

    fun reorderFavorites(oldIndex: Int, newIndex: Int) {
        scope.launch {
            val moved = vm.reorderFavorites(oldIndex, newIndex)
            if (moved != null) {
                favFocusKey = moved.channelId
                favFocusTick++
            }
        }
    }

    fun moveFavorite(index: Int, direction: Int, favorites: List<LiveChannel>) {
        val target = index + direction
        if (index < 0 || target < 0 || target >= favorites.size) return
        reorderFavorites(index, if (direction > 0) target + 1 else target)
    }

    fun handleColorKey(keyCode: Int): Boolean {
        val color = TvKeys.colorName(keyCode) ?: return false
        val action = state.colorActions.getOrNull(
            listOf("ROJO", "VERDE", "AMARILLO", "AZUL").indexOf(color),
        )
        when (action?.trim()?.lowercase()) {
            "favorites" -> navigate(ActiveView.FAVORITES)
            "live" -> openLiveChannels()
            "movies" -> navigate(ActiveView.MOVIES)
            "series" -> navigate(ActiveView.SERIES)
            "continuewatching", "continue_watching", "continue-watching" ->
                navigate(ActiveView.CONTINUE_WATCHING)
            "search" -> navigate(ActiveView.HOME)
            else -> return false
        }
        return true
    }

    DisposableEffect(Unit) {
        AppContainer.onColorKey = ::handleColorKey
        onDispose { AppContainer.onColorKey = null }
    }

    BackHandler(enabled = activeView != ActiveView.HOME) {
        activeView = ActiveView.HOME
    }

    CompositionLocalProvider(LocalHighContrast provides state.highContrast) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = AppColors.ink,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppColors.ink)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            handleColorKey(event.nativeKeyEvent.keyCode)
                        } else {
                            false
                        }
                    },
            ) {
                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.sky)
                            Text(
                                text = "Cargando información de IPTV",
                                color = Color.White,
                                modifier = Modifier.padding(top = 18.dp),
                            )
                            LoadingTable(
                                liveLoaded = state.liveContentLoaded,
                                moviesLoaded = state.moviesContentLoaded,
                                seriesLoaded = state.seriesContentLoaded,
                            )
                        }
                    }
                    state.error != null -> CatalogErrorView(
                        message = state.error!!,
                        onRetry = { vm.load(forceRefresh = true) },
                    )
                    else -> HomeBody(
                        activeView = activeView,
                        state = state,
                        vm = vm,
                        liveCat = liveCat,
                        vodCat = vodCat,
                        seriesCat = seriesCat,
                        liveQuery = liveQuery,
                        movieQuery = movieQuery,
                        seriesQuery = seriesQuery,
                        globalQuery = globalQuery,
                        progressList = progressList,
                        favFocus = favFocus,
                        favFocusKey = favFocusKey,
                        favFocusTick = favFocusTick,
                        onNavigate = ::navigate,
                        onOpenLive = ::openLiveChannels,
                        onLiveCat = { liveCat = it },
                        onVodCat = { vodCat = it },
                        onSeriesCat = { seriesCat = it },
                        onLiveQuery = { liveQuery = it },
                        onMovieQuery = { movieQuery = it },
                        onSeriesQuery = { seriesQuery = it },
                        onGlobalQuery = { globalQuery = it },
                        onPlayChannel = ::playChannel,
                        onPlayMovie = ::playMovie,
                        onPlaySeries = ::playSeries,
                        onToggleFavorite = ::toggleFavorite,
                        onReorderFavorites = ::reorderFavorites,
                        onMoveFavorite = ::moveFavorite,
                        onRemoveProgress = { progress ->
                            scope.launch {
                                vm.removeContinueWatching(progress.id)
                                loadProgress()
                            }
                            scope.launch {
                                snackbar.showSnackbar("Eliminado de Seguir viendo: ${progress.title}")
                            }
                        },
                        onLoadProgress = ::loadProgress,
                        onColorKey = ::handleColorKey,
                        navController = navController,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBody(
    activeView: ActiveView,
    state: HomeCatalogViewModel.UiState,
    vm: HomeCatalogViewModel,
    liveCat: String?,
    vodCat: String?,
    seriesCat: String?,
    liveQuery: String,
    movieQuery: String,
    seriesQuery: String,
    globalQuery: String,
    progressList: List<WatchProgress>,
    favFocus: MutableMap<Any, FocusRequester>,
    favFocusKey: Any?,
    favFocusTick: Int,
    onNavigate: (ActiveView) -> Unit,
    onOpenLive: () -> Unit,
    onLiveCat: (String?) -> Unit,
    onVodCat: (String?) -> Unit,
    onSeriesCat: (String?) -> Unit,
    onLiveQuery: (String) -> Unit,
    onMovieQuery: (String) -> Unit,
    onSeriesQuery: (String) -> Unit,
    onGlobalQuery: (String) -> Unit,
    onPlayChannel: (LiveChannel, List<LiveChannel>) -> Unit,
    onPlayMovie: (VodMovie) -> Unit,
    onPlaySeries: (Series, String?) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onReorderFavorites: (Int, Int) -> Unit,
    onMoveFavorite: (Int, Int, List<LiveChannel>) -> Unit,
    onRemoveProgress: (WatchProgress) -> Unit,
    onLoadProgress: () -> Unit,
    onColorKey: (Int) -> Boolean,
    navController: NavController,
) {
    val visibleLive = vm.visibleLiveChannels()
    val visibleMovies = vm.visibleMovies()
    val visibleSeries = vm.visibleSeries()
    val favChannels = state.channels.filter { it.isFavorite }
    val orderedFavs = vm.orderedFavorites(favChannels)

    // Reanudar un progreso: las películas van a su ficha; las series, a la
    // ficha con el episodio a medio ver ya localizado.
    fun resumeProgress(progress: WatchProgress) {
        if (progress.id.startsWith("movie:")) {
            val id = progress.id.substringAfter("movie:").toIntOrNull()
            visibleMovies.firstOrNull { it.movieId == id }?.let(onPlayMovie)
        } else {
            visibleSeries.firstOrNull { "series:${it.seriesId}" == progress.id }
                ?.let { onPlaySeries(it, progress.episodeId) }
        }
    }

    // Transición suave al cambiar de vista: fundido + deslizamiento corto.
    AnimatedContent(
        targetState = activeView,
        transitionSpec = {
            (
                slideInHorizontally(tween(220)) { it / 24 } + fadeIn(tween(180))
                    togetherWith
                    slideOutHorizontally(tween(180)) { -it / 24 } + fadeOut(tween(140))
                )
        },
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        with(current) {
            when (current) {
        ActiveView.HOME -> DashboardView(
            totalChannels = visibleLive.size,
            totalMovies = visibleMovies.size,
            totalSeries = visibleSeries.size,
            expiryText = state.expiryText,
            expiryColor = state.expiryText?.let { expiryColor(state.expiryLevel) },
            onSelectLive = onOpenLive,
            onSelectMovies = { onNavigate(ActiveView.MOVIES) },
            onSelectSeries = { onNavigate(ActiveView.SERIES) },
            onSelectContinueWatching = { onNavigate(ActiveView.CONTINUE_WATCHING) },
            onSelectSettings = { navController.navigate(Routes.SETTINGS) },
            onRefresh = { vm.load(forceRefresh = true) },
            globalSearchQuery = globalQuery,
            recentlyAdded = recentlyAdded(visibleMovies, visibleSeries),
            continueWatching = progressList,
            progressImage = { progress -> progressImage(progress, visibleMovies, visibleSeries) },
            onSelectProgress = ::resumeProgress,
            onSelectRecent = { item ->
                if (item.type == "movie") {
                    visibleMovies.firstOrNull { it.movieId.toString() == item.id }
                        ?.let(onPlayMovie)
                } else {
                    visibleSeries.firstOrNull { it.seriesId.toString() == item.id }
                        ?.let { onPlaySeries(it, null) }
                }
            },
            onSearchSubmitted = { query ->
                onGlobalQuery(query)
                val q = query.lowercase()
                AppContainer.searchSnapshot = SearchSnapshot(
                    channels = visibleLive.filter { it.channelName.lowercase().contains(q) },
                    movies = visibleMovies.filter { it.title.lowercase().contains(q) },
                    series = visibleSeries.filter { it.title.lowercase().contains(q) },
                )
                navController.navigate(Routes.SEARCH)
            },
        )
        ActiveView.LIVE -> {
            val catFiltered = when {
                liveCat == FAVORITES_CATEGORY_ID -> orderedFavs
                liveCat == null -> visibleLive
                else -> visibleLive.filter { it.categoryId == liveCat }
            }
            val liveFiltered = if (liveQuery.isEmpty()) {
                catFiltered
            } else {
                catFiltered.filter { it.channelName.lowercase().contains(liveQuery.lowercase()) }
            }
            val isFavSelected = liveCat == FAVORITES_CATEGORY_ID
            val liveCategories = vm.liveCategoriesWithFavorites().filter { category ->
                liveQuery.isBlank() || category.categoryId == FAVORITES_CATEGORY_ID &&
                    orderedFavs.any { it.channelName.contains(liveQuery, ignoreCase = true) } ||
                    category.categoryId != FAVORITES_CATEGORY_ID &&
                    visibleLive.any {
                        it.categoryId == category.categoryId &&
                            it.channelName.contains(liveQuery, ignoreCase = true)
                    }
            }
            LiveChannelBrowser(
                categories = liveCategories,
                selectedCategoryId = liveCat,
                items = liveFiltered,
                onSelectCategory = onLiveCat,
                onBack = { onNavigate(ActiveView.HOME) },
                searchQuery = liveQuery,
                onSearchChanged = onLiveQuery,
                onOpenEpg = {
                    AppContainer.epgChannels = liveFiltered
                    navController.navigate(Routes.EPG)
                },
                client = vm.client(),
                onPlay = onPlayChannel,
                onToggleFavorite = onToggleFavorite,
                onMoveItem = { index, direction ->
                    onMoveFavorite(index, direction, liveFiltered)
                },
                favoriteChannels = orderedFavs,
            )
        }
        ActiveView.MOVIES -> {
            val catFiltered = if (vodCat == null) {
                visibleMovies
            } else {
                visibleMovies.filter { it.categoryId == vodCat }
            }
            val filtered = if (movieQuery.isEmpty()) {
                catFiltered
            } else {
                // La búsqueda recorre TODAS las categorías visibles, no solo la seleccionada.
                visibleMovies.filter { it.title.lowercase().contains(movieQuery.lowercase()) }
            }
            CategoryContentView(
                categories = state.vodCats,
                selectedCategoryId = vodCat,
                getCatId = VodCategory::categoryId,
                getCatName = VodCategory::categoryName,
                items = filtered,
                itemKey = VodMovie::movieId,
                onSelectCategory = onVodCat,
                searchQuery = movieQuery,
                onSearchChanged = onMovieQuery,
                onBack = { onNavigate(ActiveView.HOME) },
                itemContent = {
                    PosterCard(
                        title = it.title,
                        imageUrl = it.logo,
                        metadata = if (it.year > 0) it.year.toString() else "",
                        accent = AppColors.amber,
                    )
                },
                onTap = onPlayMovie,
                density = state.density,
                cardScale = state.cardScale,
            )
        }
        ActiveView.SERIES -> {
            val effectiveCat = seriesCat ?: state.seriesCats.firstOrNull()?.categoryId
            val catFiltered = if (effectiveCat == null) {
                visibleSeries
            } else {
                visibleSeries.filter { it.categoryId == effectiveCat }
            }
            val filtered = if (seriesQuery.isEmpty()) {
                catFiltered
            } else {
                // La búsqueda recorre TODAS las categorías visibles, no solo la seleccionada.
                visibleSeries.filter { it.title.lowercase().contains(seriesQuery.lowercase()) }
            }
            CategoryContentView(
                categories = state.seriesCats,
                selectedCategoryId = effectiveCat,
                getCatId = SeriesCategory::categoryId,
                getCatName = SeriesCategory::categoryName,
                items = filtered,
                itemKey = Series::seriesId,
                onSelectCategory = onSeriesCat,
                searchQuery = seriesQuery,
                onSearchChanged = onSeriesQuery,
                onBack = { onNavigate(ActiveView.HOME) },
                itemContent = {
                    PosterCard(
                        title = it.title,
                        imageUrl = it.logo,
                        metadata = if (it.year > 0) it.year.toString() else "",
                        accent = AppColors.accent,
                        fallbackType = PosterType.SERIES,
                    )
                },
                onTap = { onPlaySeries(it, null) },
                density = state.density,
                cardScale = state.cardScale,
            )
        }
        ActiveView.FAVORITES -> FavoritesView(
            items = orderedFavs,
            onTap = { onPlayChannel(it, orderedFavs) },
            onLongPress = onToggleFavorite,
            onReorder = onReorderFavorites,
            onMoveItem = { index, dir -> onMoveFavorite(index, dir, orderedFavs) },
            focusRequesters = favFocus,
            focusSignal = if (favFocusKey != null) favFocusKey to favFocusTick else null,
            client = vm.client(),
            density = state.density,
            cardScale = state.cardScale,
        )
        ActiveView.HISTORY -> HistoryView(
            entries = historyEntries(visibleMovies, visibleSeries, state.history),
            onTap = { entry ->
                if (entry.type == "movie") {
                    visibleMovies.firstOrNull { it.movieId.toString() == entry.id }
                        ?.let(onPlayMovie)
                } else {
                    visibleSeries.firstOrNull { it.seriesId.toString() == entry.id }
                        ?.let { onPlaySeries(it, null) }
                }
            },
        )
        ActiveView.CONTINUE_WATCHING -> ContinueWatchingView(
            items = progressList,
            imageFor = { progress -> progressImage(progress, visibleMovies, visibleSeries) },
            onTap = ::resumeProgress,
            onLongPress = onRemoveProgress,
            onColorKey = onColorKey,
        )
            }
        }
    }
}

private fun historyEntries(
    movies: List<VodMovie>,
    series: List<Series>,
    history: List<String>,
): List<HistoryEntry> {
    val moviesById = movies.associateBy { it.movieId.toString() }
    val seriesById = series.associateBy { it.seriesId.toString() }
    return history.mapNotNull { entry ->
        val sep = entry.indexOf(':')
        if (sep <= 0) return@mapNotNull null
        val type = entry.substring(0, sep)
        val id = entry.substring(sep + 1)
        when (type) {
            "movie" -> moviesById[id]?.let { HistoryEntry(type, id, it.title, it.logo) }
            "series" -> seriesById[id]?.let { HistoryEntry(type, id, it.title, it.logo) }
            else -> null
        }
    }
}

private fun progressImage(
    progress: WatchProgress,
    movies: List<VodMovie>,
    series: List<Series>,
): String {
    if (!progress.poster.isNullOrEmpty()) return progress.poster
    if (progress.id.startsWith("movie:")) {
        val id = progress.id.substringAfter("movie:").toIntOrNull()
        return movies.firstOrNull { it.movieId == id }?.logo.orEmpty()
    }
    return series.firstOrNull { "series:${it.seriesId}" == progress.id }?.logo.orEmpty()
}

/**
 * "Añadido recientemente": sin peticiones extra al panel, se aproxima por ID de
 * stream descendente (en Xtream, IDs más altos suelen ser altas más recientes),
 * alternando películas y series.
 */
private fun recentlyAdded(
    movies: List<VodMovie>,
    series: List<Series>,
    limit: Int = 18,
): List<RecentItem> {
    val m = movies.sortedByDescending { it.movieId }
        .map { RecentItem("movie", it.movieId.toString(), it.title, it.logo) }
    val s = series.sortedByDescending { it.seriesId }
        .map { RecentItem("series", it.seriesId.toString(), it.title, it.logo) }
    val out = ArrayList<RecentItem>(limit)
    val mi = m.iterator()
    val si = s.iterator()
    while (out.size < limit && (mi.hasNext() || si.hasNext())) {
        if (mi.hasNext()) out.add(mi.next())
        if (out.size < limit && si.hasNext()) out.add(si.next())
    }
    return out
}

@Composable
private fun expiryColor(level: ExpiryLevel): Color = when (level) {
    ExpiryLevel.EXPIRED -> AppColors.expired
    ExpiryLevel.WARN -> AppColors.amber
    else -> subtleTextColor()
}

@Composable
private fun EpgHeaderButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = "Guía EPG completa",
            tint = subtleTextColor(),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RefreshHeaderButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "Actualizar",
            tint = subtleTextColor(),
            modifier = Modifier.size(20.dp),
        )
    }
}
