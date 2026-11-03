-- 003: usuarios, dispositivos (push), seguimientos, alertas y entregas de notificación.
--
-- Normalización: la alerta NO guarda el mensaje (se deriva de su tipo + la observación + el
-- producto) ni el usuario (se deriva del seguimiento). El estado del seguimiento es una FK a un
-- catálogo, y sus transiciones válidas viven en watch_status_transitions.

create table public.app_users (
  id           uuid primary key default gen_random_uuid(),
  clerk_id     text not null unique check (length(clerk_id) > 0),
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table public.devices (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references public.app_users (id) on delete cascade,
  push_token   text not null unique check (length(push_token) > 0),
  plataforma   text not null default 'android' check (plataforma in ('android')),
  created_at   timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  revoked_at   timestamptz
);

create index idx_devices_user_id on public.devices (user_id);

create table public.watches (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references public.app_users (id) on delete cascade,
  product_id      uuid not null references public.products (id),
  target_price    numeric(10,2) check (target_price is null or target_price > 0),
  status_code     text not null default 'activa' references public.watch_statuses (code),
  last_checked_at timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  unique (user_id, product_id)
);

create trigger trg_watches_updated_at
  before update on public.watches
  for each row execute function public.set_updated_at();

create index idx_watches_product_id  on public.watches (product_id);
create index idx_watches_status_code on public.watches (status_code);

-- Última defensa: aunque la API ya valida, la base rechaza una transición de estado no permitida.
create or replace function public.check_watch_status_transition()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.status_code is distinct from old.status_code and not exists (
    select 1 from public.watch_status_transitions t
    where t.from_code = old.status_code and t.to_code = new.status_code
  ) then
    raise exception 'Transición de estado no permitida: % -> %', old.status_code, new.status_code
      using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger trg_watches_status_transition
  before update of status_code on public.watches
  for each row execute function public.check_watch_status_transition();

create table public.alerts (
  id              bigint generated always as identity primary key,
  watch_id        uuid   not null references public.watches (id) on delete cascade,
  observation_id  bigint not null references public.price_observations (id) on delete cascade,
  alert_type_code text   not null references public.alert_types (code),
  created_at      timestamptz not null default now(),
  read_at         timestamptz,
  -- La misma alerta no se genera dos veces para la misma observación.
  unique (observation_id, watch_id, alert_type_code)
);

create index idx_alerts_watch_id        on public.alerts (watch_id, created_at desc);
create index idx_alerts_alert_type_code on public.alerts (alert_type_code);

-- La observación de una alerta debe ser de un precio del MISMO producto que sigue el seguimiento.
-- Ninguna FK simple puede expresar esta regla entre tres tablas, por eso va en un trigger.
create or replace function public.check_alert_same_product()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not exists (
    select 1
    from public.watches w
    join public.store_listings l on l.product_id = w.product_id
    join public.price_observations o on o.listing_id = l.id
    where w.id = new.watch_id and o.id = new.observation_id
  ) then
    raise exception 'La observación % no corresponde al producto del seguimiento %',
      new.observation_id, new.watch_id
      using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger trg_alerts_same_product
  before insert on public.alerts
  for each row execute function public.check_alert_same_product();

-- Intentos de enviar una alerta a un dispositivo (una fila por alerta y dispositivo).
create table public.push_deliveries (
  alert_id   bigint not null references public.alerts (id) on delete cascade,
  device_id  uuid   not null references public.devices (id) on delete cascade,
  estado     text   not null default 'pendiente' check (estado in ('pendiente', 'enviada', 'fallida')),
  intentos   smallint not null default 0 check (intentos >= 0),
  error      text,
  enviada_at timestamptz,
  primary key (alert_id, device_id)
);

create index idx_push_deliveries_device_id on public.push_deliveries (device_id);

alter table public.app_users        enable row level security;
alter table public.devices          enable row level security;
alter table public.watches          enable row level security;
alter table public.alerts           enable row level security;
alter table public.push_deliveries  enable row level security;

revoke all on table
  public.app_users, public.devices, public.watches, public.alerts, public.push_deliveries
from anon, authenticated;
