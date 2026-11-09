"""Genera los dos PDF de entrega del Taller 3 con Edge headless:
  docs/taller3/Taller3_Documentacion_API.pdf        (ejemplos JSON tomados de la corrida real de Newman)
  docs/taller3/Taller3_Demostracion_Evidencias.pdf  (capturas del celular y de Vercel + evidencias de datos reales)

Requiere docs/taller3/evidencia/ (scripts/build_evidencia.py) y las capturas originales en evidencia/capturas/.
Uso:  python -X utf8 -I scripts/build_entregables.py
"""
import html
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
T3 = ROOT / "docs" / "taller3"
EV = T3 / "evidencia"
IMG = EV / "imagenes"
CAP = EV / "capturas"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
AUTORES = "Brian Curay, Marco Obispo, Niels Chaves"
REPO = "github.com/MarcOBL012/comparador-precios"
PROD = "https://comparador-precios-marco-obl.vercel.app/api/v1"

CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:'Segoe UI',Arial,sans-serif;color:#1b1f27;font-size:10pt;line-height:1.42}
@page{size:A4;margin:12mm}
.page{page-break-after:always}.page:last-child{page-break-after:auto}
h1{font-size:17pt;margin:0 0 2mm;color:#111827}
h2{font-size:12pt;margin:5mm 0 2mm;color:#1f2937;border-bottom:2px solid #e11d2a;padding-bottom:1mm}
h3{font-size:10.5pt;margin:4mm 0 1.5mm;color:#1f2937}
.sub{color:#5b6472;font-size:9pt;margin-bottom:3mm}
p{margin:1.5mm 0}
code{font-family:Consolas,'Courier New',monospace;font-size:8.8pt;background:#f1f4f9;padding:0 2px;border-radius:3px}
pre{margin:1.5mm 0;font:8.3pt/1.35 Consolas,'Courier New',monospace;background:#f5f7fa;border:1px solid #e3e7ee;border-radius:4px;padding:2mm 3mm;white-space:pre-wrap;word-break:break-word}
table{border-collapse:collapse;width:100%;font-size:8.8pt;margin:1.5mm 0}
th{background:#1f2937;color:#fff;text-align:left;padding:1.6mm 2mm}
td{padding:1.4mm 2mm;border-bottom:1px solid #e3e7ee;vertical-align:top}
.m{display:inline-block;padding:0 5px;border-radius:3px;color:#fff;font-weight:700;font-size:8pt}
.GET{background:#2563eb}.POST{background:#16a34a}.PATCH{background:#d97706}.PUT{background:#7c3aed}.DELETE{background:#dc2626}
.c{display:inline-block;padding:0 5px;border-radius:3px;color:#fff;font-weight:700;font-size:8pt}
.c2{background:#15803d}.c4{background:#c2410c}
.cols{display:flex;gap:3mm;align-items:flex-start}.cols>div{flex:1;text-align:center;font-size:8.5pt}
.cols img{width:100%;border:1px solid #cfd5df;border-radius:3mm}
.fig{text-align:center;margin:2mm 0}.fig img{border:1px solid #d5dae3;border-radius:2mm}
.cap{font-size:8.5pt;color:#5b6472;margin:1mm 0 3mm}
.nota{font-size:8.5pt;color:#5b6472;border-left:3px solid #e11d2a;padding-left:3mm;margin-top:3mm}
.rojo{color:#e11d2a}
.doc{font-size:8.8pt;line-height:1.35}.doc h1{font-size:14pt}.doc h2{font-size:10pt;margin:3mm 0 1.2mm;break-after:avoid}
.doc pre{font-size:7.5pt;line-height:1.27;padding:1mm 2.5mm;margin:1mm 0;break-inside:avoid}
.doc table{font-size:7.9pt}.doc td{padding:1mm 2mm}.doc th{padding:1.2mm 2mm}.doc code{font-size:7.8pt}
"""

e = html.escape


def uri(p):
    return Path(p).as_uri()


def m(method):
    return f'<span class="m {method}">{method}</span>'


def c(code):
    cls = "c2" if code < 300 else "c4"
    return f'<span class="c {cls}">{code}</span>'


def pre(text):
    return f"<pre>{e(text)}</pre>"


def table(headers, rows):
    return "<table><tr>" + "".join(f"<th>{h}</th>" for h in headers) + "</tr>" + "".join(
        "<tr>" + "".join(f"<td>{x}</td>" for x in r) + "</tr>" for r in rows) + "</table>"


def fit(names, avail=226, maxw=175, gap=6):
    """Ancho (mm) común para que todas las imágenes de una página quepan en `avail` mm de alto."""
    from PIL import Image
    ratio = sum(Image.open(IMG / n).height / Image.open(IMG / n).width for n in names)
    return min(maxw, round((avail - gap * len(names)) / ratio))


def to_pdf(body, out, tmp, clase=""):
    page = tmp / (out.stem + ".html")
    page.write_text(f'<!doctype html><meta charset="utf-8"><style>{CSS}</style><body class="{clase}">{body}</body>', encoding="utf-8")
    subprocess.run([EDGE, "--headless=new", "--disable-gpu", f"--user-data-dir={tmp / 'profile'}", "--no-pdf-header-footer",
                    f"--print-to-pdf={out}", page.as_uri()], check=True, timeout=180, capture_output=True)


# ------------------------------------------------------------------------------------- capturas originales
def preparar_capturas():
    """Las capturas originales ya están en evidencia/capturas/ (copiadas a mano desde la conversación)."""
    faltan = [n for n in ("vercel_request_logs.png", "app_1_avisame_cuando_baje.png", "app_2_seguimiento.png",
                          "app_3_historial.png", "app_4_alertas.png") if not (CAP / n).exists()]
    if faltan:
        raise SystemExit(f"Faltan capturas en {CAP}: {faltan}")


# ------------------------------------------------------------------------------------- documentación de la API
def real(i):
    j = json.loads((EV / "raw" / "newman.json").read_text(encoding="utf-8"))
    return json.loads(bytes(j["run"]["executions"][i - 1]["response"]["stream"]["data"]).decode("utf-8"))


def jtxt(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2)


def doc_api():
    crear, repetido, invalido = real(1), real(2), real(3)
    check = real(16)
    check["seguimiento"] = {"id": check["seguimiento"]["id"], "estado": check["seguimiento"]["estado"], "...": "(seguimiento completo, abreviado)"}
    hist = real(18)
    hist["puntos"] = hist["puntos"][:2] + ["..."]
    alertas = real(20)
    endpoints = [
        (m("POST"), "/watches", "Seguir un producto (objetivo opcional)", "Cuerpo: <code>producto</code>, <code>precioObjetivo</code>", c(201), "400 401 409"),
        (m("GET"), "/watches", "Listar mis seguimientos", "Query: <code>estado</code>, <code>limit</code> (1–50), <code>offset</code>", c(200), "400 401"),
        (m("GET"), "/watches/{id}", "Detalle con mejor precio actual", "Ruta: <code>id</code> (UUID)", c(200), "400 401 404"),
        (m("PATCH"), "/watches/{id}", "Cambiar objetivo o pausar/reactivar", "Cuerpo: <code>precioObjetivo</code> o <code>estado</code>", c(200), "400 401 404 409 422"),
        (m("DELETE"), "/watches/{id}", "Dejar de seguir (borra sus alertas)", "Ruta: <code>id</code>", c(204), "401 404"),
        (m("POST"), "/watches/{id}/check", "Revisar precios ahora (operación de negocio)", "Ruta: <code>id</code>", c(200), "401 404 409 429 502"),
        (m("GET"), "/watches/{id}/history", "Historial y estadísticas", "Query: <code>days</code> (1–365)", c(200), "400 401 404"),
        (m("GET"), "/alerts", "Alertas del usuario", "Query: <code>unread</code>, <code>limit</code>, <code>offset</code>", c(200), "400 401"),
        (m("PATCH"), "/alerts/{id}", "Marcar alerta leída/no leída", "Cuerpo: <code>leida</code> (boolean)", c(200), "400 401 404"),
        (m("GET") + " " + m("POST"), "/internal/check-due", "Revisión automática (cron diario)", "Cabecera: <code>Bearer CRON_SECRET</code>", c(200), "401"),
    ]
    p1 = f"""<div><h1>Documentación de la API REST · Seguimiento de precios</h1>
<div class="sub">Taller 3 · SI806 · {AUTORES} · {REPO} · versión 1.0 (<code>/api/v1</code>)</div>
<h2>Información general</h2>
<table><tr><td width="22%"><b>URL base (producción)</b></td><td><code>{PROD}</code></td></tr>
<tr><td><b>URL base (local)</b></td><td><code>http://localhost:3000/api/v1</code> (<code>npm run dev:api</code>; ver README)</td></tr>
<tr><td><b>Formato</b></td><td>JSON UTF-8 en peticiones y respuestas (<code>Content-Type: application/json</code>)</td></tr>
<tr><td><b>Autenticación</b></td><td><code>Authorization: Bearer &lt;token de sesión de Clerk&gt;</code>. El dueño de cada recurso es el <code>sub</code> del token. Solo en local, con <code>AUTH_DEV_MODE=1</code>, se acepta <code>X-Dev-User: nombre</code> (jamás funciona en Vercel).</td></tr>
<tr><td><b>Errores</b></td><td>Siempre <code>{{ "error": "texto", "codigo": "codigo_estable", "detalles": [{{ "campo", "mensaje" }}] }}</code>. Un recurso de otra persona responde 404 (no 403).</td></tr>
<tr><td><b>Documentación completa</b></td><td><code>docs/api/openapi.yaml</code> (OpenAPI 3) y <code>docs/api/postman_collection.json</code> (26 peticiones, éxitos y fallos).</td></tr></table>
<h2>Endpoints</h2>
{table(["Método", "Ruta", "Qué hace", "Parámetros", "Éxito", "Errores"], [[a, f"<code>{b}</code>", cc, d, ee, f] for a, b, cc, d, ee, f in endpoints])}
<h2>Códigos HTTP</h2>
{table(["Código", "Cuándo"], [
    ["200 / 201 / 204", "Éxito / creado (con <code>Location</code>) / eliminado sin cuerpo"],
    ["400", "Validación (Zod): campo desconocido, UUID mal formado, rango inválido; <code>detalles</code> indica el campo"],
    ["401", "Falta la sesión, es inválida o el secreto del cron es incorrecto"],
    ["404 · 405", "No existe o es de otra persona · método no permitido (cabecera <code>Allow</code>)"],
    ["409", "<code>ya_seguido</code>, <code>limite_seguimientos</code> (máx. 20 activos), <code>seguimiento_pausado</code>, <code>transicion_invalida</code>"],
    ["422", "Regla de negocio (p. ej. reactivar un seguimiento alcanzado sin bajar el objetivo)"],
    ["429", "Una revisión cada 15 minutos por seguimiento; trae <code>Retry-After</code> (segundos)"],
    ["500 · 502 · 503", "Error interno sin detalles · las tiendas no respondieron (no se guarda nada) · sin base de datos"],
])}</div>"""
    p2 = f"""<div><h1 style="margin-top:0;break-before:page">Ejemplos de peticiones y respuestas</h1>
<div class="sub">Las respuestas son reales: salen de la corrida de la colección de Postman (Newman) del 2026-11-08.</div>
<h2>{m("POST")} /watches · Seguir un producto</h2>
{pre(jtxt({"producto": {"marca": "Free Tea", "nombre": "Frutos Rojos", "presentacion": "500ml", "categoria": "bebidas", "tipo": "té"}, "precioObjetivo": 2.5}))}
<p><b>Respuesta {c(201)}</b> (encabezado <code>Location: /api/v1/watches/{{id}}</code>)</p>{pre(jtxt(crear))}
<p><b>Respuesta {c(409)}</b> al repetir el producto:</p>{pre(jtxt(repetido))}
<p><b>Respuesta {c(400)}</b> con datos inválidos (<code>nombre</code> vacío, categoría inexistente, objetivo ≤ 0):</p>{pre(jtxt(invalido))}
<h2>{m("PATCH")} /watches/{{id}} · Cambiar el objetivo o pausar</h2>
{pre(jtxt({"precioObjetivo": 2}))}
<p>Respuesta {c(200)}: el seguimiento completo, con <code>precioObjetivo: 2</code>. Para pausar: <code>{{ "estado": "pausada" }}</code>.
Un estado que el usuario no puede pedir (por ejemplo <code>alcanzada</code>) devuelve {c(400)}.</p>
<h2>{m("DELETE")} /watches/{{id}} · Dejar de seguir</h2>
<p>Respuesta {c(204)} sin cuerpo. Borra el seguimiento y sus alertas; el historial de precios del producto se conserva. Repetirlo devuelve {c(404)}.</p></div>"""
    p3 = f"""<div><h1 style="margin-top:4mm">Operación de negocio, historial y alertas</h1>
<h2>{m("POST")} /watches/{{id}}/check · Revisar precios ahora</h2>
<p>Consulta las tiendas, guarda las observaciones y evalúa las reglas en una sola transacción (objetivo alcanzado, mínimo histórico, descarte de precios atípicos).</p>
<p><b>Respuesta {c(200)}</b> (seguimiento abreviado):</p>{pre(jtxt(check))}
<p><b>Respuesta {c(429)}</b> si se repite antes de 15 minutos (encabezado <code>Retry-After: 900</code>):</p>
{pre(jtxt(real(17)))}
<h2>{m("GET")} /watches/{{id}}/history?days=30 · Historial y estadísticas</h2>
<p>Respuesta {c(200)} (puntos abreviados). <code>days</code> fuera de 1–365 devuelve {c(400)}.</p>{pre(jtxt(hist))}
<h2>{m("GET")} /alerts · Alertas</h2>
<p>Respuesta {c(200)}; con <code>?unread=true</code> solo las no leídas:</p>{pre(jtxt(alertas))}
<h2>{m("PATCH")} /alerts/{{id}} · Marcar como leída</h2>
{pre(jtxt({"leida": True}))}<p>Respuesta {c(200)}: la alerta con <code>"leida": true</code>.</p>
<h2>{m("GET")} /internal/check-due · Revisión automática</h2>
<p>La llama el cron diario de Vercel con <code>Authorization: Bearer CRON_SECRET</code>. Sin el secreto responde {c(401)}; con él, {c(200)}:</p>{pre(jtxt(real(24)))}</div>"""
    return p1 + p2 + p3


# ------------------------------------------------------------------------------------- demostración
def demo():
    W3 = fit(['supabase_02_seguimientos_watches.png', 'supabase_05_alertas.png'])
    W4 = fit(['supabase_03_observaciones_de_precio.png', 'supabase_04_cron_diario_automatico.png'])
    W5 = fit(['postman_00_resumen_26_peticiones.png'], avail=222)
    W6 = fit(['tests_01_integracion_postgres_real.png', 'postman_17_revisar_otra_vez_enseguida_429.png'], avail=214)

    def full(name, w):
        return f'<div class="fig"><img src="{uri(IMG / name)}" style="width:{w}mm"></div>'

    filas = [
        ("22:58:23", m("GET") + " /api/v1/watches", c(200), "Se abre la pestaña Seguimiento: la app carga la lista"),
        ("22:58:32", m("POST") + " /api/scan", c(200), "Foto del producto: se identifica y se comparan precios"),
        ("22:59:01", m("POST") + " /api/v1/watches", c(201), "«Seguir precio»: se crea el seguimiento (<b>cambio persistido</b>)"),
        ("23:00:28", m("POST") + " /api/v1/watches", c(409), "Seguir el mismo producto otra vez: <code>ya_seguido</code>"),
        ("23:00:42", m("POST") + " /api/v1/watches/{id}/check", c(200), "«Revisar ahora»: consulta tiendas y guarda precios (operación de negocio)"),
        ("23:01:10", m("POST") + " /api/v1/watches/{id}/check", c(429), "Revisar de nuevo enseguida: regla R3, una revisión cada 15 min"),
        ("23:01:03 · 23:01:31 · 23:02:27", m("PATCH") + " /api/v1/watches/{id}", c(200), "Tres actualizaciones: objetivo y pausar/reactivar"),
        ("23:01:45", m("PATCH") + " /api/v1/alerts/5", c(200), "Se marca la alerta como leída"),
        ("23:03:27", m("GET") + " /api/v1/watches/{id}/history", c(200), "«Ver historial»: mínimo, promedio y máximo"),
    ]
    p1 = f"""<div class="page"><h1>Demostración · cliente Android consumiendo la API propia</h1>
<div class="sub">Taller 3 · SI806 · {AUTORES} · {REPO} · prueba del 2026-11-08 contra producción (Vercel + Supabase)</div>
<p>La app Android llama a la API REST propia desplegada en Vercel y los cambios quedan guardados en Postgres (Supabase). La captura de abajo es el
registro de peticiones de Vercel (<i>Request Logs</i>) durante la prueba: cada fila es una llamada real de la app, con su método, ruta y código HTTP.</p>
<h2>Operaciones realizadas (hora Perú)</h2>
{table(["Hora", "Petición", "Código", "Qué se hizo en la app"], [[a, b, cc, d] for a, b, cc, d in filas])}
<div class="fig"><img src="{uri(CAP / 'vercel_request_logs.png')}" style="width:186mm"></div>
<div class="cap">Vercel · Request Logs del proyecto en producción (captura original). La fila naranja <code>GET 405</code> en <code>/api/scan</code> (23:08) es posterior a la prueba: una consulta con método no permitido.</div></div>"""
    p2 = f"""<div class="page"><h1>Pantallas de la app (celular)</h1>
<div class="sub">Capturas originales del dispositivo durante la misma prueba. Cada pantalla usa un endpoint de la API.</div>
<div class="cols">
<div><img src="{uri(CAP / 'app_1_avisame_cuando_baje.png')}"><b>1 · «Avísame cuando baje»</b><br>precio objetivo y «Seguir precio» → <code>POST /watches</code></div>
<div><img src="{uri(CAP / 'app_2_seguimiento.png')}"><b>2 · Seguimiento (2)</b><br>estado, objetivo y acciones → <code>GET /watches</code></div>
<div><img src="{uri(CAP / 'app_3_historial.png')}"><b>3 · Historial de precios</b><br>mínimo, promedio, máximo → <code>GET /watches/{{id}}/history</code></div>
<div><img src="{uri(CAP / 'app_4_alertas.png')}"><b>4 · Alertas</b><br>«Objetivo alcanzado» → <code>GET /alerts</code></div></div>
<p class="nota">Las pantallas 2 y 4 muestran los datos que devuelve la API: el producto con su estado («Activo», «Objetivo alcanzado»), el mejor precio y la alerta generada por la regla de negocio.</p></div>"""
    p3 = f"""<div class="page"><h1>Cambio persistido en la base de datos (1/2)</h1>
<div class="sub">Supabase en producción. Las horas coinciden con las de la página 1: creación 22:59:02, revisión 23:00:44, alerta leída 23:01:45.</div>
{full('supabase_02_seguimientos_watches.png', W3)}
{full('supabase_05_alertas.png', W3)}</div>"""
    p4 = f"""<div class="page"><h1>Cambio persistido en la base de datos (2/2)</h1>
<div class="sub">Historial de precios guardado por la revisión y por la revisión automática diaria.</div>
{full('supabase_03_observaciones_de_precio.png', W4)}
{full('supabase_04_cron_diario_automatico.png', W4)}</div>"""
    p5 = f"""<div class="page"><h1>Colección de Postman: 26 peticiones, éxitos y fallos</h1>
<div class="sub">Corrida completa contra la API v1 local (Docker + Postgres), con la colección entregada en <code>docs/api/postman_collection.json</code>.</div>
{full('postman_00_resumen_26_peticiones.png', W5)}</div>"""
    p6 = f"""<div class="page"><h1>Pruebas automáticas y regla de negocio</h1>
<div class="sub">13 pruebas de integración escriben en Postgres real; la suite completa son 199 pruebas. Abajo, el detalle de la regla R3 (429 con <code>Retry-After</code>).</div>
{full('tests_01_integracion_postgres_real.png', W6)}
{full('postman_17_revisar_otra_vez_enseguida_429.png', W6)}
<p class="nota">Las tablas de Supabase, Postman y pruebas son representaciones generadas a partir de salida real (cada imagen lo indica en su pie).
Las pantallas del celular y el registro de Vercel son capturas originales.</p></div>"""
    return p1 + p2 + p3 + p4 + p5 + p6


def main():
    preparar_capturas()
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        to_pdf(doc_api(), T3 / "Taller3_Documentacion_API.pdf", tmp, "doc")
        to_pdf(demo(), T3 / "Taller3_Demostracion_Evidencias.pdf", tmp)
    print("listo")


if __name__ == "__main__":
    main()
