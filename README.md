# Comparador de Precios

App de Android que escanea la foto de un producto, lo identifica con IA y compara su precio en distintas tiendas peruanas (Plaza Vea, Wong), además de guardar un historial local de escaneos. Requiere iniciar sesión (Clerk) antes de poder escanear.

## Arquitectura

```
android/            App Android (Kotlin + Jetpack Compose)
  ├─ escaneo con CameraX → foto en base64
  ├─ onboarding (primera vez) → login/registro con Clerk → pantalla de escaneo
  ├─ historial local de escaneos (Room)
  └─ llama al backend (Retrofit) con el token de sesión de Clerk

api/, lib/           Backend (funciones serverless de Vercel, TypeScript)
  ├─ POST /api/scan  → requiere sesión válida (Clerk) → identifica el producto (IA,
  │                    Google Gemini) → busca el precio en paralelo en Plaza Vea y
  │                    Wong (scraping de sus APIs públicas de catálogo) → responde
  │                    con la identificación y los precios comparados
  └─ GET  /api/health
```

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `android/` | Proyecto Android completo (Gradle) |
| `api/` | Endpoints serverless (`scan.ts`, `health.ts`) |
| `lib/` | Lógica del backend: identificación con IA, scrapers, matching de productos, autenticación |
| `test/` | Tests del backend (Vitest) |
| `scripts/` | Scripts auxiliares (verificación manual de los scrapers) |
| `docs/` | Documentación de diseño y planes de implementación del proyecto |

## Requisitos previos

- **Backend:** Node.js 20+ y npm.
- **Android:** Android Studio (o el SDK de Android + JDK 17 vía línea de comandos), `compileSdk 36` / `minSdk 26`.
- Una cuenta de **Vercel** (para desplegar el backend) y una integración de **Clerk** (autenticación) — ambas tienen plan gratuito.
- Una **API key de Google AI Studio** (Gemini) para la identificación de productos.

## 1. Backend

```bash
npm install
cp .env.example .env.local   # completar con tus claves (ver abajo)
npm test                     # corre la suite de tests (Vitest)
npm run build                # type-check (tsc --noEmit)
```

### Variables de entorno

Copia `.env.example` a `.env.local` y completa:

| Variable | De dónde se obtiene |
|---|---|
| `GOOGLE_GENERATIVE_AI_API_KEY` | [Google AI Studio](https://aistudio.google.com/apikey) |
| `CLERK_SECRET_KEY` | Dashboard de Clerk → API Keys → Secret key |

### Ejecutar/desplegar el backend

El backend son funciones serverless de Vercel (no un servidor tradicional). Para probarlo end-to-end necesitas desplegarlo:

```bash
npm install -g vercel
vercel link                          # vincula el repo a un proyecto de Vercel
vercel integration add clerk         # provisiona Clerk y descarga CLERK_SECRET_KEY automáticamente
vercel env pull .env.local           # trae las variables reales del proyecto
vercel deploy --prod                 # despliega
```

La URL resultante (`https://tu-proyecto.vercel.app`) es la que usará la app de Android (`scanBaseUrl`, ver abajo).

## 2. App Android

```bash
cp android/local.properties.example android/local.properties
```

Edita `android/local.properties`:

```properties
scanBaseUrl=https://tu-backend.vercel.app/
clerkPublishableKey=pk_test_tu_clave_publicable   # Dashboard de Clerk → API Keys → Publishable key
```

Luego, desde `android/`:

```bash
./gradlew assembleDebug          # compila el APK debug
./gradlew testDebugUnitTest      # corre los tests unitarios
```

O ábrelo directamente en Android Studio (`android/` como raíz del proyecto) y ejecuta la app en un emulador o dispositivo físico con el botón Run.

## Dependencias principales

**Backend:** TypeScript, Vercel Functions, [Vercel AI SDK](https://sdk.vercel.ai/) (`ai`, `@ai-sdk/google`) para la identificación con Gemini, `@clerk/backend` para verificar la sesión, Zod, Vitest para tests.

**Android:** Kotlin + Jetpack Compose, CameraX (captura de fotos), Retrofit + OkHttp (red), kotlinx.serialization, Room (historial local), Navigation Compose, DataStore (preferencia de onboarding), Clerk Android SDK (autenticación).

## Notas de seguridad

Ningún secreto (API keys, tokens) está commiteado en este repositorio — `.env.local`, `android/local.properties` y `.vercel/` están en `.gitignore`. Los archivos `.env.example` y `android/local.properties.example` documentan qué variables se necesitan, sin valores reales.

## Documentación adicional

`android/README.md` documenta el contrato exacto con el backend, la configuración local y el historial de escaneos.
