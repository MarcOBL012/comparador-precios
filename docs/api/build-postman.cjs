// Genera docs/api/postman_collection.json: node docs/api/build-postman.cjs
// Cada petición trae pruebas de código HTTP; las ids creadas se guardan en variables de la colección.
const fs = require('node:fs');
const path = require('node:path');

const json = (obj) => JSON.stringify(obj, null, 2);
const test = (name, code) => ({ listen: 'test', script: { type: 'text/javascript', exec: [`pm.test(${JSON.stringify(name)}, function () {`, ...code.map((l) => '  ' + l), '});'] } });
const status = (n) => test(`Responde ${n}`, [`pm.response.to.have.status(${n});`]);

function req(name, method, p, { body, query, auth = 'user', tests = [], desc } = {}) {
  const headers = auth === 'cron'
    ? [{ key: 'Authorization', value: 'Bearer {{cronSecret}}' }]
    : [{ key: 'X-Dev-User', value: '{{devUser}}' }];
  if (body !== undefined) headers.push({ key: 'Content-Type', value: 'application/json' });
  return {
    name,
    event: tests,
    request: {
      method,
      header: headers,
      description: desc,
      url: {
        raw: `{{baseUrl}}${p}${query ? '?' + query.map((q) => q.join('=')).join('&') : ''}`,
        host: ['{{baseUrl}}'],
        path: p.split('/').filter(Boolean),
        query: query ? query.map(([key, value]) => ({ key, value })) : undefined,
      },
      body: body !== undefined ? { mode: 'raw', raw: json(body), options: { raw: { language: 'json' } } } : undefined,
    },
  };
}

const producto = { marca: 'Free Tea', nombre: 'Frutos Rojos', presentacion: '500ml', categoria: 'bebidas', tipo: 'té' };
const saveId = test('Guarda el id', ['pm.collectionVariables.set("watchId", pm.response.json().id);']);

const collection = {
  info: {
    name: 'API de seguimiento de precios (Taller 3)',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: 'Ejecuta la carpeta completa con el Runner de Postman: cubre éxitos y fallos de los 10 endpoints. Requiere el servidor con AUTH_DEV_MODE=1 (local) o un token de Clerk.',
  },
  variable: [
    { key: 'baseUrl', value: 'http://localhost:3000/api/v1' },
    { key: 'devUser', value: 'postman' },
    { key: 'cronSecret', value: 'cambia-esto' },
    { key: 'watchId', value: '' },
    { key: 'alertId', value: '' },
  ],
  item: [
    {
      name: '1. Seguimientos (CRUD)',
      item: [
        req('Crear seguimiento', 'POST', '/watches', { body: { producto, precioObjetivo: 2.5 }, tests: [status(201), saveId, test('Trae Location', ['pm.expect(pm.response.headers.get("Location")).to.include("/api/v1/watches/");'])] }),
        req('Crear repetido → 409', 'POST', '/watches', { body: { producto, precioObjetivo: 2.5 }, tests: [status(409), test('codigo ya_seguido', ['pm.expect(pm.response.json().codigo).to.eql("ya_seguido");'])] }),
        req('Crear con datos inválidos → 400', 'POST', '/watches', { body: { producto: { nombre: '', categoria: 'inventada' }, precioObjetivo: -3 }, tests: [status(400), test('Detalla los campos', ['pm.expect(pm.response.json().detalles.length).to.be.above(0);'])] }),
        req('Listar', 'GET', '/watches', { query: [['limit', '10']], tests: [status(200)] }),
        req('Listar con parámetro inválido → 400', 'GET', '/watches', { query: [['limit', '500']], tests: [status(400)] }),
        req('Detalle', 'GET', '/watches/{{watchId}}', { tests: [status(200)] }),
        req('Detalle de id inexistente → 404', 'GET', '/watches/00000000-0000-4000-8000-000000000000', { tests: [status(404)] }),
        req('Detalle con id mal formado → 400', 'GET', '/watches/no-es-uuid', { tests: [status(400)] }),
        req('Cambiar objetivo', 'PATCH', '/watches/{{watchId}}', { body: { precioObjetivo: 2.0 }, tests: [status(200)] }),
        req('Pausar', 'PATCH', '/watches/{{watchId}}', { body: { estado: 'pausada' }, tests: [status(200)] }),
        req('Estado que el usuario no puede pedir → 400', 'PATCH', '/watches/{{watchId}}', { body: { estado: 'alcanzada' }, tests: [status(400)] }),
        req('Revisar un seguimiento pausado → 409', 'POST', '/watches/{{watchId}}/check', { tests: [status(409)] }),
        req('Reactivar', 'PATCH', '/watches/{{watchId}}', { body: { estado: 'activa' }, tests: [status(200)] }),
        req('Sin sesión → 401', 'GET', '/watches', { tests: [status(401)], desc: 'Quita el encabezado X-Dev-User de esta petición.' }),
        req('Método no permitido → 405', 'PUT', '/watches', { tests: [status(405)] }),
      ],
    },
    {
      name: '2. Precios y alertas (lógica de negocio)',
      item: [
        req('Revisar ahora', 'POST', '/watches/{{watchId}}/check', { tests: [test('200 (ofertas) o 502 (tiendas caídas)', ['pm.expect([200, 502]).to.include(pm.response.code);'])], desc: 'Consulta las tiendas reales: 502 si no responden.' }),
        req('Revisar otra vez enseguida → 429', 'POST', '/watches/{{watchId}}/check', { tests: [test('429 con Retry-After (o 502)', ['if (pm.response.code === 429) { pm.expect(pm.response.headers.get("Retry-After")).to.be.ok; } else { pm.expect(pm.response.code).to.eql(502); }'])] }),
        req('Historial', 'GET', '/watches/{{watchId}}/history', { query: [['days', '30']], tests: [status(200), test('Trae estadísticas', ['pm.expect(pm.response.json().estadisticas).to.have.property("tendencia");'])] }),
        req('Historial con days inválido → 400', 'GET', '/watches/{{watchId}}/history', { query: [['days', '9999']], tests: [status(400)] }),
        req('Listar alertas', 'GET', '/alerts', { query: [['unread', 'false']], tests: [status(200), test('Guarda la primera alerta si hay', ['const items = pm.response.json().items; if (items.length) pm.collectionVariables.set("alertId", items[0].id);'])] }),
        req('Marcar alerta como leída', 'PATCH', '/alerts/{{alertId}}', { body: { leida: true }, tests: [test('200 si hay alerta, 404 si no', ['pm.expect([200, 404]).to.include(pm.response.code);'])] }),
        req('Marcar alerta con cuerpo inválido → 400', 'PATCH', '/alerts/1', { body: { leida: 'si' }, tests: [status(400)] }),
      ],
    },
    {
      name: '3. Limpieza y cron',
      item: [
        req('Cron sin secreto → 401', 'GET', '/internal/check-due', { auth: 'user', tests: [status(401)] }),
        req('Cron con secreto', 'GET', '/internal/check-due', { auth: 'cron', tests: [status(200), test('Resume la corrida', ['pm.expect(pm.response.json()).to.have.property("revisados");'])] }),
        req('Eliminar seguimiento', 'DELETE', '/watches/{{watchId}}', { tests: [status(204)] }),
        req('Eliminar otra vez → 404', 'DELETE', '/watches/{{watchId}}', { tests: [status(404)] }),
      ],
    },
  ],
};

// La petición "Sin sesión" no debe mandar el encabezado de desarrollo.
const noAuth = collection.item[0].item.find((i) => i.name === 'Sin sesión → 401');
noAuth.request.header = [];

fs.writeFileSync(path.join(__dirname, 'postman_collection.json'), json(collection) + '\n');
console.log('postman_collection.json generado con', collection.item.reduce((n, f) => n + f.item.length, 0), 'peticiones');
