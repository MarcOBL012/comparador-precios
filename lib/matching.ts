import type { ProductQuery } from './productIdentification.js';

export interface MatchCandidate {
  productName: string;
  brand: string;
  /** Rutas de categoría de la tienda, de la más específica a la más general (ej. "/Tecnología/Computo/Mouses/"). */
  categories?: string[];
}

export const MATCH_THRESHOLD = 0.3;
const PACK_PENALTY = 0.3;
const CATEGORY_BONUS = 0.15;
const PACK_INDICATOR_PATTERN = /^(pack|paquete|combo|kit|sixpack|doypack|x\d+|\d+un|\d+u|\d+pack)$/;

const ACCENTS: Record<string, string> = {
  á: 'a',
  é: 'e',
  í: 'i',
  ó: 'o',
  ú: 'u',
  ü: 'u',
  ñ: 'n',
};

function normalize(text: string): string {
  return text
    .toLowerCase()
    .replace(/[áéíóúüñ]/g, (char) => ACCENTS[char] ?? char)
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean)
    .join(' ');
}

function tokensOf(text: string): Set<string> {
  return new Set(normalize(text).split(' ').filter(Boolean));
}

function jaccard(tokensA: Set<string>, tokensB: Set<string>): number {
  if (tokensA.size === 0 || tokensB.size === 0) {
    return 0;
  }
  let intersection = 0;
  for (const token of tokensA) {
    if (tokensB.has(token)) {
      intersection++;
    }
  }
  const union = new Set([...tokensA, ...tokensB]).size;
  return intersection / union;
}

function hasPackIndicator(tokens: Set<string>): boolean {
  for (const token of tokens) {
    if (PACK_INDICATOR_PATTERN.test(token)) {
      return true;
    }
  }
  return false;
}

const STOPWORDS = new Set(['del', 'los', 'las', 'con', 'para', 'por', 'sin', 'y', 'en', 'de', 'la', 'el']);

// Las tiendas mezclan singular y plural: "mouses"→"mouse", "leches"→"leche", "auriculares"→"auricular".
function stem(token: string): string {
  if (token.length > 4 && /[rlndzj]es$/.test(token)) {
    return token.slice(0, -2);
  }
  return token.length > 3 && token.endsWith('s') ? token.slice(0, -1) : token;
}

function wordStems(text: string): Set<string> {
  return new Set(
    [...tokensOf(text)]
      .filter((token) => token.length > 1 && !STOPWORDS.has(token) && !/^\d/.test(token))
      .map(stem)
  );
}

// Cantidades ("400g", "1l", "128gb", "6un", "x6") llevan letras y dígitos pero no identifican un modelo.
const MEASURE_PATTERN = /^(x\d+|\d+(g|gr|kg|mg|ml|l|lt|cl|oz|cm|mm|m|w|v|gb|tb|mb|mah|hz|un|und|u|pack))$/;

function modelCodes(tokens: Set<string>): Set<string> {
  return new Set(
    [...tokens].filter((t) => t.length >= 3 && /[a-z]/.test(t) && /\d/.test(t) && !MEASURE_PATTERN.test(t))
  );
}

// "M190" contra un título que dice "MK235" es otro producto aunque compartan marca y tipo.
// Un título sin ningún código ("Licuadora Oster") no se descarta: simplemente no lo menciona.
function isOtherModel(identificationTokens: Set<string>, candidateTokens: Set<string>): boolean {
  const wanted = modelCodes(identificationTokens);
  if (wanted.size === 0 || [...wanted].some((code) => candidateTokens.has(code))) {
    return false;
  }
  return modelCodes(candidateTokens).size > 0;
}

const SYNONYM_GROUPS = [
  ['audifono', 'auricular', 'headphone', 'earbud'],
  ['celular', 'smartphone', 'telefono'],
  ['mouse', 'raton'],
  ['laptop', 'notebook', 'portatil'],
  ['televisor', 'tv', 'television'],
  ['parlante', 'altavoz', 'speaker'],
  // Muchas bebidas venden su empaque en inglés ("Free Tea") aunque Gemini describa el tipo en
  // español ("té helado"): sin este grupo, "te" (de Gemini) nunca calza con "tea" (del empaque).
  ['te', 'tea'],
  ['jugo', 'juice'],
  ['agua', 'water'],
].map((group) => new Set(group));

function isOfType(tipo: string, candidateTokens: Set<string>): boolean {
  const candidateStems = new Set([...candidateTokens].map(stem));
  return [...wordStems(tipo)].every((token) => {
    const equivalents = SYNONYM_GROUPS.find((group) => group.has(token)) ?? new Set([token]);
    return [...equivalents].some((word) => candidateStems.has(word));
  });
}

// Un kit "Teclado + Mouse" vive en /Teclados/ aunque su título diga "mouse": que la
// categoría más específica de la tienda nombre lo que se busca desempata a favor del producto correcto.
function categoryAgrees(identification: ProductQuery, candidate: MatchCandidate): boolean {
  const mostSpecific = candidate.categories?.[0];
  if (!mostSpecific) {
    return false;
  }
  const inCategory = wordStems(mostSpecific);
  const wanted = wordStems(identification.tipo || identification.nombre);
  return [...wanted].some((token) => inCategory.has(token));
}

function scoreCandidate(identification: ProductQuery, candidate: MatchCandidate): number {
  const identificationTokens = tokensOf(
    `${identification.marca} ${identification.nombre} ${identification.presentacion}`
  );
  const candidateTokens = tokensOf(`${candidate.brand} ${candidate.productName}`);

  const brandTokens = tokensOf(identification.marca);
  const brandMatches = [...brandTokens].some((token) => candidateTokens.has(token));
  if (brandTokens.size > 0 && !brandMatches) {
    return 0;
  }

  if (isOtherModel(identificationTokens, candidateTokens)) {
    return 0;
  }
  // Mostrar la leche UHT cuando se buscaba leche evaporada, o la funda "Case Galaxy A15" cuando se
  // buscaba el celular, engaña más que decir "no encontrado": el tipo se exige aunque el modelo coincida.
  if (identification.tipo && !isOfType(identification.tipo, candidateTokens)) {
    return 0;
  }

  let score = jaccard(identificationTokens, candidateTokens);

  // El pack x6 comparte categoría con la lata suelta: el bono no debe compensar la penalización.
  if (hasPackIndicator(candidateTokens) && !hasPackIndicator(identificationTokens)) {
    score -= PACK_PENALTY;
  } else if (categoryAgrees(identification, candidate)) {
    score += CATEGORY_BONUS;
  }

  return Math.max(score, 0);
}

export function pickBestMatch<T extends MatchCandidate>(
  identification: ProductQuery,
  candidates: T[]
): T | null {
  let best: T | null = null;
  let bestScore = 0;
  for (const candidate of candidates) {
    const score = scoreCandidate(identification, candidate);
    if (score > bestScore) {
      bestScore = score;
      best = candidate;
    }
  }
  return bestScore >= MATCH_THRESHOLD ? best : null;
}
