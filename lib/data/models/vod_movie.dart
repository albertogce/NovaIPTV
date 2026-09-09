class VodMovie {
  final int movieId;
  final String title;
  final String logo;
  final String category;
  final String categoryId;
  final int year;
  final String url;

  VodMovie({
    required this.movieId,
    required this.title,
    required this.logo,
    required this.category,
    required this.categoryId,
    required this.year,
    required this.url,
  });

  factory VodMovie.fromJson(Map<String, dynamic> json) {
    int parseInt(dynamic val) {
      if (val is int) return val;
      if (val is String) return int.tryParse(val) ?? 0;
      return 0;
    }

    return VodMovie(
      movieId: parseInt(json['stream_id'] ?? json['movie_id']),
      title: (json['name'] ?? json['title'] ?? '').toString(),
      logo: (json['stream_icon'] ?? json['logo'] ?? '').toString(),
      category: (json['category_name'] ?? json['category'] ?? '').toString(),
      categoryId: (json['category_id'] ?? '').toString(),
      year: parseInt(json['year']),
      url: (json['url'] ?? '').toString(),
    );
  }

  Map<String, dynamic> toJson() => {
    'stream_id': movieId,
    'name': title,
    'stream_icon': logo,
    'category_name': category,
    'category_id': categoryId,
    'year': year,
    'url': url,
  };
}
