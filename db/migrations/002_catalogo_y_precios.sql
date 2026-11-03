-- 002: catálogo normalizado (marca, tipo, producto, tienda, publicación) e historial de precios.
--
-- Decisión central: un precio se observa sobre una PUBLICACIÓN (producto en una tienda), no sobre
-- el seguimiento de un usuario. Así el historial se comparte entre todas las personas que siguen
-- el mismo producto, y no se repiten consultas a las tiendas ni filas idénticas por usuario.

create table public.brands (
  id     integer generated always as identity primary key,
  nombre extensions.citext not null unique check (length(btrim(nombre)) between 1 and 80)
);

create table public.product_types (
  id     integer generated always as identity primary key,
  nombre extensions.citext not null unique check (length(btrim(nombre)) between 1 and 60)
);

create table public.stores (
  id                    smallint generated always as identity primary key,
  slug                  text not null unique check (slug ~ '^[a-z0-9_]+$'),
  nombre                text not null,
  tiene_tiendas_fisicas boolean not null default false,
  created_at            timestamptz not null default now()
);

create table public.products (
  id              uuid primary key default gen_random_uuid(),
  brand_id        integer references public.brands (id),
  category_id     smallint not null references public.categories (id),
  product_type_id integer references public.product_types (id),
  nombre          extensions.citext not null check (length(btrim(nombre)) between 1 and 200),
  presentacion    extensions.citext not null default '' check (length(presentacion) <= 60),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  -- Un mismo producto no se registra dos veces (marca desconocida cuenta como un solo valor).
  unique nulls not distinct (brand_id, nombre, presentacion)
);

create trigger trg_products_updated_at
  before update on public.products
  for each row execute function public.set_updated_at();

create index idx_products_brand_id        on public.products (brand_id);
create index idx_products_category_id     on public.products (category_id);
create index idx_products_product_type_id on public.products (product_type_id);

-- Cómo una tienda publica un producto. (producto, tienda) → título y enlace vigentes.
create table public.store_listings (
  id         bigint generated always as identity primary key,
  product_id uuid     not null references public.products (id) on delete cascade,
  store_id   smallint not null references public.stores (id),
  titulo     text     not null check (length(btrim(titulo)) > 0),
  url        text     check (url is null or url ~ '^https?://'),
  updated_at timestamptz not null default now(),
  unique (product_id, store_id)
);

create trigger trg_store_listings_updated_at
  before update on public.store_listings
  for each row execute function public.set_updated_at();

create index idx_store_listings_store_id on public.store_listings (store_id);

-- Historial de precios (solo en soles). Solo se inserta: un precio observado no se modifica.
create table public.price_observations (
  id          bigint generated always as identity primary key,
  listing_id  bigint        not null references public.store_listings (id) on delete cascade,
  source_code text          not null references public.price_sources (code),
  precio      numeric(10,2) not null check (precio > 0),
  observed_at timestamptz   not null default now()
);

create index idx_price_observations_listing_time
  on public.price_observations (listing_id, observed_at desc);
create index idx_price_observations_source_code
  on public.price_observations (source_code);

insert into public.stores (slug, nombre, tiene_tiendas_fisicas) values
  ('plaza_vea', 'Plaza Vea', true),
  ('wong',      'Wong',      true),
  ('metro',     'Metro',     true),
  ('oechsle',   'Oechsle',   true),
  ('promart',   'Promart',   true);

alter table public.brands              enable row level security;
alter table public.product_types       enable row level security;
alter table public.stores              enable row level security;
alter table public.products            enable row level security;
alter table public.store_listings      enable row level security;
alter table public.price_observations  enable row level security;

revoke all on table
  public.brands, public.product_types, public.stores, public.products,
  public.store_listings, public.price_observations
from anon, authenticated;
