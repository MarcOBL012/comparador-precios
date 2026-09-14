# App Android — contexto

@../CLAUDE.md

Este es el proyecto que Android Studio abre directamente (carpeta `android/`). El archivo
importado arriba (`../CLAUDE.md`) tiene el contexto completo: arquitectura de ambas mitades
(app + backend), pipeline del taller, deuda técnica priorizada y gotchas del entorno.

Específico de este lado:

- Estructura: `data/` (contrato + red + Retrofit), `util/` (compresión de imagen, orden de
  precios, miniaturas), `ui/` (estados + pantallas Compose), `history/` (Room, solo local).
- Antes de compilar: `local.properties` en esta carpeta necesita `scanBaseUrl` y
  `clerkPublishableKey` reales (ver `local.properties.example`). Sin eso, el login no pasa.
- Un físico, no el emulador — la cámara del emulador no sirve para fotografiar productos.
- `./gradlew :app:testDebugUnitTest` antes de dar por bueno un cambio en `util/` o `data/`.
