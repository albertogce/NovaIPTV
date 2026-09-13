import 'package:iptv_flutter/core/storage/credential_store.dart';
import 'package:iptv_flutter/core/utils/url_normalizer.dart';

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

  factory IptvUserInfo.current() {
    return IptvUserInfo(
      username: CredentialStore.username,
      password: CredentialStore.password,
      server: normalizeUrl(CredentialStore.server),
      isAuthenticated:
          CredentialStore.username.isNotEmpty &&
          CredentialStore.password.isNotEmpty &&
          CredentialStore.server.isNotEmpty,
    );
  }

  static Future<void> save(IptvUserInfo user) async {
    await CredentialStore.save(
      username: user.username,
      password: user.password,
      server: user.server,
    );
  }
}
