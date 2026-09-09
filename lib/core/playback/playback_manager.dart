class PlaybackManager {
  static final PlaybackManager _instance = PlaybackManager._internal();

  PlaybackManager._internal();

  factory PlaybackManager() => _instance;

  bool _isPlaying = false;

  bool get isPlaying => _isPlaying;

  Future<void> startPlayback(String streamUrl) async {
    if (_isPlaying) {
      return; // Ya hay una reproducción activa
    }

    _isPlaying = true;
    // Simulación de inicio de reproducción
    await Future.delayed(const Duration(seconds: 2));
  }

  Future<void> stopPlayback() async {
    _isPlaying = false;
    // Simulación de detención
    await Future.delayed(const Duration(seconds: 1));
  }

  Future<void> restartPlayback(String streamUrl) async {
    await stopPlayback();
    await startPlayback(streamUrl);
  }
}
