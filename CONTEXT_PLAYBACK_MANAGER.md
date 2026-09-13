# Reproducción

La reproducción se implementa con `video_player` y la pantalla `PlayerScreen`.

## Flujo

1. La pantalla de inicio construye una URL Xtream Codes para el canal, película o episodio.
2. `PlayerScreen` crea e inicializa el controlador de vídeo para esa URL.
3. Al abandonar el reproductor, el controlador se pausa y libera sus recursos.

## Componentes relevantes

- `lib/presentation/player/player_screen.dart`: interfaz de reproducción.

## Consideraciones

- Las URLs de stream pueden incorporar credenciales. Antes de mostrarlas o registrarlas, se sustituyen usuario y contraseña por `***`.
- La app debe mantener una única sesión de reproducción activa por usuario.
- La recuperación ante cortes de red depende del reproductor y del servidor IPTV; no hay una política de reintentos configurable implementada actualmente.
