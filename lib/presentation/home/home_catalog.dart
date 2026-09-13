import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';

import 'home_models.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';
import 'package:iptv_flutter/data/models/live_category.dart';
import 'package:iptv_flutter/data/models/live_channel.dart';
import 'package:iptv_flutter/data/models/series.dart';
import 'package:iptv_flutter/data/models/series_category.dart';
import 'package:iptv_flutter/data/models/vod_category.dart';
import 'package:iptv_flutter/data/models/vod_movie.dart';

/// Carga, filtra y cachea el catálogo Xtream. Sin dependencias de UI salvo
/// `debugPrint`; la pantalla solo consume el resultado.
class HomeCatalog extends ChangeNotifier {
  HomeCatalog({required this.client});

  XtreamApiClient client;
  bool _disposed = false;

  List<LiveChannel> liveChannels = [];
  List<VodMovie> movies = [];
  List<Series> series = [];

  List<LiveCategory> liveCategories = [];
  List<VodCategory> vodCategories = [];
  List<SeriesCategory> seriesCategories = [];

  List<LiveCategory> allLiveCategories = [];
  List<VodCategory> allVodCategories = [];
  List<SeriesCategory> allSeriesCategories = [];

  bool isLoading = true;
  String? errorMessage;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  void _notifySafely() {
    if (!_disposed) notifyListeners();
  }

  List<LiveChannel> get visibleLiveChannels {
    final ids = liveCategories.map((c) => c.categoryId.toString()).toSet();
    return liveChannels
        .where((c) => ids.contains(c.categoryId.toString()))
        .toList();
  }

  List<VodMovie> get visibleMovies {
    final ids = vodCategories.map((c) => c.categoryId.toString()).toSet();
    return movies
        .where((m) => ids.contains(m.categoryId.toString()))
        .toList();
  }

  List<Series> get visibleSeries {
    final ids = seriesCategories.map((c) => c.categoryId.toString()).toSet();
    return series
        .where((s) => ids.contains(s.categoryId.toString()))
        .toList();
  }

  List<LiveCategory> get liveCategoriesWithFavorites => [
    LiveCategory(categoryId: favoritesCategoryId, categoryName: 'Favoritos'),
    ...liveCategories,
  ];

  Future<void> load({bool forceRefresh = false}) async {
    isLoading = true;
    errorMessage = null;
    _notifySafely();

    if (!forceRefresh && await _tryLoadLocalCache()) {
      _applyCategoryFiltersAndOrdering();
      isLoading = false;
      _notifySafely();
      return;
    }

    await fetchAndCacheRemoteData(silent: false);
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

      liveChannels = _parseList(rawCh, LiveChannel.fromJson);
      movies = _parseList(rawMov, VodMovie.fromJson);
      series = _parseList(rawSer, Series.fromJson);
      allLiveCategories = _parseList(rawLCat, LiveCategory.fromJson);
      allVodCategories = _parseList(rawVCat, VodCategory.fromJson);
      allSeriesCategories = _parseList(rawSCat, SeriesCategory.fromJson);

      final hasContent =
          liveChannels.isNotEmpty || movies.isNotEmpty || series.isNotEmpty;
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

  Future<void> fetchAndCacheRemoteData({bool silent = false}) async {
    try {
      Future<List<dynamic>> safely(
        String name,
        Future<List<dynamic>> request,
      ) async {
        try {
          return await request;
        } catch (error) {
          debugPrint('No se pudo cargar $name; se continuará sin eso.');
          return <dynamic>[];
        }
      }

      final results = await Future.wait([
        safely('canales en vivo', client.getLiveStreams()),
        safely('películas', client.getVodStreams()),
        safely('series', client.getSeries()),
        safely('categorías de canales en vivo', client.getLiveCategories()),
        safely('categorías de películas', client.getVodCategories()),
        safely('categorías de series', client.getSeriesCategories()),
      ]);

      final channelsParsed = _parseList(results[0], LiveChannel.fromJson);
      final moviesParsed = _parseList(results[1], VodMovie.fromJson);
      final seriesParsed = _parseList(results[2], Series.fromJson);
      final liveCatsParsed = _parseList(results[3], LiveCategory.fromJson);
      final vodCatsParsed = _parseList(results[4], VodCategory.fromJson);
      final seriesCatsParsed = _parseList(results[5], SeriesCategory.fromJson);

      final storage = SharedPrefsStorage();
      final compact = storage.getBool('settings_store_visible_only') ?? false;
      final hiddenLive =
          (storage.getStringList('settings_live_cat_hidden') ?? []).toSet();
      final hiddenVod = (storage.getStringList('settings_vod_cat_hidden') ?? [])
          .toSet();
      final hiddenSeries =
          (storage.getStringList('settings_series_cat_hidden') ?? []).toSet();

      await _cache(
        storage,
        'cache_live_channels',
        compact
            ? channelsParsed
                  .where((c) => !hiddenLive.contains(c.categoryId.toString()))
                  .toList()
            : channelsParsed,
      );
      await _cache(
        storage,
        'cache_movies',
        compact
            ? moviesParsed
                  .where((m) => !hiddenVod.contains(m.categoryId.toString()))
                  .toList()
            : moviesParsed,
      );
      await _cache(
        storage,
        'cache_series',
        compact
            ? seriesParsed
                  .where((s) => !hiddenSeries.contains(s.categoryId.toString()))
                  .toList()
            : seriesParsed,
      );
      await _cache(storage, 'cache_live_categories', liveCatsParsed);
      await _cache(storage, 'cache_vod_categories', vodCatsParsed);
      await _cache(storage, 'cache_series_categories', seriesCatsParsed);

      liveChannels = channelsParsed;
      movies = moviesParsed;
      series = seriesParsed;
      allLiveCategories = liveCatsParsed;
      allVodCategories = vodCatsParsed;
      allSeriesCategories = seriesCatsParsed;
      _applyCategoryFiltersAndOrdering();
      isLoading = false;
      _notifySafely();
    } catch (e) {
      if (!silent) {
        errorMessage = 'Error al cargar los datos de IPTV: $e';
        isLoading = false;
        _notifySafely();
      }
    }
  }

  /// Aplica la visibilidad y el orden guardados en ajustes.
  void _applyCategoryFiltersAndOrdering() {
    final storage = SharedPrefsStorage();
    liveCategories = _filterAndOrder(
      allLiveCategories,
      storage.getStringList('settings_live_cat_hidden') ?? [],
      storage.getStringList('settings_live_cat_order') ?? [],
      (c) => c.categoryId.toString(),
    );
    vodCategories = _filterAndOrder(
      allVodCategories,
      storage.getStringList('settings_vod_cat_hidden') ?? [],
      storage.getStringList('settings_vod_cat_order') ?? [],
      (c) => c.categoryId.toString(),
    );
    seriesCategories = _filterAndOrder(
      allSeriesCategories,
      storage.getStringList('settings_series_cat_hidden') ?? [],
      storage.getStringList('settings_series_cat_order') ?? [],
      (c) => c.categoryId.toString(),
    );
  }

  List<T> _filterAndOrder<T>(
    List<T> all,
    List<String> hidden,
    List<String> order,
    String Function(T) idOf,
  ) {
    final hiddenSet = hidden.toSet();
    final filtered = all.where((c) => !hiddenSet.contains(idOf(c))).toList();
    if (order.isEmpty) return filtered;
    filtered.sort((a, b) {
      final ia = order.indexOf(idOf(a));
      final ib = order.indexOf(idOf(b));
      if (ia == -1 && ib == -1) return 0;
      if (ia == -1) return 1;
      if (ib == -1) return -1;
      return ia.compareTo(ib);
    });
    return filtered;
  }

  void updateClient(XtreamApiClient newClient) => client = newClient;

  /// Refresca la caducidad si aún no se conoce (p. ej. sesiones automáticas
  /// iniciadas antes de que existiera esta función). Silencioso por diseño:
  /// si falla, se reintenta en el próximo arranque.
  Future<void> refreshAccountExpiry() async {
    final storage = SharedPrefsStorage();
    if ((storage.getString('account_exp_date') ?? '').isNotEmpty) return;
    try {
      final info = await client.getServerInfo();
      final userInfo = info['user_info'];
      final expDate = userInfo is Map
          ? userInfo['exp_date']?.toString().trim() ?? ''
          : '';
      await storage.setString('account_exp_date', expDate);
      _notifySafely();
    } catch (_) {
      // Sin red o respuesta inesperada: la UI sigue funcionando igual.
    }
  }

  Future<void> _cache(
    SharedPrefsStorage storage,
    String key,
    List<dynamic> items,
  ) async {
    final encoded = items
        .map((e) => (e as dynamic).toJson() as Map<String, dynamic>)
        .toList();
    await storage.setCacheString(key, json.encode(encoded));
  }

  List<T> _parseList<T>(
    List<dynamic> raw,
    T Function(Map<String, dynamic>) fromJson,
  ) => raw.map((e) => fromJson(Map<String, dynamic>.from(e))).toList();
}
