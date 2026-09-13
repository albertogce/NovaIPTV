import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';

/// Credenciales IPTV en el almacén seguro del sistema (Keystore/Keychain)
/// en lugar de SharedPreferences en claro.
///
/// Se leen a memoria una vez en [init] para que el resto de la app pueda
/// consultarlas sin await (p. ej. [IptvUserInfo.current]).
class CredentialStore {
  static const _storage = FlutterSecureStorage();
  static Future<void> _writeQueue = Future.value();
  static const _kUsername = 'username';
  static const _kPassword = 'password';
  static const _kServer = 'server';

  static String username = '';
  static String password = '';
  static String server = '';

  static Future<void> init() async {
    try {
      username = await _storage.read(key: _kUsername) ?? '';
      password = await _storage.read(key: _kPassword) ?? '';
      server = await _storage.read(key: _kServer) ?? '';
      await _migrateLegacyCredentials();
    } catch (_) {
      // Sin almacenamiento seguro disponible, la app arranca sin sesión.
      username = '';
      password = '';
      server = '';
    }
  }

  /// Mueve una sola vez las credenciales guardadas en claro a este almacén.
  static Future<void> _migrateLegacyCredentials() async {
    final prefs = SharedPrefsStorage();
    final legacyUsername = prefs.getString('username') ?? '';
    final legacyPassword = prefs.getString('password') ?? '';
    final legacyServer =
        prefs.getString('server') ?? prefs.getString('server_url') ?? '';

    if (username.isEmpty && legacyUsername.isNotEmpty) {
      username = legacyUsername;
      await _storage.write(key: _kUsername, value: username);
    }
    if (password.isEmpty && legacyPassword.isNotEmpty) {
      password = legacyPassword;
      await _storage.write(key: _kPassword, value: password);
    }
    if (server.isEmpty && legacyServer.isNotEmpty) {
      server = normalizeUrl(legacyServer);
      await _storage.write(key: _kServer, value: server);
    }

    // Aunque haya campos vacíos, no debe quedar texto sensible en claro.
    await prefs.remove('username');
    await prefs.remove('password');
    await prefs.remove('server');
    await prefs.remove('server_url');
  }

  static Future<void> save({
    required String username,
    required String password,
    required String server,
  }) {
    return _enqueue(() async {
      final normalizedServer = normalizeUrl(server);
      CredentialStore.username = username;
      CredentialStore.password = password;
      CredentialStore.server = normalizedServer;
      await _storage.write(key: _kUsername, value: username);
      await _storage.write(key: _kPassword, value: password);
      await _storage.write(key: _kServer, value: normalizedServer);
    });
  }

  static Future<void> clear() {
    return _enqueue(() async {
      username = '';
      password = '';
      server = '';
      await _storage.delete(key: _kUsername);
      await _storage.delete(key: _kPassword);
      await _storage.delete(key: _kServer);
    });
  }

  /// Serializa escrituras concurrentes, como el autoguardado de Ajustes.
  static Future<void> _enqueue(Future<void> Function() operation) {
    final next = _writeQueue.then((_) => operation());
    _writeQueue = next.then<void>((_) {}, onError: (_, __) {});
    return next;
  }
}
