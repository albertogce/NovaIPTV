class SeriesCategory {
  final String categoryId;
  final String categoryName;

  SeriesCategory({required this.categoryId, required this.categoryName});

  factory SeriesCategory.fromJson(Map<String, dynamic> json) {
    return SeriesCategory(
      categoryId: (json['category_id'] ?? '').toString(),
      categoryName: (json['category_name'] ?? '').toString(),
    );
  }

  Map<String, dynamic> toJson() => {
    'category_id': categoryId,
    'category_name': categoryName,
  };
}
