import postgres from 'postgres';
import { ConfigError } from '../errors.js';

/** Conexión o transacción: los repositorios aceptan cualquiera de las dos. */
export type Sql = postgres.Sql;

let cached: Sql | undefined;

/**
 * Un cliente por instancia de la función serverless. Va contra el pooler de Supabase en modo
 * transacción (puerto 6543): no admite sentencias preparadas, y cada función usa 1 conexión.
 */
export function getSql(): Sql {
  if (!cached) {
    const url = process.env.DATABASE_URL;
    if (!url) {
      throw new ConfigError('DATABASE_URL no está configurada en el servidor.');
    }
    cached = postgres(url, {
      max: 1,
      idle_timeout: 20,
      connect_timeout: 10,
      prepare: false,
      ssl: /supabase\.(com|co)/.test(url) ? 'require' : false,
    });
  }
  return cached;
}

export function createSql(url: string): Sql {
  return postgres(url, { max: 1, prepare: false, ssl: false, onnotice: () => {} });
}

/** Códigos de error de Postgres que la API traduce a respuestas HTTP. */
export const PG = {
  UNIQUE_VIOLATION: '23505',
  FOREIGN_KEY_VIOLATION: '23503',
  CHECK_VIOLATION: '23514',
} as const;

export function pgCode(err: unknown): string | undefined {
  return typeof err === 'object' && err !== null ? (err as { code?: string }).code : undefined;
}
