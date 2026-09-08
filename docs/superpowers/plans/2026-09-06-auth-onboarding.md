# Onboarding y autenticación con Clerk Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Antes de escanear, la app Android debe mostrar una pantalla de onboarding (solo la primera vez) y exigir registro/login real vía Clerk; el backend debe rechazar `POST /api/scan` sin una sesión de Clerk válida.

**Architecture:** Un nuevo nivel de estado (`AppFlowState`: Onboarding → Auth → Scanning) envuelve el flujo de escaneo existente en `MainActivity.kt`, sin introducir Navigation Compose. Clerk maneja el registro/login (SDK nativo de Android, componente `AuthView` prehecho) y la sesión; el backend verifica esa sesión con `@clerk/backend` antes de procesar un escaneo.

**Tech Stack:** Clerk Android SDK (`com.clerk:clerk-android-api`/`clerk-android-ui` 1.1.5, verificado en vivo contra Maven Central), `@clerk/backend` 3.17.1 (verificado en vivo contra npm), `androidx.datastore:datastore-preferences` 1.2.1.

## Global Constraints

- Onboarding se muestra **solo la primera vez** (bandera persistida en DataStore).
- Registro/login con Clerk es **obligatorio** para escanear — no hay modo sin cuenta.
- No se introduce Navigation Compose — se extiende el patrón de máquina de estados que ya usa `ScanUiState` en `MainActivity.kt`.
- El flujo de escaneo existente (`ScanScreen` → `LoadingScreen` → `ResultScreen`/`LowConfidenceScreen`/`ErrorScreen`) no cambia de comportamiento, salvo el nuevo caso de sesión inválida.
- **Nota sobre el SDK de Clerk para Android:** es un SDK nuevo (GA desde 2025) y su documentación pública no siempre coincide entre páginas para APIs de bajo nivel (ej. cómo obtener el token de sesión activo). El código de este plan usa la mejor evidencia encontrada verificando contra la documentación oficial de Clerk en vivo, pero en las Tasks 4 y 5 se marca explícitamente qué verificar contra el SDK real instalado (autocompletado de Android Studio) antes de dar el código por bueno — igual que se hizo con el AI SDK de Vercel en el Plan 1 y la API de VTEX en el Plan 2.

---

### Task 1: Verificar sesión de Clerk en `POST /api/scan`

**Files:**
- Create: `lib/auth.ts`
- Test: `test/auth.test.ts`
- Modify: `api/scan.ts`
- Modify: `test/scan.test.ts`
- Modify: `test/scanIntegration.test.ts`
- Modify: `package.json` (agregar `@clerk/backend`)

**Interfaces:**
- Consumes: nada nuevo del código existente (usa `VercelRequest` de `@vercel/node`, ya en uso).
- Produces: `isAuthenticated(req: VercelRequest): Promise<boolean>` — usada por `api/scan.ts`. No se usa en ninguna otra task de este plan.

Este endpoint hoy valida la imagen y llama a `handleScan` sin verificar quién hace la petición. Esta tarea agrega una verificación de sesión de Clerk **antes** de todo lo demás: sin sesión válida, `401` inmediato.

- [ ] **Step 1: Instalar la dependencia**

```bash
npm install @clerk/backend@^3.17.1
```

- [ ] **Step 2: Escribir el test que falla**

Crea `test/auth.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest } from '@vercel/node';

const { verifyTokenMock } = vi.hoisted(() => ({
  verifyTokenMock: vi.fn(),
}));

vi.mock('@clerk/backend', () => ({
  verifyToken: verifyTokenMock,
}));

import { isAuthenticated } from '../lib/auth';

function createMockReq(authorization?: string): VercelRequest {
  return { headers: authorization ? { authorization } : {} } as VercelRequest;
}

describe('isAuthenticated', () => {
  beforeEach(() => {
    verifyTokenMock.mockReset();
  });

  it('devuelve false si no hay header Authorization', async () => {
    const result = await isAuthenticated(createMockReq());

    expect(result).toBe(false);
    expect(verifyTokenMock).not.toHaveBeenCalled();
  });

  it('devuelve false si el header no tiene el prefijo Bearer', async () => {
    const result = await isAuthenticated(createMockReq('token-sin-prefijo'));

    expect(result).toBe(false);
  });

  it('devuelve true si el token es válido', async () => {
    verifyTokenMock.mockResolvedValue({ data: { sub: 'user_123' }, errors: null });

    const result = await isAuthenticated(createMockReq('Bearer token-valido'));

    expect(result).toBe(true);
    expect(verifyTokenMock).toHaveBeenCalledWith('token-valido', { secretKey: process.env.CLERK_SECRET_KEY });
  });

  it('devuelve false si verifyToken resuelve con errores', async () => {
    verifyTokenMock.mockResolvedValue({ data: null, errors: [{ message: 'inválido' }] });

    const result = await isAuthenticated(createMockReq('Bearer token-invalido'));

    expect(result).toBe(false);
  });

  it('devuelve false si verifyToken lanza una excepción', async () => {
    verifyTokenMock.mockRejectedValue(new Error('token malformado'));

    const result = await isAuthenticated(createMockReq('Bearer token-malformado'));

    expect(result).toBe(false);
  });
});
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/auth.test.ts`
Expected: FAIL — no se puede resolver `../lib/auth`.

- [ ] **Step 4: Implementar `lib/auth.ts`**

```ts
import type { VercelRequest } from '@vercel/node';
import { verifyToken } from '@clerk/backend';

export async function isAuthenticated(req: VercelRequest): Promise<boolean> {
  const header = req.headers['authorization'];
  const value = Array.isArray(header) ? header[0] : header;
  const token = value?.replace(/^Bearer\s+/i, '');
  if (!token) {
    return false;
  }
  try {
    const result = await verifyToken(token, { secretKey: process.env.CLERK_SECRET_KEY });
    return !result.errors;
  } catch {
    return false;
  }
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/auth.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 6: Actualizar `api/scan.ts`**

Reemplaza el contenido completo de `api/scan.ts`:

```ts
import type { VercelRequest, VercelResponse } from '@vercel/node';
import { handleScan } from '../lib/scanHandler.js';
import { ValidationError, IdentificationError } from '../lib/errors.js';
import { isAuthenticated } from '../lib/auth.js';

export const maxDuration = 60;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Método no permitido. Usa POST.' });
    return;
  }

  if (!(await isAuthenticated(req))) {
    res.status(401).json({ error: 'No autenticado. Inicia sesión para escanear.' });
    return;
  }

  try {
    const result = await handleScan(req.body);
    res.status(200).json(result);
  } catch (err) {
    console.error('POST /api/scan failed:', err);
    if (err instanceof IdentificationError && err.cause) {
      console.error('IdentificationError cause:', err.cause);
    }
    if (err instanceof ValidationError) {
      res.status(400).json({ error: err.message });
      return;
    }
    if (err instanceof IdentificationError) {
      res.status(502).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: 'Error interno del servidor.' });
  }
}
```

- [ ] **Step 7: Actualizar `test/scan.test.ts` (los tests existentes necesitan mockear la nueva verificación)**

Reemplaza el contenido completo de `test/scan.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';

const { handleScanMock } = vi.hoisted(() => ({
  handleScanMock: vi.fn(),
}));
const { isAuthenticatedMock } = vi.hoisted(() => ({
  isAuthenticatedMock: vi.fn(),
}));

vi.mock('../lib/scanHandler', () => ({
  handleScan: handleScanMock,
}));
vi.mock('../lib/auth', () => ({
  isAuthenticated: isAuthenticatedMock,
}));

import handler from '../api/scan';
import { ValidationError, IdentificationError } from '../lib/errors';

function createMockRes() {
  const res = {
    statusCode: 0,
    body: undefined as unknown,
    status(code: number) {
      res.statusCode = code;
      return res;
    },
    json(payload: unknown) {
      res.body = payload;
      return res;
    },
  };
  return res as unknown as VercelResponse & { statusCode: number; body: unknown };
}

function createMockReq(method: string, body: unknown): VercelRequest {
  return { method, body } as VercelRequest;
}

describe('POST /api/scan', () => {
  beforeEach(() => {
    handleScanMock.mockReset();
    isAuthenticatedMock.mockReset();
    isAuthenticatedMock.mockResolvedValue(true);
  });

  it('responde 405 si el método no es POST', async () => {
    const req = createMockReq('GET', {});
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(405);
    expect(res.body).toEqual({ error: 'Método no permitido. Usa POST.' });
  });

  it('responde 401 si no está autenticado', async () => {
    isAuthenticatedMock.mockResolvedValue(false);
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(401);
    expect(res.body).toEqual({ error: 'No autenticado. Inicia sesión para escanear.' });
    expect(handleScanMock).not.toHaveBeenCalled();
  });

  it('responde 200 con la identificación en éxito', async () => {
    const identification = { marca: 'Gloria', nombre: 'Leche', presentacion: '400g', categoria: 'abarrotes', confianza: 0.9 };
    handleScanMock.mockResolvedValue({ identification });
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual({ identification });
  });

  it('responde 400 si handleScan lanza ValidationError', async () => {
    handleScanMock.mockRejectedValue(new ValidationError('campo inválido'));
    const req = createMockReq('POST', {});
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ error: 'campo inválido' });
  });

  it('responde 502 si handleScan lanza IdentificationError', async () => {
    handleScanMock.mockRejectedValue(new IdentificationError('no se pudo identificar'));
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(502);
    expect(res.body).toEqual({ error: 'no se pudo identificar' });
  });

  it('responde 500 en cualquier otro error', async () => {
    handleScanMock.mockRejectedValue(new Error('boom'));
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(500);
    expect(res.body).toEqual({ error: 'Error interno del servidor.' });
  });
});
```

- [ ] **Step 8: Actualizar `test/scanIntegration.test.ts` (solo agregar el mock, sin tocar los tests existentes)**

En `test/scanIntegration.test.ts`, reemplaza el bloque de imports/mocks del inicio del archivo (todo antes de `function createMockRes()`) por:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { generateTextMock, objectMock } = vi.hoisted(() => ({
  generateTextMock: vi.fn(),
  objectMock: vi.fn((config: unknown) => ({ __schemaConfig: config })),
}));
const { isAuthenticatedMock } = vi.hoisted(() => ({
  isAuthenticatedMock: vi.fn(),
}));

vi.mock('ai', () => ({
  generateText: generateTextMock,
  Output: { object: objectMock },
}));
vi.mock('../lib/auth', () => ({
  isAuthenticated: isAuthenticatedMock,
}));

import handler from '../api/scan';
import type { VercelRequest, VercelResponse } from '@vercel/node';
```

Y agrega `isAuthenticatedMock.mockReset(); isAuthenticatedMock.mockResolvedValue(true);` dentro del `beforeEach` existente, junto a las líneas de `generateTextMock`/`fetchMock`. El resto del archivo (las 2 pruebas existentes) no cambia — solo necesitan que `isAuthenticated` esté mockeada como `true` por defecto para no romperse.

- [ ] **Step 9: Ejecutar los tests y verificar que pasan**

Run: `npx vitest run test/auth.test.ts test/scan.test.ts test/scanIntegration.test.ts`
Expected: PASS (5 + 6 + 2 = 13 tests)

- [ ] **Step 10: Ejecutar toda la suite**

Run: `npm test`
Expected: PASS — sin regresiones en el resto de tests del backend.

- [ ] **Step 11: Commit**

```bash
git add lib/auth.ts test/auth.test.ts api/scan.ts test/scan.test.ts test/scanIntegration.test.ts package.json package-lock.json
git commit -m "feat: require a valid Clerk session on POST /api/scan"
```

---

### Task 2: Pantalla de onboarding

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/OnboardingScreen.kt`

**Interfaces:**
- Consumes: nada.
- Produces: `@Composable fun OnboardingScreen(onContinue: () -> Unit)` — usada por la Task 4.

Sin test dedicado: en este código base los composables de pantalla completa (`ScanScreen.kt`, `Screens.kt`) tampoco tienen tests unitarios propios (se verifican corriendo la app) — este archivo sigue esa misma convención.

- [ ] **Step 1: Crear `ui/OnboardingScreen.kt`**

```kotlin
package pe.com.comparadorprecios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingFeature(val titulo: String, val descripcion: String)

private val ONBOARDING_FEATURES = listOf(
    OnboardingFeature(
        "Escanea un producto",
        "Toma una foto del empaque y la identificamos automáticamente.",
    ),
    OnboardingFeature(
        "Compara precios",
        "Buscamos el mismo producto en Plaza Vea y Wong en tiempo real.",
    ),
    OnboardingFeature(
        "Elige dónde comprar",
        "Ves el precio y el nombre exacto encontrado en cada tienda, para confirmar que es el mismo producto.",
    ),
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "Comparador de precios",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Encuentra el mejor precio para lo que compras, en segundos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        ONBOARDING_FEATURES.forEach { feature ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(feature.titulo, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(feature.descripcion, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Empezar")
        }
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/ui/OnboardingScreen.kt
git commit -m "feat: add onboarding screen"
```

---

### Task 3: Bandera de onboarding y estado de flujo de la app

**Files:**
- Create: `android/app/src/main/java/pe/com/comparadorprecios/data/OnboardingPreferences.kt`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/ui/AppFlowState.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/ui/AppFlowStateTest.kt`
- Modify: `android/app/build.gradle.kts` (agregar DataStore)

**Interfaces:**
- Consumes: nada.
- Produces: `OnboardingPreferences(context: Context)` con `val hasSeenOnboarding: Flow<Boolean>` y `suspend fun markOnboardingSeen()`; `AppFlowState` (sealed interface: `Onboarding`, `Auth`, `Scanning`) con `AppFlowState.from(hasSeenOnboarding: Boolean, isSignedIn: Boolean): AppFlowState` — ambos usados por la Task 4.

`OnboardingPreferences` no tiene test dedicado: es un wrapper delgado sobre DataStore (sin lógica propia más allá de leer/escribir una bandera), y este proyecto no tiene Robolectric configurado para testear DataStore con un `Context` real en tests unitarios. Toda la lógica de decisión (qué pantalla corresponde) vive en `AppFlowState.from`, que sí es pura y se testea abajo.

- [ ] **Step 1: Agregar la dependencia de DataStore**

En `android/app/build.gradle.kts`, dentro del bloque `dependencies { ... }`, agrega (junto a las demás líneas `implementation(...)`, por ejemplo después del bloque de Retrofit/OkHttp):

```kotlin
    // Persistencia local — bandera de onboarding.
    implementation("androidx.datastore:datastore-preferences:1.2.1")
```

- [ ] **Step 2: Escribir el test que falla**

Crea `android/app/src/test/java/pe/com/comparadorprecios/ui/AppFlowStateTest.kt`:

```kotlin
package pe.com.comparadorprecios.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFlowStateTest {

    @Test
    fun `onboarding no vista manda a Onboarding sin importar la sesion`() {
        assertEquals(AppFlowState.Onboarding, AppFlowState.from(hasSeenOnboarding = false, isSignedIn = false))
        assertEquals(AppFlowState.Onboarding, AppFlowState.from(hasSeenOnboarding = false, isSignedIn = true))
    }

    @Test
    fun `onboarding vista y sin sesion manda a Auth`() {
        assertEquals(AppFlowState.Auth, AppFlowState.from(hasSeenOnboarding = true, isSignedIn = false))
    }

    @Test
    fun `onboarding vista y con sesion manda a Scanning`() {
        assertEquals(AppFlowState.Scanning, AppFlowState.from(hasSeenOnboarding = true, isSignedIn = true))
    }
}
```

- [ ] **Step 3: Ejecutar el test y verificar que falla**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "pe.com.comparadorprecios.ui.AppFlowStateTest"`
Expected: FAIL — no se puede resolver `AppFlowState`.

- [ ] **Step 4: Implementar `ui/AppFlowState.kt`**

```kotlin
package pe.com.comparadorprecios.ui

sealed interface AppFlowState {
    data object Onboarding : AppFlowState
    data object Auth : AppFlowState
    data object Scanning : AppFlowState

    companion object {
        fun from(hasSeenOnboarding: Boolean, isSignedIn: Boolean): AppFlowState = when {
            !hasSeenOnboarding -> Onboarding
            !isSignedIn -> Auth
            else -> Scanning
        }
    }
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "pe.com.comparadorprecios.ui.AppFlowStateTest"`
Expected: PASS (3 tests)

- [ ] **Step 6: Implementar `data/OnboardingPreferences.kt`**

```kotlin
package pe.com.comparadorprecios.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

private val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")

class OnboardingPreferences(private val context: Context) {
    val hasSeenOnboarding: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[HAS_SEEN_ONBOARDING] ?: false }

    suspend fun markOnboardingSeen() {
        context.onboardingDataStore.edit { it[HAS_SEEN_ONBOARDING] = true }
    }
}
```

- [ ] **Step 7: Verificar que todo compila**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/java/pe/com/comparadorprecios/data/OnboardingPreferences.kt android/app/src/main/java/pe/com/comparadorprecios/ui/AppFlowState.kt android/app/src/test/java/pe/com/comparadorprecios/ui/AppFlowStateTest.kt
git commit -m "feat: add onboarding preference storage and app flow state"
```

---

### Task 4: Integrar Clerk y la compuerta Onboarding → Auth → Scanning

**Files:**
- Modify: `android/app/build.gradle.kts` (dependencias de Clerk, clave publicable, bump de `lifecycle-*`)
- Modify: `android/local.properties.example`
- Create: `android/app/src/main/java/pe/com/comparadorprecios/App.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt`

**Interfaces:**
- Consumes: `OnboardingScreen` (Task 2), `OnboardingPreferences`, `AppFlowState` (Task 3).
- Produces: nada nuevo para otras tasks — este es el punto de integración final de la compuerta.

**Antes de escribir el código de este task: verifica contra el SDK real.** Después del Step 1 (agregar las dependencias de Clerk y sincronizar Gradle), abre el proyecto en Android Studio y usa autocompletado sobre `Clerk.` (import `com.clerk.api.Clerk`) para confirmar que existen `Clerk.initialize(context, publishableKey = ...)`, `Clerk.userFlow` (debería ser `StateFlow<User?>`), y el composable `AuthView()` (import `com.clerk.ui.auth.AuthView`). Estos tres fueron verificados contra un ejemplo completo y oficial de Clerk al escribir este plan, así que deberían coincidir — pero si algo no compila exactamente así, usa lo que el autocompletado de Android Studio muestre como fuente de verdad por encima de este texto.

- [ ] **Step 1: Agregar dependencias de Clerk y actualizar `lifecycle-*`**

En `android/app/build.gradle.kts`, reemplaza estas dos líneas existentes:

```kotlin
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
```

por:

```kotlin
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
```

(Clerk requiere `2.9.2` o superior; se sube la versión existente en vez de declarar una segunda.)

Y agrega, junto al bloque de DataStore agregado en la Task 3:

```kotlin
    // Auth — Clerk (registro/login + verificación de sesión con el backend).
    implementation("com.clerk:clerk-android-api:1.1.5")
    implementation("com.clerk:clerk-android-ui:1.1.5")
```

- [ ] **Step 2: Exponer la clave publicable de Clerk como `BuildConfig`**

En `android/app/build.gradle.kts`, junto a la lectura existente de `scanBaseUrl` (cerca del inicio del archivo), agrega:

```kotlin
val clerkPublishableKey: String =
    (localProps.getProperty("clerkPublishableKey"))
        ?: System.getenv("CLERK_PUBLISHABLE_KEY")
        ?: "pk_test_replace_me"
```

Y dentro de `defaultConfig { ... }`, junto a la línea existente `buildConfigField("String", "SCAN_BASE_URL", ...)`, agrega:

```kotlin
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")
```

- [ ] **Step 3: Documentar la nueva propiedad local**

Reemplaza el contenido de `android/local.properties.example` por:

```properties
# Copia a local.properties (no se commitea) y ajusta a tu backend desplegado.
scanBaseUrl=https://tu-backend.vercel.app/
# Clave publicable de Clerk (Dashboard de Clerk → API Keys → Publishable key).
clerkPublishableKey=pk_test_reemplaza_esto
```

- [ ] **Step 4: Sincronizar Gradle y verificar la instalación**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (esto descarga las nuevas dependencias; confirma que `com.clerk.api.Clerk` y `com.clerk.ui.auth.AuthView` se pueden importar antes de continuar).

- [ ] **Step 5: Crear `App.kt` (Application) e inicializar Clerk**

```kotlin
package pe.com.comparadorprecios

import android.app.Application
import com.clerk.api.Clerk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(this, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
    }
}
```

- [ ] **Step 6: Registrar `App` en el manifiesto**

En `android/app/src/main/AndroidManifest.xml`, agrega `android:name=".App"` a la etiqueta `<application>` (sin tocar el resto del archivo):

```xml
    <application
        android:name=".App"
        android:allowBackup="false"
        android:label="Comparador de precios"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
```

- [ ] **Step 7: Reescribir `MainActivity.kt` con la compuerta Onboarding → Auth → Scanning**

Reemplaza el contenido completo de `MainActivity.kt`:

```kotlin
package pe.com.comparadorprecios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.OnboardingPreferences
import pe.com.comparadorprecios.data.RetrofitProvider
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.ui.AppFlowState
import pe.com.comparadorprecios.ui.ErrorScreen
import pe.com.comparadorprecios.ui.LoadingScreen
import pe.com.comparadorprecios.ui.LowConfidenceScreen
import pe.com.comparadorprecios.ui.OnboardingScreen
import pe.com.comparadorprecios.ui.ResultScreen
import pe.com.comparadorprecios.ui.ScanScreen
import pe.com.comparadorprecios.ui.ScanUiState
import pe.com.comparadorprecios.ui.ScanViewModel
import pe.com.comparadorprecios.ui.openWebSearch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val onboardingPrefs = remember { OnboardingPreferences(context) }
                    val hasSeenOnboarding by onboardingPrefs.hasSeenOnboarding.collectAsState(initial = false)
                    val user by Clerk.userFlow.collectAsStateWithLifecycle()
                    val scope = rememberCoroutineScope()

                    val flowState = AppFlowState.from(
                        hasSeenOnboarding = hasSeenOnboarding,
                        isSignedIn = user != null,
                    )

                    when (flowState) {
                        AppFlowState.Onboarding -> OnboardingScreen(
                            onContinue = { scope.launch { onboardingPrefs.markOnboardingSeen() } },
                        )
                        AppFlowState.Auth -> AuthView()
                        AppFlowState.Scanning -> {
                            val repository = remember {
                                ScanRepository(
                                    RetrofitProvider.create(
                                        baseUrl = BuildConfig.SCAN_BASE_URL,
                                        debug = BuildConfig.DEBUG,
                                    )
                                )
                            }
                            val vm: ScanViewModel = viewModel { ScanViewModel(repository) }
                            val state by vm.state.collectAsState()
                            var captureError by remember { mutableStateOf<String?>(null) }
                            var manualName by remember { mutableStateOf("") }

                            when (val s = state) {
                                is ScanUiState.Idle -> {
                                    val err = captureError
                                    if (err != null) {
                                        ErrorScreen(message = err, onRetry = { captureError = null }, onBack = { captureError = null })
                                    } else {
                                        ScanScreen(
                                            onImageCaptured = { vm.scan(it) },
                                            onError = { captureError = it },
                                        )
                                    }
                                }
                                is ScanUiState.Loading -> LoadingScreen()
                                is ScanUiState.LowConfidence -> LowConfidenceScreen(
                                    identification = s.identification,
                                    manualName = manualName,
                                    onManualNameChange = { manualName = it },
                                    onRetake = { vm.reset() },
                                    onSearchWeb = { base, q -> openWebSearch(context, base, q) },
                                )
                                is ScanUiState.Success -> ResultScreen(
                                    identification = s.identification,
                                    tiendas = s.tiendas,
                                    onNewScan = { vm.reset() },
                                )
                                is ScanUiState.Error -> ErrorScreen(
                                    message = s.message,
                                    onRetry = { vm.reset() },
                                    onBack = { vm.reset() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 8: Verificar que compila**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Ejecutar la suite de tests de Android para confirmar que no hay regresiones**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos los tests existentes (`ScanModelsTest`, `ScanViewModelTest`, `DataUriTest`, `PriceSortingTest`, `AppFlowStateTest`) pasan.

- [ ] **Step 10: Commit**

```bash
git add android/app/build.gradle.kts android/local.properties.example android/app/src/main/java/pe/com/comparadorprecios/App.kt android/app/src/main/AndroidManifest.xml android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt
git commit -m "feat: gate the app behind onboarding and Clerk auth"
```

---

### Task 5: Adjuntar el token de Clerk a las peticiones y manejar sesión inválida

**Files:**
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/data/ScanApi.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/data/ScanRepository.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanUiState.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/ui/ScanViewModel.kt`
- Modify: `android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt`
- Test: `android/app/src/test/java/pe/com/comparadorprecios/data/ScanRepositoryTest.kt`

**Interfaces:**
- Consumes: `Clerk.session` (SDK de Clerk, Task 4).
- Produces: `ScanError.Unauthorized`, `ScanUiState.Unauthorized` — consumidos únicamente dentro de este mismo task (por `MainActivity.kt`).

**Antes de escribir el interceptor: verifica contra el SDK real.** La forma exacta de obtener el token de la sesión activa no quedó 100% confirmada en la documentación pública de Clerk al escribir este plan — una página mostraba `Clerk.auth.getToken()`, pero el código interno del SDK sugiere que el token se obtiene desde el objeto `Session` (`Clerk.session?.getToken()`). Antes del Step 1, usa autocompletado de Android Studio sobre `Clerk.session?.` para confirmar el nombre real del método (debería ser una `suspend fun` que devuelve `String?`). El código de abajo asume `Clerk.session?.getToken()`; ajústalo si el autocompletado muestra otro nombre.

- [ ] **Step 1: Escribir el test que falla**

Crea `android/app/src/test/java/pe/com/comparadorprecios/data/ScanRepositoryTest.kt`:

```kotlin
package pe.com.comparadorprecios.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ScanRepositoryTest {

    private fun apiThatFailsWith(code: Int): ScanApi = object : ScanApi {
        override suspend fun scan(request: ScanRequest): ScanResponse {
            throw HttpException(
                Response.error<ScanResponse>(
                    code,
                    "{}".toResponseBody("application/json".toMediaType()),
                )
            )
        }
    }

    @Test
    fun `401 produce ScanError Unauthorized`() = runTest {
        val repository = ScanRepository(apiThatFailsWith(401))
        try {
            repository.scan("uri")
            fail("debía lanzar ScanError.Unauthorized")
        } catch (e: ScanError.Unauthorized) {
            assertTrue(true)
        }
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "pe.com.comparadorprecios.data.ScanRepositoryTest"`
Expected: FAIL — `ScanError.Unauthorized` no existe todavía (error de compilación).

- [ ] **Step 3: Agregar `ScanError.Unauthorized` en `ScanRepository.kt`**

Reemplaza el contenido completo de `ScanRepository.kt`:

```kotlin
package pe.com.comparadorprecios.data

import retrofit2.HttpException
import java.io.IOException

/** Errores de dominio mapeados desde HTTP — mensajes accionables en español. */
sealed class ScanError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Validation(message: String) : ScanError(message)
    class IdentificationFailed(message: String) : ScanError(message)
    class Unauthorized(message: String) : ScanError(message)
    class Server(message: String) : ScanError(message)
    class Network(message: String, cause: Throwable? = null) : ScanError(message, cause)
}

class ScanRepository(private val api: ScanApi) {
    suspend fun scan(imageDataUri: String): ScanResponse {
        try {
            return api.scan(ScanRequest(imageDataUri))
        } catch (e: HttpException) {
            val detail = e.response()?.errorBody()?.string()?.take(300)
            throw when (e.code()) {
                400 -> ScanError.Validation(
                    "La imagen no es válida. Toma otra foto (JPG/PNG, menos de ~3 MB). ${detail.orEmpty()}".trim()
                )
                401 -> ScanError.Unauthorized("Tu sesión expiró. Inicia sesión de nuevo.")
                502 -> ScanError.IdentificationFailed(
                    "No se pudo identificar el producto. Reintenta con mejor luz y el empaque visible."
                )
                else -> ScanError.Server("Error del servidor (${e.code()}). Reintenta en unos segundos.")
            }
        } catch (e: IOException) {
            throw ScanError.Network("Sin conexión o el servidor tardó demasiado. Revisa tu internet y reintenta.", e)
        }
    }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "pe.com.comparadorprecios.data.ScanRepositoryTest"`
Expected: PASS (1 test)

- [ ] **Step 5: Agregar el interceptor de autenticación en `ScanApi.kt`**

Reemplaza el contenido completo de `ScanApi.kt`:

```kotlin
package pe.com.comparadorprecios.data

import com.clerk.api.Clerk
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** Único endpoint del backend (Planes 1+2). */
interface ScanApi {
    @POST("api/scan")
    suspend fun scan(@Body request: ScanRequest): ScanResponse
}

object RetrofitProvider {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(baseUrl: String, debug: Boolean = false): ScanApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val auth = Interceptor { chain ->
            val token = runBlocking { Clerk.session?.getToken() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // El backend tiene maxDuration = 60s (IA + 2 scrapers en paralelo).
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScanApi::class.java)
    }
}
```

- [ ] **Step 6: Agregar `ScanUiState.Unauthorized`**

En `ui/ScanUiState.kt`, agrega un nuevo caso al `sealed interface` (junto a los demás `data object`):

```kotlin
    data object Unauthorized : ScanUiState
```

El archivo completo queda:

```kotlin
package pe.com.comparadorprecios.ui

import pe.com.comparadorprecios.data.Identification
import pe.com.comparadorprecios.data.StoreResult

/** Estados de la pantalla de escaneo → resultados (spec + HANDOFF). */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Loading : ScanUiState
    data object Unauthorized : ScanUiState

    /** confianza < 0.5 → el backend devolvió tiendas: []. Reintentar o ingreso manual. */
    data class LowConfidence(val identification: Identification) : ScanUiState

    /** Comparación lista. `tiendas` ya viene ordenada para mostrar (ver PriceSorting). */
    data class Success(
        val identification: Identification,
        val tiendas: List<StoreResult>,
    ) : ScanUiState

    data class Error(val message: String) : ScanUiState
}
```

- [ ] **Step 7: Capturar `ScanError.Unauthorized` en `ScanViewModel.kt`**

En `ui/ScanViewModel.kt`, agrega un nuevo `catch` **antes** del `catch (e: ScanError.Validation)` existente:

```kotlin
            } catch (e: ScanError.Unauthorized) {
                _state.value = ScanUiState.Unauthorized
            } catch (e: ScanError.Validation) {
```

El archivo completo queda:

```kotlin
package pe.com.comparadorprecios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.com.comparadorprecios.data.ScanError
import pe.com.comparadorprecios.data.ScanRepository
import pe.com.comparadorprecios.data.isLowConfidence
import pe.com.comparadorprecios.util.PriceSorting

class ScanViewModel(
    private val repository: ScanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = ScanUiState.Idle
    }

    fun scan(imageDataUri: String) {
        if (_state.value is ScanUiState.Loading) return
        _state.value = ScanUiState.Loading
        viewModelScope.launch {
            try {
                val response = repository.scan(imageDataUri)
                _state.value = if (response.identification.isLowConfidence()) {
                    ScanUiState.LowConfidence(response.identification)
                } else {
                    ScanUiState.Success(
                        identification = response.identification,
                        tiendas = PriceSorting.sortForDisplay(response.tiendas),
                    )
                }
            } catch (e: ScanError.Unauthorized) {
                _state.value = ScanUiState.Unauthorized
            } catch (e: ScanError.Validation) {
                _state.value = ScanUiState.Error(
                    e.message ?: "La imagen no es válida. Toma otra foto."
                )
            } catch (e: ScanError.IdentificationFailed) {
                _state.value = ScanUiState.Error(
                    e.message ?: "No se pudo identificar el producto."
                )
            } catch (e: ScanError) {
                _state.value = ScanUiState.Error(e.message ?: "Ocurrió un error. Reintenta.")
            } catch (e: Exception) {
                _state.value = ScanUiState.Error("Error inesperado. Reintenta.")
            }
        }
    }
}
```

- [ ] **Step 8: Reaccionar a `ScanUiState.Unauthorized` en `MainActivity.kt` cerrando la sesión de Clerk**

En `MainActivity.kt`, agrega un nuevo import:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

Y agrega un nuevo caso al `when (val s = state)` existente dentro de `AppFlowState.Scanning`, junto a los demás (`is ScanUiState.Idle`, `is ScanUiState.Loading`, etc.):

```kotlin
                                is ScanUiState.Unauthorized -> {
                                    LaunchedEffect(Unit) { Clerk.signOut() }
                                    LoadingScreen()
                                }
```

Al cerrar la sesión, `Clerk.userFlow` emite `null`, `AppFlowState.from` recalcula a `Auth`, y el `when(flowState)` externo muestra `AuthView()` automáticamente — no hace falta ningún otro manejo manual.

- [ ] **Step 9: Verificar que todo compila y los tests pasan**

Run: `cd android && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos los tests pasan (incluyendo el nuevo `ScanRepositoryTest`).

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/java/pe/com/comparadorprecios/data/ScanApi.kt android/app/src/main/java/pe/com/comparadorprecios/data/ScanRepository.kt android/app/src/main/java/pe/com/comparadorprecios/ui/ScanUiState.kt android/app/src/main/java/pe/com/comparadorprecios/ui/ScanViewModel.kt android/app/src/main/java/pe/com/comparadorprecios/MainActivity.kt android/app/src/test/java/pe/com/comparadorprecios/data/ScanRepositoryTest.kt
git commit -m "feat: attach Clerk session token to scan requests, sign out on 401"
```

---

### Task 6: Verificación manual en vivo (cuenta real de Clerk + dispositivo real)

Esta tarea no tiene test automatizado: requiere una cuenta de Vercel y de Clerk reales, y un dispositivo Android físico o emulador con Play Services. No la puede completar un subagente.

**Files:** ninguno (solo verificación; puede generar un commit de ajuste si algo no coincide con lo asumido en las Tasks 4-5).

- [ ] **Step 1: Instalar Clerk vía Vercel Marketplace**

```bash
vercel link
vercel integration add clerk --yes
```

Si pide completar algo en el navegador/dashboard (crear la aplicación de Clerk), termina ese paso ahí antes de continuar.

- [ ] **Step 2: Descargar las variables de entorno y confirmar los nombres reales**

```bash
vercel env pull .env.local
vercel env ls
```

Expected: aparece una variable de secret key de Clerk (se asumió `CLERK_SECRET_KEY` en la Task 1 — si Vercel la nombró distinto, ajusta `lib/auth.ts` para usar el nombre real).

- [ ] **Step 3: Obtener la clave publicable de Clerk**

En el Dashboard de Clerk → API Keys, copia la "Publishable key" (empieza con `pk_test_` o `pk_live_`).

- [ ] **Step 4: Configurar `android/local.properties`**

Si no existe, cópialo desde el ejemplo:

```bash
cp android/local.properties.example android/local.properties
```

Edita `android/local.properties` con la URL real del backend desplegado y la clave publicable del Step 3:

```properties
scanBaseUrl=https://<tu-proyecto>.vercel.app/
clerkPublishableKey=pk_test_<tu-clave-real>
```

- [ ] **Step 5: Desplegar el backend**

```bash
vercel deploy --prod
```

- [ ] **Step 6: Compilar y correr la app en un dispositivo real**

Abre `android/` en Android Studio (o `cd android && ./gradlew :app:installDebug` con un dispositivo/emulador conectado) y verifica en orden:

1. Al abrir la app por primera vez, aparece `OnboardingScreen`. Al tocar "Empezar", pasa a la pantalla de Clerk.
2. Regístrate con un email de prueba usando el `AuthView` de Clerk.
3. Tras registrarte, la app pasa directo al flujo de escaneo (`ScanScreen`).
4. Cierra la app y ábrela de nuevo: **no** vuelve a mostrar el onboarding, y si la sesión de Clerk sigue activa, entra directo al escaneo (sin pedir login de nuevo).
5. Escanea un producto real — confirma que la petición a `/api/scan` ahora requiere sesión (revisa los logs de Vercel del deployment para confirmar que no hay peticiones 401 inesperadas) y que el resultado se ve igual que antes de esta feature.
6. En el Dashboard de Clerk, revoca la sesión activa del usuario de prueba (o elimina el usuario). Intenta escanear de nuevo en la app: debe volver a la pantalla de login (`AuthView`) en vez de mostrar un error genérico.

- [ ] **Step 7: Documentar y ajustar si algo no coincidió**

Si algún nombre de método de Clerk (`Clerk.userFlow`, `Clerk.session?.getToken()`, `Clerk.signOut()`) no existía tal cual y tuviste que usar otro durante las Tasks 4-5, o si el nombre real de la variable de entorno del secret key es distinto de `CLERK_SECRET_KEY`, corrígelo ahora y vuelve a correr `npm test` (backend) y `cd android && ./gradlew :app:testDebugUnitTest` (Android) para confirmar que las suites mockeadas siguen en verde.

- [ ] **Step 8: Commit final si hubo ajustes**

```bash
git add -A
git commit -m "fix: adjust Clerk integration after live verification"
```

(Omite este paso si el Step 6 funcionó sin cambios.)
