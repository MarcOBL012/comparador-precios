# Onboarding y registro/login con Clerk

## Resumen

Antes de poder escanear un producto, la app Android debe: (1) mostrarle al usuario, solo la primera vez que abre la app, una pantalla de onboarding con las funcionalidades principales, y (2) exigirle iniciar sesión o registrarse mediante Clerk. El flujo de escaneo existente (Plan 3) no cambia — esta feature añade una compuerta delante de él.

## Objetivo

- Mostrar una pantalla de bienvenida/funcionalidades la primera vez que se abre la app.
- Exigir una cuenta real (registro o inicio de sesión) antes de permitir escanear.
- Usar Clerk como proveedor de autenticación: su SDK nativo de Android para la UI de registro/login, y verificación de sesión en el backend de Vercel antes de procesar un escaneo.

## Alcance

- Onboarding: solo UI local, se muestra una única vez (bandera persistida en el dispositivo).
- Autenticación: cuentas reales vía Clerk (no un formulario local ni una simulación). Login **obligatorio** para escanear.
- Fuera de alcance: historial de escaneos ligado a la cuenta, perfil de usuario editable, inicio de sesión social (Google/Apple), recuperación de cuenta más allá de lo que Clerk ya incluye por defecto. Estas quedan como posibles iteraciones futuras una vez que exista una cuenta de usuario real que las sostenga.

## Arquitectura

```
[App Android]
  Abrir app
    → leer bandera "hasSeenOnboarding" (DataStore)
        no vista → OnboardingScreen → al continuar, guardar bandera
        vista    → saltar
    → ¿sesión activa de Clerk?
        no  → AuthView de Clerk (registro o login)
        sí  → continuar
    → flujo de escaneo existente (ScanScreen → ... sin cambios)
        cada POST /api/scan incluye "Authorization: Bearer <token de sesión de Clerk>"

[Backend Vercel]
  POST /api/scan
    → verificar token de Clerk con @clerk/backend
        inválido/ausente → 401
        válido           → sigue a handleScan (sin cambios)
```

Clerk se instala como integración nativa del Vercel Marketplace (variables de entorno auto-provisionadas, no se hardcodean claves). La app Android no habla directamente con el backend para autenticarse — el SDK de Clerk en Android maneja el registro/login y la sesión directamente contra los servidores de Clerk; el backend solo verifica el token que la app ya obtuvo.

## Componentes

### App Android — nuevos

- **`ui/OnboardingScreen.kt`**: Composable con tarjetas de las funcionalidades principales de la app (escanear producto, comparar precios en Plaza Vea y Wong) y un botón para continuar.
- **`data/OnboardingPreferences.kt`**: wrapper de DataStore con una única bandera booleana `hasSeenOnboarding`, con métodos para leerla (`Flow<Boolean>` o `suspend fun`) y marcarla como vista.
- **`ui/AppFlowState.kt`**: sealed class con los estados de nivel superior: `Onboarding`, `Auth`, `Scanning`. Sigue el mismo patrón de máquina de estados sin `NavHost` que ya usa `ScanUiState` en el flujo de escaneo — no se introduce Navigation Compose en esta feature.

### App Android — modificados

- **`MainActivity.kt`**: el `when` sobre `ScanUiState` que ya existe queda envuelto dentro de un `when` sobre `AppFlowState`. Cuando el estado es `Scanning`, se renderiza exactamente el flujo actual sin cambios.
- **Cliente Retrofit/OkHttp** (en `data/`): se agrega un interceptor que obtiene el token de sesión activo de Clerk y lo añade como header `Authorization: Bearer <token>` en las peticiones a `/api/scan`. Si Clerk no tiene sesión activa en ese momento, la petición no debería llegar a dispararse (el `AppFlowState` ya lo impide), pero el interceptor no debe asumirlo ciegamente — si no hay token disponible, deja la petición sin ese header y el backend la rechaza con 401 de todas formas.
- **`local.properties.example`**: se agrega una entrada para la publishable key de Clerk (análoga a como ya existe `scanBaseUrl`).

### Backend — modificado

- **`api/scan.ts`**: antes de llamar a `handleScan`, verifica el header `Authorization` contra Clerk usando `@clerk/backend`. Sin sesión válida, responde `401` con un cuerpo `{ "error": "..." }` consistente con el resto de respuestas de error del endpoint. Con sesión válida, el comportamiento existente no cambia en nada.

### Backend — nueva dependencia

- `@clerk/backend` (paquete oficial de Clerk, agnóstico de framework — funciona en una función serverless plana de Vercel).

## Nota para quien escriba el plan de implementación

Este spec describe el diseño y el flujo, no fija nombres exactos de funciones del SDK de Clerk (por ejemplo, cómo se inicializa `Clerk` en Android, la firma exacta del composable `AuthView`, cómo se lee el token de sesión activo, o la función exacta de `@clerk/backend` para verificar un token). Esas APIs deben verificarse contra la documentación oficial vigente de Clerk al momento de escribir el plan — igual que se hizo con el AI SDK de Vercel en el Plan 1 — en vez de asumirlas de memoria, ya que Clerk las ha cambiado entre versiones (ver el aviso de breaking changes de Clerk Core 3 de referencia general, aunque ese aviso es específico de Next.js y no aplica literalmente aquí).

## Manejo de errores y casos borde

- **Onboarding**: sin estados de error; si la lectura de DataStore falla o está vacía la primera vez, se trata como "no vista" (se muestra el onboarding).
- **Registro/login**: delegado por completo al `AuthView` de Clerk — errores de contraseña débil, email inválido, cuenta ya existente, etc. se muestran con la UI que Clerk ya provee.
- **Sesión expirada o token inválido a mitad de uso**: si `/api/scan` responde `401`, la app debe interpretar esto como "sesión inválida", limpiar el estado de `Scanning` y volver a `AppFlowState.Auth` — no debe mostrarse como un error genérico del flujo de escaneo.
- **Clerk mal configurado** (publishable key faltante o inválida, o falla al inicializar el SDK): esto es un error de configuración de desarrollo, no un caso de usuario final a manejar con UI — debe fallar de forma visible (crash o log claro) para que se note durante el desarrollo, no silenciarse.

## Testing

- **Android**: tests unitarios de `AppFlowState` — dadas las combinaciones (onboarding visto/no visto) × (sesión activa/inactiva), verificar qué estado corresponde. Tests unitarios de `OnboardingPreferences` (guardar y leer la bandera). Estos son lógica pura, no requieren dispositivo ni SDK de Clerk real.
- **Backend**: test de `api/scan.ts` mockeando `@clerk/backend` — token válido deja pasar a `handleScan` (mockeado); token ausente o inválido responde `401`. Sigue el mismo patrón de mocks (`vi.hoisted()`, `vi.mock()`) ya usado en el resto de tests del backend.
- **Manual**: verificación de extremo a extremo en un dispositivo real con una cuenta real de Clerk (registro, login, escaneo autenticado, sesión expirada) — no se puede automatizar por completo, análogo a la Task 7 del Plan 1.

## Fuera de alcance (para futuras iteraciones)

- Historial de escaneos ligado a la cuenta del usuario.
- Perfil de usuario editable (nombre, foto, preferencias).
- Login social (Google, Apple) — Clerk lo soporta, pero no se activa en esta iteración.
- Recuperación de cuenta más allá de lo que Clerk provee por defecto.
