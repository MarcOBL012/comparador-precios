"""Genera docs/arquitectura-eventos/Plan_Arquitectura_Eventos.pdf (HTML -> PDF con Edge headless).
Uso:  python -X utf8 -I docs/arquitectura-eventos/build_plan.py
"""
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "Plan_Arquitectura_Eventos.pdf"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

CSS = """
*{box-sizing:border-box}
body{margin:0;font-family:'Segoe UI',Arial,sans-serif;color:#1b1f27;font-size:9.4pt;line-height:1.42}
@page{size:A4;margin:14mm 13mm 15mm 13mm;@bottom-right{content:"Plan EDA · página " counter(page) " de " counter(pages);font-size:7.5pt;color:#6b7280}}
h1{font-size:15pt;margin:7mm 0 2mm;break-after:avoid;color:#111827;border-bottom:2.5px solid #e11d2a;padding-bottom:1.5mm}
h2{font-size:11pt;margin:5mm 0 1.5mm;color:#1f2937;break-after:avoid}
h3{font-size:9.8pt;margin:3.5mm 0 1mm;color:#374151;break-after:avoid}
p{margin:1.3mm 0}ul,ol{margin:1mm 0 1mm 5mm;padding:0}li{margin:.6mm 0}
code{font-family:Consolas,'Courier New',monospace;font-size:8.2pt;background:#f1f4f9;padding:0 2px;border-radius:3px}
pre{margin:1.5mm 0;font:7.8pt/1.35 Consolas,'Courier New',monospace;background:#f5f7fa;border:1px solid #e3e7ee;border-radius:4px;padding:2mm 3mm;white-space:pre-wrap;break-inside:avoid}
table{border-collapse:collapse;width:100%;font-size:8pt;margin:1.5mm 0 2.5mm}
th{background:#1f2937;color:#fff;text-align:left;padding:1.3mm 1.8mm;font-weight:600}
td{padding:1.2mm 1.8mm;border-bottom:1px solid #e3e7ee;vertical-align:top}
tr:nth-child(even) td{background:#fafbfc}
tr{break-inside:avoid}
.pb{break-before:page;margin-top:0}
.cover{background:#1f2937;color:#fff;padding:6mm 8mm 5mm;border-radius:3mm;margin-bottom:4mm}
.cover .k{font-size:8.5pt;letter-spacing:.12em;text-transform:uppercase;color:#fca5a5}
.cover .t{font-size:18.5pt;font-weight:700;line-height:1.15;margin:2mm 0}
.cover .s{font-size:10pt;color:#d1d5db}
.cover .m{font-size:8.5pt;color:#9ca3af;margin-top:3mm}
.box{border-left:4px solid #2563eb;background:#eff6ff;padding:2mm 3mm;margin:2mm 0;border-radius:0 3px 3px 0;break-inside:avoid}
.box.warn{border-color:#c2410c;background:#fff7ed}.box.ok{border-color:#15803d;background:#f0fdf4}.box.red{border-color:#b91c1c;background:#fef2f2}
.box b.h{display:block;font-size:8.3pt;text-transform:uppercase;letter-spacing:.06em;margin-bottom:.5mm}
.kpis{display:flex;gap:3mm;margin:2mm 0}.kpi{flex:1;border:1px solid #e3e7ee;border-radius:3px;padding:2mm 2.5mm;background:#fff}
.kpi b{display:block;font-size:15pt;color:#e11d2a;line-height:1.1}.kpi span{font-size:7.8pt;color:#5b6472}
.tag{display:inline-block;padding:0 4px;border-radius:3px;font-size:7.4pt;font-weight:700;color:#fff}
.n{background:#15803d}.m2{background:#2563eb}.d{background:#6b7280}.o{background:#c2410c}
.fig{margin:2mm 0;break-inside:avoid}.fig .cap{font-size:7.8pt;color:#5b6472;margin-top:1mm}
.toc{font-size:8.3pt;color:#374151;margin-top:3mm}
"""

# ------------------------------------------------------------------ helpers
def tbl(headers, rows, widths=None):
    cols = ""
    if widths:
        cols = "<colgroup>" + "".join(f'<col style="width:{w}%">' for w in widths) + "</colgroup>"
    head = "".join(f"<th>{h}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    return f"<table>{cols}<tr>{head}</tr>{body}</table>"


def box(kind, title, html):
    return f'<div class="box {kind}"><b class="h">{title}</b>{html}</div>'


def tag(txt, cls):
    return f'<span class="tag {cls}">{txt}</span>'


NEW, MOD, SAME = tag("NUEVO", "n"), tag("MODIFICA", "m2"), tag("SIN CAMBIO", "d")


# ------------------------------------------------------------------ SVG
def svg_open(w, h, pct=100):
    return (f'<svg viewBox="0 0 {w} {h}" width="{pct}%" xmlns="http://www.w3.org/2000/svg" font-family="Segoe UI, Arial, sans-serif" font-size="10.5">'
            '<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
            '<path d="M0 0L10 5L0 10z" fill="#374151"/></marker>'
            '<marker id="r" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
            '<path d="M0 0L10 5L0 10z" fill="#b91c1c"/></marker></defs>')


def sbox(x, y, w, h, text, fill="#fff", stroke="#374151", bold=False, color="#111827", dash=False, size=None):
    lines = text.split("\n")
    fs = size or 10.5
    y0 = y + h / 2 - (len(lines) - 1) * (fs * 0.62) + fs * 0.35
    tsp = "".join(f'<tspan x="{x + w / 2}" dy="{0 if i == 0 else fs * 1.25}">{t}</tspan>' for i, t in enumerate(lines))
    d = ' stroke-dasharray="4 3"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="{fill}" stroke="{stroke}" stroke-width="1.2"{d}/>'
            f'<text x="{x + w / 2}" y="{y0}" text-anchor="middle" fill="{color}" font-size="{fs}"{" font-weight=\"700\"" if bold else ""}>{tsp}</text>')


def arrow(x1, y1, x2, y2, label="", dash=False, red=False, lx=None, ly=None, anchor="middle"):
    col = "#b91c1c" if red else "#374151"
    d = ' stroke-dasharray="5 3"' if dash else ""
    s = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="1.3"{d} marker-end="url(#{"r" if red else "a"})"/>'
    if label:
        tx = lx if lx is not None else (x1 + x2) / 2
        ty = ly if ly is not None else (y1 + y2) / 2 - 4
        s += (f'<text x="{tx}" y="{ty}" text-anchor="{anchor}" fill="{col}" font-size="9.5" paint-order="stroke" stroke="#fff" stroke-width="4">{label}</text>')
    return s


def label(x, y, text, size=10, color="#374151", bold=False, anchor="start"):
    return f'<text x="{x}" y="{y}" fill="{color}" font-size="{size}" text-anchor="{anchor}"{" font-weight=\"700\"" if bold else ""}>{text}</text>'


def diagram_asis():
    s = svg_open(720, 330)
    s += sbox(10, 105, 150, 120, "App Android\n\nMainActivity (445 líneas)\ncoordina con\nLaunchedEffect", fill="#eff6ff", bold=False)
    s += f'<rect x="215" y="12" width="215" height="306" rx="8" fill="#f9fafb" stroke="#9ca3af" stroke-dasharray="5 3"/>'
    s += label(322, 29, "Vercel · funciones serverless (60 s)", 10, "#6b7280", True, "middle")
    s += sbox(230, 40, 185, 44, "POST /api/scan\nidentifica y busca, todo en una llamada", fill="#fff", size=9.5)
    s += sbox(230, 94, 185, 44, "/api/prices · insights\n/api/nearby-stores · /api/route", fill="#fff", size=9.5)
    s += sbox(230, 148, 185, 44, "/api/v1/* (10 endpoints)\nwatches · check · alerts", fill="#fff", size=9.5)
    s += sbox(230, 202, 185, 44, "Cron diario  check-due\nrevisa máx. 5 seguimientos", fill="#fff7ed", stroke="#c2410c", size=9.5)
    s += sbox(230, 256, 185, 50, "Escribe eventos y\nNADIE los lee", fill="#fef2f2", stroke="#b91c1c", color="#b91c1c", bold=True, size=9.5)
    # externos
    s += sbox(545, 20, 165, 34, "Gemini (identifica foto)", fill="#f3f4f6", size=9.5)
    s += sbox(545, 62, 165, 34, "Tiendas VTEX + Serper", fill="#f3f4f6", size=9.5)
    s += sbox(545, 104, 165, 34, "OSRM · FOSSGIS · VTEX pickup", fill="#f3f4f6", size=9.5)
    s += sbox(545, 170, 165, 150, "Postgres (Supabase)\n\nwatches · alerts\nprice_observations\ndomain_events  53 filas,\n0 procesadas\ndevices · push_deliveries\n(sin código)", fill="#fffbeb", size=9.5)
    s += arrow(160, 150, 228, 62, "HTTPS", lx=190, ly=95)
    s += arrow(160, 165, 228, 170)
    s += arrow(160, 180, 228, 116)
    s += arrow(415, 54, 543, 37)
    s += arrow(415, 70, 543, 78)
    s += arrow(415, 116, 543, 121)
    s += arrow(415, 170, 543, 220, "SQL", lx=482, ly=186)
    s += arrow(415, 224, 543, 250)
    s += arrow(415, 282, 543, 280, "", red=True)
    s += arrow(160, 205, 228, 205, "sin push", dash=True, red=True, lx=194, ly=198)
    return s + "</svg>"


def diagram_tobe():
    s = svg_open(720, 440, 86)
    # productores
    s += label(10, 16, "PRODUCTORES", 10, "#1d4ed8", True)
    s += sbox(10, 24, 150, 50, "API v1\nwatches · prices · devices", fill="#eff6ff", size=9.5)
    s += sbox(10, 84, 150, 50, "api/scan · prices\n(best-effort, no bloquea)", fill="#eff6ff", size=9.5)
    s += sbox(10, 144, 150, 50, "Scheduler\n(cron diario / pg_cron)", fill="#eff6ff", size=9.5)
    s += sbox(10, 204, 150, 50, "App Android\n(registro de token,\nconfirmaciones)", fill="#eff6ff", size=9.5)
    # bus
    s += label(210, 16, "BUS DE EVENTOS (Postgres)", 10, "#b45309", True)
    s += f'<rect x="200" y="24" width="165" height="230" rx="8" fill="#fffbeb" stroke="#b45309"/>'
    s += sbox(212, 38, 141, 62, "domain_events\n(outbox transaccional)\nenvelope versionado", fill="#fff", stroke="#b45309", size=9.5)
    s += sbox(212, 112, 141, 62, "event_deliveries\n1 fila por consumidor\nreintentos · DLQ", fill="#fff", stroke="#b45309", size=9.5)
    s += sbox(212, 186, 141, 56, "tópico = tipo de evento\nsuscripción = consumidor", fill="#fff", stroke="#b45309", dash=True, size=9)
    # dispatcher
    s += label(410, 16, "DESPACHADOR", 10, "#6d28d9", True)
    s += sbox(405, 24, 130, 150, "Dispatcher\n(función Vercel)\n\nreclama con\nSKIP LOCKED\nreintenta · backoff\npresupuesto 45 s", fill="#f5f3ff", stroke="#6d28d9", size=9.5)
    s += sbox(405, 186, 130, 68, "Disparadores:\nDB webhook · pg_cron\ndespacho inline", fill="#fff", stroke="#6d28d9", dash=True, size=9)
    # consumidores
    s += label(585, 16, "CONSUMIDORES", 10, "#15803d", True)
    cons = ["AlertEvaluator", "WatchChecker", "NotificationDispatcher", "CatalogLearner", "SignatureConsensus", "MetricsRecorder"]
    for i, c in enumerate(cons):
        s += sbox(580, 24 + i * 38, 130, 31, c, fill="#f0fdf4", stroke="#15803d", size=9.5)
    # flechas productores->bus
    for y in (49, 109, 169, 229):
        s += arrow(160, y, 198, 70 + (y - 49) * 0.45)
    s += arrow(365, 140, 403, 100, "entregas", lx=384, ly=112)
    s += arrow(535, 99, 578, 99)
    s += arrow(535, 150, 578, 220)
    s += arrow(470, 186, 470, 176, "")
    # efectos
    s += label(10, 292, "EFECTOS", 10, "#374151", True)
    s += sbox(200, 282, 165, 48, "alerts · price_observations\nproducts · signatures", fill="#fffbeb", stroke="#b45309", size=9.5)
    s += sbox(405, 282, 130, 48, "Firebase Cloud\nMessaging (push)", fill="#f3f4f6", size=9.5)
    s += arrow(645, 256, 367, 284, "escribe estado", lx=500, ly=262)
    s += arrow(645, 256, 535, 305)
    # android
    s += label(10, 360, "CLIENTE ANDROID", 10, "#1d4ed8", True)
    s += sbox(405, 368, 130, 56, "App Android\nAppEventBus", fill="#eff6ff", size=9.5)
    s += arrow(470, 330, 470, 366, "push", lx=488, ly=352)
    s += sbox(570, 368, 140, 56, "Coordinators\nScan · Historial · Sync\nWorkManager", fill="#eff6ff", size=9.5)
    s += arrow(535, 396, 568, 396)
    s += sbox(200, 368, 165, 56, "Room (cola offline,\nhistorial, lista)\nsigue siendo local", fill="#fff", dash=True, size=9.5)
    s += arrow(568, 410, 367, 410, "persiste", dash=True, lx=466, ly=432)
    return s + "</svg>"


def diagram_seq():
    s = svg_open(720, 560)
    lanes = [("App Android", 55), ("API\nPOST /check", 175), ("Postgres\n(outbox)", 300), ("Dispatcher", 420), ("Consumidores", 540), ("FCM", 655)]
    for name, x in lanes:
        s += sbox(x - 48, 8, 96, 34, name, fill="#1f2937", stroke="#1f2937", color="#fff", bold=True, size=9.5)
        s += f'<line x1="{x}" y1="42" x2="{x}" y2="545" stroke="#9ca3af" stroke-dasharray="4 4"/>'
    X = dict(app=55, api=175, pg=300, dis=420, con=540, fcm=655)
    steps = [
        (68, "app", "api", "1  POST /watches/{id}/check", False),
        (102, "api", "pg", "2  BEGIN · ofertas + price.observed + watch.checked · COMMIT", False),
        (136, "api", "dis", "3  despacho inline (≤ 8 s)", False),
        (170, "dis", "pg", "4  reclama entregas (SKIP LOCKED)", False),
        (204, "dis", "con", "5  AlertEvaluator(watch.checked)", False),
        (238, "con", "pg", "6  alert + alert.created (misma tx)", False),
        (272, "dis", "api", "7  resultado: alertas nuevas", True),
        (306, "api", "app", "8  200 OK + alertasNuevas (contrato igual al actual)", True),
    ]
    for y, a, b, t, dash in steps:
        s += arrow(X[a], y, X[b], y, t, dash=dash, lx=(X[a] + X[b]) / 2, ly=y - 5)
    s += f'<rect x="10" y="335" width="700" height="24" rx="4" fill="#eef2ff"/>'
    s += label(360, 351, "Fase asíncrona: ocurre aunque la app esté cerrada", 10, "#3730a3", True, "middle")
    steps2 = [
        (388, "pg", "dis", "9  webhook / pg_cron: alert.created", False),
        (422, "dis", "con", "10  NotificationDispatcher(alert.created)", False),
        (456, "con", "fcm", "11  enviar push por dispositivo (push_deliveries)", False),
        (490, "fcm", "app", "12  notificación en el celular", True),
        (524, "app", "api", "13  al tocarla: GET /alerts → AlertReceived", True),
    ]
    for y, a, b, t, dash in steps2:
        s += arrow(X[a], y, X[b], y, t, dash=dash, lx=(X[a] + X[b]) / 2, ly=y - 5)
    return s + "</svg>"


# ------------------------------------------------------------------ contenido
def portada():
    return f"""
<div class="cover">
  <div class="k">Plan de migración · Desarrollo Adaptativo e Integrado de Software</div>
  <div class="t">Arquitectura orientada a eventos<br>para el Comparador de Precios</div>
  <div class="s">Backend (Vercel + Supabase) y app Android · basado en la Sesión 3 «Arquitectura orientada a eventos»</div>
  <div class="m">Versión 1.0 · 2026-11-23 · Estado: <b>propuesta para aprobación</b> — no se ha modificado ningún archivo del proyecto</div>
</div>

<h2>Resumen ejecutivo</h2>
<p>Hoy la app funciona de forma <b>síncrona y acoplada</b>: cada acción llama a una función que hace todo en una sola petición (identificar, buscar, guardar, decidir alertas) y la app Android coordina los pasos a mano. Este plan convierte los <b>efectos secundarios y el trabajo diferido</b> en eventos que un bus entrega a consumidores independientes, con reintentos, cola de fallos y notificaciones push, y reorganiza el cliente Android alrededor de un bus de eventos interno con procesamiento en segundo plano.</p>
<div class="kpis">
  <div class="kpi"><b>53 → 0</b><span>eventos en producción vs. procesados: se escriben y nadie los lee</span></div>
  <div class="kpi"><b>5</b><span>seguimientos máx. por corrida del cron (límite de 60 s, 1 vez al día)</span></div>
  <div class="kpi"><b>3</b><span>conjuntos de tablas ya creadas y sin código (push, dispositivos, firmas)</span></div>
  <div class="kpi"><b>445</b><span>líneas en MainActivity, que coordina flujos con LaunchedEffect</span></div>
</div>
<h3>Enfoque propuesto: híbrido, por etapas</h3>
<ul>
 <li><b>Se mueve a eventos:</b> revisión y vigilancia de precios, alertas, notificaciones, aprendizaje del catálogo, confirmaciones de la comunidad, métricas y la coordinación interna de la app Android.</li>
 <li><b>Se mantiene consulta-respuesta:</b> escaneo, insights, tienda cercana y ruta (la persona espera el resultado en pantalla; no hay beneficio en volverlos asíncronos). Emiten eventos «best-effort» pero no dependen de ellos. Hay una opción para llevarlos también a asíncrono (Fase 7).</li>
 <li><b>Bus de eventos:</b> el <i>outbox</i> de Postgres que ya existe (<code>domain_events</code>), ampliado con una tabla de entregas por consumidor. No se introduce Kafka ni RabbitMQ: no caben en Vercel serverless gratuito ni se justifican con este volumen (sección 5).</li>
 <li><b>Compatibilidad:</b> los 10 endpoints, las 26 peticiones de Postman, los PDF ya entregados y las 199 + 122 pruebas siguen siendo válidos. Cada fase es un Pull Request independiente con banderas para volver atrás.</li>
</ul>
<h3>Resultado esperado</h3>
<p>Una alerta de bajada de precio llega al celular <b>aunque la app esté cerrada</b>; una sola revisión de un producto beneficia a <b>todas las personas que lo siguen</b>; el cron deja de depender de un lote de 5; ningún fallo de una tienda, de la IA o de las notificaciones tumba al resto; y se puede ver cuánto tarda y qué falló en cada paso.</p>
<p><b>Esfuerzo estimado:</b> ≈ 21 jornadas de ~6 h para quien ya conoce el código (±30 %), más 3 opcionales. Las Fases 1–3 ya dan una demostración completa (alerta push de punta a punta).</p>
{box("warn", "Decisiones que necesito de ti antes de empezar", "<p>Hay 8 decisiones abiertas con mi recomendación en la <b>sección 14</b>. Las tres más importantes: alcance (híbrido o «todo a eventos»), cómo despertar al despachador (webhook de Supabase vs. servicio externo) y si las notificaciones usan Firebase (FCM) o sondeo con WorkManager.</p>")}
<p class="toc"><b>Contenido:</b> 1 Diagnóstico · 2 ¿Es candidata a EDA? · 3 Arquitectura objetivo · 4 Eventos (etapa 1) · 5 Bus (etapa 2) · 6 Productores, consumidores y procesamiento (etapas 3–4) · 7 Coreografía y orquestación (etapa 5) · 8 Estado y persistencia (etapa 6) · 9 Resiliencia (etapa 7) · 10 Monitoreo y pruebas (etapas 8–9) · 11 Cambios por archivo · 12 Base de datos · 13 Fases y rollback · 14 Riesgos y decisiones.</p>
"""


def s1():
    rows = [
        ["Escaneo", "<code>POST /api/scan</code> identifica con Gemini y busca en tiendas dentro de la misma petición; no guarda nada en el servidor.", "Un solo punto de fallo: si Gemini falla, todo falla (502). Sin rastro de lo ocurrido.", "<code>lib/scanHandler.ts:29-55</code>, <code>api/scan.ts</code>"],
        ["Revisión de precio", "<code>recordCheck</code> hace en una transacción: guardar ofertas, decidir alertas, cambiar estado y escribir eventos.", "Mezcla productor, procesador y consumidor en 70 líneas. Las alertas solo se evalúan para el seguimiento revisado, no para los demás que siguen el mismo producto.", "<code>lib/prices/repository.ts:74-142</code>"],
        ["Cron", "Revisa los seguimientos con más de 6 h sin revisar, <b>máximo 5 por corrida</b>, en bucle, <b>una vez al día</b> (<code>0 11 * * *</code>).", "Con más de 5 seguimientos activos, el resto espera al día siguiente. El límite sale de los 60 s de la función.", "<code>lib/prices/service.ts:11,132-150</code>, <code>vercel.json</code>"],
        ["Eventos existentes", "<code>watch.checked</code> y <code>alert.created</code> se insertan en <code>domain_events</code>.", "<b>Nadie los consume</b>: producción tiene 53 eventos, 0 procesados, 0 intentos. La tabla crece con cada revisión.", "<code>repository.ts:126-135</code>; consulta a Supabase del 2026-11-23"],
        ["Notificaciones", "Las tablas <code>devices</code> y <code>push_deliveries</code> existen desde la migración 003.", "Sin código ni endpoint. La app solo ve las alertas al abrir la pestaña y refrescar. Android no tiene FCM, WorkManager ni permiso de notificaciones.", "<code>db/migrations/003</code>, <code>app/build.gradle.kts</code>, <code>AndroidManifest.xml</code>"],
        ["Catálogo comunitario", "Tablas <code>identification_signatures</code>, <code>signature_confirmations</code> y la vista de consenso (migración 004).", "Sin uso. La confirmación «¿es este producto?» se queda solo en Room (<code>HistoryRepository.confirm</code>).", "<code>db/migrations/004</code>, <code>ProductConfirmationViewModel.kt</code>"],
        ["Seguimientos (CRUD)", "Crear, cambiar objetivo, pausar y borrar no emiten ningún evento.", "Sin auditoría de qué cambió y cuándo; imposible reaccionar a un cambio de objetivo.", "<code>lib/watches/service.ts</code>"],
        ["Android: orquestación", "<code>MainActivity</code> conecta pasos con <code>LaunchedEffect</code>: red→procesar cola; escaneo exitoso→guardar historial. Los ViewModels llaman repositorios directo.", "Lógica de flujo dentro de un composable de 445 líneas, difícil de probar. Sí hay buena base: <code>DeviceContext</code> y <code>CaptureConditions</code> ya son <code>Flow</code>.", "<code>MainActivity.kt:186-195,317-332</code>"],
        ["Android: cola offline", "Durable (Room + archivo), pero solo se procesa con la app abierta.", "Sin backoff ni límite de intentos; limitación ya anotada en el proyecto.", "<code>PendingScanProcessor.kt</code>"],
    ]
    return f"""<h1 class="pb">1. Diagnóstico del estado actual</h1>
<p>Lo siguiente sale de leer todo el backend (<code>api/</code>, <code>lib/</code>, <code>db/</code>), las capas clave de Android, y de consultar la base de producción.</p>
<div class="fig">{diagram_asis()}<div class="cap">Figura 1. Arquitectura actual: peticiones síncronas, efectos acoplados y una bandeja de eventos sin lector.</div></div>
{tbl(["Flujo", "Cómo funciona hoy", "Limitación o acoplamiento", "Evidencia"], rows, [11, 33, 37, 19])}
{box("ok", "Lo que ya tenemos a favor", "<ul><li><b>Outbox transaccional</b> ya implementado: el evento se guarda en la misma transacción que el cambio.</li><li><b>Idempotencia en la base</b>: <code>unique (observation_id, watch_id, alert_type_code)</code> en alertas, <code>primary key (alert_id, device_id)</code> en entregas push y <code>unique nulls not distinct</code> en productos.</li><li><b>Reglas puras</b> (<code>alertRules.decide</code>) que se pueden reutilizar tal cual como procesador.</li><li><b>Catálogo compartido</b>: el historial de precios es del producto, no del usuario (migración 002), lo que permite que una observación sirva a todos.</li><li>El servicio de precios ya recibe un reloj inyectable (<code>now</code>) y un proveedor intercambiable (<code>PriceProvider</code>): facilita probar reintentos y consumidores.</li></ul>")}"""


def s2():
    rows = [
        ["Alta concurrencia y escalabilidad", "Parcial", "Muchas personas pueden seguir el mismo producto y una observación de precio sirve a todas. Hoy son pocas, pero el diseño debe permitir revisar en paralelo."],
        ["Desacoplamiento temporal", "<b>Sí</b>", "El aviso al celular no necesita enviarse en el mismo milisegundo que la revisión, y la app puede estar cerrada o sin red."],
        ["Abundancia de efectos secundarios", "<b>Sí</b>", "Una revisión dispara guardar observaciones, evaluar alertas de todos los seguidores, notificar, medir y aprender del catálogo."],
        ["Procesamiento en tiempo real", "Parcial", "Una alerta de precio es útil en minutos, no en milisegundos. «Casi tiempo real» (≤ 1 min) basta."],
        ["Consistencia eventual", "<b>Sí</b>", "La alerta puede aparecer unos segundos después. Lo crítico (observaciones + estado del seguimiento) sigue siendo atómico en una transacción."],
    ]
    keep = [
        ["<code>POST /api/scan</code>, <code>/api/prices</code>", "La persona espera el resultado en pantalla; el valor está en la respuesta inmediata.", "Emiten <code>scan.*</code> best-effort para el catálogo y métricas. Opcional: hacerlos asíncronos en la Fase 7."],
        ["<code>/api/insights</code>, <code>/api/nearby-stores</code>, <code>/api/route</code>", "Proxies sin estado hacia IA, VTEX y OSRM; no producen cambios que otros deban reaccionar.", "Se quedan como están. Mismo contrato."],
        ["CRUD de lectura (<code>GET /watches</code>, <code>/history</code>, <code>/alerts</code>)", "Consultas puras; no hay efecto que propagar.", "Sin cambios."],
    ]
    return f"""<h1>2. ¿Es la app candidata a arquitectura de eventos?</h1>
<p>La Sesión 3 (diapositiva 5) advierte que <i>«no todos los proyectos deben ser EDA»</i> y da cinco criterios. Así se aplican aquí:</p>
{tbl(["Criterio de la teoría", "¿Aplica?", "En esta app"], rows, [26, 9, 65])}
<p><b>Conclusión:</b> la app es candidata <b>parcial</b>. Las partes con efectos secundarios y trabajo diferido (precios, alertas, notificaciones, catálogo) ganan mucho; las consultas interactivas no. Por eso el plan es <b>híbrido</b>.</p>
<h3>Qué NO se migra (y por qué)</h3>
{tbl(["Parte", "Por qué se queda síncrona", "Qué hace en el plan"], keep, [26, 40, 34])}
{box("warn", "Fuera de alcance", "<p>Event sourcing completo (reconstruir todo el estado desde eventos), Kafka/RabbitMQ/EventBridge, microservicios separados, cambio de proveedor de IA y cambios visuales en la UI. Cada uno se explica en las secciones 5 y 8.</p>")}"""


def s3():
    comp = [
        ["Productores de eventos", "<code>WatchService</code>, <code>PriceRecorder</code>, <code>api/scan</code> (best-effort), <b>Scheduler</b> (cron) y la app Android (token, confirmaciones). Su única tarea es publicar el hecho."],
        ["Eventos", "Filas inmutables de <code>domain_events</code> con envelope: id, tipo, versión, fecha, agregado, <code>correlationId</code>, <code>causationId</code>, actor y carga útil."],
        ["Broker", "Postgres: <code>domain_events</code> (outbox) + <code>event_deliveries</code> (una fila por evento y consumidor). Interfaz <code>EventBus</code> con tópicos = tipo de evento."],
        ["Consumidores", "<code>AlertEvaluator</code>, <code>WatchChecker</code>, <code>NotificationDispatcher</code>, <code>CatalogLearner</code>, <code>SignatureConsensus</code>, <code>MetricsRecorder</code>. Cada uno con su propio estado de entrega."],
        ["Procesadores / filtros", "<code>OutlierFilter</code> (regla R6), agregación por revisión (<code>watch.checked</code>), <code>NotificationDeduper</code> (ventana de 6 h)."],
        ["Despachador", "Función Vercel que reclama entregas, invoca consumidores, aplica reintentos y registra resultados. No contiene lógica de negocio."],
    ]
    return f"""<h1>3. Arquitectura objetivo</h1>
<div class="fig">{diagram_tobe()}<div class="cap">Figura 2. Arquitectura orientada a eventos propuesta. Las líneas continuas son flujo de eventos; el cliente solo conoce el contrato HTTP y las notificaciones.</div></div>
<h3>Principios de diseño</h3>
<ol>
 <li><b>Outbox transaccional:</b> un cambio y su evento se guardan juntos o no se guarda nada (ya es así hoy).</li>
 <li><b>Entrega al-menos-una-vez + consumidores idempotentes:</b> un evento puede llegar dos veces; el resultado debe ser el mismo.</li>
 <li><b>Un estado de entrega por consumidor:</b> si las notificaciones fallan, las alertas y las métricas siguen su curso.</li>
 <li><b>Evento = hecho en pasado, inmutable y versionado</b> (<code>watch.target_changed</code>, no <code>cambiar_objetivo</code>), con correlación de punta a punta.</li>
 <li><b>Despachador «tonto»:</b> enruta, reintenta y registra; la lógica vive en los consumidores.</li>
 <li><b>Contratos externos intactos:</b> el cliente sigue hablando HTTP con las mismas rutas y respuestas.</li>
 <li><b>Apto para serverless:</b> sin procesos de larga duración; cada invocación tiene un presupuesto de tiempo (45 s de los 60 s disponibles).</li>
</ol>
<h3>Correspondencia con los componentes de la teoría (diapositiva 6)</h3>
{tbl(["Componente", "En este proyecto"], comp, [20, 80])}"""


def s4():
    be = [
        ["<code>watch.created</code>", "WatchService.create", "watchId, productId, precioObjetivo", "WatchChecker (primera revisión), MetricsRecorder", NEW],
        ["<code>watch.target_changed</code>", "WatchService.update", "watchId, anterior, nuevo", "AlertEvaluator (re-evalúa con el precio actual), MetricsRecorder", NEW],
        ["<code>watch.paused</code> / <code>watch.reactivated</code>", "WatchService.update", "watchId, actor", "MetricsRecorder", NEW],
        ["<code>watch.deleted</code>", "WatchService.remove", "watchId, productId", "MetricsRecorder", NEW],
        ["<code>watch.check_requested</code>", "Scheduler o <code>POST /check</code>", "watchId, motivo (manual · programada · objetivo)", "WatchChecker", NEW],
        ["<code>price.observed</code>", "PriceRecorder (tras OutlierFilter)", "observationId, productId, tienda, precio, fuente", "AlertEvaluator (fan-out a todos los que siguen el producto), MetricsRecorder", NEW],
        ["<code>watch.checked</code>", "PriceRecorder", "watchId, ofertas, descartadas, mejorPrecio, observationIds", "AlertEvaluator, MetricsRecorder", "<span class='tag o'>EXISTE</span>"],
        ["<code>alert.created</code>", "AlertEvaluator", "alertId, watchId, tipo", "NotificationDispatcher", "<span class='tag o'>EXISTE</span>"],
        ["<code>alert.read</code>", "PriceService.markAlert", "alertId", "MetricsRecorder", NEW],
        ["<code>notification.sent</code> / <code>.failed</code>", "NotificationDispatcher", "alertId, deviceId, error", "MetricsRecorder, DeviceRevoker (token inválido)", NEW],
        ["<code>device.registered</code> / <code>.revoked</code>", "DeviceController", "deviceId, plataforma", "MetricsRecorder", NEW],
        ["<code>user.registered</code>", "ensureUser (primera vez)", "userId", "MetricsRecorder", NEW],
        ["<code>scan.identified</code>", "api/scan (best-effort)", "marca, nombre, presentación, categoría, confianza, modeloMs", "CatalogLearner, SignatureConsensus, MetricsRecorder", NEW],
        ["<code>scan.low_confidence</code>", "api/scan (best-effort)", "confianza", "MetricsRecorder", NEW],
        ["<code>identification.confirmed</code> / <code>.corrected</code>", "<code>POST /identifications/confirm</code> (nuevo)", "firma, productId, veredicto", "SignatureConsensus", NEW],
        ["<code>signature.verified</code>", "SignatureConsensus", "firma, productId, votos", "api/scan (atajo: salta Gemini si la firma ya está verificada)", NEW],
    ]
    cli = [
        ["<code>ContextChanged</code>", "DeviceContextMonitor (ya es un Flow)", "Red, batería, ahorro de energía", "ScanCoordinator, PendingScanWorker", SAME],
        ["<code>ScanRequested</code>", "ScanScreen / ScanViewModel", "uri de imagen, categoría", "ScanCoordinator", NEW],
        ["<code>ScanQueued</code>", "ScanCoordinator (sin red)", "pendingId", "UI (aviso), PendingScanWorker", NEW],
        ["<code>ScanCompleted</code> / <code>ScanFailed</code>", "ScanCoordinator / PendingScanWorker", "identificación, tiendas · motivo", "HistoryRecorder, ScanViewModel", NEW],
        ["<code>WatchFollowed</code> / <code>WatchesChanged</code>", "WatchesViewModel", "watchId", "WatchesViewModel (otras pantallas)", NEW],
        ["<code>AlertReceived</code>", "PushService o AlertsSyncWorker", "alertId, mensaje", "WatchesViewModel (badge y lista), notificación local", NEW],
        ["<code>SessionExpired</code>", "ScanRepository / Worker (401)", "—", "MainActivity (cerrar sesión)", NEW],
    ]
    return f"""<h1>4. Etapa 1 · Identificación de eventos y componentes</h1>
<p>Convención de nombres: <code>&lt;agregado&gt;.&lt;hecho_en_pasado&gt;</code>, que ya es la que exige la restricción de la base (<code>event_type ~ '^[a-z_]+\\.[a-z_]+$'</code>). Cargas útiles de máximo 8 KB y <b>sin datos sensibles</b> (nunca imágenes, tokens ni correos).</p>
<h2>Eventos de dominio del servidor</h2>
{tbl(["Evento", "Productor", "Carga útil (campos clave)", "Consumidores", "Hoy"], be, [19, 17, 25, 29, 10])}
<h2>Eventos internos de la app Android</h2>
<p>Viven en memoria (un <code>SharedFlow</code>); las partes que deben sobrevivir al cierre de la app ya son durables en Room (cola de escaneos pendientes).</p>
{tbl(["Evento", "Productor", "Carga útil", "Consumidores", "Hoy"], cli, [20, 22, 22, 26, 10])}
{box("", "Envelope común (versionado)", "<pre>{ id, type, version: 1, occurredAt, aggregateType, aggregateId,\n  correlationId,   // id de la petición HTTP que lo originó\n  causationId,     // id del evento que lo causó (cadena de reacciones)\n  actor,           // 'user:&lt;id&gt;' | 'system'\n  payload }        // validado con zod según (type, version)</pre>")}"""


def s5():
    opts = [
        ["<b>A.</b> Outbox + sondeo por cron", "Un cron lee la bandeja y despacha.", "Cero infraestructura nueva.", "Vercel gratuito solo permite cron <b>diario</b>: latencia de horas.", "Insuficiente sola"],
        ["<b>B.</b> Outbox + disparo por la base <span class='tag n'>RECOMENDADA</span>", "Un <i>Database Webhook</i> o <code>pg_net</code> llama al despachador cuando se inserta un evento; <code>pg_cron</code> cada minuto como red de seguridad; y despacho <i>inline</i> tras el commit para la petición actual.", "Sin cuentas ni costos nuevos; latencia de 1–5 s; conserva la garantía transaccional.", "Hay que habilitar extensiones en Supabase (verificar disponibilidad en el plan gratuito, spike de la Fase 0).", "Recomendada"],
        ["<b>C.</b> Servicio externo gestionado (QStash, Inngest, Trigger.dev)", "Colas HTTP con reintentos gestionados.", "Reintentos y programación ya resueltos.", "Cuenta y límites nuevos; el evento viaja fuera de la base y se pierde la transacción única.", "Plan B"],
        ["<b>D.</b> Broker clásico (Kafka, RabbitMQ, EventBridge)", "Los que cita la teoría.", "Ecosistema maduro, streaming real.", "Requiere un servidor siempre encendido o una cuenta de pago; incompatible con funciones serverless de 60 s; muy sobredimensionado para decenas de eventos al día.", "Descartada"],
    ]
    return f"""<h1>5. Etapa 2 · Diseño del bus de eventos</h1>
<p>La teoría distingue <b>pub/sub</b> (productores publican en tópicos, consumidores se suscriben) de <b>event streaming</b> (registro ordenado que se puede releer). Para este proyecto basta <b>pub/sub con registro durable</b>: el outbox ya es ese registro y permite releer si hace falta.</p>
{tbl(["Opción", "Cómo funciona", "Ventaja", "Inconveniente", "Veredicto"], opts, [19, 27, 18, 27, 9])}
<h3>Cómo se ve el pub/sub en el código</h3>
<ul>
 <li><b>Tópico</b> = tipo de evento (<code>price.observed</code>). <b>Suscripción</b> = nombre de consumidor. Una sola tabla de registro en código (<code>lib/events/subscriptions.ts</code>) dice qué consumidor escucha qué tipo.</li>
 <li><b>Fan-out al escribir:</b> al publicar un evento se insertan, en la <i>misma transacción</i>, una fila de <code>event_deliveries</code> por cada consumidor suscrito. Así cada consumidor tiene su propio estado, intentos y cola de fallos.</li>
 <li><b>Interfaz</b> <code>EventBus.publish(tx, evento)</code>, con dos implementaciones: <code>PgEventBus</code> (producción) e <code>InMemoryEventBus</code> (pruebas unitarias sin base de datos, igual que hoy se usa un repositorio en memoria).</li>
 <li><b>Qué despierta al despachador</b> (en orden de preferencia): ① despacho inline acotado tras el commit, ② webhook de la base al insertar, ③ <code>pg_cron</code> cada minuto, ④ cron diario de Vercel como último recurso. Si Supabase no ofrece ②/③ en el plan gratuito, el plan B es un workflow de GitHub Actions programado cada 5 min que llame al endpoint (gratis) o la opción C.</li>
</ul>
{box("warn", "Por qué no Kafka o RabbitMQ aunque la teoría los menciona", "<p>La arquitectura (productores, bus, consumidores) es la misma; cambia solo la tecnología del bus. Vercel gratuito no permite procesos permanentes, y un broker externo agrega costo y operación sin aportar nada a decenas de eventos diarios. Quedará documentado que el <code>EventBus</code> es una interfaz: si algún día crece, se reemplaza <code>PgEventBus</code> sin tocar productores ni consumidores.</p>")}"""


def s6():
    cons = [
        ["<b>AlertEvaluator</b>", "<code>watch.checked</code>, <code>watch.target_changed</code>", "Para cada seguimiento activo del producto aplica <code>alertRules.decide</code> (sin cambios), crea la alerta, pasa a «alcanzada» y emite <code>alert.created</code>.", "<code>unique (observation_id, watch_id, alert_type_code)</code> ✔ ya existe", "6"],
        ["<b>WatchChecker</b>", "<code>watch.check_requested</code>", "Consulta tiendas (<code>PriceProvider</code>) y registra la revisión (hoy <code>execute</code>).", "Bloqueo <code>for update</code> + ventana R3 por <code>last_checked_at</code>", "4"],
        ["<b>NotificationDispatcher</b>", "<code>alert.created</code>", "Por cada dispositivo no revocado crea <code>push_deliveries</code> y envía el push por FCM; token inválido → <code>device.revoked</code>.", "<code>primary key (alert_id, device_id)</code> ✔ ya existe", "5"],
        ["<b>CatalogLearner</b>", "<code>scan.identified</code>", "Registra producto, marca y tipo sin duplicar.", "<code>unique nulls not distinct</code> ✔ ya existe", "3"],
        ["<b>SignatureConsensus</b>", "<code>identification.confirmed</code> / <code>.corrected</code>", "Guarda el voto, recalcula el consenso y, si queda verificado (≥ 2 votos y ≥ 66 %), emite <code>signature.verified</code>.", "<code>primary key (signature_id, user_id)</code> ✔ ya existe", "3"],
        ["<b>MetricsRecorder</b>", "todos los tipos", "Contadores, latencias y registro JSON estructurado.", "<code>event_id</code>", "1"],
    ]
    return f"""<h1>6. Etapas 3 y 4 · Productores, consumidores y procesamiento</h1>
<h2>Etapa 3 · Consumidores</h2>
<p>Todos reciben <code>(evento, contexto)</code>, son idempotentes y declaran sus tópicos. Cuatro de las seis claves de idempotencia <b>ya existen</b> como restricciones de la base, por lo que el diseño actual ya estaba pensado para esto.</p>
{tbl(["Consumidor", "Se suscribe a", "Efecto", "Clave de idempotencia", "Intentos"], cons, [14, 17, 34, 28, 7])}
<h3>Productores</h3>
<ul>
 <li><b>PriceRecorder</b> (lo que hoy es <code>recordCheck</code>): guarda ofertas ya filtradas y publica <code>price.observed</code> y <code>watch.checked</code> dentro de la misma transacción, conservando el <code>for update</code> que hoy evita revisiones simultáneas.</li>
 <li><b>CheckScheduler</b> (lo que hoy es <code>checkDue</code>): en vez de revisar en un bucle de 5, publica un <code>watch.check_requested</code> por cada seguimiento vencido (con clave de deduplicación por seguimiento y hora). Cada revisión corre independiente, con sus propios reintentos.</li>
 <li><b>WatchService</b>: publica <code>watch.*</code> dentro de la transacción de cada cambio.</li>
</ul>
<h2>Etapa 4 · Procesamiento de eventos</h2>
{tbl(["Técnica (teoría)", "Aplicación en la app", "Beneficio"], [
    ["<b>Filtro</b> (event processor)", "<code>OutlierFilter</code>: la regla R6 (descartar precios &lt; 40 % de la mediana) pasa de estar dentro del guardado a ser un procesador previo a <code>price.observed</code>. Reutiliza la lógica pura existente.", "Se prueba por separado; el guardado queda simple."],
    ["<b>Agregación</b>", "Varias <code>price.observed</code> de una revisión se agrupan en <code>watch.checked</code> (con <code>mejorPrecio</code> y <code>observationIds</code>), así las alertas se evalúan una vez por revisión.", "Una alerta por revisión, no por oferta."],
    ["<b>Correlación</b>", "Una observación de un producto se correlaciona con <b>todos</b> los seguimientos activos de ese producto (índice <code>idx_watches_product_id</code> ya existe).", "Si Ana revisa, Luis también recibe su alerta: aprovecha el historial compartido de la migración 002."],
    ["<b>Ventanas / deduplicación</b>", "<code>NotificationDeduper</code>: máximo 1 push por seguimiento cada 6 h, salvo que sea un nuevo mínimo histórico.", "Evita spam de notificaciones."],
    ["<b>CEP ligero</b> <span class='tag d'>OPCIONAL</span>", "Patrón «3 bajadas seguidas en 7 días» → <code>price.trend_down</code>; subida anómala &gt; X % → marcar el listado como sospechoso.", "Detecta tendencias sin tocar los consumidores existentes."],
    ["<b>Tiempo real vs. lotes</b>", "Alertas: casi tiempo real (≤ 1 min). Retención, limpieza y reportes: por lotes diarios.", "Cada cosa en su ritmo."],
], [18, 52, 30])}"""


def s7():
    return f"""<h1>7. Etapa 5 · Orquestación y coreografía</h1>
<p><b>Regla general: coreografía.</b> Cada servicio reacciona a eventos sin conocer al siguiente. La cadena principal queda así:</p>
<pre>watch.check_requested → WatchChecker → price.observed + watch.checked
   → AlertEvaluator → alert.created → NotificationDispatcher → notification.sent</pre>
<p>Agregar un consumidor nuevo (por ejemplo, un correo) no exige tocar a ningún productor: solo se suscribe a <code>alert.created</code>.</p>
<div class="fig">{diagram_seq()}<div class="cap">Figura 3. Secuencia de «Revisar ahora». La parte superior mantiene el contrato actual de la API; la inferior ocurre aunque la app esté cerrada.</div></div>
<h3>Dónde sí se usa orquestación (y por qué)</h3>
<ul>
 <li><b>El despachador:</b> control centralizado del presupuesto de tiempo, del orden por agregado (los eventos de un mismo seguimiento se procesan en orden con un bloqueo asesor) y de los reintentos. No decide negocio.</li>
 <li><b>En Android, <code>ScanCoordinator</code>:</b> captura → ¿hay red? → identificar o encolar → resultado → historial son pasos de experiencia de usuario que deben ocurrir en orden y con compensación (si falla, la foto queda en la cola). Es un orquestador pequeño y localizado.</li>
</ul>
{box("", "Por qué el «Revisar ahora» mantiene el despacho inline", "<p>Hoy la app muestra enseguida la alerta que salta al revisar (<code>result.alertasNuevas</code>). Si todo fuera asíncrono, la respuesta ya no traería alertas y habría que cambiar contrato y pantalla. Con el despacho inline acotado a 8 s, la API devuelve lo mismo que hoy; si no alcanza el tiempo, responde sin alertas y la persona las recibe por push o al sincronizar. <b>Cero cambios visibles para el cliente actual.</b></p>")}"""


def s8():
    return f"""<h1>8. Etapa 6 · Gestión del estado y persistencia de eventos</h1>
<h3>Postura sobre event sourcing</h3>
<p>La teoría presenta <i>event sourcing</i> (reconstruir el estado desde los eventos) como una opción. Para esta app propongo <b>«estado + registro de eventos»</b>, no event sourcing completo:</p>
<ul>
 <li>Las tablas de estado (<code>watches</code>, <code>alerts</code>, <code>price_observations</code>) siguen siendo la fuente de verdad. Reconstruirlas desde eventos obligaría a reescribir los repositorios y el trigger de transiciones de estado, sin beneficio proporcional.</li>
 <li><code>price_observations</code> <b>ya es un registro de hechos append-only</b>: el historial de precios es, en la práctica, event sourcing de precios.</li>
 <li><code>domain_events</code> aporta auditoría y la capacidad de <b>reejecutar</b> consumidores (reconstruir una proyección, reenviar notificaciones perdidas) con <code>npm run events:replay -- --consumer X --desde &lt;id&gt;</code>.</li>
</ul>
<h3>Qué se guarda y por cuánto tiempo</h3>
{tbl(["Dato", "Dónde", "Retención"], [
    ["Evento (envelope + carga)", "<code>domain_events</code>", "30 días tras quedar entregado a todos; luego se borra (el plan gratuito de Supabase tiene un tope de almacenamiento, a verificar en el panel)."],
    ["Estado de entrega por consumidor", "<code>event_deliveries</code> (nueva)", "Se borra en cascada con el evento."],
    ["Eventos muertos (DLQ)", "<code>event_deliveries.status = 'dead'</code>", "Se conservan hasta resolverlos manualmente; no entran en la purga."],
    ["Historial de precios", "<code>price_observations</code>", "Sin cambio (es el valor del producto)."],
    ["Eventos del cliente", "<code>SharedFlow</code> en memoria", "No se persisten; lo que debe sobrevivir ya está en Room."],
], [27, 28, 45])}
<h3>Estado en Android</h3>
<p>Room pasa de la versión 4 a la 5: <code>pending_scans</code> gana <code>attempts</code> y <code>nextAttemptAt</code> para reintentos con backoff y un máximo de intentos (hoy una foto fallida se queda indefinidamente). La migración sigue el patrón de <code>MIGRATION_3_4</code> y se prueba con <code>AppDatabaseMigrationTest</code>.</p>
<h3>Consistencia</h3>
<ul>
 <li><b>Atómico (una transacción):</b> ofertas + eventos + última revisión (hoy ya es así).</li>
 <li><b>Eventual (segundos):</b> alertas derivadas, notificaciones, catálogo, métricas.</li>
 <li><b>Orden:</b> garantizado solo <i>por agregado</i> (todos los eventos de un seguimiento, en orden); no hay orden global, y no hace falta.</li>
</ul>"""


def s9():
    return f"""<h1>9. Etapa 7 · Gestión de errores y resiliencia</h1>
{tbl(["Mecanismo", "Diseño", "Valores iniciales"], [
    ["<b>Reintentos con backoff</b>", "Si un consumidor lanza un error temporal (timeout, 5xx, 429), se reprograma <code>next_attempt_at</code> con espera exponencial y variación aleatoria.", "30 s, 1 min, 2, 4, 8 y 16 min (±20 %), máximo <b>6 intentos</b>."],
    ["<b>Errores permanentes</b>", "Los de validación o 4xx no se reintentan: van directo a «muerto» para no gastar intentos en algo que no se arreglará solo.", "Clasificación en <code>retryPolicy.ts</code>."],
    ["<b>Cola de fallos (DLQ)</b>", "Entregas con <code>status = 'dead'</code>. Se listan en el endpoint de estadísticas y se reponen con <code>POST /internal/events/{{id}}/retry</code>.", "Alerta cuando hay ≥ 1 muerto."],
    ["<b>Circuit breaker</b>", "Por dependencia externa (tiendas, FCM, Gemini): si falla 5 veces en 2 min se «abre» y los consumidores aplazan sus entregas en lugar de golpear un servicio caído; prueba de nuevo a los 60 s. El estado se guarda en la base porque las funciones serverless no comparten memoria.", "Umbral 5 fallos / 2 min; medio-abierto a 60 s."],
    ["<b>Arrendamiento (lease)</b>", "Al reclamar una entrega se marca <code>locked_until</code>. Si la función muere a mitad (timeout de 60 s), otra instancia la recupera cuando vence el arrendamiento.", "Lease de 60 s."],
    ["<b>Presupuesto de tiempo</b>", "El despachador deja de reclamar entregas a los 45 s para terminar limpio antes de los 60 s de Vercel; lo pendiente lo toma la siguiente invocación.", "45 s de 60 s; lotes de 20."],
    ["<b>Idempotencia</b>", "Cada consumidor tolera recibir dos veces el mismo evento (restricciones únicas ya existentes + clave de deduplicación en el scheduler).", "Probado con «ejecutar dos veces»."],
    ["<b>Mensaje venenoso</b>", "Una entrega que falla siempre no bloquea las demás: cada consumidor y cada evento tienen estado propio.", "Aislamiento por fila."],
    ["<b>Android: cola offline</b>", "WorkManager con restricción de red conectada y backoff exponencial; tope de 5 intentos y mensaje visible al agotarlos.", "Hoy: sin límite ni backoff."],
], [17, 58, 25])}
{box("red", "Caso real que este diseño habría contenido", "<p>El error actual de «No se pudo identificar el producto» (502 de Gemini) hoy llega tal cual a la persona. Con un circuit breaker sobre Gemini, las llamadas se cortan rápido cuando el servicio está caído o sin cuota, y con firmas verificadas (<code>signature.verified</code>) los productos ya confirmados por la comunidad se identifican sin llamar a la IA.</p>")}"""


def s10():
    metr = [
        ["Retraso (lag)", "Segundos desde el evento pendiente más antiguo", "&gt; 15 min"],
        ["Pendientes por consumidor", "Entregas en <code>pending</code> / <code>failed</code>", "creciendo 3 lecturas seguidas"],
        ["Tasa de error por consumidor", "Fallos / intentos en la última hora", "&gt; 20 %"],
        ["Muertos (DLQ)", "Entregas en <code>dead</code>", "≥ 1"],
        ["Duración p50 / p95", "Por consumidor, desde los registros", "p95 &gt; 20 s"],
        ["Rendimiento", "Eventos entregados por minuto", "informativo"],
    ]
    tests = [
        ["Contrato de eventos", "Un esquema zod por tipo y versión; prueba que <code>publish</code> rechaza cargas inválidas.", "≈ 17"],
        ["Productores (unitarias)", "Cada operación de <code>WatchService</code> y <code>PriceService</code> emite el evento correcto exactamente una vez, con <code>InMemoryEventBus</code>.", "≈ 15"],
        ["Consumidores idempotentes", "Ejecutar el mismo evento dos veces deja el mismo resultado.", "≈ 12"],
        ["Integración con Postgres real", "Outbox atómico (si la transacción falla no hay evento); dos despachadores simultáneos no duplican (SKIP LOCKED); orden por agregado; backoff con reloj falso; DLQ; replay. Extiende <code>test/integration/api.pg.test.ts</code>.", "≈ 12"],
        ["Resiliencia", "Un consumidor que siempre falla llega a «muerto» en 6 intentos; breaker abierto aplaza; función muerta a mitad → el lease recupera la entrega.", "≈ 6"],
        ["Carga", "Script que inserta 1 000 eventos y mide el drenado del despachador. Criterio: &lt; 5 min.", "1"],
        ["API / Postman", "Las 26 peticiones actuales sin cambios, más 4 nuevas (<code>/devices</code>, <code>/internal/events/stats</code>, <code>/retry</code>, <code>/identifications/confirm</code>).", "26 + 4"],
        ["Android (JVM)", "<code>AppEventBus</code> y coordinators con <code>kotlinx-coroutines-test</code>; <code>PendingScanWorker</code> con <code>work-testing</code>; migración Room 4→5.", "≈ 20"],
        ["Manual extremo a extremo", "Apagar la red y volver; push con la app cerrada; token inválido; despachador caído y recuperado.", "checklist"],
    ]
    return f"""<h1>10. Etapas 8 y 9 · Monitoreo y pruebas</h1>
<h2>Etapa 8 · Monitorización y optimización</h2>
<p>La teoría cita Prometheus y Grafana. Con este volumen propongo empezar con lo que ya tenemos (la base y los registros de Vercel) y dejar Prometheus/Grafana como opcional.</p>
{tbl(["Métrica", "Cómo se mide", "Umbral de alerta"], metr, [26, 48, 26])}
<ul>
 <li><b>Endpoint</b> <code>GET /api/v1/internal/events/stats</code> (protegido con <code>CRON_SECRET</code>): devuelve las métricas de arriba a partir de una vista SQL <code>v_event_stats</code>.</li>
 <li><b>Registros JSON estructurados</b> en cada entrega: <code>{{ts, nivel, eventId, tipo, consumidor, correlationId, intento, duracionMs, resultado}}</code>, buscables en Vercel Logs (que ya mostraron servir para esto en la demostración del Taller 3).</li>
 <li><b>Optimización:</b> ajustar tamaño de lote, intervalo de <code>pg_cron</code> y concurrencia por consumidor según las métricas reales.</li>
 <li><b>Opcional:</b> exponer <code>/metrics</code> en formato Prometheus y conectarlo a Grafana Cloud (gratis).</li>
</ul>
<h2>Etapa 9 · Pruebas y control de calidad</h2>
{tbl(["Nivel", "Qué se prueba", "Pruebas nuevas"], tests, [20, 66, 14])}
{box("ok", "Meta de calidad por Pull Request", "<p>Las 199 pruebas del backend y las 122 de Android deben seguir pasando en <b>cada</b> fase, junto con la corrida de las 26 peticiones de Postman. Esto garantiza que el contrato no se rompe mientras se migra.</p>")}"""


def s11():
    be = [
        [NEW, "<code>db/migrations/006_eventos_v2.sql</code>", "Columnas de envelope en <code>domain_events</code>, tabla <code>event_deliveries</code>, índices, RLS y marcado de los 53 eventos históricos como procesados.", "Aditiva; reversible borrando lo nuevo."],
        [NEW, "<code>db/migrations/007_retencion_eventos.sql</code>", "Función <code>purge_old_events()</code> y (si hay <code>pg_cron</code>) su programación diaria.", "Bajo."],
        [NEW, "<code>lib/events/catalog.ts</code>", "Tipos de evento + esquema zod + versión.", "Bajo."],
        [NEW, "<code>lib/events/bus.ts</code>", "Interfaz <code>EventBus</code>; <code>PgEventBus</code> (publica en la transacción y crea las entregas) e <code>InMemoryEventBus</code>.", "Medio: es el corazón."],
        [NEW, "<code>lib/events/subscriptions.ts</code>", "Registro tópico → consumidores.", "Bajo."],
        [NEW, "<code>lib/events/dispatcher.ts</code> + <code>repository.ts</code>", "Reclamar (<code>FOR UPDATE SKIP LOCKED</code>), ejecutar, confirmar, fallar, marcar muerto, reponer, estadísticas.", "Medio-alto: concurrencia."],
        [NEW, "<code>lib/events/retryPolicy.ts</code>, <code>breaker.ts</code>", "Backoff con variación, clasificación de errores y circuit breaker.", "Medio."],
        [NEW, "<code>lib/events/consumers/*.ts</code>", "AlertEvaluator, WatchChecker, NotificationDispatcher, CatalogLearner, SignatureConsensus, MetricsRecorder.", "Medio."],
        [NEW, "<code>lib/events/scheduler.ts</code>", "CheckScheduler: publica <code>watch.check_requested</code> con deduplicación.", "Bajo."],
        [NEW, "<code>lib/devices/*</code>, <code>lib/notifications/fcm.ts</code>", "Registro y baja de tokens; proveedor FCM (HTTP v1) detrás de una interfaz <code>PushProvider</code> con versión falsa para pruebas.", "Medio: credenciales."],
        [NEW, "<code>lib/identifications/*</code>", "<code>POST /identifications/confirm</code> y consulta de firmas verificadas.", "Medio."],
        [MOD, "<code>lib/app.ts</code>", "Raíz de composición: construir bus, consumidores y rutas nuevas (<code>/devices</code>, <code>/internal/events/*</code>, <code>/identifications/confirm</code>).", "Bajo."],
        [MOD, "<code>lib/prices/repository.ts</code>", "<code>recordCheck</code> deja de decidir alertas: guarda ofertas filtradas y publica <code>price.observed</code> y <code>watch.checked</code> en la misma transacción. Conserva <code>for update</code>.", "<b>Alto</b>: es la pieza más delicada; el código viejo se conserva una versión detrás de bandera."],
        [MOD, "<code>lib/prices/service.ts</code>", "<code>check</code> despacha inline (≤ 8 s) y devuelve <code>alertasNuevas</code> como hoy; <code>checkDue</code> pasa a publicar <code>check_requested</code> (se elimina <code>CRON_BATCH_SIZE</code>).", "Medio."],
        [SAME, "<code>lib/prices/alertRules.ts</code>", "Sin cambios: se reutiliza como procesador puro.", "—"],
        [MOD, "<code>lib/watches/service.ts</code>, <code>repository.ts</code>", "Publicar <code>watch.*</code> dentro de la transacción de cada alta, cambio y baja.", "Medio: hoy cada método usa su propia conexión."],
        [MOD, "<code>lib/scanHandler.ts</code>, <code>api/scan.ts</code>, <code>api/prices.ts</code>", "Publicar <code>scan.*</code> best-effort después de responder (un fallo de la base no rompe el escaneo); identificar al usuario con <code>authenticate</code>; atajo de firma verificada antes de Gemini.", "Medio: agrega dependencia de la base al escaneo."],
        [MOD, "<code>lib/http/respond.ts</code>, <code>api/v1/dispatch.ts</code>", "Propagar <code>x-correlation-id</code>; ayudante para 202 (Fase 7).", "Bajo."],
        [MOD, "<code>vercel.json</code>", "Mantener el cron diario (ahora invoca al scheduler) y agregar uno diario de purga.", "Bajo: el plan gratuito limita la cantidad de crons."],
        [MOD, "<code>scripts/dev-server.ts</code>, <code>package.json</code>", "Bucle local del despachador para desarrollo; scripts <code>events:replay</code> y <code>events:dlq</code>.", "Bajo."],
        [MOD, "<code>docs/api/openapi.yaml</code>, Postman, <code>README.md</code>", "Documentar endpoints y variables nuevas (<code>EVENTS_*</code>, credenciales FCM).", "Bajo."],
        [SAME, "<code>lib/stores/*</code>, <code>matching.ts</code>, <code>searchStores.ts</code>, <code>productInsights.ts</code>, <code>route.ts</code>", "Sin cambios: siguen siendo la lógica de consulta.", "—"],
    ]
    an = [
        [NEW, "<code>events/AppEvent.kt</code>, <code>AppEventBus.kt</code>", "Eventos locales y bus con <code>SharedFlow</code> (buffer acotado, descartar el más antiguo).", "Bajo."],
        [NEW, "<code>di/AppContainer.kt</code>", "Creación única de Retrofit, repositorios, bus y coordinators. Hoy se reconstruyen con <code>remember{{}}</code> dentro de un composable.", "Medio."],
        [NEW, "<code>coordinators/ScanCoordinator.kt</code>", "Escucha <code>ScanRequested</code> + contexto; decide en línea / sin conexión (la <code>ContextPolicy</code> pura no cambia) y publica el resultado.", "Medio."],
        [NEW, "<code>coordinators/HistoryRecorder.kt</code>", "<code>ScanCompleted</code> → guardar en historial (reemplaza el <code>LaunchedEffect(state)</code>).", "Bajo."],
        [NEW, "<code>work/PendingScanWorker.kt</code>", "<code>CoroutineWorker</code> con red conectada y backoff; reemplaza el <code>LaunchedEffect(policy.offline)</code> y permite procesar con la app cerrada. Reutiliza <code>PendingScanProcessor</code> sin cambios.", "Alto: requiere token de Clerk en segundo plano (spike)."],
        [NEW, "<code>work/AlertsSyncWorker.kt</code>", "Sondeo cada 15 min de <code>GET /alerts?unread=true</code> + notificación local. Respaldo sin Firebase.", "Bajo."],
        [NEW, "<code>push/PushService.kt</code>, canal de notificaciones, <code>data/DeviceRepository</code>", "Servicio FCM, permiso <code>POST_NOTIFICATIONS</code> (Android 13+), registro del token con <code>POST /devices</code> y baja al cerrar sesión.", "Medio: Firebase y permisos."],
        [MOD, "<code>ui/ScanViewModel.kt</code>", "Su estado se deriva de los eventos; la API pública (<code>scan()</code>, <code>reset()</code>) no cambia.", "Medio: tiene pruebas."],
        [MOD, "<code>ui/PendingScansViewModel.kt</code>", "Observa el estado del Worker en vez de procesar; conserva <code>saveForLater</code> y <code>discard</code>.", "Medio."],
        [MOD, "<code>ui/WatchesViewModel.kt</code>", "Se suscribe a <code>AlertReceived</code> y <code>WatchesChanged</code>: actualiza lista y contador sin refrescar a mano.", "Bajo."],
        [MOD, "<code>ui/ProductConfirmationViewModel.kt</code>", "Además de Room, publica <code>IdentificationConfirmed</code> y lo sincroniza con el servidor.", "Bajo."],
        [MOD, "<code>MainActivity.kt</code>", "De 445 a ~150 líneas: solo UI y navegación. Se retiran los <code>LaunchedEffect</code> de coordinación.", "Medio: es el archivo más grande."],
        [MOD, "<code>App.kt</code>", "Inicializa <code>AppContainer</code>, canal de notificaciones y programa los workers.", "Bajo."],
        [MOD, "<code>history/AppDatabase.kt</code>, <code>pending/PendingScan*.kt</code>", "Versión 5: <code>attempts</code> y <code>nextAttemptAt</code> (<code>MIGRATION_4_5</code> + prueba).", "Medio: datos de usuarios existentes."],
        [MOD, "<code>AndroidManifest.xml</code>, <code>app/build.gradle.kts</code>", "Permiso y servicio FCM; dependencias <code>work-runtime-ktx</code>, <code>firebase-messaging</code>, plugin <code>google-services</code>; <code>work-testing</code> para pruebas.", "Medio: <code>google-services.json</code> no se commitea."],
        [SAME, "<code>context/ContextPolicy</code>, <code>DeviceContextMonitor</code>, <code>CaptureConditionsMonitor</code>, pantallas, <code>util/*</code>", "Sin cambios: ya son reactivos o puros.", "—"],
    ]
    return f"""<h1 class="pb">11. Cambios por archivo</h1>
<h2>Backend (<code>api/</code>, <code>lib/</code>, <code>db/</code>, <code>scripts/</code>)</h2>
{tbl(["", "Archivo", "Qué cambia y cómo", "Riesgo"], be, [8, 27, 47, 18])}
<h2 class="pb">App Android</h2>
{tbl(["", "Archivo", "Qué cambia y cómo", "Riesgo"], an, [8, 27, 47, 18])}"""


def s12():
    return f"""<h1>12. Cambios en la base de datos</h1>
<p>Todo es <b>aditivo</b>: no se borra ni renombra nada existente. La migración 006 se prueba primero con Docker local (<code>npm run db:up</code>) y solo después se aplica a Supabase de producción.</p>
<h3>Migración 006 · envelope y entregas por consumidor (borrador)</h3>
<pre>alter table public.domain_events
  add column version        smallint not null default 1,
  add column correlation_id uuid,
  add column causation_id   bigint references public.domain_events (id),
  add column actor          text   not null default 'system',
  add column dedupe_key     text;                       -- para el scheduler
create unique index uq_domain_events_dedupe
  on public.domain_events (dedupe_key) where dedupe_key is not null;

create table public.event_deliveries (
  event_id        bigint not null references public.domain_events (id) on delete cascade,
  consumer        text   not null,
  status          text   not null default 'pending'
                  check (status in ('pending','processing','done','failed','dead')),
  attempts        smallint not null default 0 check (attempts &gt;= 0),
  next_attempt_at timestamptz not null default now(),
  locked_until    timestamptz,
  last_error      text,
  processed_at    timestamptz,
  primary key (event_id, consumer)
);
create index idx_event_deliveries_due
  on public.event_deliveries (next_attempt_at) where status in ('pending','failed');

alter table public.event_deliveries enable row level security;
revoke all on table public.event_deliveries from anon, authenticated;   -- mismo patrón que 004

-- Los 53 eventos históricos no se reprocesan:
update public.domain_events set processed_at = now() where processed_at is null;</pre>
<h3>Reclamo concurrente sin duplicados (núcleo del despachador)</h3>
<pre>update public.event_deliveries d
   set status = 'processing', attempts = attempts + 1, locked_until = now() + interval '60 seconds'
 where (d.event_id, d.consumer) in (
         select event_id, consumer from public.event_deliveries
          where status in ('pending','failed') and next_attempt_at &lt;= now()
          order by event_id
          for update skip locked limit 20)
returning d.event_id, d.consumer, d.attempts;</pre>
<h3>Qué pasa con lo que ya existe</h3>
{tbl(["Tabla", "Estado hoy", "Qué cambia"], [
    ["<code>domain_events</code>", "Se escribe, no se lee; 53 filas, 0 procesadas.", "Se amplía el envelope; se empieza a consumir; se purga a los 30 días."],
    ["<code>devices</code>, <code>push_deliveries</code>", "Creadas en la migración 003, sin código.", "Se activan en la Fase 3 sin cambiar su estructura."],
    ["<code>identification_signatures</code>, <code>signature_confirmations</code>, <code>v_signature_consensus</code>", "Creadas en la migración 004, sin código.", "Se activan en la Fase 4 sin cambiar su estructura."],
    ["<code>alerts</code>, <code>price_observations</code>, <code>watches</code>", "En uso.", "Sin cambios de estructura."],
], [34, 33, 33])}
{box("warn", "Cuidado con producción", "<p>Hay una sola base de Supabase compartida entre producción y las vistas previas de Vercel. Como las migraciones son aditivas, una vista previa sigue funcionando contra el esquema nuevo, pero <b>aplicar la 006 a producción requiere tu aprobación explícita</b> y conviene hacerlo con el código nuevo apagado por bandera.</p>")}"""


def s13():
    ph = [
        ["<b>F0</b><br>Preparación", "Confirmar las 8 decisiones; rama <code>feat/arquitectura-eventos</code>; tres «spikes»: ① <code>pg_net</code>/webhooks/<code>pg_cron</code> en Supabase, ② token de Clerk en segundo plano, ③ Firebase/FCM.", "Informe de spikes y decisiones registradas.", "1", "—"],
        ["<b>F1</b><br>Núcleo backend", "Migración 006, <code>EventBus</code>, despachador, repositorio, <code>MetricsRecorder</code>, endpoints <code>/internal/events/dispatch</code> y <code>/stats</code>, bucle local.", "Sin cambios visibles. Los 53 eventos viejos marcados; los nuevos se entregan a <code>MetricsRecorder</code>. 199 pruebas + ≈ 20 nuevas.", "3", "<code>EVENTS_DISPATCH=off</code>: los eventos se acumulan como hoy."],
        ["<b>F2</b><br>Precios y alertas", "<code>AlertEvaluator</code>, <code>WatchChecker</code>, scheduler, eventos <code>watch.*</code>, despacho inline en <code>/check</code>.", "26 peticiones de Postman idénticas; un segundo usuario recibe alerta sin revisar; el cron procesa más de 5 seguimientos; idempotencia probada.", "3", "<code>EVENTS_ALERTS=legacy</code> usa el <code>recordCheck</code> anterior."],
        ["<b>F3</b><br>Notificaciones", "API de dispositivos, proveedor FCM, <code>NotificationDispatcher</code> y deduplicador; en Android: permiso, canal, servicio y registro de token (o sondeo con WorkManager si se elige).", "Una alerta llega con la app cerrada en menos de 2 min; token inválido se revoca; sin spam.", "4", "Sin dispositivos registrados, el consumidor no hace nada."],
        ["<b>F4</b><br>Escaneo y catálogo", "Eventos <code>scan.*</code>, <code>CatalogLearner</code>, confirmaciones sincronizadas, <code>SignatureConsensus</code> y atajo de firma verificada.", "Con ≥ 2 confirmaciones la firma queda verificada; un escaneo repetido no llama a Gemini (proveedor falso en la prueba); una falla de la base no rompe el escaneo.", "3", "<code>EVENTS_SCAN=off</code>."],
        ["<b>F5</b><br>Android por eventos", "<code>AppContainer</code>, <code>AppEventBus</code>, coordinators, <code>PendingScanWorker</code>, <code>MainActivity</code> reducida, migración Room 4→5.", "122 pruebas + ≈ 20 nuevas; la cola se procesa con la app cerrada al volver la red (prueba manual); sin cambios visuales.", "4", "Puede retrasarse sin afectar el backend (F5 es independiente de F3)."],
        ["<b>F6</b><br>Resiliencia y cierre", "Circuit breaker, endpoints de DLQ, <code>v_event_stats</code>, retención, prueba de carga, actualizar PDF técnico/OpenAPI/README, guion de demostración.", "Prueba de carga &lt; 5 min por 1 000 eventos; cero muertos tras la demostración; documentación al día.", "3", "—"],
        ["<b>F7</b><br><span class='tag d'>OPCIONAL</span>", "Escaneo asíncrono (<code>202</code> + push con el resultado) y métricas Prometheus.", "El escaneo no bloquea la pantalla; el resultado llega por push.", "3", "Bandera por versión de la app."],
    ]
    return f"""<h1>13. Fases, compatibilidad y rollback</h1>
{tbl(["Fase", "Qué se hace", "Criterio de aceptación", "Jornadas", "Rollback"], ph, [10, 31, 33, 7, 19])}
<p><b>Total:</b> 21 jornadas (≈ 6 h cada una) + 3 opcionales. F5 (Android) es independiente de F3 y podría hacerse en paralelo por otra persona del equipo.</p>
<h3>Reglas de compatibilidad (invariantes)</h3>
<ol>
 <li>No cambian rutas, cuerpos ni códigos HTTP de los 10 endpoints actuales; <code>POST /check</code> sigue devolviendo <code>alertasNuevas</code>.</li>
 <li>Las 26 peticiones de Postman y todas las pruebas existentes se corren en cada Pull Request.</li>
 <li>Banderas de entorno por capacidad: <code>EVENTS_DISPATCH</code>, <code>EVENTS_ALERTS</code>, <code>EVENTS_PUSH</code>, <code>EVENTS_SCAN</code>. Apagadas = comportamiento actual.</li>
 <li>Migraciones aditivas; el código anterior se conserva una versión detrás de bandera antes de borrarse.</li>
 <li>Cada fase es un Pull Request independiente, con despliegue de vista previa de Vercel antes de mergear.</li>
</ol>
<h3>Qué documentación se actualiza</h3>
<p>Diagrama y sección de arquitectura del documento técnico, <code>openapi.yaml</code> y colección de Postman, README (variables y comandos nuevos) y el guion de demostración.</p>"""


def s14():
    risks = [
        ["Supabase gratuito sin <code>pg_net</code>, webhooks o <code>pg_cron</code>", "Media", "Alto", "Spike en F0. Plan B: workflow de GitHub Actions cada 5 min o servicio externo (opción C)."],
        ["Vercel gratuito: cron solo diario y funciones de 60 s", "Alta", "Medio", "Despacho inline, presupuesto de 45 s, lotes de 20 y varios disparadores."],
        ["Doble procesamiento o desorden", "Media", "Alto", "Idempotencia por restricciones únicas, orden por agregado, pruebas de ejecución doble y de concurrencia."],
        ["Más latencia en «Revisar ahora» por el despacho inline", "Media", "Bajo", "Límite de 8 s; si se excede, responde sin alertas y llegan por push."],
        ["Crecimiento de la tabla de eventos", "Media", "Medio", "Retención de 30 días, carga útil ≤ 8 KB, sin imágenes."],
        ["Token de Clerk no disponible en segundo plano", "Media", "Alto", "Spike en F0. Si no se puede: el push lleva solo el <code>alertId</code> y la cola offline sigue procesándose con la app abierta (como hoy)."],
        ["Firebase: requiere proyecto y credenciales", "Alta", "Medio", "Cuenta de Marco (dueño de Vercel); el JSON de la cuenta de servicio va a variables de entorno, nunca al repositorio. Alternativa: sondeo con WorkManager."],
        ["Complejidad excesiva para el tamaño del proyecto", "Media", "Medio", "Enfoque híbrido; F1–F3 ya demuestran el valor; F4–F7 son incrementales y prescindibles."],
        ["Romper el contrato de la API", "Baja", "Alto", "26 peticiones de Postman + pruebas + banderas + código viejo conservado."],
        ["Datos personales en eventos", "Baja", "Medio", "Solo identificadores internos; nunca correos, imágenes ni tokens en la carga útil."],
        ["Plazo del curso", "Media", "Medio", "Ordenar por valor demostrable: F1 → F2 → F3 primero."],
    ]
    dec = [
        ["<b>D1</b> Alcance", "Híbrido: consultas interactivas síncronas, efectos y trabajo diferido por eventos.", "«Todo a eventos» incluye hacer asíncrono el escaneo (F7)."],
        ["<b>D2</b> Despertar al despachador", "Opción B (webhook de la base + <code>pg_cron</code> + inline), validada en el spike F0.", "C (servicio externo) o GitHub Actions si B no está disponible."],
        ["<b>D3</b> Notificaciones", "FCM real (push inmediato, con la app cerrada).", "Sondeo con WorkManager cada 15 min: sin Firebase, pero no es tiempo real."],
        ["<b>D4</b> Accesos", "Necesito saber quién aprueba cambios en Supabase (extensiones, migración 006 en producción), Vercel (variables) y Firebase.", "Hoy Vercel es de Marco; yo puedo leer Supabase, pero no aplicaré nada en producción sin tu aprobación."],
        ["<b>D5</b> Entrega académica", "¿Es para un taller con fecha y rúbrica? Si lo es, ordeno las fases por lo que se evalúa (diagramas, etapas, demostración).", "Si no hay fecha, sigo el orden F0 → F7."],
        ["<b>D6</b> Ramas y autoría", "Rama <code>feat/arquitectura-eventos</code> y un PR por fase; commits en español (<code>tipo: descripción</code>); coautoría de cada persona del equipo.", "Una sola rama grande (más difícil de revisar)."],
        ["<b>D7</b> Entorno de pruebas", "Docker local (ya existe) + vista previa de Vercel; migración a producción solo al cerrar cada fase.", "Segundo proyecto de Supabase para pruebas (cabe en el plan gratuito)."],
        ["<b>D8</b> Datos de escaneo", "Guardar por usuario qué producto se escaneó (marca, nombre) para el catálogo comunitario. ¿Aceptable para ustedes?", "No guardar nada del escaneo; solo las confirmaciones explícitas."],
    ]
    return f"""<h1>14. Riesgos y decisiones abiertas</h1>
<h2>Riesgos</h2>
{tbl(["Riesgo", "Prob.", "Impacto", "Mitigación"], risks, [34, 8, 9, 49])}
<h2>Decisiones que necesito de ti</h2>
{tbl(["Decisión", "Mi recomendación", "Alternativa"], dec, [18, 49, 33])}
<h2>Trazabilidad con la teoría (Sesión 3)</h2>
{tbl(["Etapa de la teoría", "Dónde está en este plan", "Artefacto principal"], [
    ["1. Eventos y componentes", "Sección 4", "20 tipos de evento del servidor + 9 internos del cliente"],
    ["2. Bus de eventos", "Sección 5", "Outbox + <code>event_deliveries</code>, pub/sub por tópico"],
    ["3. Productores y consumidores", "Sección 6", "6 consumidores idempotentes, scheduler"],
    ["4. Procesamiento", "Sección 6", "Filtro, agregación, correlación, deduplicación, CEP opcional"],
    ["5. Orquestación y coreografía", "Sección 7", "Coreografía general; orquestación en despachador y <code>ScanCoordinator</code>"],
    ["6. Estado y persistencia", "Secciones 8 y 12", "Estado + registro de eventos; migración 006; retención"],
    ["7. Errores y resiliencia", "Sección 9", "Backoff, DLQ, circuit breaker, lease"],
    ["8. Monitorización", "Sección 10", "<code>v_event_stats</code>, <code>/stats</code>, registros JSON"],
    ["9. Pruebas", "Sección 10", "Matriz de 9 niveles; meta: 321 pruebas existentes siempre verdes"],
], [27, 22, 51])}
<h3>Material revisado para este plan</h3>
<p>PDF de teoría (27 diapositivas); backend completo (<code>api/</code>, <code>lib/</code> ≈ 3 100 líneas, 5 migraciones SQL, <code>vercel.json</code>, variables de entorno); Android (≈ 6 900 líneas en <code>main</code>: <code>MainActivity</code>, ViewModels, repositorios, cola offline, monitores de contexto y sensores, Room, dependencias y manifiesto); estado real de producción (53 eventos, 0 procesados); estructura de las pruebas existentes.</p>"""


def main():
    body = (portada() + s1() + s2() + s3() + s4() + s5() + s6() + s7() + s8() + s9() + s10() + s11() + s12() + s13() + s14())
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        page = tmp / "plan.html"
        page.write_text(f'<!doctype html><meta charset="utf-8"><title>Plan de arquitectura orientada a eventos</title><style>{CSS}</style><body>{body}</body>', encoding="utf-8")
        subprocess.run([EDGE, "--headless=new", "--disable-gpu", f"--user-data-dir={tmp / 'profile'}", "--no-pdf-header-footer",
                        f"--print-to-pdf={OUT}", page.as_uri()], check=True, timeout=240, capture_output=True)
    print("listo", OUT)


if __name__ == "__main__":
    main()
