// Port of Debts.kt. All money in integer cents.
import { Expense, netFor, payersOf } from "./types";
export interface Transfer { from: string; to: string; cents: number }
export type Mode = "EQUAL" | "EXACT" | "PERCENT" | "SHARES";
export const MODE_LABEL: Record<Mode, string> = { EQUAL: "Equally", EXACT: "Exact", PERCENT: "Percent", SHARES: "Shares" };

/** net: positive = is owed, negative = owes. Greedy match; at most n-1 transfers. */
export function simplify(net: Record<string, number>): Transfer[] {
  const debtors = Object.entries(net).filter(([, v]) => v < 0).map(([k, v]) => [k, -v] as [string, number]).sort((a, b) => b[1] - a[1]);
  const creditors = Object.entries(net).filter(([, v]) => v > 0).map(([k, v]) => [k, v] as [string, number]).sort((a, b) => b[1] - a[1]);
  const out: Transfer[] = []; let i = 0, j = 0;
  while (i < debtors.length && j < creditors.length) {
    const pay = Math.min(debtors[i][1], creditors[j][1]);
    out.push({ from: debtors[i][0], to: creditors[j][0], cents: pay });
    debtors[i] = [debtors[i][0], debtors[i][1] - pay]; creditors[j] = [creditors[j][0], creditors[j][1] - pay];
    if (debtors[i][1] === 0) i++;
    if (creditors[j][1] === 0) j++;
  }
  return out;
}
/** Proportional split (largest remainder): shares always add up to total exactly. */
export function weightedSplit(total: number, weights: Record<string, number>): Record<string, number> {
  const keys = Object.keys(weights); const sum = keys.reduce((a, k) => a + weights[k], 0);
  const out: Record<string, number> = {}; keys.forEach((k) => (out[k] = Math.floor((total * weights[k]) / sum)));
  let left = total - keys.reduce((a, k) => a + out[k], 0);
  const order = [...keys].sort((a, b) => ((total * weights[b]) % sum) - ((total * weights[a]) % sum));
  for (const k of order) { if (left === 0) break; out[k] += 1; left--; }
  return out;
}
export const equalSplit = (total: number, ids: string[]) => weightedSplit(total, Object.fromEntries(ids.map((i) => [i, 1])));
/** "12.5" -> 1250. Null if not a positive number. */
export function parseCents(s: string): number | null {
  const t = s.trim(); if (!/^(\d+\.?\d*|\.\d+)$/.test(t)) return null;
  const n = Math.round(parseFloat(t) * 100); return n > 0 ? n : null;
}
/** Turns typed values into exact per-person cents. Throws Error with the user-facing message. */
export function buildSplit(mode: Mode, total: number, chosen: string[], values: Record<string, string>): Record<string, number> {
  if (!chosen.length) throw new Error("Pick at least one person");
  const units = (id: string): number => { const v = parseCents(values[id] ?? ""); if (v == null) throw new Error("Enter a value for everyone selected"); return v; };
  if (mode === "EQUAL") return equalSplit(total, chosen);
  if (mode === "SHARES") return weightedSplit(total, Object.fromEntries(chosen.map((c) => [c, units(c)])));
  if (mode === "PERCENT") {
    const w = Object.fromEntries(chosen.map((c) => [c, units(c)]));
    if (Object.values(w).reduce((a, b) => a + b, 0) !== 10000) throw new Error("Percentages must add up to 100");
    return weightedSplit(total, w);
  }
  const c = Object.fromEntries(chosen.map((x) => [x, units(x)]));
  if (Object.values(c).reduce((a, b) => a + b, 0) !== total) throw new Error("Amounts must add up to the total");
  return c;
}
/** Who pays whom to square this one expense. */
export function expenseTransfers(e: Expense): Transfer[] {
  const ids = [...new Set([...payersOf(e).map((p) => p.user_id), ...(e.expense_splits ?? []).map((s) => s.user_id)])];
  return simplify(Object.fromEntries(ids.map((i) => [i, netFor(e, i)])));
}
/** Who owes whom without combining debts across people (Simplify group debts off). */
export function rawTransfers(expenses: Expense[]): Transfer[] {
  const owed = new Map<string, number>(); const key = (a: string, b: string) => a + "\u0000" + b;
  expenses.forEach((e) => expenseTransfers(e).forEach((t) => owed.set(key(t.from, t.to), (owed.get(key(t.from, t.to)) ?? 0) + t.cents)));
  const out: Transfer[] = [];
  owed.forEach((v, k) => { const [a, b] = k.split("\u0000"); const net = v - (owed.get(key(b, a)) ?? 0); if (net > 0) out.push({ from: a, to: b, cents: net }); });
  return out.sort((a, b) => b.cents - a.cents);
}
