// Mirrors Data.kt. Money is always integer cents.
export interface Profile { id: string; name: string; email?: string | null; upi_id?: string | null; image?: string | null; phone?: string | null }
export interface Group { id: string; name: string; currency: string; invite_code: string; image?: string | null; created_by: string }
export interface SplitRow { user_id: string; share_cents: number }
export interface PayerRow { user_id: string; paid_cents: number }
export interface Expense {
  id: string; description: string; group_id: string; amount_cents: number; paid_by: string; expense_date: string; created_at: string;
  is_settlement: boolean; category?: string | null; notes?: string | null; expense_splits: SplitRow[]; expense_payers: PayerRow[];
}
export interface HomeData {
  groups: Group[]; members: Record<string, string[]>; names: Record<string, string>; myNet: Record<string, number>;
  recent: Expense[]; upis: Record<string, string>; phones: Record<string, string>; offline?: boolean;
}
export interface GroupData { members: string[]; expenses: Expense[]; net: Record<string, number> }
export interface RecurringRow { id: string; description: string; amount_cents: number; every: string; next_date: string }
export interface ExpenseInput {
  desc: string; cents: number; payers: Record<string, number>; shares: Record<string, number>;
  date: string; category?: string | null; notes?: string | null; // date is YYYY-MM-DD
  /** Small JPEG data URL (order screenshot, receipt). Saved only when photoChanged; null with photoChanged removes it. */
  photo?: string | null; photoChanged?: boolean;
}

// Expense helpers = the Kotlin member functions on Expense
export const payersOf = (e: Expense): PayerRow[] => (e.expense_payers?.length ? e.expense_payers : [{ user_id: e.paid_by, paid_cents: e.amount_cents }]);
export const paidBy = (e: Expense, uid: string) => payersOf(e).find((p) => p.user_id === uid)?.paid_cents ?? 0;
export const shareOf = (e: Expense, uid: string) => (e.expense_splits ?? []).find((s) => s.user_id === uid)?.share_cents ?? 0;
/** What uid gained (+) or lost (-) on this expense. */
export const netFor = (e: Expense, uid: string) => paidBy(e, uid) - shareOf(e, uid);
/** "Asha", "Asha and Ravi" or "3 people". */
export function payerNames(e: Expense, name: (id: string) => string): string {
  const p = [...payersOf(e)].sort((a, b) => b.paid_cents - a.paid_cents);
  return p.length === 1 ? name(p[0].user_id) : p.length === 2 ? `${name(p[0].user_id)} and ${name(p[1].user_id)}` : `${p.length} people`;
}
