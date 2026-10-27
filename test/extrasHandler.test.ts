import { describe, it, expect, vi, beforeEach } from 'vitest';

const { generateInsightsMock, buscarTiendasMock, buscarRutaMock } = vi.hoisted(() => ({
  generateInsightsMock: vi.fn(),
  buscarTiendasMock: vi.fn(),
  buscarRutaMock: vi.fn(),
}));

vi.mock('../lib/productInsights', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/productInsights')>()),
  generateInsights: generateInsightsMock,
}));
vi.mock('../lib/stores/nearbyStores', () => ({ buscarTiendasCercanas: buscarTiendasMock }));
vi.mock('../lib/route', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/route')>()),
  buscarRuta: buscarRutaMock,
}));

import { distanciaKm, handleInsights, handleNearbyStores, handleRoute } from '../lib/extrasHandler';
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
    expect(generateInsightsMock).toHaveBeenCalledWith(expect.objectContaining({ marca: 'Gloria' }), 'salud', []);
  });

  it('pasa el perfil limpio: sin repetidos, sin vacíos, sin textos largos ni no-texto', async () => {
    await handleInsights({
      identification: PRODUCTO,
      perfil: ['Bajar azúcar', ' Bajar azúcar ', '', 42, 'x'.repeat(200), 'Deportista'],
    });

    expect(generateInsightsMock).toHaveBeenCalledWith(expect.anything(), 'salud', ['Bajar azúcar', 'Deportista']);
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

describe('handleRoute', () => {
  const YO = { lat: -12.0464, lng: -77.0428 };

  beforeEach(() => {
    buscarRutaMock.mockReset();
    buscarRutaMock.mockResolvedValue(null);
  });

  it('propone ir a pie si la tienda está cerca y en auto si está lejos', async () => {
    await handleRoute({ origen: YO, destino: { lat: -12.0486, lng: -77.0427 } });
    await handleRoute({ origen: YO, destino: { lat: -12.12, lng: -77.03 } });

    expect(buscarRutaMock.mock.calls[0][2]).toBe('pie');
    expect(buscarRutaMock.mock.calls[1][2]).toBe('auto');
  });

  it('rechaza coordenadas inválidas sin llamar al servidor de rutas', async () => {
    await expect(handleRoute({ origen: YO, destino: { lat: 'x', lng: 0 } })).rejects.toThrow(ValidationError);
    await expect(handleRoute({ origen: YO })).rejects.toThrow(ValidationError);
    expect(buscarRutaMock).not.toHaveBeenCalled();
  });

  it('distanciaKm calcula la distancia en línea recta', () => {
    expect(distanciaKm(YO, { lat: -12.04855, lng: -77.04271 })).toBeCloseTo(0.24, 1);
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
