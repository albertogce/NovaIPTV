import 'dart:convert';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';

class LiveChannel {
  final int channelId;
  final String channelName;
  final String channelLogo;
  final String groupTitle;
  final String categoryId;
  final String currentEpgTitle;
  final bool isFavorite;

  LiveChannel({
    required this.channelId,
    required this.channelName,
    required this.channelLogo,
    required this.groupTitle,
    required this.categoryId,
    this.currentEpgTitle = '',
    this.isFavorite = false,
  });

  static String cleanEpgTitle(dynamic val) {
    if (val == null) return '';
    var str = val.toString().trim();
    if (str.isEmpty) return '';

    // Check if it's base64 encoded
    final base64Regexp = RegExp(r'^[A-Za-z0-9+/=]+$');
    if (str.length % 4 == 0 &&
        base64Regexp.hasMatch(str) &&
        !str.contains(' ')) {
      try {
        final decoded = utf8.decode(base64.decode(str)).trim();
        if (decoded.isNotEmpty) return decoded;
      } catch (_) {}
    }
    return str;
  }

  factory LiveChannel.fromJson(Map<String, dynamic> json) {
    int parseId(dynamic val) {
      if (val is int) return val;
      if (val is String) return int.tryParse(val) ?? 0;
      return 0;
    }

    final id = parseId(json['stream_id'] ?? json['channel_id']);
    final favList =
        SharedPrefsStorage().getStringList('favorite_channels') ?? [];

    return LiveChannel(
      channelId: id,
      channelName: (json['name'] ?? json['channel_name'] ?? '').toString(),
      channelLogo: (json['stream_icon'] ?? json['channel_logo'] ?? '')
          .toString(),
      groupTitle: (json['category_name'] ?? json['group_title'] ?? '')
          .toString(),
      categoryId: (json['category_id'] ?? '').toString(),
      currentEpgTitle: cleanEpgTitle(
        json['epg_title'] ?? json['title'] ?? json['now_playing'],
      ),
      isFavorite:
          json['is_favorite'] == true || favList.contains(id.toString()),
    );
  }

  Map<String, dynamic> toJson() => {
    'stream_id': channelId,
    'name': channelName,
    'stream_icon': channelLogo,
    'category_name': groupTitle,
    'category_id': categoryId,
    'epg_title': currentEpgTitle,
    'is_favorite': isFavorite,
  };

  LiveChannel copyWith({String? currentEpgTitle, bool? isFavorite}) {
    return LiveChannel(
      channelId: channelId,
      channelName: channelName,
      channelLogo: channelLogo,
      groupTitle: groupTitle,
      categoryId: categoryId,
      currentEpgTitle: currentEpgTitle ?? this.currentEpgTitle,
      isFavorite: isFavorite ?? this.isFavorite,
    );
  }
}
