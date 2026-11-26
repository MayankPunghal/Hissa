// Helpers for the Add expense screen (AddExpense.kt): SplitState, itemsSplit.
import { Mode, buildSplit, equalSplit, parseCents, weightedSplit } from "./debts";
import { money } from "./format";

export type Res = { ok: true; v: Record<string, number> } | { ok: false; msg: string };
export const attempt = (f: () => Record<string, number>): Res => { try { return { ok: true, v: f() }; } catch (e: any) { return { ok: false, msg: e.message }; } };

export function cleanAmount(s: string): string {
  const t = s.replace(/[^0-9.]/g, ""); const i = t.indexOf(".");
  return i < 0 ? t.slice(0, 9) : t.slice(0, i).slice(0, 9) + "." + t.slice(i + 1).replace(/\D/g, "").slice(0, 2);
}
export const fixed2 = (cents: number) => (cents / 100).toFixed(2);

/** Who is in and what was typed for them, for either "who paid" or "who owes". */
export interface SplitState { mode: Mode; chosen: string[]; vals: Record<string, string> }
export const newState = (chosen: string[]): SplitState => ({ mode: "EQUAL", chosen, vals: {} });
export const picked = (s: SplitState, members: string[]) => members.filter((m) => s.chosen.includes(m));
export const stateResult = (s: SplitState, total: number, members: string[]) => attempt(() => buildSplit(s.mode, total, picked(s, members), s.vals));

/** Stores what was typed for id and, for exact amounts and percentages, fills the last other person with whatever is left. */
export function editState(s: SplitState, id: string, text: string, members: string[], total: number | null): SplitState {
  const vals = { ...s.vals, [id]: text }; const out = { ...s, vals };
  const whole = s.mode === "EXACT" ? total : s.mode === "PERCENT" ? 10000 : null;
  if (whole == null) return out;
  const ps = picked(s, members);
  if (ps.length < 2 || !ps.includes(id)) return out;
  const target = ps.filter((p) => p !== id).slice(-1)[0];
  const fixed = ps.filter((p) => p !== target);
  if (fixed.some((p) => !vals[p]?.trim())) return out;
  const rest = Math.max(0, whole - fixed.reduce((a, p) => a + (parseCents(vals[p] ?? "") ?? 0), 0));
  vals[target] = rest % 100 === 0 ? String(rest / 100) : `${Math.floor(rest / 100)}.${String(rest % 100).padStart(2, "0")}`;
  return out;
}

export interface ItemRow { name: string; price: string; who: string[] }

/** Per-person totals from the items, with the leftover (tax, tip, fees) spread in proportion. */
export function itemsSplit(total: number, items: ItemRow[]): Res {
  return attempt(() => {
    if (!items.length) throw new Error("Add at least one item");
    const sub: Record<string, number> = {}; let sum = 0;
    for (const r of items) {
      const p = parseCents(r.price); if (p == null) throw new Error("Enter a price for every item");
      if (!r.who.length) throw new Error("Tick who shared each item");
      sum += p;
      Object.entries(equalSplit(p, [...r.who].sort())).forEach(([u, c]) => (sub[u] = (sub[u] ?? 0) + c));
    }
    if (sum > total) throw new Error(`Items add up to ${money(sum - total)} more than the total`);
    return weightedSplit(total, sub);
  });
}
