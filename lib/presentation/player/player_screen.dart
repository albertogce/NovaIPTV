import 'dart:async';
import 'dart:developer' as developer;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../../core/playback/watch_progress_store.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/url_normalizer.dart';

const _seekStep = Duration(seconds: 10);
const _seekStepLarge = Duration(seconds: 30);
const _seekStepBar = Duration(seconds: 60);
const _seekStepBarLarge = Duration(minutes: 2);
const _controlsHideDelay = Duration(seconds: 5);

class PlayerQueueItem {
  final String streamUrl;
  final String title;
  final String? episodeId;

  const PlayerQueueItem({
    required this.streamUrl,
    required this.title,
    this.episodeId,
  });
}

enum _VideoFitMode { contain, cover, stretch, zoom }

class PlayerScreen extends StatefulWidget {
  final String streamUrl;
  final String channelName;
  final List<PlayerQueueItem> queue;
  final int initialQueueIndex;
  final ValueChanged<int>? onQueueIndexChanged;
  final String? progressId;
  final bool isLive;

  const PlayerScreen({
    super.key,
    required this.streamUrl,
    required this.channelName,
    this.queue = const [],
    this.initialQueueIndex = 0,
    this.onQueueIndexChanged,
    this.progressId,
    this.isLive = false,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  VideoPlayerController? _controller;
  bool _isInitialized = false;
  bool _hasError = false;
  String _errorMessage = '';
  String _lastAttemptedUrl = '';
  bool _showControls = true;
  bool _isClosing = false;
  int _connectionAttempt = 0;
  late int _queueIndex;
  late String _currentChannelName;
  String? _currentEpisodeId;
  bool _advancingToNext = false;
  bool _treatAsLive = false;
  _VideoFitMode _fitMode = _VideoFitMode.contain;
  double _volume = 1;
  String? _resumeHint;
  String? _osdHint;
  Timer? _controlsTimer;
  Timer? _osdTimer;
  Timer? _saveTimer;
  DateTime? _lastUiUpdate;
  bool? _lastPlaying;
  bool? _lastBuffering;

  final FocusNode _rootFocusNode = FocusNode(debugLabel: 'player-root');
  final FocusNode _backFocusNode = FocusNode(debugLabel: 'player-back');
  final FocusNode _rewindFocusNode = FocusNode(debugLabel: 'player-rewind');
  final FocusNode _previousFocusNode = FocusNode(debugLabel: 'player-prev');
  final FocusNode _playPauseFocusNode = FocusNode(debugLabel: 'player-play');
  final FocusNode _nextFocusNode = FocusNode(debugLabel: 'player-next');
  final FocusNode _forwardFocusNode = FocusNode(debugLabel: 'player-forward');
  final FocusNode _progressFocusNode = FocusNode(debugLabel: 'player-progress');
  final FocusNode _fitFocusNode = FocusNode(debugLabel: 'player-fit');
  final FocusNode _restartFocusNode = FocusNode(debugLabel: 'player-restart');
  final FocusNode _retryFocusNode = FocusNode(debugLabel: 'player-retry');

  bool get _canSeek =>
      _isInitialized && !_treatAsLive && _controller!.value.duration > Duration.zero;

  bool get _isPlaying => _isInitialized && (_controller?.value.isPlaying ?? false);

  bool get _isBuffering =>
      _isInitialized && (_controller?.value.isBuffering ?? false);

  bool get _hasPrevious => widget.queue.isNotEmpty && _queueIndex > 0;

  bool get _hasNext =>
      widget.queue.isNotEmpty && _queueIndex < widget.queue.length - 1;

  Duration get _position => _controller?.value.position ?? Duration.zero;

  Duration get _duration => _controller?.value.duration ?? Duration.zero;

  @override
  void initState() {
    super.initState();
    _queueIndex = widget.queue.isEmpty ? 0 : widget.initialQueueIndex;
    if (_queueIndex < 0) _queueIndex = 0;
    if (widget.queue.isNotEmpty && _queueIndex >= widget.queue.length) {
      _queueIndex = widget.queue.length - 1;
    }
    _currentChannelName = widget.channelName;
    _treatAsLive = widget.isLive;
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    WakelockPlus.enable();
    HardwareKeyboard.instance.addHandler(_onHardwareKey);
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    final connectionAttempt = ++_connectionAttempt;
    VideoPlayerController? controller;
    try {
      final item = widget.queue.isEmpty ? null : widget.queue[_queueIndex];
      final url = item?.streamUrl ?? widget.streamUrl;
      _currentEpisodeId = item?.episodeId;
      _lastAttemptedUrl = url;
      controller = VideoPlayerController.networkUrl(
        Uri.parse(url),
        httpHeaders: const {'User-Agent': 'IPTV-Flutter/1.0', 'Accept': '*/*'},
      );
      _controller = controller;
      await controller.initialize();
      if (!mounted || _isClosing || connectionAttempt != _connectionAttempt) {
        await controller.dispose();
        return;
      }

      controller.addListener(_onControllerUpdate);
      await controller.setVolume(_volume);

      String? resumeHint;
      final saved = widget.progressId == null
          ? null
          : WatchProgressStore().get(widget.progressId!);
      final duration = controller.value.duration;
      final looksLive = widget.isLive || duration <= Duration.zero;
      // ponytail: progressId is per-series, so only resume same episode.
      final isSameEpisode = saved?.episodeId == _currentEpisodeId;
      if (!looksLive &&
          saved != null &&
          isSameEpisode &&
          saved.position > const Duration(seconds: 3) &&
          saved.position < duration - const Duration(seconds: 5)) {
        await controller.seekTo(saved.position);
        resumeHint = 'Reanudando desde ${_formatDuration(saved.position)}';
      }

      await controller.play();
      if (!mounted || _isClosing || connectionAttempt != _connectionAttempt) {
        return;
      }
      setState(() {
        _isInitialized = true;
        _treatAsLive = looksLive;
        _resumeHint = resumeHint;
        _showControls = true;
      });
      _focusPlayButton();
      _scheduleControlsHide();
      if (resumeHint != null) {
        Future<void>.delayed(const Duration(seconds: 3), () {
          if (mounted) setState(() => _resumeHint = null);
        });
      }
    } catch (e) {
      if (identical(_controller, controller)) {
        _controller = null;
      }
      await controller?.dispose();
      if (widget.queue.isEmpty &&
          connectionAttempt == 1 &&
          (widget.streamUrl.endsWith('.ts') ||
              widget.streamUrl.endsWith('.m3u8'))) {
        developer.log(
          'Reintentando el canal con una URL sin extensión',
          name: 'PlayerScreen',
        );
        if (mounted && !_isClosing) {
          await _initPlayer();
          return;
        }
      }
      if (mounted && !_isClosing && connectionAttempt == _connectionAttempt) {
        setState(() {
          _hasError = true;
          _errorMessage = _friendlyPlaybackError(e);
        });
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _retryFocusNode.requestFocus();
        });
      }
    }
  }

  void _onControllerUpdate() {
    final value = _controller?.value;
    if (value == null || _isClosing) return;

    if (value.hasError && !_hasError) {
      setState(() {
        _hasError = true;
        _errorMessage = _friendlyPlaybackError(
          value.errorDescription ?? 'Error de reproducción inesperado.',
        );
      });
      return;
    }

    final significant =
        value.isPlaying != _lastPlaying || value.isBuffering != _lastBuffering;
    _lastPlaying = value.isPlaying;
    _lastBuffering = value.isBuffering;
    final now = DateTime.now();
    if (significant ||
        _lastUiUpdate == null ||
        now.difference(_lastUiUpdate!) >= const Duration(milliseconds: 250)) {
      _lastUiUpdate = now;
      if (mounted) setState(() {});
    }

    _saveTimer ??= Timer(const Duration(seconds: 5), () {
      _saveTimer = null;
      _saveProgress();
    });

    // ponytail: live has no "next episode"; position sits at the live edge.
    if (_advancingToNext ||
        widget.queue.isEmpty ||
        !_isInitialized ||
        _treatAsLive) {
      return;
    }
    if (!value.isInitialized || value.duration <= Duration.zero) return;
    if (value.position >= value.duration - const Duration(milliseconds: 500) &&
        _queueIndex < widget.queue.length - 1) {
      _advancingToNext = true;
      _changeQueueItem(1).whenComplete(() => _advancingToNext = false);
    }
  }

  Future<void> _saveProgress() async {
    if (widget.progressId == null ||
        _treatAsLive ||
        _controller == null ||
        !_controller!.value.isInitialized) {
      return;
    }
    final value = _controller!.value;
    if (value.duration.inSeconds < 1 || value.position.inSeconds < 2) return;
    await WatchProgressStore().save(
      WatchProgress(
        id: widget.progressId!,
        title: _currentChannelName,
        position: value.position,
        duration: value.duration,
        updatedAt: DateTime.now(),
        episodeId: _currentEpisodeId,
      ),
    );
  }

  bool _onHardwareKey(KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) return false;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.mediaPlayPause) {
      _togglePlayPause();
      _revealControls();
      return true;
    }
    if (key == LogicalKeyboardKey.mediaPlay) {
      _play();
      _revealControls();
      return true;
    }
    if (key == LogicalKeyboardKey.mediaPause) {
      _pause();
      _revealControls();
      return true;
    }
    if (key == LogicalKeyboardKey.mediaStop) {
      _closePlayer();
      return true;
    }
    if (key == LogicalKeyboardKey.mediaFastForward) {
      _seekBy(event is KeyRepeatEvent ? _seekStepLarge : _seekStep);
      return true;
    }
    if (key == LogicalKeyboardKey.mediaRewind) {
      _seekBy(event is KeyRepeatEvent ? -_seekStepLarge : -_seekStep);
      return true;
    }
    if (key == LogicalKeyboardKey.mediaTrackNext) {
      _changeQueueItem(1);
      return true;
    }
    if (key == LogicalKeyboardKey.mediaTrackPrevious) {
      _changeQueueItem(-1);
      return true;
    }
    // ponytail: CH+/- zaps live channels; ignored elsewhere.
    if (key == LogicalKeyboardKey.channelUp && _treatAsLive) {
      _changeQueueItem(1);
      _revealControls();
      return true;
    }
    if (key == LogicalKeyboardKey.channelDown && _treatAsLive) {
      _changeQueueItem(-1);
      _revealControls();
      return true;
    }
    return false;
  }

  KeyEventResult _onRootKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    // ponytail: TV back arrives as key event AND system back; PopScope owns
    // it — swallowing here avoids re-showing controls on the way out.
    if (key == LogicalKeyboardKey.goBack) return KeyEventResult.handled;
    final isSelect =
        key == LogicalKeyboardKey.select ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.gameButtonSelect ||
        key == LogicalKeyboardKey.space;

    if (key == LogicalKeyboardKey.escape) {
      _onBack();
      return KeyEventResult.handled;
    }

    if (!_showControls) {
      if (isSelect) {
        _revealControls();
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowLeft) {
        _seekBy(event is KeyRepeatEvent ? -_seekStepLarge : -_seekStep);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowRight) {
        _seekBy(event is KeyRepeatEvent ? _seekStepLarge : _seekStep);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowUp) {
        _adjustVolume(0.1);
        return KeyEventResult.handled;
      }
      if (key == LogicalKeyboardKey.arrowDown) {
        _adjustVolume(-0.1);
        return KeyEventResult.handled;
      }
      _revealControls();
      return KeyEventResult.handled;
    }

    return KeyEventResult.ignored;
  }

  void _onBack() {
    if (_showControls) {
      _hideControls();
      return;
    }
    _closePlayer();
  }

  void _revealControls() {
    if (!mounted) return;
    setState(() => _showControls = true);
    _focusPlayButton();
    _scheduleControlsHide();
  }

  void _toggleControls() {
    if (_showControls) {
      _hideControls();
    } else {
      _revealControls();
    }
  }

  void _scheduleControlsHide() {
    _controlsTimer?.cancel();
    if (!_isPlaying) return;
    _controlsTimer = Timer(_controlsHideDelay, _hideControls);
  }

  void _hideControls() {
    _controlsTimer?.cancel();
    if (!mounted) return;
    setState(() => _showControls = false);
    _rootFocusNode.requestFocus();
  }

  void _focusPlayButton() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _showControls) _playPauseFocusNode.requestFocus();
    });
  }

  void _showOsd(String message) {
    _osdTimer?.cancel();
    setState(() => _osdHint = message);
    _osdTimer = Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() => _osdHint = null);
    });
  }

  void _play() {
    if (!_isInitialized) return;
    _controller!.play();
    _scheduleControlsHide();
  }

  void _pause() {
    if (!_isInitialized) return;
    _controller!.pause();
    _controlsTimer?.cancel();
    if (!_showControls) _revealControls();
    setState(() {});
  }

  void _togglePlayPause() {
    if (!_isInitialized) return;
    if (_controller!.value.isPlaying) {
      _pause();
    } else {
      _play();
    }
    setState(() {});
  }

  Future<void> _seekBy(Duration offset) async {
    if (!_canSeek) {
      if (_treatAsLive) _showOsd('Emisión en vivo');
      return;
    }
    final duration = _duration;
    var target = _position + offset;
    if (target < Duration.zero) target = Duration.zero;
    if (target > duration) target = duration;
    await _controller!.seekTo(target);
    _showOsd(
      '${offset.isNegative ? '−' : '+'}${offset.abs().inSeconds}s  ${_formatDuration(target)}',
    );
    _scheduleControlsHide();
    if (mounted) setState(() {});
  }

  Future<void> _seekToFraction(double fraction) async {
    if (!_canSeek) return;
    final ms = (_duration.inMilliseconds * fraction.clamp(0.0, 1.0)).round();
    await _controller!.seekTo(Duration(milliseconds: ms));
    _scheduleControlsHide();
    if (mounted) setState(() {});
  }

  Future<void> _restart() async {
    if (!_isInitialized) return;
    if (_treatAsLive) {
      await _retryPlayer();
      return;
    }
    await _controller!.seekTo(Duration.zero);
    await _controller!.play();
    _showOsd('Desde el inicio');
    _scheduleControlsHide();
  }

  void _cycleFit() {
    const values = _VideoFitMode.values;
    _fitMode = values[(_fitMode.index + 1) % values.length];
    _showOsd(_fitModeLabel(_fitMode));
    _scheduleControlsHide();
    setState(() {});
  }

  void _adjustVolume(double delta) {
    _volume = (_volume + delta).clamp(0.0, 1.0);
    _controller?.setVolume(_volume);
    _showOsd('Volumen ${(_volume * 100).round()}%');
    setState(() {});
  }

  Future<void> _changeQueueItem(int offset) async {
    if (widget.queue.isEmpty || !_isInitialized) return;
    final nextIndex = _queueIndex + offset;
    if (nextIndex < 0 || nextIndex >= widget.queue.length) return;

    await _saveProgress();
    await _controller?.pause();
    _controller?.removeListener(_onControllerUpdate);
    await _controller?.dispose();
    _controller = null;
    if (!mounted) return;
    setState(() {
      _queueIndex = nextIndex;
      _currentChannelName = widget.queue[nextIndex].title;
      _currentEpisodeId = widget.queue[nextIndex].episodeId;
      _isInitialized = false;
      _hasError = false;
      _errorMessage = '';
      _showControls = true;
      _resumeHint = null;
    });
    _focusPlayButton();
    widget.onQueueIndexChanged?.call(nextIndex);
    await _initPlayer();
  }

  Future<void> _closePlayer() async {
    if (_isClosing || !mounted) return;
    _isClosing = true;
    _connectionAttempt++;
    await _saveProgress();
    final controller = _controller;
    _controller = null;
    controller?.removeListener(_onControllerUpdate);
    await controller?.dispose();
    if (!mounted) return;
    // ponytail: PopScope only allows pop with controls hidden; hide then pop.
    setState(() => _showControls = false);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.of(context).pop();
    });
  }

  Future<void> _retryPlayer() async {
    if (_isClosing) return;
    _connectionAttempt++;
    final controller = _controller;
    _controller = null;
    controller?.removeListener(_onControllerUpdate);
    await controller?.dispose();
    if (!mounted) return;
    setState(() {
      _isInitialized = false;
      _hasError = false;
      _errorMessage = '';
      _showControls = true;
    });
    await _initPlayer();
  }

  @override
  void dispose() {
    _isClosing = true;
    _connectionAttempt++;
    _saveProgress();
    _controlsTimer?.cancel();
    _osdTimer?.cancel();
    _saveTimer?.cancel();
    HardwareKeyboard.instance.removeHandler(_onHardwareKey);
    _rootFocusNode.dispose();
    _backFocusNode.dispose();
    _rewindFocusNode.dispose();
    _previousFocusNode.dispose();
    _playPauseFocusNode.dispose();
    _nextFocusNode.dispose();
    _forwardFocusNode.dispose();
    _progressFocusNode.dispose();
    _fitFocusNode.dispose();
    _restartFocusNode.dispose();
    _retryFocusNode.dispose();
    // ponytail: pause before release to quiet MediaCodec dead-thread warns.
    _controller?.removeListener(_onControllerUpdate);
    _controller?.pause();
    _controller?.dispose();
    WakelockPlus.disable();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      // ponytail: back hides controls first, exits only when hidden.
      canPop: !_showControls,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) _onBack();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Focus(
          focusNode: _rootFocusNode,
          autofocus: !_showControls,
          onKeyEvent: _onRootKey,
          child: GestureDetector(
            onTap: _toggleControls,
            onDoubleTap: _togglePlayPause,
            onHorizontalDragEnd: (details) {
              final vx = details.primaryVelocity ?? 0;
              if (vx > 300) {
                _seekBy(-_seekStep);
              } else if (vx < -300) {
                _seekBy(_seekStep);
              }
            },
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (_isInitialized) _buildVideo(),
                if (_isBuffering || !_isInitialized && !_hasError)
                  const Center(
                    child: CircularProgressIndicator(color: AppColors.accent),
                  ),
                if (!_isInitialized && !_hasError)
                  Center(
                    child: Padding(
                      padding: EdgeInsets.only(top: 88),
                      child: Text(
                        'Conectando...',
                        style: TextStyle(color: AppColors.bodyText, fontSize: 18),
                      ),
                    ),
                  ),
                if (_hasError)
                  _ErrorView(
                    message: _errorMessage,
                    url: _lastAttemptedUrl,
                    retryFocusNode: _retryFocusNode,
                    onBack: _closePlayer,
                    onRetry: _retryPlayer,
                  ),
                if (!_hasError && (_osdHint != null || _resumeHint != null))
                  _OsdBanner(text: _osdHint ?? _resumeHint!),
                if (_showControls && !_hasError) _buildControls(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildVideo() {
    final controller = _controller!;
    final size = controller.value.size;
    final aspect = controller.value.aspectRatio == 0
        ? 16 / 9
        : controller.value.aspectRatio;
    final video = size.isEmpty
        ? VideoPlayer(controller)
        : SizedBox(
            width: size.width,
            height: size.height,
            child: VideoPlayer(controller),
          );

    return ClipRect(
      child: switch (_fitMode) {
        _VideoFitMode.contain => Center(
          child: AspectRatio(
            aspectRatio: aspect,
            child: VideoPlayer(controller),
          ),
        ),
        _VideoFitMode.cover => SizedBox.expand(
          child: FittedBox(fit: BoxFit.cover, child: video),
        ),
        _VideoFitMode.stretch => SizedBox.expand(child: VideoPlayer(controller)),
        _VideoFitMode.zoom => SizedBox.expand(
          child: Transform.scale(
            scale: 1.18,
            child: FittedBox(fit: BoxFit.cover, child: video),
          ),
        ),
      },
    );
  }

  Widget _buildControls() {
    final progress = _canSeek && _duration.inMilliseconds > 0
        ? (_position.inMilliseconds / _duration.inMilliseconds).clamp(0.0, 1.0)
        : 0.0;

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color(0xCC000000),
            Color(0x22000000),
            Color(0x22000000),
            Color(0xE6000000),
          ],
          stops: [0, 0.22, 0.62, 1],
        ),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 36, vertical: 24),
      child: Column(
        children: [
          Row(
            children: [
              _TvIconButton(
                focusNode: _backFocusNode,
                icon: Icons.arrow_back_rounded,
                tooltip: 'Volver',
                onPressed: _closePlayer,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  _currentChannelName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (widget.queue.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(right: 12),
                  child: Text(
                    '${_queueIndex + 1} / ${widget.queue.length}',
                    style: TextStyle(color: AppColors.bodyText, fontSize: 16),
                  ),
                ),
              if (_treatAsLive)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.accent,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Text(
                    'EN VIVO',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.4,
                    ),
                  ),
                ),
            ],
          ),
          const Spacer(),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_canSeek)
                _TvIconButton(
                  focusNode: _rewindFocusNode,
                  icon: Icons.replay_10_rounded,
                  tooltip: 'Retroceder 10 segundos',
                  onPressed: () => _seekBy(-_seekStep),
                ),
              if (_hasPrevious)
                _TvIconButton(
                  focusNode: _previousFocusNode,
                  icon: Icons.skip_previous_rounded,
                  size: 40,
                  tooltip: 'Anterior',
                  onPressed: () => _changeQueueItem(-1),
                ),
              _TvIconButton(
                focusNode: _playPauseFocusNode,
                autofocus: true,
                primary: true,
                icon: _isPlaying
                    ? Icons.pause_rounded
                    : Icons.play_arrow_rounded,
                size: 48,
                tooltip: _isPlaying ? 'Pausa' : 'Reproducir',
                onPressed: _togglePlayPause,
              ),
              if (_hasNext)
                _TvIconButton(
                  focusNode: _nextFocusNode,
                  icon: Icons.skip_next_rounded,
                  size: 40,
                  tooltip: 'Siguiente',
                  onPressed: () => _changeQueueItem(1),
                ),
              if (_canSeek)
                _TvIconButton(
                  focusNode: _forwardFocusNode,
                  icon: Icons.forward_10_rounded,
                  tooltip: 'Avanzar 10 segundos',
                  onPressed: () => _seekBy(_seekStep),
                ),
            ],
          ),
          const Spacer(),
          _TvSeekBar(
            focusNode: _progressFocusNode,
            enabled: _canSeek,
            progress: progress,
            positionLabel: _treatAsLive ? 'En vivo' : _formatDuration(_position),
            durationLabel: _treatAsLive
                ? ''
                : _formatDuration(_duration),
            onSeekStep: _seekBy,
            onSeekFraction: _seekToFraction,
            step: _seekStepBar,
            stepLarge: _seekStepBarLarge,
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _TvChipButton(
                focusNode: _fitFocusNode,
                label: _fitModeLabel(_fitMode),
                icon: Icons.fit_screen_rounded,
                onPressed: _cycleFit,
              ),
              _TvChipButton(
                focusNode: _restartFocusNode,
                label: _treatAsLive ? 'Recargar' : 'Reiniciar',
                icon: Icons.replay_rounded,
                onPressed: _restart,
              ),
              const Spacer(),
              Text(
                'OK play/pausa  ·  barra ← → 1 min  ·  ↑↓ volumen  ·  Atrás oculta',
                style: TextStyle(color: AppColors.subtleText, fontSize: 13),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TvIconButton extends StatefulWidget {
  final FocusNode? focusNode;
  final bool autofocus;
  final bool primary;
  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final double size;

  const _TvIconButton({
    this.focusNode,
    this.autofocus = false,
    this.primary = false,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.size = 30,
  });

  @override
  State<_TvIconButton> createState() => _TvIconButtonState();
}

class _TvIconButtonState extends State<_TvIconButton> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onPressed != null;
    final primary = widget.primary;
    final diameter = primary ? 84.0 : 56.0;
    return Focus(
      focusNode: widget.focusNode,
      autofocus: widget.autofocus,
      onFocusChange: (focused) => setState(() => _focused = focused),
      onKeyEvent: (node, event) {
        if (event is! KeyDownEvent || !enabled) return KeyEventResult.ignored;
        if (event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.space) {
          widget.onPressed?.call();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Tooltip(
        message: widget.tooltip,
        child: GestureDetector(
          onTap: widget.onPressed,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 140),
            curve: Curves.easeOut,
            width: diameter,
            height: diameter,
            margin: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: primary
                  ? (_focused ? AppColors.accent : Colors.white)
                  : _focused
                  ? AppColors.accent.withValues(alpha: 0.22)
                  : Colors.white.withValues(alpha: 0.1),
              border: Border.all(
                color: _focused
                    ? (primary ? Colors.white : AppColors.accent)
                    : Colors.white.withValues(alpha: 0.18),
                width: _focused ? 3 : 1,
              ),
              boxShadow: [
                if (primary)
                  BoxShadow(
                    color: (_focused ? AppColors.accent : Colors.black).withValues(
                      alpha: 0.45,
                    ),
                    blurRadius: _focused ? 28 : 16,
                    offset: const Offset(0, 8),
                  ),
                if (_focused && !primary)
                  BoxShadow(
                    color: AppColors.accent.withValues(alpha: 0.35),
                    blurRadius: 18,
                  ),
              ],
            ),
            child: Center(
              child: Icon(
                widget.icon,
                size: widget.size,
                color: !enabled
                    ? AppColors.faintText
                    : primary
                    ? (_focused ? Colors.white : AppColors.playerInk)
                    : Colors.white,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _TvChipButton extends StatefulWidget {
  final FocusNode? focusNode;
  final String label;
  final IconData icon;
  final VoidCallback onPressed;

  const _TvChipButton({
    this.focusNode,
    required this.label,
    required this.icon,
    required this.onPressed,
  });

  @override
  State<_TvChipButton> createState() => _TvChipButtonState();
}

class _TvChipButtonState extends State<_TvChipButton> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.focusNode,
      onFocusChange: (focused) => setState(() => _focused = focused),
      onKeyEvent: (node, event) {
        if (event is! KeyDownEvent) return KeyEventResult.ignored;
        if (event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.space) {
          widget.onPressed();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: GestureDetector(
        onTap: widget.onPressed,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          margin: const EdgeInsets.only(right: 10),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: _focused
                ? AppColors.accent.withValues(alpha: 0.28)
                : Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: _focused ? AppColors.accent : Colors.white24,
              width: _focused ? 2 : 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(widget.icon, size: 20, color: Colors.white),
              const SizedBox(width: 8),
              Text(
                widget.label,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TvSeekBar extends StatefulWidget {
  final FocusNode focusNode;
  final bool enabled;
  final double progress;
  final String positionLabel;
  final String durationLabel;
  final Future<void> Function(Duration offset) onSeekStep;
  final Future<void> Function(double fraction) onSeekFraction;
  final Duration step;
  final Duration stepLarge;

  const _TvSeekBar({
    required this.focusNode,
    required this.enabled,
    required this.progress,
    required this.positionLabel,
    required this.durationLabel,
    required this.onSeekStep,
    required this.onSeekFraction,
    this.step = _seekStepBar,
    this.stepLarge = _seekStepBarLarge,
  });

  @override
  State<_TvSeekBar> createState() => _TvSeekBarState();
}

class _TvSeekBarState extends State<_TvSeekBar> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: widget.focusNode,
      onFocusChange: (focused) => setState(() => _focused = focused),
      onKeyEvent: (node, event) {
        if (!widget.enabled) return KeyEventResult.ignored;
        if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
          return KeyEventResult.ignored;
        }
        if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
          widget.onSeekStep(event is KeyRepeatEvent ? -widget.stepLarge : -widget.step);
          return KeyEventResult.handled;
        }
        if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
          widget.onSeekStep(event is KeyRepeatEvent ? widget.stepLarge : widget.step);
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      child: Row(
        children: [
          SizedBox(
            width: 72,
            child: Text(
              widget.positionLabel,
              style: TextStyle(
                color: _focused ? Colors.white : AppColors.bodyText,
                fontSize: 14,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                return GestureDetector(
                  onTapDown: widget.enabled
                      ? (details) => widget.onSeekFraction(
                          details.localPosition.dx / constraints.maxWidth,
                        )
                      : null,
                  onHorizontalDragUpdate: widget.enabled
                      ? (details) => widget.onSeekFraction(
                          details.localPosition.dx / constraints.maxWidth,
                        )
                      : null,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 120),
                    height: _focused ? 18 : 10,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(99),
                      border: Border.all(
                        color: _focused ? AppColors.accent : Colors.transparent,
                        width: 2,
                      ),
                    ),
                    alignment: Alignment.center,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(99),
                      child: LinearProgressIndicator(
                        value: widget.enabled ? widget.progress : 1,
                        minHeight: _focused ? 10 : 6,
                        backgroundColor: Colors.white24,
                        color: widget.enabled ? AppColors.accent : Colors.redAccent,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          SizedBox(
            width: 72,
            child: Text(
              widget.durationLabel,
              textAlign: TextAlign.right,
              style: TextStyle(
                color: AppColors.bodyText,
                fontSize: 14,
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _OsdBanner extends StatelessWidget {
  final String text;

  const _OsdBanner({required this.text});

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 96,
      left: 0,
      right: 0,
      child: Center(
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.7),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
            child: Text(
              text,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final String url;
  final FocusNode retryFocusNode;
  final VoidCallback onBack;
  final VoidCallback onRetry;

  const _ErrorView({
    required this.message,
    required this.url,
    required this.retryFocusNode,
    required this.onBack,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(48),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.signal_wifi_off, color: Colors.redAccent, size: 72),
            const SizedBox(height: 16),
            const Text(
              'Contenido no disponible',
              style: TextStyle(
                color: Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.subtleText, fontSize: 14),
            ),
            const SizedBox(height: 16),
            Text(
              sanitizeStreamUrl(url),
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.faintText, fontSize: 12),
            ),
            const SizedBox(height: 28),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _TvChipButton(
                  focusNode: retryFocusNode,
                  label: 'Reintentar',
                  icon: Icons.refresh_rounded,
                  onPressed: onRetry,
                ),
                _TvChipButton(
                  label: 'Volver',
                  icon: Icons.arrow_back_rounded,
                  onPressed: onBack,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ponytail: decoder failures (HEVC/MKV on cheap boxes) get an actionable
// message instead of the raw ExoPlayer dump.
String _friendlyPlaybackError(Object e) {
  final s = sanitizeStreamUrl(e.toString());
  if (s.contains('MediaCodecVideoRenderer') ||
      s.contains('MediaCodecAudioRenderer') ||
      s.contains('MediaCodecRenderer')) {
    return 'Tu dispositivo no puede decodificar este vídeo (códec no soportado, p. ej. HEVC en MKV).\nPrueba con otro capítulo o en otro dispositivo.\n$s';
  }
  return 'No se pudo reproducir el contenido.\n$s';
}

String _fitModeLabel(_VideoFitMode mode) {
  switch (mode) {
    case _VideoFitMode.contain:
      return 'Ajustar';
    case _VideoFitMode.cover:
      return 'Rellenar';
    case _VideoFitMode.stretch:
      return 'Estirar';
    case _VideoFitMode.zoom:
      return 'Zoom';
  }
}

String _formatDuration(Duration value) {
  final hours = value.inHours;
  final minutes = value.inMinutes.remainder(60).toString().padLeft(2, '0');
  final seconds = value.inSeconds.remainder(60).toString().padLeft(2, '0');
  if (hours > 0) return '$hours:$minutes:$seconds';
  return '$minutes:$seconds';
}
