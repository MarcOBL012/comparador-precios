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

## 3. API REST de seguimiento de precios (Taller 3)

API propia, versionada en `/api/v1`, con base de datos Postgres (Supabase en producción, Docker en local). El usuario sigue un producto, el sistema revisa sus precios en las tiendas y genera alertas cuando baja.

### Arquitectura

```
Android (Retrofit)  →  api/v1/[...route].ts  →  Router  →  Controller  →  Service  →  Repository  →  Postgres
                                                  │            │            │             │
                                                  │            │            │             └─ solo SQL (lib/*/repository.ts)
                                                  │            │            └─ reglas de negocio (lib/*/service.ts, lib/prices/alertRules.ts)
                                                  │            └─ validación con Zod + códigos HTTP (lib/*/controller.ts)
                                                  └─ autenticación: Clerk (usuario) o CRON_SECRET (tareas internas)
```

| Carpeta | Contenido |
|---|---|
| `api/v1/` | Punto de entrada serverless (un solo catch-all) |
| `lib/http/` | Router, respuestas y mapeo de errores de dominio a códigos HTTP |
| `lib/watches/` | Seguimientos: CRUD |
| `lib/prices/` | Revisión de precios, historial, alertas y reglas |
| `lib/db/` | Conexión a Postgres |
| `db/migrations/` | Esquema SQL normalizado (001–005) |
| `docs/api/` | `openapi.yaml` y colección de Postman |
| `test/watches`, `test/prices`, `test/integration` | Tests unitarios, de API y de integración contra Postgres real |

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/watches` | Seguir un producto (con precio objetivo opcional) |
| GET | `/watches` | Listar seguimientos (filtro `estado`, paginación) |
| GET | `/watches/{id}` | Detalle |
| PATCH | `/watches/{id}` | Cambiar objetivo o estado (pausar/reactivar) |
| DELETE | `/watches/{id}` | Dejar de seguir (borra sus alertas) |
| POST | `/watches/{id}/check` | Revisar precios ahora (operación de negocio) |
| GET | `/watches/{id}/history` | Historial de precios y estadísticas |
| GET | `/alerts` | Alertas del usuario |
| PATCH | `/alerts/{id}` | Marcar alerta como leída o no leída |
| GET/POST | `/internal/check-due` | Revisión automática (cron diario, requiere `CRON_SECRET`) |

Códigos: 200, 201, 204, 400 (validación), 401, 404, 405, 409 (ya seguido, límite de seguimientos, estado inválido), 422 (regla de negocio), 429 (con `Retry-After`), 502 (tiendas caídas), 503, 500. Detalle de cada ruta, cuerpos JSON y ejemplos en [`docs/api/openapi.yaml`](docs/api/openapi.yaml).

### Reglas de negocio

1. Un seguimiento por usuario y producto (409 `ya_seguido`).
2. Máximo 20 seguimientos activos por usuario (409 `limite_seguimientos`).
3. Una revisión cada 15 minutos por seguimiento (429).
4. Si el mejor precio es menor o igual al objetivo: alerta `objetivo_alcanzado` y el seguimiento pasa a `alcanzada`.
5. Alerta `minimo_historico` si supera el mínimo de al menos 3 observaciones previas.
6. Se descartan precios atípicos (menos del 40 % de la mediana).
7. Transiciones de estado válidas: `activa ↔ pausada`; `alcanzada → activa | pausada` (reactivar exige un objetivo menor).

### Base de datos

Esquema normalizado (3FN): `categories`, `brands`, `product_types`, `products`, `stores`, `store_listings`, `price_observations`, `app_users`, `devices`, `watches`, `watch_statuses` y `watch_status_transitions`, `alerts`, `alert_types`, `push_deliveries`, `identification_signatures`, `signature_confirmations` y la tabla de eventos `domain_events` (outbox). Las claves foráneas, los `unique` y los triggers garantizan la integridad aunque se escriba por fuera de la API.

### Correr todo en local

Requiere Docker.

```bash
npm install
npm run db:up                                                                    # Postgres en localhost:54329
DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm run db:migrate
TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm test   # unitarios + integración
```

Para levantar la API en local (`vercel dev`, puerto 3000) define en `.env.local`:

```
DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador
AUTH_DEV_MODE=1        # permite el encabezado X-Dev-User en lugar de un token de Clerk
CRON_SECRET=cualquier-texto
```

`AUTH_DEV_MODE` se ignora cuando corre en Vercel.

### Probar con Postman

Importa `docs/api/postman_collection.json` y ejecuta la colección completa con el Runner (cubre éxitos y fallos de los 10 endpoints). Variables: `baseUrl` (`http://localhost:3000/api/v1` o la URL de Vercel), `devUser` y `cronSecret`.

### En producción (Vercel + Supabase)

Variables de entorno en Vercel (Production y Preview): `DATABASE_URL` (cadena del *Transaction pooler* de Supabase) y `CRON_SECRET`, además de las del backend original. `vercel.json` programa la revisión diaria de seguimientos (`/api/v1/internal/check-due`).

## Dependencias principales

**Backend:** TypeScript, Vercel Functions, [Vercel AI SDK](https://sdk.vercel.ai/) (`ai`, `@ai-sdk/google`) para la identificación con Gemini, `@clerk/backend` para verificar la sesión, Zod, Vitest para tests.

**Android:** Kotlin + Jetpack Compose, CameraX (captura de fotos), Retrofit + OkHttp (red), kotlinx.serialization, Room (historial local), Navigation Compose, DataStore (preferencia de onboarding), Clerk Android SDK (autenticación).

## Notas de seguridad

Ningún secreto (API keys, tokens) está commiteado en este repositorio — `.env.local`, `android/local.properties` y `.vercel/` están en `.gitignore`. Los archivos `.env.example` y `android/local.properties.example` documentan qué variables se necesitan, sin valores reales.

## Documentación adicional

`android/README.md` documenta el contrato exacto con el backend, la configuración local y el historial de escaneos.
