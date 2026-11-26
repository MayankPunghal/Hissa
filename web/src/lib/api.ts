// Port of the Api object in Data.kt: same tables, same RPCs, same offline cache behaviour.
import { createClient, SupabaseClient, RealtimeChannel } from "@supabase/supabase-js";
import { signal, computed } from "@preact/signals";
import type { Expense, ExpenseInput, Group, GroupData, HomeData, Profile, RecurringRow } from "./types";
import { netFor, shareOf } from "./types";
import { extractInviteCode, todayIso } from "./format";
import * as Demo from "./demo";

const LS = {
  get: (k: string) => { try { return localStorage.getItem(k); } catch { return null; } },
  set: (k: string, v: string | null) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* private mode */ } },
};
export const store = LS;

// ---- reactive state the UI reads ----
export const client = signal<SupabaseClient | null>(null);
export const serverUrl = signal("");
/** 0 follow the device, 1 light, 2 dark. */
export const themeMode = signal(0);
/** Profile pictures by user id (small JPEG data URLs), filled by loadHome. Avatars read this. */
export const photos = signal<Record<string, string>>({});
export const session = signal<{ id: string; email: string } | null>(null);
export const authReady = signal(false);
/** Sample-data mode, DEV builds only (open /?demo=1). Never true in a production build. */
export const demo = import.meta.env.DEV && new URLSearchParams(location.search).has("demo");
export const me = computed(() => session.value?.id ?? null);
export const myEmail = computed(() => session.value?.email ?? "");
/** Invite code from a tapped link, waiting for the signed-in home screen to offer joining. */
export const pendingInvite = signal<string | null>(null);

export function applyTheme(mode: number) {
  themeMode.value = mode; LS.set("theme", String(mode));
  const r = document.documentElement;
  if (mode === 1) r.dataset.theme = "light"; else if (mode === 2) r.dataset.theme = "dark"; else delete r.dataset.theme;
  syncChrome();
}
/** Keeps the browser/OS status bar colour equal to the app background (the PWA equivalent of Android edge-to-edge bars). */
export function syncChrome() {
  const dark = themeMode.value === 2 || (themeMode.value === 0 && matchMedia("(prefers-color-scheme: dark)").matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", dark ? "#0B0B0C" : "#FFFFFF");
}
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => syncChrome());

const sb = () => { if (!client.value) throw new Error("Not connected"); return client.value; };
export { sb };

// Baked in at build time (GitHub repo variables) or typed on the connect screen, exactly like Android's BuildConfig / prefs.
const BAKED_URL = (import.meta.env.VITE_SUPABASE_URL as string | undefined) ?? "";
const BAKED_KEY = (import.meta.env.VITE_SUPABASE_KEY as string | undefined) ?? "";

export function init() {
  if (demo) { client.value = {} as SupabaseClient; session.value = { id: Demo.ME, email: "aarav@example.com" }; authReady.value = true; applyTheme(Number(LS.get("theme") ?? 0) || 0); return; }
  applyTheme(Number(LS.get("theme") ?? 0) || 0);
  const url = LS.get("url") || BAKED_URL, key = LS.get("key") || BAKED_KEY;
  if (url.startsWith("https://") && key.length > 20) connect(url, key); else authReady.value = true;
}
export function configure(url: string, key: string) {
  const u = url.trim().replace(/\/+$/, ""), k = key.trim();
  if (!u.startsWith("https://")) throw new Error("The URL must start with https://");
  if (k.length <= 20) throw new Error("That key looks too short");
  LS.set("url", u); LS.set("key", k); connect(u, k);
}
export const isBaked = () => BAKED_URL.length > 0 && BAKED_KEY.length > 0;

function connect(url: string, key: string) {
  serverUrl.value = url;
  const c = createClient(url, key, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: false, storageKey: "hissa-auth" } });
  client.value = c;
  const set = (s: any) => (session.value = s?.user ? { id: s.user.id, email: s.user.email ?? "" } : null);
  c.auth.getSession().then(({ data }) => { set(data.session); authReady.value = true; });
  c.auth.onAuthStateChange((_e, s) => set(s));
}

// ---- auth ----
export async function signIn(email: string, password: string) {
  const { error } = await sb().auth.signInWithPassword({ email: email.trim(), password }); if (error) throw error;
}
/** Returns true if signed in straight away, false if the project wants email confirmation first. */
export async function signUp(name: string, email: string, password: string): Promise<boolean> {
  const { data, error } = await sb().auth.signUp({ email: email.trim(), password, options: { data: { name: name.trim() } } });
  if (error) throw error;
  if (data.user && (data.user.identities?.length ?? 1) === 0) throw new Error("already registered");
  return data.session != null;
}
export async function confirmSignup(email: string, code: string) {
  const { error } = await sb().auth.verifyOtp({ email: email.trim(), token: code.trim(), type: "signup" }); if (error) throw error;
}
export async function resendSignupCode(email: string) {
  const { error } = await sb().auth.resend({ type: "signup", email: email.trim() }); if (error) throw error;
}
/** Emails a recovery code (the Supabase "Reset Password" template must include {{ .Token }}). */
export async function sendResetCode(email: string) {
  const { error } = await sb().auth.resetPasswordForEmail(email.trim()); if (error) throw error;
}
export async function resetPassword(email: string, code: string, newPassword: string) {
  const { error } = await sb().auth.verifyOtp({ email: email.trim(), token: code.trim(), type: "recovery" }); if (error) throw error;
  const u = await sb().auth.updateUser({ password: newPassword }); if (u.error) throw u.error;
}
export async function changePassword(newPassword: string) { if (demo) return; const { error } = await sb().auth.updateUser({ password: newPassword }); if (error) throw error; }
export async function signOut() { if (demo) return; await sb().auth.signOut(); }

const ok = <T>(r: { data: T | null; error: any }): T => { if (r.error) throw r.error; return r.data as T; };

// ---- reads ----
const WITH_SPLITS = "*, expense_splits(user_id, share_cents), expense_payers(user_id, paid_cents)";
const groups = async () => ok(await sb().from("groups").select("*").order("created_at", { ascending: false })) as Group[];
const profiles = async () => ok(await sb().from("profiles").select("*")) as Profile[];
const allMembers = async () => ok(await sb().from("group_members").select("*")) as { group_id: string; user_id: string }[];
const recentExpenses = async () => ok(await sb().from("expenses").select(WITH_SPLITS).order("created_at", { ascending: false }).limit(1000)) as Expense[];
const groupExpenses = async (gid: string) => ok(await sb().from("expenses").select(WITH_SPLITS).eq("group_id", gid).order("expense_date", { ascending: false }).order("created_at", { ascending: false })) as Expense[];
export const balances = async (gid: string) => ok(await sb().rpc("group_balances", { p_group_id: gid })) as { user_id: string; net_cents: number }[];

const cacheKey = () => "home_cache_" + (me.value ?? "");
const readCache = (): HomeData | null => { try { const s = LS.get(cacheKey()); return s ? JSON.parse(s) : null; } catch { return null; } };
const isNetworkError = (e: any) => /failed to fetch|networkerror|load failed|network request failed/i.test(String(e?.message ?? e));

export async function loadHome(): Promise<HomeData> {
  if (demo) return Demo.home();
  try {
    const gs = await groups();
    const [ps, ms, recent, nets] = await Promise.all([
      profiles(), allMembers(), recentExpenses(),
      Promise.all(gs.map(async (g) => [g.id, (await balances(g.id)).find((b) => b.user_id === me.value)?.net_cents ?? 0] as const)),
    ]);
    const ph: Record<string, string> = {}; ps.forEach((p) => { if (p.image) ph[p.id] = p.image; }); photos.value = ph;
    const members: Record<string, string[]> = {}; ms.forEach((m) => (members[m.group_id] ??= []).push(m.user_id));
    const data: HomeData = {
      groups: gs, members, names: Object.fromEntries(ps.map((p) => [p.id, p.name?.trim() ? p.name : "Friend"])),
      myNet: Object.fromEntries(nets), recent,
      upis: Object.fromEntries(ps.filter((p) => p.upi_id?.trim()).map((p) => [p.id, p.upi_id!])),
      phones: Object.fromEntries(ps.filter((p) => p.phone?.trim()).map((p) => [p.id, p.phone!])),
    };
    LS.set(cacheKey(), JSON.stringify(data));
    return data;
  } catch (e) {
    const saved = readCache(); if (saved && isNetworkError(e)) return { ...saved, offline: true }; throw e;
  }
}
export async function loadGroup(gid: string): Promise<GroupData> {
  if (demo) return Demo.group(gid);
  try {
    const [ms, expenses, net] = await Promise.all([
      allMembers().then((m) => m.filter((x) => x.group_id === gid).map((x) => x.user_id)), groupExpenses(gid),
      balances(gid).then((b) => Object.fromEntries(b.map((x) => [x.user_id, x.net_cents]))),
    ]);
    return { members: ms, expenses, net };
  } catch (e) {
    const saved = readCache(); if (!saved || !isNetworkError(e)) throw e;
    const list = saved.recent.filter((x) => x.group_id === gid).sort((a, b) => (a.expense_date < b.expense_date ? 1 : a.expense_date > b.expense_date ? -1 : 0));
    const ms = saved.members[gid] ?? [];
    return { members: ms, expenses: list, net: Object.fromEntries(ms.map((u) => [u, list.reduce((a, x) => a + netFor(x, u), 0)])) };
  }
}

// ---- realtime: fires whenever an expense, split or member row changes anywhere visible to this user ----
export function liveChannel(name: string, onChange: () => void): () => void {
  if (demo) return () => {};
  const ch: RealtimeChannel = sb().channel(name);
  ["expenses", "expense_splits", "expense_payers", "group_members", "groups"].forEach((table) =>
    ch.on("postgres_changes", { event: "*", schema: "public", table }, onChange));
  ch.subscribe();
  return () => { sb().removeChannel(ch); };
}

// ---- writes ----
export async function deleteExpense(id: string) { if (demo) return Demo.deleteExpense(id); ok(await sb().from("expenses").delete().eq("id", id).select()); }
export async function createGroup(name: string, currency = "INR") { if (demo) return Demo.createGroup(name.trim(), currency); ok(await sb().rpc("create_group", { p_name: name.trim(), p_currency: currency })); }
export async function updateGroup(gid: string, name: string, image: string | null) { if (demo) return Demo.updateGroup(gid, name.trim(), image); ok(await sb().rpc("update_group", { p_group_id: gid, p_name: name.trim(), p_image: image ?? "" })); }
export async function leaveGroup(gid: string) { if (demo) return Demo.leaveGroup(gid); ok(await sb().rpc("leave_group", { p_group_id: gid })); }
export async function deleteGroup(gid: string) { if (demo) return Demo.deleteGroup(gid); ok(await sb().rpc("delete_group", { p_group_id: gid })); }
export async function joinGroup(code: string) { if (demo) return Demo.joinGroup(code.trim()); ok(await sb().rpc("join_group", { p_code: code.trim() })); }

/** "Simplify group debts" per group, kept on this device. */
export const simplifyDebts = (gid: string) => LS.get("simplify_" + gid) !== "false";
export const setSimplifyDebts = (gid: string, on: boolean) => LS.set("simplify_" + gid, String(on));
/** "Use this split by default" per group: MODE|id,id|id:value,id:value. */
export const splitDefault = (gid: string) => LS.get("split_" + gid);
export const setSplitDefault = (gid: string, v: string | null) => LS.set("split_" + gid, v);

const rows = (m: Record<string, number>, key: string) => Object.entries(m).filter(([, c]) => c > 0).map(([u, c]) => ({ user_id: u, [key]: c }));
export async function addExpense(gid: string, x: ExpenseInput, settlement = false) {
  if (demo) { const id = Demo.addExpense(gid, x, settlement); if (x.photo) Demo.setPhoto(id, x.photo); return; }
  const id = ok(await sb().rpc("add_expense2", {
    p_group_id: gid, p_description: x.desc, p_amount_cents: x.cents, p_payers: rows(x.payers, "paid_cents"), p_splits: rows(x.shares, "share_cents"),
    p_expense_date: x.date, p_is_settlement: settlement, p_category: x.category ?? "", p_notes: x.notes ?? "",
  })) as unknown as string;
  if (x.photo) await setExpensePhoto(id, x.photo);
}
/** A payment of cents from `from` to `to`. */
export const settle = (gid: string, from: string, to: string, cents: number) =>
  addExpense(gid, { desc: "Settle up", cents, payers: { [from]: cents }, shares: { [to]: cents }, date: todayIso() }, true);
export async function editExpense(id: string, x: ExpenseInput) {
  if (demo) { Demo.editExpense(id, x); if (x.photoChanged) Demo.setPhoto(id, x.photo ?? null); return; }
  const newId = ok(await sb().rpc("edit_expense", {
    p_expense_id: id, p_description: x.desc, p_amount_cents: x.cents, p_payers: rows(x.payers, "paid_cents"), p_splits: rows(x.shares, "share_cents"),
    p_expense_date: x.date, p_category: x.category ?? "", p_notes: x.notes ?? "",
  })) as unknown as string;
  if (x.photoChanged) await setExpensePhoto(newId, x.photo ?? null); // an unchanged photo is carried over by the server
}

// ---- expense photos (own table, fetched only when an expense is opened) ----
export async function expensePhoto(id: string): Promise<string | null> {
  if (demo) return Demo.photo(id);
  const r = ok(await sb().from("expense_photos").select("image").eq("expense_id", id)) as { image: string }[];
  return r[0]?.image ?? null;
}
export async function setExpensePhoto(id: string, image: string | null) {
  if (demo) return Demo.setPhoto(id, image);
  ok(await sb().rpc("set_expense_photo", { p_expense_id: id, p_image: image ?? "" }));
}
export async function addRecurring(gid: string, x: ExpenseInput, every: string, next: string) {
  if (demo) return Demo.addRecurring(gid, x, every, next);
  ok(await sb().rpc("add_recurring", {
    p_group_id: gid, p_description: x.desc, p_amount_cents: x.cents, p_payers: rows(x.payers, "paid_cents"), p_splits: rows(x.shares, "share_cents"),
    p_category: x.category ?? "", p_every: every, p_next_date: next,
  }));
}
export const recurring = async (gid: string): Promise<RecurringRow[]> => demo ? Demo.recurring(gid) : ok(await sb().from("recurring_expenses").select("*").eq("group_id", gid).order("next_date", { ascending: true })) as RecurringRow[];
export async function deleteRecurring(id: string) { if (demo) return Demo.deleteRecurring(id); ok(await sb().rpc("delete_recurring", { p_id: id })); }
/** Creates any repeats that came due; returns how many were made. Quietly does nothing on older servers. */
export async function runRecurring(gid: string): Promise<number> {
  if (demo) return 0;
  try { const r = await sb().rpc("run_recurring", { p_group_id: gid }); return Number(r.data) || 0; } catch { return 0; }
}

// ---- profile ----
const myProfile = async () => { const id = me.value; return id ? ((ok(await sb().from("profiles").select("*").eq("id", id)) as Profile[])[0] ?? null) : null; };
async function updateProfile(patch: Partial<Profile>) { if (demo) return; const id = me.value; if (!id) return; ok(await sb().from("profiles").update(patch).eq("id", id).select()); }
export const setUpi = (upi: string) => updateProfile({ upi_id: upi.trim() || null });
export const setPhone = (phone: string) => updateProfile({ phone: phone || null });
export async function setPhoto(dataUrl: string | null) {
  await updateProfile({ image: dataUrl }); const id = me.value!; const p = { ...photos.value };
  if (dataUrl == null) delete p[id]; else p[id] = dataUrl; photos.value = p;
}
export const myPhone = async () => (demo ? "9876543210" : (await myProfile())?.phone ?? "");
export const myUpi = async () => (demo ? "aarav@upi" : (await myProfile())?.upi_id ?? "");

// ---- invite links ----
/** The link people tap to join: the invite page (opens the Android app, or the web app elsewhere). */
export function inviteLink(code: string) {
  return new URL(`join/?code=${code}`, new URL("../", location.href)).toString();
}
export const codeFromLocation = (s: string) => extractInviteCode(s);
export { shareOf };
