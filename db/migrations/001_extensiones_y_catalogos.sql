-- 001: extensiones, trigger de auditoría y tablas de referencia (catálogos cerrados).
-- Todas las tablas nacen con RLS activado y SIN acceso para anon/authenticated: el celular nunca
-- habla con Supabase directo, solo con nuestra API (que se conecta con un rol propio).

create extension if not exists citext with schema extensions;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ── Categorías del producto (mismos ids que CATEGORIAS en lib/productIdentification.ts) ──
create table public.categories (
  id     smallint generated always as identity primary key,
  slug   text not null unique check (slug ~ '^[a-z_]+$'),
  nombre text not null
);

-- ── Estados de un seguimiento y transiciones permitidas (reglas como datos, no como if) ──
create table public.watch_statuses (
  code   text primary key check (code ~ '^[a-z_]+$'),
  nombre text not null
);

create table public.watch_status_transitions (
  from_code text not null references public.watch_statuses (code),
  to_code   text not null references public.watch_statuses (code),
  actor     text not null check (actor in ('usuario', 'sistema')),
  primary key (from_code, to_code),
  check (from_code <> to_code)
);

-- ── Tipos de alerta y fuentes de un precio ──
create table public.alert_types (
  code        text primary key check (code ~ '^[a-z_]+$'),
  nombre      text not null,
  descripcion text not null
);

create table public.price_sources (
  code   text primary key check (code ~ '^[a-z_]+$'),
  nombre text not null
);

insert into public.categories (slug, nombre) values
  ('abarrotes', 'Abarrotes'), ('bebidas', 'Bebidas'), ('limpieza', 'Limpieza'),
  ('cuidado_personal', 'Cuidado personal'), ('tecnologia', 'Tecnología'),
  ('electrohogar', 'Electrohogar'), ('hogar', 'Hogar'), ('ferreteria', 'Ferretería'),
  ('moda', 'Moda'), ('juguetes', 'Juguetes'), ('otros', 'Otros');

insert into public.watch_statuses (code, nombre) values
  ('activa', 'Activa'), ('pausada', 'Pausada'), ('alcanzada', 'Objetivo alcanzado');

insert into public.watch_status_transitions (from_code, to_code, actor) values
  ('activa',    'pausada',   'usuario'),
  ('pausada',   'activa',    'usuario'),
  ('activa',    'alcanzada', 'sistema'),
  ('alcanzada', 'activa',    'usuario'),
  ('alcanzada', 'pausada',   'usuario');

insert into public.alert_types (code, nombre, descripcion) values
  ('objetivo_alcanzado', 'Objetivo alcanzado', 'El mejor precio llegó a tu precio objetivo.'),
  ('minimo_historico',   'Mínimo histórico',   'Es el precio más bajo que hemos visto de este producto.');

insert into public.price_sources (code, nombre) values
  ('directa',  'Consulta directa a la tienda'),
  ('shopping', 'Google Shopping');

alter table public.categories                enable row level security;
alter table public.watch_statuses            enable row level security;
alter table public.watch_status_transitions  enable row level security;
alter table public.alert_types               enable row level security;
alter table public.price_sources             enable row level security;

revoke all on table
  public.categories, public.watch_statuses, public.watch_status_transitions,
  public.alert_types, public.price_sources
from anon, authenticated;
