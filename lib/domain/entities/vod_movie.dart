class VodMovie {
  final int movieId;
  final String title;
  final String logo;
  final String category;
  final int year;
  bool isFavorite;

  VodMovie({
    required this.movieId,
    required this.title,
    required this.logo,
    required this.category,
    required this.year,
    this.isFavorite = false,
  });

  VodMovie copyWith({
    int? movieId,
    String? title,
    String? logo,
    String? category,
    int? year,
    bool? isFavorite,
  }) {
    return VodMovie(
      movieId: movieId ?? this.movieId,
      title: title ?? this.title,
      logo: logo ?? this.logo,
      category: category ?? this.category,
      year: year ?? this.year,
      isFavorite: isFavorite ?? this.isFavorite,
    );
  }
}
