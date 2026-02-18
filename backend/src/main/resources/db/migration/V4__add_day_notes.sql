create table if not exists day_notes (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  due_date date not null,
  issue text not null default '',
  memo text not null default '',
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique(user_id, due_date)
);

create index if not exists idx_day_notes_user_date on day_notes(user_id, due_date);
