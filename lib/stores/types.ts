export interface StoreProduct {
  producto: string;
  precio: number;
  url: string;
}

export interface StoreResult {
  tienda: string;
  estado: 'encontrado' | 'no_encontrado' | 'error';
  producto?: string;
  precio?: number;
  url?: string;
  mensaje?: string;
  /**
   * "web" = precio visto en Google Shopping, no consultado directo a la tienda.
   * "busqueda_web" = último recurso: ni tiendas directas ni Shopping tenían el producto,
   * este es un link de una búsqueda normal, SIN precio confirmado (ver stores/googleSearch.ts).
   * Ausente = tienda directa.
   */
  fuente?: 'web' | 'busqueda_web';
}
