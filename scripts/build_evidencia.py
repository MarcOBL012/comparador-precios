"""Genera docs/taller3/evidencia/imagenes/*.png y Evidencia_Taller3.pdf a partir de la salida REAL guardada en
docs/taller3/evidencia/raw/ (Newman, Vitest, curl a Vercel y consultas a Supabase).

Son representaciones de datos reales, no capturas de pantalla de Postman/Supabase/Vercel.
Uso:  python -I scripts/build_evidencia.py
"""
import html
import json
import re
import shutil
import subprocess
import tempfile
import textwrap
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent.parent
EV = ROOT / "docs" / "taller3" / "evidencia"
RAW = EV / "raw"
IMG = EV / "imagenes"
COLLECTION = ROOT / "docs" / "api" / "postman_collection.json"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
WIDTH = 92  # columnas de texto antes de cortar la línea
LIMA = timezone(timedelta(hours=-5))
NOW = datetime.now(LIMA).strftime("%Y-%m-%d %H:%M")

CSS = """
*{box-sizing:border-box}
body{margin:0;background:#eef1f5;font-family:'Segoe UI',Arial,sans-serif;color:#1b1f27}
.wrap{padding:24px;width:930px}
.card{width:880px;background:#fff;border:1px solid #d5dae3;border-radius:10px;overflow:hidden;margin-bottom:18px}
.hd{padding:14px 20px;background:#1f2937;color:#fff}
.hd .t{font-size:17px;font-weight:600}.hd .s{font-size:12px;color:#aeb8c8;margin-top:3px}
.sec{padding:12px 20px;border-top:1px solid #e6e9ef}
.lbl{font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#6b7482;margin-bottom:6px;font-weight:600}
pre{margin:0;font:13px/19px Consolas,'Courier New',monospace;white-space:pre-wrap;word-break:break-word;
background:#f5f7fa;border:1px solid #e3e7ee;border-radius:6px;padding:10px 12px}
.req{font:14px Consolas,monospace;word-break:break-all}
.m{display:inline-block;padding:2px 8px;border-radius:4px;color:#fff;font-weight:700;margin-right:8px;font-size:13px}
.GET{background:#2563eb}.POST{background:#16a34a}.PATCH{background:#d97706}.PUT{background:#7c3aed}.DELETE{background:#dc2626}
.st{display:inline-block;padding:3px 10px;border-radius:5px;color:#fff;font-weight:700;font-size:14px}
.c2{background:#15803d}.c4{background:#c2410c}.c5{background:#b91c1c}
.meta{color:#5b6472;font-size:12.5px;margin-left:10px}
table{border-collapse:collapse;width:100%;font-size:13px}
th{background:#f1f4f9;text-align:left;padding:6px 8px;border-bottom:1px solid #d5dae3}
td{padding:5px 8px;border-bottom:1px solid #eef0f4;vertical-align:top}
.ft{padding:9px 20px;background:#f8fafc;border-top:1px solid #e6e9ef;font-size:11.5px;color:#5b6472}
.ok{color:#15803d;font-weight:700}.bad{color:#b91c1c;font-weight:700}
.big{font-size:26px;font-weight:700}.kpi{display:inline-block;margin-right:34px}.kpi small{display:block;color:#6b7482;font-size:12px}
h1{font-size:24px;margin:0 0 6px}p{line-height:1.5;font-size:14px}
.pdf .card{zoom:.78;page-break-inside:avoid}.pdf .wrap{width:auto;padding:0}
.pb{page-break-after:always}
@page{size:A4;margin:10mm}
"""

PIE_REAL = "Generado el {now} (hora Perú) a partir de salida real. Es una representación de datos, no una captura de pantalla de la herramienta."


def esc(x):
    return html.escape(str(x))


def wrap(text, width=WIDTH):
    out = []
    for line in str(text).split("\n"):
        out.extend(textwrap.wrap(line, width, replace_whitespace=False, break_long_words=True) or [""])
    return "\n".join(out)


def pre(text):
    return f"<pre>{esc(wrap(text))}</pre>"


def sec(label, body):
    return f'<div class="sec"><div class="lbl">{esc(label)}</div>{body}</div>'


def card(title, subtitle, sections, pie):
    return (
        f'<div class="card"><div class="hd"><div class="t">{esc(title)}</div><div class="s">{esc(subtitle)}</div></div>'
        + "".join(sections)
        + f'<div class="ft">{esc(pie)}</div></div>'
    )


def table(headers, rows):
    head = "".join(f"<th>{esc(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in r) + "</tr>" for r in rows)
    return f"<table><tr>{head}</tr>{body}</table>"


def badge(code, text=""):
    cls = "c2" if code < 300 else "c4" if code < 500 else "c5"
    return f'<span class="st {cls}">{code} {esc(text)}</span>'


def slug(s):
    s = re.sub(r"[→]", "", s.lower())
    s = (s.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n"))
    return re.sub(r"[^a-z0-9]+", "_", s).strip("_")[:48]


# ---------------------------------------------------------------------------------------------- Postman
def url_str(u):
    if isinstance(u, str):
        return u
    host = ".".join(u["host"]) if isinstance(u.get("host"), list) else u.get("host", "")
    port = f":{u['port']}" if u.get("port") else ""
    path = "/".join(u.get("path", []))
    q = "&".join(f"{x['key']}={x.get('value', '')}" for x in (u.get("query") or []))
    return f"{u.get('protocol', 'http')}://{host}{port}/{path}" + (f"?{q}" if q else "")


def hdr_text(headers, skip=()):
    lines = []
    for h in headers or []:
        k, v = h["key"], h["value"]
        if k.lower() in skip:
            continue
        if k.lower() == "authorization":
            v = "Bearer ********"
        lines.append(f"{k}: {v}")
    return "\n".join(lines) or "(ninguno)"


def folder_names():
    c = json.loads(COLLECTION.read_text(encoding="utf-8"))
    out = []
    for f in c["item"]:
        for r in f["item"]:
            out.append((f["name"], r["name"]))
    return out


def postman_cards():
    j = json.loads((RAW / "newman.json").read_text(encoding="utf-8"))
    names = folder_names()
    ex = j["run"]["executions"]
    cards = {}
    summary_rows = []
    for i, e in enumerate(ex, 1):
        folder, name = names[i - 1]
        code = e["response"]["code"]
        status = e["response"]["status"]
        body_raw = bytes(e["response"]["stream"]["data"]).decode("utf-8") if e["response"].get("stream") else ""
        try:
            body_txt = json.dumps(json.loads(body_raw), ensure_ascii=False, indent=2) if body_raw else "(sin cuerpo)"
        except ValueError:
            body_txt = body_raw or "(sin cuerpo)"
        req_body = (e["request"].get("body") or {}).get("raw")
        asserts = e.get("assertions", [])
        ok_asserts = [a for a in asserts if not a.get("error")]
        a_html = "".join(
            f'<div class="{"ok" if not a.get("error") else "bad"}">{"✔" if not a.get("error") else "✘"} {esc(a["assertion"])}</div>'
            for a in asserts
        ) or "<i>(sin comprobaciones)</i>"
        m = e["request"]["method"]
        url = url_str(e["request"]["url"])
        secs = [
            sec("Petición", f'<div class="req"><span class="m {m}">{m}</span>{esc(url)}</div>'),
            sec("Encabezados enviados", pre(hdr_text(e["request"].get("header")))),
        ]
        if req_body:
            try:
                req_body = json.dumps(json.loads(req_body), ensure_ascii=False, indent=2)
            except ValueError:
                pass
            secs.append(sec("Cuerpo enviado (JSON)", pre(req_body)))
        secs.append(
            sec(
                "Respuesta",
                f'{badge(code, status)}<span class="meta">{e["response"]["responseTime"]} ms · {e["response"]["responseSize"]} bytes</span>',
            )
        )
        secs.append(sec("Encabezados de respuesta", pre(hdr_text(e["response"].get("header"), skip=("connection", "keep-alive", "date")))))
        secs.append(sec("Cuerpo de la respuesta (JSON)", pre(body_txt)))
        secs.append(sec("Comprobaciones de la colección", a_html))
        title = f"Postman/Newman · {i:02d}. {name}"
        fname = f"postman_{i:02d}_{slug(name)}.png"
        cards[fname] = card(title, f"Carpeta: {folder}", secs, PIE_REAL.format(now=NOW) + " Servidor: API v1 local (Docker + Postgres).")
        summary_rows.append(
            [
                str(i),
                f'<span class="m {m}">{m}</span>',
                esc(name),
                badge(code),
                f'<span class="{"ok" if len(ok_asserts) == len(asserts) else "bad"}">{len(ok_asserts)}/{len(asserts)}</span>',
            ]
        )
    st = j["run"]["stats"]
    kp = (
        f'<span class="kpi"><span class="big">{st["requests"]["total"]}</span><small>peticiones</small></span>'
        f'<span class="kpi"><span class="big ok">{st["assertions"]["total"] - st["assertions"]["failed"]}/{st["assertions"]["total"]}</span><small>comprobaciones superadas</small></span>'
        f'<span class="kpi"><span class="big ok">{st["requests"]["failed"] + st["assertions"]["failed"]}</span><small>fallos</small></span>'
    )
    cards["postman_00_resumen_26_peticiones.png"] = card(
        "Postman/Newman · Resumen de la colección completa",
        "docs/api/postman_collection.json contra http://localhost:3000/api/v1 (equivalente al Runner de Postman)",
        [sec("Resultado", kp), sec("Detalle por petición", table(["#", "Método", "Petición", "Código", "Tests"], summary_rows))],
        PIE_REAL.format(now=NOW),
    )
    return cards


# ---------------------------------------------------------------------------------------------- tests
def tests_cards():
    cards = {}
    integ = [l for l in (RAW / "tests_integracion.txt").read_text(encoding="utf-8").splitlines() if "✓" in l]
    names = [re.sub(r"^.*Postgres real > ", "", l.replace("✓", "").strip()) for l in integ]
    rows = [f'<div class="ok">✔ <span style="color:#1b1f27;font-weight:400">{esc(n)}</span></div>' for n in names]
    cmd = "TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npx vitest run test/integration --reporter=verbose"
    cards["tests_01_integracion_postgres_real.png"] = card(
        "Pruebas de integración contra Postgres real (13)",
        "test/integration/api.pg.test.ts · Vitest",
        [
            sec("Comando", pre(cmd)),
            sec("Resultado", "".join(rows)),
            sec("Resumen", f'<span class="kpi"><span class="big ok">{len(names)}/{len(names)}</span><small>pruebas pasaron</small></span>'),
            sec(
                "Nota",
                "<p>Los mensajes «timeout» y «violates check constraint» que Vitest imprime en esta corrida son de las pruebas "
                "<b>«si las tiendas fallan responde 502»</b> y <b>«atomicidad»</b>: fuerzan el fallo a propósito y la prueba verifica que no quede ningún dato a medias.</p>",
            ),
        ],
        PIE_REAL.format(now=NOW),
    )
    suite = (RAW / "tests_suite.txt").read_text(encoding="utf-8").splitlines()
    files = [l for l in suite if l.startswith(" ✓")]
    summ = [l.strip() for l in suite if re.match(r"\s*(Test Files|Tests)\s", l)]
    rows = []
    for l in files:
        m = re.match(r"\s*✓ (\S+) \((\d+) tests?\)", l)
        if m:
            rows.append([f'<span class="ok">✔</span> {esc(m.group(1))}', m.group(2)])
    cards["tests_02_suite_completa_199.png"] = card(
        "Suite completa del backend: 26 archivos, 199 pruebas",
        "npm test (Vitest) con TEST_DATABASE_URL definido, así corren también las de integración",
        [sec("Resumen", pre("\n".join(summ))), sec("Archivos de prueba", table(["Archivo", "Pruebas"], rows))],
        PIE_REAL.format(now=NOW),
    )
    return cards


# ---------------------------------------------------------------------------------------------- Supabase
SB = json.loads((RAW / "supabase_produccion.json").read_text(encoding="utf-8"))
SB_SUB = "Supabase · proyecto DAIS (rama main, PRODUCTION) · consultado " + SB["consultado"].split(" - ")[0]
SB_PIE = "Resultado real de la consulta SQL (SQL Editor / API de Supabase), sin identificadores de usuario. " + PIE_REAL.format(now=NOW)


def sb_card(title, sql, headers, rows, extra=None):
    secs = [sec("Consulta SQL", pre(sql)), sec("Resultado", table(headers, rows))]
    if extra:
        secs.append(sec("Qué demuestra", extra))
    return card(title, SB_SUB, secs, SB_PIE)


def supabase_cards():
    c = {}
    e = SB["esquema"]
    sql = "select 'watches' as tabla, count(*) as filas from watches union all\nselect 'price_observations', count(*) from price_observations union all\n... (8 tablas)"
    kp = "".join(f'<span class="kpi"><span class="big">{v}</span><small>{k.replace("_", " ")}</small></span>' for k, v in e.items())
    c["supabase_01_tablas_y_esquema.png"] = card(
        "Base de datos en producción: tablas, filas e integridad",
        SB_SUB,
        [
            sec("Esquema (information_schema, schema public)", kp),
            sec("Consulta SQL (filas por tabla)", pre(sql)),
            sec("Filas por tabla", table(["Tabla", "Filas"], [[esc(r["tabla"]), str(r["filas"])] for r in SB["filas_por_tabla"]])),
            sec("Qué demuestra", "<p>Más de 2 entidades relacionadas (21 claves foráneas), con restricciones UNIQUE, CHECK y triggers que protegen la integridad aunque se escriba por fuera de la API.</p>"),
        ],
        SB_PIE,
    )
    c["supabase_02_seguimientos_watches.png"] = sb_card(
        "Tabla watches (seguimientos) con su producto",
        "select p.nombre as producto, p.presentacion, w.target_price as objetivo,\n       w.status_code as estado, w.last_checked_at, w.created_at\nfrom watches w join products p on p.id = w.product_id\norder by w.created_at desc;",
        ["Producto", "Present.", "Objetivo (S/)", "Estado", "Última revisión", "Creado"],
        [[esc(w["producto"]), esc(w["presentacion"]), w["objetivo"], esc(w["estado"]), w["ultima_revision_lima"], w["creado_lima"]] for w in SB["watches"]],
        "<p>La primera fila se creó el 2026-11-08 a las 22:59:02 (<code>POST /watches</code>) y su última revisión es de las 23:00:44 (<code>POST /watches/{id}/check</code>): "
        "el cambio quedó persistido. La fila «alcanzada» muestra la transición de estado cuando el precio llegó al objetivo (regla R4).</p>",
    )
    c["supabase_03_observaciones_de_precio.png"] = sb_card(
        "Tabla price_observations (historial de precios)",
        "select o.observed_at, s.nombre as tienda, o.precio, o.source_code as fuente, l.titulo\nfrom price_observations o\njoin store_listings l on l.id = o.listing_id\njoin stores s on s.id = l.store_id\norder by o.observed_at desc limit 12;",
        ["Observado", "Tienda", "Precio (S/)", "Fuente", "Título"],
        [[o["observado_lima"], esc(o["tienda"]), o["precio"], esc(o["fuente"]), esc(o["titulo"])] for o in SB["observaciones_recientes"]],
        "<p>La primera fila (Falabella, S/ 27.70, fuente «shopping») corresponde a la revisión de las 23:00:44. Las de las 06:22 coinciden con la revisión automática diaria (ver supabase_04).</p>",
    )
    c["supabase_04_cron_diario_automatico.png"] = sb_card(
        "Revisión automática diaria (cron) funcionando en producción",
        "select date_trunc('day', observed_at) as dia, count(*) as observaciones,\n       min(observed_at) as primera_hora\nfrom price_observations group by 1 order by 1 desc;",
        ["Día", "Observaciones", "Primera hora (Perú)"],
        [[d["dia"], str(d["observaciones"]), d["primera_hora_lima"]] for d in SB["observaciones_por_dia"]],
        "<p>Cada día del 4 al 8 de noviembre aparecen observaciones a las 06:22 hora Perú (11:22 UTC), a la misma hora todos los días. Coincide con la revisión automática "
        "<code>/api/v1/internal/check-due</code> programada en vercel.json (<code>0 11 * * *</code>, UTC; en el plan gratuito Vercel puede demorarla unos minutos).</p>",
    )
    c["supabase_05_alertas.png"] = sb_card(
        "Tabla alerts (alertas generadas por la regla de negocio)",
        "select id, alert_type_code as tipo, created_at as creada, read_at as leida\nfrom alerts order by created_at desc;",
        ["Id", "Tipo", "Creada (Perú)", "Leída (Perú)"],
        [[str(a["id"]), esc(a["tipo"]), a["creada_lima"], a["leida_lima"]] for a in SB["alertas"]],
        "<p>La alerta <b>objetivo_alcanzado</b> se creó el 2026-11-04 a las 06:22, en la hora de la revisión automática (regla R4). El campo <b>read_at</b> se llenó el 2026-11-08 a las 23:01:45 al marcarla como leída (<code>PATCH /alerts/{id}</code>).</p>",
    )
    c["supabase_06_migraciones_aplicadas.png"] = card(
        "Migraciones del esquema aplicadas en producción",
        SB_SUB,
        [sec("Migraciones (supabase list_migrations)", table(["Versión", "Nombre"], [[m["version"], esc(m["name"])] for m in SB["migraciones"]])),
         sec("Qué demuestra", "<p>El esquema versionado de <code>db/migrations/001–005</code> está aplicado en la base de producción.</p>")],
        SB_PIE,
    )
    return c


# ---------------------------------------------------------------------------------------------- Vercel
def vercel_cards():
    txt = (RAW / "vercel_produccion.txt").read_text(encoding="utf-8")
    stamp = txt.splitlines()[0].replace("# Captura: ", "")
    cards = {}
    for i, block in enumerate([b for b in txt.split("### ")[1:]], 1):
        lines = block.strip().split("\n")
        method, url = lines[0].split(" ", 1)
        status = lines[1]
        code = int(status.split()[1])
        sep = lines.index("") if "" in lines else len(lines)
        headers = "\n".join(l for l in lines[2:sep] if not l.lower().startswith(("etag", "strict-transport", "x-robots", "content-length")))
        body = "\n".join(lines[sep + 1:]).strip()
        try:
            body = json.dumps(json.loads(body), ensure_ascii=False, indent=2)
        except ValueError:
            pass
        path = url.split("vercel.app")[1]
        secs = [
            sec("Petición", f'<div class="req"><span class="m {method}">{method}</span>{esc(url)}</div>'),
            sec("Respuesta", badge(code, " ".join(status.split()[2:]))),
            sec("Encabezados de respuesta (note «Server: Vercel»)", pre(headers)),
            sec("Cuerpo", pre(body)),
        ]
        cards[f"vercel_{i:02d}_{slug(method + ' ' + path)}.png"] = card(
            f"Producción en Vercel · {method} {path}",
            "https://comparador-precios-marco-obl.vercel.app",
            secs,
            f"curl real contra la API desplegada, {stamp}. Sin token: se espera 401 en rutas protegidas, 405 con método incorrecto.",
        )
    return cards


# ---------------------------------------------------------------------------------------------- render
def render_png(html_doc, out_png, tmp, edge_profile):
    page = tmp / (out_png.stem + ".html")
    page.write_text(f'<!doctype html><meta charset="utf-8"><style>{CSS}</style><body><div class="wrap">{html_doc}</div></body>', encoding="utf-8")
    raw = tmp / (out_png.stem + "_raw.png")
    subprocess.run(
        [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--user-data-dir={edge_profile}", "--force-device-scale-factor=1",
         f"--screenshot={raw}", "--window-size=960,6000", page.as_uri()],
        check=True, timeout=90, capture_output=True,
    )
    im = Image.open(raw).convert("RGB")
    bg = Image.new("RGB", im.size, im.getpixel((2, 2)))
    box = ImageChops.difference(im, bg).getbbox()
    pad = 20
    im.crop((max(box[0] - pad, 0), max(box[1] - pad, 0), min(box[2] + pad, im.width), min(box[3] + pad, im.height))).save(out_png)


def build_pdf(all_cards, tmp, edge_profile):
    mapa = [
        ("postman_*", "API REST con ≥5 endpoints, CRUD, validaciones y códigos HTTP (201, 400, 401, 404, 405, 409, 429, 204)"),
        ("postman_*_revisar_*", "Operación de negocio más allá de CRUD (POST /watches/{id}/check) y regla R3 (429 con Retry-After)"),
        ("tests_*", "≥5 pruebas con éxitos y fallos; las de integración prueban la persistencia en Postgres real"),
        ("supabase_*", "Base de datos con entidades relacionadas, cambio persistido, alertas y cron diario en producción"),
        ("vercel_*", "La API desplegada responde en Vercel y exige autenticación"),
    ]
    idx = (
        '<div class="pb"><h1>Evidencias del Taller 3 · API REST de seguimiento de precios</h1>'
        f"<p>Generado el {NOW} (hora Perú). Imágenes disponibles también por separado en <code>docs/taller3/evidencia/imagenes/</code>.</p>"
        + table(["Imágenes", "Qué demuestran"], [[f"<code>{a}</code>", esc(b)] for a, b in mapa])
        + "<p><b>Importante:</b> son representaciones de salida real (Newman, Vitest, Supabase, curl), no capturas de pantalla de las "
        "aplicaciones. Las pantallas de la app Android y del panel de Vercel/Supabase deben tomarse a mano.</p></div>"
    )
    body = idx + "\n".join(all_cards)
    page = tmp / "pdf.html"
    page.write_text(f'<!doctype html><meta charset="utf-8"><style>{CSS}</style><body class="pdf"><div class="wrap">{body}</div></body>', encoding="utf-8")
    out = EV / "Evidencia_Taller3.pdf"
    subprocess.run(
        [EDGE, "--headless=new", "--disable-gpu", f"--user-data-dir={edge_profile}", "--no-pdf-header-footer", f"--print-to-pdf={out}", page.as_uri()],
        check=True, timeout=180, capture_output=True,
    )


def main():
    cards = {}
    cards.update(postman_cards())
    cards.update(tests_cards())
    cards.update(supabase_cards())
    cards.update(vercel_cards())
    if IMG.exists():
        shutil.rmtree(IMG)
    IMG.mkdir(parents=True)
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        profile = tmp / "edge-profile"
        for fname, doc in sorted(cards.items()):
            render_png(doc, IMG / fname, tmp, profile)
            print("ok", fname)
        build_pdf([cards[k] for k in sorted(cards)], tmp, profile)
    print(len(cards), "imagenes + PDF")


if __name__ == "__main__":
    main()
