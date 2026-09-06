# App Android — Comparador de precios (Plan 3 de 3) Implementation Plan

> **For agentic workers:** implementa tarea por tarea con TDD donde aplique (capa datos + ViewModel con tests JVM). La UI Compose se verifica con `assembleDebug` en Android Studio / CI con SDK.

**Goal:** App Android nativa (Kotlin + Compose) que toma una foto, la envía a `POST /api/scan` (backend de Planes 1+2, ya en `master`) y muestra la comparación Plaza Vea + Wong ordenada por precio.

**Architecture:** `android/` (proyecto Gradle separado en este mismo repo). Capas: `data` (modelos + Retrofit) → `repository` → `ui` (ViewModel con `StateFlow<ScanUiState>` + pantallas Compose). CameraX para captura puntual. Sin DB ni auth (MVP per spec).

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00, CameraX 1.3.4, Retrofit 2.11.0 / OkHttp 4.12.0, Coroutines 1.8.1, Lifecycle ViewModel 2.7.0, Navigation Compose 2.7.7, JUnit 4 + coroutines-test para unit tests.

## Global Constraints (de HANDOFF-PLAN3.md — no renegociar)

- Contrato `POST /api/scan`: request `{ "image": "data:image/<jpeg|png|gif|webp>;base64,<...>" }`. Regex backend: `^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$` — **sin saltos de línea**.
- **Android DEBE usar `Base64.NO_WRAP`** (`DEFAULT` inserta `\n` cada 76 chars → 400).
- Máx **4_000_000 chars** en el string. Comprimir antes de enviar; sin resize server-side.
- `confianza < 0.5` → `tiendas: []`. UI debe pedir reintentar o ingreso manual (UX del spec — se construye aquí).
- `estado` = `encontrado` | `no_encontrado` | `error`. En `encontrado`: `producto`, `precio` (soles, number), `url`. En `error`: `mensaje` (mostrable, no hacer pattern-match).
- **Mitigación obligatoria del bug de matching:** mostrar `producto` (nombre completo matcheado) junto al precio, no solo el precio — el usuario debe poder detectar un pack mismatch.
- **No hardcodear "2 tiendas"**: `LazyColumn` sobre el array `tiendas` (un futuro Plan 2b puede agregar Falabella/Ripley sin update de la app).
- Errores HTTP: 400 validación, 405 método, 502 fallo IA, 500 inesperado. Body `{ "error": "<msg>" }`.
- Base URL configurable (BuildConfig `SCAN_BASE_URL`, override vía `local.properties` `scanBaseUrl` o env `SCAN_BASE_URL`). Default: `https://tu-backend.vercel.app/`.

---

### Task 1: Scaffolding Gradle + Manifest + MainActivity

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`, `android/gradlew`, `android/gradlew.bat`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt`
- Create: `android/local.properties.example`, `android/README.md`

- [ ] Step 1: crear archivos Gradle con versiones fijadas (AGP 8.5.2, Kotlin 1.9.24, compileSdk 34, minSdk 26)
- [ ] Step 2: `AndroidManifest.xml` con `CAMERA` + `INTERNET`, `MainActivity` como launcher
- [ ] Step 3: verificar en Android Studio: `File > Open > android/` → Sync OK (requiere SDK 34; sin SDK local no se puede compilar aquí)
- [ ] Step 4: Commit `chore(android): scaffold gradle project`

### Task 2: Capa de datos (modelos + Retrofit + repositorio)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/data/ScanModels.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/data/ScanApi.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/data/ScanRepository.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/data/ScanModelsTest.kt`

**Interfaces:**
- Consumes: contrato `POST /api/scan` del HANDOFF.
- Produces: `@Serializable`/Moshi `ScanRequest`, `Identification`, `StoreResult`, `ScanResponse`; `ScanApi.scan()`; `ScanRepository.scan(imageDataUri)` con mapeo de `HttpException` 400/502/500 → `ScanError`.

- [ ] Step 1: escribir `ScanModelsTest` (parseo 200 con tiendas, `tiendas: []` baja confianza, `error` con mensaje, `encontrado` sin precio → falla)
- [ ] Step 2: implementar modelos + Retrofit (`RetrofitProvider` con `SCAN_BASE_URL`, OkHttp timeout 60s — el backend tiene `maxDuration = 60`)
- [ ] Step 3: `./gradlew :app:testDebugUnitTest --tests "*ScanModelsTest*"` → PASS
- [ ] Step 4: Commit `feat(android): add scan data layer and repository`

### Task 3: Utilidades (imagen + ordenamiento)

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/util/ImageEncoding.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/util/PriceSorting.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/util/ImageEncodingTest.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/util/PriceSortingTest.kt`

- [ ] Step 1: `ImageEncodingTest`: `toDataUri(bytes, "image/jpeg")` usa `NO_WRAP` (sin `\n`), respeta el regex del backend, `exceedsLimit()` a los 4M chars
- [ ] Step 2: `PriceSortingTest`: encontrados primero por precio asc, luego no_encontrado, luego error; array vacío se preserva; no se asume tamaño 2
- [ ] Step 3: implementar `ImageEncoding` (compresión JPEG iterativa hasta < 4M chars) + `PriceSorting.sortForDisplay()`
- [ ] Step 4: tests PASS
- [ ] Step 5: Commit `feat(android): add image encoding and price sorting utils`

### Task 4: ViewModel + estados UI

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanUiState.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanViewModel.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/ui/ScanViewModelTest.kt`

Estados: `Idle` | `Loading` | `LowConfidence(identification)` (tiendas vacía) | `Success(identification, tiendasOrdenadas)` | `Error(message)` (mapea 400/502/500 a mensajes ES accionables).

- [ ] Step 1: escribir `ScanViewModelTest` (éxito ordena, confianza < 0.5 → LowConfidence sin llamar de más, 400 → Error validación, 502 → Error IA, excepción repo → Error genérico)
- [ ] Step 2: implementar ViewModel con `StateFlow`, inyección de repo por constructor (testeable sin Hilt)
- [ ] Step 3: tests PASS
- [ ] Step 4: Commit `feat(android): add scan ViewModel with UI states`

### Task 5: UI Compose (escaneo / carga / resultados / baja confianza)

**Files:**
- Create: `.../ui/ScanScreen.kt` (CameraX preview + captura)
- Create: `.../ui/ResultScreen.kt` (`LazyColumn` genérica + fila con `producto` + precio S/ + link)
- Create: `.../ui/LoadingScreen.kt`, `.../ui/LowConfidenceScreen.kt` (reintentar + ingreso manual → deep-link búsqueda web Plaza Vea/Wong), `.../ui/ErrorScreen.kt`
- Modify: `MainActivity.kt` (NavHost: scan → loading → result/lowConfidence/error)

- [ ] Step 1: `ScanScreen` con `ProcessCameraProvider` + `ImageCapture`, permiso `CAMERA` con Accompanist-permissions
- [ ] Step 2: `ResultScreen`: `LazyColumn` sobre `tiendas` (cero hardcodeo), fila encontrado muestra `tienda`, `producto` completo, `precio`, botón abrir `url` (Custom Tab / Intent); no_encontrado → "no disponible"; error → mensaje
- [ ] Step 3: `LowConfidenceScreen`: "Tomar otra foto" + campo manual con botones "Buscar en Plaza Vea / Wong" (abre `https://www.plazavea.com.pe/search?text=` en browser — sin backend, MVP honesto)
- [ ] Step 4: `assembleDebug` OK + prueba manual contra backend desplegado
- [ ] Step 5: Commit `feat(android): add compose scan and results UI`

### Task 6: Verificación manual end-to-end (requiere backend desplegado)

Sin test automatizado (necesita Vercel + IA real + cámara). Si el backend aún no está desplegado, hacer primero Task 7 del Plan 1 (`vercel link`, `vercel env pull`, `vercel dev` / deploy).

- [ ] Step 1: desplegar backend (`vercel --prod`), anotar URL en `local.properties` (`scanBaseUrl=https://<tu-app>.vercel.app/`)
- [ ] Step 2: instalar `app-debug.apk` en dispositivo físico, tomar foto real → 200 con `identification` + `tiendas` con precios reales
- [ ] Step 3: probar foto borrosa → `LowConfidenceScreen`; imagen gigante → 400; modo avión parcial → `error` por tienda sin tumbar la otra
- [ ] Step 4: documentar URL final en `android/README.md`
