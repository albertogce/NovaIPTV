import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/theme/app_theme.dart';
import 'presentation/auth/login_screen.dart';
import 'presentation/home/home_screen.dart';

import 'data/api/xtream_api_client.dart';
import 'data/models/iptv_user_info.dart';

import 'core/storage/shared_prefs_storage.dart';

/// Returns true when the user has saved credentials and can go directly home.
Future<bool> _canSkipLogin() async {
  final user = IptvUserInfo.fromSharedPrefs();
  return user.isAuthenticated;
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SharedPrefsStorage.init();

  // Determine the initial route before the app renders.
  final skipLogin = await _canSkipLogin();

  runApp(ProviderScope(child: IPTVApp(skipLogin: skipLogin)));
}

class IPTVApp extends StatelessWidget {
  final bool skipLogin;

  const IPTVApp({super.key, required this.skipLogin});

  @override
  Widget build(BuildContext context) {
    // If we have saved credentials and cached data, build HomeScreen directly
    // so the user never sees the login screen.
    if (skipLogin) {
      final user = IptvUserInfo.fromSharedPrefs();
      return MaterialApp(
        title: 'Nova IPTV',
        theme: appTheme,
        darkTheme: appTheme,
        themeMode: ThemeMode.dark,
        home: HomeScreen(
          client: XtreamApiClient(
            baseUrl: user.server,
            username: user.username,
            password: user.password,
          ),
        ),
        routes: {
          '/login': (context) => const LoginScreen(),
          '/home': (context) {
            final u =
                ModalRoute.of(context)?.settings.arguments as IptvUserInfo?;
            return HomeScreen(
              client: XtreamApiClient(
                baseUrl: u?.server ?? '',
                username: u?.username ?? '',
                password: u?.password ?? '',
              ),
            );
          },
        },
      );
    }

    return MaterialApp(
      title: 'Nova IPTV',
      theme: appTheme,
      darkTheme: appTheme,
      themeMode: ThemeMode.dark,
      initialRoute: '/login',
      routes: {
        '/login': (context) => const LoginScreen(),
        '/home': (context) {
          final user =
              ModalRoute.of(context)?.settings.arguments as IptvUserInfo?;
          return HomeScreen(
            client: XtreamApiClient(
              baseUrl: user?.server ?? '',
              username: user?.username ?? '',
              password: user?.password ?? '',
            ),
          );
        },
      },
    );
  }
}
