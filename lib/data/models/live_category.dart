class LiveCategory {
  final String categoryId;
  final String categoryName;

  LiveCategory({required this.categoryId, required this.categoryName});

  factory LiveCategory.fromJson(Map<String, dynamic> json) {
    return LiveCategory(
      categoryId: (json['category_id'] ?? '').toString(),
      categoryName: (json['category_name'] ?? '').toString(),
    );
  }

  Map<String, dynamic> toJson() => {
    'category_id': categoryId,
    'category_name': categoryName,
  };
}
