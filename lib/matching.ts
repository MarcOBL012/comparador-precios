import type { ProductIdentification } from './productIdentification';

export interface MatchCandidate {
  productName: string;
  brand: string;
}

export const MATCH_THRESHOLD = 0.3;

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

function tokenSetSimilarity(a: string, b: string): number {
  const tokensA = new Set(normalize(a).split(' ').filter(Boolean));
  const tokensB = new Set(normalize(b).split(' ').filter(Boolean));
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

function scoreCandidate(identification: ProductIdentification, candidate: MatchCandidate): number {
  const identificationText = `${identification.marca} ${identification.nombre} ${identification.presentacion}`;
  const candidateText = `${candidate.brand} ${candidate.productName}`;
  return tokenSetSimilarity(identificationText, candidateText);
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
