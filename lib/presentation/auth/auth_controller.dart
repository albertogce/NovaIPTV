import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:iptv_flutter/core/storage/credential_store.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';
import 'package:iptv_flutter/data/models/iptv_user_info.dart';

final authStateProvider = StateNotifierProvider<AuthController, AuthState>((
  ref,
) {
  return AuthController();
});

class AuthController extends StateNotifier<AuthState> {
  AuthController() : super(AuthState.initial());

  Future<void> login(String server, String username, String password) async {
    state = AuthState.loading();
    try {
      final normalizedServer = normalizeUrl(server);
      final client = XtreamApiClient(
        baseUrl: normalizedServer,
        username: username,
        password: password,
      );
      // La API confirma la cuenta; sin esto cualquier credencial entraba.
      final info = await client.getServerInfo();
      final userInfo = info['user_info'];
      final auth = userInfo is Map ? userInfo['auth'] : null;
      final authorized = auth == 1 || auth?.toString() == '1';
      if (!authorized) {
        final status = userInfo is Map
            ? userInfo['status']?.toString().trim() ?? ''
            : '';
        state = AuthState.error(
          status.isNotEmpty
              ? 'Acceso denegado: $status.'
              : 'Usuario o contraseña incorrectos, o cuenta inactiva.',
        );
        return;
      }
      final user = IptvUserInfo(
        username: username,
        password: password,
        server: normalizedServer,
        isAuthenticated: true,
      );
      await IptvUserInfo.save(user);
      // La caducidad solo se refresca al iniciar sesión, no en auto-arranque.
      await _saveAccountExpiry(userInfo);
      state = AuthState.authenticated(user);
    } on Exception catch (e) {
      state = AuthState.error(e.toString());
    }
  }

  /// Guarda `exp_date` (segundos unix en texto; vacío si ilimitada).
  Future<void> _saveAccountExpiry(Object? userInfo) async {
    final expDate = userInfo is Map
        ? userInfo['exp_date']?.toString().trim() ?? ''
        : '';
    await SharedPrefsStorage().setString('account_exp_date', expDate);
  }

  Future<void> logout() async {    try {
      await CredentialStore.clear();
      state = AuthState.unauthenticated();
    } catch (e) {
      state = AuthState.error('Error al cerrar sesión');
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
