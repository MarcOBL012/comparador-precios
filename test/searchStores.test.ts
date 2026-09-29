import { describe, it, expect, vi, beforeEach } from 'vitest';

const mocks = vi.hoisted(() => ({
  plazaVea: vi.fn(),
  wong: vi.fn(),
  metro: vi.fn(),
  oechsle: vi.fn(),
  promart: vi.fn(),
  googleShopping: vi.fn(),
  googleSearch: vi.fn(),
}));

vi.mock('../lib/stores/plazaVea', () => ({ buscarPlazaVea: mocks.plazaVea }));
vi.mock('../lib/stores/wong', () => ({ buscarWong: mocks.wong }));
vi.mock('../lib/stores/metro', () => ({ buscarMetro: mocks.metro }));
vi.mock('../lib/stores/oechsle', () => ({ buscarOechsle: mocks.oechsle }));
vi.mock('../lib/stores/promart', () => ({ buscarPromart: mocks.promart }));
vi.mock('../lib/stores/googleShopping', () => ({ buscarEnGoogleShopping: mocks.googleShopping }));
vi.mock('../lib/stores/googleSearch', () => ({ buscarLinkComoUltimoRecurso: mocks.googleSearch }));

import { parseDeviceContext, routeFor, searchStores } from '../lib/searchStores';
import type { ProductQuery } from '../lib/productIdentification';

const LECHE: ProductQuery = { marca: 'Gloria', nombre: 'Leche evaporada', presentacion: '400g', categoria: 'abarrotes' };
const MOUSE: ProductQuery = { marca: 'Logitech', nombre: 'Mouse M190', presentacion: '', categoria: 'tecnologia' };
const NORMAL = { ahorroBateria: false };
const WEB_OFFER = { tienda: 'Sodimac', estado: 'encontrado', producto: 'Mouse Logitech M190', precio: 51.5, url: 'https://g.co/x', fuente: 'web' };

describe('routeFor', () => {
  it('manda los abarrotes a supermercados, sin búsqueda web', () => {
    expect(routeFor('abarrotes')).toEqual({ tiendas: ['Plaza Vea', 'Wong', 'Metro'], web: false });
  });

  it('manda tecnología a tiendas por departamento, con búsqueda web', () => {
    expect(routeFor('tecnologia')).toEqual({ tiendas: ['Plaza Vea', 'Oechsle', 'Promart'], web: true });
  });

  it('trata una categoría desconocida (escaneos antiguos) como "otros"', () => {
    expect(routeFor('electrodomésticos')).toEqual(routeFor('otros'));
  });

  it('con dos categorías distintas consulta las tiendas de ambas, sin repetir', () => {
    expect(routeFor('tecnologia', 'abarrotes')).toEqual({
      tiendas: ['Plaza Vea', 'Oechsle', 'Promart', 'Wong', 'Metro'],
      web: true,
    });
  });
});

describe('parseDeviceContext', () => {
  it('solo activa el ahorro de batería con un true explícito', () => {
    expect(parseDeviceContext({ ahorroBateria: true })).toEqual({ ahorroBateria: true });
    expect(parseDeviceContext({ ahorroBateria: 'si' })).toEqual({ ahorroBateria: false });
    expect(parseDeviceContext(undefined)).toEqual({ ahorroBateria: false });
  });
});

describe('searchStores', () => {
  beforeEach(() => {
    for (const mock of Object.values(mocks)) {
      mock.mockReset();
      mock.mockResolvedValue(null);
    }
    mocks.googleShopping.mockResolvedValue([WEB_OFFER]);
    mocks.googleSearch.mockResolvedValue([]);
  });

  it('consulta solo los supermercados para abarrotes y no gasta la búsqueda web', async () => {
    mocks.plazaVea.mockResolvedValue({ producto: 'Leche Gloria 400g', precio: 4.5, url: 'https://pv/p/1' });

    const result = await searchStores(LECHE, NORMAL, { serperApiKey: 'key' });

    expect(result).toEqual({
      tiendas: [
        { tienda: 'Plaza Vea', estado: 'encontrado', producto: 'Leche Gloria 400g', precio: 4.5, url: 'https://pv/p/1' },
        { tienda: 'Wong', estado: 'no_encontrado' },
        { tienda: 'Metro', estado: 'no_encontrado' },
      ],
      busquedaWeb: 'no_aplica',
    });
    expect(mocks.oechsle).not.toHaveBeenCalled();
    expect(mocks.googleShopping).not.toHaveBeenCalled();
  });

  it('suma las ofertas web a las tiendas directas para tecnología, excluyendo las ya consultadas', async () => {
    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('realizada');
    expect(result.tiendas.map((t) => t.tienda)).toEqual(['Plaza Vea', 'Oechsle', 'Promart', 'Sodimac']);
    expect(mocks.googleShopping).toHaveBeenCalledWith(MOUSE, { apiKey: 'key', excluir: ['Plaza Vea', 'Oechsle', 'Promart'] });
    expect(mocks.wong).not.toHaveBeenCalled();
    expect(mocks.googleSearch).not.toHaveBeenCalled();
  });

  it('si la categoría elegida no coincide con la identificada, busca en las tiendas de ambas', async () => {
    await searchStores(LECHE, NORMAL, { serperApiKey: 'key', categoriaElegida: 'tecnologia' });

    expect(mocks.wong).toHaveBeenCalled();
    expect(mocks.oechsle).toHaveBeenCalled();
  });

  it('omite la búsqueda web cuando el celular está en ahorro de batería', async () => {
    const result = await searchStores(MOUSE, { ahorroBateria: true }, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('omitida_por_bateria');
    expect(result.tiendas).toHaveLength(3);
    expect(mocks.googleShopping).not.toHaveBeenCalled();
  });

  it('no hace búsqueda web si el backend no tiene key de Serper', async () => {
    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: null });

    expect(result.busquedaWeb).toBe('no_aplica');
    expect(mocks.googleShopping).not.toHaveBeenCalled();
  });

  it('si la búsqueda web falla, devuelve igual las tiendas directas', async () => {
    mocks.googleShopping.mockRejectedValue(new Error('403'));
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('error');
    expect(result.tiendas).toHaveLength(3);
    expect(mocks.googleSearch).not.toHaveBeenCalled();
  });

  it('si Shopping no encuentra nada, intenta una búsqueda web normal como último recurso', async () => {
    mocks.googleShopping.mockResolvedValue([]);
    mocks.googleSearch.mockResolvedValue([
      { tienda: 'Tottus', estado: 'encontrado', producto: 'Mouse Logitech M190', url: 'https://tottus.pe/p/1', fuente: 'busqueda_web' },
    ]);

    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('realizada');
    expect(result.tiendas.map((t) => t.tienda)).toEqual(['Plaza Vea', 'Oechsle', 'Promart', 'Tottus']);
    expect(result.tiendas.at(-1)).toEqual({
      tienda: 'Tottus',
      estado: 'encontrado',
      producto: 'Mouse Logitech M190',
      url: 'https://tottus.pe/p/1',
      fuente: 'busqueda_web',
    });
    expect(mocks.googleSearch).toHaveBeenCalledWith(MOUSE, { apiKey: 'key', excluir: ['Plaza Vea', 'Oechsle', 'Promart'] });
  });

  it('si ni Shopping ni el último recurso encuentran nada, solo quedan las tiendas directas', async () => {
    mocks.googleShopping.mockResolvedValue([]);
    mocks.googleSearch.mockResolvedValue([]);

    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('realizada');
    expect(result.tiendas.map((t) => t.tienda)).toEqual(['Plaza Vea', 'Oechsle', 'Promart']);
  });

  it('si el último recurso falla, se marca error pero las tiendas directas se devuelven igual', async () => {
    mocks.googleShopping.mockResolvedValue([]);
    mocks.googleSearch.mockRejectedValue(new Error('timeout'));
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const result = await searchStores(MOUSE, NORMAL, { serperApiKey: 'key' });

    expect(result.busquedaWeb).toBe('error');
    expect(result.tiendas).toHaveLength(3);
  });

  it('marca como error la tienda que falla sin afectar a las demás', async () => {
    mocks.wong.mockRejectedValue(new Error('timeout de red'));

    const result = await searchStores(LECHE, NORMAL, { serperApiKey: 'key' });

    expect(result.tiendas[1]).toEqual({ tienda: 'Wong', estado: 'error', mensaje: 'timeout de red' });
  });
});
