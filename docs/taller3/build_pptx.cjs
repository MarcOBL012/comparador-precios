// Genera docs/taller3/Taller3_Presentacion.pptx (9 diapositivas).
//   cd <carpeta con pptxgenjs, react-icons, react-dom, sharp> && node <repo>/docs/taller3/build_pptx.cjs
// Variable SKILL_DIR: carpeta del skill pptx (para apply_theme.js).
const path = require('node:path');
const pptxgen = require('pptxgenjs');
const React = require('react');
const ReactDOMServer = require('react-dom/server');
const sharp = require('sharp');
const fa = require('react-icons/fa');
const { applyTheme } = require(path.join(process.env.SKILL_DIR, 'scripts', 'apply_theme.js'));

const THEME = {
  name: 'Comparador Verde',
  headFontFace: 'Cambria',
  bodyFontFace: 'Calibri',
  colors: {
    dk1: '1F2933', lt1: 'FFFFFF', dk2: '0F3D2E', lt2: 'F2F6F4',
    accent1: '1B7F4B', accent2: 'E8741E', accent3: '8CC5A6', accent4: 'B33A3A', accent5: '52606D', accent6: 'F4C95D',
    hlink: '1B7F4B', folHlink: '52606D',
  },
};
const HEX = { dark: '0F3D2E', green: '1B7F4B', orange: 'E8741E', light: 'F2F6F4', ink: '1F2933', slate: '52606D', soft: '8CC5A6' };

const pres = new pptxgen();
pres.layout = 'LAYOUT_16x9'; // 10 x 5.625
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
pres.title = 'Taller 3 - API REST de seguimiento de precios';
pres.author = 'Brian Curay, Marco Obispo, Niels Chaves';
const C = pres.SchemeColor;

async function icon(Comp, color) {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { color: '#' + color, size: 256 }));
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return 'image/png;base64,' + buf.toString('base64');
}
const shadow = () => ({ type: 'outer', color: '000000', opacity: 0.12, blur: 6, offset: 2, angle: 90 });

// ---- Layouts --------------------------------------------------------------------------------
const FOOT = 'Taller 3 · SI806 · API REST de seguimiento de precios';
pres.defineSlideMaster({
  title: 'TITULO',
  background: { color: HEX.dark },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 1.35, w: 5.6, h: 1.9, fontSize: 38, bold: true, color: C.background1, align: 'left', valign: 'top', margin: 0 }, text: '' } },
    { placeholder: { options: { name: 'body', type: 'body', x: 0.6, y: 3.45, w: 5.6, h: 1.2, fontSize: 16, color: C.accent3, align: 'left', valign: 'top', margin: 0 }, text: '' } },
  ],
});
pres.defineSlideMaster({
  title: 'CONTENIDO',
  background: { color: HEX.light },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.3, w: 9, h: 0.75, fontSize: 32, bold: true, color: C.text2, align: 'left', valign: 'middle', margin: 0 }, text: '' } },
    { text: { text: FOOT, options: { x: 0.5, y: 5.2, w: 6, h: 0.25, fontSize: 10, color: C.accent5, margin: 0, isTextBox: true } } },
  ],
  slideNumber: { x: 9.0, y: 5.2, w: 0.5, h: 0.25, fontSize: 10, color: C.accent5, align: 'right' },
});
pres.defineSlideMaster({
  title: 'CIERRE',
  background: { color: HEX.dark },
  objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.3, w: 9, h: 0.75, fontSize: 32, bold: true, color: C.background1, align: 'left', valign: 'middle', margin: 0 }, text: '' } },
    { text: { text: FOOT, options: { x: 0.5, y: 5.2, w: 6, h: 0.25, fontSize: 10, color: C.accent3, margin: 0, isTextBox: true } } },
  ],
  slideNumber: { x: 9.0, y: 5.2, w: 0.5, h: 0.25, fontSize: 10, color: C.accent3, align: 'right' },
});

function card(slide, name, x, y, w, h, fill = C.background1) {
  slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.08, fill: { color: fill }, line: { color: fill, width: 0 }, shadow: shadow(), objectName: name });
}
function circleIcon(slide, name, img, x, y, d, bg) {
  slide.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: bg }, line: { color: bg, width: 0 }, objectName: name + ' fondo' });
  const pad = d * 0.25;
  slide.addImage({ data: img, x: x + pad, y: y + pad, w: d - 2 * pad, h: d - 2 * pad, altText: name, objectName: name });
}
function line(slide, x1, y1, x2, y2, color = HEX.slate) {
  const o = { x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1), h: Math.abs(y2 - y1), line: { color, width: 1.5, endArrowType: 'triangle' } };
  if ((x2 < x1) !== (y2 < y1) && x1 !== x2 && y1 !== y2) o.flipV = true;
  if (x2 < x1 && y1 === y2) o.flipH = true;
  if (y2 < y1 && x1 === x2) o.flipV = true;
  if (x2 < x1 && y2 > y1) { o.flipH = true; o.flipV = false; }
  slide.addShape(pres.shapes.LINE, o);
}

(async () => {
  const I = {
    mobile: await icon(fa.FaMobileAlt, 'FFFFFF'), server: await icon(fa.FaServer, 'FFFFFF'), shield: await icon(fa.FaShieldAlt, 'FFFFFF'),
    cogs: await icon(fa.FaCogs, 'FFFFFF'), db: await icon(fa.FaDatabase, 'FFFFFF'), bell: await icon(fa.FaBell, 'FFFFFF'),
    tag: await icon(fa.FaTags, 'FFFFFF'), check: await icon(fa.FaCheckCircle, 'FFFFFF'), plug: await icon(fa.FaPlug, 'FFFFFF'),
    clock: await icon(fa.FaClock, 'FFFFFF'), github: await icon(fa.FaGithub, 'FFFFFF'),
    bellBig: await icon(fa.FaBell, HEX.dark), store: await icon(fa.FaStore, 'FFFFFF'), lock: await icon(fa.FaLock, 'FFFFFF'),
  };

  // 1. Portada ---------------------------------------------------------------------------------
  let s = pres.addSlide({ masterName: 'TITULO' });
  s.addText('API REST de seguimiento de precios con alertas', { placeholder: 'title' });
  s.addText([
    { text: 'Taller 3 · SI806 · Desarrollo e integración de una API REST', options: { breakLine: true } },
    { text: 'Brian Curay · Marco Obispo · Niels Chaves' },
  ], { placeholder: 'body' });
  s.addShape(pres.shapes.OVAL, { x: 6.9, y: 1.2, w: 2.6, h: 2.6, fill: { color: HEX.soft }, line: { color: HEX.soft, width: 0 }, objectName: 'Circulo portada' });
  s.addImage({ data: I.bellBig, x: 7.6, y: 1.9, w: 1.2, h: 1.2, altText: 'Campana de alerta de precio', objectName: 'Icono portada' });
  s.addNotes('Presentación del Taller 3: una API REST propia con base de datos y un cliente Android que la consume. Dominio: seguimiento de precios con alertas.');

  // 2. Problema y solución ----------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Problema y solución', { placeholder: 'title' });
  card(s, 'Tarjeta problema', 0.5, 1.3, 4.4, 1.7);
  s.addText('El problema', { x: 0.75, y: 1.4, w: 3.9, h: 0.4, fontSize: 18, bold: true, color: C.accent2, margin: 0, isTextBox: true });
  s.addText('Quien compra no sabe cuándo baja el precio de lo que quiere. Comparar en un momento no basta.', { x: 0.75, y: 1.85, w: 3.9, h: 1.05, fontSize: 15, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  card(s, 'Tarjeta solución', 0.5, 3.2, 4.4, 1.8);
  s.addText('La solución', { x: 0.75, y: 3.3, w: 3.9, h: 0.4, fontSize: 18, bold: true, color: C.accent1, margin: 0, isTextBox: true });
  s.addText('Seguir un producto, guardar su historial de precios y alertar al llegar al objetivo o a su mínimo histórico. Precios reales de tiendas, sin datos simulados.', { x: 0.75, y: 3.75, w: 3.9, h: 1.15, fontSize: 14, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  const stats = [['10', 'endpoints REST'], ['19', 'tablas normalizadas'], ['7', 'reglas de negocio'], ['83', 'pruebas automáticas']];
  stats.forEach(([n, l], i) => {
    const x = 5.2 + (i % 2) * 2.35, y = 1.3 + Math.floor(i / 2) * 1.9;
    card(s, 'Dato ' + l, x, y, 2.2, 1.7, i === 0 ? C.accent1 : C.background1);
    s.addText(n, { x, y: y + 0.2, w: 2.2, h: 0.9, fontSize: 54, bold: true, color: i === 0 ? C.background1 : C.accent1, align: 'center', margin: 0, isTextBox: true });
    s.addText(l, { x: x + 0.1, y: y + 1.1, w: 2.0, h: 0.45, fontSize: 14, color: i === 0 ? C.background1 : C.text1, align: 'center', margin: 0, isTextBox: true });
  });
  s.addNotes('El caso: seguir precios. La API es nueva, versionada en /api/v1. Los números: 10 endpoints, 19 tablas, 7 reglas, 83 pruebas (60 de backend y 23 de Android).');

  // 3. Arquitectura -----------------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Arquitectura en capas', { placeholder: 'title' });
  const layers = [
    ['App Android', 'Retrofit + ViewModel', I.mobile], ['Router', 'Autenticación y ruta', I.server], ['Controller', 'Valida con Zod y fija el código HTTP', I.shield],
    ['Service', 'Reglas de negocio R1–R7', I.cogs], ['Repository', 'Solo SQL y transacciones', I.db],
  ];
  const bw = 1.6, gap = 0.25;
  layers.forEach(([t, d, img], i) => {
    const x = 0.5 + i * (bw + gap);
    card(s, 'Capa ' + t, x, 1.3, bw, 2.05);
    circleIcon(s, 'Icono ' + t, img, x + bw / 2 - 0.3, 1.45, 0.6, HEX.green);
    s.addText(t, { x: x + 0.05, y: 2.15, w: bw - 0.1, h: 0.4, fontSize: 16, bold: true, align: 'center', color: C.text2, margin: 0, isTextBox: true });
    s.addText(d, { x: x + 0.1, y: 2.55, w: bw - 0.2, h: 0.95, fontSize: 13, align: 'center', color: C.text1, margin: 0, valign: 'top', isTextBox: true });
    if (i < layers.length - 1) line(s, x + bw + 0.02, 2.45, x + bw + gap - 0.02, 2.45);
  });
  const ext = [['Clerk', 'El sub del token es el dueño', I.lock, 2.25], ['Tiendas', 'Serper y VTEX: precios reales', I.store, 4.55], ['Postgres', 'Supabase; solo el repository entra', I.db, 6.85]];
  ext.forEach(([t, d, img, x]) => {
    card(s, 'Servicio ' + t, x, 3.8, 2.15, 1.15, C.background1);
    circleIcon(s, 'Icono ' + t, img, x + 0.12, 4.12, 0.5, HEX.orange);
    s.addText(t, { x: x + 0.72, y: 3.9, w: 1.35, h: 0.35, fontSize: 14, bold: true, color: C.text2, margin: 0, valign: 'middle', isTextBox: true });
    s.addText(d, { x: x + 0.72, y: 4.27, w: 1.38, h: 0.62, fontSize: 11, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  });
  line(s, 3.15, 3.37, 3.3, 3.78, HEX.slate);
  line(s, 6.6, 3.37, 5.9, 3.78, HEX.slate);
  line(s, 8.5, 3.37, 8.0, 3.78, HEX.slate);
  s.addNotes('Cada capa tiene una sola responsabilidad. El controller valida la entrada y traduce los errores a códigos HTTP; el service aplica las reglas; solo el repository escribe SQL. Hay un único punto de entrada serverless (api/v1/dispatch.ts) con un router propio.');

  // 4. Modelo de datos --------------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Modelo de datos normalizado', { placeholder: 'title' });
  const nw = 1.7, nh = 0.7, cx = [0.5, 2.55, 4.6], ry = [1.45, 3.9];
  const nodes = { app_users: [0, 0], watches: [1, 0], products: [2, 0], alerts: [0, 1], price_observations: [1, 1], store_listings: [2, 1] };
  Object.entries(nodes).forEach(([n, [c, r]]) => {
    const hot = n === 'watches';
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: cx[c], y: ry[r], w: nw, h: nh, rectRadius: 0.08, fill: { color: hot ? C.accent1 : C.background1 }, line: { color: C.accent1, width: 1.25 }, shadow: shadow(), objectName: 'Tabla ' + n });
    s.addText(n, { x: cx[c], y: ry[r], w: nw, h: nh, fontSize: 13, bold: true, color: hot ? C.background1 : C.text2, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
  });
  line(s, cx[1] - 0.02, ry[0] + nh / 2, cx[0] + nw + 0.02, ry[0] + nh / 2);
  line(s, cx[1] + nw + 0.02, ry[0] + nh / 2, cx[2] - 0.02, ry[0] + nh / 2);
  line(s, cx[2] + nw / 2, ry[1] - 0.02, cx[2] + nw / 2, ry[0] + nh + 0.02);
  line(s, cx[1] + nw + 0.02, ry[1] + nh / 2, cx[2] - 0.02, ry[1] + nh / 2);
  line(s, cx[0] + nw + 0.02, ry[1] + nh / 2, cx[1] - 0.02, ry[1] + nh / 2);
  line(s, cx[0] + nw / 2 + 0.3, ry[1] - 0.02, cx[1] + 0.3, ry[0] + nh + 0.02);
  s.addText('Cada flecha es una clave foránea', { x: 0.5, y: 4.75, w: 5.8, h: 0.3, fontSize: 12, color: C.accent5, italic: true, margin: 0, isTextBox: true });
  const pts = [
    ['3FN', 'La alerta no guarda su mensaje ni el usuario: se derivan del tipo, la observación y el seguimiento.'],
    ['Integridad en la base', 'Únicos, claves foráneas y triggers: un estado inválido se rechaza aunque se escriba por fuera de la API.'],
    ['19 tablas, 5 migraciones', 'Catálogos, productos, precios, seguimientos, alertas y eventos.'],
  ];
  pts.forEach(([t, d], i) => {
    const y = 1.3 + i * 1.25;
    card(s, 'Punto ' + t, 6.55, y, 2.95, 1.1);
    s.addText(t, { x: 6.7, y: y + 0.07, w: 2.65, h: 0.32, fontSize: 14, bold: true, color: C.accent1, margin: 0, isTextBox: true });
    s.addText(d, { x: 6.7, y: y + 0.4, w: 2.7, h: 0.68, fontSize: 11, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  });
  s.addNotes('El modelo está en 3FN. Ejemplo: la alerta no guarda el mensaje ni el usuario. El estado del seguimiento es una clave foránea a un catálogo y sus transiciones válidas viven en la tabla watch_status_transitions; un trigger las hace cumplir. Hay 19 tablas y una vista, en 5 migraciones.');

  // 5. Endpoints --------------------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('10 endpoints: CRUD y operación de negocio', { placeholder: 'title' });
  const colors = { GET: HEX.green, POST: HEX.orange, PATCH: '2F6F8F', DELETE: 'B33A3A' };
  const groups = [
    ['Seguimientos (CRUD)', [['POST', '/watches', '201'], ['GET', '/watches', '200'], ['GET', '/watches/{id}', '200'], ['PATCH', '/watches/{id}', '200'], ['DELETE', '/watches/{id}', '204']]],
    ['Precios y alertas', [['POST', '/watches/{id}/check', 'negocio'], ['GET', '/watches/{id}/history', '200'], ['GET', '/alerts', '200'], ['PATCH', '/alerts/{id}', '200'], ['GET', '/internal/check-due', 'cron']]],
  ];
  groups.forEach(([title, rows], g) => {
    const x = 0.5 + g * 4.6;
    card(s, 'Grupo ' + title, x, 1.25, 4.4, 3.2);
    s.addText(title, { x: x + 0.2, y: 1.32, w: 4.0, h: 0.4, fontSize: 16, bold: true, color: C.text2, margin: 0, isTextBox: true });
    rows.forEach(([m, p, r], i) => {
      const y = 1.8 + i * 0.52;
      s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x + 0.2, y, w: 0.85, h: 0.34, rectRadius: 0.05, fill: { color: colors[m] }, line: { color: colors[m], width: 0 }, objectName: 'Metodo ' + m });
      s.addText(m, { x: x + 0.2, y, w: 0.85, h: 0.34, fontSize: 11, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
      s.addText(p, { x: x + 1.15, y, w: 2.3, h: 0.34, fontSize: 13, color: C.text1, valign: 'middle', margin: 0, isTextBox: true });
      s.addText(r, { x: x + 3.4, y, w: 0.85, h: 0.34, fontSize: 12, bold: true, color: r === 'negocio' ? C.accent2 : C.accent5, align: 'right', valign: 'middle', margin: 0, isTextBox: true });
    });
  });
  s.addText([{ text: 'Códigos HTTP: 200 · 201 · 204 · 400 · 401 · 404 · 405 · 409 · 422 · 429 · 502', options: { breakLine: true } }, { text: 'Documentado en OpenAPI y en Postman (26 peticiones)' }], { x: 0.5, y: 4.6, w: 9, h: 0.5, fontSize: 13, color: C.text1, align: 'left', margin: 0, isTextBox: true });
  s.addNotes('CRUD completo de seguimientos más historial, revisión bajo demanda, alertas y un cron diario. La operación de negocio es POST /watches/{id}/check: consulta las tiendas, guarda observaciones y decide alertas. El cron requiere un secreto.');

  // 6. Reglas de negocio ------------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Reglas de negocio', { placeholder: 'title' });
  const rules = [
    ['R1', 'Un seguimiento por usuario y producto', '409'], ['R2', 'Máximo 20 seguimientos activos', '409'], ['R3', 'Una revisión cada 15 minutos', '429'],
    ['R4', 'Precio ≤ objetivo: alerta y estado «alcanzada»', 'alerta'], ['R5', 'Nuevo mínimo histórico (≥ 3 datos previos)', 'alerta'],
    ['R6', 'Precios atípicos (< 40 % de la mediana) se descartan', 'filtro'], ['R7', 'Solo transiciones de estado válidas', '409 / 422'],
  ];
  rules.forEach(([r, t, res], i) => {
    const y = 1.25 + i * 0.54;
    s.addShape(pres.shapes.OVAL, { x: 0.5, y, w: 0.44, h: 0.44, fill: { color: C.accent1 }, line: { color: C.accent1, width: 0 }, objectName: 'Regla ' + r });
    s.addText(r, { x: 0.5, y, w: 0.44, h: 0.44, fontSize: 12, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText(t, { x: 1.1, y, w: 4.7, h: 0.44, fontSize: 14, color: C.text1, valign: 'middle', margin: 0, isTextBox: true });
    s.addText(res, { x: 5.75, y, w: 0.95, h: 0.44, fontSize: 13, bold: true, color: C.accent2, align: 'right', valign: 'middle', margin: 0, isTextBox: true });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 7.0, y: 1.25, w: 2.5, h: 3.7, rectRadius: 0.1, fill: { color: C.text2 }, line: { color: C.text2, width: 0 }, shadow: shadow(), objectName: 'Panel transaccion' });
  circleIcon(s, 'Icono transaccion', I.check, 8.0, 1.45, 0.6, HEX.green);
  s.addText('Atómico', { x: 7.15, y: 2.2, w: 2.2, h: 0.4, fontSize: 18, bold: true, color: C.background1, align: 'center', margin: 0, isTextBox: true });
  s.addText('Observaciones, alerta, cambio de estado y evento se guardan en una transacción con bloqueo de fila, o no se guarda nada.', { x: 7.2, y: 2.65, w: 2.1, h: 2.1, fontSize: 13, color: C.accent3, align: 'center', margin: 0, valign: 'top', isTextBox: true });
  s.addNotes('R3 protege a las tiendas y la cuota: 429 con Retry-After. R4 y R5 generan las alertas. R6 evita alertas falsas por listados mal emparejados. La revisión usa una transacción con for update, así dos revisiones simultáneas no duplican alertas.');

  // 7. Validación, errores y cliente ------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Validación, errores y cliente Android', { placeholder: 'title' });
  const cols = [
    ['Validación', I.shield, ['Zod en cuerpo, ruta y consulta', 'Un campo desconocido es 400', '400 con detalle por campo']],
    ['Errores del servidor', I.server, ['401, 404, 405, 409 y 422 con mensaje claro', '429 con Retry-After', '502 si las tiendas fallan; 500 sin detalles internos']],
    ['Cliente Android', I.mobile, ['Consume 9 endpoints', 'ApiError tipado: red, respuesta, servidor', 'Mensaje distinto por error y botón que se libera']],
  ];
  cols.forEach(([t, img, items], i) => {
    const x = 0.5 + i * 3.1;
    card(s, 'Columna ' + t, x, 1.25, 2.9, 3.45);
    circleIcon(s, 'Icono ' + t, img, x + 0.2, 1.45, 0.55, HEX.green);
    s.addText(t, { x: x + 0.9, y: 1.45, w: 1.9, h: 0.55, fontSize: 16, bold: true, color: C.text2, valign: 'middle', margin: 0, isTextBox: true });
    s.addText(items.map((it, k) => ({ text: it, options: { bullet: true, breakLine: k < items.length - 1 } })), { x: x + 0.2, y: 2.25, w: 2.55, h: 2.35, fontSize: 14, color: C.text1, valign: 'top', margin: 0, paraSpaceAfter: 8, isTextBox: true });
  });
  s.addNotes('Los errores de dominio se mapean a códigos HTTP en un solo sitio. En el cliente, WatchRepository convierte cualquier fallo en un ApiError: sin conexión, respuesta con forma inesperada, error del servidor, sesión, 404, 409, validación y límite de revisiones.');

  // 8. Pruebas ----------------------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CONTENIDO' });
  s.addText('Pruebas: éxitos y fallos', { placeholder: 'title' });
  card(s, 'Tarjeta grafico', 0.5, 1.25, 5.9, 3.75);
  s.addChart(pres.charts.BAR, [{ name: 'Pruebas', labels: ['Reglas puras', 'Servicio', 'API completa', 'Postgres real', 'Android'], values: [15, 15, 17, 13, 23] }], {
    x: 0.65, y: 1.4, w: 5.6, h: 3.45, barDir: 'bar', chartColors: [HEX.green], showLegend: false, showTitle: false,
    showValue: true, dataLabelPosition: 'outEnd', dataLabelColor: HEX.ink, dataLabelFontSize: 13, dataLabelFontFace: '+mn-lt',
    catAxisLabelColor: HEX.ink, catAxisLabelFontSize: 13, catAxisLabelFontFace: '+mn-lt', catAxisOrientation: 'maxMin',
    valAxisHidden: true, valGridLine: { style: 'none' }, catGridLine: { style: 'none' }, barGapWidthPct: 50,
  });
  card(s, 'Tarjeta total', 6.65, 1.25, 2.85, 1.7, C.accent1);
  s.addText('83', { x: 6.65, y: 1.35, w: 2.85, h: 0.95, fontSize: 56, bold: true, color: C.background1, align: 'center', margin: 0, isTextBox: true });
  s.addText('pruebas pasan', { x: 6.65, y: 2.3, w: 2.85, h: 0.45, fontSize: 15, color: C.background1, align: 'center', margin: 0, isTextBox: true });
  card(s, 'Tarjeta integracion', 6.65, 3.15, 2.85, 1.85);
  s.addText('Contra Postgres real', { x: 6.8, y: 3.25, w: 2.6, h: 0.4, fontSize: 14, bold: true, color: C.accent2, margin: 0, isTextBox: true });
  s.addText([{ text: 'CRUD persistido, R3, R4, R6, atomicidad, cascadas y cron.', options: { breakLine: true } }, { text: 'Cubren 200, 201, 204, 400, 401, 404, 405, 409, 422, 429 y 502.' }], { x: 6.8, y: 3.7, w: 2.6, h: 1.25, paraSpaceAfter: 4, fontSize: 12, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  s.addNotes('60 pruebas de backend (reglas puras, servicio, API completa y 13 de integración contra una base Postgres real) y 23 de Android (repositorio y ViewModel). Cubren los códigos 200, 201, 204, 400, 401, 404, 405, 409, 422, 429 y 502.');

  // 9. Demo y entregables -----------------------------------------------------------------------
  s = pres.addSlide({ masterName: 'CIERRE' });
  s.addText('Demo y entregables', { placeholder: 'title' });
  s.addText('Demo en vivo', { x: 0.5, y: 1.25, w: 4.4, h: 0.4, fontSize: 18, bold: true, color: C.accent6, margin: 0, isTextBox: true });
  const demo = ['Seguir un producto: aparece la fila en Supabase', 'Revisar ahora: precios y alerta guardados', 'Revisar otra vez: error 429 controlado', 'Modo avión: error de conexión', 'Eliminar: desaparece de la base'];
  demo.forEach((t, i) => {
    const y = 1.8 + i * 0.62;
    s.addShape(pres.shapes.OVAL, { x: 0.5, y, w: 0.4, h: 0.4, fill: { color: C.accent2 }, line: { color: C.accent2, width: 0 }, objectName: 'Paso ' + (i + 1) });
    s.addText(String(i + 1), { x: 0.5, y, w: 0.4, h: 0.4, fontSize: 13, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText(t, { x: 1.05, y: y - 0.05, w: 3.9, h: 0.5, fontSize: 14, color: C.background1, valign: 'middle', margin: 0, isTextBox: true });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.3, y: 1.25, w: 4.2, h: 3.75, rectRadius: 0.1, fill: { color: C.background1 }, line: { color: C.background1, width: 0 }, objectName: 'Panel entregables' });
  circleIcon(s, 'Icono github', I.github, 5.55, 1.45, 0.55, HEX.dark);
  s.addText('Entregables en GitHub', { x: 6.25, y: 1.45, w: 3.1, h: 0.55, fontSize: 16, bold: true, color: C.text2, valign: 'middle', margin: 0, isTextBox: true });
  s.addText([
    ['Código del cliente y del backend'], ['README para ejecutarlo'], ['Esquema SQL: db/migrations'], ['OpenAPI y colección de Postman'], ['Documento técnico en PDF'], ['API en producción: Vercel + Supabase'],
  ].map(([t], k, a) => ({ text: t, options: { bullet: true, breakLine: k < a.length - 1 } })), { x: 5.55, y: 2.25, w: 3.75, h: 2.5, fontSize: 14, color: C.text1, valign: 'top', margin: 0, paraSpaceAfter: 8, isTextBox: true });
  s.addNotes('Guion completo en docs/taller3/GUION_DEMO.md. Cada paso de la demo muestra un cambio persistido en la base o un error controlado.');

  const out = path.join(process.env.OUT_DIR, 'Taller3_Presentacion.pptx');
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log('listo', out);
})();
