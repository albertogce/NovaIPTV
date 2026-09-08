import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';
import 'package:iptv_flutter/data/api/xtream_api_client.dart';
import 'package:iptv_flutter/data/models/vod_movie.dart';
import 'package:iptv_flutter/presentation/player/player_screen.dart';

class MovieDetailScreen extends StatefulWidget {
  final VodMovie movie;
  final XtreamApiClient client;

  const MovieDetailScreen({
    super.key,
    required this.movie,
    required this.client,
  });

  @override
  State<MovieDetailScreen> createState() => _MovieDetailScreenState();
}

class _MovieDetailScreenState extends State<MovieDetailScreen> {
  Map<String, dynamic>? _vodInfo;

  final FocusNode _playButtonFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _loadMovieDetail();
  }

  @override
  void dispose() {
    _playButtonFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadMovieDetail() async {
    try {
      final data = await widget.client.getVodInfo(vodId: widget.movie.movieId);
      if (mounted) {
        setState(() {
          _vodInfo = data;
        });
      }
    } catch (_) {
      // vodInfo remains null; URL will use mp4 fallback
    }
  }

  String _buildMovieStreamUrl() {
    final base = widget.client.baseUrl.replaceAll(RegExp(r'/$'), '');
    // Try to get extension from vod info; fallback to mp4
    final ext =
        _vodInfo?['movie_data']?['container_extension'] as String? ??
        _vodInfo?['info']?['container_extension'] as String? ??
        'mp4';
    return '$base/movie/${widget.client.username}/${widget.client.password}/${widget.movie.movieId}.$ext';
  }

  void _playMovie() {
    final url = _buildMovieStreamUrl();
    _saveToHistory();
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) =>
            PlayerScreen(streamUrl: url, channelName: widget.movie.title),
      ),
    );
  }

  Future<void> _saveToHistory() async {
    final storage = SharedPrefsStorage();
    final history = storage.getStringList('watch_history') ?? [];
    final key = 'movie:${widget.movie.movieId}';
    history.remove(key);
    history.insert(0, key);
    if (history.length > 50) history.removeRange(50, history.length);
    await storage.setStringList('watch_history', history);
  }

  @override
  Widget build(BuildContext context) {
    final info = _vodInfo?['info'] ?? {};
    final plot =
        info['plot'] ?? info['description'] ?? 'Sin descripción disponible';
    final genre = info['genre'] ?? widget.movie.category;
    final rating = info['rating'] ?? info['rating_5rating'] ?? '';
    final director = info['director'] ?? '';

    return Scaffold(
      backgroundColor: const Color(0xFF0B1117),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: SizedBox(
                  width: 220,
                  height: 320,
                  child: widget.movie.logo.isNotEmpty
                      ? Image.network(
                          widget.movie.logo,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => Container(
                            color: Colors.black45,
                            child: const Icon(
                              Icons.movie,
                              size: 80,
                              color: Colors.amber,
                            ),
                          ),
                        )
                      : Container(
                          color: Colors.black45,
                          child: const Icon(
                            Icons.movie,
                            size: 80,
                            color: Colors.amber,
                          ),
                        ),
                ),
              ),
              const SizedBox(width: 28),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.movie.title,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 26,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        if (widget.movie.year > 0) ...[
                          Chip(
                            label: Text(
                              '${widget.movie.year}',
                              style: const TextStyle(color: Colors.white),
                            ),
                            backgroundColor: Colors.white12,
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (genre.toString().isNotEmpty) ...[
                          Chip(
                            label: Text(
                              '$genre',
                              style: const TextStyle(color: Colors.white),
                            ),
                            backgroundColor: Colors.white12,
                          ),
                          const SizedBox(width: 8),
                        ],
                        if (rating.toString().isNotEmpty) ...[
                          Chip(
                            avatar: const Icon(
                              Icons.star,
                              color: Colors.amber,
                              size: 16,
                            ),
                            label: Text(
                              '$rating',
                              style: const TextStyle(color: Colors.white),
                            ),
                            backgroundColor: Colors.white12,
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 16),
                    if (director.toString().isNotEmpty) ...[
                      Text(
                        'Director: $director',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                    Text(
                      plot.toString(),
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 15,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 28),
                    // Play button — handles D-Pad Enter/Select explicitly
                    Focus(
                      focusNode: _playButtonFocusNode,
                      autofocus: true,
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            (event.logicalKey == LogicalKeyboardKey.select ||
                                event.logicalKey == LogicalKeyboardKey.enter ||
                                event.logicalKey ==
                                    LogicalKeyboardKey.gameButtonSelect)) {
                          _playMovie();
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: ValueListenableBuilder<bool>(
                        valueListenable: _PlayButtonFocusListenable(
                          _playButtonFocusNode,
                        ),
                        builder: (context, hasFocus, _) {
                          return AnimatedContainer(
                            duration: const Duration(milliseconds: 150),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(
                                color: hasFocus
                                    ? Colors.white
                                    : Colors.transparent,
                                width: 2,
                              ),
                              boxShadow: hasFocus
                                  ? [
                                      BoxShadow(
                                        color: const Color(
                                          0xFFE91E63,
                                        ).withValues(alpha: 0.5),
                                        blurRadius: 16,
                                        spreadRadius: 2,
                                      ),
                                    ]
                                  : [],
                            ),
                            child: ElevatedButton.icon(
                              focusNode: FocusNode(skipTraversal: true),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: hasFocus
                                    ? const Color(0xFFE91E63)
                                    : const Color(0xFF880E4F),
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 28,
                                  vertical: 16,
                                ),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(6),
                                ),
                              ),
                              icon: const Icon(
                                Icons.play_arrow,
                                color: Colors.white,
                                size: 28,
                              ),
                              label: const Text(
                                'REPRODUCIR PELÍCULA',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              onPressed: _playMovie,
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Simple ValueNotifier that tracks whether a FocusNode has focus,
/// so we can use ValueListenableBuilder for clean focus-based rebuilds.
class _PlayButtonFocusListenable extends ValueNotifier<bool> {
  final FocusNode _focusNode;

  _PlayButtonFocusListenable(this._focusNode) : super(_focusNode.hasFocus) {
    _focusNode.addListener(_onFocusChange);
  }

  void _onFocusChange() {
    value = _focusNode.hasFocus;
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    super.dispose();
  }
}
