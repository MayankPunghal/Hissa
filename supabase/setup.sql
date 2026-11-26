-- ============================================================================
--  Hissa: complete backend setup. Paste this whole file into
--  Supabase -> SQL Editor -> New query -> Run. Safe to run again any time.
--  Money is stored as integer cents (never floats).
--  A "settle up" is an expense with is_settlement = true.
-- ============================================================================

-- ---------- tables ----------
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default '',
  email text,
  created_at timestamptz not null default now()
);

create table if not exists public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(name) between 1 and 80),
  currency text not null default 'INR',
  invite_code text not null unique default substr(replace(gen_random_uuid()::text, '-', ''), 1, 8),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now()
);

create table if not exists public.group_members (
  group_id uuid not null references public.groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index if not exists group_members_user_idx on public.group_members (user_id);

create table if not exists public.expenses (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups(id) on delete cascade,
  description text not null check (length(description) between 1 and 200),
  amount_cents bigint not null check (amount_cents > 0),
  paid_by uuid not null references auth.users(id),
  created_by uuid not null references auth.users(id),
  expense_date date not null default current_date,
  is_settlement boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists expenses_group_date_idx on public.expenses (group_id, expense_date desc);

create table if not exists public.expense_splits (
  expense_id uuid not null references public.expenses(id) on delete cascade,
  user_id uuid not null references auth.users(id),
  share_cents bigint not null check (share_cents >= 0),
  primary key (expense_id, user_id)
);

-- ---------- helpers (security definer avoids RLS recursion) ----------
create or replace function public.is_member(gid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members
                 where group_id = gid and user_id = auth.uid());
$$;

create or replace function public.shares_group(uid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members a
                 join public.group_members b on a.group_id = b.group_id
                 where a.user_id = auth.uid() and b.user_id = uid);
$$;

-- a profile row is created automatically for every new sign-up
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, name, email)
  values (new.id,
          coalesce(nullif(new.raw_user_meta_data->>'name', ''), split_part(new.email, '@', 1)),
          new.email)
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- profiles for anyone who signed up before this script ran
insert into public.profiles (id, name, email)
select id, coalesce(nullif(raw_user_meta_data->>'name', ''), split_part(email, '@', 1)), email
from auth.users on conflict (id) do nothing;

-- ---------- row level security: you only see groups you belong to ----------
alter table public.profiles       enable row level security;
alter table public.groups         enable row level security;
alter table public.group_members  enable row level security;
alter table public.expenses       enable row level security;
alter table public.expense_splits enable row level security;

drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select to authenticated
  using (id = auth.uid() or public.shares_group(id));
drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists groups_read on public.groups;
create policy groups_read on public.groups for select to authenticated
  using (public.is_member(id));
drop policy if exists groups_update on public.groups;
create policy groups_update on public.groups for update to authenticated
  using (public.is_member(id)) with check (public.is_member(id));
drop policy if exists groups_delete on public.groups;
create policy groups_delete on public.groups for delete to authenticated
  using (created_by = auth.uid());

drop policy if exists members_read on public.group_members;
create policy members_read on public.group_members for select to authenticated
  using (public.is_member(group_id));
drop policy if exists members_leave on public.group_members;
create policy members_leave on public.group_members for delete to authenticated
  using (user_id = auth.uid());

drop policy if exists expenses_read on public.expenses;
create policy expenses_read on public.expenses for select to authenticated
  using (public.is_member(group_id));
drop policy if exists expenses_delete on public.expenses;
create policy expenses_delete on public.expenses for delete to authenticated
  using (public.is_member(group_id));

drop policy if exists splits_read on public.expense_splits;
create policy splits_read on public.expense_splits for select to authenticated
  using (exists (select 1 from public.expenses e
                 where e.id = expense_id and public.is_member(e.group_id)));

-- No direct INSERT policies on groups / members / expenses / splits.
-- All writes go through the validated functions below.

-- ---------- functions the app calls ----------
create or replace function public.create_group(p_name text, p_currency text default 'INR')
returns uuid language plpgsql security definer set search_path = '' as $$
declare gid uuid;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into public.groups (name, currency, created_by)
  values (p_name, p_currency, auth.uid()) returning id into gid;
  insert into public.group_members (group_id, user_id) values (gid, auth.uid());
  return gid;
end $$;

create or replace function public.join_group(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare gid uuid;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  select id into gid from public.groups where invite_code = lower(trim(p_code));
  if gid is null then raise exception 'invalid invite code'; end if;
  insert into public.group_members (group_id, user_id) values (gid, auth.uid())
    on conflict do nothing;
  return gid;
end $$;

-- p_splits: [{"user_id":"<uuid>","share_cents":123}, ...]; must add up to p_amount_cents
create or replace function public.add_expense(
  p_group_id uuid, p_description text, p_amount_cents bigint, p_paid_by uuid,
  p_expense_date date, p_splits jsonb, p_is_settlement boolean default false)
returns uuid language plpgsql security definer set search_path = '' as $$
declare eid uuid; s jsonb; total bigint := 0;
begin
  if not public.is_member(p_group_id) then raise exception 'not a group member'; end if;
  if not exists (select 1 from public.group_members
                 where group_id = p_group_id and user_id = p_paid_by)
    then raise exception 'payer not in group'; end if;
  if jsonb_typeof(p_splits) <> 'array' or jsonb_array_length(p_splits) = 0
    then raise exception 'splits required'; end if;
  for s in select * from jsonb_array_elements(p_splits) loop
    if not exists (select 1 from public.group_members
                   where group_id = p_group_id and user_id = (s->>'user_id')::uuid)
      then raise exception 'split user not in group'; end if;
    total := total + (s->>'share_cents')::bigint;
  end loop;
  if total <> p_amount_cents then
    raise exception 'splits (%) must equal amount (%)', total, p_amount_cents;
  end if;

  insert into public.expenses (group_id, description, amount_cents, paid_by, created_by, expense_date, is_settlement)
  values (p_group_id, p_description, p_amount_cents, p_paid_by, auth.uid(),
          coalesce(p_expense_date, current_date), p_is_settlement)
  returning id into eid;
  insert into public.expense_splits (expense_id, user_id, share_cents)
  select eid, (e->>'user_id')::uuid, (e->>'share_cents')::bigint
  from jsonb_array_elements(p_splits) e;
  return eid;
end $$;

-- net per member: positive = is owed money, negative = owes money
create or replace function public.group_balances(p_group_id uuid)
returns table (user_id uuid, net_cents bigint)
language sql stable security definer set search_path = '' as $$
  select m.user_id,
    coalesce((select sum(e.amount_cents) from public.expenses e
              where e.group_id = m.group_id and e.paid_by = m.user_id), 0)
  - coalesce((select sum(s.share_cents) from public.expense_splits s
              join public.expenses e on e.id = s.expense_id
              where e.group_id = m.group_id and s.user_id = m.user_id), 0)
  from public.group_members m
  where m.group_id = p_group_id and public.is_member(p_group_id);
$$;

-- only signed-in users may call the functions
revoke execute on function
  public.create_group(text, text), public.join_group(text),
  public.add_expense(uuid, text, bigint, uuid, date, jsonb, boolean),
  public.group_balances(uuid), public.is_member(uuid), public.shares_group(uuid)
  from public, anon;
grant execute on function
  public.create_group(text, text), public.join_group(text),
  public.add_expense(uuid, text, bigint, uuid, date, jsonb, boolean),
  public.group_balances(uuid), public.is_member(uuid), public.shares_group(uuid)
  to authenticated;

-- ---------- realtime (live updates) ----------
do $$
declare t text;
begin
  foreach t in array array['expenses', 'expense_splits', 'group_members'] loop
    if not exists (select 1 from pg_publication_tables
                   where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- ---------- v0.2: group settings (rename, picture, leave, delete) ----------
-- The picture is a small JPEG (about 256 px) stored as a base64 data URL, so no Storage bucket is needed.
alter table public.groups add column if not exists image text;
alter table public.groups drop constraint if exists groups_image_size;
alter table public.groups add constraint groups_image_size check (image is null or length(image) < 150000);

create or replace function public.update_group(p_group_id uuid, p_name text, p_image text default null)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_member(p_group_id) then raise exception 'not a member of this group'; end if;
  update public.groups set name = trim(p_name), image = nullif(p_image, '') where id = p_group_id;
end $$;

-- Leaving is only allowed when you are settled up; the last person out removes the group.
create or replace function public.leave_group(p_group_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare net bigint;
begin
  if not public.is_member(p_group_id) then raise exception 'not a member of this group'; end if;
  select coalesce((select sum(amount_cents) from public.expenses where group_id = p_group_id and paid_by = auth.uid()), 0)
       - coalesce((select sum(s.share_cents) from public.expense_splits s
                   join public.expenses e on e.id = s.expense_id
                   where e.group_id = p_group_id and s.user_id = auth.uid()), 0)
    into net;
  if net <> 0 then raise exception 'settle up before leaving this group'; end if;
  delete from public.group_members where group_id = p_group_id and user_id = auth.uid();
  if not exists (select 1 from public.group_members where group_id = p_group_id) then
    delete from public.groups where id = p_group_id;
  end if;
end $$;

-- Only the person who created the group can delete it (and all its expenses).
create or replace function public.delete_group(p_group_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.groups where id = p_group_id and created_by = auth.uid()) then
    raise exception 'only the group creator can delete it';
  end if;
  delete from public.groups where id = p_group_id;
end $$;

revoke execute on function
  public.update_group(uuid, text, text), public.leave_group(uuid), public.delete_group(uuid)
  from public, anon;
grant execute on function
  public.update_group(uuid, text, text), public.leave_group(uuid), public.delete_group(uuid)
  to authenticated;

-- live group renames and pictures
do $$
begin
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'groups') then
    alter publication supabase_realtime add table public.groups;
  end if;
end $$;

-- ---------- v0.3: several payers, categories, notes, editing, recurring expenses, UPI id ----------
alter table public.expenses add column if not exists category text;
alter table public.expenses add column if not exists notes text;
alter table public.profiles add column if not exists upi_id text;
alter table public.profiles add column if not exists image text;
alter table public.profiles add column if not exists phone text;
alter table public.profiles drop constraint if exists profiles_phone_fmt;
alter table public.profiles add constraint profiles_phone_fmt check (phone is null or phone ~ '^[0-9]{10}$');
alter table public.profiles drop constraint if exists profiles_image_size;
alter table public.profiles add constraint profiles_image_size check (image is null or length(image) < 150000);

create table if not exists public.expense_payers (
  expense_id uuid not null references public.expenses(id) on delete cascade,
  user_id uuid not null references auth.users(id),
  paid_cents bigint not null check (paid_cents >= 0),
  primary key (expense_id, user_id)
);
-- older expenses had one payer: give them a payer row so every balance reads from one place
insert into public.expense_payers (expense_id, user_id, paid_cents)
select e.id, e.paid_by, e.amount_cents from public.expenses e
where not exists (select 1 from public.expense_payers p where p.expense_id = e.id);

alter table public.expense_payers enable row level security;
drop policy if exists payers_read on public.expense_payers;
create policy payers_read on public.expense_payers for select to authenticated
  using (exists (select 1 from public.expenses e where e.id = expense_id and public.is_member(e.group_id)));

-- p_payers: [{"user_id":"<uuid>","paid_cents":123}, ...] and p_splits both add up to p_amount_cents
create or replace function public.add_expense2(
  p_group_id uuid, p_description text, p_amount_cents bigint, p_payers jsonb, p_splits jsonb,
  p_expense_date date, p_is_settlement boolean default false, p_category text default null, p_notes text default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare eid uuid; s jsonb; total bigint := 0; paid bigint := 0; main uuid;
begin
  if not public.is_member(p_group_id) then raise exception 'not a group member'; end if;
  if jsonb_typeof(p_payers) <> 'array' or jsonb_array_length(p_payers) = 0 then raise exception 'payers required'; end if;
  if jsonb_typeof(p_splits) <> 'array' or jsonb_array_length(p_splits) = 0 then raise exception 'splits required'; end if;
  for s in select * from jsonb_array_elements(p_payers) loop
    if not exists (select 1 from public.group_members where group_id = p_group_id and user_id = (s->>'user_id')::uuid)
      then raise exception 'payer not in group'; end if;
    paid := paid + (s->>'paid_cents')::bigint;
  end loop;
  for s in select * from jsonb_array_elements(p_splits) loop
    if not exists (select 1 from public.group_members where group_id = p_group_id and user_id = (s->>'user_id')::uuid)
      then raise exception 'split user not in group'; end if;
    total := total + (s->>'share_cents')::bigint;
  end loop;
  if total <> p_amount_cents or paid <> p_amount_cents then
    raise exception 'payers (%) and splits (%) must both equal amount (%)', paid, total, p_amount_cents;
  end if;
  select (e->>'user_id')::uuid into main from jsonb_array_elements(p_payers) e
    order by (e->>'paid_cents')::bigint desc limit 1;
  insert into public.expenses (group_id, description, amount_cents, paid_by, created_by, expense_date, is_settlement, category, notes)
  values (p_group_id, trim(p_description), p_amount_cents, main, auth.uid(),
          coalesce(p_expense_date, current_date), p_is_settlement, nullif(p_category, ''), nullif(trim(coalesce(p_notes, '')), ''))
  returning id into eid;
  insert into public.expense_payers (expense_id, user_id, paid_cents)
  select eid, (e->>'user_id')::uuid, (e->>'paid_cents')::bigint from jsonb_array_elements(p_payers) e
  where (e->>'paid_cents')::bigint > 0;
  insert into public.expense_splits (expense_id, user_id, share_cents)
  select eid, (e->>'user_id')::uuid, (e->>'share_cents')::bigint from jsonb_array_elements(p_splits) e;
  return eid;
end $$;

-- editing = replace: the old expense goes, the corrected one takes its place
create or replace function public.edit_expense(
  p_expense_id uuid, p_description text, p_amount_cents bigint, p_payers jsonb, p_splits jsonb,
  p_expense_date date, p_category text default null, p_notes text default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare gid uuid;
begin
  select group_id into gid from public.expenses where id = p_expense_id and not is_settlement;
  if gid is null or not public.is_member(gid) then raise exception 'not a group member'; end if;
  delete from public.expenses where id = p_expense_id;
  return public.add_expense2(gid, p_description, p_amount_cents, p_payers, p_splits, p_expense_date, false, p_category, p_notes);
end $$;

-- net per member now reads the payer rows
create or replace function public.group_balances(p_group_id uuid)
returns table (user_id uuid, net_cents bigint)
language sql stable security definer set search_path = '' as $$
  select m.user_id,
    coalesce((select sum(p.paid_cents) from public.expense_payers p
              join public.expenses e on e.id = p.expense_id
              where e.group_id = m.group_id and p.user_id = m.user_id), 0)
  - coalesce((select sum(s.share_cents) from public.expense_splits s
              join public.expenses e on e.id = s.expense_id
              where e.group_id = m.group_id and s.user_id = m.user_id), 0)
  from public.group_members m
  where m.group_id = p_group_id and public.is_member(p_group_id);
$$;

create or replace function public.leave_group(p_group_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare net bigint;
begin
  if not public.is_member(p_group_id) then raise exception 'not a member of this group'; end if;
  select b.net_cents into net from public.group_balances(p_group_id) b where b.user_id = auth.uid();
  if coalesce(net, 0) <> 0 then raise exception 'settle up before leaving this group'; end if;
  delete from public.group_members where group_id = p_group_id and user_id = auth.uid();
  if not exists (select 1 from public.group_members where group_id = p_group_id) then
    delete from public.groups where id = p_group_id;
  end if;
end $$;

-- recurring expenses: stored as a template; whoever opens the group next creates the ones that are due
create table if not exists public.recurring_expenses (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups(id) on delete cascade,
  description text not null,
  amount_cents bigint not null check (amount_cents > 0),
  payers jsonb not null,
  splits jsonb not null,
  category text,
  every text not null check (every in ('week', 'month')),
  next_date date not null,
  created_by uuid not null references auth.users(id)
);
alter table public.recurring_expenses enable row level security;
drop policy if exists recurring_read on public.recurring_expenses;
create policy recurring_read on public.recurring_expenses for select to authenticated using (public.is_member(group_id));

create or replace function public.add_recurring(
  p_group_id uuid, p_description text, p_amount_cents bigint, p_payers jsonb, p_splits jsonb,
  p_category text, p_every text, p_next_date date) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_member(p_group_id) then raise exception 'not a group member'; end if;
  insert into public.recurring_expenses (group_id, description, amount_cents, payers, splits, category, every, next_date, created_by)
  values (p_group_id, trim(p_description), p_amount_cents, p_payers, p_splits, nullif(p_category, ''), p_every, p_next_date, auth.uid());
end $$;

create or replace function public.delete_recurring(p_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.recurring_expenses r where r.id = p_id and public.is_member(r.group_id);
end $$;

create or replace function public.run_recurring(p_group_id uuid) returns integer
language plpgsql security definer set search_path = '' as $$
declare r record; nd date; made integer := 0; guard integer;
begin
  if not public.is_member(p_group_id) then return 0; end if;
  for r in select * from public.recurring_expenses where group_id = p_group_id and next_date <= current_date for update loop
    guard := 0; nd := r.next_date;
    while nd <= current_date and guard < 24 loop
      perform public.add_expense2(r.group_id, r.description, r.amount_cents, r.payers, r.splits, nd, false, r.category, 'Repeats ' || r.every || 'ly');
      nd := (nd + case r.every when 'week' then interval '7 days' else interval '1 month' end)::date;
      made := made + 1; guard := guard + 1;
    end loop;
    update public.recurring_expenses set next_date = nd where id = r.id;
  end loop;
  return made;
end $$;

revoke execute on function
  public.add_expense2(uuid, text, bigint, jsonb, jsonb, date, boolean, text, text),
  public.edit_expense(uuid, text, bigint, jsonb, jsonb, date, text, text),
  public.add_recurring(uuid, text, bigint, jsonb, jsonb, text, text, date),
  public.delete_recurring(uuid), public.run_recurring(uuid)
  from public, anon;
grant execute on function
  public.add_expense2(uuid, text, bigint, jsonb, jsonb, date, boolean, text, text),
  public.edit_expense(uuid, text, bigint, jsonb, jsonb, date, text, text),
  public.add_recurring(uuid, text, bigint, jsonb, jsonb, text, text, date),
  public.delete_recurring(uuid), public.run_recurring(uuid)
  to authenticated;

do $$
declare t text;
begin
  foreach t in array array['expense_payers', 'recurring_expenses'] loop
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- the original single-payer function now goes through the new one, so old app versions keep working
create or replace function public.add_expense(
  p_group_id uuid, p_description text, p_amount_cents bigint, p_paid_by uuid,
  p_expense_date date, p_splits jsonb, p_is_settlement boolean default false)
returns uuid language sql security definer set search_path = '' as $$
  select public.add_expense2(p_group_id, p_description, p_amount_cents,
    jsonb_build_array(jsonb_build_object('user_id', p_paid_by, 'paid_cents', p_amount_cents)),
    p_splits, p_expense_date, p_is_settlement);
$$;

-- ───────────── photos on expenses (order screenshot, receipt) ─────────────
-- Own table, so expense lists stay light: the photo is fetched only when an expense is opened.
create table if not exists public.expense_photos (
  expense_id uuid primary key references public.expenses(id) on delete cascade,
  image text not null check (length(image) < 600000)
);
alter table public.expense_photos enable row level security;
drop policy if exists photos_read on public.expense_photos;
create policy photos_read on public.expense_photos for select to authenticated
  using (exists (select 1 from public.expenses e where e.id = expense_id and public.is_member(e.group_id)));

-- p_image: a small JPEG as a base64 data URL; null or '' removes the photo
create or replace function public.set_expense_photo(p_expense_id uuid, p_image text) returns void
language plpgsql security definer set search_path = '' as $$
declare gid uuid;
begin
  select group_id into gid from public.expenses where id = p_expense_id;
  if gid is null or not public.is_member(gid) then raise exception 'not a group member'; end if;
  if coalesce(p_image, '') = '' then
    delete from public.expense_photos where expense_id = p_expense_id;
  else
    insert into public.expense_photos (expense_id, image) values (p_expense_id, p_image)
    on conflict (expense_id) do update set image = excluded.image;
  end if;
end $$;
revoke execute on function public.set_expense_photo(uuid, text) from public, anon;
grant execute on function public.set_expense_photo(uuid, text) to authenticated;

-- editing replaces the expense, so carry its photo over to the new row
create or replace function public.edit_expense(
  p_expense_id uuid, p_description text, p_amount_cents bigint, p_payers jsonb, p_splits jsonb,
  p_expense_date date, p_category text default null, p_notes text default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare gid uuid; img text; eid uuid;
begin
  select group_id into gid from public.expenses where id = p_expense_id and not is_settlement;
  if gid is null or not public.is_member(gid) then raise exception 'not a group member'; end if;
  select image into img from public.expense_photos where expense_id = p_expense_id;
  delete from public.expenses where id = p_expense_id;
  eid := public.add_expense2(gid, p_description, p_amount_cents, p_payers, p_splits, p_expense_date, false, p_category, p_notes);
  if img is not null then insert into public.expense_photos (expense_id, image) values (eid, img); end if;
  return eid;
end $$;
