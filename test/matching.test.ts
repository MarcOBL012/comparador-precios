import { describe, it, expect } from 'vitest';
import { pickBestMatch } from '../lib/matching';
import type { ProductIdentification } from '../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  tipo: 'leche evaporada',
  confianza: 0.9,
};

describe('pickBestMatch', () => {
  it('elige el candidato con mayor similitud por encima del umbral', () => {
    const candidates = [
      { productName: 'Leche Evaporada Ideal 400g', brand: 'Ideal' },
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[1]);
  });

  it('devuelve null si ningún candidato supera el umbral', () => {
    const candidates = [{ productName: 'Detergente Ariel 800g', brand: 'Ariel' }];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('devuelve null si no hay candidatos', () => {
    expect(pickBestMatch(identification, [])).toBeNull();
  });

  it('ignora mayúsculas y acentos al comparar', () => {
    const candidates = [{ productName: 'LECHE EVAPORADA GLÓRIA 400G', brand: 'GLORIA' }];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });

  it('conserva campos extra del candidato en el resultado', () => {
    const candidates = [
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria', precio: 4.5, url: 'https://example.pe/p/1' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });

  it('descarta un candidato de otra marca aunque el texto sea similar', () => {
    const candidates = [{ productName: 'Leche Evaporada Laive 400g', brand: 'Laive' }];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('penaliza candidatos de pack/multipack cuando la identificación no menciona pack', () => {
    const candidates = [
      { productName: 'Leche Evaporada Entera Gloria Lata 390g Paquete 6un', brand: 'Gloria' },
      { productName: 'Pack x6 Leche Evaporada Gloria Entera', brand: 'Gloria' },
    ];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('desempata a favor del candidato cuya categoría en la tienda coincide con lo que se busca', () => {
    const mouse = { marca: 'Logitech', nombre: 'Mouse', presentacion: '', categoria: 'tecnologia' };
    const candidates = [
      { productName: 'Teclado Logitech + Mouse MK120', brand: 'Logitech', categories: ['/Tecnología/Computo/Teclados/'] },
      { productName: 'Logitech Mouse Inalámbrico M190', brand: 'Logitech', categories: ['/Tecnología/Computo/Mouses/'] },
    ];

    expect(pickBestMatch(mouse, candidates)).toEqual(candidates[1]);
  });

  it('descarta un producto de otro tipo aunque comparta marca y palabras (leche UHT no es leche evaporada)', () => {
    const query = { marca: 'Gloria', nombre: 'Leche evaporada entera', presentacion: '400g', categoria: 'abarrotes', tipo: 'leche evaporada' };
    const candidates = [{ productName: 'Leche Entera UHT GLORIA Caja 946ml', brand: 'GLORIA' }];

    expect(pickBestMatch(query, candidates)).toBeNull();
  });

  it('exige el tipo aunque el modelo coincida (una funda no es el celular)', () => {
    const query = { marca: 'Samsung', nombre: 'Galaxy A15', presentacion: '128GB', categoria: 'tecnologia', tipo: 'celular' };
    const candidates = [
      { productName: 'Case Samsung Galaxy A15 Transparente', brand: 'Samsung' },
      { productName: 'Smartphone Samsung Galaxy A15 128GB Negro', brand: 'Samsung' },
    ];

    expect(pickBestMatch(query, candidates)).toEqual(candidates[1]);
  });

  it('acepta sinónimos comunes del tipo (auriculares por audífonos)', () => {
    const query = { marca: 'JBL', nombre: 'Tune 520BT', presentacion: '', categoria: 'tecnologia', tipo: 'audífonos' };
    const candidates = [{ productName: 'JBL Auriculares Inalámbricos Tune 520BT', brand: 'JBL' }];

    expect(pickBestMatch(query, candidates)).toEqual(candidates[0]);
  });

  it('acepta el empaque en inglés cuando Gemini describe el tipo en español (Free Tea / té)', () => {
    const query = { marca: 'AJE', nombre: 'Free Tea Frutos Rojos', presentacion: '500ml', categoria: 'bebidas', tipo: 'té' };
    const candidates = [{ productName: 'AJE Free Tea Frutos Rojos 500ml', brand: '' }];

    expect(pickBestMatch(query, candidates)).toEqual(candidates[0]);
  });

  it('descarta un candidato con otro código de modelo', () => {
    const query = { marca: 'Logitech', nombre: 'Mouse inalámbrico M190', presentacion: '', categoria: 'tecnologia', tipo: 'mouse' };
    const candidates = [
      { productName: 'Teclado mas Mouse Logitech Inalámbrico MK235', brand: 'Logitech' },
      { productName: 'Mouse Logitech G203 Inalámbrico', brand: 'Logitech' },
    ];

    expect(pickBestMatch(query, candidates)).toBeNull();
  });

  it('no confunde cantidades con códigos de modelo, ni descarta títulos que no mencionan modelo', () => {
    const query = { marca: 'Oster', nombre: 'Licuadora BLSTKAG', presentacion: '1.5L', categoria: 'electrohogar', tipo: 'licuadora' };
    const candidates = [{ productName: 'Licuadora OSTER 1.5L 2174286 Rojo', brand: 'OSTER' }];

    expect(pickBestMatch(query, candidates)).toEqual(candidates[0]);
  });

  it('la categoría no rescata un pack cuando se buscaba la unidad', () => {
    const candidates = [
      { productName: 'Pack x6 Leche Evaporada Gloria Entera', brand: 'Gloria', categories: ['/Lácteos/Leches Evaporadas/'] },
    ];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('la categoría no rescata un candidato de otra marca', () => {
    const candidates = [
      { productName: 'Leche Evaporada Laive 400g', brand: 'Laive', categories: ['/Abarrotes/Leche Evaporada/'] },
    ];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('prefiere la unidad individual sobre el pack cuando ambas están disponibles', () => {
    const candidates = [
      { productName: 'Pack x6 Leche Evaporada Gloria Entera', brand: 'Gloria' },
      { productName: 'Leche Evaporada Entera Gloria Lata 400g', brand: 'Gloria' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[1]);
  });
});
