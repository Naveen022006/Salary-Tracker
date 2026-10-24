-- =====================================================================
-- Salary Tracker - Supabase backend, migration 0003: Lending (Postgres 15+)
-- Run once in: Supabase Dashboard -> SQL Editor, AFTER 0001_init.sql and
-- 0002_fix_rate_limit_bucket.sql. Safe to run again (idempotent).
-- Adds: public.people, public.loan_entries, their RLS, rate limits, row caps,
-- and an updated public.delete_my_data(). Nothing existing is changed except
-- two new columns on private.user_counters and delete_my_data().
-- No secrets in this file. Money is bigint paise.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Tables
-- ---------------------------------------------------------------------
create table if not exists public.people (
  id          uuid primary key,                         -- generated on the phone
  user_id     uuid not null default auth.uid()
                references auth.users (id) on delete cascade,
  name        text not null check (char_length(name) between 1 and 80),
  phone       text check (char_length(phone) <= 20),
  note        text check (char_length(note) <= 500),
  deleted     boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index if not exists people_user_updated_idx
  on public.people (user_id, updated_at);

create table if not exists public.loan_entries (
  id            uuid primary key,                       -- generated on the phone
  user_id       uuid not null default auth.uid()
                  references auth.users (id) on delete cascade,
  person_id     uuid not null
                  references public.people (id) on delete cascade,
  kind          text   not null
                  check (kind in ('GAVE','GOT_BACK','BORROWED','PAID_BACK')),
  amount_paise  bigint not null
                  check (amount_paise > 0 and amount_paise <= 100000000000),
  method        text   not null
                  check (method in ('CASH','UPI','BANK','CARD','OTHER')),
  app_name      text   check (char_length(app_name) <= 40),   -- e.g. GPay, PhonePe
  occurred_at   timestamptz not null,
  due_date      date,
  note          text   check (char_length(note) <= 500),
  deleted       boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create index if not exists loan_entries_user_updated_idx
  on public.loan_entries (user_id, updated_at);
create index if not exists loan_entries_person_idx
  on public.loan_entries (person_id);

-- ---------------------------------------------------------------------
-- 2. Row Level Security: enabled AND forced, authenticated only
-- ---------------------------------------------------------------------
alter table public.people       enable row level security;
alter table public.people       force  row level security;
alter table public.loan_entries enable row level security;
alter table public.loan_entries force  row level security;

revoke all on public.people, public.loan_entries from public, anon;
grant select, insert, update, delete
  on public.people, public.loan_entries
  to authenticated;

-- people
drop policy if exists people_select on public.people;
drop policy if exists people_insert on public.people;
drop policy if exists people_update on public.people;
drop policy if exists people_delete on public.people;
create policy people_select on public.people for select to authenticated
  using (user_id = (select auth.uid()));
create policy people_insert on public.people for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy people_update on public.people for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy people_delete on public.people for delete to authenticated
  using (user_id = (select auth.uid()));

-- loan_entries
drop policy if exists loan_entries_select on public.loan_entries;
drop policy if exists loan_entries_insert on public.loan_entries;
drop policy if exists loan_entries_update on public.loan_entries;
drop policy if exists loan_entries_delete on public.loan_entries;
create policy loan_entries_select on public.loan_entries for select to authenticated
  using (user_id = (select auth.uid()));
create policy loan_entries_insert on public.loan_entries for insert to authenticated
  with check (user_id = (select auth.uid()));
create policy loan_entries_update on public.loan_entries for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy loan_entries_delete on public.loan_entries for delete to authenticated
  using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- 3. Row-cap counters (columns next to txn_rows in private.user_counters)
-- ---------------------------------------------------------------------
alter table private.user_counters add column if not exists people_rows bigint not null default 0;
alter table private.user_counters add column if not exists loan_rows   bigint not null default 0;

-- ---------------------------------------------------------------------
-- 4. Trigger functions (schema private, not callable via the API)
--    private.tg_stamp() and private.hit() come from 0001 / 0002.
-- ---------------------------------------------------------------------
-- A loan entry may only point at a person of the SAME user. Runs after
-- tg_stamp (trigger names sort a_ < b_), so new.user_id is already the JWT user.
-- SECURITY DEFINER so the lookup does not depend on the caller's RLS view;
-- a person that does not exist (yet) or belongs to someone else is refused
-- with a foreign-key error (23503) -> the app retries after uploading people.
-- SECURITY INVOKER on purpose: it runs as the signed-in user, so RLS on public.people shows only that user's
-- people. (As the table owner it would hit FORCE ROW LEVEL SECURITY with no owner policy and see nothing.)
create or replace function private.tg_loan_person_owner()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
declare
  owner_id uuid;
begin
  select p.user_id into owner_id from public.people p where p.id = new.person_id;
  if owner_id is distinct from new.user_id then
    raise exception 'Person not found for this user' using errcode = '23503';
  end if;
  return new;
end;
$$;

create or replace function private.tg_limit_people()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.hit('people_write_min', 60, 60);
  return new;
end;
$$;

create or replace function private.tg_limit_loans()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.hit('loan_write_min', 300, 60);
  perform private.hit('loan_write_day', 5000, 86400);
  return new;
end;
$$;

-- Row caps: max 2,000 people and 50,000 loan entries per user (soft-deleted rows still count).
-- Same statement-level transition-table pattern as the transactions counter in 0001.
create or replace function private.tg_people_count_ins()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  for r in select user_id, count(*) as n from new_rows group by user_id loop
    insert into private.user_counters as c (user_id, people_rows)
    values (r.user_id, r.n)
    on conflict (user_id) do update set people_rows = c.people_rows + excluded.people_rows;

    if (select people_rows from private.user_counters where user_id = r.user_id) > 2000 then
      raise exception 'People limit reached (2000 rows)' using errcode = 'PT413';
    end if;
  end loop;
  return null;
end;
$$;

create or replace function private.tg_people_count_del()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update private.user_counters c
     set people_rows = greatest(0, c.people_rows - s.n)
    from (select user_id, count(*) as n from old_rows group by user_id) s
   where c.user_id = s.user_id;
  return null;
end;
$$;

create or replace function private.tg_loan_count_ins()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  for r in select user_id, count(*) as n from new_rows group by user_id loop
    insert into private.user_counters as c (user_id, loan_rows)
    values (r.user_id, r.n)
    on conflict (user_id) do update set loan_rows = c.loan_rows + excluded.loan_rows;

    if (select loan_rows from private.user_counters where user_id = r.user_id) > 50000 then
      raise exception 'Lending entry limit reached (50000 rows)' using errcode = 'PT413';
    end if;
  end loop;
  return null;
end;
$$;

create or replace function private.tg_loan_count_del()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update private.user_counters c
     set loan_rows = greatest(0, c.loan_rows - s.n)
    from (select user_id, count(*) as n from old_rows group by user_id) s
   where c.user_id = s.user_id;
  return null;
end;
$$;

revoke all on function
  private.tg_loan_person_owner(),
  private.tg_limit_people(), private.tg_limit_loans(),
  private.tg_people_count_ins(), private.tg_people_count_del(),
  private.tg_loan_count_ins(), private.tg_loan_count_del()
  from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- 5. Attach triggers (BEFORE triggers fire in name order: a_stamp, then b_*)
-- ---------------------------------------------------------------------
drop trigger if exists a_stamp_ins on public.people;
drop trigger if exists a_stamp_upd on public.people;
create trigger a_stamp_ins before insert on public.people
  for each row execute function private.tg_stamp();
create trigger a_stamp_upd before update on public.people
  for each row execute function private.tg_stamp();

drop trigger if exists a_stamp_ins on public.loan_entries;
drop trigger if exists a_stamp_upd on public.loan_entries;
create trigger a_stamp_ins before insert on public.loan_entries
  for each row execute function private.tg_stamp();
create trigger a_stamp_upd before update on public.loan_entries
  for each row execute function private.tg_stamp();

drop trigger if exists b_limit on public.people;
create trigger b_limit before insert or update on public.people
  for each row execute function private.tg_limit_people();

drop trigger if exists b_limit on public.loan_entries;
create trigger b_limit before insert or update on public.loan_entries
  for each row execute function private.tg_limit_loans();

drop trigger if exists b_owner on public.loan_entries;
create trigger b_owner before insert or update on public.loan_entries
  for each row execute function private.tg_loan_person_owner();

drop trigger if exists c_count_ins on public.people;
create trigger c_count_ins after insert on public.people
  referencing new table as new_rows
  for each statement execute function private.tg_people_count_ins();

drop trigger if exists c_count_del on public.people;
create trigger c_count_del after delete on public.people
  referencing old table as old_rows
  for each statement execute function private.tg_people_count_del();

drop trigger if exists c_count_ins on public.loan_entries;
create trigger c_count_ins after insert on public.loan_entries
  referencing new table as new_rows
  for each statement execute function private.tg_loan_count_ins();

drop trigger if exists c_count_del on public.loan_entries;
create trigger c_count_del after delete on public.loan_entries
  referencing old table as old_rows
  for each statement execute function private.tg_loan_count_del();

-- ---------------------------------------------------------------------
-- 6. delete_my_data(): same as 0001 (SECURITY INVOKER, rate limited, RLS applies)
--    plus the lending tables. Entries first, then people.
-- ---------------------------------------------------------------------
create or replace function public.delete_my_data()
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  perform private.hit('delete_all', 3, 3600);
  delete from public.loan_entries;
  delete from public.people;
  delete from public.transactions;
  delete from public.category_rules;
  delete from public.profiles;
end;
$$;
revoke all on function public.delete_my_data() from public, anon;
grant execute on function public.delete_my_data() to authenticated;

-- Make the new tables visible to the API at once.
notify pgrst, 'reload schema';
