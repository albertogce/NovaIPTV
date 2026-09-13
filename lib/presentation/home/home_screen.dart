import 'dart:async';
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
import 'package:iptv_flutter/presentation/home/home_catalog.dart';
import 'package:iptv_flutter/presentation/home/home_models.dart';
import 'package:iptv_flutter/presentation/home/movie_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/series_detail_screen.dart';
import 'package:iptv_flutter/presentation/home/search_results_screen.dart';
import 'package:iptv_flutter/presentation/settings/settings_screen.dart';
import 'package:iptv_flutter/presentation/home/epg_guide_screen.dart';
import 'package:iptv_flutter/core/playback/watch_progress_store.dart';
import 'package:iptv_flutter/core/theme/app_theme.dart';

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

class _HomeScreenState extends State<HomeScreen> {
  late final HomeCatalog _catalog;
  ActiveView _activeView = ActiveView.home;

  String? _selectedLiveCatId;
  String? _selectedVodCatId;
  String? _selectedSeriesCatId;

  // Search state
  String _liveSearchQuery = '';
  String _movieSearchQuery = '';
  String _seriesSearchQuery = '';
  String _globalSearchQuery = '';

  Timer? _autoRefreshTimer;
  final Map<int, FocusNode> _favoriteFocusNodes = {};
  final FocusNode _epgFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _catalog = HomeCatalog(client: widget.client)..addListener(_onCatalog);
    HardwareKeyboard.instance.addHandler(_handleHardwareKey);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _catalog.load();
    _catalog.refreshAccountExpiry();
    _startAutoRefreshTimer();
  }

  void _onCatalog() => setState(() {});

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_handleHardwareKey);
    _autoRefreshTimer?.cancel();
    _catalog.removeListener(_onCatalog);
    _catalog.dispose();
    for (final node in _favoriteFocusNodes.values) {
      node.dispose();
    }
    _epgFocusNode.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _startAutoRefreshTimer() {
    _autoRefreshTimer?.cancel();
    final refreshStr = SharedPrefsStorage().getString(
      'settings_auto_refresh_minutes',
    );
    final minutes = refreshStr != null ? int.tryParse(refreshStr) ?? 0 : 0;

    if (minutes > 0) {
      _autoRefreshTimer = Timer.periodic(Duration(minutes: minutes), (_) {
        _catalog.fetchAndCacheRemoteData(silent: true);
      });
    }
  }

  String _buildStreamUrl(LiveChannel channel) =>
      _catalog.client.liveStreamUrl(channel.channelId);

  void _onChannelTap(
    LiveChannel channel,
    ActiveView sourceView, {
    List<LiveChannel>? zapQueue,
  }) {
    // ponytail: reuse the episode queue for live zapping (prev/next + CH+/-).
    final channels = zapQueue ?? [channel];
    final queue = channels
        .map(
          (c) => PlayerQueueItem(
            streamUrl: _buildStreamUrl(c),
            title: c.channelName,
          ),
        )
        .toList();
    var index = channels.indexWhere((c) => c.channelId == channel.channelId);
    if (index < 0) index = 0;
    Navigator.of(context)
        .push(
          MaterialPageRoute(
            builder: (_) => PlayerScreen(
              streamUrl: queue[index].streamUrl,
              channelName: queue[index].title,
              queue: queue,
              initialQueueIndex: index,
              isLive: true,
            ),
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
        builder: (_) => MovieDetailScreen(movie: movie, client: _catalog.client),
      ),
    );
  }

  void _onSeriesTap(Series series, {String? initialEpisodeId}) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SeriesDetailScreen(
          series: series,
          client: _catalog.client,
          initialEpisodeId: initialEpisodeId,
        ),
      ),
    );
  }

  List<HistoryEntry> _historyEntries(
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
            return HistoryEntry(
              type: type,
              id: id,
              title: movie.title,
              logo: movie.logo,
            );
          }
          if (type == 'series') {
            final item = seriesById[id];
            if (item == null) return null;
            return HistoryEntry(
              type: type,
              id: id,
              title: item.title,
              logo: item.logo,
            );
          }
          return null;
        })
        .whereType<HistoryEntry>()
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
      _catalog.liveChannels = _catalog.liveChannels.map((c) {
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
              ? AppColors.pink
              : Colors.grey[800],
        ),
      );
    }
  }

  void _reorderFavoriteChannels(int oldIndex, int newIndex) async {
    final favChannels = _orderedFavoriteChannels(
      _catalog.liveChannels.where((c) => c.isFavorite).toList(),
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
      final nonFavs = _catalog.liveChannels
          .where((c) => !c.isFavorite)
          .toList();
      _catalog.liveChannels = [...favChannels, ...nonFavs];
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        final focusNode = _favoriteFocusNodes[movedItem.channelId];
        focusNode?.requestFocus();
        final itemContext = focusNode?.context;
        if (itemContext != null) {
          Scrollable.ensureVisible(
            itemContext,
            alignment: .5,
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOut,
          );
        }
      }
    });
  }

  void _moveFavoriteChannel(int index, int direction) {
    final favorites = _orderedFavoriteChannels(
      _catalog.liveChannels.where((channel) => channel.isFavorite).toList(),
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
    final hasFavorites = _catalog.liveChannels.any((c) => c.isFavorite);
    setState(() {
      _selectedLiveCatId = hasFavorites
          ? favoritesCategoryId
          : (_catalog.liveCategories.isNotEmpty
                ? _catalog.liveCategories.first.categoryId.toString()
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
      case 'continueWatching':
        _navigateTo(ActiveView.continueWatching);
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

  /// Texto de caducidad de cuenta o null si es ilimitada/desconocida.
  ({String text, Color color})? _accountExpiry() {
    final seconds = int.tryParse(
      SharedPrefsStorage().getString('account_exp_date') ?? '',
    );
    if (seconds == null || seconds <= 0) return null;
    final daysLeft = DateTime.fromMillisecondsSinceEpoch(
      seconds * 1000,
    ).difference(DateTime.now()).inDays;
    if (daysLeft < 0) {
      return (text: 'Suscripción caducada', color: Colors.redAccent);
    }
    if (daysLeft == 0) {
      return (text: 'La suscripción caduca hoy', color: AppColors.amber);
    }
    final days = daysLeft == 1 ? '1 día' : '$daysLeft días';
    return (
      text: 'La suscripción caduca en $days',
      color: daysLeft <= 7 ? AppColors.amber : AppColors.subtleText,
    );
  }

  List<LiveChannel> _orderedFavoriteChannels(List<LiveChannel> channels) {    final favoriteIds =
        SharedPrefsStorage().getStringList('favorite_channels') ?? [];
    final byId = {
      for (final channel in channels) channel.channelId.toString(): channel,
    };
    return favoriteIds.map((id) => byId[id]).whereType<LiveChannel>().toList();
  }

  Widget _buildBody() {
    if (_catalog.isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: AppColors.pink),
      );
    }

    if (_catalog.errorMessage != null) {
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
                  _catalog.errorMessage!,
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppColors.bodyText),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => _catalog.load(forceRefresh: true),
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final visibleLiveChannels = _catalog.visibleLiveChannels;
    final visibleMovies = _catalog.visibleMovies;
    final visibleSeries = _catalog.visibleSeries;

    final favChannels = _catalog.liveChannels
        .where((c) => c.isFavorite)
        .toList();
    final orderedFavChannels = _orderedFavoriteChannels(favChannels);
    final liveCategoriesWithFavorites = _catalog.liveCategoriesWithFavorites;

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
          onRefresh: () => _catalog.load(forceRefresh: true),
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
              _onChannelTap(
                result,
                ActiveView.home,
                zapQueue: visibleLiveChannels,
              );
            } else if (result is VodMovie) {
              _onMovieTap(result);
            } else if (result is Series) {
              _onSeriesTap(result);
            }
          },
          onChannelTap: (ch) => _onChannelTap(
            ch,
            ActiveView.home,
            zapQueue: visibleLiveChannels,
          ),
          onMovieTap: _onMovieTap,
          onSeriesTap: _onSeriesTap,
          client: _catalog.client,
          accountExpiry: _accountExpiry(),
        );
      case ActiveView.settings:
        return SettingsScreen(
          client: _catalog.client,
          liveCategories: _catalog.allLiveCategories,
          vodCategories: _catalog.allVodCategories,
          seriesCategories: _catalog.allSeriesCategories,
          onSettingsSaved: (newClient) {
            final credentialsChanged =
                newClient.baseUrl != _catalog.client.baseUrl ||
                newClient.username != _catalog.client.username ||
                newClient.password != _catalog.client.password;
            _catalog.updateClient(newClient);
            _startAutoRefreshTimer();
            // Only a changed IPTV account invalidates the current data.
            // Category and appearance changes continue using the local cache.
            _catalog.load(forceRefresh: credentialsChanged);
            _navigateTo(ActiveView.home);
          },
        );
      case ActiveView.favorites:
        return GridViewContentView<LiveChannel>(
          items: orderedFavChannels,
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: _catalog.client),
          onTap: (channel) => _onChannelTap(
            channel,
            ActiveView.live,
            zapQueue: orderedFavChannels,
          ),
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
                  ? Center(
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
                            style: TextStyle(color: AppColors.subtleText),
                          ),
                        ],
                      ),
                    )
                  : GridViewContentView<HistoryEntry>(
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
            // ponytail: finished movies drop out, finished series stay for next episode.
            .where(
              (p) =>
                  p.fraction > 0 &&
                  (p.fraction < .95 || !p.id.startsWith('movie:')),
            )
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
                      .where((s) => p.id == 'series:${s.seriesId}')
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
                  .where((s) => p.id == 'series:${s.seriesId}')
                  .firstOrNull;
              if (series != null) {
                _onSeriesTap(series, initialEpisodeId: p.episodeId);
              }
            }
          },
          onLongPress: _removeContinueWatching,
        );
      case ActiveView.live:
        final catFiltered = _selectedLiveCatId == favoritesCategoryId
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

        final isFavSelected = _selectedLiveCatId == favoritesCategoryId;

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
              icon: Icon(
                Icons.calendar_month,
                color: AppColors.bodyText,
                size: 20,
              ),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => EpgGuideScreen(
                    client: _catalog.client,
                    channels: liveFiltered,
                  ),
                ),
              ),
            ),
            IconButton(
              tooltip: 'Actualizar',
              icon: Icon(Icons.refresh, color: AppColors.bodyText, size: 20),
              onPressed: () => _catalog.load(forceRefresh: true),
            ),
          ],
          itemBuilder: (channel) =>
              LiveChannelCard(channel: channel, client: _catalog.client),
          onTap: (channel) => _onChannelTap(
            channel,
            ActiveView.live,
            zapQueue: liveFiltered,
          ),
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
          categories: _catalog.vodCategories,
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
            accent: AppColors.amber,
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
          categories: _catalog.seriesCategories,
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
            accent: AppColors.accent,
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
