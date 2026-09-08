import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import '../../core/playback/watch_progress_store.dart';

class PlayerQueueItem {
  final String streamUrl;
  final String title;

  const PlayerQueueItem({required this.streamUrl, required this.title});
}

class PlayerScreen extends StatefulWidget {
  final String streamUrl;
  final String channelName;
  final List<PlayerQueueItem> queue;
  final int initialQueueIndex;
  final ValueChanged<int>? onQueueIndexChanged;
  final String? progressId;

  const PlayerScreen({
    super.key,
    required this.streamUrl,
    required this.channelName,
    this.queue = const [],
    this.initialQueueIndex = 0,
    this.onQueueIndexChanged,
    this.progressId,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  VideoPlayerController? _controller;
  bool _isInitialized = false;
  bool _hasError = false;
  String _errorMessage = '';
  bool _showControls = true;
  bool _isClosing = false;
  late int _queueIndex;
  late String _currentChannelName;

  final FocusNode _focusNode = FocusNode();
  final FocusNode _progressFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _queueIndex = widget.queue.isEmpty ? 0 : widget.initialQueueIndex;
    if (_queueIndex < 0) _queueIndex = 0;
    if (_queueIndex >= widget.queue.length && widget.queue.isNotEmpty) {
      _queueIndex = widget.queue.length - 1;
    }
    _currentChannelName = widget.channelName;
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      final item = widget.queue.isEmpty ? null : widget.queue[_queueIndex];
      final url = item?.streamUrl ?? widget.streamUrl;
      final controller = VideoPlayerController.networkUrl(
        Uri.parse(url),
        httpHeaders: const {'User-Agent': 'IPTV-Flutter/1.0'},
      );
      _controller = controller;
      await controller.initialize();
      if (!mounted) return;
      setState(() => _isInitialized = true);
      controller.addListener(_saveProgress);
      final saved = widget.progressId == null ? null : WatchProgressStore().get(widget.progressId!);
      if (saved != null && saved.position < controller.value.duration) {
        await controller.seekTo(saved.position);
      }
      controller.play();
      Future.delayed(const Duration(seconds: 5), () {
        if (mounted) setState(() => _showControls = false);
      });
    } catch (e) {
      await _controller?.dispose();
      _controller = null;
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = 'No se pudo reproducir el canal.\n$e';
        });
      }
    }
  }

  Future<void> _saveProgress() async {
    if (widget.progressId == null || _controller == null || !_controller!.value.isInitialized) return;
    final value = _controller!.value;
    if (value.duration.inSeconds < 1 || value.position.inSeconds < 2) return;
    await WatchProgressStore().save(WatchProgress(id: widget.progressId!, title: _currentChannelName, position: value.position, duration: value.duration, updatedAt: DateTime.now()));
  }

  void _toggleControls() {
    setState(() => _showControls = !_showControls);
    if (_showControls) {
      Future.delayed(const Duration(seconds: 5), () {
        if (mounted && _showControls) setState(() => _showControls = false);
      });
    }
  }

  void _togglePlayPause() {
    if (!_isInitialized) return;
    setState(() {
      if (_controller!.value.isPlaying) {
        _controller!.pause();
      } else {
        _controller!.play();
      }
    });
  }

  Future<void> _seekBy(Duration offset) async {
    if (!_isInitialized) return;
    final position = _controller!.value.position + offset;
    final duration = _controller!.value.duration;
    final target = position < Duration.zero
        ? Duration.zero
        : position > duration
        ? duration
        : position;
    await _controller!.seekTo(target);
    _showControlsBriefly();
  }

  void _showControlsBriefly() {
    if (!mounted) return;
    setState(() => _showControls = true);
    Future.delayed(const Duration(seconds: 5), () {
      if (mounted) setState(() => _showControls = false);
    });
  }

  Future<void> _changeQueueItem(int offset) async {
    if (widget.queue.isEmpty || !_isInitialized) return;
    final nextIndex = _queueIndex + offset;
    if (nextIndex < 0 || nextIndex >= widget.queue.length) return;

    await _controller!.pause();
    await _controller!.dispose();
    _controller = null;
    if (!mounted) return;
    setState(() {
      _queueIndex = nextIndex;
      _currentChannelName = widget.queue[nextIndex].title;
      _isInitialized = false;
      _hasError = false;
      _errorMessage = '';
      _showControls = true;
    });
    widget.onQueueIndexChanged?.call(nextIndex);
    await _initPlayer();
  }

  void _closePlayer() {
    if (_isClosing || !mounted) return;
    _isClosing = true;
    Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _saveProgress();
    _focusNode.dispose();
    _progressFocusNode.dispose();
    _controller?.dispose();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Focus(
          focusNode: _focusNode..requestFocus(),
          autofocus: true,
          onKeyEvent: (node, event) {
            if (event is! KeyDownEvent) return KeyEventResult.ignored;
            if (event.logicalKey == LogicalKeyboardKey.goBack) {
              // Android TV dispatches this key together with the system back action.
              // Let PopScope handle the route pop exactly once.
              return KeyEventResult.ignored;
            }
            if (event.logicalKey == LogicalKeyboardKey.escape) {
              _closePlayer();
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
              _seekBy(const Duration(seconds: -10));
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
              _seekBy(const Duration(seconds: 10));
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.arrowUp) {
              _changeQueueItem(-1);
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
              _changeQueueItem(1);
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.select ||
                event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.gameButtonSelect ||
                event.logicalKey == LogicalKeyboardKey.mediaPlayPause) {
              _toggleControls();
              _togglePlayPause();
              return KeyEventResult.handled;
            }
            return KeyEventResult.ignored;
          },
          child: GestureDetector(
            onTap: _toggleControls,
            child: Stack(
              alignment: Alignment.center,
              children: [
                if (_isInitialized)
                  Center(
                    child: AspectRatio(
                      aspectRatio: _controller!.value.aspectRatio,
                      child: VideoPlayer(_controller!),
                    ),
                  )
                else if (_hasError)
                  _ErrorView(message: _errorMessage, onBack: _closePlayer)
                else
                  const Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircularProgressIndicator(color: Color(0xFFFF6B4A)),
                        SizedBox(height: 16),
                        Text(
                          'Conectando al canal...',
                          style: TextStyle(color: Colors.white70, fontSize: 16),
                        ),
                      ],
                    ),
                  ),
                if (_showControls && !_hasError)
                  _ControlsOverlay(
                    channelName: _currentChannelName,
                    isPlaying: _isInitialized && _controller!.value.isPlaying,
                    onBack: _closePlayer,
                    onPlayPause: _togglePlayPause,
                    hasPrevious: _queueIndex > 0,
                    hasNext: _queueIndex < widget.queue.length - 1,
                    onPrevious: () => _changeQueueItem(-1),
                    onNext: () => _changeQueueItem(1),
                    position: _controller!.value.position,
                    duration: _controller!.value.duration,
                    onSeek: (value) => _controller!.seekTo(value),
                    progressFocusNode: _progressFocusNode,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ControlsOverlay extends StatelessWidget {
  final String channelName;
  final bool isPlaying;
  final VoidCallback onBack;
  final VoidCallback onPlayPause;
  final bool hasPrevious;
  final bool hasNext;
  final VoidCallback onPrevious;
  final VoidCallback onNext;
  final Duration position;
  final Duration duration;
  final ValueChanged<Duration> onSeek;
  final FocusNode progressFocusNode;

  const _ControlsOverlay({
    required this.channelName,
    required this.isPlaying,
    required this.onBack,
    required this.onPlayPause,
    required this.hasPrevious,
    required this.hasNext,
    required this.onPrevious,
    required this.onNext,
    required this.position,
    required this.duration,
    required this.onSeek,
    required this.progressFocusNode,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black54,
      child: Column(
        children: [
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(
                      Icons.arrow_back,
                      color: Colors.white,
                      size: 28,
                    ),
                    onPressed: onBack,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      channelName,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const Icon(Icons.live_tv, color: Color(0xFFFF6B4A), size: 24),
                ],
              ),
            ),
          ),
          const Spacer(),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (hasPrevious)
                IconButton(
                  tooltip: 'Capítulo anterior',
                  icon: const Icon(
                    Icons.skip_previous,
                    color: Colors.white,
                    size: 42,
                  ),
                  onPressed: onPrevious,
                ),
              IconButton(
                icon: Icon(
                  isPlaying
                      ? Icons.pause_circle_filled
                      : Icons.play_circle_filled,
                  color: Colors.white,
                  size: 72,
                ),
                onPressed: onPlayPause,
              ),
              if (hasNext)
                IconButton(
                  tooltip: 'Siguiente capítulo',
                  icon: const Icon(
                    Icons.skip_next,
                    color: Colors.white,
                    size: 42,
                  ),
                  onPressed: onNext,
                ),
            ],
          ),
          const Spacer(),
          Focus(
            focusNode: progressFocusNode,
            autofocus: true,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Row(children: [
                Text(_formatDuration(position), style: const TextStyle(color: Colors.white, fontSize: 12)),
                const SizedBox(width: 8),
                Expanded(child: Slider(
                  min: 0,
                  max: duration.inMilliseconds > 0 ? duration.inMilliseconds.toDouble() : 1,
                  value: position.inMilliseconds.clamp(0, duration.inMilliseconds > 0 ? duration.inMilliseconds : 1).toDouble(),
                  onChanged: (value) => onSeek(Duration(milliseconds: value.round())),
                )),
                const SizedBox(width: 8),
                Text(_formatDuration(duration), style: const TextStyle(color: Colors.white, fontSize: 12)),
              ],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFFFF6B4A),
                borderRadius: BorderRadius.circular(4),
              ),
              child: const Text(
                'EN VIVO',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.5,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _formatDuration(Duration value) => '${value.inMinutes.remainder(60).toString().padLeft(2, '0')}:${value.inSeconds.remainder(60).toString().padLeft(2, '0')}';

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onBack;

  const _ErrorView({required this.message, required this.onBack});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.signal_wifi_off,
              color: Colors.redAccent,
              size: 64,
            ),
            const SizedBox(height: 16),
            const Text(
              'Canal no disponible',
              style: TextStyle(
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54, fontSize: 13),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              icon: const Icon(Icons.arrow_back),
              label: const Text('Volver'),
              onPressed: onBack,
            ),
          ],
        ),
      ),
    );
  }
}
