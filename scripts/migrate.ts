// Aplica db/migrations/*.sql en orden sobre el Postgres de DATABASE_URL (pensado para Docker local).
//   docker compose up -d db && DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm run db:migrate
//
// En Supabase las migraciones ya se aplicaron desde su panel/MCP: el script se niega a correr ahí
// para no duplicarlas (usa --force solo si sabes lo que haces).
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { createSql } from '../lib/db/client.js';

const url = process.env.DATABASE_URL;
if (!url) {
  console.error('Falta DATABASE_URL.');
  process.exit(1);
}
if (/supabase\.(com|co)/.test(url) && !process.argv.includes('--force')) {
  console.error('DATABASE_URL apunta a Supabase: las migraciones ya están aplicadas ahí. Usa --force para saltarte esto.');
  process.exit(1);
}

const dir = join(process.cwd(), 'db', 'migrations');
const files = readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();
const sql = createSql(url);

try {
  await sql`create table if not exists public.app_migrations (
    name text primary key, applied_at timestamptz not null default now())`;
  const applied = new Set((await sql<{ name: string }[]>`select name from public.app_migrations`).map((r) => r.name));
  for (const file of files) {
    if (applied.has(file)) {
      console.log(`= ${file} (ya aplicada)`);
      continue;
    }
    await sql.begin(async (tx) => {
      await tx.unsafe(readFileSync(join(dir, file), 'utf8'));
      await tx`insert into public.app_migrations (name) values (${file})`;
    });
    console.log(`+ ${file}`);
  }
} finally {
  await sql.end();
}
