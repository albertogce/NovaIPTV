import 'package:iptv_flutter/data/models/series_season.dart';

class SeriesDetail {
  final int seriesId;
  final String title;
  final String logo;
  final String description;
  final String category;
  final int year;
  final List<SeriesSeason> seasons;

  SeriesDetail({
    required this.seriesId,
    required this.title,
    required this.logo,
    required this.description,
    required this.category,
    required this.year,
    required this.seasons,
  });

  factory SeriesDetail.fromJson(Map<String, dynamic> json) {
    final seasons =
        (json['seasons'] as List?)
            ?.map((s) => SeriesSeason.fromJson(s))
            .toList() ??
        [];

    return SeriesDetail(
      seriesId: json['series_id'] ?? 0,
      title: json['title'] ?? '',
      logo: json['logo'] ?? '',
      description: json['description'] ?? '',
      category: json['category'] ?? '',
      year: json['year'] ?? 0,
      seasons: seasons,
    );
  }
}
