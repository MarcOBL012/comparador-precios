# Comparador de Precios — contexto del proyecto

Monorepo de un trabajo universitario (software adaptativo al contexto). Dos mitades
desplegadas por separado que hablan por un único endpoint:

```
android/   → app Android (Kotlin + Compose + CameraX), cliente
api/, lib/ → backend TypeScript en Vercel (serverless), decide y hace el trabajo pesado
```

Backend en producción: `https://comparador-precios-marco-obl.vercel.app/`. Repo con 53+
commits, todos actualmente bajo un solo autor (Marco) — si el trabajo es en equipo, las
mejoras nuevas conviene hacerlas con autoría propia (rama + commits), no solo código.

## El pipeline (lo que pide el taller)

```
CONTEXTO → PROCESAMIENTO → DECISIÓN → ADAPTACIÓN
```

1. **Contexto**: foto (CameraX), categoría elegida antes de la foto, `confianza` de Gemini, y
   estado del dispositivo — red y batería vía `context/DeviceContextMonitor.kt`.
2. **Procesamiento**: compresión en hilo de fondo (1600 px en wifi, 1024 px con datos móviles o
   batería baja); backend identifica con Gemini (`categoria` fija + `tipo`) y consulta tiendas.
3. **Decisión**:
   - `ContextPolicy.decide` (Android): sin red → no escanea; datos móviles/batería → foto liviana.
   - `confianza < 0.5` → no se consultan tiendas ([lib/scanHandler.ts](lib/scanHandler.ts)).
   - `routeFor` en [lib/searchStores.ts](lib/searchStores.ts): la categoría decide qué tiendas se
     consultan (supermercados vs. tiendas por departamento + Google Shopping vía Serper). Si la
     categoría elegida y la de Gemini difieren, se consultan las tiendas de ambas. Con batería
     baja se omite Google Shopping.
   - [lib/matching.ts](lib/matching.ts): descarta otra marca, otro código de modelo, otro `tipo`
     (leche UHT ≠ evaporada) y packs no pedidos; categoría de la tienda desempata.
4. **Adaptación**: `ScanUiState` cambia solo; avisos de contexto en vivo en la cámara (demo:
   modo avión y el botón se bloquea solo).

## Mapa "si quiero cambiar X, toco Y"

| Quiero cambiar… | Archivo |
|---|---|
| Umbral de confianza | [lib/scanHandler.ts](lib/scanHandler.ts) **y** [android/app/src/main/java/pe/com/comparadorprecios/data/ScanModels.kt](android/app/src/main/java/pe/com/comparadorprecios/data/ScanModels.kt) — **están duplicados, ver deuda técnica #2** |
| Qué tan exigente es la búsqueda | `MATCH_THRESHOLD`, `PACK_PENALTY`, `SYNONYM_GROUPS` en [lib/matching.ts](lib/matching.ts) |
| Modelo de IA / prompt / categorías | [lib/identifyProduct.ts](lib/identifyProduct.ts), `CATEGORIAS` en [lib/productIdentification.ts](lib/productIdentification.ts) **y** `PRODUCT_CATEGORIES` en `android/.../ui/ProductCategories.kt` (mismos ids) |
| Qué tiendas por categoría | `ROUTES` en [lib/searchStores.ts](lib/searchStores.ts) |
| Agregar una tienda VTEX | archivo nuevo en `lib/stores/` + entrada en `DIRECT_STORES` y `ROUTES` de `lib/searchStores.ts` |
| Reglas de red/batería | `ContextPolicy` en `android/.../context/DeviceContext.kt` |
| Colores de la app | `android/.../ui/Theme.kt` (verde = ahorro, naranja = acción, rojo = subió) |
| Probar búsqueda real sin IA | `npx tsx scripts/verify-stores.ts` (con `SERPER_API_KEY` en el entorno para Google Shopping) |
| Pantallas / textos | [android/app/src/main/java/pe/com/comparadorprecios/ui/Screens.kt](android/app/src/main/java/pe/com/comparadorprecios/ui/Screens.kt) |
| Mensajes de error | [android/app/src/main/java/pe/com/comparadorprecios/data/ScanRepository.kt](android/app/src/main/java/pe/com/comparadorprecios/data/ScanRepository.kt) |

## Deuda técnica conocida, priorizada

0. **Backend nuevo sin desplegar** (rama `feat/busqueda-contexto-lista`) — producción no tiene
   `/api/prices` (verificado 2026-09-14: 404), así que en el APK "Revisar precio" y "Actualizar
   precios" fallan, y no hay categorías fijas, `tipo`, tiendas nuevas ni Serper hasta desplegar.
   Hay que configurar `SERPER_API_KEY` en Vercel. El repo no está vinculado a Vercel localmente.
1. ~~Compresión de imagen en el hilo principal~~ — resuelto (executor de fondo en `ScanScreen.kt`).
2. **Umbral 0.5 duplicado** (backend y app) — si divergen, `Success` puede renderizar con
   `tiendas: []` sin explicación. Fix correcto: el backend devuelve `lowConfidence: true`
   en el JSON y la app solo lee ese campo, no repite el número.
3. **Sin estado para "encontrado pero ninguna tienda lo tiene"** — hoy se ven dos tarjetas
   de "sin precios" sueltas en vez de un mensaje unificado.
4. **Backend desplegado desactualizado** — verificado el 2026-09-14: un `POST /api/scan`
   sin token devolvió 400 (falta imagen), no 401. El código actual en `master` sí exige
   sesión (`api/scan.ts`); falta redesplegar (`vercel deploy --prod`) para que el endpoint
   público deje de aceptar requests sin autenticar.
5. ~~Ninguna variable de contexto del dispositivo~~ — resuelto para red y batería. Pendiente:
   ubicación (sucursal cercana) y alertas de bajada de precio en segundo plano (WorkManager +
   notificaciones + token de Clerk fuera de la app).
6. **Sin firma de release** — `buildTypes.release` no tiene `signingConfig`; por eso solo
   se puede generar/instalar el APK debug (~30 MB, sin minificar).

## Gotchas del entorno de desarrollo (Windows + OneDrive)

- El repo vive dentro de OneDrive. Gradle puede fallar con `Unable to delete directory`
  porque OneDrive bloquea archivos en `build/` mientras sincroniza. Si pasa, pausar
  sync de OneDrive o clonar fuera de OneDrive.
- Android Studio se abre apuntando a la carpeta **`android/`**, nunca a la raíz del repo.
  Abrir la raíz genera un `.idea/` y `.iml` sueltos tipo "Java genérico" sin Gradle (ya
  pasó una vez, limpiado; el `.gitignore` tiene una red de seguridad para no volver a
  commitearlo, pero el fix real es abrir la carpeta correcta).
- `android/local.properties` (no versionado) debe tener `scanBaseUrl` y
  `clerkPublishableKey` reales — sin esto el login no pasa. Ver
  `android/local.properties.example`.
- `android/gradle.properties` fija `org.gradle.java.home` a un JDK 17 con ruta absoluta de una
  máquina concreta (hoy `C:/Users/curay/.jdks/ms-17.0.20.1`). En otra PC hay que cambiarla o
  fallará el sync con "Java home supplied is invalid". No commitear ese cambio sin acordarlo.
- Runtime de Android Studio sin JCEF rompía la GUI del plugin de Claude Code — ya resuelto
  instalando el runtime JCEF vía "Choose Boot Java Runtime for the IDE".

## Comandos

```
npm test                              # backend (Vitest), 21 archivos de test
npm run build                         # type-check backend (tsc --noEmit)
./gradlew :app:testDebugUnitTest      # tests Android (JVM, sin SDK de emulador)
./gradlew :app:assembleDebug          # genera el APK debug
```

## Convenciones

- Mensajes de commit en español, estilo `tipo: descripción corta` (`feat:`, `fix:`,
  `chore:`, `docs:`), como el historial existente.
- No commitear secretos: `local.properties`, `.env.local` ya están en `.gitignore`.
