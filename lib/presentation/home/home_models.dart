part of 'home_screen.dart';

enum ActiveView {
  home,
  live,
  movies,
  series,
  favorites,
  history,
  continueWatching,
  settings,
}

class _HistoryEntry {
  final String type;
  final String id;
  final String title;
  final String logo;

  const _HistoryEntry({
    required this.type,
    required this.id,
    required this.title,
    required this.logo,
  });
}
