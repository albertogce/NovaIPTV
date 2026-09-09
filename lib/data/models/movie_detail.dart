class MovieDetail {
  final int movieId;
  final String title;
  final String logo;
  final String description;
  final String category;
  final int year;
  final List<String> tags;
  final String url;

  MovieDetail({
    required this.movieId,
    required this.title,
    required this.logo,
    required this.description,
    required this.category,
    required this.year,
    required this.tags,
    required this.url,
  });

  factory MovieDetail.fromJson(Map<String, dynamic> json) {
    final tags =
        (json['tags'] as List?)?.map((t) => t.toString()).toList() ?? [];

    return MovieDetail(
      movieId: json['movie_id'] ?? 0,
      title: json['title'] ?? '',
      logo: json['logo'] ?? '',
      description: json['description'] ?? '',
      category: json['category'] ?? '',
      year: json['year'] ?? 0,
      tags: tags,
      url: json['url'] ?? '',
    );
  }
}
