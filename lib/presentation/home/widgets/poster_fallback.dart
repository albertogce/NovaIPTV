part of '../home_screen.dart';

class _PosterFallback extends StatelessWidget {
  final IconData icon;

  const _PosterFallback({required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: const Color(0xFF20323A),
      alignment: Alignment.center,
      child: Icon(icon, color: Colors.white38, size: 42),
    );
  }
}
