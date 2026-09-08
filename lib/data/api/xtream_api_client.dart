import 'package:dio/dio.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';

class XtreamRequestException implements Exception {
  final String operation;
  final String kind;
  final int? statusCode;
  final String detail;

  const XtreamRequestException({
    required this.operation,
    required this.kind,
    required this.statusCode,
    required this.detail,
  });

  @override
  String toString() {
    final status = statusCode == null ? '' : ' | HTTP $statusCode';
    return 'No se pudo cargar "$operation"\n'
        'Tipo: $kind$status\n'
        'Detalle: $detail';
  }
}

class XtreamApiClient {
  static const _connectTimeout = Duration(seconds: 20);
  static const _receiveTimeout = Duration(seconds: 60);

  final Dio dio;
  final String baseUrl;
  final String username;
  final String password;

  XtreamApiClient({
    required String baseUrl,
    required this.username,
    required this.password,
  }) : baseUrl = normalizeUrl(baseUrl),
       dio = Dio(
         BaseOptions(
           baseUrl: normalizeUrl(baseUrl),
           connectTimeout: _connectTimeout,
           receiveTimeout: _receiveTimeout,
           headers: {'Content-Type': 'application/json'},
         ),
       );

  // 1. Autenticación e Información del Servidor
  Future<Map<String, dynamic>> getServerInfo() async {
    final response = await _get(
      'información del servidor',
      '/player_api.php',
      queryParameters: {'username': username, 'password': password},
    );
    return _asMap(response.data);
  }

  // 2. Obtener Categorías de Canales en Vivo
  Future<List<dynamic>> getLiveCategories() async {
    final response = await _get(
      'categorías de canales en vivo',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_live_categories',
      },
    );
    return _asList(response.data);
  }

  // 2b. Obtener Canales en Vivo (listado completo)
  Future<List<dynamic>> getLiveStreams({int? categoryId}) async {
    final query = {
      'username': username,
      'password': password,
      'action': 'get_live_streams',
    };
    if (categoryId != null) {
      query['category_id'] = categoryId.toString();
    }
    final response = await _get(
      'canales en vivo',
      '/player_api.php',
      queryParameters: query,
    );
    return _asList(response.data);
  }

  // 3. Obtener Categorías de Películas (VOD)
  Future<List<dynamic>> getVodCategories() async {
    final response = await _get(
      'categorías de películas',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_vod_categories',
      },
    );
    return _asList(response.data);
  }

  // 3b. Obtener Lista de Películas (VOD)
  Future<List<dynamic>> getVodStreams() async {
    final response = await _get(
      'películas',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_vod_streams',
      },
    );
    return _asList(response.data);
  }

  // 3c. Ficha/Película Info
  Future<Map<String, dynamic>> getVodInfo({required int vodId}) async {
    final response = await _get(
      'información de película',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_vod_info',
        'vod_id': vodId,
      },
    );
    return _asMap(response.data);
  }

  // 4. Obtener Categorías de Series
  Future<List<dynamic>> getSeriesCategories() async {
    final response = await _get(
      'categorías de series',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_series_categories',
      },
    );
    return _asList(response.data);
  }

  // 4b. Obtener Lista de Series
  Future<List<dynamic>> getSeries() async {
    final response = await _get(
      'series',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_series',
      },
    );
    return _asList(response.data);
  }

  // 4c. Temporadas y Episodios de una Serie
  Future<Map<String, dynamic>> getSeriesInfo({required int seriesId}) async {
    final response = await _get(
      'información de serie',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_series_info',
        'series_id': seriesId,
      },
    );
    return _asMap(response.data);
  }

  // 4d. Corto EPG por canal
  Future<Map<String, dynamic>> getShortEpg({required int streamId}) async {
    final response = await _get(
      'guía EPG del canal',
      '/player_api.php',
      queryParameters: {
        'username': username,
        'password': password,
        'action': 'get_short_epg',
        'stream_id': streamId,
      },
    );
    return _asMap(response.data);
  }

  Future<Response<dynamic>> _get(
    String operation,
    String path, {
    required Map<String, dynamic> queryParameters,
  }) async {
    try {
      return await dio.get(path, queryParameters: queryParameters);
    } on DioException catch (error) {
      throw XtreamRequestException(
        operation: operation,
        kind: _errorKind(error.type),
        statusCode: error.response?.statusCode,
        detail: _errorDetail(error),
      );
    }
  }

  String _errorKind(DioExceptionType type) {
    switch (type) {
      case DioExceptionType.connectionTimeout:
        return 'tiempo de conexión agotado';
      case DioExceptionType.sendTimeout:
        return 'tiempo de envío agotado';
      case DioExceptionType.receiveTimeout:
        return 'tiempo de recepción agotado';
      case DioExceptionType.transformTimeout:
        return 'tiempo de procesamiento de respuesta agotado';
      case DioExceptionType.badResponse:
        return 'respuesta HTTP no válida';
      case DioExceptionType.connectionError:
        return 'error de conexión';
      case DioExceptionType.badCertificate:
        return 'certificado TLS no válido';
      case DioExceptionType.cancel:
        return 'petición cancelada';
      case DioExceptionType.unknown:
        return 'error de red desconocido';
    }
  }

  String _errorDetail(DioException error) {
    final data = error.response?.data;
    if (data is Map) {
      final message = data['message'] ?? data['error'] ?? data['detail'];
      if (message != null && message.toString().trim().isNotEmpty) {
        return message.toString();
      }
    }
    if (data is String && data.trim().isNotEmpty) return data.trim();
    if (error.message != null && error.message!.trim().isNotEmpty) {
      return error.message!;
    }
    return 'El servidor no devolvió más información.';
  }

  // Helper methods to handle Xtream Codes JSON responses safely
  List<dynamic> _asList(dynamic data) {
    if (data is List) return data;
    if (data is Map) {
      if (data.containsKey('live_streams') && data['live_streams'] is List)
        return data['live_streams'];
      if (data.containsKey('vod') && data['vod'] is List) return data['vod'];
      if (data.containsKey('series') && data['series'] is List)
        return data['series'];
      if (data.containsKey('live_categories') &&
          data['live_categories'] is List)
        return data['live_categories'];
      if (data.containsKey('vod_categories') && data['vod_categories'] is List)
        return data['vod_categories'];
      if (data.containsKey('series_categories') &&
          data['series_categories'] is List)
        return data['series_categories'];
      return data.values.toList();
    }
    return [];
  }

  Map<String, dynamic> _asMap(dynamic data) {
    if (data is Map<String, dynamic>) return data;
    if (data is Map) return Map<String, dynamic>.from(data);
    return {};
  }
}
