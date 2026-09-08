import 'package:flutter/material.dart';

const _ink = Color(0xFF0B1117);
const _panel = Color(0xFF15212A);
const _accent = Color(0xFFFF6B4A);
const _mint = Color(0xFF5DE0C2);

final appTheme = ThemeData.dark().copyWith(
  scaffoldBackgroundColor: _ink,
  colorScheme: const ColorScheme.dark(
    primary: _accent,
    secondary: _mint,
    surface: _panel,
  ),
  appBarTheme: const AppBarTheme(
    backgroundColor: _ink,
    foregroundColor: Colors.white,
    elevation: 0,
  ),
  cardTheme: const CardThemeData(
    color: _panel,
    elevation: 0,
    margin: EdgeInsets.zero,
  ),
  bottomNavigationBarTheme: const BottomNavigationBarThemeData(
    backgroundColor: Color(0xFF1F1F1F),
    selectedItemColor: _accent,
    unselectedItemColor: Colors.white60,
    selectedIconTheme: IconThemeData(size: 28, color: _accent),
    unselectedIconTheme: IconThemeData(size: 24, color: Colors.white60),
    showUnselectedLabels: true,
    type: BottomNavigationBarType.fixed,
  ),
  textTheme: const TextTheme(
    bodyLarge: TextStyle(color: Colors.white70),
    bodyMedium: TextStyle(color: Colors.white54),
    titleLarge: TextStyle(
      color: Colors.white,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.2,
    ),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: _panel,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: Colors.white12),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: Colors.white12),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(14)),
      borderSide: BorderSide(color: _accent, width: 2),
    ),
  ),
);
