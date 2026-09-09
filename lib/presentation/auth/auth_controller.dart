import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/data/models/iptv_user_info.dart';

final authStateProvider = StateNotifierProvider<AuthController, AuthState>((
  ref,
) {
  return AuthController(storage: SharedPrefsStorage());
});

class AuthController extends StateNotifier<AuthState> {
  final SharedPrefsStorage _storage;
  AuthController({required SharedPrefsStorage storage})
    : _storage = storage,
      super(AuthState.initial());

  Future<void> login(String server, String username, String password) async {
    state = AuthState.loading();
    try {
      final normalizedServer = normalizeUrl(server);
      final user = IptvUserInfo(
        username: username,
        password: password,
        server: normalizedServer,
        isAuthenticated: true,
      );
      await IptvUserInfo.save(user);
      state = AuthState.authenticated(user);
    } on Exception catch (e) {
      state = AuthState.error(e.toString());
    }
  }

  Future<void> logout() async {
    try {
      await _storage.remove('username');
      await _storage.remove('password');
      await _storage.remove('server');
      state = AuthState.unauthenticated();
    } catch (e) {
      state = AuthState.error('Error al cerrar sesión');
    }
  }

  Future<void> checkAuth() async {
    final user = IptvUserInfo.fromSharedPrefs();
    if (user.isAuthenticated && user.username.isNotEmpty) {
      state = AuthState.authenticated(user);
    } else {
      state = AuthState.unauthenticated();
    }
  }
}

enum AuthStateStatus { initial, authenticated, unauthenticated, loading, error }

class AuthState {
  AuthStateStatus status;
  String? message;
  IptvUserInfo? user;

  AuthState._({required this.status, this.message, this.user});

  static AuthState initial() => AuthState._(status: AuthStateStatus.initial);
  static AuthState loading() => AuthState._(status: AuthStateStatus.loading);
  static AuthState authenticated(IptvUserInfo u) =>
      AuthState._(status: AuthStateStatus.authenticated, user: u);
  static AuthState unauthenticated() =>
      AuthState._(status: AuthStateStatus.unauthenticated);
  static AuthState error(String msg) =>
      AuthState._(status: AuthStateStatus.error, message: msg);

  bool get isAuthenticated => status == AuthStateStatus.authenticated;
  bool get isLoading => status == AuthStateStatus.loading;
  bool get hasError => status == AuthStateStatus.error;
}
