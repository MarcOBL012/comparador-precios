# Evidencias del Taller 3

## Entregables finales (carpeta `docs/taller3/`)

| Entregable del taller | Archivo | Páginas |
|---|---|---|
| Documento técnico (máx. 5, sin carátula) | `Taller3_DocumentoTecnico.pdf` | 3 |
| Documentación de la API (rutas, métodos, parámetros, JSON, códigos) | `Taller3_Documentacion_API.pdf` (+ `docs/api/openapi.yaml` y `postman_collection.json`) | 4 |
| Demostración (cliente consumiendo la API, cambio persistido) | `Taller3_Demostracion_Evidencias.pdf` | 6 |

Se regeneran con `python -X utf8 -I scripts/build_evidencia.py` y luego `python -X utf8 -I scripts/build_entregables.py`.
Las capturas originales (celular y Vercel) están en `evidencia/capturas/`.

---

Generadas el 2026-11-08 (hora Perú). `Evidencia_Taller3.pdf` junta todo (36 páginas); las imágenes sueltas están en `imagenes/`.

**Qué son:** representaciones de salida real (Newman, Vitest, consultas SQL a Supabase, curl a Vercel). **No son capturas de pantalla** de Postman, Supabase ni Vercel. Si el docente exige la ventana de la herramienta, hay que capturarla a mano.

| Imágenes | Qué demuestran | Contra qué base |
|---|---|---|
| `postman_00_resumen_26_peticiones` | La colección completa: 26 peticiones, 33 comprobaciones, 0 fallos | Local (Docker) |
| `postman_01` … `postman_26` | Cada petición con su JSON de entrada y respuesta, encabezados y código (201, 200, 204, 400, 401, 404, 405, 409, 429) | Local (Docker) |
| `tests_01_integracion_postgres_real` | 13 pruebas que escriben en Postgres real (CRUD, R3, R4, R6, atomicidad, cascadas, cron) | Local (Docker) |
| `tests_02_suite_completa_199` | 26 archivos, 199 pruebas pasando | Local (Docker) |
| `supabase_01` … `supabase_06` | Tablas, esquema (19 tablas, 21 FK), seguimientos, historial de precios, cron diario del 4 al 8 de nov, alerta creada y leída, migraciones | Producción (Supabase) |
| `vercel_01` … `vercel_05` | La API desplegada responde: 200 en /api/health, 401 sin sesión, 405 con método incorrecto | Producción (Vercel) |

## Lo que falta y hay que capturar a mano
- Pantallas de la app Android (pestaña Seguimiento, «Revisar ahora», historial, errores 409/429, modo avión).
- Panel de Supabase (Table Editor o SQL Editor) y panel de Vercel, si el docente pide ver la herramienta.

## Regenerar
`python -I scripts/build_evidencia.py` (necesita Edge y Pillow). Lee `raw/`: `newman.json`, `tests_*.txt`, `supabase_produccion.json`, `vercel_produccion.txt`.
