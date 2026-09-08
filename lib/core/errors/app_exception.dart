class AppException implements Exception {
  final String message;
  final String? prefix;
  final int? code;

  AppException(this.message, [this.prefix, this.code]);

  @override
  String toString() => '$prefix$message';
}