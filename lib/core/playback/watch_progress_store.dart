import 'dart:convert';
import '../storage/shared_prefs_storage.dart';

class WatchProgress {
  final String id;
  final String title;
  final Duration position;
  final Duration duration;
  final DateTime updatedAt;

  const WatchProgress({
    required this.id,
    required this.title,
    required this.position,
    required this.duration,
    required this.updatedAt,
  });

  double get fraction => duration.inMilliseconds <= 0
      ? 0
      : (position.inMilliseconds / duration.inMilliseconds)
            .clamp(0, 1)
            .toDouble();
  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'position': position.inMilliseconds,
    'duration': duration.inMilliseconds,
    'updatedAt': updatedAt.toIso8601String(),
  };
  factory WatchProgress.fromJson(Map<String, dynamic> json) => WatchProgress(
    id: json['id'].toString(),
    title: json['title']?.toString() ?? '',
    position: Duration(milliseconds: (json['position'] as num?)?.toInt() ?? 0),
    duration: Duration(milliseconds: (json['duration'] as num?)?.toInt() ?? 0),
    updatedAt:
        DateTime.tryParse(json['updatedAt']?.toString() ?? '') ??
        DateTime.now(),
  );
}

class WatchProgressStore {
  static const _key = 'watch_progress_v1';
  final SharedPrefsStorage storage;
  WatchProgressStore([SharedPrefsStorage? storage])
    : storage = storage ?? SharedPrefsStorage();

  List<WatchProgress> getAll() {
    final raw = storage.getString(_key);
    if (raw == null) return [];
    try {
      return (json.decode(raw) as List)
          .map((e) => WatchProgress.fromJson(Map<String, dynamic>.from(e)))
          .toList();
    } catch (_) {
      return [];
    }
  }

  WatchProgress? get(String id) {
    for (final item in getAll()) {
      if (item.id == id) return item;
    }
    return null;
  }

  Future<void> save(WatchProgress item) async {
    final items = getAll()
      ..removeWhere((e) => e.id == item.id)
      ..insert(0, item);
    if (items.length > 100) items.removeRange(100, items.length);
    await storage.setString(
      _key,
      json.encode(items.map((e) => e.toJson()).toList()),
    );
  }

  Future<void> remove(String id) async {
    final items = getAll()..removeWhere((item) => item.id == id);
    await storage.setString(
      _key,
      json.encode(items.map((e) => e.toJson()).toList()),
    );
  }
}
