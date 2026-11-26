// Port of GroupScreen.kt. Routes: /g/:gid, /g/:gid/e/:eid (expense focused), /g/:gid/add, /g/:gid/edit/:eid, /g/:gid/settings.
import { useEffect, useRef, useState } from "preact/hooks";
import * as api from "../lib/api";
import { catOf } from "../lib/categories";
import { Transfer, rawTransfers, simplify } from "../lib/debts";
import { dayLabel, firstName, friendly, money } from "../lib/format";
import { fullName, home, name as nm, refresh as refreshHome } from "../lib/model";
import { back, go, replace } from "../lib/nav";
import { groupCsv, shareCsv } from "../lib/extras";
import { Expense, ExpenseInput, GroupData, netFor, payerNames } from "../lib/types";
import { PhotoViewer } from "../ui/Photo";
import { AddExpenseScreen } from "./AddExpense";
import { inviteMessage } from "./AddPeople";
import { GroupSettings } from "./GroupSettings";
import { InsightItems } from "./Insights";
import { Avatar, AppSheet, BalanceHero, EmptyState, ErrorText, ExtendedFab, Field, FullScreenLoading, GroupTile, Icon, IconButton, PrimaryButton, SecondaryButton, SectionTitle, Segmented, Spinner, enterIn, runner, say, useFlip } from "../ui/ui";
import "../group.css";

const ymd = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const parse = (iso: string) => { const [y, m, d] = iso.slice(0, 10).split("-").map(Number); return new Date(y, m - 1, d); };
const plusDays = (iso: string, n: number) => { const d = parse(iso); d.setDate(d.getDate() + n); return ymd(d); };
/** LocalDate.plusMonths: clamps to the end of a shorter month. */
const plusMonth = (iso: string) => { const d = parse(iso); const day = d.getDate(); d.setDate(1); d.setMonth(d.getMonth() + 1); d.setDate(Math.min(day, new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate())); return ymd(d); };

export async function shareInvite(g: { name: string; invite_code: string } & any) {
  const text = inviteMessage(g) || api.inviteLink(g.invite_code);
  try { if (navigator.share) { await navigator.share({ text }); return; } } catch (e: any) { if (e?.name === "AbortError") return; }
  try { await navigator.clipboard.writeText(text); say("Invite copied"); } catch { say(text); }
}

/** AnimatedVisibility: keeps the last overlay mounted for the exit animation (slide 240ms + fade 180ms) after `node` goes away. */
function useOverlay(node: any) {
  const last = useRef<any>(null); const [, force] = useState(0); const open = !!node;
  if (node) last.current = node;
  useEffect(() => { if (open || !last.current) return; const id = setTimeout(() => { last.current = null; force((n) => n + 1); }, 240); return () => clearTimeout(id); }, [open]);
  return { node: node ?? last.current, exiting: !open && !!last.current };
}

/** Pull down from the top of this scroller to refresh (PullToRefreshBox). */
function usePull(sc: { current: HTMLDivElement | null }, onRefresh: () => Promise<unknown>) {
  const [pull, setPull] = useState(0); const [busy, setBusy] = useState(false); const y0 = useRef<number | null>(null); const cur = useRef(0);
  useEffect(() => {
    const el = sc.current; if (!el) return;
    const s = (e: TouchEvent) => { if (el.scrollTop <= 0 && !busy) y0.current = e.touches[0].clientY; };
    const m = (e: TouchEvent) => { if (y0.current == null) return; const d = e.touches[0].clientY - y0.current; if (d > 0 && el.scrollTop <= 0) { cur.current = Math.min(100, d * 0.5); setPull(cur.current); } else { y0.current = null; cur.current = 0; setPull(0); } };
    const en = async () => { if (y0.current == null) return; y0.current = null; if (cur.current >= 56) { setBusy(true); setPull(56); try { await onRefresh(); } finally { setBusy(false); setPull(0); cur.current = 0; } } else { setPull(0); cur.current = 0; } };
    el.addEventListener("touchstart", s, { passive: true }); el.addEventListener("touchmove", m, { passive: true }); el.addEventListener("touchend", en);
    return () => { el.removeEventListener("touchstart", s); el.removeEventListener("touchmove", m); el.removeEventListener("touchend", en); };
  });
  return (
    <div class="ptr" style={{ height: pull, opacity: Math.min(1, pull / 40) }}>{(pull > 8 || busy) && <div class="ptr-dot"><Spinner size={24} stroke={2.5} /></div>}</div>
  );
}

export function GroupScreen({ gid, rest }: { gid: string; rest: string[] }) {
  const me = api.me.value ?? "";
  const g = home.data.value?.groups.find((x) => x.id === gid);
  const [gd, setGd] = useState<GroupData | null>(null);
  const [tab, setTab] = useState(0);
  const [query, setQuery] = useState(""); const [searching, setSearching] = useState(false);
  const [range, setRange] = useState(0);
  const [detail, setDetail] = useState<Expense | null>(null);
  const [settle, setSettle] = useState<Transfer | null>(null);
  const focused = useRef(false);
  const scroller = useRef<HTMLDivElement>(null);
  const gdRef = useRef<GroupData | null>(null); gdRef.current = gd;
  const closeOverlay = () => back(`/g/${gid}`);
  const view = rest[0] === "settings" ? "settings" : rest[0] === "add" || rest[0] === "edit" ? "add" : "";
  const focusId = rest[0] === "e" ? rest[1] : null;
  const editing = rest[0] === "edit" ? gd?.expenses.find((e) => e.id === rest[1]) ?? null : null;

  useEffect(() => { if (view) setDetail(null); }, [view]); // opening Add / Settings by link closes an open expense sheet
  const reload = async () => { setGd(await api.loadGroup(gid)); };
  // If the group isn't in the home list once it has loaded (left / deleted / bad link), go home.
  useEffect(() => { if (home.data.value && !g) replace("/"); }, [home.data.value, g]);
  useEffect(() => { reload().catch((e) => { if (!gdRef.current) say(friendly(e)); }); }, [gid, home.tick.value]);
  // Repeating expenses that came due are created the first time anyone opens the group.
  useEffect(() => { api.runRecurring(gid).then((n) => { if (n > 0) reload().catch(() => {}); }); }, [gid]);
  // Arriving from the Activity tab: open that expense once its group has loaded.
  useEffect(() => { if (focusId && !focused.current && gd) { focused.current = true; setDetail(gd.expenses.find((e) => e.id === focusId) ?? null); } }, [gd, focusId]);

  const overlayNode = view === "settings" ? <GroupSettings key="settings" g={g!} members={gd?.members ?? []} net={gd?.net ?? {}} onClose={closeOverlay} onGone={() => replace("/")} />
    : view === "add" && (rest[0] === "add" || editing) ? <AddExpenseScreen key={editing?.id ?? "new"} g={g!} members={gd?.members ?? []} initial={editing} onClose={closeOverlay} onSave={async (x: ExpenseInput, every: string | null) => {
      if (editing) await api.editExpense(editing.id, x);
      else {
        await api.addExpense(gid, x);
        if (every != null) await api.addRecurring(gid, x, every, every === "week" ? plusDays(x.date, 7) : plusMonth(x.date));
      }
      closeOverlay(); await reload();
      say(editing ? "Expense updated" : every != null ? `Expense added, repeating every ${every}` : "Expense added");
    }} /> : null;
  const overlay = useOverlay(g ? overlayNode : null);
  const pull = usePull(scroller, async () => { try { await reload(); await refreshHome(true); } catch (e) { say(friendly(e)); } });
  if (!g) return <div class="g-root"><FullScreenLoading /></div>;
  const n = nm;

  const spent = gd?.expenses.filter((e) => !e.is_settlement) ?? [];

  return (
    <div class="g-root">
      <div class="g-scroll" ref={scroller}>
        {pull}
        <div class="g-top">
          <IconButton icon="arrow_back" label="Back" onClick={() => back("/")} />
          <div style={{ flex: 1 }} />
          {tab === 0 && (gd?.expenses.length ?? 0) > 3 && <IconButton icon={searching ? "close" : "search"} label={searching ? "Close search" : "Search expenses"} onClick={() => { setSearching(!searching); if (searching) setQuery(""); }} />}
          <IconButton icon="share" label="Share invite link" onClick={() => shareInvite(g)} />
          <IconButton icon="settings" label="Group settings" onClick={() => go(`/g/${gid}/settings`)} />
        </div>
        {gd == null ? <div style={{ height: 240, display: "grid", placeItems: "center" }}><FullScreenLoading /></div> : (
          <>
            <div style={{ padding: "0 20px" }}>
              <button class="press g-row" style={{ textAlign: "left", width: "100%" }} onClick={() => go(`/g/${gid}/settings`)}>
                <GroupTile g={g} size={52} />
                <div style={{ width: 14 }} />
                <div class="t-headline-l" style={{ display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden", minWidth: 0 }}>{g.name}</div>
              </button>
              <div style={{ height: 12 }} />
              {spent.length === 0 ? <div class="t-title-l">No expenses yet</div> : <BalanceHero cents={gd.net[me] ?? 0} currency={g.currency} compact />}
              <div style={{ height: 10 }} />
              <div class="t-body-m muted" style={{ whiteSpace: "pre-wrap" }}>{(spent.length ? `${money(spent.reduce((a, e) => a + e.amount_cents, 0), g.currency)} spent  ·  ` : "") + `${gd.members.length} ${gd.members.length === 1 ? "person" : "people"}`}</div>
              {gd.members.length < 2 && (<><div style={{ height: 12 }} /><button class="press g-invite t-label-l" onClick={() => shareInvite(g)}><Icon name="person_add" size={18} />Invite friends</button></>)}
              <div style={{ height: 20 }} />
              <Segmented options={["Expenses", "Balances", "Insights"]} selected={tab} onSelect={setTab} />
            </div>
            {tab === 0 && <Expenses g={g} gd={gd} me={me} query={query} setQuery={setQuery} searching={searching} onOpen={setDetail} />}
            {tab === 1 && <Balances g={g} gd={gd} me={me} onSettle={setSettle} />}
            {tab === 2 && <InsightItems g={g} d={gd} me={me} range={range} onRange={setRange} onExport={() => shareCsv(g, groupCsv(g, gd, n))} />}
          </>
        )}
      </div>
      <div class="g-fab"><ExtendedFab icon="add" text="Add expense" onClick={() => go(`/g/${gid}/add`)} /></div>

      {overlay.node && <div class={"g-overlay" + (overlay.exiting ? " closing" : "")}>{overlay.node}</div>}
      {detail && <Detail g={g} e={detail} me={me} onClose={() => setDetail(null)} onEdit={() => { const id = detail.id; setDetail(null); go(`/g/${gid}/edit/${id}`); }} onDeleted={async () => { setDetail(null); await reload(); say("Deleted"); }} />}
      {settle && <SettleSheet g={g} t={settle} onClose={() => setSettle(null)} onDone={async () => { setSettle(null); await reload(); say("Payment recorded"); }} />}
    </div>
  );
}

function Expenses({ g, gd, me, query, setQuery, searching, onOpen }: { g: any; gd: GroupData; me: string; query: string; setQuery: (q: string) => void; searching: boolean; onOpen: (e: Expense) => void }) {
  const flip = useFlip(); const q = query.trim().toLowerCase();
  const shown = !q ? gd.expenses : gd.expenses.filter((e) =>
    e.description.toLowerCase().includes(q) || (e.notes ?? "").toLowerCase().includes(q) || catOf(e.category).label.toLowerCase().includes(q) ||
    payerNames(e, nm).toLowerCase().includes(q) || (e.amount_cents / 100).toFixed(2).includes(q));
  const days = new Map<string, Expense[]>(); shown.forEach((e) => { const k = dayLabel(e.expense_date); days.set(k, [...(days.get(k) ?? []), e]); });
  return (
    <>
      {searching && <div style={{ padding: "8px 20px" }}><Field value={query} onChange={setQuery} label="Search expenses" autoFocus leading={<Icon name="search" />} /></div>}
      {gd.expenses.length === 0 ? <EmptyState title="No expenses yet" body={gd.members.length < 2 ? "Invite friends with the button above, then add the first expense." : "Add the first one and Hissa will keep the score."} /> : (
        <>
          {shown.length === 0 && <EmptyState title="No matches" body={`Nothing found for "${query.trim()}".`} />}
          <div ref={flip}>{[...days].map(([day, list]) => (
            <div key={day}>
              <SectionTitle text={day} />
              {list.map((e, i) => {
                const mine = netFor(e, me); const cat = catOf(e.category);
                const to = e.expense_splits.find((s) => s.share_cents > 0 && s.user_id !== e.paid_by)?.user_id;
                return (
                  <button key={e.id} data-flip={e.id} class="press g-item" style={enterIn(i)} onClick={() => onOpen(e)}>
                    <div class="g-icon-circle" style={{ background: e.is_settlement ? "var(--primary-container)" : "var(--surface-container)" }}>
                      <Icon name={e.is_settlement ? "check" : cat.icon} size={22} style={{ color: e.is_settlement ? "var(--on-primary-container)" : "var(--on-surface)" }} />
                    </div>
                    <div style={{ width: 14 }} />
                    <div class="g-grow">
                      <div class="t-title-m ellipsis">{e.is_settlement ? `${nm(e.paid_by)} paid ${nm(to ?? "")}` : e.description}</div>
                      <div class="t-body-s muted">{e.is_settlement ? money(e.amount_cents, g.currency) : `${payerNames(e, nm)} paid ${money(e.amount_cents, g.currency)}`}</div>
                    </div>
                    <div class="g-right">
                      {e.is_settlement && mine > 0 ? <div class="t-label-l muted">you paid</div>
                        : e.is_settlement && mine < 0 ? <div class="t-label-l muted">you received</div>
                        : mine > 0 ? <><div class="t-label-s" style={{ color: "var(--owed)" }}>you're owed</div><div class="t-title-s" style={{ color: "var(--owed)" }}>{money(mine, g.currency)}</div></>
                        : mine < 0 ? <><div class="t-label-s" style={{ color: "var(--owe)" }}>you owe</div><div class="t-title-s" style={{ color: "var(--owe)" }}>{money(mine, g.currency)}</div></>
                        : <div class="t-label-s muted">not involved</div>}
                    </div>
                  </button>
                );
              })}
            </div>
          ))}</div>
        </>
      )}
    </>
  );
}

function Balances({ g, gd, me, onSettle }: { g: any; gd: GroupData; me: string; onSettle: (t: Transfer) => void }) {
  // The actionable part comes first: what to pay (yours on top), then who is owed what.
  const plan = (api.simplifyDebts(g.id) ? simplify(gd.net) : rawTransfers(gd.expenses)).sort((a, b) => Number(b.from === me) - Number(a.from === me));
  return (
    <>
      <SectionTitle text="Settle up" />
      {plan.length === 0 && <div class="t-body-l muted" style={{ padding: "8px 20px" }}>Hisaab barabar! Everyone is even.</div>}
      {plan.map((t, i) => (
        <div key={t.from + t.to} class="g-item" style={enterIn(i)}>
          <div class="g-grow">
            <div class="t-title-m">{nm(t.from)} {t.from === me ? "pay" : "pays"} {nm(t.to)}</div>
            <div class="t-body-m muted">{money(t.cents, g.currency)}</div>
          </div>
          {t.from === me && <button class="press g-pill t-label-l" onClick={() => onSettle(t)}>Settle</button>}
        </div>
      ))}
      <SectionTitle text="Who's owed what" />
      {gd.members.map((m, i) => {
        const v = gd.net[m] ?? 0;
        return (
          <div key={m} class="g-item" style={enterIn(i)}>
            <Avatar name={fullName(m)} id={m} size={44} />
            <div style={{ width: 14 }} />
            <div class="t-title-m g-grow">{nm(m)}</div>
            {v > 0 ? <div class="t-title-s" style={{ color: "var(--owed)" }}>{m === me ? "get" : "gets"} back {money(v, g.currency)}</div>
              : v < 0 ? <div class="t-title-s" style={{ color: "var(--owe)" }}>{m === me ? "owe" : "owes"} {money(v, g.currency)}</div>
              : <div class="t-title-s muted">settled</div>}
          </div>
        );
      })}
    </>
  );
}

const Row36 = ({ id, left, right }: { id: string; left: string; right: string }) => (
  <div class="g-row" style={{ padding: "6px 0" }}>
    <Avatar name={fullName(id)} id={id} size={36} /><div style={{ width: 12 }} />
    <div class="t-body-l g-grow">{left}</div><div class="t-title-s">{right}</div>
  </div>
);

function Detail({ g, e, me, onClose, onEdit, onDeleted }: { g: any; e: Expense; me: string; onClose: () => void; onEdit: () => void; onDeleted: () => Promise<void> }) {
  const [sure, setSure] = useState(false); const [err, setErr] = useState<string | null>(null);
  const del = runner({ onError: setErr });
  const [shot, setShot] = useState<string | null>(null); const [big, setBig] = useState(false);
  useEffect(() => { setShot(null); api.expensePhoto(e.id).then(setShot).catch(() => {}); }, [e.id]);
  const payers = (e.expense_payers?.length ? e.expense_payers : [{ user_id: e.paid_by, paid_cents: e.amount_cents }]).slice().sort((a, b) => b.paid_cents - a.paid_cents);
  return (
    <AppSheet onDismiss={onClose} title={e.is_settlement ? "Payment" : e.description}>
      <div class="t-display-s">{money(e.amount_cents, g.currency)}</div>
      <div class="t-body-m muted" style={{ whiteSpace: "pre-wrap" }}>{(e.is_settlement ? "" : catOf(e.category).label + "  ·  ") + dayLabel(e.expense_date)}</div>
      {e.notes && <div class="t-body-m" style={{ paddingTop: 8 }}>{e.notes}</div>}
      {shot && <><div style={{ height: 12 }} /><img class="photo-detail" src={shot} alt="Expense photo, tap to enlarge" onClick={() => setBig(true)} />{big && <PhotoViewer src={shot} onClose={() => setBig(false)} />}</>}
      <div style={{ height: 16 }} />
      {!e.is_settlement ? (
        <>
          <div class="t-title-s muted">Paid by</div>
          {payers.map((p) => <Row36 key={p.user_id} id={p.user_id} left={nm(p.user_id)} right={money(p.paid_cents, g.currency)} />)}
          <div style={{ height: 8 }} />
          <div class="t-title-s muted">Split between</div>
        </>
      ) : <div class="t-title-s muted">{nm(e.paid_by)} paid</div>}
      {e.expense_splits.filter((s) => s.share_cents > 0).map((s) => (
        <Row36 key={s.user_id} id={s.user_id} left={e.is_settlement ? `${nm(s.user_id)} received` : `${nm(s.user_id)} ${s.user_id === me ? "owe" : "owes"}`} right={money(s.share_cents, g.currency)} />
      ))}
      <ErrorText msg={err} />
      <div style={{ height: 20 }} />
      {!e.is_settlement && <><SecondaryButton text="Edit" onClick={onEdit} /><div style={{ height: 8 }} /></>}
      <SecondaryButton text={sure ? "Tap again to delete" : "Delete"} danger onClick={() => { if (!sure) setSure(true); else del(async () => { await api.deleteExpense(e.id); await onDeleted(); }); }} />
    </AppSheet>
  );
}

function SettleSheet({ g, t, onClose, onDone }: { g: any; t: Transfer; onClose: () => void; onDone: () => Promise<void> }) {
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  const upi = home.data.value?.upis[t.to]; const phone = home.data.value?.phones[t.to];
  const pay = () => {
    if (!/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) { setErr("No UPI app found on this device."); return; }
    const q = new URLSearchParams({ pa: upi!, pn: fullName(t.to), am: (t.cents / 100).toFixed(2), cu: "INR", tn: `Hissa: ${g.name}` });
    location.href = `upi://pay?${q.toString()}`;
  };
  const copy = async () => { try { await navigator.clipboard.writeText(phone!); say("Copied. Paste it in Pay to number in your UPI app."); } catch { setErr("Couldn't copy the number."); } };
  return (
    <AppSheet onDismiss={onClose} title="Settle up">
      <div class="t-body-l muted">Record that you paid {nm(t.to)} {money(t.cents, g.currency)}? This adds a payment to the group.</div>
      <ErrorText msg={err} />
      {upi && g.currency === "INR" && <><div style={{ height: 16 }} /><SecondaryButton text={`Pay ${firstName(fullName(t.to))} with a UPI app`} onClick={pay} /></>}
      {phone && g.currency === "INR" && <><div style={{ height: upi ? 8 : 16 }} /><SecondaryButton text={`Copy ${firstName(fullName(t.to))}'s number  ${phone}`} onClick={copy} /></>}
      <div style={{ height: 24 }} />
      <PrimaryButton text="Record payment" loading={busy} onClick={() => run(async () => { await api.settle(g.id, t.from, t.to, t.cents); await onDone(); })} />
      <div style={{ height: 8 }} />
      <SecondaryButton text="Cancel" onClick={onClose} />
    </AppSheet>
  );
}
