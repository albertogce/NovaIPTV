import 'dart:async';

typedef VoidCallback = void Function();

class Debouncer {
  final int milliseconds;
  VoidCallback? _action;
  Timer? _timer;

  Debouncer({required this.milliseconds});

  run(VoidCallback action) {
    _action = action;
    _timer?.cancel();
    _timer = Timer(Duration(milliseconds: milliseconds), _action!);
  }

  void cancel() {
    _timer?.cancel();
    _timer = null;
  }
}