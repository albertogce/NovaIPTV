import 'package:iptv_flutter/core/constants/AppConstants.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';

class IptvUserInfo {
  final String username;
  final String password;
  final String server;
  final bool isAuthenticated;

  IptvUserInfo({
    required this.username,
    required this.password,
    required this.server,
    this.isAuthenticated = false,
  });

  factory IptvUserInfo.fromSharedPrefs() {
    final storage = SharedPrefsStorage();
    final username = storage.getString('username') ?? '';
    final password = storage.getString('password') ?? '';
    final server = storage.getString('server') ?? AppConstants.defaultServer;
    return IptvUserInfo(
      username: username,
      password: password,
      server: normalizeUrl(server),
      isAuthenticated: username.isNotEmpty && password.isNotEmpty,
    );
  }

  Map<String, dynamic> toMap() {
    return {'username': username, 'password': password, 'server': server};
  }

  static Future<void> save(IptvUserInfo user) async {
    final storage = SharedPrefsStorage();
    await storage.setString('username', user.username);
    await storage.setString('password', user.password);
    await storage.setString('server', user.server);
  }
}
