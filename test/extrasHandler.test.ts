import { describe, it, expect, vi, beforeEach } from 'vitest';

const { generateInsightsMock, buscarTiendasMock } = vi.hoisted(() => ({
  generateInsightsMock: vi.fn(),
  buscarTiendasMock: vi.fn(),
}));

vi.mock('../lib/productInsights', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/productInsights')>()),
  generateInsights: generateInsightsMock,
}));
vi.mock('../lib/stores/nearbyStores', () => ({ buscarTiendasCercanas: buscarTiendasMock }));

import { handleInsights, handleNearbyStores } from '../lib/extrasHandler';
import { ValidationError } from '../lib/errors';

const PRODUCTO = { marca: 'Gloria', nombre: 'Leche evaporada', presentacion: '400g', categoria: 'abarrotes', tipo: 'leche evaporada' };

describe('handleInsights', () => {
  beforeEach(() => {
    generateInsightsMock.mockReset();
    generateInsightsMock.mockResolvedValue({ titular: 'x' });
  });

  it('sin modo elige el que corresponde a la categoría', async () => {
    const result = await handleInsights({ identification: PRODUCTO });

    expect(result.modo).toBe('salud');
    expect(generateInsightsMock).toHaveBeenCalledWith(expect.objectContaining({ marca: 'Gloria' }), 'salud');
  });

  it('respeta el modo que eligió la persona', async () => {
    const result = await handleInsights({ identification: PRODUCTO, modo: 'curiosidades' });

    expect(result.modo).toBe('curiosidades');
  });

  it('rechaza un modo desconocido o un producto sin marca ni nombre', async () => {
    await expect(handleInsights({ identification: PRODUCTO, modo: 'deportes' })).rejects.toThrow(ValidationError);
    await expect(handleInsights({ identification: { ...PRODUCTO, marca: '', nombre: '' } })).rejects.toThrow(ValidationError);
    expect(generateInsightsMock).not.toHaveBeenCalled();
  });
});

describe('handleNearbyStores', () => {
  beforeEach(() => {
    buscarTiendasMock.mockReset();
    buscarTiendasMock.mockResolvedValue([]);
  });

  it('busca las tiendas de las cadenas pedidas en la ubicación dada', async () => {
    await handleNearbyStores({ cadenas: ['Plaza Vea'], lat: -12.05, lng: -77.04 });

    expect(buscarTiendasMock).toHaveBeenCalledWith(['Plaza Vea'], { lat: -12.05, lng: -77.04 });
  });

  it('rechaza coordenadas inválidas o una lista de cadenas vacía', async () => {
    await expect(handleNearbyStores({ cadenas: ['Plaza Vea'], lat: 200, lng: -77 })).rejects.toThrow(ValidationError);
    await expect(handleNearbyStores({ cadenas: [], lat: -12, lng: -77 })).rejects.toThrow(ValidationError);
    await expect(handleNearbyStores({ cadenas: ['Plaza Vea'], lat: '-12', lng: -77 })).rejects.toThrow(ValidationError);
    expect(buscarTiendasMock).not.toHaveBeenCalled();
  });
});
