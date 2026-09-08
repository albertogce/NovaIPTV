class Series {
  final int seriesId;
  final String title;
  final String logo;
  final String category;
  int year;
  bool isFavorite;

  Series({
    required this.seriesId,
    required this.title,
    required this.logo,
    required this.category,
    required this.year,
    this.isFavorite = false,
  });

  Series copyWith({
    int? seriesId,
    String? title,
    String? logo,
    String? category,
    int? year,
    bool? isFavorite,
  }) {
    return Series(
      seriesId: seriesId ?? this.seriesId,
      title: title ?? this.title,
      logo: logo ?? this.logo,
      category: category ?? this.category,
      year: year ?? this.year,
      isFavorite: isFavorite ?? this.isFavorite,
    );
  }
}