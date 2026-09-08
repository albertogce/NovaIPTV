class Series {
  final int seriesId;
  final String title;
  final String logo;
  final String category;
  final String categoryId;
  final int year;

  Series({
    required this.seriesId,
    required this.title,
    required this.logo,
    required this.category,
    required this.categoryId,
    required this.year,
  });

  factory Series.fromJson(Map<String, dynamic> json) {
    int parseInt(dynamic val) {
      if (val is int) return val;
      if (val is String) return int.tryParse(val) ?? 0;
      return 0;
    }

    return Series(
      seriesId: parseInt(json['series_id']),
      title: (json['name'] ?? json['title'] ?? '').toString(),
      logo: (json['cover'] ?? json['logo'] ?? json['stream_icon'] ?? '').toString(),
      category: (json['category_name'] ?? json['category'] ?? '').toString(),
      categoryId: (json['category_id'] ?? '').toString(),
      year: parseInt(json['year']),
    );
  }

  Map<String, dynamic> toJson() => {
        'series_id': seriesId,
        'name': title,
        'cover': logo,
        'category_name': category,
        'category_id': categoryId,
        'year': year,
      };
}