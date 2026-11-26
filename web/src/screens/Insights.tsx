// Port of Insights.kt: spending charts for one group (the Insights tab body). Numbers, sizes, colours and motion copied from the Kotlin.
import { JSX } from "preact";
import { useEffect, useState } from "preact/hooks";
import { CATS, catOf } from "../lib/categories";
import { dayLabel, money } from "../lib/format";
import { fullName, name } from "../lib/model";
import type { Group, GroupData } from "../lib/types";
import { paidBy, shareOf } from "../lib/types";
import { Avatar, EmptyState, Icon, SecondaryButton, SectionTitle, Segmented } from "../ui/ui";
import "./insights.css";

export const insightRanges = ["All time", "This month", "3 months"];

/** One colour per category so the chart, legend and expense icons agree. */
const CAT_COLORS = ["#7A8699", "#E9692C", "#12A18A", "#2F80ED", "#B8860B", "#7C5CFF", "#D6409F", "#E5484D", "#0EA5C6", "#8A6D3B", "#3D9970", "#DC3545", "#F59E0B"];
const catColor = (id: string | null | undefined) => CAT_COLORS[Math.max(0, CATS.findIndex((c) => c.id === (id ?? ""))) % 13];

/** 12400 cents -> "124", 1250000 -> "12.5k": short labels above chart bars. */
function compact(cents: number): string {
  const v = cents / 100;
  return v >= 100000 ? (v / 100000).toFixed(1) + "L" : v >= 1000 ? (v / 1000).toFixed(1) + "k" : v.toFixed(0);
}

// ---- dates: ISO strings, calendar maths in UTC so there are no DST surprises ----
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DOW = ["M", "T", "W", "T", "F", "S", "S"];
const utc = (iso: string) => { const [y, m, d] = iso.slice(0, 10).split("-").map(Number); return Date.UTC(y, m - 1, d); };
const isoOf = (ms: number) => new Date(ms).toISOString().slice(0, 10);
const addDays = (iso: string, n: number) => isoOf(utc(iso) + n * 864e5);
function todayLocal() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; }
/** LocalDate.minusMonths: same day, clamped to the end of the shorter month. */
function minusMonths(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number); const t = new Date(Date.UTC(y, m - 1 - n, 1));
  const last = new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() + 1, 0)).getUTCDate();
  return isoOf(Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), Math.min(d, last)));
}
const sum = <T,>(l: T[], f: (x: T) => number) => l.reduce((a, x) => a + f(x), 0);

// FastOutSlowInEasing = cubic-bezier(.4,0,.2,1)
function ease(t: number) {
  let lo = 0, hi = 1;
  for (let i = 0; i < 20; i++) { const m = (lo + hi) / 2; const x = 3 * (1 - m) * (1 - m) * m * 0.4 + 3 * (1 - m) * m * m * 0.2 + m * m * m; if (x < t) lo = m; else hi = m; }
  const m = (lo + hi) / 2; return 3 * (1 - m) * m * m + m * m * m;
}
/** 0 -> 1 over ms, restarting when dep changes (the donut sweep). */
function useSweep(dep: unknown, ms = 800) {
  const [p, setP] = useState(0);
  useEffect(() => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { setP(1); return; }
    const t0 = performance.now(); let raf = 0;
    const tick = (now: number) => { const t = Math.min(1, (now - t0) / ms); setP(ease(t)); if (t < 1) raf = requestAnimationFrame(tick); };
    raf = requestAnimationFrame(tick); return () => cancelAnimationFrame(raf);
  }, [dep]);
  return p;
}

/** Horizontal bar that grows to its fraction over 650ms (Bar in Insights.kt). */
function Bar({ fraction, color, style }: { fraction: number; color: string; style?: JSX.CSSProperties }) {
  const [w, setW] = useState(0);
  useEffect(() => { const t = setTimeout(() => setW(Math.min(1, Math.max(0.02, fraction))), 30); return () => clearTimeout(t); }, [fraction]);
  return <div class="ins-bar" style={style}><div style={{ width: "100%", clipPath: `inset(0 ${(1 - w) * 100}% 0 0 round 8px)`, background: color }} /></div>;
}

function Donut({ byCat, total }: { byCat: [string, number][]; total: number }) {
  const sweepIn = useSweep(total);
  const R = 62, C = 75; let start = -90;
  const pt = (deg: number) => [C + R * Math.cos((deg * Math.PI) / 180), C + R * Math.sin((deg * Math.PI) / 180)];
  const arcs = byCat.map(([id, v]) => {
    const sweep = (360 * v / total) * sweepIn;
    const a0 = start + 1, sw = Math.max(sweep - 2, 0.5); start += sweep;
    const [x0, y0] = pt(a0), [x1, y1] = pt(a0 + sw);
    return <path key={id} d={`M${x0} ${y0}A${R} ${R} 0 ${sw > 180 ? 1 : 0} 1 ${x1} ${y1}`} fill="none" stroke={catColor(id)} stroke-width={26} />;
  });
  return <svg width="150" height="150" viewBox="0 0 150 150" aria-hidden="true">{arcs}</svg>;
}

/** Spending charts for one group: totals, categories, trend, weekdays, people and the biggest expenses. */
export function InsightItems({ g, d, me, range, onRange, onExport }: { g: Group; d: GroupData; me: string; range: number; onRange: (r: number) => void; onExport: () => void }) {
  const all = d.expenses.filter((e) => !e.is_settlement);
  if (!all.length) return <EmptyState title="Nothing to chart yet" body="Add a few expenses and the charts appear here." />;
  const today = todayLocal();
  const from = range === 1 ? today.slice(0, 8) + "01" : range === 2 ? minusMonths(today, 3) : "";
  const spent = all.filter((e) => !/^\d{4}-\d{2}-\d{2}/.test(e.expense_date) || e.expense_date.slice(0, 10) >= from);
  const chips = <div style={{ padding: "12px 20px" }}><Segmented options={insightRanges} selected={range} onSelect={onRange} /></div>;
  if (!spent.length) return <>{chips}<EmptyState title="Nothing in this period" body="Try All time to see every expense." /></>;

  const cur = g.currency;
  const total = sum(spent, (e) => e.amount_cents);
  const myShare = sum(spent, (e) => shareOf(e, me)), myPaid = sum(spent, (e) => paidBy(e, me));
  const biggest = spent.reduce((a, e) => (e.amount_cents > a.amount_cents ? e : a));
  const stats = [
    [["Group total", money(total, cur)], ["Your share", money(myShare, cur)], ["You paid", money(myPaid, cur)]],
    [["Per person", money(Math.trunc(total / Math.max(d.members.length, 1)), cur)], ["Average", money(Math.trunc(total / spent.length), cur)], ["Biggest", money(biggest.amount_cents, cur)]],
  ];

  // Where the money went
  const catTotals = new Map<string, number>(); spent.forEach((e) => { const id = catOf(e.category).id; catTotals.set(id, (catTotals.get(id) ?? 0) + e.amount_cents); });
  const byCat = [...catTotals.entries()].sort((a, b) => b[1] - a[1]);
  const pct = (v: number) => Math.floor((v * 100) / total);
  const desc = byCat.map(([id, v]) => `${catOf(id).label} ${pct(v)} percent`).join(", ");

  // Spend per day (last 14 days that have data)
  const perDay = new Map<string, number>(); spent.forEach((e) => { const k = e.expense_date.slice(0, 10); perDay.set(k, (perDay.get(k) ?? 0) + e.amount_cents); });
  const dayKeys = [...perDay.keys()].sort();
  let dayChart: JSX.Element | null = null;
  if (dayKeys.length >= 2) {
    const last = dayKeys[dayKeys.length - 1];
    const days = Array.from({ length: 14 }, (_, i) => addDays(last, i - 13)).filter((x) => x >= dayKeys[0]);
    const peakDay = days.reduce((a, x) => ((perDay.get(x) ?? 0) > (perDay.get(a) ?? 0) ? x : a));
    const peak = Math.max(perDay.get(peakDay) ?? 1, 1);
    dayChart = <>
      <SectionTitle text="Spend per day" />
      <div style={{ padding: "0 20px" }}>
        <div class="ins-cols" style={{ height: 110, gap: 4 }}>
          {days.map((x) => <div key={x} style={{ flex: 1, height: Math.max((100 * (perDay.get(x) ?? 0)) / peak, 3), borderRadius: "6px 6px 0 0", background: x === peakDay ? "var(--primary)" : "color-mix(in srgb, var(--primary) 35%, transparent)" }} />)}
        </div>
        <div style={{ display: "flex", paddingTop: 6 }} class="t-label-s muted">
          <span style={{ flex: 1 }}>{dayLabel(days[0])}</span>
          <span>Peak {dayLabel(peakDay)}: {money(peak, cur)}</span>
        </div>
      </div>
    </>;
  }

  // By month (last 6)
  const monthTotals = new Map<string, number>(); spent.forEach((e) => { const k = e.expense_date.slice(0, 7); monthTotals.set(k, (monthTotals.get(k) ?? 0) + e.amount_cents); });
  let monthChart: JSX.Element | null = null;
  if (monthTotals.size >= 2) {
    const last = [...monthTotals.keys()].sort().slice(-6);
    const peak = Math.max(...last.map((k) => monthTotals.get(k)!), 1);
    monthChart = <>
      <SectionTitle text="By month" />
      <div class="ins-cols" style={{ height: 150, gap: 10, padding: "0 20px" }}>
        {last.map((k) => { const v = monthTotals.get(k)!; return (
          <div key={k} class="ins-col" style={{ flex: 1, height: "100%" }}>
            <div class="t-label-s muted" style={{ whiteSpace: "nowrap" }}>{compact(v)}</div>
            <div style={{ height: 4 }} />
            <div style={{ width: 36, height: Math.max((96 * v) / peak, 4), borderRadius: "10px 10px 0 0", background: "var(--primary)" }} />
            <div style={{ height: 6 }} />
            <div class="t-label-s muted">{MONTHS[Number(k.slice(5)) - 1]}</div>
          </div>
        ); })}
      </div>
    </>;
  }

  // By day of the week (Monday first)
  let dowChart: JSX.Element | null = null;
  if (spent.length >= 3) {
    const byDow = [0, 0, 0, 0, 0, 0, 0]; spent.forEach((e) => { byDow[(new Date(utc(e.expense_date)).getUTCDay() + 6) % 7] += e.amount_cents; });
    const peak = Math.max(...byDow, 1);
    dowChart = <>
      <SectionTitle text="By day of the week" />
      <div class="ins-cols" style={{ height: 120, gap: 8, padding: "0 20px" }}>
        {byDow.map((v, i) => (
          <div key={i} class="ins-col" style={{ flex: 1, height: "100%" }}>
            <div style={{ width: "100%", height: Math.max((80 * v) / peak, 3), borderRadius: "8px 8px 0 0", background: v === peak && v > 0 ? "var(--primary)" : "var(--surface-high)" }} />
            <div style={{ height: 6 }} />
            <div class="t-label-s muted">{DOW[i]}</div>
          </div>
        ))}
      </div>
    </>;
  }

  // People: paid against own share
  const people = d.members.map((m) => [m, sum(spent, (e) => paidBy(e, m)), sum(spent, (e) => shareOf(e, m))] as const).sort((a, b) => b[1] - a[1]);
  const top = Math.max(...people.map((p) => Math.max(p[1], p[2])), 1);

  return <>
    {chips}
    <div style={{ padding: "0 20px", display: "flex", flexDirection: "column", gap: 12 }}>
      {stats.map((row, i) => (
        <div key={i} style={{ display: "flex", gap: 12 }}>
          {row.map(([label, v]) => (
            <div key={label} class="ins-stat">
              <div class="t-label-m muted">{label}</div>
              <div style={{ height: 4 }} />
              <div class="t-title-s ellipsis">{v}</div>
            </div>
          ))}
        </div>
      ))}
    </div>

    <SectionTitle text="Where it went" />
    <div style={{ display: "flex", alignItems: "center", padding: "0 20px" }}>
      <div style={{ width: 150, height: 150, position: "relative", flex: "none" }} role="img" aria-label={`Spending by category: ${desc}`}>
        <Donut byCat={byCat} total={total} />
        <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
          <div class="t-label-m muted">Total</div>
          <div class="t-title-m">{compact(total)}</div>
        </div>
      </div>
      <div style={{ width: 16, flex: "none" }} />
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 6 }}>
        {byCat.slice(0, 5).map(([id, v]) => (
          <div key={id} style={{ display: "flex", alignItems: "center" }}>
            <span style={{ width: 10, height: 10, borderRadius: "50%", background: catColor(id), flex: "none" }} />
            <span style={{ width: 8, flex: "none" }} />
            <span class="t-body-s ellipsis" style={{ flex: 1, minWidth: 0 }}>{catOf(id).label}</span>
            <span class="t-label-l">{pct(v)}%</span>
          </div>
        ))}
      </div>
    </div>
    <div style={{ height: 12 }} />
    {byCat.map(([id, v]) => (
      <div key={id} style={{ display: "flex", alignItems: "center", padding: "6px 20px" }}>
        <div class="ins-chip" style={{ background: `color-mix(in srgb, ${catColor(id)} 16%, transparent)`, color: catColor(id) }}><Icon name={catOf(id).icon} size={20} /></div>
        <div style={{ width: 12, flex: "none" }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex" }}>
            <span class="t-body-m" style={{ flex: 1 }}>{catOf(id).label}</span>
            <span class="t-label-l">{money(v, cur)}</span>
          </div>
          <div style={{ height: 6 }} />
          <Bar fraction={v / byCat[0][1]} color={catColor(id)} />
        </div>
      </div>
    ))}

    {dayChart}
    {monthChart}
    {dowChart}

    <SectionTitle text="Paid vs share" />
    {people.map(([m, paid, share]) => (
      <div key={m} style={{ display: "flex", alignItems: "center", padding: "6px 20px" }}>
        <Avatar name={fullName(m)} id={m} size={32} />
        <div style={{ width: 12, flex: "none" }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div class="t-body-m">{name(m)}</div>
          <div style={{ height: 6 }} />
          <div style={{ display: "flex", alignItems: "center" }}>
            <Bar fraction={paid / top} color="var(--primary)" style={{ flex: 1 }} />
            <span class="t-label-m ellipsis" style={{ width: 110, paddingLeft: 8, flex: "none" }}>paid {money(paid, cur)}</span>
          </div>
          <div style={{ height: 4 }} />
          <div style={{ display: "flex", alignItems: "center" }}>
            <Bar fraction={share / top} color="var(--outline)" style={{ flex: 1 }} />
            <span class="t-label-m muted ellipsis" style={{ width: 110, paddingLeft: 8, flex: "none" }}>share {money(share, cur)}</span>
          </div>
        </div>
      </div>
    ))}

    <SectionTitle text="Biggest expenses" />
    {[...spent].sort((a, b) => b.amount_cents - a.amount_cents).slice(0, 5).map((e) => (
      <div key={e.id} style={{ display: "flex", alignItems: "center", padding: "8px 20px" }}>
        <div class="ins-chip" style={{ background: "var(--surface-container)" }}><Icon name={catOf(e.category).icon} size={20} /></div>
        <div style={{ width: 12, flex: "none" }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div class="t-body-m ellipsis">{e.description}</div>
          <div class="t-body-s muted">{dayLabel(e.expense_date)}</div>
        </div>
        <div class="t-title-s">{money(e.amount_cents, cur)}</div>
      </div>
    ))}

    <div style={{ height: 20 }} />
    <div style={{ padding: "0 20px" }}><SecondaryButton text="Export as CSV" onClick={onExport} /></div>
  </>;
}
