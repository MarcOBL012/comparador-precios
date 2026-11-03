-- 004: catálogo comunitario de confirmaciones (módulo B) y bandeja de eventos de dominio (outbox).
--
-- Una "firma de identificación" es lo que la IA leyó de una foto (marca, nombre, presentación).
-- Cada persona vota a qué producto del catálogo corresponde. Cuando suficientes personas coinciden
-- la firma queda VERIFICADA y se usa antes de buscar en tiendas (ver v_signature_consensus).

create table public.identification_signatures (
  id                 bigint generated always as identity primary key,
  marca_leida        extensions.citext not null default '',
  nombre_leido       extensions.citext not null check (length(btrim(nombre_leido)) > 0),
  presentacion_leida extensions.citext not null default '',
  created_at         timestamptz not null default now(),
  unique (marca_leida, nombre_leido, presentacion_leida)
);

create table public.signature_confirmations (
  signature_id  bigint not null references public.identification_signatures (id) on delete cascade,
  user_id       uuid   not null references public.app_users (id) on delete cascade,
  product_id    uuid   not null references public.products (id),
  veredicto     text   not null check (veredicto in ('confirma', 'corrige')),
  url_evidencia text   check (url_evidencia is null or url_evidencia ~ '^https?://'),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  -- Un voto por persona y firma: cambiar de opinión actualiza la fila, no suma votos.
  primary key (signature_id, user_id)
);

create trigger trg_signature_confirmations_updated_at
  before update on public.signature_confirmations
  for each row execute function public.set_updated_at();

create index idx_signature_confirmations_user_id    on public.signature_confirmations (user_id);
create index idx_signature_confirmations_product_id on public.signature_confirmations (product_id);

-- Consenso: por firma y producto, cuántos votos tiene y qué proporción del total representa.
-- Verificado = al menos 2 personas distintas y al menos 66% de los votos de esa firma.
create view public.v_signature_consensus
with (security_invoker = true) as
with votos as (
  select signature_id, product_id, count(*)::int as votos
  from public.signature_confirmations
  group by signature_id, product_id
),
totales as (
  select signature_id, sum(votos)::int as total
  from votos
  group by signature_id
)
select
  v.signature_id,
  v.product_id,
  v.votos,
  t.total,
  round(v.votos::numeric / t.total, 2) as proporcion,
  (v.votos >= 2 and v.votos::numeric / t.total >= 0.66) as verificado
from votos v
join totales t using (signature_id);

-- ── Bandeja de eventos de dominio (patrón outbox) ──
-- Se escribe en la MISMA transacción que el cambio que lo origina; un proceso aparte (cron o
-- webhook) lo consume. Así nunca hay un cambio guardado sin su evento, ni un evento sin cambio.
create table public.domain_events (
  id             bigint generated always as identity primary key,
  aggregate_type text  not null check (length(aggregate_type) > 0),
  aggregate_id   text  not null,
  event_type     text  not null check (event_type ~ '^[a-z_]+\.[a-z_]+$'),
  payload        jsonb not null default '{}'::jsonb,
  occurred_at    timestamptz not null default now(),
  processed_at   timestamptz,
  attempts       smallint not null default 0 check (attempts >= 0),
  last_error     text
);

create index idx_domain_events_pending
  on public.domain_events (occurred_at) where processed_at is null;

alter table public.identification_signatures  enable row level security;
alter table public.signature_confirmations    enable row level security;
alter table public.domain_events              enable row level security;

revoke all on table
  public.identification_signatures, public.signature_confirmations,
  public.domain_events, public.v_signature_consensus
from anon, authenticated;

-- Las secuencias de las columnas identity tampoco quedan accesibles para anon/authenticated.
revoke all on all sequences in schema public from anon, authenticated;
