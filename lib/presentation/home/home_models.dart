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

/// Categoría sintética que agrupa los canales favoritos en la vista de Directos.
const favoritesCategoryId = '__favorites__';

class HistoryEntry {
  final String type;
  final String id;
  final String title;
  final String logo;

  const HistoryEntry({
    required this.type,
    required this.id,
    required this.title,
    required this.logo,
  });
}
