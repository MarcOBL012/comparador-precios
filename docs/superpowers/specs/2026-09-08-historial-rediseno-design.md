# Rediseño M3 Expressive + Historial local de escaneos

## Resumen

La app Android (Plan 3) hoy escanea y compara precios pero con UI básica y sin memoria:
cada escaneo se pierde. Este spec cubre (1) rediseño de todas las pantallas con
Material 3 Expressive y (2) guardado local de escaneos con catálogo navegable.
El dashboard queda explícitamente fuera (futura iteración).

## Objetivo

Dado que un usuario escanea productos repetidamente, la app debe verse moderna
y permitirle volver a ver comparaciones anteriores sin re-escanear.

## Alcance

- Rediseño M3 Expressive de las 5 pantallas existentes (escaneo, carga,
  resultados, baja confianza, error) sin cambiar su comportamiento.
- Guardado automático de cada escaneo exitoso en base local (Room).
- Catálogo (Historial) con miniatura, detalle completo y borrado con deshacer.
- Navegación con barra inferior: Escanear / Historial.

## Arquitectura

```
MainActivity
 └── NavHost (scan | history | detail/{id})
      ├── scan: ScanScreen → Loading → Result / LowConfidence / Error  (sin cambios de comportamiento)
      └── history/: HistoryViewModel + DetailViewModel
                         └── HistoryRepository → HistoryDao → Room (solo dispositivo)
```

- Se adopta Navigation Compose; la máquina de estados de `ScanUiState` sigue
  gobernando el flujo de escaneo dentro del destino `scan`.
- Nueva capa `history/` aislada: entidad `ScanRecord`, `HistoryDao`,
  `HistoryRepository`, `HistoryViewModel`, `DetailViewModel`. Testeable sin Android.
- Al llegar a `ScanUiState.Success`, el registro se persiste en segundo plano;
  el resultado se muestra igual que hoy aunque el guardado falle.

## Componentes

### Rediseño (M3 Expressive, componentes estándar)

- Tema con esquema de color dinámico, tipografía y formas expresivas; modo
  oscuro según sistema.
- Captura con FAB grande y estado de progreso; resultados en tarjetas elevadas
  con precio destacado + nombre completo del producto (se conserva la
  mitigación del bug de multipacks del Plan 2).
- Carga/error con mensajes accionables en español. Sin marca custom.

### Historial local

- `ScanRecord`: id, fecha (epoch ms), marca, nombre, presentación, categoría,
  confianza, tiendas (JSON serializado), mejor precio y miniatura JPEG ~256px.
- La miniatura se deriva de la foto capturada; si excede 200 KB se recomprime.
  Nunca se guarda la foto completa.
- Catálogo ordenado por recientes primero: tarjeta con miniatura, nombre, fecha
  y mejor precio. Tap → detalle idéntico al resultado original (con links).
- Borrado con diálogo de confirmación + Snackbar con Deshacer.

## Manejo de errores y casos borde

- Sin espacio o DB corrupta: se informa con mensaje accionable; escanear sigue
  funcionando (el guardado nunca bloquea el resultado).
- Registro con `tiendas` vacía (baja confianza) no se guarda: no hay nada que
  comparar.
- La base vive solo en el dispositivo; desinstalar la app la elimina (documentado
  en el README, no se promete respaldo).

## Testing

- Unit tests JVM: DAO con Room in-memory, repositorio (guardar/listar/borrar),
  orden del catálogo, ViewModels (incluido deshacer).
- UI: `assembleDebug` + prueba manual en dispositivo (flujo completo y rotación).

## Fuera de alcance

- Dashboard de precios/estadísticas.- Sincronización en nube o cuentas (incompatible a propósito con depender del
  plan de Clerk aún no implementado; el repositorio se diseña con interfaz que
  permitiría una fuente remota futura).
- Buscador dentro del historial y edición de registros.

## Compatibilidad con el plan de auth (no implementado)

- Existe `docs/superpowers/plans/2026-09-06-auth-onboarding.md` (Clerk, sin
  implementar) que pedía no introducir Navigation Compose. Este spec lo
  sustituye en ese punto: la barra inferior exige navegación real y el historial
  local no necesita sesión. Si el plan de auth se retoma, su `AppFlowState`
  (Onboarding → Auth → App) envolverá al `NavHost` de este spec, no al revés.
