# Comparador de precios por escaneo de productos (Android)

## Resumen

App Android que permite escanear un producto con la cámara, identificarlo mediante IA de visión, y comparar su precio en tiempo real entre varias tiendas peruanas (Plaza Vea, Wong, Falabella, Ripley).

## Objetivo

Dado que un usuario apunta la cámara a un producto (de supermercado o de retail general) y toma una foto, la app debe:

1. Identificar qué producto es (marca, nombre, presentación/tamaño, categoría).
2. Buscar ese mismo producto en las 4 tiendas objetivo.
3. Mostrar una comparación de precios ordenada de menor a mayor, para que el usuario decida dónde comprarlo más barato.

## Alcance

- Categorías de producto: abarrotes/supermercado (Plaza Vea, Wong) y retail general/electrónicos (Falabella, Ripley) desde el MVP.
- Mercado: Perú.
- Sin cuentas de usuario ni historial persistente en el MVP.
- Precios obtenidos por scraping en vivo en el momento del escaneo (no hay catálogo pre-indexado).

## Arquitectura

```
[App Android (Kotlin)]
        |  foto (comprimida)
        v
[POST /api/scan  -- Backend Vercel Node.js/TS]
        |
        |-- 1. IA de visión (Claude/GPT-4V/Gemini)
        |      -> identifica: {marca, nombre, presentacion, categoria, confianza}
        |
        |-- 2. Búsqueda en paralelo por tienda (si confianza suficiente)
        |      - scraper Plaza Vea
        |      - scraper Wong
        |      - scraper Falabella
        |      - scraper Ripley
        |      (cada uno: fetch+parseo HTML, o headless browser si la tienda
        |       requiere JS para renderizar resultados de búsqueda)
        |
        |-- 3. Matching difuso: elige el mejor candidato de cada tienda
        |      comparando contra el producto identificado; descarta si la
        |      similitud es muy baja.
        |
        |-- 4. Agregación: combina resultados con estado por tienda
        |      (encontrado / no_encontrado / error)
        v
[Respuesta JSON] -> App muestra lista ordenada por precio
```

El backend concentra toda la lógica sensible (API keys de IA, scraping) — el cliente Android nunca llama directamente a las tiendas ni a la IA.

## Componentes

### App Android (Kotlin, nativo)

- **Pantalla de escaneo**: CameraX para visor y captura de foto (captura puntual, no streaming de frames a IA).
- **Pantalla de carga**: mientras el backend procesa (identificación + scraping en vivo puede tardar varios segundos).
- **Pantalla de resultados**: lista de tiendas ordenada por precio ascendente. Cada fila muestra tienda, nombre del producto encontrado, precio y enlace al producto. Tiendas sin resultado se muestran como "no disponible", no se omiten.
- **Pantalla de baja confianza / sin resultados**: si la IA no identifica el producto con confianza suficiente, o ninguna tienda encuentra match, se ofrece reintentar la foto o ingresar el nombre manualmente.
- Cliente HTTP (Retrofit/OkHttp) para subir la foto y recibir el JSON de comparación.
- Sin base de datos local ni autenticación en el MVP.

### Backend (Vercel, Node.js/TypeScript)

Endpoint único `POST /api/scan`:

1. **Identificación visual**: llama a una IA multimodal con un prompt que exige salida JSON estructurada (`marca`, `nombre`, `presentacion`, `categoria`, `confianza`). Si `confianza` es baja, responde de inmediato sin invocar scrapers.
2. **Scrapers por tienda**: un módulo independiente por tienda con interfaz común `buscar(query) -> {producto, precio, url} | null`, cada uno con su propio timeout para no bloquear a los demás si una tienda está lenta o caída. Empiezan con fetch HTTP + parseo HTML (cheerio); se usa navegador headless (Playwright) solo en las tiendas donde la búsqueda requiera JavaScript para renderizar resultados — decisión que se toma por tienda durante la implementación.
3. **Matching difuso**: dentro de los resultados de cada scraper, se selecciona el candidato más similar al producto identificado (comparación de texto sobre marca+nombre+presentación); se descarta si la similitud es insuficiente en vez de forzar un match incorrecto.
4. **Agregación**: combina los resultados de las 4 tiendas (o menos) en una respuesta con estado explícito por tienda (`encontrado`, `no_encontrado`, `error`).

## Manejo de errores y casos borde

- **Baja confianza en la identificación**: no se ejecutan scrapers; se pide reintentar foto o ingresar nombre manualmente.
- **Fallo o timeout de una tienda**: se marca como `error` para esa tienda; las demás continúan normalmente y se muestran igual.
- **Ninguna tienda encuentra match**: se informa al usuario y se ofrece reintentar o buscar manualmente.
- **Cambios en el HTML de una tienda** (rompe su scraper): al ser módulos independientes, no afecta a las demás tiendas. Se requiere monitoreo básico (logs en Vercel) para detectar cuándo un scraper deja de devolver resultados de forma sistemática.
- **Riesgo de Términos de Servicio**: el scraping de estos sitios para comparación de precios es una zona gris legal (no ilegal per se, pero puede violar ToS de cada tienda). Relevante si la app se distribuye públicamente a escala; se mitiga con rate limiting razonable y scrapers respetuosos (sin carga agresiva).

## Testing

- **Backend**: tests unitarios de cada scraper contra HTML de fixture (no contra las tiendas reales en cada test run) y tests del matching difuso con casos conocidos.
- **Verificación de scrapers reales**: chequeo manual/periódico contra las tiendas en vivo, dado que son inherentemente frágiles ante cambios de sitio.
- **Android**: pruebas manuales del flujo cámara → resultados (depende de hardware de cámara real); tests unitarios para el parseo de la respuesta del backend y los estados de la UI (carga, resultados, error, baja confianza).

## Fuera de alcance (para futuras iteraciones)

- Historial de escaneos / cuentas de usuario.
- Catálogo pre-indexado de precios (actualización periódica en vez de scraping en vivo).
- Soporte iOS.
- Más tiendas además de las 4 iniciales.
