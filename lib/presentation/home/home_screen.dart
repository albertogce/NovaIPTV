import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';
import 'package:iptv_flutter/data/models/live_channel.dart';
import 'package:iptv_flutter/data/models/vod_movie.dart';
import 'package:iptv_flutter/data/models/series.dart';
import 'package:iptv_flutter/data/models/live_category.dart';
import 'package:iptv_flutter/data/models/vod_category.dart';
import 'package:iptv_flutter/data/models/series_category.dart';
import 'package:iptv_flutter/data/models/epg_program.dart';
import 'package:iptv_flutter/presentation/player/player_screen.dart';
import 'package:iptv_flutter/presentation/home/movie_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/series_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/search_results_screen.dart';
import 'package:iptv_flutter/presentation/settings/settings_screen.dart';
import 'package:iptv_flutter/presentation/home/epg_guide_screen.dart';
import 'package:iptv_flutter/core/playback/watch_progress_store.dart';

part 'home_models.dart';
part 'widgets/library_stat.dart';
part 'widgets/poster_fallback.dart';
part 'home_dashboard.dart';
part 'home_content_widgets.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.client});

  final XtreamApiClient client;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

const _favoritesCategoryId = '__favorites__';

class _HomeScreenState extends State<HomeScreen> {
  late XtreamApiClient _client;
  ActiveView _activeView = ActiveView.home;

  List<LiveChannel> _liveChannels = [];
  List<VodMovie> _movies = [];
  List<Series> _series = [];

  List<LiveCategory> _liveCategories = [];
  List<VodCategory> _vodCategories = [];
  List<SeriesCategory> _seriesCategories = [];
  List<LiveCategory> _allLiveCategories = [];
  List<VodCategory> _allVodCategories = [];
  List<SeriesCategory> _allSeriesCategories = [];

  String? _selectedLiveCatId;
  String? _selectedVodCatId;
  String? _selectedSeriesCatId;

  // Search state
  String _liveSearchQuery = '';
  String _movieSearchQuery = '';
  String _seriesSearchQuery = '';
  String _globalSearchQuery = '';

  bool _isLoading = true;
  String? _errorMessage;

  Timer? _autoRefreshTimer;
  final Map<int, FocusNode> _favoriteFocusNodes = {};
  final FocusNode _epgFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _client = widget.client;
    HardwareKeyboard.instance.addHandler(_handleHardwareKey);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _loadData();
    _startAutoRefreshTimer();
  }

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_handleHardwareKey);
    _autoRefreshTimer?.cancel();
    for (final node in _favoriteFocusNodes.values) {
      node.dispose();
    }
    _epgFocusNode.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _startAutoRefreshTimer() {
    _autoRefreshTimer?.cancel();
    final storage = SharedPrefsStorage();
    final refreshStr = storage.getString('settings_auto_refresh_minutes');
    final minutes = refreshStr != null ? int.tryParse(refreshStr) ?? 0 : 0;

    if (minutes > 0) {
      _autoRefreshTimer = Timer.periodic(Duration(minutes: minutes), (_) {
        _fetchAndCacheRemoteData(silent: true);
      });
    }
  }

  void _applyCategoryFiltersAndOrdering() {
    final storage = SharedPrefsStorage();

    // Live Categories
    final hiddenLive = (storage.getStringList('settings_live_cat_hidden') ?? [])
        .toSet();
    final liveOrder = storage.getStringList('settings_live_cat_order') ?? [];
    var filteredLive = _allLiveCategories
        .where((c) => !hiddenLive.contains(c.categoryId.toString()))
        .toList();
    if (liveOrder.isNotEmpty) {
      filteredLive.sort((a, b) {
        final indexA = liveOrder.indexOf(a.categoryId.toString());
        final indexB = liveOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }

    // Vod Categories
    final hiddenVod = (storage.getStringList('settings_vod_cat_hidden') ?? [])
        .toSet();
    final vodOrder = storage.getStringList('settings_vod_cat_order') ?? [];
    var filteredVod = _allVodCategories
        .where((c) => !hiddenVod.contains(c.categoryId.toString()))
        .toList();
    if (vodOrder.isNotEmpty) {
      filteredVod.sort((a, b) {
        final indexA = vodOrder.indexOf(a.categoryId.toString());
        final indexB = vodOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }

    // Series Categories
    final hiddenSeries =
        (storage.getStringList('settings_series_cat_hidden') ?? []).toSet();
    final seriesOrder =
        storage.getStringList('settings_series_cat_order') ?? [];
    var filteredSeries = _allSeriesCategories
        .where((c) => !hiddenSeries.contains(c.categoryId.toString()))
        .toList();
    if (seriesOrder.isNotEmpty) {
      filteredSeries.sort((a, b) {
        final indexA = seriesOrder.indexOf(a.categoryId.toString());
        final indexB = seriesOrder.indexOf(b.categoryId.toString());
        if (indexA == -1 && indexB == -1) return 0;
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });
    }

    _liveCategories = filteredLive;
    _vodCategories = filteredVod;
    _seriesCategories = filteredSeries;
  }

  Future<void> _loadData({bool forceRefresh = false}) async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    if (!forceRefresh) {
      final hasCachedData = await _tryLoadLocalCache();
      if (hasCachedData) {
        _applyCategoryFiltersAndOrdering();
        setState(() {
          _isLoading = false;
        });
        return;
      }
    }

    await _fetchAndCacheRemoteData(silent: false);
  }

  Future<bool> _tryLoadLocalCache() async {
    final storage = SharedPrefsStorage();
    final cachedChannels = await storage.getCacheString('cache_live_channels');
    final cachedMovies = await storage.getCacheString('cache_movies');
    final cachedSeries = await storage.getCacheString('cache_series');
    final cachedLiveCats = await storage.getCacheString(
      'cache_live_categories',
    );
    final cachedVodCats = await storage.getCacheString('cache_vod_categories');
    final cachedSeriesCats = await storage.getCacheString(
      'cache_series_categories',
    );

    if (cachedChannels == null ||
        cachedMovies == null ||
        cachedSeries == null ||
        cachedLiveCats == null ||
        cachedVodCats == null ||
        cachedSeriesCats == null) {
      debugPrint('Caché IPTV no disponible; se solicitarán los datos remotos.');
      return false;
    }

    try {
      final List rawCh = json.decode(cachedChannels);
      final List rawMov = json.decode(cachedMovies);
      final List rawSer = json.decode(cachedSeries);
      final List rawLCat = json.decode(cachedLiveCats);
      final List rawVCat = json.decode(cachedVodCats);
      final List rawSCat = json.decode(cachedSeriesCats);

      _liveChannels = rawCh
          .map((e) => LiveChannel.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _movies = rawMov
          .map((e) => VodMovie.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _series = rawSer
          .map((e) => Series.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _allLiveCategories = rawLCat
          .map((e) => LiveCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _allVodCategories = rawVCat
          .map((e) => VodCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _allSeriesCategories = rawSCat
          .map((e) => SeriesCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      _liveCategories = List.of(_allLiveCategories);
      _vodCategories = List.of(_allVodCategories);
      _seriesCategories = List.of(_allSeriesCategories);

      final hasContent =
          _liveChannels.isNotEmpty || _movies.isNotEmpty || _series.isNotEmpty;
      debugPrint(
        hasContent
            ? 'Caché IPTV cargada; no se solicitan listados remotos.'
            : 'Caché IPTV vacía; se solicitarán los datos remotos.',
      );
      return hasContent;
    } catch (_) {
      debugPrint('Caché IPTV inválida; se solicitarán los datos remotos.');
      return false;
    }
  }

  Future<void> _fetchAndCacheRemoteData({bool silent = false}) async {
    try {
      Future<List<dynamic>> loadListSafely(
        String name,
        Future<List<dynamic>> request,
      ) async {
        try {
          return await request;
        } catch (error) {
          debugPrint(
            'No se pudo cargar $name; se continuará sin esos datos: $error',
          );
          return <dynamic>[];
        }
      }

      final results = await Future.wait([
        loadListSafely('canales en vivo', _client.getLiveStreams()),
        loadListSafely('películas', _client.getVodStreams()),
        loadListSafely('series', _client.getSeries()),
        loadListSafely(
          'categorías de canales en vivo',
          _client.getLiveCategories(),
        ),
        loadListSafely('categorías de películas', _client.getVodCategories()),
        loadListSafely('categorías de series', _client.getSeriesCategories()),
      ]);

      final rawChannels = results[0];
      final rawMovies = results[1];
      final rawSeries = results[2];
      final rawLiveCats = results[3];
      final rawVodCats = results[4];
      final rawSeriesCats = results[5];

      final channelsParsed = rawChannels
          .map((e) => LiveChannel.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final moviesParsed = rawMovies
          .map((e) => VodMovie.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final seriesParsed = rawSeries
          .map((e) => Series.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final liveCatsParsed = rawLiveCats
          .map((e) => LiveCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final vodCatsParsed = rawVodCats
          .map((e) => VodCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      final seriesCatsParsed = rawSeriesCats
          .map((e) => SeriesCategory.fromJson(Map<String, dynamic>.from(e)))
          .toList();

      // A manual refresh deliberately keeps the complete response on disk.
      // Automatic refreshes can use the compact mode selected in Settings.
      final storage = SharedPrefsStorage();
      final compactCache =
          storage.getBool('settings_store_visible_only') ?? false;
      List<T> keepVisible<T>(
        List<T> items,
        String Function(T) categoryId,
        Set<String> hidden,
      ) {
        if (!compactCache) return items;
        return items
            .where((item) => !hidden.contains(categoryId(item)))
            .toList();
      }

      final hiddenLive =
          (storage.getStringList('settings_live_cat_hidden') ?? []).toSet();
      final hiddenVod = (storage.getStringList('settings_vod_cat_hidden') ?? [])
          .toSet();
      final hiddenSeries =
          (storage.getStringList('settings_series_cat_hidden') ?? []).toSet();
      final channelsToCache = keepVisible(
        channelsParsed,
        (item) => item.categoryId.toString(),
        hiddenLive,
      );
      final moviesToCache = keepVisible(
        moviesParsed,
        (item) => item.categoryId.toString(),
        hiddenVod,
      );
      final seriesToCache = keepVisible(
        seriesParsed,
        (item) => item.categoryId.toString(),
        hiddenSeries,
      );

      await storage.setCacheString(
        'cache_live_channels',
        json.encode(channelsToCache.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_movies',
        json.encode(moviesToCache.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_series',
        json.encode(seriesToCache.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_live_categories',
        json.encode(liveCatsParsed.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_vod_categories',
        json.encode(vodCatsParsed.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_series_categories',
        json.encode(seriesCatsParsed.map((e) => e.toJson()).toList()),
      );

      if (mounted) {
        _liveChannels = channelsParsed;
        _movies = moviesParsed;
        _series = seriesParsed;
        _allLiveCategories = liveCatsParsed;
        _allVodCategories = vodCatsParsed;
        _allSeriesCategories = seriesCatsParsed;
        _liveCategories = List.of(_allLiveCategories);
        _vodCategories = List.of(_allVodCategories);
        _seriesCategories = List.of(_allSeriesCategories);
        _applyCategoryFiltersAndOrdering();
        setState(() {
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted && !silent) {
        setState(() {
          _errorMessage = 'Error al cargar los datos de IPTV: $e';
          _isLoading = false;
        });
      }
    }
  }

  String _buildStreamUrl(LiveChannel channel) {
    final base = _client.baseUrl.replaceAll(RegExp(r'/$'), '');
    return '$base/live/${_client.username}/${_client.password}/${channel.channelId}.ts';
  }

  void _onChannelTap(LiveChannel channel, ActiveView sourceView) {
    final url = _buildStreamUrl(channel);
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (_) =>
                PlayerScreen(streamUrl: url, channelName: channel.channelName),
          ),
        )
        .then((_) {
          if (mounted) {
            setState(() {
              _activeView = sourceView;
            });
          }
        });
  }

  void _onMovieTap(VodMovie movie) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MovieDetailScreen(movie: movie, client: _client),
      ),
    );
  }

  void _onSeriesTap(Series series) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SeriesDetailScreen(series: series, client: _client),
      ),
    );
  }

  List<_HistoryEntry> _historyEntries(
    List<VodMovie> movies,
    List<Series> series,
  ) {
    final moviesById = {
      for (final movie in movies) movie.movieId.toString(): movie,
    };
    final seriesById = {
      for (final item in series) item.seriesId.toString(): item,
    };
    final history = SharedPrefsStorage().getStringList('watch_history') ?? [];

    return history
        .map((entry) {
          final separator = entry.indexOf(':');
          if (separator <= 0) return null;
          final type = entry.substring(0, separator);
          final id = entry.substring(separator + 1);
          if (type == 'movie') {
            final movie = moviesById[id];
            if (movie == null) return null;
            return _HistoryEntry(
              type: type,
              id: id,
              title: movie.title,
              logo: movie.logo,
            );
          }
          if (type == 'series') {
            final item = seriesById[id];
            if (item == null) return null;
            return _HistoryEntry(
              type: type,
              id: id,
              title: item.title,
              logo: item.logo,
            );
          }
          return null;
        })
        .whereType<_HistoryEntry>()
        .toList();
  }

  void _toggleFavoriteChannel(LiveChannel channel) async {
    final storage = SharedPrefsStorage();
    final favList = storage.getStringList('favorite_channels') ?? [];
    final idStr = channel.channelId.toString();

    final isFavNow = !channel.isFavorite;
    if (isFavNow) {
      if (!favList.contains(idStr)) favList.add(idStr);
    } else {
      favList.remove(idStr);
    }

    await storage.setStringList('favorite_channels', favList);

    setState(() {
      _liveChannels = _liveChannels.map((c) {
        if (c.channelId == channel.channelId) {
          return c.copyWith(isFavorite: isFavNow);
        }
        return c;
      }).toList();
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          duration: const Duration(seconds: 2),
          content: Text(
            isFavNow
                ? 'Añadido a Favoritos: ${channel.channelName}'
                : 'Eliminado de Favoritos: ${channel.channelName}',
          ),
          backgroundColor: isFavNow
              ? const Color(0xFFE91E63)
              : Colors.grey[800],
        ),
      );
    }
  }

  void _reorderFavoriteChannels(int oldIndex, int newIndex) async {
    final favChannels = _orderedFavoriteChannels(
      _liveChannels.where((c) => c.isFavorite).toList(),
    );

    if (favChannels.isEmpty) return;

    if (newIndex > oldIndex) newIndex--;
    if (oldIndex < 0 || oldIndex >= favChannels.length) return;
    if (newIndex < 0 || newIndex >= favChannels.length) return;

    final movedItem = favChannels.removeAt(oldIndex);
    favChannels.insert(newIndex, movedItem);

    final newFavIds = favChannels.map((c) => c.channelId.toString()).toList();
    final storage = SharedPrefsStorage();
    await storage.setStringList('favorite_channels', newFavIds);

    setState(() {
      final nonFavs = _liveChannels.where((c) => !c.isFavorite).toList();
      _liveChannels = [...favChannels, ...nonFavs];
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _favoriteFocusNodes[movedItem.channelId]?.requestFocus();
      }
    });
  }

  void _moveFavoriteChannel(int index, int direction) {
    final favorites = _orderedFavoriteChannels(
      _liveChannels.where((channel) => channel.isFavorite).toList(),
    );
    final targetIndex = index + direction;
    if (index < 0 || targetIndex < 0 || targetIndex >= favorites.length) {
      return;
    }
    _reorderFavoriteChannels(
      index,
      direction > 0 ? targetIndex + 1 : targetIndex,
    );
  }

  void _navigateTo(ActiveView view) {
    setState(() {
      _activeView = view;
    });
  }

  void _openLiveChannels() {
    final hasFavorites = _liveChannels.any((channel) => channel.isFavorite);
    setState(() {
      _selectedLiveCatId = hasFavorites
          ? _favoritesCategoryId
          : (_liveCategories.isNotEmpty
                ? _liveCategories.first.categoryId.toString()
                : null);
      _activeView = ActiveView.live;
    });
  }

  KeyEventResult _handleColorKey(KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final logicalKeyId = event.logicalKey.keyId;
    // Android normally exposes key codes to Flutter with the Android
    // 0x100000000 prefix, but some TV firmwares/ADB versions expose the raw
    // value instead.
    final keyCode = logicalKeyId >= 0x100000000
        ? logicalKeyId - 0x100000000
        : logicalKeyId;
    final label = event.logicalKey.keyLabel.toLowerCase();
    final color = label.contains('red') || label.contains('rojo')
        ? 'ROJO'
        : label.contains('green') || label.contains('verde')
        ? 'VERDE'
        : label.contains('yellow') || label.contains('amarillo')
        ? 'AMARILLO'
        : label.contains('blue') || label.contains('azul')
        ? 'AZUL'
        : switch (keyCode) {
            183 => 'ROJO',
            184 => 'VERDE',
            185 => 'AMARILLO',
            186 => 'AZUL',
            _ => null,
          };
    if (color == null) return KeyEventResult.ignored;

    final actions =
        SharedPrefsStorage().getStringList('settings_color_actions') ?? [];
    final action = actions.elementAtOrNull(
      ['ROJO', 'VERDE', 'AMARILLO', 'AZUL'].indexOf(color),
    );
    switch (action) {
      case 'favorites':
        _navigateTo(ActiveView.favorites);
      case 'live':
        _openLiveChannels();
      case 'movies':
        _navigateTo(ActiveView.movies);
      case 'series':
        _navigateTo(ActiveView.series);
      case 'search':
        _navigateTo(ActiveView.home);
      default:
        return KeyEventResult.ignored;
    }
    return KeyEventResult.handled;
  }

  bool _handleHardwareKey(KeyEvent event) {
    return _handleColorKey(event) == KeyEventResult.handled;
  }

  Future<void> _removeContinueWatching(WatchProgress progress) async {
    await WatchProgressStore().remove(progress.id);
    if (!mounted) return;
    setState(() {});
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Eliminado de Seguir viendo: ${progress.title}')),
    );
  }

  List<LiveChannel> _orderedFavoriteChannels(List<LiveChannel> channels) {
    final favoriteIds =
        SharedPrefsStorage().getStringList('favorite_channels') ?? [];
    final byId = {
      for (final channel in channels) channel.channelId.toString(): channel,
    };
    return favoriteIds.map((id) => byId[id]).whereType<LiveChannel>().toList();
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: Color(0xFFE91E63)),
      );
    }

    if (_errorMessage != null) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.error_outline,
                  color: Colors.redAccent,
                  size: 48,
                ),
                const SizedBox(height: 12),
                SelectableText(
                  _errorMessage!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white70),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => _loadData(forceRefresh: true),
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final visibleLiveCatIds = _liveCategories
        .map((c) => c.categoryId.toString())
        .toSet();
    final visibleLiveChannels = _liveChannels
        .where((c) => visibleLiveCatIds.contains(c.categoryId.toString()))
        .toList();

    final visibleVodCatIds = _vodCategories
        .map((c) => c.categoryId.toString())
        .toSet();
    final visibleMovies = _movies
        .where((m) => visibleVodCatIds.contains(m.categoryId.toString()))
        .toList();

    final visibleSeriesCatIds = _seriesCategories
        .map((c) => c.categoryId.toString())
        .toSet();
    final visibleSeries = _series
        .where((s) => visibleSeriesCatIds.contains(s.categoryId.toString()))
        .toList();

    final favChannels = _liveChannels.where((c) => c.isFavorite).toList();
    final orderedFavChannels = _orderedFavoriteChannels(favChannels);
    final liveCategoriesWithFavorites = [
      LiveCategory(categoryId: _favoritesCategoryId, categoryName: 'Favoritos'),
      ..._liveCategories,
    ];

    switch (_activeView) {
      case ActiveView.home:
        return HomeCenterDashboard(
          totalChannels: visibleLiveChannels.length,
          totalMovies: visibleMovies.length,
          totalSeries: visibleSeries.length,
          totalFavorites: favChannels.length,
          onSelectLive: _openLiveChannels,
          onSelectMovies: () => _navigateTo(ActiveView.movies),
          onSelectSeries: () => _navigateTo(ActiveView.series),
          onSelectContinueWatching: () =>
              _navigateTo(ActiveView.continueWatching),
          onSelectSettings: () => _navigateTo(ActiveView.settings),
          onRefresh: () => _loadData(forceRefresh: true),
          globalSearchQuery: _globalSearchQuery,
          onGlobalSearchChanged: (q) => setState(() => _globalSearchQuery = q),
          allChannels: visibleLiveChannels,
          allMovies: visibleMovies,
          allSeries: visibleSeries,
          onSearchSubmitted: (query) async {
            final result = await Navigator.of(context).push<Object?>(
              MaterialPageRoute(
                builder: (_) => SearchResultsScreen(
                  query: query,
                  channels: visibleLiveChannels,
                  movies: visibleMovies,
                  series: visibleSeries,
                ),
              ),
            );
            if (!mounted || result == null) return;
            if (result is LiveChannel) {
              _onChannelTap(result, ActiveView.home);
            } else if (result is VodMovie) {
              _onMovieTap(result);
            } else if (result is Series) {
              _onSeriesTap(result);
            }
          },
          onChannelTap: (ch) => _onChannelTap(ch, ActiveView.home),
          onMovieTap: _onMovieTap,
          onSeriesTap: _onSeriesTap,
          client: _client,
        );
      case ActiveView.settings:
        return SettingsScreen(
          client: _client,
          liveCategories: _allLiveCategories,
          vodCategories: _allVodCategories,
          seriesCategories: _allSeriesCategories,
          onSettingsSaved: (newClient) {
            final credentialsChanged =
                newClient.baseUrl != _client.baseUrl ||
                newClient.username != _client.username ||
                newClient.password != _client.password;
            _client = newClient;
            _startAutoRefreshTimer();
            // Only a changed IPTV account invalidates the current data.
            // Category and appearance changes continue using the local cache.
            _loadData(forceRefresh: credentialsChanged);
            _navigateTo(ActiveView.home);
          },
        );
      case ActiveView.favorites:
        return GridViewContentView<LiveChannel>(
          items: orderedFavChannels,
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: _client),
          onTap: (channel) => _onChannelTap(channel, ActiveView.live),
          onLongPress: (channel) => _toggleFavoriteChannel(channel),
          onReorder: _reorderFavoriteChannels,
          showReorderControls: true,
          onMoveItem: _moveFavoriteChannel,
          focusNodeForItem: (channel) =>
              _favoriteFocusNodes.putIfAbsent(channel.channelId, FocusNode.new),
        );
      case ActiveView.history:
        final historyEntries = _historyEntries(visibleMovies, visibleSeries);
        return Column(
          children: [
            Expanded(
              child: historyEntries.isEmpty
                  ? const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.history_toggle_off,
                            color: Colors.white24,
                            size: 58,
                          ),
                          SizedBox(height: 14),
                          Text(
                            'Tu historial está vacío',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          SizedBox(height: 6),
                          Text(
                            'Cuando reproduzcas algo, aparecerá aquí.',
                            style: TextStyle(color: Colors.white54),
                          ),
                        ],
                      ),
                    )
                  : GridViewContentView<_HistoryEntry>(
                      items: historyEntries,
                      itemBuilder: (entry) => _HistoryCard(entry: entry),
                      onTap: (entry) {
                        if (entry.type == 'movie') {
                          final movie = visibleMovies.firstWhere(
                            (item) => item.movieId.toString() == entry.id,
                          );
                          _onMovieTap(movie);
                        } else {
                          final item = visibleSeries.firstWhere(
                            (series) => series.seriesId.toString() == entry.id,
                          );
                          _onSeriesTap(item);
                        }
                      },
                    ),
            ),
          ],
        );
      case ActiveView.continueWatching:
        final progress = WatchProgressStore()
            .getAll()
            .where((p) => p.fraction > 0 && p.fraction < .95)
            .toList();
        return GridViewContentView<WatchProgress>(
          items: progress,
          itemBuilder: (p) {
            final movie = p.id.startsWith('movie:')
                ? visibleMovies
                      .where((m) => m.movieId.toString() == p.id.substring(6))
                      .firstOrNull
                : null;
            final series = movie == null
                ? visibleSeries
                      .where((s) => p.title.startsWith(s.title))
                      .firstOrNull
                : null;
            return _ContinueWatchingCard(
              progress: p,
              imageUrl: movie?.logo ?? series?.logo ?? '',
            );
          },
          onTap: (p) {
            if (p.id.startsWith('movie:')) {
              final id = int.tryParse(p.id.substring(6));
              final movie = visibleMovies
                  .where((m) => m.movieId == id)
                  .firstOrNull;
              if (movie != null) _onMovieTap(movie);
            } else {
              final series = visibleSeries
                  .where((s) => p.title.startsWith(s.title))
                  .firstOrNull;
              if (series != null) _onSeriesTap(series);
            }
          },
          onLongPress: _removeContinueWatching,
        );
      case ActiveView.live:
        final catFiltered = _selectedLiveCatId == _favoritesCategoryId
            ? orderedFavChannels
            : _selectedLiveCatId == null
            ? visibleLiveChannels
            : visibleLiveChannels
                  .where(
                    (c) =>
                        c.categoryId.toString() ==
                        _selectedLiveCatId.toString(),
                  )
                  .toList();

        final liveFiltered = _liveSearchQuery.isEmpty
            ? catFiltered
            : catFiltered
                  .where(
                    (c) => c.channelName.toLowerCase().contains(
                      _liveSearchQuery.toLowerCase(),
                    ),
                  )
                  .toList();

        final isFavSelected = _selectedLiveCatId == _favoritesCategoryId;

        return CategoryContentView<LiveCategory, LiveChannel>(
          onBack: () => _navigateTo(ActiveView.home),
          categoriesOnSide: true,
          categories: liveCategoriesWithFavorites,
          selectedCategoryId: _selectedLiveCatId,
          getCatId: (c) => c.categoryId,
          getCatName: (c) => c.categoryName,
          items: liveFiltered,
          searchQuery: _liveSearchQuery,
          onSearchChanged: (q) => setState(() => _liveSearchQuery = q),
          onSelectCategory: (id) => setState(() => _selectedLiveCatId = id),
          externalRightFocusNode: _epgFocusNode,
          headerActions: [
            IconButton(
              focusNode: _epgFocusNode,
              tooltip: 'Guía EPG completa',
              icon: const Icon(
                Icons.calendar_month,
                color: Colors.white70,
                size: 20,
              ),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) =>
                      EpgGuideScreen(client: _client, channels: _liveChannels),
                ),
              ),
            ),
            IconButton(
              tooltip: 'Actualizar',
              icon: const Icon(Icons.refresh, color: Colors.white70, size: 20),
              onPressed: () => _loadData(forceRefresh: true),
            ),
          ],
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: _client),
          onTap: (channel) => _onChannelTap(channel, ActiveView.live),
          onLongPress: (channel) => _toggleFavoriteChannel(channel),
          onReorder: isFavSelected ? _reorderFavoriteChannels : null,
          showReorderControls: isFavSelected,
          onMoveItem: isFavSelected ? _moveFavoriteChannel : null,
          focusNodeForItem: isFavSelected
              ? (channel) => _favoriteFocusNodes.putIfAbsent(
                  channel.channelId,
                  FocusNode.new,
                )
              : null,
        );
      case ActiveView.movies:
        final catFiltered = _selectedVodCatId == null
            ? visibleMovies
            : visibleMovies
                  .where(
                    (m) =>
                        m.categoryId.toString() == _selectedVodCatId.toString(),
                  )
                  .toList();
        final movFiltered = _movieSearchQuery.isEmpty
            ? catFiltered
            : catFiltered
                  .where(
                    (m) => m.title.toLowerCase().contains(
                      _movieSearchQuery.toLowerCase(),
                    ),
                  )
                  .toList();

        return CategoryContentView<VodCategory, VodMovie>(
          onBack: () => _navigateTo(ActiveView.home),
          categoriesOnSide: true,
          categories: _vodCategories,
          selectedCategoryId: _selectedVodCatId,
          getCatId: (c) => c.categoryId,
          getCatName: (c) => c.categoryName,
          items: movFiltered,
          searchQuery: _movieSearchQuery,
          onSearchChanged: (q) => setState(() => _movieSearchQuery = q),
          onSelectCategory: (id) => setState(() => _selectedVodCatId = id),
          itemBuilder: (movie) => _PosterContent(
            title: movie.title,
            imageUrl: movie.logo,
            icon: Icons.movie_outlined,
            metadata: movie.year > 0 ? movie.year.toString() : 'Película',
            accent: const Color(0xFFFFC857),
          ),
          onTap: _onMovieTap,
        );
      case ActiveView.series:
        final catFiltered = _selectedSeriesCatId == null
            ? visibleSeries
            : visibleSeries
                  .where(
                    (s) =>
                        s.categoryId.toString() ==
                        _selectedSeriesCatId.toString(),
                  )
                  .toList();
        final serFiltered = _seriesSearchQuery.isEmpty
            ? catFiltered
            : catFiltered
                  .where(
                    (s) => s.title.toLowerCase().contains(
                      _seriesSearchQuery.toLowerCase(),
                    ),
                  )
                  .toList();

        return CategoryContentView<SeriesCategory, Series>(
          onBack: () => _navigateTo(ActiveView.home),
          categoriesOnSide: true,
          categories: _seriesCategories,
          selectedCategoryId: _selectedSeriesCatId,
          getCatId: (c) => c.categoryId,
          getCatName: (c) => c.categoryName,
          items: serFiltered,
          searchQuery: _seriesSearchQuery,
          onSearchChanged: (q) => setState(() => _seriesSearchQuery = q),
          onSelectCategory: (id) => setState(() => _selectedSeriesCatId = id),
          itemBuilder: (s) => _PosterContent(
            title: s.title,
            imageUrl: s.logo,
            icon: Icons.tv_outlined,
            metadata: s.year > 0 ? s.year.toString() : 'Serie',
            accent: const Color(0xFFFF6B4A),
          ),
          onTap: _onSeriesTap,
        );
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _activeView == ActiveView.home,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        if (_activeView != ActiveView.home) {
          setState(() {
            _activeView = ActiveView.home;
          });
        }
      },
      child: Focus(
        // This Focus only listens to bubbled events. It must not take the
        // initial focus away from the dashboard and its D-pad controls.
        canRequestFocus: false,
        onKeyEvent: (node, event) => _handleColorKey(event),
        child: Scaffold(body: SafeArea(child: _buildBody())),
      ),
    );
  }
}
