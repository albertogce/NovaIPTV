# Nova IPTV

Cliente IPTV en Flutter orientado a Android TV y Google TV. Permite conectar una cuenta compatible con la API de Xtream Codes para navegar y reproducir canales en directo, películas y series con mando a distancia.

## Funcionalidades

- Inicio de sesión con servidor, usuario y contraseña introducidos por cada usuario.
- Catálogo de canales en directo, películas y series.
- Categorías laterales y navegación optimizada para D-pad.
- Búsqueda global y resultados organizados por tipo de contenido.
- EPG breve en tarjetas de canales cuando el servidor lo proporciona.
- Favoritos de canales, historial y ordenación de favoritos.
- Reproducción de streams mediante `video_player`.
- Ajustes para credenciales, actualización automática y visibilidad/orden de categorías.

## Requisitos

- Flutter SDK compatible con Dart `^3.11.0`.
- Un dispositivo o emulador Android TV/Google TV para la experiencia de mando completa.
- Una cuenta y un servidor IPTV compatibles con Xtream Codes. El proyecto no incluye listas, servidores ni credenciales.

## Ejecutar el proyecto

```powershell
flutter pub get
flutter run
```

## Compilar el APK

```powershell
flutter build apk --release `
  --target-platform android-arm,android-arm64 `
  --obfuscate `
  --split-debug-info=build/symbols/android
```

El APK resultante se genera en `build/app/outputs/flutter-apk/app-release.apk`.

## Privacidad y seguridad

- No hay credenciales, servidores ni listas IPTV preconfigurados en el código.
- Las credenciales introducidas se guardan localmente en el dispositivo para permitir el inicio de sesión automático; no se envían a ningún servicio distinto del servidor IPTV configurado por el usuario.

## Estructura

```text
lib/
  core/          Tema, almacenamiento, utilidades y reproducción
  data/          Cliente Xtream Codes y modelos de datos
  domain/        Entidades e interfaces de repositorio
  presentation/  Pantallas de acceso, inicio, ajustes y reproductor
```

## Tecnologías

- Flutter y Material
- Riverpod (estado de autenticación)
- Dio (cliente HTTP Xtream Codes)
- Shared Preferences (credenciales, favoritos, historial, ajustes)
- video_player (reproducción)
- wakelock_plus (pantalla encendida durante la reproducción)

## Licencia

Apache License 2.0 — ver el archivo `LICENSE`.
