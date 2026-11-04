# Guion de la demo — Taller 3 (≈ 6 minutos)

Objetivo: mostrar que la app consume la API propia, que el cambio **queda guardado en la base de datos**
y que los errores se manejan. Cada paso indica qué decir y qué mostrar.

## Antes de empezar (5 min antes, no en vivo)

- APK nuevo instalado (el que trae la pestaña **Seguimiento**) y sesión iniciada en la app.
- Supabase abierto en **Table Editor** → tabla `watches` (y otra pestaña con `alerts`).
- Abrir una vez la app con datos móviles o wifi funcionando. Tener un producto a mano (una Free Tea, una leche...).
- Plan B si falla el celular: Postman con la colección `docs/api/postman_collection.json`
  contra la API local (`AUTH_DEV_MODE=1`, ver README), con la misma base en Docker.

## Guion

1. **Arquitectura (30 s).** «La app Android habla con una API REST propia en Vercel, versionada en `/api/v1`. Capas:
   router, controller que valida, service con las reglas y repository con SQL. Los datos viven en Postgres (Supabase).»
   Mostrar el diagrama del PDF o del README.

2. **Crear (POST).** En la cámara, fotografiar un producto y esperar la comparación. En la tarjeta **«Avísame cuando baje»**,
   poner un precio objetivo (por ejemplo, un poco menos que el mejor precio) y pulsar **Seguir precio**.
   - Mostrar la pestaña **Seguimiento**: aparece el producto.
   - **Cambio persistido:** en Supabase, recargar `watches`: hay una fila nueva con `target_price` y `status_code = activa`.

3. **Error controlado 409.** Volver a seguir el mismo producto. La app dice «Ya sigues este producto».
   «La base lo impide con una restricción única y la API responde 409.»

4. **Operación de negocio (POST /check).** En el seguimiento, pulsar **Revisar ahora**.
   - La API consulta las tiendas reales, guarda las observaciones de precio y decide alertas.
   - Mostrar `price_observations` y, si el objetivo se alcanzó, la fila en `alerts` y el estado `alcanzada` en `watches`.
   - Si el objetivo no se alcanza, bajar el objetivo con el paso 4b y revisar de nuevo, o subirlo para forzar la alerta.

   4b. **Actualizar (PATCH).** Pulsar **Objetivo** en la tarjeta y cambiarlo; recargar `watches` y ver `target_price` cambiado.

5. **Error controlado 429.** Pulsar **Revisar ahora** otra vez enseguida. La app avisa «vuelve a intentarlo en N min».
   «Regla de negocio: una revisión cada 15 minutos por seguimiento; el servidor devuelve 429 con `Retry-After`.»

6. **Historial (GET).** Abrir **Ver historial**: precios por tienda y estadística (mínimo, promedio).

7. **Pausar y alertas.** Pulsar **Pausar** (estado `pausada` en la base). Abrir la pestaña **Alertas** y marcar una como leída
   (`read_at` se llena en `alerts`).

8. **Error de conexión.** Activar modo avión y tocar **Revisar ahora**: mensaje de «sin conexión» y el botón se libera.
   Desactivar el modo avión.

9. **Eliminar (DELETE).** Pulsar **Eliminar** y confirmar en «Dejar de seguir»: desaparece de la lista y de `watches` (las alertas caen en cascada,
   el historial de precios del producto se conserva).

10. **Cierre (30 s).** «10 endpoints, base normalizada con triggers de integridad, 7 reglas de negocio y pruebas
    de integración contra Postgres real. Todo está documentado en OpenAPI y Postman, y se ejecuta con el README.»

## Si algo sale mal

| Síntoma | Qué hacer |
|---|---|
| «No pudimos consultar las tiendas» (502) | Es el manejo de error de un servicio externo: explicarlo como tal y repetir con otro producto. |
| «Sesión expirada» (401) | Cerrar sesión y volver a entrar en la app. |
| Error 500 o 503 | Falta `DATABASE_URL` en Vercel o Supabase está pausado: reanudar el proyecto en Supabase y reintentar. |
| Nada responde | Usar el plan B (Postman local). |

## Preguntas probables

- **¿Por qué 404 y no 403 para un recurso ajeno?** Para no revelar que existe.
- **¿Qué pasa si dos revisiones llegan a la vez?** La revisión usa una transacción con `for update`: no se duplican alertas.
- **¿Por qué la alerta no guarda el mensaje?** Normalización: se deriva del tipo, la observación y el producto.
- **¿Hay datos simulados?** No: los precios vienen de las tiendas en cada revisión.
