import { describe, it, expect, vi, beforeEach } from 'vitest';

const { identifyProductMock, searchStoresMock } = vi.hoisted(() => ({
  identifyProductMock: vi.fn(),
  searchStoresMock: vi.fn(),
}));

vi.mock('../lib/identifyProduct', () => ({
  identifyProduct: identifyProductMock,
}));
vi.mock('../lib/searchStores', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/searchStores')>()),
  searchStores: searchStoresMock,
}));

import { handlePrices, handleScan } from '../lib/scanHandler';
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

const SEARCH_RESULT = {
  tiendas: [{ tienda: 'Plaza Vea', estado: 'encontrado', producto: 'Leche Evaporada Gloria 400g', precio: 4.5, url: 'https://pv/p/1' }],
  busquedaWeb: 'no_aplica',
};

const IMAGE = 'data:image/jpeg;base64,ABC123';

describe('handleScan', () => {
  beforeEach(() => {
    identifyProductMock.mockReset();
    searchStoresMock.mockReset();
    searchStoresMock.mockResolvedValue(SEARCH_RESULT);
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
    await expect(handleScan({ image: IMAGE })).rejects.toThrow(IdentificationError);
  });

  it('no busca en las tiendas si la confianza es baja, y devuelve tiendas vacío', async () => {
    identifyProductMock.mockResolvedValue(LOW_CONFIDENCE_IDENTIFICATION);

    const result = await handleScan({ image: IMAGE });

    expect(result).toEqual({ identification: LOW_CONFIDENCE_IDENTIFICATION, tiendas: [], busquedaWeb: 'no_aplica' });
    expect(searchStoresMock).not.toHaveBeenCalled();
  });

  it('busca en las tiendas cuando la confianza es exactamente 0.5 (el umbral)', async () => {
    identifyProductMock.mockResolvedValue({ ...VALID_IDENTIFICATION, confianza: 0.5 });

    await handleScan({ image: IMAGE });

    expect(searchStoresMock).toHaveBeenCalled();
  });

  it('devuelve la identificación junto al resultado de la búsqueda en tiendas', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);

    const result = await handleScan({ image: IMAGE });

    expect(result).toEqual({ identification: VALID_IDENTIFICATION, ...SEARCH_RESULT });
    expect(searchStoresMock).toHaveBeenCalledWith(VALID_IDENTIFICATION, { ahorroBateria: false }, { categoriaElegida: null });
  });

  it('pasa la categoría elegida antes de la foto a la IA y a la búsqueda, e ignora una inválida', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);

    await handleScan({ image: IMAGE, categoria: 'tecnologia' });
    await handleScan({ image: IMAGE, categoria: 'cosas raras' });

    expect(identifyProductMock).toHaveBeenNthCalledWith(1, IMAGE, 'tecnologia');
    expect(searchStoresMock).toHaveBeenNthCalledWith(1, VALID_IDENTIFICATION, { ahorroBateria: false }, { categoriaElegida: 'tecnologia' });
    expect(identifyProductMock).toHaveBeenNthCalledWith(2, IMAGE, null);
  });

  it('pasa el contexto del dispositivo a la búsqueda', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);

    await handleScan({ image: IMAGE, contexto: { ahorroBateria: true } });

    expect(searchStoresMock).toHaveBeenCalledWith(VALID_IDENTIFICATION, { ahorroBateria: true }, { categoriaElegida: null });
  });
});

describe('handlePrices', () => {
  beforeEach(() => {
    searchStoresMock.mockReset();
    searchStoresMock.mockResolvedValue(SEARCH_RESULT);
  });

  it('busca precios de una identificación ya conocida, aceptando categorías de escaneos antiguos', async () => {
    const identification = { ...VALID_IDENTIFICATION, categoria: 'electrodomésticos' };

    const result = await handlePrices({ identification, contexto: { ahorroBateria: true } });

    expect(result).toEqual(SEARCH_RESULT);
    expect(searchStoresMock).toHaveBeenCalledWith(
      { marca: 'Gloria', nombre: 'Leche evaporada', presentacion: '400g', categoria: 'electrodomésticos', tipo: '' },
      { ahorroBateria: true }
    );
  });

  it('lanza ValidationError si falta la identificación o está incompleta', async () => {
    await expect(handlePrices({})).rejects.toThrow(ValidationError);
    await expect(handlePrices({ identification: { marca: 'Gloria' } })).rejects.toThrow(ValidationError);
    expect(searchStoresMock).not.toHaveBeenCalled();
  });

  it('lanza ValidationError si no hay marca ni nombre con qué buscar', async () => {
    const identification = { marca: ' ', nombre: '', presentacion: '', categoria: 'otros' };
    await expect(handlePrices({ identification })).rejects.toThrow(ValidationError);
  });
});
