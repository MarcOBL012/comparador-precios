/**
 * Tiendas físicas más cercanas a la persona, para las cadenas VTEX que ya consultamos.
 *
 * Usa el endpoint público de puntos de recojo de VTEX (`/api/checkout/pub/pickup-points`),
 * que devuelve las tiendas ordenadas por distancia con dirección y coordenadas. Ese endpoint
 * NO confirma stock por tienda (la simulación por tienda resultó inconsistente al probarla
 * contra los sitios reales), así que la app lo presenta como "tienda más cercana de la cadena
 * donde está disponible online", no como "hay stock en esta tienda".
 */

export interface Coordenadas {
  lat: number;
  lng: number;
}

export interface TiendaCercana {
  cadena: string;
  nombre: string;
  direccion: string;
  lat: number;
  lng: number;
  distanciaKm: number;
}

interface ChainConfig {
  baseUrl: string;
  /**
   * Los puntos de recojo incluyen agentes de terceros (Pickit, Scharff, Cruz del Sur, otras
   * tiendas del grupo): solo se muestran los que llevan la marca de la cadena en el nombre.
   */
  marca: RegExp;
}

export const CHAINS: Record<string, ChainConfig> = {
  'Plaza Vea': { baseUrl: 'https://www.plazavea.com.pe', marca: /plaza\s*vea/i },
  Promart: { baseUrl: 'https://www.promart.pe', marca: /promart/i },
  Oechsle: { baseUrl: 'https://www.oechsle.pe', marca: /oechsle/i },
  Wong: { baseUrl: 'https://www.wong.pe', marca: /wong/i },
  Metro: { baseUrl: 'https://www.metro.pe', marca: /\bmetro\b/i },
};

interface VtexAddress {
  street?: string;
  number?: string;
  neighborhood?: string;
  geoCoordinates?: [number, number];
}

interface VtexPickupPointsResponse {
  items?: Array<{
    distance?: number;
    pickupPoint?: {
      friendlyName?: string;
      address?: VtexAddress;
    };
  }>;
}

function direccionDe(address: VtexAddress | undefined): string {
  if (!address) {
    return '';
  }
  const calle = [address.street, address.number].filter(Boolean).join(' ');
  return [calle, address.neighborhood].filter(Boolean).join(', ');
}

export function esCadenaConTiendas(cadena: string): boolean {
  return cadena in CHAINS;
}

async function buscarEnCadena(
  cadena: string,
  { lat, lng }: Coordenadas,
  timeoutMs: number
): Promise<TiendaCercana | null> {
  const config = CHAINS[cadena];
  // VTEX recibe las coordenadas como "longitud;latitud".
  const url = `${config.baseUrl}/api/checkout/pub/pickup-points?geoCoordinates=${lng};${lat}&page=1&pageSize=30`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json', 'User-Agent': 'ComparadorPreciosPeru/1.0' },
    });
    if (!response.ok) {
      throw new Error(`${cadena} respondió con estado ${response.status}`);
    }
    const body = (await response.json()) as VtexPickupPointsResponse;
    for (const item of body.items ?? []) {
      const punto = item.pickupPoint;
      const coords = punto?.address?.geoCoordinates;
      if (!punto?.friendlyName || !coords || !config.marca.test(punto.friendlyName)) {
        continue;
      }
      return {
        cadena,
        nombre: punto.friendlyName,
        direccion: direccionDe(punto.address),
        lng: coords[0],
        lat: coords[1],
        distanciaKm: Math.round((item.distance ?? 0) * 10) / 10,
      };
    }
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * La tienda más cercana de cada cadena pedida, ordenadas de la más cercana a la más lejana.
 * Una cadena que falla o no tiene tiendas cerca simplemente no aparece: no tumba a las demás.
 */
export async function buscarTiendasCercanas(
  cadenas: string[],
  ubicacion: Coordenadas,
  timeoutMs = 6000
): Promise<TiendaCercana[]> {
  const validas = [...new Set(cadenas)].filter(esCadenaConTiendas);
  const resultados = await Promise.all(
    validas.map((cadena) =>
      buscarEnCadena(cadena, ubicacion, timeoutMs).catch((err: unknown) => {
        console.error(`Tiendas cercanas de ${cadena} fallaron:`, err);
        return null;
      })
    )
  );
  return resultados
    .filter((tienda): tienda is TiendaCercana => tienda !== null)
    .sort((a, b) => a.distanciaKm - b.distanciaKm);
}
