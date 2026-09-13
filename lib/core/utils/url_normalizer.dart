String normalizeUrl(String url) {
  var normalized = url.trim();
  if (normalized.isEmpty) return '';
  if (!normalized.startsWith('http://') && !normalized.startsWith('https://')) {
    normalized = 'http://$normalized';
  }
  normalized = normalized.replaceAll(RegExp(r'/+$'), '');
  return normalized;
}

/// Oculta usuario y contraseña en URLs Xtream directas
/// (`/live/user/pass/id.ts`) y en parámetros `username`/`password`, antes de
/// mostrar texto o registrarlo.
String sanitizeStreamUrl(String text) {
  final withoutQueryCredentials = text.replaceAllMapped(
    RegExp(
      "([?&](?:username|password)=)[^&\\s'\"]+",
      caseSensitive: false,
    ),
    (match) => '${match.group(1)}***',
  );
  return withoutQueryCredentials.replaceAllMapped(
    RegExp("((?:live|movie|series|timeshift)/)[^/]+/[^/]+(/[^'\"\\s]*)"),
    (match) => '${match.group(1)}***/***${match.group(2)}',
  );
}
