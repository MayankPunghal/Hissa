// Port of Extras.kt (non-UI): friend balances, settle-all, CSV export.
import * as api from "./api";
import { expenseTransfers } from "./debts";
import { friendly } from "./format";
import type { Group, GroupData, HomeData } from "./types";

/** What one friend owes you (+) or you owe them (-) in one group. */
export interface PairLine { group: Group; cents: number }
/** One payment to record so a pair is square in one group. */
export interface Payment { group: Group; from: string; to: string; cents: number }
export interface SettleOutcome { done: number; total: number; error: string | null }

/** Splitwise-style person-to-person balances: on every expense the payer is owed each other person's share. */
export function pairwise(me: string, d: HomeData): Record<string, PairLine[]> {
  const out: Record<string, Record<string, number>> = {};
  const add = (friend: string, gid: string, c: number) => { const m = (out[friend] ??= {}); m[gid] = (m[gid] ?? 0) + c; };
  d.recent.forEach((e) => expenseTransfers(e).forEach((t) => { if (t.to === me) add(t.from, e.group_id, t.cents); else if (t.from === me) add(t.to, e.group_id, -t.cents); }));
  return Object.fromEntries(Object.entries(out).map(([f, m]) => [f,
    d.groups.flatMap((g) => (m[g.id] ? [{ group: g, cents: m[g.id] }] : []))]));
}

/** The payments that zero out every shared group with friend: you pay where you owe, they pay where they owe. */
export const settleAllPlan = (me: string, friend: string, lines: PairLine[]): Payment[] =>
  lines.filter((l) => l.cents !== 0).map((l) => (l.cents > 0 ? { group: l.group, from: friend, to: me, cents: l.cents } : { group: l.group, from: me, to: friend, cents: -l.cents }));

/** What actually changes hands once groups are netted, per currency. + you receive, - you pay. */
export function settleNet(me: string, plan: Payment[]): Record<string, number> {
  const out: Record<string, number> = {};
  plan.forEach((p) => (out[p.group.currency] = (out[p.group.currency] ?? 0) + (p.to === me ? p.cents : -p.cents)));
  return out;
}

/** Re-reads balances, refuses if they no longer match what the user confirmed, then records one payment per group. Safe to re-run after a partial failure. */
export async function settleAll(me: string, friend: string, confirmed: Payment[]): Promise<SettleOutcome> {
  const home = await api.loadHome();
  if (home.offline) return { done: 0, total: 0, error: "You are offline. Connect and try again." };
  const fresh = settleAllPlan(me, friend, pairwise(me, home)[friend] ?? []);
  const key = (p: Payment) => `${p.group.id}|${p.from}|${p.cents}`;
  if (fresh.map(key).join() !== confirmed.map(key).join()) return { done: 0, total: fresh.length, error: "Balances changed. Please review and try again." };
  let done = 0; let error: string | null = null;
  for (const p of fresh) { try { await api.settle(p.group.id, p.from, p.to, p.cents); done++; } catch (e) { error ??= friendly(e); } }
  return { done, total: fresh.length, error };
}

/** One row per expense or payment, names resolved, ready for a spreadsheet. */
export function groupCsv(g: Group, d: GroupData, name: (id: string) => string): string {
  const q = (s: string) => `"${s.replace(/"/g, '""')}"`; const f2 = (c: number) => (c / 100).toFixed(2);
  const rows = [...d.expenses].sort((a, b) => (a.expense_date < b.expense_date ? -1 : a.expense_date > b.expense_date ? 1 : 0)).map((e) => {
    const who = e.expense_splits.filter((s) => s.share_cents > 0).map((s) => `${name(s.user_id)} ${f2(s.share_cents)}`).join("; ");
    return [e.expense_date, q(e.description), e.is_settlement ? "payment" : "expense", f2(e.amount_cents), g.currency, q(name(e.paid_by)), q(who)].join(",");
  });
  return ["Date,Description,Type,Amount,Currency,Paid by,Split", ...rows].join("\n");
}

/** Browser stand-in for the Android share sheet: downloads the CSV. */
export function shareCsv(g: Group, csv: string) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" }));
  a.download = `${g.name} expenses.csv`; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
