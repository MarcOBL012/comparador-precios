import { buscarPlazaVea } from '../lib/stores/plazaVea.js';
import { buscarWong } from '../lib/stores/wong.js';
import type { ProductIdentification } from '../lib/productIdentification.js';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

const [plazaVea, wong] = await Promise.all([
  buscarPlazaVea(identification).catch((err: unknown) => ({ error: String(err) })),
  buscarWong(identification).catch((err: unknown) => ({ error: String(err) })),
]);

console.log('Plaza Vea:', JSON.stringify(plazaVea, null, 2));
console.log('Wong:', JSON.stringify(wong, null, 2));
