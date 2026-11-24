-- 006: eventos v2. Amplía la bandeja de eventos (outbox) y agrega una entrega por consumidor.
--
-- Hasta la 005 `domain_events` solo se escribía: nadie la leía y `processed_at` quedaba siempre nulo.
-- Ahora cada evento tiene un envelope completo (versión, correlación, causa, actor) y una fila en
-- `event_deliveries` por cada consumidor suscrito: cada consumidor tiene su propio estado, intentos y
-- cola de fallos, así que uno que falla no frena ni repite a los demás.
--
-- Todo es aditivo: no se borra ni renombra nada, y el código anterior (que inserta solo las cuatro
-- columnas originales) sigue funcionando sin cambios.

alter table public.domain_events
  add column version        smallint not null default 1 check (version > 0),
  add column correlation_id uuid,
  add column causation_id   bigint references public.domain_events (id) on delete set null,
  add column actor          text   not null default 'system' check (length(actor) > 0),
  add column dedupe_key     text   check (dedupe_key is null or length(dedupe_key) > 0);

-- Impide publicar dos veces el mismo hecho (por ejemplo, dos corridas del scheduler en la misma hora).
create unique index uq_domain_events_dedupe
  on public.domain_events (dedupe_key) where dedupe_key is not null;

create index idx_domain_events_aggregate
  on public.domain_events (aggregate_type, aggregate_id, id);
create index idx_domain_events_causation
  on public.domain_events (causation_id) where causation_id is not null;

create table public.event_deliveries (
  event_id        bigint not null references public.domain_events (id) on delete cascade,
  consumer        text   not null check (consumer ~ '^[a-z][a-z0-9_]*$'),
  status          text   not null default 'pending'
                  check (status in ('pending', 'processing', 'done', 'failed', 'dead')),
  attempts        smallint not null default 0 check (attempts >= 0),
  -- Cuándo se puede (re)intentar. El backoff exponencial lo mueve hacia adelante tras cada fallo.
  next_attempt_at timestamptz not null default now(),
  -- Arrendamiento: si la función que reclamó la entrega muere, otra la recupera al vencer.
  locked_until    timestamptz,
  last_error      text,
  processed_at    timestamptz,
  primary key (event_id, consumer)
);

-- Entregas que ya se pueden reclamar (pendientes o fallidas con la espera cumplida).
create index idx_event_deliveries_due
  on public.event_deliveries (next_attempt_at) where status in ('pending', 'failed');
-- Entregas en curso, para recuperar las que quedaron colgadas.
create index idx_event_deliveries_leases
  on public.event_deliveries (locked_until) where status = 'processing';
-- Cola de fallos (DLQ) y estadísticas por consumidor.
create index idx_event_deliveries_consumer_status
  on public.event_deliveries (consumer, status);

alter table public.event_deliveries enable row level security;
revoke all on table public.event_deliveries from anon, authenticated;

-- Los eventos que ya existían se escribieron sin ningún consumidor: no se reprocesan.
update public.domain_events set processed_at = now() where processed_at is null;
