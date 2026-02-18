create extension if not exists pgcrypto;

create table users (
  id uuid primary key,
  email varchar(255) not null unique,
  password_hash varchar(255) not null,
  role varchar(50) not null,
  failed_login_count int not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table workspace_items (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  parent_id uuid references workspace_items(id) on delete cascade,
  title varchar(255) not null,
  status varchar(20) not null,
  due_date date,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index idx_workspace_items_user_parent on workspace_items(user_id, parent_id);
create index idx_workspace_items_title on workspace_items using gin (to_tsvector('simple', title));

create table blocks (
  id uuid primary key,
  item_id uuid not null references workspace_items(id) on delete cascade,
  sort_order int not null,
  type varchar(50) not null,
  content jsonb not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
create index idx_blocks_item_sort on blocks(item_id, sort_order);

create table tags (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  name varchar(100) not null,
  created_at timestamptz not null,
  unique(user_id, name)
);

create table item_tags (
  item_id uuid not null references workspace_items(id) on delete cascade,
  tag_id uuid not null references tags(id) on delete cascade,
  created_at timestamptz not null,
  primary key(item_id, tag_id)
);

create table file_assets (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  item_id uuid not null references workspace_items(id) on delete cascade,
  original_name varchar(255) not null,
  stored_name varchar(255) not null unique,
  mime_type varchar(120) not null,
  size_bytes bigint not null,
  created_at timestamptz not null
);
