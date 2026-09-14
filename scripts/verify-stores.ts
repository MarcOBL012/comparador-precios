// Consulta real a las tiendas (sin IA). La búsqueda web solo corre con SERPER_API_KEY en el entorno.
// Uso: npx tsx scripts/verify-stores.ts
import { searchStores } from '../lib/searchStores.js';
import type { ProductQuery } from '../lib/productIdentification.js';

const productos: ProductQuery[] = [
  { marca: 'Gloria', nombre: 'Leche evaporada entera', presentacion: '400g', categoria: 'abarrotes', tipo: 'leche evaporada' },
  { marca: 'Logitech', nombre: 'Mouse inalámbrico M190', presentacion: '', categoria: 'tecnologia', tipo: 'mouse' },
  { marca: 'JBL', nombre: 'Audífonos Tune 520BT', presentacion: '', categoria: 'tecnologia', tipo: 'audífonos' },
  { marca: 'Oster', nombre: 'Licuadora', presentacion: '1.5L', categoria: 'electrohogar', tipo: 'licuadora' },
];

for (const producto of productos) {
  const inicio = Date.now();
  const { tiendas, busquedaWeb } = await searchStores(producto, { ahorroBateria: false });
  console.log(`\n${producto.marca} ${producto.nombre} [${producto.categoria}] búsqueda web: ${busquedaWeb}, ${Date.now() - inicio} ms`);
  for (const t of tiendas) {
    const detalle = t.estado === 'encontrado' ? `S/ ${t.precio}  ${t.producto}` : (t.mensaje ?? '');
    console.log(`  ${t.tienda.padEnd(12)} ${t.estado.padEnd(13)} ${detalle}${t.fuente ? '  (web)' : ''}`);
  }
}
