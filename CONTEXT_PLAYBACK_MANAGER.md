# Reproducción

La reproducción se implementa con `video_player` y la pantalla `PlayerScreen`.

## Flujo

1. La pantalla de inicio construye una URL Xtream Codes para el canal, película o episodio.
2. `PlayerScreen` crea e inicializa el controlador de vídeo para esa URL.
3. Al abandonar el reproductor, el controlador se pausa y libera sus recursos.

## Componentes relevantes

- `lib/presentation/player/player_screen.dart`: interfaz de reproducción.
- `lib/core/playback/playback_manager.dart`: punto de coordinación de reproducción.
- `lib/core/playback/playback_provider.dart`: proveedor de acceso al gestor.

## Consideraciones

- Las URLs de stream pueden incorporar credenciales. No deben escribirse en logs ni incluirse en informes de errores.
- La app debe mantener una única sesión de reproducción activa por usuario.
- La recuperación ante cortes de red depende del reproductor y del servidor IPTV; no hay una política de reintentos configurable implementada actualmente.
