# Arquitectura actual

Este documento describe la implementación actual de Nova IPTV; no es una hoja de ruta.

## Capas

- `lib/presentation`: interfaz Flutter, navegación y controles de mando.
- `lib/data`: `XtreamApiClient` y modelos obtenidos de la API Xtream Codes.
- `lib/domain`: entidades e interfaces que separan la UI de los datos.
- `lib/core`: tema, almacenamiento local, normalización de URL y reproducción.

## Flujo principal

1. El usuario introduce servidor, usuario y contraseña en `LoginScreen`.
2. `AuthController` valida la cuenta mediante `XtreamApiClient`.
3. `HomeScreen` obtiene y guarda en caché canales, categorías, películas y series.
4. Las vistas de Directos, Películas y Series filtran el catálogo por categoría y búsqueda.
5. El reproductor recibe la URL de stream construida con el formato Xtream Codes.

## Estado y persistencia

- Riverpod gestiona el estado de autenticación.
- `SharedPreferences` mantiene credenciales, favoritos, historial y ajustes.
- La caché de catálogo se guarda temporalmente en el directorio temporal del sistema.

> Las credenciales se almacenan localmente. Una futura migración a almacenamiento seguro es recomendable antes de distribuir una versión con requisitos de seguridad elevados.

## Navegación de TV

- Los contenidos se presentan en cuadrículas con foco visible.
- Directos, Películas y Series comparten selector lateral de categorías.
- Desde la primera columna, `←` abre las categorías; `→` devuelve el foco a la cuadrícula.
- `OK` reproduce o abre el detalle del contenido seleccionado.
