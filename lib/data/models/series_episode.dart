class SeriesEpisode {
  final int episodeNumber;
  final String title;
  final String url;

  SeriesEpisode({
    required this.episodeNumber,
    required this.title,
    required this.url,
  });

  factory SeriesEpisode.fromJson(Map<String, dynamic> json) {
    return SeriesEpisode(
      episodeNumber: json['episode_number'] ?? 1,
      title: json['title'] ?? '',
      url: json['url'] ?? '',
    );
  }
}