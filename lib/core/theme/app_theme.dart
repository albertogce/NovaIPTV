import 'package:flutter/material.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';

/// Paleta única de la app. Los literales `Color(0xFF...)` no deben aparecer
/// fuera de este fichero.
class AppColors {
  // Base.
  static const ink = Color(0xFF0B1117);
  static const panel = Color(0xFF15212A);
  static const accent = Color(0xFFFF6B4A);
  static const mint = Color(0xFF5DE0C2);
  static const amber = Color(0xFFFFC857);
  static const pink = Color(0xFFE91E63);

  // Superficies y focos.
  static const focusFill = Color(0xFF1D3039);
  static const bottomNav = Color(0xFF1F1F1F);
  static const playerInk = Color(0xFF121212);
  static const epgLive = Color(0xFF1D403D);
  static const searchTile = Color(0xFF1E1E1E);
  static const searchTileFocus = Color(0xFF2A0A1A);
  static const episodeFocus = Color(0xFF3B211C);
  static const thumbPlaceholder = Color(0xFF2C2C2C);
  static const posterFallback = Color(0xFF20323A);
  static const movieButtonDark = Color(0xFF880E4F);

  // Degradados.
  static const cardGradStart = Color(0xFF29434A);
  static const cardGradEnd = Color(0xFF17262E);
  static const contentGradEnd = Color(0xFF10232B);
  static const dashboardGradEnd = Color(0xFF12232A);
  static const seriesHeadStart = Color(0xFF1C333B);

  // Acentos secundarios.
  static const chipFocus = Color(0xFFB94732);
  static const salmon = Color(0xFFFF8B70);
  static const mutedLabel = Color(0xFF8CAAA9);

  // ponytail: lectura síncrona del ajuste para no hilanar el flag por
  // decenas de widgets; Ajustes fuerza reconstrucción al guardar.
  static bool get highContrast =>
      SharedPrefsStorage().getBool('settings_high_contrast') ?? false;

  /// Texto principal: blanco puro con alto contraste, `white70` si no.
  static Color get bodyText =>
      highContrast ? Colors.white : Colors.white70;

  /// Texto secundario: sube un escalón con alto contraste.
  static Color get subtleText =>
      highContrast ? Colors.white70 : Colors.white54;

  /// Texto tenue: sube un escalón con alto contraste.
  static Color get faintText =>
      highContrast ? Colors.white54 : Colors.white38;
}

final appTheme = ThemeData.dark().copyWith(
  scaffoldBackgroundColor: AppColors.ink,
  colorScheme: const ColorScheme.dark(
    primary: AppColors.accent,
    secondary: AppColors.mint,
    surface: AppColors.panel,
  ),
  appBarTheme: const AppBarTheme(
    backgroundColor: AppColors.ink,
    foregroundColor: Colors.white,
    elevation: 0,
  ),
  cardTheme: const CardThemeData(
    color: AppColors.panel,
    elevation: 0,
    margin: EdgeInsets.zero,
  ),
  bottomNavigationBarTheme: const BottomNavigationBarThemeData(
    backgroundColor: AppColors.bottomNav,
    selectedItemColor: AppColors.accent,
    unselectedItemColor: Colors.white60,
    selectedIconTheme: IconThemeData(size: 28, color: AppColors.accent),
    unselectedIconTheme: IconThemeData(size: 24, color: Colors.white60),
    showUnselectedLabels: true,
    type: BottomNavigationBarType.fixed,
  ),
  textTheme: TextTheme(
    bodyLarge: TextStyle(color: AppColors.bodyText),
    bodyMedium: TextStyle(color: AppColors.subtleText),
    titleLarge: const TextStyle(
      color: Colors.white,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.2,
    ),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: AppColors.panel,
    border: const OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: Colors.white12),
    ),
    enabledBorder: const OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: Colors.white12),
    ),
    focusedBorder: const OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: AppColors.accent, width: 2),
    ),
  ),
);
