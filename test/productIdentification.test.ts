import { describe, it, expect } from 'vitest';
import { ProductIdentificationSchema, ProductQuerySchema } from '../lib/productIdentification';

describe('ProductIdentificationSchema', () => {
  it('acepta un objeto válido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      tipo: 'leche evaporada',
      confianza: 0.92,
    });
    expect(result.success).toBe(true);
  });

  it('rechaza confianza mayor a 1', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 1.5,
    });
    expect(result.success).toBe(false);
  });

  it('rechaza confianza negativa', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: -0.1,
    });
    expect(result.success).toBe(false);
  });

  it('rechaza una categoría fuera de la lista fija', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'lácteos y derivados',
      confianza: 0.9,
    });
    expect(result.success).toBe(false);
  });

  it('ProductQuerySchema acepta categorías de texto libre (escaneos antiguos)', () => {
    const result = ProductQuerySchema.safeParse({
      marca: 'Oster',
      nombre: 'Licuadora',
      presentacion: '',
      categoria: 'electrodomésticos',
    });
    expect(result.success).toBe(true);
  });

  it('rechaza si falta un campo requerido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      confianza: 0.9,
    });
    expect(result.success).toBe(false);
  });
});
