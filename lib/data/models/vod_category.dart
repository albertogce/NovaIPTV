class VodCategory {
  final String categoryId;
  final String categoryName;

  VodCategory({required this.categoryId, required this.categoryName});

  factory VodCategory.fromJson(Map<String, dynamic> json) {
    return VodCategory(
      categoryId: (json['category_id'] ?? '').toString(),
      categoryName: (json['category_name'] ?? '').toString(),
    );
  }

  Map<String, dynamic> toJson() => {
    'category_id': categoryId,
    'category_name': categoryName,
  };
}
