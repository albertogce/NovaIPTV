import 'package:iptv_flutter/data/models/series_episode.dart';

class SeriesSeason {
  final int seasonNumber;
  final String seasonTitle;
  final List<SeriesEpisode> episodes;

  SeriesSeason({
    required this.seasonNumber,
    required this.seasonTitle,
    this.episodes = const [],
  });

  factory SeriesSeason.fromJson(Map<String, dynamic> json) {
    final episodes =
        (json['episodes'] as List?)
            ?.map((e) => SeriesEpisode.fromJson(e))
            .toList() ??
        [];

    return SeriesSeason(
      seasonNumber: json['season_number'] ?? 1,
      seasonTitle:
          json['season_title'] ?? 'Season ${json['season_number'] ?? 1}',
      episodes: episodes,
    );
  }
}
