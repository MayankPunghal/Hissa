// Port of Import.kt: CSV (Splitwise export or plain Date, Description, Amount) -> expenses for one group.
import { guessCategory } from "./categories";
import { equalSplit, parseCents, weightedSplit } from "./debts";
import { todayIso } from "./format";
import type { ExpenseInput } from "./types";

export interface ImportedExpense { input: ExpenseInput; settlement: boolean }
export interface ImportPlan { rows: ImportedExpense[]; skipped: number }

/** Splits CSV text into rows of cells, honouring "quoted, cells" and "" escapes. */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = []; let row: string[] = []; let cell = ""; let quoted = false;
  const t = text.replace(/^﻿/, "");
  for (let i = 0; i < t.length; i++) {
    const c = t[i];
    if (quoted && c === '"' && t[i + 1] === '"') { cell += '"'; i++; }
    else if (c === '"') quoted = !quoted;
    else if (!quoted && c === ",") { row.push(cell.trim()); cell = ""; }
    else if (!quoted && (c === "\n" || c === "\r")) {
      if (c === "\r" && t[i + 1] === "\n") i++;
      row.push(cell.trim()); cell = "";
      if (row.some((x) => x)) rows.push(row);
      row = [];
    } else cell += c;
  }
  row.push(cell.trim()); if (row.some((x) => x)) rows.push(row);
  return rows;
}

const sum = (o: Record<string, number>) => Object.values(o).reduce((a, b) => a + b, 0);

/** names maps each member id to the names that may appear as a column header. */
export function planImport(csv: string, members: string[], names: Record<string, string[]>, me: string): ImportPlan {
  const rows = parseCsv(csv);
  if (rows.length < 2) return { rows: [], skipped: 0 };
  const head = rows[0].map((h) => h.toLowerCase());
  const col = (...keys: string[]) => head.findIndex((h) => keys.includes(h));
  const iDate = col("date"), iDesc = col("description", "title", "name"), iCat = col("category"), iAmt = col("cost", "amount", "total");
  if (iDesc < 0 || iAmt < 0) return { rows: [], skipped: rows.length - 1 };
  const memberCol: Record<string, number> = {};
  head.forEach((h, i) => {
    if ([iDate, iDesc, iCat, iAmt].includes(i) || h === "currency") return;
    const m = members.find((x) => (names[x] ?? []).some((n) => n.toLowerCase() === h)); if (m) memberCol[m] = i;
  });
  const out: ImportedExpense[] = []; let skipped = 0;
  rows.slice(1, 2001).forEach((r) => {
    const desc = r[iDesc] ?? "";
    const raw = r[iAmt]; const cents = raw != null ? parseCents(raw.replace(/,/g, "").replace(/^-/, "")) : null;
    if (!desc.trim() || cents == null || desc.toLowerCase() === "total balance") { skipped++; return; }
    const d = (r[iDate] ?? "").slice(0, 10); const date = /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : todayIso();
    const cat = guessCategory(desc)?.id || null;
    const settlement = (r[iCat] ?? "").toLowerCase() === "payment" || desc.toLowerCase() === "payment" || desc.toLowerCase().startsWith("settle");
    const nets: Record<string, number> = {};
    for (const [m, i] of Object.entries(memberCol)) { const v = parseFloat((r[i] ?? "").replace(/,/g, "")); nets[m] = isNaN(v) ? 0 : Math.round(v * 100); }
    const pos = Object.fromEntries(Object.entries(nets).filter(([, v]) => v > 0));
    let x: ExpenseInput | null = null;
    if (Object.keys(memberCol).length && Object.keys(pos).length) {
      const paid = weightedSplit(cents, pos);
      const shares = Object.fromEntries(Object.keys(nets).map((u) => [u, (paid[u] ?? 0) - nets[u]]));
      if (Object.values(shares).some((v) => v < 0) || sum(shares) !== cents) x = null;
      else x = { desc, cents, payers: paid, shares: Object.fromEntries(Object.entries(shares).filter(([, v]) => v > 0)), date, category: settlement ? null : cat };
    } else if (!Object.keys(memberCol).length && members.length) {
      x = { desc, cents, payers: { [members.includes(me) ? me : members[0]]: cents }, shares: equalSplit(cents, members), date, category: cat };
    }
    if (!x) skipped++; else out.push({ input: x, settlement });
  });
  return { rows: out, skipped };
}
