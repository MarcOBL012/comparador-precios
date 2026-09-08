# App Android — Comparador de precios (Plan 3)

App nativa (Kotlin + Compose + CameraX + Retrofit) contra `POST /api/scan` del backend (Planes 1+2).

## Contrato que respeta (ver `../HANDOFF-PLAN3.md`)

- `POST {SCAN_BASE_URL}/api/scan` con `{ "image": "data:image/jpeg;base64,..." }`.
- Base64 con **`NO_WRAP`** (nunca `DEFAULT`).
- Máx 4.000.000 chars → la app comprime el JPEG antes de enviar.
- `confianza < 0.5` → `tiendas: []` → pantalla de baja confianza (reintentar + búsqueda web manual).
- `estado`: `encontrado` | `no_encontrado` | `error`. Se muestra el **nombre completo del producto** junto al precio (mitigación del bug de multipacks).
- La lista es un `LazyColumn` genérico: **cero tiendas hardcodeadas** (Plan 2b compatible).

## Configurar

1. Abrir **esta carpeta `android/`** en Android Studio (no la raíz del repo). Dejar que sincronice Gradle (descarga AGP 8.5.2 + SDK 34).
2. Copiar `local.properties.example` → `local.properties` y poner tu URL:
   `scanBaseUrl=https://<tu-app>.vercel.app/` (o exportar `SCAN_BASE_URL`).
   Sin esto apunta a `https://tu-backend.vercel.app/` (placeholder).
3. `Run > app` en un dispositivo físico (la cámara del emulador no sirve para fotos reales).

## Tests

- `./gradlew :app:testDebugUnitTest` — `DataUriTest`, `PriceSortingTest`, `ScanModelsTest`, `ScanViewModelTest` (JVM, sin SDK de IA ni red).
- Requieren SDK 34 instalado. `:app:testDebugUnitTest` y `:app:assembleDebug` corren en esta máquina; la prueba de cámara necesita un dispositivo físico.

## Historial

- Room local (`comparador-precios.db`, **solo en el dispositivo**, sin backend): cada `Success` del escaneo se guarda en segundo plano con `runCatching` — si Room falla, el resultado ya está en pantalla y no pasa nada.
- Solo se guarda la **miniatura** (lado largo 256px, máx 200 KB JPEG, calidades 85→40), **nunca la foto completa**; si la miniatura falla, el escaneo continúa sin guardarse.
- **Baja confianza no se guarda**: solo el estado `Success` dispara el guardado.
- Lista con borrar + confirmación y Deshacer (Snackbar); el detalle muestra el registro idéntico al resultado.
- **Desinstalar la app borra el historial** (la base de datos vive en el almacenamiento interno de la app).

## Estructura

- `data/` — `ScanModels.kt` (contrato), `ScanApi.kt` (Retrofit, timeouts 60s por `maxDuration = 60` del backend), `ScanRepository.kt` (mapeo 400/502/500 → `ScanError`).
- `util/` — `DataUri.kt` (puro JVM: formato + límite), `ImageEncoding.kt` (compresión + `NO_WRAP`), `PriceSorting.kt` (encontrados por precio asc, luego resto).
- `ui/` — `ScanUiState.kt`, `ScanViewModel.kt`, `ScanScreen.kt` (CameraX puntual), `Screens.kt` (carga, resultados, baja confianza, error).
- `MainActivity.kt` — máquina de estados sin NavHost (MVP: 1 flujo lineal).
