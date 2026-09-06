import { describe, it, expect } from 'vitest';
import { ProductIdentificationSchema } from '../lib/productIdentification';

describe('ProductIdentificationSchema', () => {
  it('acepta un objeto válido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
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

  it('rechaza si falta un campo requerido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      confianza: 0.9,
    });
    expect(result.success).toBe(false);
  });
});
