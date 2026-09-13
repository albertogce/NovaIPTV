import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/theme/app_theme.dart';
import 'presentation/auth/login_screen.dart';
import 'presentation/home/home_screen.dart';

import 'data/api/xtream_api_client.dart';
import 'data/models/iptv_user_info.dart';

import 'core/storage/credential_store.dart';
import 'core/storage/shared_prefs_storage.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SharedPrefsStorage.init();
  await CredentialStore.init();

  final user = IptvUserInfo.current();
  runApp(ProviderScope(child: IPTVApp(initialUser: user)));
}

class IPTVApp extends StatelessWidget {
  final IptvUserInfo initialUser;

  const IPTVApp({super.key, required this.initialUser});

  XtreamApiClient _clientFor(IptvUserInfo? user) => XtreamApiClient(
    baseUrl: user?.server ?? '',
    username: user?.username ?? '',
    password: user?.password ?? '',
  );

  @override
  Widget build(BuildContext context) {
    final skipLogin = initialUser.isAuthenticated;
    return MaterialApp(
      title: 'Nova IPTV',
      theme: appTheme,
      darkTheme: appTheme,
      themeMode: ThemeMode.dark,
      home: skipLogin ? HomeScreen(client: _clientFor(initialUser)) : null,
      initialRoute: skipLogin ? null : '/login',
      routes: {
        '/login': (context) => const LoginScreen(),
        '/home': (context) {
          final user =
              ModalRoute.of(context)?.settings.arguments as IptvUserInfo?;
          return HomeScreen(client: _clientFor(user ?? initialUser));
        },
      },
    );
  }
}
