# Arquitectura actual

Este documento describe la implementación actual de Nova IPTV; no es una hoja de ruta.

## Capas

- `lib/presentation`: interfaz Flutter, navegación y controles de mando.
- `lib/data`: `XtreamApiClient` y modelos obtenidos de la API Xtream Codes.
- `lib/core`: tema, almacenamiento local, normalización de URL y reproducción.

## Flujo principal

1. El usuario introduce servidor, usuario y contraseña en `LoginScreen`.
2. `AuthController` valida la cuenta contra la API Xtream Codes antes de guardarla.
3. `HomeCatalog` obtiene, filtra y guarda en caché canales, categorías, películas y series.
4. Las vistas de Directos, Películas y Series filtran el catálogo por categoría y búsqueda.
5. El reproductor recibe la URL de stream construida por `XtreamApiClient`.

## Estado y persistencia

- Riverpod gestiona el estado de autenticación.
- El almacén seguro del sistema guarda servidor, usuario y contraseña.
- `SharedPreferences` mantiene favoritos, historial y ajustes.
- La caché de catálogo se guarda en el directorio de soporte de la app.

## Navegación de TV

- Los contenidos se presentan en cuadrículas con foco visible.
- Directos, Películas y Series comparten selector lateral de categorías.
- Desde la primera columna, `←` abre las categorías; `→` devuelve el foco a la cuadrícula.
- `OK` reproduce o abre el detalle del contenido seleccionado.
