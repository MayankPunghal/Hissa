// Port of Demo.kt: sample data for DEV builds only (open /?demo=1). Same names, groups and expenses as the Android debug demo,
// so a screen can be compared side by side with the emulator. Never enabled in a production build.
import { equalSplit } from "./debts";
import type { Expense, ExpenseInput, Group, GroupData, HomeData, RecurringRow } from "./types";
import { netFor } from "./types";

export const ME = "me";
const names: Record<string, string> = { me: "Aarav Mehta", u1: "Asha Rao", u2: "Ravi Iyer", u3: "Meera Shah", u4: "Kabir Singh" };
const mk = (id: string, name: string, code: string, by: string): Group => ({ id, name, currency: "INR", invite_code: code, created_by: by });
let groups: Group[] = [mk("g1", "Goa trip", "goa2026x", ME), mk("g2", "Flat 402", "flat402a", "u1"), mk("g3", "Office lunches", "lunchbox", "u3"), mk("g4", "Trek crew", "trek2026", "u1")];
const members: Record<string, string[]> = { g1: [ME, "u1", "u2", "u3", "u4"], g2: [ME, "u1", "u2"], g3: [ME, "u3", "u4"], g4: ["u1", "u2"] };

const day = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return d; };
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
function exp(id: string, gid: string, desc: string, rupees: number, by: string, daysAgo: number, who: string[], settle = false, cat: string | null = null, also: string | null = null): Expense {
  const cents = rupees * 100;
  const shares = settle ? { [who[0]]: cents } : equalSplit(cents, who);
  const payers = also == null ? [] : [{ user_id: by, paid_cents: cents - Math.trunc((cents * 2) / 5) }, { user_id: also, paid_cents: Math.trunc((cents * 2) / 5) }];
  return {
    id, description: desc, group_id: gid, amount_cents: cents, paid_by: by, expense_date: iso(day(daysAgo)),
    created_at: new Date(Date.now() - daysAgo * 864e5 - id.length * 6e4).toISOString(), is_settlement: settle, category: cat, notes: null,
    expense_splits: Object.entries(shares).map(([user_id, share_cents]) => ({ user_id, share_cents })), expense_payers: payers,
  };
}
const g1m = members.g1;
let expenses: Expense[] = [
  exp("e1", "g1", "Villa booking", 28000, "u1", 4, g1m, false, "stay", "u2"),
  exp("e2", "g1", "Scooter rentals", 3200, "u2", 3, [ME, "u2", "u3"], false, "transport"),
  exp("e3", "g1", "Dinner at Fisherman's Wharf", 6450, ME, 2, g1m, false, "food"),
  exp("e4", "g1", "Beach shack lunch", 2780, "u4", 2, g1m, false, "food"),
  exp("e5", "g1", "Airport cab", 1900, "u3", 4, [ME, "u1", "u3"], false, "transport"),
  exp("e6", "g1", "Payment", 2000, "u4", 1, ["u1"], true),
  exp("e7", "g2", "Wi-Fi bill", 1179, ME, 5, members.g2),
  exp("e8", "g2", "Groceries", 3640, "u2", 3, members.g2, false, "groceries"),
  exp("e9", "g2", "Electricity", 2450, "u1", 1, members.g2, false, "utilities"),
  exp("e10", "g3", "Thali lunch", 1260, "u3", 0, members.g3),
  exp("e11", "g3", "Coffee run", 540, "u4", 6, members.g3),
];
const net = (gid: string) => { const l = expenses.filter((e) => e.group_id === gid); return Object.fromEntries(members[gid].map((u) => [u, l.reduce((a, e) => a + netFor(e, u), 0)])); };

export const home = (): HomeData => {
  const mine = groups.filter((g) => members[g.id].includes(ME));
  return {
    groups: mine, members: { ...members }, names: { ...names }, myNet: Object.fromEntries(mine.map((g) => [g.id, net(g.id)[ME] ?? 0])),
    recent: expenses.filter((e) => mine.some((g) => g.id === e.group_id)).sort((a, b) => (a.created_at < b.created_at ? 1 : a.created_at > b.created_at ? -1 : 0)), upis: { u1: "asha@upi", u2: "ravi@upi" }, phones: { u2: "9812345670" },
  };
};
export const group = (gid: string): GroupData => ({ members: members[gid], expenses: expenses.filter((e) => e.group_id === gid).sort((a, b) => (a.expense_date < b.expense_date ? 1 : a.expense_date > b.expense_date ? -1 : 0)), net: net(gid) });
export const createGroup = (name: string, currency: string) => { const id = "g" + (groups.length + 1); groups = [mk(id, name, "new" + id + "xyz", ME), ...groups]; groups[0].currency = currency; members[id] = [ME]; };
export const joinGroup = (code: string) => { const g = groups.find((x) => x.invite_code.toLowerCase() === code.toLowerCase()); if (!g) throw new Error("No group with that code"); if (!members[g.id].includes(ME)) members[g.id] = [...members[g.id], ME]; };
const build = (id: string, gid: string, x: ExpenseInput, settlement: boolean): Expense => ({
  id, description: x.desc, group_id: gid, amount_cents: x.cents, paid_by: Object.entries(x.payers).sort((a, b) => b[1] - a[1])[0][0], expense_date: x.date, created_at: new Date().toISOString(),
  is_settlement: settlement, category: x.category, notes: x.notes,
  expense_splits: Object.entries(x.shares).filter(([, v]) => v > 0).map(([user_id, share_cents]) => ({ user_id, share_cents })),
  expense_payers: Object.entries(x.payers).filter(([, v]) => v > 0).map(([user_id, paid_cents]) => ({ user_id, paid_cents })),
});
export const addExpense = (gid: string, x: ExpenseInput, s: boolean): string => { const id = "x" + Date.now(); expenses = [...expenses, build(id, gid, x, s)]; return id; };
const photos: Record<string, string> = {};
export const photo = (id: string): string | null => photos[id] ?? null;
export const setPhoto = (id: string, image: string | null) => { if (image) photos[id] = image; else delete photos[id]; };
export const editExpense = (id: string, x: ExpenseInput) => { const i = expenses.findIndex((e) => e.id === id); if (i >= 0) expenses[i] = build(id, expenses[i].group_id, x, false); };
export const deleteExpense = (id: string) => { expenses = expenses.filter((e) => e.id !== id); };
let rec: [string, RecurringRow][] = [];
export const addRecurring = (gid: string, x: ExpenseInput, every: string, next: string) => { rec.push([gid, { id: "r" + Date.now(), description: x.desc, amount_cents: x.cents, every, next_date: next }]); };
export const recurring = (gid: string) => rec.filter((r) => r[0] === gid).map((r) => r[1]);
export const deleteRecurring = (id: string) => { rec = rec.filter((r) => r[1].id !== id); };
export const updateGroup = (gid: string, name: string, image: string | null) => { groups = groups.map((g) => (g.id === gid ? { ...g, name, image: image || null } : g)); };
export const leaveGroup = (gid: string) => { if (net(gid)[ME] !== 0) throw new Error("settle up before leaving this group"); members[gid] = members[gid].filter((u) => u !== ME); };
export const deleteGroup = (gid: string) => { groups = groups.filter((g) => g.id !== gid); expenses = expenses.filter((e) => e.group_id !== gid); };
