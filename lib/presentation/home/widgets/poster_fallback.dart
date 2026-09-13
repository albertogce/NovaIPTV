part of '../home_screen.dart';

class _PosterFallback extends StatelessWidget {
  final IconData icon;

  const _PosterFallback({required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.posterFallback,
      alignment: Alignment.center,
      child: Icon(icon, color: AppColors.faintText, size: 42),
    );
  }
}
