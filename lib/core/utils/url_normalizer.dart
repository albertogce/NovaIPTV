String normalizeUrl(String url) {
  var normalized = url.trim();
  if (normalized.isEmpty) return '';
  if (!normalized.startsWith('http://') && !normalized.startsWith('https://')) {
    normalized = 'http://$normalized';
  }
  normalized = normalized.replaceAll(RegExp(r'/+$'), '');
  return normalized;
}