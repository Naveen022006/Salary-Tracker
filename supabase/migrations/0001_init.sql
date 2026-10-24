-- =====================================================================
-- Salary Tracker - Supabase backend, migration 0001 (Postgres 15+)
-- Run once in: Supabase Dashboard -> SQL Editor. Safe to run again.
-- No secrets in this file. Money is bigint paise.
-- Original SMS / notification text is NEVER stored (only a SHA-256 hash).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Private schema (NOT exposed through the API)
-- ---------------------------------------------------------------------
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
-- authenticated needs USAGE only so that public.delete_my_data() (SECURITY
-- INVOKER) can call private.hit(). The schema is not in the exposed-schemas
-- list of the Data API, so nobody can call it through REST/RPC.
grant usage on schema private to authenticated;

-- ---------------------------------------------------------------------
-- 2. Tables
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
  user_id              uuid primary key default auth.uid()
                         references auth.users (id) on delete cascade,
  salary_paise         bigint   not null default 0
                         check (salary_paise between 0 and 100000000000),
  pay_day              smallint not null default 1
                         check (pay_day between 1 and 31),
  savings_goal_paise   bigint   not null default 0
                         check (savings_goal_paise >= 0 and savings_goal_paise <= 100000000000),
  reminder_enabled     boolean  not null default true,
  reminder_hour        smallint not null default 21 check (reminder_hour between 0 and 23),
  reminder_minute      smallint not null default 30 check (reminder_minute between 0 and 59),
  tracking_start       date,
  sms_enabled          boolean  not null default true,
  notif_enabled        boolean  not null default true,
  smart_detect         boolean  not null default true,
  alert_on_capture     boolean  not null default true,
  allowed_packages     text[]   not null default '{}'
                         check (cardinality(allowed_packages) <= 300),
  theme_mode           text     not null default 'SYSTEM'
                         check (theme_mode in ('SYSTEM','LIGHT','DARK')),
  onboarding_done      boolean  not null default false,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create table if not exists public.transactions (
  id            uuid primary key,                       -- generated on the phone
  user_id       uuid not null default auth.uid()
                  references auth.users (id) on delete cascade,
  amount_paise  bigint not null
                  check (amount_paise > 0 and amount_paise <= 100000000000),
  type          text   not null check (type in ('DEBIT','CREDIT')),
  occurred_at   timestamptz not null,
  merchant      text   check (char_length(merchant) <= 120),
  category      text   not null check (char_length(category) <= 32),
  source        text   not null check (source in ('SMS','NOTIFICATION','MANUAL')),
  sender        text   check (char_length(sender) <= 200),
  account_last4 text   check (char_length(account_last4) <= 4),
  upi_ref       text   check (char_length(upi_ref) <= 40),
  note          text   check (char_length(note) <= 500),
  excluded      boolean not null default false,
  body_hash     text   check (char_length(body_hash) <= 64),  -- SHA-256 hex, not the text
  deleted       boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- Same message (same hash) can exist only once per user, unless soft-deleted.
create unique index if not exists transactions_user_body_hash_uq
  on public.transactions (user_id, body_hash)
  where body_hash is not null and not deleted;

-- Used by the phone's pull: where user_id = me and updated_at > cursor.
create index if not exists transactions_user_updated_idx
  on public.transactions (user_id, updated_at);

create table if not exists public.category_rules (
  user_id    uuid not null default auth.uid()
               references auth.users (id) on delete cascade,
  keyword    text not null check (char_length(keyword) between 1 and 120),
  category   text not null check (char_length(category) <= 32),
  deleted    boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (user_id, keyword)
);

create index if not exists category_rules_user_updated_idx
  on public.category_rules (user_id, updated_at);

-- ---------------------------------------------------------------------
-- 3. Row Level Security: enabled AND forced, authenticated only
-- ---------------------------------------------------------------------
alter table public.profiles       enable row level security;
alter table public.profiles       force  row level security;
alter table public.transactions   enable row level security;
alter table public.transactions   force  row level security;
alter table public.category_rules enable row level security;
alter table public.category_rules force  row level security;

revoke all on public.profiles, public.transactions, public.category_rules
  from public, anon;
grant select, insert, update, delete
  on public.profiles, public.transactions, public.category_rules
  to authenticated;

-- profiles
drop policy if exists profiles_select on public.profiles;
drop policy if exists profiles_insert on public.profiles;
drop policy if exists profiles_update on public.profiles;
drop policy if exists profiles_delete on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
  using (user_id = (select auth.uid()));
create policy profiles_insert on public.profiles for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy profiles_update on public.profiles for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy profiles_delete on public.profiles for delete to authenticated
  using (user_id = (select auth.uid()));

-- transactions
drop policy if exists transactions_select on public.transactions;
drop policy if exists transactions_insert on public.transactions;
drop policy if exists transactions_update on public.transactions;
drop policy if exists transactions_delete on public.transactions;
create policy transactions_select on public.transactions for select to authenticated
  using (user_id = (select auth.uid()));
create policy transactions_insert on public.transactions for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy transactions_update on public.transactions for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy transactions_delete on public.transactions for delete to authenticated
  using (user_id = (select auth.uid()));

-- category_rules
drop policy if exists category_rules_select on public.category_rules;
drop policy if exists category_rules_insert on public.category_rules;
drop policy if exists category_rules_update on public.category_rules;
drop policy if exists category_rules_delete on public.category_rules;
create policy category_rules_select on public.category_rules for select to authenticated
  using (user_id = (select auth.uid()));
create policy category_rules_insert on public.category_rules for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy category_rules_update on public.category_rules for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy category_rules_delete on public.category_rules for delete to authenticated
  using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- 4. Rate limit storage and helper
-- ---------------------------------------------------------------------
create table if not exists private.rate_limits (
  user_id      uuid        not null,
  bucket       text        not null,
  window_start timestamptz not null,
  hits         int         not null default 0,
  primary key (user_id, bucket, window_start)
);
-- RLS on but NOT forced: no policies exist, so API roles get nothing, while the
-- SECURITY DEFINER helpers (owned by postgres) can still read/write as table owner.
alter table private.rate_limits enable row level security;
revoke all on private.rate_limits from public, anon, authenticated;

-- Per-user row counter for the "max rows" cap (see section 6 for why).
create table if not exists private.user_counters (
  user_id  uuid primary key,
  txn_rows bigint not null default 0
);
alter table private.user_counters enable row level security;  -- not forced, see rate_limits
revoke all on private.user_counters from public, anon, authenticated;

-- Fixed-window counter. Raises HTTP 429 (via PostgREST "PT429") when exceeded.
-- Note: the exception rolls the whole statement back, including the increment,
-- so the counter stays at max_hits and later calls in the window keep failing.
create or replace function private.hit(bucket text, max_hits int, window_seconds int)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid   uuid := auth.uid();
  w     timestamptz;
  cnt   int;
begin
  if uid is null then
    return;  -- no end-user (SQL editor / service role): nothing to limit
  end if;

  w := to_timestamp(floor(extract(epoch from now()) / window_seconds) * window_seconds);

  insert into private.rate_limits as r (user_id, bucket, window_start, hits)
  values (uid, bucket, w, 1)
  on conflict (user_id, bucket, window_start)
  do update set hits = r.hits + 1
  returning r.hits into cnt;

  if cnt > max_hits then
    raise exception 'Too many requests, try again later' using errcode = 'PT429';
  end if;
end;
$$;

revoke all on function private.hit(text, int, int) from public, anon;
grant execute on function private.hit(text, int, int) to authenticated;  -- for delete_my_data()

-- Cleanup of old windows.
create or replace function private.prune_rate_limits()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from private.rate_limits where window_start < now() - interval '2 days';
$$;
revoke all on function private.prune_rate_limits() from public, anon, authenticated;

-- OPTIONAL: schedule with pg_cron (Database -> Extensions -> enable pg_cron), then run:
--   select cron.schedule('prune-rate-limits', '17 3 * * *',
--                        $$select private.prune_rate_limits()$$);
-- Without pg_cron the table only grows slowly (a few rows per user per day);
-- you can also run `select private.prune_rate_limits();` by hand now and then.

-- ---------------------------------------------------------------------
-- 5. Trigger functions (all in schema private, not callable via API)
-- ---------------------------------------------------------------------
-- Never trust the client: user_id comes from the JWT, updated_at from the server.
create or replace function private.tg_stamp()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    new.user_id := coalesce(auth.uid(), new.user_id);
  else
    new.user_id := old.user_id;   -- owner can never change
  end if;
  new.updated_at := now();
  return new;
end;
$$;

create or replace function private.tg_limit_transactions()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.hit('txn_write_min', 300, 60);
  perform private.hit('txn_write_day', 5000, 86400);
  return new;
end;
$$;

create or replace function private.tg_limit_profiles()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.hit('profile_write_min', 30, 60);
  return new;
end;
$$;

create or replace function private.tg_limit_rules()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.hit('rule_write_min', 120, 60);
  return new;
end;
$$;

revoke all on function private.tg_stamp(), private.tg_limit_transactions(),
  private.tg_limit_profiles(), private.tg_limit_rules() from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- 6. Row cap: max 200,000 transaction rows per user
-- ---------------------------------------------------------------------
-- Why a counter table: count(*) over up to 200k rows on every insert is slow.
-- Instead statement-level triggers (with transition tables) add/subtract the
-- number of rows actually inserted/deleted in ONE update per statement, so a
-- batch of 100 rows costs one counter update. AFTER INSERT fires only for rows
-- really inserted (not for upserts that turn into updates). Soft-deleted rows
-- still count, because they still use space; delete_my_data() frees them.
create or replace function private.tg_txn_count_ins()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  for r in select user_id, count(*) as n from new_rows group by user_id loop
    insert into private.user_counters as c (user_id, txn_rows)
    values (r.user_id, r.n)
    on conflict (user_id) do update set txn_rows = c.txn_rows + excluded.txn_rows;

    if (select txn_rows from private.user_counters where user_id = r.user_id) > 200000 then
      raise exception 'Transaction limit reached (200000 rows)' using errcode = 'PT413';
    end if;
  end loop;
  return null;
end;
$$;

create or replace function private.tg_txn_count_del()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update private.user_counters c
     set txn_rows = greatest(0, c.txn_rows - s.n)
    from (select user_id, count(*) as n from old_rows group by user_id) s
   where c.user_id = s.user_id;
  return null;
end;
$$;

revoke all on function private.tg_txn_count_ins(), private.tg_txn_count_del()
  from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- 7. Attach triggers
-- ---------------------------------------------------------------------
-- BEFORE triggers fire in name order: a_stamp first, then b_limit.
drop trigger if exists a_stamp_ins on public.profiles;
drop trigger if exists a_stamp_upd on public.profiles;
create trigger a_stamp_ins before insert on public.profiles
  for each row execute function private.tg_stamp();
create trigger a_stamp_upd before update on public.profiles
  for each row execute function private.tg_stamp();

drop trigger if exists a_stamp_ins on public.transactions;
drop trigger if exists a_stamp_upd on public.transactions;
create trigger a_stamp_ins before insert on public.transactions
  for each row execute function private.tg_stamp();
create trigger a_stamp_upd before update on public.transactions
  for each row execute function private.tg_stamp();

drop trigger if exists a_stamp_ins on public.category_rules;
drop trigger if exists a_stamp_upd on public.category_rules;
create trigger a_stamp_ins before insert on public.category_rules
  for each row execute function private.tg_stamp();
create trigger a_stamp_upd before update on public.category_rules
  for each row execute function private.tg_stamp();

drop trigger if exists b_limit on public.transactions;
create trigger b_limit before insert or update on public.transactions
  for each row execute function private.tg_limit_transactions();

drop trigger if exists b_limit on public.profiles;
create trigger b_limit before insert or update on public.profiles
  for each row execute function private.tg_limit_profiles();

drop trigger if exists b_limit on public.category_rules;
create trigger b_limit before insert or update on public.category_rules
  for each row execute function private.tg_limit_rules();

drop trigger if exists c_count_ins on public.transactions;
create trigger c_count_ins after insert on public.transactions
  referencing new table as new_rows
  for each statement execute function private.tg_txn_count_ins();

drop trigger if exists c_count_del on public.transactions;
create trigger c_count_del after delete on public.transactions
  referencing old table as old_rows
  for each statement execute function private.tg_txn_count_del();

-- ---------------------------------------------------------------------
-- 8. RPC functions
-- ---------------------------------------------------------------------
-- Server clock, used by the phone as the base for its pull cursor.
create or replace function public.server_time()
returns timestamptz
language sql
stable
security invoker
set search_path = ''
as $$ select now(); $$;
revoke all on function public.server_time() from public, anon;
grant execute on function public.server_time() to authenticated;

-- Hard-deletes everything of the caller. RLS applies (SECURITY INVOKER).
-- Does not delete the login itself (auth.users); remove that in the dashboard.
create or replace function public.delete_my_data()
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  perform private.hit('delete_all', 3, 3600);
  delete from public.transactions;
  delete from public.category_rules;
  delete from public.profiles;
end;
$$;
revoke all on function public.delete_my_data() from public, anon;
grant execute on function public.delete_my_data() to authenticated;
