import { describe, it, expect, vi, beforeEach } from 'vitest';

const { identifyProductMock } = vi.hoisted(() => ({
  identifyProductMock: vi.fn(),
}));
const { buscarPlazaVeaMock } = vi.hoisted(() => ({
  buscarPlazaVeaMock: vi.fn(),
}));
const { buscarWongMock } = vi.hoisted(() => ({
  buscarWongMock: vi.fn(),
}));

vi.mock('../lib/identifyProduct', () => ({
  identifyProduct: identifyProductMock,
}));
vi.mock('../lib/stores/plazaVea', () => ({
  buscarPlazaVea: buscarPlazaVeaMock,
}));
vi.mock('../lib/stores/wong', () => ({
  buscarWong: buscarWongMock,
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

const LOW_CONFIDENCE_IDENTIFICATION = {
  ...VALID_IDENTIFICATION,
  confianza: 0.3,
};

describe('handleScan', () => {
  beforeEach(() => {
    identifyProductMock.mockReset();
    buscarPlazaVeaMock.mockReset();
    buscarWongMock.mockReset();
    buscarPlazaVeaMock.mockResolvedValue(null);
    buscarWongMock.mockResolvedValue(null);
  });

  it('lanza ValidationError si el body no es un objeto', async () => {
    await expect(handleScan(null)).rejects.toThrow(ValidationError);
    await expect(handleScan('texto')).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si falta el campo image', async () => {
    await expect(handleScan({})).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si image no es una data URI de imagen soportada', async () => {
    await expect(handleScan({ image: 'no-es-una-imagen' })).rejects.toThrow(ValidationError);
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

  it('lanza IdentificationError si identifyProduct falla', async () => {
    identifyProductMock.mockRejectedValue(new Error('fallo de IA'));
    await expect(handleScan({ image: 'data:image/jpeg;base64,ABC123' })).rejects.toThrow(IdentificationError);
  });

  it('no busca en las tiendas si la confianza es baja, y devuelve tiendas vacío', async () => {
    identifyProductMock.mockResolvedValue(LOW_CONFIDENCE_IDENTIFICATION);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({ identification: LOW_CONFIDENCE_IDENTIFICATION, tiendas: [] });
    expect(buscarPlazaVeaMock).not.toHaveBeenCalled();
    expect(buscarWongMock).not.toHaveBeenCalled();
  });

  it('busca en las tiendas cuando la confianza es exactamente 0.5 (el umbral)', async () => {
    identifyProductMock.mockResolvedValue({ ...VALID_IDENTIFICATION, confianza: 0.5 });

    await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(buscarPlazaVeaMock).toHaveBeenCalled();
    expect(buscarWongMock).toHaveBeenCalled();
  });

  it('busca en ambas tiendas y agrega los resultados cuando la confianza es suficiente', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);
    buscarPlazaVeaMock.mockResolvedValue({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.5,
      url: 'https://www.plazavea.com.pe/p/1',
    });
    buscarWongMock.mockResolvedValue(null);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({
      identification: VALID_IDENTIFICATION,
      tiendas: [
        {
          tienda: 'Plaza Vea',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.5,
          url: 'https://www.plazavea.com.pe/p/1',
        },
        { tienda: 'Wong', estado: 'no_encontrado' },
      ],
    });
  });

  it('reporta estado error para una tienda que falla, sin afectar a la otra', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);
    buscarPlazaVeaMock.mockRejectedValue(new Error('timeout de red'));
    buscarWongMock.mockResolvedValue({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.6,
      url: 'https://www.wong.pe/p/1',
    });

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({
      identification: VALID_IDENTIFICATION,
      tiendas: [
        { tienda: 'Plaza Vea', estado: 'error', mensaje: 'timeout de red' },
        {
          tienda: 'Wong',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.6,
          url: 'https://www.wong.pe/p/1',
        },
      ],
    });
  });
});
