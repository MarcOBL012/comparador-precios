import { describe, it, expect, vi, beforeEach } from 'vitest';

const { identifyProductMock } = vi.hoisted(() => ({
  identifyProductMock: vi.fn(),
}));

vi.mock('../lib/identifyProduct', () => ({
  identifyProduct: identifyProductMock,
}));

import { handleScan } from '../lib/scanHandler';
import { ValidationError, IdentificationError } from '../lib/errors';

const VALID_IDENTIFICATION = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.92,
};

describe('handleScan', () => {
  beforeEach(() => {
    identifyProductMock.mockReset();
  });

  it('lanza ValidationError si el body no es un objeto', async () => {
    await expect(handleScan(null)).rejects.toThrow(ValidationError);
    await expect(handleScan('texto')).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si falta el campo image', async () => {
    await expect(handleScan({})).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si image no es una data URI de imagen', async () => {
    await expect(handleScan({ image: 'no-es-una-imagen' })).rejects.toThrow(ValidationError);
  });

  it('devuelve la identificación cuando el body es válido', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({ identification: VALID_IDENTIFICATION });
    expect(identifyProductMock).toHaveBeenCalledWith('data:image/jpeg;base64,ABC123');
  });

  it('lanza IdentificationError si identifyProduct falla', async () => {
    identifyProductMock.mockRejectedValue(new Error('fallo de IA'));

    await expect(handleScan({ image: 'data:image/jpeg;base64,ABC123' })).rejects.toThrow(IdentificationError);
  });

  it('rechaza un tipo de imagen no soportado', async () => {
    await expect(handleScan({ image: 'data:image/tiff;base64,ABC123' })).rejects.toThrow(ValidationError);
  });

  it('rechaza una data URI con payload base64 vacío', async () => {
    await expect(handleScan({ image: 'data:image/jpeg;base64,' })).rejects.toThrow(ValidationError);
  });

  it('rechaza una imagen que excede el tamaño máximo', async () => {
    const oversized = 'data:image/jpeg;base64,' + 'A'.repeat(4_000_001);
    await expect(handleScan({ image: oversized })).rejects.toThrow(ValidationError);
  });
});
