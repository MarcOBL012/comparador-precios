import type { ProductIdentification } from './productIdentification.js';

export interface MatchCandidate {
  productName: string;
  brand: string;
}

export const MATCH_THRESHOLD = 0.3;
const PACK_PENALTY = 0.3;
const PACK_INDICATOR_PATTERN = /^(pack|paquete|combo|sixpack|doypack|x\d+|\d+un|\d+u|\d+pack)$/;

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

function scoreCandidate(identification: ProductIdentification, candidate: MatchCandidate): number {
  const identificationTokens = tokensOf(
    `${identification.marca} ${identification.nombre} ${identification.presentacion}`
  );
  const candidateTokens = tokensOf(`${candidate.brand} ${candidate.productName}`);

  const brandTokens = tokensOf(identification.marca);
  const brandMatches = [...brandTokens].some((token) => candidateTokens.has(token));
  if (brandTokens.size > 0 && !brandMatches) {
    return 0;
  }

  let score = jaccard(identificationTokens, candidateTokens);

  if (hasPackIndicator(candidateTokens) && !hasPackIndicator(identificationTokens)) {
    score -= PACK_PENALTY;
  }

  return Math.max(score, 0);
}

export function pickBestMatch<T extends MatchCandidate>(
  identification: ProductIdentification,
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
