# Genera docs/taller3/Taller3_DocumentoTecnico.pdf:  python docs/taller3/build_pdf.py
# Documento técnico del Taller 3 (máx. 5 páginas, sin carátula).
import os
from reportlab.lib import colors
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether

FONTS = os.environ.get('WINDIR', 'C:/Windows') + '/Fonts/'
pdfmetrics.registerFont(TTFont('Body', FONTS + 'arial.ttf'))
pdfmetrics.registerFont(TTFont('Body-Bold', FONTS + 'arialbd.ttf'))
pdfmetrics.registerFont(TTFont('Body-Italic', FONTS + 'ariali.ttf'))
pdfmetrics.registerFont(TTFont('Mono', FONTS + 'consola.ttf'))
pdfmetrics.registerFontFamily('Body', normal='Body', bold='Body-Bold', italic='Body-Italic', boldItalic='Body-Bold')

GREEN = colors.HexColor('#1B7F4B')
ORANGE = colors.HexColor('#E8741E')
INK = colors.HexColor('#1F2933')
GREY = colors.HexColor('#F2F4F5')
LINE = colors.HexColor('#C9D0D4')

h1 = ParagraphStyle('h1', fontName='Body-Bold', fontSize=15, leading=18, textColor=GREEN, spaceAfter=2)
by = ParagraphStyle('by', fontName='Body', fontSize=8, leading=10, textColor=colors.HexColor('#5B6770'), spaceAfter=6)
h2 = ParagraphStyle('h2', fontName='Body-Bold', fontSize=10.5, leading=13, textColor=GREEN, spaceBefore=7, spaceAfter=2)
p = ParagraphStyle('p', fontName='Body', fontSize=8.6, leading=11.2, textColor=INK, alignment=TA_JUSTIFY, spaceAfter=3)
cell = ParagraphStyle('cell', parent=p, fontSize=7.8, leading=9.6, alignment=0, spaceAfter=0)
cellb = ParagraphStyle('cellb', parent=cell, fontName='Body-Bold')
code = ParagraphStyle('code', parent=cell, fontName='Mono', fontSize=7.4)
bullet = ParagraphStyle('bullet', parent=p, leftIndent=10, bulletIndent=1, spaceAfter=1.5)


def para(t):
    return Paragraph(t, p)


def bl(items):
    return [Paragraph(t, bullet, bulletText='•') for t in items]


def table(rows, widths, head=True, mono_cols=()):
    data = []
    for i, r in enumerate(rows):
        data.append([Paragraph(str(c), cellb if (head and i == 0) else (code if j in mono_cols else cell)) for j, c in enumerate(r)])
    t = Table(data, colWidths=widths, repeatRows=1 if head else 0)
    st = [('GRID', (0, 0), (-1, -1), 0.4, LINE), ('VALIGN', (0, 0), (-1, -1), 'TOP'),
          ('TOPPADDING', (0, 0), (-1, -1), 2), ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
          ('LEFTPADDING', (0, 0), (-1, -1), 3), ('RIGHTPADDING', (0, 0), (-1, -1), 3)]
    if head:
        st += [('BACKGROUND', (0, 0), (-1, 0), GREY)]
    t.setStyle(TableStyle(st))
    return t


def box(d, x, y, w, h, title, sub='', fill=GREY, stroke=GREEN):
    d.add(Rect(x, y, w, h, rx=4, ry=4, fillColor=fill, strokeColor=stroke, strokeWidth=0.9))
    d.add(String(x + w / 2, y + h - 11 if sub else y + h / 2 - 3, title, fontName='Body-Bold', fontSize=7.6, fillColor=INK, textAnchor='middle'))
    if sub:
        for i, line in enumerate(sub.split('\n')):
            d.add(String(x + w / 2, y + h - 21 - i * 8.2, line, fontName='Body', fontSize=6.5, fillColor=colors.HexColor('#3E4C59'), textAnchor='middle'))


def arrow(d, x1, y1, x2, y2):
    d.add(Line(x1, y1, x2, y2, strokeColor=colors.HexColor('#52606D'), strokeWidth=0.9))
    import math
    a = math.atan2(y2 - y1, x2 - x1)
    s = 4
    d.add(Polygon([x2, y2, x2 - s * math.cos(a - 0.45), y2 - s * math.sin(a - 0.45), x2 - s * math.cos(a + 0.45), y2 - s * math.sin(a + 0.45)],
                  fillColor=colors.HexColor('#52606D'), strokeColor=colors.HexColor('#52606D')))


def architecture():
    d = Drawing(17.4 * cm, 3.5 * cm)
    y = 38
    xs = [(0, 'App Android', 'Compose + Retrofit\nViewModel + errores tipados'),
          (92, 'api/v1/dispatch', 'Vercel serverless\nRouter + autenticación'),
          (184, 'Controller', 'Zod: valida entrada\nerror -> código HTTP'),
          (276, 'Service', 'Reglas R1–R7\nalertRules (puras)'),
          (368, 'Repository', 'Solo SQL, transacciones\npostgres.js')]
    for x, t, s in xs:
        box(d, x + 2, y, 84, 52, t, s)
    for i in range(len(xs) - 1):
        arrow(d, xs[i][0] + 86, y + 26, xs[i + 1][0] + 2, y + 26)
    box(d, 368 + 2, 0, 84, 28, 'Postgres', 'Supabase / Docker', fill=colors.HexColor('#FFF3E6'), stroke=ORANGE)
    arrow(d, 368 + 44, y, 368 + 44, 28)
    box(d, 184 + 2, 0, 84, 28, 'Clerk', 'sub = dueño', fill=colors.HexColor('#FFF3E6'), stroke=ORANGE)
    arrow(d, 92 + 60, y, 184 + 30, 28)
    box(d, 276 + 2, 0, 84, 28, 'Tiendas (Serper/VTEX)', '', fill=colors.HexColor('#FFF3E6'), stroke=ORANGE)
    arrow(d, 276 + 44, y, 276 + 44, 28)
    return d


def er():
    d = Drawing(17.4 * cm, 185)
    W, H = 100, 38
    def e(x, y, t, sub):
        box(d, x, y, W, H, t, sub)
    e(0, 118, 'brands / product_types', 'catálogos de marca y tipo')
    e(0, 64, 'categories', 'catálogo de categorías')
    e(0, 10, 'stores', 'cadenas (5 sembradas)')
    e(135, 91, 'products', 'brand, category, type\nnombre + presentación únicos')
    e(135, 10, 'store_listings', 'product_id, store_id\ntítulo, url')
    e(270, 10, 'price_observations', 'listing_id, source\nprecio > 0, observed_at')
    e(270, 91, 'watches', 'user_id, product_id únicos\ntarget_price, status_code')
    e(405, 91, 'app_users', 'clerk_id único')
    e(405, 10, 'alerts', 'watch, observation, tipo\nunique + trigger')
    e(270, 140, 'watch_statuses', 'estados + transiciones')
    arrow(d, W, 137, 135, 125)
    arrow(d, W, 83, 135, 108)
    arrow(d, 185, 91, 185, 48)
    arrow(d, W, 29, 135, 29)
    arrow(d, 235, 29, 270, 29)
    arrow(d, 270, 110, 235, 110)
    arrow(d, 405, 110, 370, 110)
    arrow(d, 405, 29, 370, 29)
    arrow(d, 320, 140, 320, 129)
    arrow(d, 405, 48, 350, 91)
    return d


def build(path):
    doc = SimpleDocTemplate(path, pagesize=A4, leftMargin=1.7 * cm, rightMargin=1.7 * cm, topMargin=1.4 * cm, bottomMargin=1.3 * cm,
                            title='Taller 3 - API REST de seguimiento de precios', author='Brian Curay, Marco Obispo, Niels Chaves')
    s = []
    s.append(Paragraph('API REST de seguimiento de precios con alertas', h1))
    s.append(Paragraph('Taller 3 · SI806 Desarrollo e integración de una API REST · Brian Curay, Marco Obispo, Niels Chaves · Repositorio: github.com/MarcOBL012/comparador-precios', by))

    s.append(Paragraph('1. Problema y alcance', h2))
    s.append(para('Quien compra en tiendas peruanas no sabe cuándo baja el precio de un producto que quiere. La app <i>Comparador de Precios</i> ya identifica un producto con una foto y compara tiendas, pero esa consulta es puntual. '
                  'Este taller agrega una <b>API REST propia</b> que permite <b>seguir un producto</b>, guardar la historia de sus precios en una base de datos relacional, y <b>generar alertas</b> cuando el precio alcanza el objetivo del usuario o llega a su mínimo histórico. '
                  'Los precios provienen de consultas reales a las tiendas; no hay datos simulados. La API es nueva (versionada en <font name="Mono">/api/v1</font>) y el cliente Android la consume desde una pestaña nueva, «Seguimiento».'))

    s.append(Paragraph('2. Arquitectura', h2))
    s.append(architecture())
    s.append(Spacer(1, 3))
    s += bl([
        '<b>Capas separadas.</b> El <i>router</i> resuelve método y ruta; el <i>controller</i> valida la entrada con Zod (esquemas estrictos: un campo desconocido es error 400) y traduce errores de dominio a códigos HTTP; el <i>service</i> aplica las reglas de negocio; el <i>repository</i> es la única capa con SQL. '
        'Las decisiones de alertas viven en funciones puras (<font name="Mono">alertRules.ts</font>), fáciles de probar sin base de datos.',
        '<b>Un solo punto de entrada.</b> Una función serverless <font name="Mono">api/v1/dispatch.ts</font> atiende toda la API con un router propio; así se evita el límite de funciones del plan gratuito de Vercel y las rutas con parámetros quedan en un solo sitio.',
        '<b>Identidad.</b> El dueño de cada recurso es el <font name="Mono">sub</font> del token de Clerk, nunca un dato del cuerpo. Un recurso de otro usuario responde 404 (no 403) para no revelar que existe. Las tareas internas se protegen con <font name="Mono">CRON_SECRET</font> comparado en tiempo constante.',
        '<b>Preparada para eventos.</b> Cada alerta escribe un evento en la tabla <font name="Mono">domain_events</font> (patrón <i>outbox</i>) dentro de la misma transacción, lo que deja listo el enganche para notificaciones o procesos asíncronos sin acoplar el servicio.',
    ])

    s.append(Paragraph('3. Modelo de datos', h2))
    s.append(para('Postgres (Supabase en producción, Docker en desarrollo), esquema versionado en <font name="Mono">db/migrations/001–005</font>. 19 tablas y una vista, normalizadas a 3FN: ninguna columna depende de otra no clave. '
                  'Por ejemplo, la alerta <b>no guarda</b> su mensaje (se deriva del tipo, la observación y el producto) ni el usuario (se deriva del seguimiento), y el estado del seguimiento es una clave foránea a un catálogo.'))
    s.append(er())
    s.append(Spacer(1, 3))
    s.append(table([
        ['Tabla', 'Rol', 'Integridad'],
        ['products, brands, product_types, categories', 'Catálogo de productos; el mismo producto no se duplica', '<font name="Mono">unique nulls not distinct (marca, nombre, presentación)</font>; FK a catálogos'],
        ['stores, store_listings, price_observations', 'Dónde se vende y qué precio tuvo y cuándo (historial append-only)', 'precio &gt; 0; borrado en cascada de listados y observaciones'],
        ['app_users, watches', 'Un seguimiento por usuario y producto', '<font name="Mono">unique (user_id, product_id)</font>; objetivo &gt; 0; FK a <font name="Mono">watch_statuses</font>'],
        ['watch_statuses, watch_status_transitions', 'Máquina de estados como datos', 'Trigger rechaza una transición no registrada, aunque se escriba por fuera de la API'],
        ['alerts, alert_types', 'Alertas derivadas de una observación', '<font name="Mono">unique (observación, seguimiento, tipo)</font>: no se duplican; trigger exige que la observación sea del mismo producto'],
        ['devices, push_deliveries, domain_events', 'Dispositivos, entregas de notificación y outbox', 'Preparadas para notificaciones push y procesamiento de eventos'],
        ['identification_signatures, signature_confirmations', 'Confirmaciones de la comunidad (vista de consenso)', 'Un voto por usuario y firma; verificado con ≥ 2 votos y ≥ 66 %'],
    ], [4.6 * cm, 5.6 * cm, 7.4 * cm]))

    s.append(Paragraph('4. Endpoints', h2))
    s.append(table([
        ['Método y ruta', 'Qué hace', 'Respuestas'],
        ['POST /watches', 'Sigue un producto (objetivo opcional)', '201 + Location; 400, 401, 409, 422'],
        ['GET /watches', 'Lista del usuario (filtro estado, límite, desplazamiento)', '200; 400, 401'],
        ['GET /watches/{id}', 'Detalle con mejor precio actual', '200; 400, 401, 404'],
        ['PATCH /watches/{id}', 'Cambia objetivo o estado (pausar, reactivar)', '200; 400, 401, 404, 409, 422'],
        ['DELETE /watches/{id}', 'Deja de seguir y borra sus alertas', '204; 401, 404'],
        ['POST /watches/{id}/check', '<b>Operación de negocio:</b> consulta tiendas, guarda observaciones y decide alertas', '200; 401, 404, 409, 429 + Retry-After, 502'],
        ['GET /watches/{id}/history', 'Historial de precios y estadística (mínimo, máximo, mediana, tendencia)', '200; 400, 401, 404'],
        ['GET /alerts', 'Alertas del usuario, con filtro de no leídas', '200; 400, 401'],
        ['PATCH /alerts/{id}', 'Marca una alerta como leída o no leída', '200; 400, 401, 404'],
        ['GET|POST /internal/check-due', 'Cron diario: revisa los seguimientos que lo necesitan', '200; 401'],
    ], [4.4 * cm, 8.0 * cm, 5.2 * cm], mono_cols=(0,)))
    s.append(Spacer(1, 2))
    s.append(para('Todos los errores devuelven JSON <font name="Mono">{error, codigo, detalles?}</font>; en 400 y 422 <i>detalles</i> indica el campo y el motivo. '
                  'Cuerpos de ejemplo, esquemas y códigos de cada ruta están en <font name="Mono">docs/api/openapi.yaml</font>, y una colección de Postman con 26 peticiones (éxitos y fallos) en <font name="Mono">docs/api/postman_collection.json</font>.'))

    s.append(Paragraph('5. Reglas de negocio', h2))
    s.append(table([
        ['Regla', 'Descripción', 'Resultado'],
        ['R1', 'Un seguimiento por usuario y producto', '409 <font name="Mono">ya_seguido</font>'],
        ['R2', 'Máximo 20 seguimientos activos por usuario', '409 <font name="Mono">limite_seguimientos</font>'],
        ['R3', 'Una revisión cada 15 minutos por seguimiento (protege a las tiendas y la cuota)', '429 con <font name="Mono">Retry-After</font>'],
        ['R4', 'Si el mejor precio ≤ objetivo se crea la alerta <i>objetivo_alcanzado</i> y el seguimiento pasa a <i>alcanzada</i>', 'Alerta + cambio de estado, en una transacción'],
        ['R5', 'Alerta <i>minimo_historico</i> si el precio supera el menor de al menos 3 observaciones previas', 'Alerta'],
        ['R6', 'Se descartan precios atípicos: menos del 40 % de la mediana histórica, con al menos 3 observaciones previas (listados mal emparejados)', 'No se guarda la observación'],
        ['R7', 'Transiciones: activa ↔ pausada; alcanzada → activa | pausada; solo el sistema pasa a alcanzada; reactivar una alcanzada exige un objetivo menor', '409 / 422'],
    ], [1.2 * cm, 11.0 * cm, 5.4 * cm]))
    s.append(Spacer(1, 2))
    s.append(para('La revisión (<font name="Mono">recordCheck</font>) usa una transacción con bloqueo de fila (<font name="Mono">for update</font>): observaciones, alertas, cambio de estado y evento se guardan juntos o no se guarda nada. '
                  'Así, dos revisiones simultáneas no duplican alertas.'))

    s.append(Paragraph('6. Validación y manejo de errores', h2))
    s += bl([
        '<b>Servidor:</b> Zod valida cuerpo, parámetros de ruta y consulta (UUID, rangos, tipos); el controller devuelve 400 con detalle por campo. Los errores de dominio se mapean en un solo sitio (<font name="Mono">lib/http/respond.ts</font>): 401, 404, 405, 409, 422, 429, 502 (tiendas no responden), 503 (sin base de datos) y 500 sin filtrar detalles internos.',
        '<b>Cliente Android:</b> <font name="Mono">WatchRepository</font> convierte cualquier fallo en un <font name="Mono">ApiError</font> tipado: <i>Network</i> (sin conexión o tiempo agotado), <i>BadResponse</i> (JSON con forma inesperada), <i>Server</i> (5xx), más <i>Unauthorized</i>, <i>NotFound</i>, <i>Conflict</i>, <i>Validation</i> y <i>RateLimited</i>. La pantalla muestra un mensaje distinto para cada caso y ofrece reintentar.',
    ])

    s.append(Paragraph('7. Cliente Android', h2))
    s.append(para('Consume 9 de los endpoints desde Retrofit con kotlinx.serialization. Pantalla <i>Seguimiento</i>: lista con estado, mejor precio y objetivo; acciones revisar ahora, pausar o reactivar, editar objetivo, ver historial y eliminar; alertas con contador de no leídas. '
                  'Tras una comparación, la tarjeta «Seguir este precio» crea el seguimiento. El <i>ViewModel</i> expone un <font name="Mono">StateFlow</font> y libera los botones ante cualquier error, incluido el 429.'))

    s.append(KeepTogether([Paragraph('8. Pruebas', h2), table([
        ['Nivel', 'Cantidad', 'Qué cubre'],
        ['Reglas puras (<font name="Mono">alertRules</font>)', '15', 'Objetivo alcanzado, mínimo histórico, outliers, historial insuficiente, mensajes'],
        ['Servicio con repositorio en memoria', '15', 'R1–R3 y R7, propiedad del recurso, estados inválidos'],
        ['API completa (router + controller + servicio) y autenticación', '17', 'Códigos 200/201/204/400/401/404/405/409/422/429, JSON de error, validación, modo desarrollo ignorado en Vercel'],
        ['<b>Integración contra Postgres real</b>', '13', 'CRUD persistido, producto compartido entre usuarios (normalización), R3, R4 y R6, atomicidad de la revisión, cascadas, cron, 404 en recursos ajenos'],
        ['Android (repositorio y ViewModel)', '23', 'Mapeo de 401/404/409/400/422/429/502/500, sin red, JSON inválido; estados de la pantalla'],
    ], [6.4 * cm, 1.8 * cm, 9.4 * cm])]))
    s.append(Spacer(1, 2))
    s.append(para('Backend: <font name="Mono">npm test</font> (Vitest; las de integración corren con <font name="Mono">TEST_DATABASE_URL</font>) y <font name="Mono">npm run build</font> (tsc). Android: <font name="Mono">./gradlew :app:testDebugUnitTest</font>. Todas pasan al momento de la entrega.'))

    s.append(Paragraph('9. Cómo ejecutarlo', h2))
    s.append(para('El README del repositorio detalla los pasos: <font name="Mono">npm run db:up</font> levanta Postgres con Docker, <font name="Mono">npm run db:migrate</font> aplica el esquema, y la API local se prueba con la colección de Postman. '
                  'En producción se requieren dos variables de entorno en Vercel (<font name="Mono">DATABASE_URL</font>, del <i>Transaction pooler</i> de Supabase, y <font name="Mono">CRON_SECRET</font>); <font name="Mono">vercel.json</font> programa la revisión diaria.'))

    doc.build(s)


if __name__ == '__main__':
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'Taller3_DocumentoTecnico.pdf')
    build(out)
    print(out)
