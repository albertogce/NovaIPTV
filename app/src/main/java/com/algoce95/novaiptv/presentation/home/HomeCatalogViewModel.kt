package com.algoce95.novaiptv.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.data.model.LiveCategory
import com.algoce95.novaiptv.data.model.LiveChannel
import com.algoce95.novaiptv.data.model.Parsers
import com.algoce95.novaiptv.data.model.Series
import com.algoce95.novaiptv.data.model.SeriesCategory
import com.algoce95.novaiptv.data.model.VodCategory
import com.algoce95.novaiptv.data.model.VodMovie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Nivel visual del aviso de caducidad (paridad con los colores de Flutter). */
enum class ExpiryLevel { NONE, INFO, WARN, EXPIRED }

/** Paridad con `HomeCatalog` + favoritos de `HomeScreen` de Flutter. */
class HomeCatalogViewModel(
    private val prefs: PrefsStore = AppContainer.prefs,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val liveContentLoaded: Boolean = false,
        val moviesContentLoaded: Boolean = false,
        val seriesContentLoaded: Boolean = false,
        val loadingCompleted: Int = 0,
        val loadingTotal: Int = 6,
        val loadingLabel: String = "Preparando catálogo IPTV…",
        val error: String? = null,
        val channels: List<LiveChannel> = emptyList(),
        val movies: List<VodMovie> = emptyList(),
        val series: List<Series> = emptyList(),
        val liveCats: List<LiveCategory> = emptyList(),
        val vodCats: List<VodCategory> = emptyList(),
        val seriesCats: List<SeriesCategory> = emptyList(),
        val allLiveCats: List<LiveCategory> = emptyList(),
        val allVodCats: List<VodCategory> = emptyList(),
        val allSeriesCats: List<SeriesCategory> = emptyList(),
        val density: Int = 0,
        val cardScale: Float = 1f,
        val highContrast: Boolean = false,
        val colorActions: List<String> = emptyList(),
        val favoriteIds: List<String> = emptyList(),
        val history: List<String> = emptyList(),
        val expiryText: String? = null,
        val expiryLevel: ExpiryLevel = ExpiryLevel.NONE,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val loadMutex = Mutex()

    companion object {
        private const val TAG = "NovaIPTV"
    }

    init {
        viewModelScope.launch {
            refreshSettings()
            load()
        }
    }

    fun client() = requireNotNull(AppContainer.api) { "sin sesión" }

    // -- Carga y caché (réplica de HomeCatalog) -----------------------------

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadMutex.withLock {
                _state.update {
                    it.copy(
                        isLoading = true,
                        liveContentLoaded = false,
                        moviesContentLoaded = false,
                        seriesContentLoaded = false,
                        error = null,
                        loadingCompleted = 0,
                        loadingTotal = 6,
                        loadingLabel = "Conectando con la IPTV…",
                    )
                }
                Log.i(TAG, "cargando catálogo (forzar=$forceRefresh)")
                if (!forceRefresh && tryLoadCache()) {
                    Log.i(TAG, "catálogo desde caché local")
                    applyFilters()
                    _state.update { it.copy(isLoading = false) }
                    return@withLock
                }
                fetchRemote(silent = false)
            }
        }
    }

    /** Refresco silencioso del temporizador automático. */
    fun silentRefresh() {
        viewModelScope.launch {
            loadMutex.withLock { fetchRemote(silent = true) }
        }
    }

    /**
     * Recrea el cliente si las credenciales cambiaron (vuelta de Ajustes).
     * Devuelve true si hay que invalidar los datos.
     */
    fun updateClientIfCredentialsChanged(): Boolean {
        val current = AppContainer.api
        val c = AppContainer.credentials
        val changed = current == null ||
            current.baseUrl != c.server ||
            current.username != c.username ||
            current.password != c.password
        AppContainer.refreshApi()
        return changed
    }

    /** Relee ajustes, favoritos, historial y caducidad (vuelta de otras vistas). */
    fun refreshDynamic() {
        viewModelScope.launch {
            refreshSettings()
        }
    }

    private suspend fun tryLoadCache(): Boolean {
        val favIds = (prefs.getStringList(PrefsStore.Keys.FAVORITE_CHANNELS) ?: emptyList())
            .toSet()
        val channels = prefs.readCacheList("cache_live_channels") {
            Parsers.parseLiveChannel(it, favIds)
        } ?: return false
        val baseUrl = AppContainer.api?.baseUrl
        val movies = prefs.readCacheList("cache_movies") { 
            Parsers.parseVodMovie(it, baseUrl)
        } ?: return false
        val series = prefs.readCacheList("cache_series", Parsers::parseSeries)
            ?: return false
        val liveCats = prefs.readCacheList("cache_live_categories", Parsers::parseLiveCategory)
            ?: return false
        val vodCats = prefs.readCacheList("cache_vod_categories", Parsers::parseVodCategory)
            ?: return false
        val seriesCats = prefs.readCacheList("cache_series_categories", Parsers::parseSeriesCategory)
            ?: return false

        _state.update {
            it.copy(
                channels = channels,
                movies = movies,
                series = series,
                allLiveCats = liveCats,
                allVodCats = vodCats,
                allSeriesCats = seriesCats,
            )
        }
        return channels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()
    }

    private suspend fun fetchRemote(silent: Boolean) {
        try {
            val client = client()
            val favIds = (prefs.getStringList(PrefsStore.Keys.FAVORITE_CHANNELS) ?: emptyList())
                .toSet()
            val compact = prefs.getBoolean(PrefsStore.Keys.STORE_VISIBLE_ONLY) ?: false
            val hiddenLive = (prefs.getStringList(PrefsStore.Keys.LIVE_HIDDEN) ?: emptyList()).toSet()
            val hiddenVod = (prefs.getStringList(PrefsStore.Keys.VOD_HIDDEN) ?: emptyList()).toSet()
            val hiddenSeries = (prefs.getStringList(PrefsStore.Keys.SERIES_HIDDEN) ?: emptyList()).toSet()
            suspend fun markLoaded(label: String) {
                _state.update {
                    it.copy(
                        loadingCompleted = (it.loadingCompleted + 1).coerceAtMost(it.loadingTotal),
                        loadingLabel = "$label completado",
                    )
                }
            }
            // Cada sección se publica al completarse, sin esperar al resto: si una
            // falla o se cuelga, las demás se muestran igual. Lo que falla no se
            // publica ni machaca su caché (conserva lo anterior).
            supervisorScope {
                val jobs = listOf(
                    launch {
                        safely("canales en vivo") { client.getLiveStreams(favoriteIds = favIds) }
                            ?.let { list ->
                                val cached = if (compact) {
                                    list.filter { it.categoryId !in hiddenLive }
                                } else {
                                    list
                                }
                                prefs.writeCacheList("cache_live_channels", cached.map { it.toCacheJson() })
                                _state.update { it.copy(channels = list) }
                            }
                        _state.update { it.copy(liveContentLoaded = true) }
                        markLoaded("Canales en vivo")
                    },
                    launch {
                        safely("películas") { client.getVodStreams() }?.let { list ->
                            val cached = if (compact) {
                                list.filter { it.categoryId !in hiddenVod }
                            } else {
                                list
                            }
                            prefs.writeCacheList("cache_movies", cached.map { it.toCacheJson() })
                            _state.update { it.copy(movies = list) }
                        }
                        _state.update { it.copy(moviesContentLoaded = true) }
                        markLoaded("Películas")
                    },
                    launch {
                        safely("series") { client.getSeries() }?.let { list ->
                            val cached = if (compact) {
                                list.filter { it.categoryId !in hiddenSeries }
                            } else {
                                list
                            }
                            prefs.writeCacheList("cache_series", cached.map { it.toCacheJson() })
                            _state.update { it.copy(series = list) }
                        }
                        _state.update { it.copy(seriesContentLoaded = true) }
                        markLoaded("Series")
                    },
                    launch {
                        safely("categorías de canales en vivo") { client.getLiveCategories() }
                            ?.let { list ->
                                prefs.writeCacheList(
                                    "cache_live_categories",
                                    list.map { it.toCacheJson() },
                                )
                                _state.update { it.copy(allLiveCats = list) }
                            }
                        markLoaded("Categorías de canales")
                    },
                    launch {
                        safely("categorías de películas") { client.getVodCategories() }
                            ?.let { list ->
                                prefs.writeCacheList(
                                    "cache_vod_categories",
                                    list.map { it.toCacheJson() },
                                )
                                _state.update { it.copy(allVodCats = list) }
                            }
                        markLoaded("Categorías de películas")
                    },
                    launch {
                        safely("categorías de series") { client.getSeriesCategories() }
                            ?.let { list ->
                                prefs.writeCacheList(
                                    "cache_series_categories",
                                    list.map { it.toCacheJson() },
                                )
                                _state.update { it.copy(allSeriesCats = list) }
                            }
                        markLoaded("Categorías de series")
                    },
                )
                jobs.joinAll()
            }
            applyFilters()
            _state.value.let {
                Log.i(
                    TAG,
                    "catálogo: ${it.channels.size} canales, ${it.movies.size} pelis, " +
                        "${it.series.size} series, " +
                        "${it.allLiveCats.size}/${it.allVodCats.size}/${it.allSeriesCats.size} cats",
                )
            }
            // Aunque todo falle se muestra la pantalla (con ceros si no hay nada),
            // nunca una pantalla de error que bloquee el resto.
            _state.update { it.copy(isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "carga remota: $e")
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun <T> safely(name: String, block: suspend () -> T): T? {
        return try {
            val result = block()
            val count = (result as? List<*>)?.size
            Log.i(TAG, "cargado $name${if (count != null) ": $count" else ""}")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sin $name: $e")
            null
        }
    }

    private suspend fun applyFilters() {
        val liveHidden = prefs.getStringList(PrefsStore.Keys.LIVE_HIDDEN) ?: emptyList()
        val liveOrder = prefs.getStringList(PrefsStore.Keys.LIVE_ORDER) ?: emptyList()
        val vodHidden = prefs.getStringList(PrefsStore.Keys.VOD_HIDDEN) ?: emptyList()
        val vodOrder = prefs.getStringList(PrefsStore.Keys.VOD_ORDER) ?: emptyList()
        val seriesHidden = prefs.getStringList(PrefsStore.Keys.SERIES_HIDDEN) ?: emptyList()
        val seriesOrder = prefs.getStringList(PrefsStore.Keys.SERIES_ORDER) ?: emptyList()
        _state.update {
            it.copy(
                liveCats = filterAndOrder(it.allLiveCats, liveHidden, liveOrder, LiveCategory::categoryId),
                vodCats = filterAndOrder(it.allVodCats, vodHidden, vodOrder, VodCategory::categoryId),
                seriesCats = filterAndOrder(
                    it.allSeriesCats,
                    seriesHidden,
                    seriesOrder,
                    SeriesCategory::categoryId,
                ),
            )
        }
    }

    /** Visibilidad y orden de Ajustes (réplica exacta del comparador de Dart). */
    private fun <T> filterAndOrder(
        all: List<T>,
        hidden: List<String>,
        order: List<String>,
        idOf: (T) -> String,
    ): List<T> {
        val hiddenSet = hidden.toSet()
        val filtered = all.filter { idOf(it) !in hiddenSet }
        if (order.isEmpty()) return filtered
        return filtered.sortedWith { a, b ->
            val ia = order.indexOf(idOf(a))
            val ib = order.indexOf(idOf(b))
            when {
                ia == -1 && ib == -1 -> 0
                ia == -1 -> 1
                ib == -1 -> -1
                else -> ia.compareTo(ib)
            }
        }
    }

    // -- Selectores derivados -------------------------------------------------

    fun visibleLiveChannels(): List<LiveChannel> {
        val s = _state.value
        val ids = s.liveCats.map { it.categoryId }.toSet()
        return s.channels.filter { it.categoryId in ids }
    }

    fun visibleMovies(): List<VodMovie> {
        val s = _state.value
        val ids = s.vodCats.map { it.categoryId }.toSet()
        return s.movies.filter { it.categoryId in ids }.distinctBy { it.movieId }
    }

    fun visibleSeries(): List<Series> {
        val s = _state.value
        val ids = s.seriesCats.map { it.categoryId }.toSet()
        return s.series.filter { it.categoryId in ids }.distinctBy { it.seriesId }
    }

    fun liveCategoriesWithFavorites(): List<LiveCategory> =
        listOf(LiveCategory(FAVORITES_CATEGORY_ID, "Favoritos")) +
            _state.value.liveCats

    fun orderedFavorites(channels: List<LiveChannel>): List<LiveChannel> {
        val byId = channels.associateBy { it.channelId.toString() }
        return _state.value.favoriteIds.mapNotNull { byId[it] }
    }

    // -- Favoritos ------------------------------------------------------------

    /** Alterna favorito; devuelve el estado resultante. */
    suspend fun toggleFavorite(channel: LiveChannel): Boolean {
        val favList = (
            prefs.getStringList(PrefsStore.Keys.FAVORITE_CHANNELS) ?: emptyList()
            ).toMutableList()
        val id = channel.channelId.toString()
        val isFavNow = !channel.isFavorite
        if (isFavNow) {
            if (!favList.contains(id)) favList.add(id)
        } else {
            favList.remove(id)
        }
        prefs.setStringList(PrefsStore.Keys.FAVORITE_CHANNELS, favList)
        _state.update { state ->
            state.copy(
                channels = state.channels.map {
                    if (it.channelId == channel.channelId) it.copy(isFavorite = isFavNow) else it
                },
                favoriteIds = favList,
            )
        }
        return isFavNow
    }

    suspend fun reorderFavorites(oldIndex: Int, newIndex: Int): LiveChannel? {
        val favChannels = orderedFavorites(
            _state.value.channels.filter { it.isFavorite },
        ).toMutableList()
        if (favChannels.isEmpty()) return null
        var to = newIndex
        if (to > oldIndex) to--
        if (oldIndex !in favChannels.indices || to !in favChannels.indices) return null
        val moved = favChannels.removeAt(oldIndex)
        favChannels.add(to, moved)
        prefs.setStringList(
            PrefsStore.Keys.FAVORITE_CHANNELS,
            favChannels.map { it.channelId.toString() },
        )
        val nonFavs = _state.value.channels.filter { !it.isFavorite }
        _state.update {
            it.copy(
                channels = favChannels + nonFavs,
                favoriteIds = favChannels.map { c -> c.channelId.toString() },
            )
        }
        return moved
    }

    suspend fun removeContinueWatching(id: String) {
        AppContainer.watchProgress.remove(id)
    }

    /** Progreso para Seguir viendo (acabadas caen solo en pelis). */
    suspend fun continueWatching(): List<com.algoce95.novaiptv.data.model.WatchProgress> =
        AppContainer.watchProgress.getAll().filter { progress ->
            progress.fraction > 0 &&
                (progress.fraction < .95 || !progress.id.startsWith("movie:"))
        }

    // -- Internos -------------------------------------------------------------

    private suspend fun refreshSettings() {
        val density = prefs.getString(PrefsStore.Keys.GRID_DENSITY)?.toIntOrNull() ?: 0
        val scale = prefs.getString(PrefsStore.Keys.CARD_SCALE)?.toFloatOrNull() ?: 1f
        val contrast = prefs.getBoolean(PrefsStore.Keys.HIGH_CONTRAST) ?: false
        val colors = prefs.getStringList(PrefsStore.Keys.COLOR_ACTIONS) ?: emptyList()
        val favIds = prefs.getStringList(PrefsStore.Keys.FAVORITE_CHANNELS) ?: emptyList()
        val history = prefs.getStringList(PrefsStore.Keys.WATCH_HISTORY) ?: emptyList()
        val expiry = accountExpiry(prefs.getString(PrefsStore.Keys.ACCOUNT_EXP_DATE))
        _state.update {
            it.copy(
                density = density,
                cardScale = scale,
                highContrast = contrast,
                colorActions = colors,
                favoriteIds = favIds,
                history = history,
                expiryText = expiry?.first,
                expiryLevel = expiry?.second ?: ExpiryLevel.NONE,
            )
        }
    }

    private fun accountExpiry(rawSeconds: String?): Pair<String, ExpiryLevel>? {
        val seconds = rawSeconds?.toLongOrNull() ?: return null
        if (seconds <= 0) return null
        val daysLeft = ((seconds * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
        return when {
            daysLeft < 0 -> "Suscripción caducada" to ExpiryLevel.EXPIRED
            daysLeft == 0 -> "La suscripción caduca hoy" to ExpiryLevel.WARN
            daysLeft == 1 -> "La suscripción caduca en 1 día" to ExpiryLevel.WARN
            daysLeft <= 7 -> "La suscripción caduca en $daysLeft días" to ExpiryLevel.WARN
            else -> "La suscripción caduca en $daysLeft días" to ExpiryLevel.INFO
        }
    }
}
