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
import 'package:iptv_flutter/presentation/player/player_screen.dart';
import 'package:iptv_flutter/presentation/home/movie_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/series_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/search_results_screen.dart';
import 'package:iptv_flutter/presentation/settings/settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.client});

  final XtreamApiClient client;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

enum ActiveView { home, live, movies, series, favorites, history, settings }

const _favoritesCategoryId = '__favorites__';

class _HistoryEntry {
  final String type;
  final String id;
  final String title;
  final String logo;

  const _HistoryEntry({
    required this.type,
    required this.id,
    required this.title,
    required this.logo,
  });
}

class _HomeScreenState extends State<HomeScreen> {
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

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _loadData();
    _startAutoRefreshTimer();
  }

  @override
  void dispose() {
    _autoRefreshTimer?.cancel();
    for (final node in _favoriteFocusNodes.values) {
      node.dispose();
    }
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

      return _liveChannels.isNotEmpty ||
          _movies.isNotEmpty ||
          _series.isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  Future<void> _fetchAndCacheRemoteData({bool silent = false}) async {
    try {
      final results = await Future.wait([
        widget.client.getLiveStreams(),
        widget.client.getVodStreams(),
        widget.client.getSeries(),
        widget.client.getLiveCategories(),
        widget.client.getVodCategories(),
        widget.client.getSeriesCategories(),
      ]);

      final rawChannels = results[0] as List<dynamic>;
      final rawMovies = results[1] as List<dynamic>;
      final rawSeries = results[2] as List<dynamic>;
      final rawLiveCats = results[3] as List<dynamic>;
      final rawVodCats = results[4] as List<dynamic>;
      final rawSeriesCats = results[5] as List<dynamic>;

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

      final storage = SharedPrefsStorage();
      await storage.setCacheString(
        'cache_live_channels',
        json.encode(channelsParsed.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_movies',
        json.encode(moviesParsed.map((e) => e.toJson()).toList()),
      );
      await storage.setCacheString(
        'cache_series',
        json.encode(seriesParsed.map((e) => e.toJson()).toList()),
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

  /// Builds the stream URL for a live channel using the Xtream Codes format.
  String _buildStreamUrl(LiveChannel channel) {
    final base = widget.client.baseUrl.replaceAll(RegExp(r'/$'), '');
    return '$base/${widget.client.username}/${widget.client.password}/${channel.channelId}';
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
        builder: (_) => MovieDetailScreen(movie: movie, client: widget.client),
      ),
    );
  }

  void _onSeriesTap(Series series) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            SeriesDetailScreen(series: series, client: widget.client),
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
    if (newIndex > oldIndex) newIndex--;
    if (oldIndex < 0 || oldIndex >= favChannels.length) return;
    if (newIndex < 0 || newIndex >= favChannels.length) return;
    final movedItem = favChannels.removeAt(oldIndex);
    favChannels.insert(newIndex, movedItem);

    final newFavIds = favChannels.map((c) => c.channelId.toString()).toList();
    final storage = SharedPrefsStorage();
    await storage.setStringList('favorite_channels', newFavIds);

    setState(() {
      // Re-sort _liveChannels favorites according to newFavIds order
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
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
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
      );
    }

    // Visible items based on filtered categories
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
          historyCount: _historyEntries(visibleMovies, visibleSeries).length,
          onSelectLive: () => _navigateTo(ActiveView.live),
          onSelectMovies: () => _navigateTo(ActiveView.movies),
          onSelectSeries: () => _navigateTo(ActiveView.series),
          onSelectHistory: () => _navigateTo(ActiveView.history),
          onSelectSettings: () => _navigateTo(ActiveView.settings),
          // Global search
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
          client: widget.client,
        );
      case ActiveView.settings:
        return SettingsScreen(
          client: widget.client,
          liveCategories: _allLiveCategories,
          vodCategories: _allVodCategories,
          seriesCategories: _allSeriesCategories,
          onSettingsSaved: () {
            _startAutoRefreshTimer();
            _loadData(forceRefresh: false);
            _navigateTo(ActiveView.home);
          },
        );
      case ActiveView.favorites:
        return GridViewContentView<LiveChannel>(
          items: orderedFavChannels,
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: widget.client),
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
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: widget.client),
          onTap: (channel) => _onChannelTap(channel, ActiveView.live),
          onLongPress: (channel) => _toggleFavoriteChannel(channel),
          onReorder: _selectedLiveCatId == _favoritesCategoryId
              ? _reorderFavoriteChannels
              : null,
          showReorderControls: _selectedLiveCatId == _favoritesCategoryId,
          onMoveItem: _selectedLiveCatId == _favoritesCategoryId
              ? _moveFavoriteChannel
              : null,
          focusNodeForItem: _selectedLiveCatId == _favoritesCategoryId
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
      child: Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              Positioned.fill(child: _buildBody()),
              if (_activeView == ActiveView.home)
                Positioned(
                  top: 8,
                  right: 8,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Ajustes',
                        icon: const Icon(
                          Icons.settings,
                          color: Colors.white70,
                          size: 20,
                        ),
                        onPressed: () => _navigateTo(ActiveView.settings),
                      ),
                      IconButton(
                        tooltip: 'Actualizar',
                        icon: const Icon(
                          Icons.refresh,
                          color: Colors.white70,
                          size: 20,
                        ),
                        onPressed: () => _loadData(forceRefresh: true),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class HomeCenterDashboard extends StatefulWidget {
  final int totalChannels;
  final int totalMovies;
  final int totalSeries;
  final int totalFavorites;
  final int historyCount;
  final VoidCallback onSelectLive;
  final VoidCallback onSelectMovies;
  final VoidCallback onSelectSeries;
  final VoidCallback onSelectHistory;
  final VoidCallback onSelectSettings;
  // Global search
  final String globalSearchQuery;
  final ValueChanged<String> onGlobalSearchChanged;
  final List<LiveChannel> allChannels;
  final List<VodMovie> allMovies;
  final List<Series> allSeries;
  final ValueChanged<String> onSearchSubmitted;
  final void Function(LiveChannel) onChannelTap;
  final void Function(VodMovie) onMovieTap;
  final void Function(Series) onSeriesTap;
  final XtreamApiClient client;

  const HomeCenterDashboard({
    super.key,
    required this.totalChannels,
    required this.totalMovies,
    required this.totalSeries,
    required this.totalFavorites,
    required this.historyCount,
    required this.onSelectLive,
    required this.onSelectMovies,
    required this.onSelectSeries,
    required this.onSelectHistory,
    required this.onSelectSettings,
    required this.globalSearchQuery,
    required this.onGlobalSearchChanged,
    required this.allChannels,
    required this.allMovies,
    required this.allSeries,
    required this.onSearchSubmitted,
    required this.onChannelTap,
    required this.onMovieTap,
    required this.onSeriesTap,
    required this.client,
  });

  @override
  State<HomeCenterDashboard> createState() => _HomeCenterDashboardState();
}

class _HomeCenterDashboardState extends State<HomeCenterDashboard> {
  final FocusNode _searchFocusNode = FocusNode();
  final FocusNode _firstButtonFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _searchFocusNode.dispose();
    _firstButtonFocusNode.dispose();
    super.dispose();
  }

  Future<void> _openSearchDialog() async {
    final query = await showDialog<String>(
      context: context,
      builder: (_) => _TvSearchDialog(initialQuery: widget.globalSearchQuery),
    );

    if (!mounted || query == null || query.isEmpty) return;
    widget.onGlobalSearchChanged(query);
    widget.onSearchSubmitted(query);
  }

  @override
  Widget build(BuildContext context) {
    final query = widget.globalSearchQuery.toLowerCase();
    final isSearching = query.isNotEmpty;

    final filteredChannels = isSearching
        ? widget.allChannels
              .where((c) => c.channelName.toLowerCase().contains(query))
              .toList()
        : <LiveChannel>[];
    final filteredMovies = isSearching
        ? widget.allMovies
              .where((m) => m.title.toLowerCase().contains(query))
              .toList()
        : <VodMovie>[];
    final filteredSeries = isSearching
        ? widget.allSeries
              .where((s) => s.title.toLowerCase().contains(query))
              .toList()
        : <Series>[];

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B1117), Color(0xFF12232A)],
        ),
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 22, 24, 32),
        child: Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: 1100,
              minHeight: MediaQuery.sizeOf(context).height - 54,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _LibraryStat(
                      icon: Icons.live_tv,
                      value: widget.totalChannels,
                      label: 'canales',
                      color: const Color(0xFF5DE0C2),
                    ),
                    _LibraryStat(
                      icon: Icons.movie_outlined,
                      value: widget.totalMovies,
                      label: 'películas',
                      color: const Color(0xFFFFC857),
                    ),
                    _LibraryStat(
                      icon: Icons.tv_outlined,
                      value: widget.totalSeries,
                      label: 'series',
                      color: const Color(0xFFFF6B4A),
                    ),
                    _LibraryStat(
                      icon: Icons.history,
                      value: widget.historyCount,
                      label: 'en historial',
                      color: const Color(0xFF9D8CFF),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                _SearchActionButton(
                  query: widget.globalSearchQuery,
                  focusNode: _searchFocusNode,
                  onPressed: _openSearchDialog,
                ),
                const SizedBox(height: 28),
                const Text(
                  'Explorar biblioteca',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 16,
                  runSpacing: 16,
                  alignment: WrapAlignment.center,
                  children: [
                    _MenuCenterButton(
                      icon: Icons.live_tv,
                      label: 'Canales en Vivo',
                      accent: const Color(0xFF5DE0C2),
                      subtitle: null,
                      onPressed: widget.onSelectLive,
                      focusNode: _firstButtonFocusNode,
                      autofocus: true,
                    ),
                    _MenuCenterButton(
                      icon: Icons.movie,
                      label: 'Películas',
                      accent: const Color(0xFFFFC857),
                      subtitle: null,
                      onPressed: widget.onSelectMovies,
                    ),
                    _MenuCenterButton(
                      icon: Icons.tv_outlined,
                      label: 'Series',
                      accent: const Color(0xFFFF6B4A),
                      subtitle: null,
                      onPressed: widget.onSelectSeries,
                    ),
                    _MenuCenterButton(
                      icon: Icons.history,
                      label: 'Historial',
                      accent: const Color(0xFF9D8CFF),
                      subtitle: null,
                      onPressed: widget.onSelectHistory,
                    ),
                  ],
                ),
                if (isSearching) ...[
                  Offstage(
                    offstage: true,
                    child: Column(
                      children: [
                        if (filteredChannels.isEmpty &&
                            filteredMovies.isEmpty &&
                            filteredSeries.isEmpty)
                          const Padding(
                            padding: EdgeInsets.only(top: 32),
                            child: Text(
                              'No se encontraron resultados',
                              style: TextStyle(
                                color: Colors.white54,
                                fontSize: 16,
                              ),
                            ),
                          )
                        else ...[
                          if (filteredChannels.isNotEmpty) ...[
                            _SearchResultSection(
                              title:
                                  'Canales en Vivo (${filteredChannels.length})',
                              icon: Icons.live_tv,
                              children: filteredChannels
                                  .take(10)
                                  .map(
                                    (ch) => _SearchResultTile(
                                      title: ch.channelName,
                                      subtitle: ch.currentEpgTitle.isNotEmpty
                                          ? ch.currentEpgTitle
                                          : null,
                                      leading: ch.channelLogo.isNotEmpty
                                          ? Image.network(
                                              ch.channelLogo,
                                              width: 40,
                                              height: 40,
                                              fit: BoxFit.contain,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.tv,
                                                    color: Colors.white54,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.tv,
                                              color: Colors.white54,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onChannelTap(ch),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                          if (filteredMovies.isNotEmpty) ...[
                            _SearchResultSection(
                              title: 'Películas (${filteredMovies.length})',
                              icon: Icons.movie,
                              children: filteredMovies
                                  .take(10)
                                  .map(
                                    (m) => _SearchResultTile(
                                      title: m.title,
                                      subtitle: m.year > 0
                                          ? m.year.toString()
                                          : null,
                                      leading: m.logo.isNotEmpty
                                          ? Image.network(
                                              m.logo,
                                              width: 40,
                                              height: 56,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.movie,
                                                    color: Colors.amber,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.movie,
                                              color: Colors.amber,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onMovieTap(m),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                          if (filteredSeries.isNotEmpty) ...[
                            _SearchResultSection(
                              title: 'Series (${filteredSeries.length})',
                              icon: Icons.tv_outlined,
                              children: filteredSeries
                                  .take(10)
                                  .map(
                                    (s) => _SearchResultTile(
                                      title: s.title,
                                      subtitle: null,
                                      leading: s.logo.isNotEmpty
                                          ? Image.network(
                                              s.logo,
                                              width: 40,
                                              height: 56,
                                              fit: BoxFit.cover,
                                              errorBuilder: (_, __, ___) =>
                                                  const Icon(
                                                    Icons.tv_outlined,
                                                    color: Colors.amber,
                                                    size: 32,
                                                  ),
                                            )
                                          : const Icon(
                                              Icons.tv_outlined,
                                              color: Colors.amber,
                                              size: 32,
                                            ),
                                      onTap: () => widget.onSeriesTap(s),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                        ],
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _TvSearchDialog extends StatefulWidget {
  final String initialQuery;

  const _TvSearchDialog({required this.initialQuery});

  @override
  State<_TvSearchDialog> createState() => _TvSearchDialogState();
}

class _TvSearchDialogState extends State<_TvSearchDialog> {
  late final TextEditingController _controller;
  final FocusNode _inputFocusNode = FocusNode();
  final FocusNode _cancelFocusNode = FocusNode();
  final FocusNode _searchFocusNode = FocusNode();
  bool _cancelHasFocus = false;
  bool _searchHasFocus = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery);
  }

  @override
  void dispose() {
    _controller.dispose();
    _inputFocusNode.dispose();
    _cancelFocusNode.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(_controller.text.trim());

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Align(
        alignment: Alignment.topCenter,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(32, 20, 32, 0),
          child: Material(
            color: const Color(0xFF15212A),
            elevation: 16,
            shadowColor: Colors.black54,
            borderRadius: BorderRadius.circular(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Buscar en tu biblioteca',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Escribe lo que buscas o pulsa abajo para acceder a las acciones.',
                      style: TextStyle(color: Colors.white60, fontSize: 13),
                    ),
                    const SizedBox(height: 20),
                    Focus(
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            event.logicalKey == LogicalKeyboardKey.arrowDown) {
                          _searchFocusNode.requestFocus();
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: TextField(
                        controller: _controller,
                        focusNode: _inputFocusNode,
                        autofocus: true,
                        textInputAction: TextInputAction.search,
                        decoration: const InputDecoration(
                          hintText: 'Canal, película o serie',
                          prefixIcon: Icon(
                            Icons.search,
                            color: Color(0xFF5DE0C2),
                          ),
                        ),
                        onSubmitted: (_) => _submit(),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: Focus(
                            focusNode: _cancelFocusNode,
                            onFocusChange: (hasFocus) =>
                                setState(() => _cancelHasFocus = hasFocus),
                            onKeyEvent: (node, event) {
                              if (event is KeyDownEvent &&
                                  (event.logicalKey ==
                                          LogicalKeyboardKey.select ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey.enter ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey
                                              .gameButtonSelect)) {
                                Navigator.of(context).pop();
                                return KeyEventResult.handled;
                              }
                              if (event is KeyDownEvent &&
                                  event.logicalKey ==
                                      LogicalKeyboardKey.arrowUp) {
                                _inputFocusNode.requestFocus();
                                return KeyEventResult.handled;
                              }
                              return KeyEventResult.ignored;
                            },
                            child: OutlinedButton(
                              style: OutlinedButton.styleFrom(
                                foregroundColor: Colors.white,
                                backgroundColor: _cancelHasFocus
                                    ? const Color(
                                        0xFF5DE0C2,
                                      ).withValues(alpha: 0.16)
                                    : Colors.transparent,
                                side: BorderSide(
                                  color: _cancelHasFocus
                                      ? const Color(0xFF5DE0C2)
                                      : Colors.white54,
                                  width: _cancelHasFocus ? 2 : 1,
                                ),
                              ),
                              onPressed: () => Navigator.of(context).pop(),
                              child: const Text('Cancelar'),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Focus(
                            focusNode: _searchFocusNode,
                            onFocusChange: (hasFocus) =>
                                setState(() => _searchHasFocus = hasFocus),
                            onKeyEvent: (node, event) {
                              if (event is KeyDownEvent &&
                                  (event.logicalKey ==
                                          LogicalKeyboardKey.select ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey.enter ||
                                      event.logicalKey ==
                                          LogicalKeyboardKey
                                              .gameButtonSelect)) {
                                _submit();
                                return KeyEventResult.handled;
                              }
                              if (event is KeyDownEvent &&
                                  event.logicalKey ==
                                      LogicalKeyboardKey.arrowUp) {
                                _inputFocusNode.requestFocus();
                                return KeyEventResult.handled;
                              }
                              return KeyEventResult.ignored;
                            },
                            child: FilledButton.icon(
                              style: FilledButton.styleFrom(
                                backgroundColor: _searchHasFocus
                                    ? const Color(0xFFFF6B4A)
                                    : const Color(0xFFB94732),
                                side: BorderSide(
                                  color: _searchHasFocus
                                      ? Colors.white
                                      : Colors.transparent,
                                  width: 2,
                                ),
                              ),
                              onPressed: _submit,
                              icon: const Icon(Icons.search),
                              label: const Text('Buscar'),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchActionButton extends StatelessWidget {
  final String query;
  final FocusNode focusNode;
  final VoidCallback onPressed;

  const _SearchActionButton({
    required this.query,
    required this.focusNode,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: focusNode,
      autofocus: false,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: onPressed,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: hasFocus
                    ? const Color(0xFFFF6B4A)
                    : const Color(0xFF15212A),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: hasFocus ? Colors.white : Colors.white12,
                  width: hasFocus ? 2 : 1,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.search,
                    color: hasFocus ? Colors.white : const Color(0xFF5DE0C2),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      query.isEmpty ? 'Buscar en todo el contenido' : query,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: query.isEmpty ? Colors.white70 : Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Icon(
                    Icons.arrow_forward_ios,
                    color: hasFocus ? Colors.white70 : Colors.white38,
                    size: 15,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _LibraryStat extends StatelessWidget {
  final IconData icon;
  final int value;
  final String label;
  final Color color;

  const _LibraryStat({
    required this.icon,
    required this.value,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 19),
          const SizedBox(width: 8),
          Text(
            '$value',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(width: 5),
          Text(label, style: const TextStyle(color: Colors.white60)),
        ],
      ),
    );
  }
}

class _SearchResultSection extends StatelessWidget {
  final String title;
  final IconData icon;
  final List<Widget> children;

  const _SearchResultSection({
    required this.title,
    required this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            children: [
              Icon(icon, color: const Color(0xFFE91E63), size: 20),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
        ...children,
        const SizedBox(height: 8),
      ],
    );
  }
}

class _SearchResultTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final Widget leading;
  final VoidCallback onTap;

  const _SearchResultTile({
    required this.title,
    required this.subtitle,
    required this.leading,
    required this.onTap,
  });

  @override
  State<_SearchResultTile> createState() => _SearchResultTileState();
}

class _SearchResultTileState extends State<_SearchResultTile> {
  bool _hasFocus = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      onFocusChange: (focused) => setState(() => _hasFocus = focused),
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          widget.onTap();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.symmetric(vertical: 2),
        decoration: BoxDecoration(
          color: _hasFocus ? const Color(0xFF2A0A1A) : const Color(0xFF1E1E1E),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _hasFocus ? const Color(0xFFE91E63) : Colors.white12,
            width: _hasFocus ? 2 : 1,
          ),
        ),
        child: ListTile(
          leading: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: SizedBox(width: 40, height: 40, child: widget.leading),
          ),
          title: Text(
            widget.title,
            style: const TextStyle(color: Colors.white, fontSize: 14),
          ),
          subtitle: widget.subtitle != null
              ? Text(
                  widget.subtitle!,
                  style: const TextStyle(color: Colors.white54, fontSize: 12),
                )
              : null,
          trailing: const Icon(Icons.chevron_right, color: Colors.white38),
          onTap: widget.onTap,
        ),
      ),
    );
  }
}

class _MenuCenterButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color accent;
  final String? subtitle;
  final VoidCallback onPressed;
  final bool autofocus;
  final FocusNode? focusNode;

  const _MenuCenterButton({
    required this.icon,
    required this.label,
    required this.accent,
    this.subtitle,
    required this.onPressed,
    this.autofocus = false,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: focusNode,
      autofocus: autofocus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: onPressed,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 176,
              height: 148,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: hasFocus
                    ? accent.withValues(alpha: 0.9)
                    : const Color(0xFF15212A),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: hasFocus
                      ? Colors.white
                      : accent.withValues(alpha: 0.3),
                  width: hasFocus ? 2 : 1,
                ),
                boxShadow: hasFocus
                    ? [
                        BoxShadow(
                          color: accent.withValues(alpha: 0.35),
                          blurRadius: 22,
                          spreadRadius: 1,
                        ),
                      ]
                    : [],
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: hasFocus
                          ? Colors.white.withValues(alpha: 0.16)
                          : accent.withValues(alpha: 0.13),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      icon,
                      size: 30,
                      color: hasFocus ? Colors.white : accent,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: hasFocus
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        color: hasFocus ? Colors.white70 : Colors.white38,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _PosterContent extends StatelessWidget {
  final String title;
  final String imageUrl;
  final IconData icon;
  final String metadata;
  final Color accent;

  const _PosterContent({
    required this.title,
    required this.imageUrl,
    required this.icon,
    required this.metadata,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (imageUrl.isNotEmpty)
                Image.network(
                  imageUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => _PosterFallback(icon: icon),
                )
              else
                _PosterFallback(icon: icon),
              Positioned(
                left: 8,
                bottom: 8,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: const Color(0xE60B1117),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: accent.withValues(alpha: 0.7)),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 7,
                      vertical: 4,
                    ),
                    child: Text(
                      metadata,
                      style: TextStyle(
                        color: accent,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(9, 8, 9, 9),
          child: Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              height: 1.15,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }
}

class _PosterFallback extends StatelessWidget {
  final IconData icon;

  const _PosterFallback({required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF20323A),
      alignment: Alignment.center,
      child: Icon(icon, color: Colors.white38, size: 42),
    );
  }
}

class _HistoryCard extends StatelessWidget {
  final _HistoryEntry entry;

  const _HistoryCard({required this.entry});

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: entry.logo.isNotEmpty
                  ? Image.network(
                      entry.logo,
                      fit: BoxFit.cover,
                      width: double.infinity,
                      errorBuilder: (_, __, ___) => Icon(
                        entry.type == 'movie' ? Icons.movie : Icons.tv_outlined,
                        color: Colors.amber,
                      ),
                    )
                  : Icon(
                      entry.type == 'movie' ? Icons.movie : Icons.tv_outlined,
                      color: Colors.amber,
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(7),
              child: Text(
                entry.title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        Positioned(
          top: 7,
          left: 7,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: const Color(0xDD0B1117),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
              child: Text(
                entry.type == 'movie' ? 'PELÍCULA' : 'SERIE',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 9,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class LiveChannelCard extends StatefulWidget {
  final LiveChannel channel;
  final XtreamApiClient client;

  const LiveChannelCard({
    super.key,
    required this.channel,
    required this.client,
  });

  @override
  State<LiveChannelCard> createState() => _LiveChannelCardState();
}

class _LiveChannelCardState extends State<LiveChannelCard> {
  String? _epgTitle;
  bool _loadingEpg = false;

  @override
  void initState() {
    super.initState();
    if (widget.channel.currentEpgTitle.isNotEmpty) {
      _epgTitle = widget.channel.currentEpgTitle;
    } else {
      _loadEpg();
    }
  }

  Future<void> _loadEpg() async {
    if (!mounted) return;
    setState(() => _loadingEpg = true);
    try {
      final data = await widget.client.getShortEpg(
        streamId: widget.channel.channelId,
      );
      final epgList = data['epg_listings'] as List<dynamic>?;
      if (epgList != null && epgList.isNotEmpty) {
        final current = epgList.first as Map<String, dynamic>;
        final rawTitle = current['title'] ?? current['now_playing'] ?? '';
        if (mounted) {
          setState(() {
            _epgTitle = LiveChannel.cleanEpgTitle(rawTitle);
            _loadingEpg = false;
          });
        }
      } else {
        if (mounted) setState(() => _loadingEpg = false);
      }
    } catch (_) {
      if (mounted) setState(() => _loadingEpg = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            Expanded(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Container(
                    margin: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFF29434A), Color(0xFF17262E)],
                      ),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.08),
                      ),
                    ),
                    padding: const EdgeInsets.all(12),
                    child: widget.channel.channelLogo.isNotEmpty
                        ? Image.network(
                            widget.channel.channelLogo,
                            fit: BoxFit.contain,
                            errorBuilder: (_, __, ___) => const Icon(
                              Icons.live_tv_rounded,
                              size: 38,
                              color: Colors.white70,
                            ),
                          )
                        : const Icon(
                            Icons.live_tv_rounded,
                            size: 38,
                            color: Colors.white70,
                          ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4.0),
              child: Text(
                widget.channel.channelName,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(height: 2),
            Padding(
              padding: const EdgeInsets.only(
                left: 4.0,
                right: 4.0,
                bottom: 6.0,
              ),
              child: _loadingEpg
                  ? const SizedBox(
                      height: 10,
                      width: 10,
                      child: CircularProgressIndicator(
                        strokeWidth: 1.5,
                        color: Colors.amberAccent,
                      ),
                    )
                  : Text(
                      _epgTitle != null && _epgTitle!.isNotEmpty
                          ? _epgTitle!
                          : 'Sin guía EPG',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Color(0xFF5DE0C2),
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
            ),
          ],
        ),
        if (widget.channel.isFavorite)
          const Positioned(
            top: 12,
            right: 12,
            child: Icon(Icons.star, color: Color(0xFFFFC857), size: 18),
          ),
      ],
    );
  }
}

class CategoryContentView<C, T> extends StatefulWidget {
  final bool categoriesOnSide;
  final List<C> categories;
  final String? selectedCategoryId;
  final String Function(C) getCatId;
  final String Function(C) getCatName;
  final List<T> items;
  final Widget Function(T) itemBuilder;
  final ValueChanged<String?> onSelectCategory;
  final void Function(T)? onTap;
  final void Function(T)? onLongPress;
  final ReorderCallback? onReorder;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final FocusNode? Function(T item)? focusNodeForItem;
  // Search
  final String searchQuery;
  final ValueChanged<String> onSearchChanged;
  final VoidCallback onBack;

  const CategoryContentView({
    super.key,
    required this.categories,
    required this.selectedCategoryId,
    required this.getCatId,
    required this.getCatName,
    required this.items,
    required this.itemBuilder,
    required this.onSelectCategory,
    required this.searchQuery,
    required this.onSearchChanged,
    required this.onBack,
    this.categoriesOnSide = false,
    this.onTap,
    this.onLongPress,
    this.onReorder,
    this.showReorderControls = false,
    this.onMoveItem,
    this.focusNodeForItem,
  });

  @override
  State<CategoryContentView<C, T>> createState() =>
      _CategoryContentViewState<C, T>();
}

class _CategoryContentViewState<C, T> extends State<CategoryContentView<C, T>> {
  final FocusNode _searchFocusNode = FocusNode();
  late TextEditingController _searchController;
  // FocusNode for the first grid item so we can focus it programmatically
  final FocusNode _gridFirstItemFocusNode = FocusNode();
  final Map<String, FocusNode> _sidebarCategoryFocusNodes = {};
  final ValueNotifier<String?> _focusedSidebarCategoryId = ValueNotifier(null);

  FocusNode _sidebarFocusNodeFor(String? categoryId) {
    final key = categoryId ?? '__all__';
    return _sidebarCategoryFocusNodes.putIfAbsent(key, FocusNode.new);
  }

  String? get _sidebarEntryCategoryId =>
      widget.selectedCategoryId ??
      (widget.categories.isEmpty
          ? null
          : widget.getCatId(widget.categories.first));

  void _focusSelectedSidebarCategory() {
    final targetId = _sidebarEntryCategoryId;
    _focusedSidebarCategoryId.value = targetId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        FocusScope.of(context).requestFocus(_sidebarFocusNodeFor(targetId));
      }
    });
  }

  void _exitSidebarToGrid() {
    _focusedSidebarCategoryId.value = null;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _gridFirstItemFocusNode.requestFocus();
    });
  }

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.searchQuery)
      ..selection = TextSelection.collapsed(offset: widget.searchQuery.length);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      }
    });
  }

  @override
  void didUpdateWidget(covariant CategoryContentView<C, T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.searchQuery != widget.searchQuery) {
      _searchController.text = widget.searchQuery;
      _searchController.selection = TextSelection.collapsed(
        offset: widget.searchQuery.length,
      );
    }
  }

  @override
  void dispose() {
    _searchFocusNode.dispose();
    _searchController.dispose();
    _gridFirstItemFocusNode.dispose();
    _focusedSidebarCategoryId.dispose();
    for (final node in _sidebarCategoryFocusNodes.values) {
      node.dispose();
    }
    super.dispose();
  }

  /// Returns the category index currently selected (0 = "Todas", 1..n = category).
  int get _selectedCatIndex {
    if (widget.selectedCategoryId == null) return 0;
    final idx = widget.categories.indexWhere(
      (c) => widget.getCatId(c) == widget.selectedCategoryId,
    );
    return idx == -1 ? 0 : idx + 1; // +1 because index 0 is "Todas"
  }

  void _navigateCategoryLeft() {
    final current = _selectedCatIndex;
    if (current <= 0) return; // already at "Todas" or before
    final newIndex = current - 1;
    final newCatId = newIndex == 0
        ? null
        : widget.getCatId(widget.categories[newIndex - 1]);
    widget.onSelectCategory(newCatId);
    // After the rebuild the grid will have new items; focus first item
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      } else if (mounted) {
        _searchFocusNode.requestFocus();
      }
    });
  }

  void _navigateCategoryRight() {
    final current = _selectedCatIndex;
    final total = widget.categories.length + 1; // +1 for "Todas"
    if (current >= total - 1) return; // already at last category
    final newIndex = current + 1;
    final newCatId = widget.getCatId(widget.categories[newIndex - 1]);
    widget.onSelectCategory(newCatId);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.items.isNotEmpty) {
        _gridFirstItemFocusNode.requestFocus();
      } else if (mounted) {
        _searchFocusNode.requestFocus();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0B1117), Color(0xFF10232B)],
        ),
      ),
      child: Column(
        children: [
          // Search bar
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Focus(
              onFocusChange: (gained) {
                // When outer Focus gains focus via D-Pad traversal, activate the TextField
                if (gained && !_searchFocusNode.hasFocus) {
                  _searchFocusNode.requestFocus();
                }
              },
              onKeyEvent: (node, event) {
                if (event is KeyDownEvent) {
                  if (event.logicalKey == LogicalKeyboardKey.goBack ||
                      event.logicalKey == LogicalKeyboardKey.escape) {
                    widget.onBack();
                    return KeyEventResult.handled;
                  }
                  if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
                    _gridFirstItemFocusNode.requestFocus();
                    return KeyEventResult.handled;
                  }
                  if (event.logicalKey == LogicalKeyboardKey.escape) {
                    _gridFirstItemFocusNode.requestFocus();
                    return KeyEventResult.handled;
                  }
                }
                return KeyEventResult.ignored;
              },
              child: TextField(
                focusNode: _searchFocusNode,
                controller: _searchController,
                onChanged: widget.onSearchChanged,
                style: const TextStyle(color: Colors.white, fontSize: 14),
                decoration: InputDecoration(
                  hintText: 'Buscar...',
                  hintStyle: const TextStyle(color: Colors.white38),
                  prefixIcon: const Icon(
                    Icons.search,
                    color: Color(0xFF5DE0C2),
                    size: 20,
                  ),
                  suffixIcon: widget.searchQuery.isNotEmpty
                      ? IconButton(
                          icon: const Icon(
                            Icons.clear,
                            color: Colors.white54,
                            size: 18,
                          ),
                          onPressed: () {
                            widget.onSearchChanged('');
                            _gridFirstItemFocusNode.requestFocus();
                          },
                        )
                      : null,
                  isDense: true,
                  filled: true,
                  fillColor: const Color(0xFF15212A),
                  contentPadding: const EdgeInsets.symmetric(
                    vertical: 10,
                    horizontal: 12,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(color: Colors.white12),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(color: Colors.white12),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(
                      color: Color(0xFFFF6B4A),
                      width: 2,
                    ),
                  ),
                ),
              ),
            ),
          ),
          if (!widget.categoriesOnSide)
            // Horizontal category selector
            Container(
              height: 54,
              margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              decoration: BoxDecoration(
                color: const Color(0xFF15212A).withValues(alpha: 0.9),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: const Color(0xFF5DE0C2).withValues(alpha: 0.18),
                ),
              ),
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 7,
                ),
                itemCount: widget.categories.length + 1,
                itemBuilder: (context, index) {
                  final isAll = index == 0;
                  final catId = isAll
                      ? null
                      : widget.getCatId(widget.categories[index - 1]);
                  final isSelected = isAll
                      ? widget.selectedCategoryId == null
                      : widget.selectedCategoryId.toString() ==
                            catId.toString();
                  final title = isAll
                      ? 'Todas'
                      : widget.getCatName(widget.categories[index - 1]);

                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: Focus(
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            (event.logicalKey == LogicalKeyboardKey.select ||
                                event.logicalKey == LogicalKeyboardKey.enter ||
                                event.logicalKey ==
                                    LogicalKeyboardKey.gameButtonSelect)) {
                          widget.onSelectCategory(catId);
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: Builder(
                        builder: (context) {
                          final hasFocus = Focus.of(context).hasFocus;
                          return ChoiceChip(
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                              side: BorderSide(
                                color: isSelected
                                    ? Colors.white24
                                    : Colors.transparent,
                              ),
                            ),
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            avatar: Icon(
                              isAll ? Icons.apps : Icons.label_outline,
                              size: 16,
                              color: isSelected || hasFocus
                                  ? Colors.white
                                  : const Color(0xFF5DE0C2),
                            ),
                            label: Text(
                              title,
                              style: TextStyle(
                                color: isSelected || hasFocus
                                    ? Colors.white
                                    : Colors.white60,
                                fontWeight: isSelected || hasFocus
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                              ),
                            ),
                            selected: isSelected,
                            selectedColor: const Color(0xFFFF6B4A),
                            backgroundColor: hasFocus
                                ? const Color(0xFFB94732)
                                : const Color(0xFF1D3039),
                            onSelected: (_) => widget.onSelectCategory(catId),
                          );
                        },
                      ),
                    ),
                  );
                },
              ),
            ),
          if (!widget.categoriesOnSide)
            Container(
              height: 1,
              margin: const EdgeInsets.symmetric(horizontal: 16),
              color: const Color(0xFF5DE0C2).withValues(alpha: 0.12),
            ),
          // Grid content view with 7 columns
          if (widget.categoriesOnSide)
            Expanded(
              child: Row(
                children: [
                  _CategorySidebar<C>(
                    categories: widget.categories,
                    selectedCategoryId: widget.selectedCategoryId,
                    getCatId: widget.getCatId,
                    getCatName: widget.getCatName,
                    onSelectCategory: widget.onSelectCategory,
                    focusNodeForCategory: _sidebarFocusNodeFor,
                    focusedCategoryId: _focusedSidebarCategoryId,
                    onExitRight: _exitSidebarToGrid,
                  ),
                  Expanded(
                    child: GridViewContentView<T>(
                      key: ValueKey(widget.selectedCategoryId ?? '__all__'),
                      items: widget.items,
                      itemBuilder: widget.itemBuilder,
                      onTap: widget.onTap,
                      onLongPress: widget.onLongPress,
                      onReorder: widget.onReorder,
                      showReorderControls: widget.showReorderControls,
                      onMoveItem: widget.onMoveItem,
                      focusNodeForItem: widget.focusNodeForItem,
                      firstItemFocusNode: _gridFirstItemFocusNode,
                      onAtLeftEdge: _focusSelectedSidebarCategory,
                      onAtRightEdge: _navigateCategoryRight,
                    ),
                  ),
                ],
              ),
            )
          else
            Expanded(
              child: GridViewContentView<T>(
                key: ValueKey(widget.selectedCategoryId ?? '__all__'),
                items: widget.items,
                itemBuilder: widget.itemBuilder,
                onTap: widget.onTap,
                onLongPress: widget.onLongPress,
                onReorder: widget.onReorder,
                showReorderControls: widget.showReorderControls,
                onMoveItem: widget.onMoveItem,
                focusNodeForItem: widget.focusNodeForItem,
                firstItemFocusNode: _gridFirstItemFocusNode,
                onAtLeftEdge: _navigateCategoryLeft,
                onAtRightEdge: _navigateCategoryRight,
              ),
            ),
        ],
      ),
    );
  }
}

class _CategorySidebar<C> extends StatelessWidget {
  final List<C> categories;
  final String? selectedCategoryId;
  final String Function(C) getCatId;
  final String Function(C) getCatName;
  final ValueChanged<String?> onSelectCategory;
  final FocusNode Function(String? categoryId) focusNodeForCategory;
  final ValueNotifier<String?> focusedCategoryId;
  final VoidCallback onExitRight;

  const _CategorySidebar({
    required this.categories,
    required this.selectedCategoryId,
    required this.getCatId,
    required this.getCatName,
    required this.onSelectCategory,
    required this.focusNodeForCategory,
    required this.focusedCategoryId,
    required this.onExitRight,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: MediaQuery.sizeOf(context).width < 700 ? 148 : 210,
      margin: const EdgeInsets.fromLTRB(16, 6, 4, 20),
      decoration: BoxDecoration(
        color: const Color(0xFF15212A).withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: ListView(
        padding: const EdgeInsets.all(8),
        children: List.generate(categories.length, (index) {
          final category = categories[index];
          final id = getCatId(category);
          final selected = id.toString() == selectedCategoryId.toString();
          final label = getCatName(category);

          final focusKey = id;
          return ValueListenableBuilder<String?>(
            valueListenable: focusedCategoryId,
            builder: (context, focusedCategory, _) => Focus(
              focusNode: focusNodeForCategory(id),
              onFocusChange: (hasFocus) {
                if (hasFocus) focusedCategoryId.value = focusKey;
              },
              onKeyEvent: (node, event) {
                if (event is! KeyDownEvent) return KeyEventResult.ignored;
                if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
                  onExitRight();
                  return KeyEventResult.handled;
                }
                if (event.logicalKey == LogicalKeyboardKey.select ||
                    event.logicalKey == LogicalKeyboardKey.enter ||
                    event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
                  onSelectCategory(id);
                  WidgetsBinding.instance.addPostFrameCallback(
                    (_) => onExitRight(),
                  );
                  return KeyEventResult.handled;
                }
                return KeyEventResult.ignored;
              },
              child: Builder(
                builder: (context) {
                  final hasFocus = focusedCategory == focusKey;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Material(
                      color: Colors.transparent,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(10),
                        onTap: () => onSelectCategory(id),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 160),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 11,
                          ),
                          decoration: BoxDecoration(
                            color: hasFocus
                                ? const Color(
                                    0xFF5DE0C2,
                                  ).withValues(alpha: 0.16)
                                : selected
                                ? const Color(
                                    0xFFFF6B4A,
                                  ).withValues(alpha: 0.18)
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: hasFocus
                                  ? const Color(0xFF5DE0C2)
                                  : selected
                                  ? const Color(
                                      0xFFFF6B4A,
                                    ).withValues(alpha: 0.5)
                                  : Colors.transparent,
                              width: hasFocus ? 2 : 1,
                            ),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.label_outline_rounded,
                                size: 17,
                                color: hasFocus
                                    ? const Color(0xFF5DE0C2)
                                    : selected
                                    ? const Color(0xFFFF8B70)
                                    : const Color(0xFF8CAAA9),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  label,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: hasFocus || selected
                                        ? Colors.white
                                        : Colors.white70,
                                    fontSize: 12,
                                    fontWeight: selected
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          );
        }),
      ),
    );
  }
}

class GridViewContentView<T> extends StatelessWidget {
  final List<T> items;
  final Widget Function(T) itemBuilder;

  /// Called when the user taps (or short-presses D-Pad select) on an item.
  final void Function(T)? onTap;

  /// Called when the user long-presses (or holds D-Pad select ≥600ms) on an item.
  final void Function(T)? onLongPress;
  final ReorderCallback? onReorder;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final FocusNode? Function(T item)? focusNodeForItem;

  /// FocusNode assigned to the first item so parent can focus it programmatically.
  final FocusNode? firstItemFocusNode;

  /// Called when the focused item is at the left edge of the grid and user presses left.
  final VoidCallback? onAtLeftEdge;

  /// Called when the focused item is at the right edge of the grid and user presses right.
  final VoidCallback? onAtRightEdge;

  const GridViewContentView({
    super.key,
    required this.items,
    required this.itemBuilder,
    this.onTap,
    this.onLongPress,
    this.onReorder,
    this.showReorderControls = false,
    this.onMoveItem,
    this.focusNodeForItem,
    this.firstItemFocusNode,
    this.onAtLeftEdge,
    this.onAtRightEdge,
  });

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final crossAxisCount = width >= 1200
        ? 6
        : width >= 800
        ? 5
        : width >= 600
        ? 4
        : 2;

    if (items.isEmpty) {
      return const Center(
        child: Text(
          'No hay contenido disponible',
          style: TextStyle(color: Colors.white70),
        ),
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 20),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: crossAxisCount,
        childAspectRatio: 0.8,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
      ),
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        final isFirstItem = index == 0;
        final isLeftEdge = index % crossAxisCount == 0;
        final isRightEdge =
            index % crossAxisCount == crossAxisCount - 1 ||
            index == items.length - 1;
        final isBottomEdge = index + crossAxisCount >= items.length;
        return _GridItem<T>(
          key: ValueKey(item.hashCode),
          item: item,
          itemBuilder: itemBuilder,
          onTap: onTap,
          onLongPress: onLongPress,
          focusNode:
              focusNodeForItem?.call(item) ??
              (isFirstItem ? firstItemFocusNode : null),
          onMoveLeft: onReorder != null && index > 0
              ? () => onReorder!(index, index - 1)
              : null,
          onMoveRight: onReorder != null && index < items.length - 1
              ? () => onReorder!(index, index + 2)
              : null,
          onEdgeLeft: isLeftEdge ? onAtLeftEdge : null,
          onEdgeRight: isRightEdge ? onAtRightEdge : null,
          isBottomEdge: isBottomEdge,
          autofocus: isFirstItem,
          showReorderControls: showReorderControls,
          onMoveItem: onMoveItem,
          itemIndex: index,
        );
      },
    );
  }
}

/// Handles both touch and D-Pad tap vs long-press for a single grid item.
/// For D-Pad: starts a 600ms timer on KeyDownEvent; fires onTap if key is
/// released before the timer, or onLongPress if the timer expires first.
class _GridItem<T> extends StatefulWidget {
  final T item;
  final Widget Function(T) itemBuilder;
  final void Function(T)? onTap;
  final void Function(T)? onLongPress;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool showReorderControls;
  final void Function(int, int)? onMoveItem;
  final int itemIndex;
  final VoidCallback? onMoveLeft;
  final VoidCallback? onMoveRight;

  /// Called when focused item is at the left edge of a row and user presses left.
  final VoidCallback? onEdgeLeft;

  /// Called when focused item is at the right edge of a row and user presses right.
  final VoidCallback? onEdgeRight;
  final bool isBottomEdge;

  const _GridItem({
    super.key,
    required this.item,
    required this.itemBuilder,
    this.onTap,
    this.onLongPress,
    this.focusNode,
    this.autofocus = false,
    this.showReorderControls = false,
    this.onMoveItem,
    this.itemIndex = 0,
    this.onMoveLeft,
    this.onMoveRight,
    this.onEdgeLeft,
    this.onEdgeRight,
    this.isBottomEdge = false,
  });

  @override
  State<_GridItem<T>> createState() => _GridItemState<T>();
}

class _GridItemState<T> extends State<_GridItem<T>> {
  Timer? _longPressTimer;
  Timer? _initialFocusTimer;
  bool _longPressTriggered = false;
  bool _ignoreInitialSelect = false;
  bool _suppressNextTap = false;

  @override
  void initState() {
    super.initState();
    _ignoreInitialSelect = widget.autofocus;
    if (_ignoreInitialSelect) {
      _initialFocusTimer = Timer(const Duration(milliseconds: 500), () {
        _ignoreInitialSelect = false;
      });
    }
  }

  void _startLongPressTimer() {
    _longPressTriggered = false;
    _longPressTimer?.cancel();
    _longPressTimer = Timer(const Duration(milliseconds: 600), () {
      _longPressTriggered = true;
      _suppressNextTap = true;
      widget.onLongPress?.call(widget.item);
    });
  }

  void _handleGestureLongPress() {
    _suppressNextTap = true;
    widget.onLongPress?.call(widget.item);
  }

  void _handleTap() {
    if (_suppressNextTap) {
      _suppressNextTap = false;
      return;
    }
    widget.onTap?.call(widget.item);
  }

  void _cancelTimer() {
    _longPressTimer?.cancel();
    _longPressTimer = null;
  }

  @override
  void dispose() {
    _longPressTimer?.cancel();
    _initialFocusTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.focusNode,
      autofocus: widget.autofocus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          // Shift/Ctrl + arrow: reorder (favorites)
          if (HardwareKeyboard.instance.isShiftPressed ||
              HardwareKeyboard.instance.isControlPressed) {
            if (event.logicalKey == LogicalKeyboardKey.arrowLeft &&
                widget.onMoveLeft != null) {
              widget.onMoveLeft!();
              return KeyEventResult.handled;
            } else if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
                widget.onMoveRight != null) {
              widget.onMoveRight!();
              return KeyEventResult.handled;
            }
          }
          // Edge navigation: left/right at row boundaries jumps to prev/next category
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft &&
              widget.onEdgeLeft != null) {
            widget.onEdgeLeft!();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
              widget.onEdgeRight != null) {
            widget.onEdgeRight!();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowDown &&
              widget.isBottomEdge) {
            return KeyEventResult.handled;
          }
          if (widget.showReorderControls &&
              (event.logicalKey == LogicalKeyboardKey.channelUp ||
                  event.logicalKey == LogicalKeyboardKey.pageUp)) {
            widget.onMoveItem?.call(widget.itemIndex, -1);
            return KeyEventResult.handled;
          }
          if (widget.showReorderControls &&
              (event.logicalKey == LogicalKeyboardKey.channelDown ||
                  event.logicalKey == LogicalKeyboardKey.pageDown)) {
            widget.onMoveItem?.call(widget.itemIndex, 1);
            return KeyEventResult.handled;
          }
        }

        final isSelectKey =
            event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.gameButtonSelect;

        if (!isSelectKey) return KeyEventResult.ignored;
        if (_ignoreInitialSelect) return KeyEventResult.handled;

        if (event is KeyDownEvent) {
          // First physical press — start the long-press timer
          _startLongPressTimer();
          return KeyEventResult.handled;
        }

        if (event is KeyUpEvent) {
          if (_longPressTriggered) {
            // Long press already fired — just clean up
            _cancelTimer();
          } else {
            // Released before timer — treat as tap
            _cancelTimer();
            _handleTap();
          }
          return KeyEventResult.handled;
        }

        // KeyRepeatEvent while holding — timer is already running, ignore
        return KeyEventResult.handled;
      },
      child: Builder(
        builder: (context) {
          final hasFocus = Focus.of(context).hasFocus;
          return GestureDetector(
            onTap: _handleTap,
            onLongPress: _handleGestureLongPress,
            child: Card(
              color: hasFocus
                  ? const Color(0xFFFF6B4A)
                  : const Color(0xFF15212A),
              margin: const EdgeInsets.all(2),
              elevation: hasFocus ? 8 : 2,
              shadowColor: const Color(0xFFFF6B4A).withValues(alpha: 0.25),
              clipBehavior: Clip.antiAlias,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
                side: BorderSide(
                  color: hasFocus
                      ? Colors.white
                      : Colors.white.withValues(alpha: 0.08),
                  width: hasFocus ? 2 : 1,
                ),
              ),
              child: Stack(
                children: [
                  widget.itemBuilder(widget.item),
                  if (widget.showReorderControls)
                    const Positioned(
                      top: 4,
                      left: 4,
                      child: Icon(
                        Icons.swap_vert,
                        color: Colors.white70,
                        size: 18,
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
