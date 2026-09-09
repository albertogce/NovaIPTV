import 'dart:io';

import 'package:shared_preferences/shared_preferences.dart';

class SharedPrefsStorage {
  static SharedPrefsStorage? _instance;
  static SharedPreferences? _prefs;

  factory SharedPrefsStorage() => _instance ??= SharedPrefsStorage._();

  SharedPrefsStorage._();

  static Future init() async {
    _prefs = await SharedPreferences.getInstance();
    _instance = SharedPrefsStorage._();
  }

  Future<void> setString(String key, String value) async {
    await _prefs?.setString(key, value);
  }

  String? getString(String key) {
    return _prefs?.getString(key);
  }

  Future<void> setStringList(String key, List<String> value) async {
    await _prefs?.setStringList(key, value);
  }

  List<String>? getStringList(String key) {
    return _prefs?.getStringList(key);
  }

  Future<void> setBool(String key, bool value) async {
    await _prefs?.setBool(key, value);
  }

  bool? getBool(String key) {
    return _prefs?.getBool(key);
  }

  Future<void> remove(String key) async {
    await _prefs?.remove(key);
  }

  Future<void> setCacheString(String key, String value) async {
    final file = await _cacheFile(key);
    await file.parent.create(recursive: true);
    await file.writeAsString(value, flush: true);
  }

  Future<String?> getCacheString(String key) async {
    final file = await _cacheFile(key);
    if (!await file.exists()) return null;
    return file.readAsString();
  }

  Future<File> _cacheFile(String key) async {
    final directory = Directory('${Directory.systemTemp.path}/nova_iptv');
    return File('${directory.path}/$key.json');
  }
}
