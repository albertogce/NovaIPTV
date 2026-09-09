import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';
import 'package:iptv_flutter/data/models/series.dart';
import 'package:iptv_flutter/presentation/player/player_screen.dart';

class SeriesDetailScreen extends StatefulWidget {
  final Series series;
  final XtreamApiClient client;

  const SeriesDetailScreen({
    super.key,
    required this.series,
    required this.client,
  });

  @override
  State<SeriesDetailScreen> createState() => _SeriesDetailScreenState();
}

class _SeriesDetailScreenState extends State<SeriesDetailScreen> {
  Map<String, dynamic>? _seriesData;
  bool _isLoading = true;
  String? _selectedSeason;

  int? _lastEpisodeId;
  String? _lastEpisodeTitle;

  // FocusNodes for keyboard navigation
  final FocusNode _seasonListFocusNode = FocusNode();
  // Key to track first episode item focus
  final FocusNode _firstEpisodeFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _loadLastWatchedEpisode();
    _loadSeriesInfo();
  }

  @override
  void dispose() {
    _seasonListFocusNode.dispose();
    _firstEpisodeFocusNode.dispose();
    super.dispose();
  }

  void _loadLastWatchedEpisode() {
    final storage = SharedPrefsStorage();
    final key = 'last_episode_${widget.series.seriesId}';
    final stored = storage.getString(key);
    if (stored != null) {
      final parts = stored.split('|');
      if (parts.length >= 2) {
        _lastEpisodeId = int.tryParse(parts[0]);
        _lastEpisodeTitle = parts.sublist(1).join('|');
      }
    }
  }

  void _saveLastWatchedEpisode(int epId, String title) async {
    final storage = SharedPrefsStorage();
    final key = 'last_episode_${widget.series.seriesId}';
    await storage.setString(key, '$epId|$title');
    setState(() {
      _lastEpisodeId = epId;
      _lastEpisodeTitle = title;
    });
  }

  Future<void> _loadSeriesInfo() async {
    try {
      final data = await widget.client.getSeriesInfo(
        seriesId: widget.series.seriesId,
      );
      if (mounted) {
        final episodesMap = _episodesMap(data);
        // Auto-select first season
        final firstSeason = episodesMap.keys.isNotEmpty
            ? _sortSeasonKeys(episodesMap.keys.toList()).first
            : null;
        setState(() {
          _seriesData = data;
          _selectedSeason = firstSeason;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _playEpisode(Map<String, dynamic> ep) {
    final epId = ep['id'] ?? ep['stream_id'];
    final parsedEpisodeId = int.tryParse(epId?.toString() ?? '');
    if (parsedEpisodeId == null) return;
    final epTitle = _episodeTitle(ep);
    final allEpisodes = _allEpisodes;
    final streamUrl = _episodeStreamUrl(ep);
    final playableEpisodes = allEpisodes
        .where(
          (episode) =>
              int.tryParse(
                (episode['id'] ?? episode['stream_id']).toString(),
              ) !=
              null,
        )
        .toList();
    final queue = playableEpisodes
        .map(
          (episode) => PlayerQueueItem(
            streamUrl: _episodeStreamUrl(episode),
            title: '${widget.series.title} - ${_episodeTitle(episode)}',
          ),
        )
        .toList();
    final queueIndex = queue.indexWhere((item) => item.streamUrl == streamUrl);
    final validQueueIndex = queueIndex < 0 ? 0 : queueIndex;

    _saveToHistory();
    _saveLastWatchedEpisode(parsedEpisodeId, epTitle.toString());

    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => PlayerScreen(
          streamUrl: streamUrl,
          channelName: '${widget.series.title} - $epTitle',
          progressId: 'episode:$parsedEpisodeId',
          queue: queue,
          initialQueueIndex: validQueueIndex,
          onQueueIndexChanged: (index) {
            if (index < 0 || index >= playableEpisodes.length) return;
            final episode = playableEpisodes[index];
            final episodeId = int.tryParse(
              (episode['id'] ?? episode['stream_id']).toString(),
            );
            if (episodeId != null) {
              _saveLastWatchedEpisode(episodeId, _episodeTitle(episode));
            }
          },
        ),
      ),
    );
  }

  String _episodeTitle(Map<String, dynamic> episode) {
    final rawTitle = episode['title'] ?? episode['name'];
    if (rawTitle is String && rawTitle.trim().isNotEmpty) return rawTitle;
    return 'Episodio ${(episode['episode_num'] ?? episode['episode_number'] ?? '').toString()}';
  }

  String _episodeStreamUrl(Map<String, dynamic> episode) {
    final episodeId = episode['id'] ?? episode['stream_id'];
    final extension = episode['container_extension'] ?? 'mp4';
    final base = widget.client.baseUrl.replaceAll(RegExp(r'/$'), '');
    return '$base/series/${widget.client.username}/${widget.client.password}/$episodeId.$extension';
  }

  List<Map<String, dynamic>> get _allEpisodes {
    final episodesMap = _episodesMap(_seriesData);
    final result = <Map<String, dynamic>>[];
    for (final season in _sortedSeasons) {
      final rawEpisodes = episodesMap[season];
      if (rawEpisodes is List) {
        result.addAll(
          rawEpisodes.whereType<Map>().map(
            (episode) => Map<String, dynamic>.from(episode),
          ),
        );
      }
    }
    return result;
  }

  Future<void> _saveToHistory() async {
    final storage = SharedPrefsStorage();
    final history = storage.getStringList('watch_history') ?? [];
    final key = 'series:${widget.series.seriesId}';
    history.remove(key);
    history.insert(0, key);
    if (history.length > 50) history.removeRange(50, history.length);
    await storage.setStringList('watch_history', history);
  }

  List<String> get _sortedSeasons {
    return _sortSeasonKeys(_episodesMap(_seriesData).keys.toList());
  }

  List<Map<String, dynamic>> get _currentEpisodes {
    if (_selectedSeason == null) return [];
    final rawEpisodes = _episodesMap(_seriesData)[_selectedSeason];
    if (rawEpisodes is! List) return [];
    return rawEpisodes
        .whereType<Map>()
        .map((episode) => Map<String, dynamic>.from(episode))
        .toList();
  }

  Map<String, dynamic> _episodesMap(Map<String, dynamic>? data) {
    final rawEpisodes = data?['episodes'];
    if (rawEpisodes is! Map) return {};
    return rawEpisodes.map((key, value) => MapEntry(key.toString(), value));
  }

  List<String> _sortSeasonKeys(List<String> keys) {
    keys.sort((a, b) {
      final numericA = int.tryParse(a);
      final numericB = int.tryParse(b);
      if (numericA != null && numericB != null) {
        return numericA.compareTo(numericB);
      }
      if (numericA != null) {
        return -1;
      }
      if (numericB != null) {
        return 1;
      }
      return a.compareTo(b);
    });
    return keys;
  }

  @override
  Widget build(BuildContext context) {
    final info = _seriesData?['info'] is Map
        ? Map<String, dynamic>.from(_seriesData!['info'] as Map)
        : <String, dynamic>{};
    final rawPlot = info['plot'] ?? info['description'];
    final plot = rawPlot is String && rawPlot.trim().isNotEmpty
        ? rawPlot
        : 'Sin descripción disponible';
    final seasons = _sortedSeasons;
    final episodes = _currentEpisodes;

    final episodeColumns = MediaQuery.sizeOf(context).width >= 900 ? 2 : 1;

    return Scaffold(
      backgroundColor: const Color(0xFF0B1117),
      body: SafeArea(
        child: _isLoading
            ? const Center(
                child: CircularProgressIndicator(color: Color(0xFFFF6B4A)),
              )
            : Column(
                children: [
                  // ── Header: poster + info + last watched ──────────────────
                  _SeriesHeader(
                    series: widget.series,
                    plot: plot.toString(),
                    lastEpisodeTitle: _lastEpisodeTitle,
                  ),
                  // ── Season tabs (horizontal) ───────────────────────────────
                  if (seasons.isNotEmpty)
                    _SeasonSelector(
                      seasons: seasons,
                      selectedSeason: _selectedSeason,
                      onSeasonSelected: (s) {
                        setState(() => _selectedSeason = s);
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          if (mounted && _currentEpisodes.isNotEmpty) {
                            _firstEpisodeFocusNode.requestFocus();
                          }
                        });
                      },
                      seasonFocusNode: _seasonListFocusNode,
                      firstEpisodeFocusNode: _firstEpisodeFocusNode,
                    ),
                  const Divider(height: 1, color: Colors.white12),
                  // ── Episodes grid ─────────────────────────────────────────
                  Expanded(
                    child: episodes.isEmpty
                        ? const Center(
                            child: Text(
                              'No hay episodios disponibles',
                              style: TextStyle(color: Colors.white54),
                            ),
                          )
                        : GridView.builder(
                            padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                            gridDelegate:
                                SliverGridDelegateWithFixedCrossAxisCount(
                                  crossAxisCount: episodeColumns,
                                  mainAxisExtent: 150,
                                  crossAxisSpacing: 12,
                                  mainAxisSpacing: 12,
                                ),
                            itemCount: episodes.length,
                            itemBuilder: (context, index) {
                              final ep = episodes[index];
                              final isLastWatched =
                                  _lastEpisodeId != null &&
                                  int.tryParse(
                                        (ep['id'] ?? ep['stream_id'])
                                            .toString(),
                                      ) ==
                                      _lastEpisodeId;
                              return _EpisodeCard(
                                episode: ep,
                                isLastWatched: isLastWatched,
                                focusNode: index == 0
                                    ? _firstEpisodeFocusNode
                                    : null,
                                onArrowUp: index == 0
                                    ? () => _seasonListFocusNode.requestFocus()
                                    : null,
                                onPlay: () => _playEpisode(ep),
                              );
                            },
                          ),
                  ),
                ],
              ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header widget
// ─────────────────────────────────────────────────────────────────────────────
class _SeriesHeader extends StatelessWidget {
  final Series series;
  final String plot;
  final String? lastEpisodeTitle;

  const _SeriesHeader({
    required this.series,
    required this.plot,
    this.lastEpisodeTitle,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(12, 12, 12, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF1C333B), Color(0xFF15212A)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(13),
            child: SizedBox(
              width: 140,
              height: 198,
              child: series.logo.isNotEmpty
                  ? Image.network(
                      series.logo,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: Colors.black45,
                        child: const Icon(
                          Icons.tv,
                          size: 48,
                          color: Color(0xFFFFC857),
                        ),
                      ),
                    )
                  : Container(
                      color: Colors.black45,
                      child: const Icon(
                        Icons.tv,
                        size: 48,
                        color: Color(0xFFFFC857),
                      ),
                    ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  series.title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 7,
                  runSpacing: 7,
                  children: [
                    if (series.year > 0)
                      _SeriesMetaChip(
                        icon: Icons.calendar_today_outlined,
                        label: series.year.toString(),
                      ),
                    if (series.category.isNotEmpty)
                      _SeriesMetaChip(
                        icon: Icons.local_offer_outlined,
                        label: series.category,
                      ),
                  ],
                ),
                const SizedBox(height: 10),
                if (lastEpisodeTitle != null) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFF6B4A).withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: const Color(0xFFFF6B4A)),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.history,
                          color: Color(0xFFFF6B4A),
                          size: 16,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'Último visto: $lastEpisodeTitle',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
                Text(
                  plot,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 13,
                    height: 1.4,
                  ),
                  maxLines: 5,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SeriesMetaChip extends StatelessWidget {
  final IconData icon;
  final String label;

  const _SeriesMetaChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(7),
        border: Border.all(color: Colors.white12),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: const Color(0xFF5DE0C2), size: 14),
            const SizedBox(width: 5),
            Text(
              label,
              style: const TextStyle(
                color: Colors.white70,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Season selector (horizontal chips)
// ─────────────────────────────────────────────────────────────────────────────
class _SeasonSelector extends StatelessWidget {
  final List<String> seasons;
  final String? selectedSeason;
  final ValueChanged<String> onSeasonSelected;
  final FocusNode seasonFocusNode;
  final FocusNode firstEpisodeFocusNode;

  const _SeasonSelector({
    required this.seasons,
    required this.selectedSeason,
    required this.onSeasonSelected,
    required this.seasonFocusNode,
    required this.firstEpisodeFocusNode,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF15212A),
      height: 52,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: seasons.length,
        itemBuilder: (context, index) {
          final season = seasons[index];
          final isSelected = season == selectedSeason;

          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Focus(
              focusNode: index == 0 ? seasonFocusNode : null,
              onKeyEvent: (node, event) {
                if (event is KeyDownEvent) {
                  if (event.logicalKey == LogicalKeyboardKey.select ||
                      event.logicalKey == LogicalKeyboardKey.enter ||
                      event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
                    onSeasonSelected(season);
                    return KeyEventResult.handled;
                  }
                  if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
                    firstEpisodeFocusNode.requestFocus();
                    return KeyEventResult.handled;
                  }
                }
                return KeyEventResult.ignored;
              },
              child: Builder(
                builder: (context) {
                  final hasFocus = Focus.of(context).hasFocus;
                  return ChoiceChip(
                    label: Text(
                      'T$season',
                      style: TextStyle(
                        color: isSelected || hasFocus
                            ? Colors.white
                            : Colors.white60,
                        fontWeight: isSelected || hasFocus
                            ? FontWeight.bold
                            : FontWeight.normal,
                      ),
                    ),
                    selected: isSelected,
                    selectedColor: const Color(0xFFFF6B4A),
                    backgroundColor: hasFocus
                        ? const Color(0xFFB94732)
                        : const Color(0xFF1D3039),
                    onSelected: (_) => onSeasonSelected(season),
                  );
                },
              ),
            ),
          );
        },
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Episode card (thumbnail + title + synopsis)
// ─────────────────────────────────────────────────────────────────────────────
class _EpisodeCard extends StatefulWidget {
  final Map<String, dynamic> episode;
  final bool isLastWatched;
  final FocusNode? focusNode;
  final VoidCallback? onArrowUp;
  final VoidCallback onPlay;

  const _EpisodeCard({
    required this.episode,
    required this.isLastWatched,
    required this.onPlay,
    this.onArrowUp,
    this.focusNode,
  });

  @override
  State<_EpisodeCard> createState() => _EpisodeCardState();
}

class _EpisodeCardState extends State<_EpisodeCard> {
  bool _hasFocus = false;

  late final FocusNode _focusNode;

  @override
  void initState() {
    super.initState();
    _focusNode = widget.focusNode ?? FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  void _onFocusChange() {
    if (mounted) setState(() => _hasFocus = _focusNode.hasFocus);
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    // Only dispose if we created it ourselves
    if (widget.focusNode == null) _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ep = widget.episode;
    final info = ep['info'] is Map
        ? Map<String, dynamic>.from(ep['info'] as Map)
        : <String, dynamic>{};
    final epNum = (ep['episode_num'] ?? ep['episode_number'])?.toString() ?? '';
    final rawTitle = ep['title'] ?? ep['name'];
    final title = rawTitle is String && rawTitle.trim().isNotEmpty
        ? rawTitle
        : 'Episodio $epNum';
    final rawPlot = info['plot'] ?? info['description'];
    final plot = rawPlot is String ? rawPlot : '';
    final rawThumbnail =
        info['movie_image'] ??
        info['thumbnail'] ??
        info['cover_big'] ??
        ep['movie_image'];
    final thumbnail = rawThumbnail is String ? rawThumbnail : '';

    return Focus(
      focusNode: _focusNode,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect)) {
          widget.onPlay();
          return KeyEventResult.handled;
        }
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.arrowUp &&
            widget.onArrowUp != null) {
          widget.onArrowUp!();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: GestureDetector(
        onTap: widget.onPlay,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            color: _hasFocus
                ? const Color(0xFF3B211C)
                : const Color(0xFF15212A),
            border: Border.all(
              color: _hasFocus
                  ? const Color(0xFFFF6B4A)
                  : (widget.isLastWatched ? Colors.amber : Colors.white12),
              width: _hasFocus || widget.isLastWatched ? 2 : 1,
            ),
            boxShadow: _hasFocus
                ? [
                    BoxShadow(
                      color: const Color(0xFFFF6B4A).withValues(alpha: 0.35),
                      blurRadius: 12,
                    ),
                  ]
                : [],
          ),
          child: Row(
            children: [
              // Thumbnail
              ClipRRect(
                borderRadius: const BorderRadius.horizontal(
                  left: Radius.circular(7),
                ),
                child: SizedBox(
                  width: 150,
                  height: double.infinity,
                  child: thumbnail.isNotEmpty
                      ? Image.network(
                          thumbnail,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) =>
                              _placeholderThumbnail(epNum),
                        )
                      : _placeholderThumbnail(epNum),
                ),
              ),
              // Info
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 8,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Row(
                        children: [
                          if (widget.isLastWatched)
                            const Padding(
                              padding: EdgeInsets.only(right: 4),
                              child: Icon(
                                Icons.play_circle_fill,
                                color: Colors.amber,
                                size: 14,
                              ),
                            ),
                          Expanded(
                            child: Text(
                              epNum.isNotEmpty ? '$epNum. $title' : title,
                              style: TextStyle(
                                color: widget.isLastWatched
                                    ? Colors.amber
                                    : Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                              ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                      if (plot.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          plot,
                          style: const TextStyle(
                            color: Colors.white54,
                            fontSize: 10,
                            height: 1.3,
                          ),
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                      if (widget.isLastWatched) ...[
                        const SizedBox(height: 4),
                        const Text(
                          'Último visualizado',
                          style: TextStyle(
                            color: Colors.amber,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              // Play icon
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: Icon(
                  Icons.play_circle_outline,
                  color: _hasFocus ? const Color(0xFFFF6B4A) : Colors.white24,
                  size: 24,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _placeholderThumbnail(String epNum) {
    return Container(
      color: const Color(0xFF2C2C2C),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.movie, color: Colors.white24, size: 28),
          if (epNum.isNotEmpty)
            Text(
              'Ep. $epNum',
              style: const TextStyle(color: Colors.white38, fontSize: 10),
            ),
        ],
      ),
    );
  }
}
