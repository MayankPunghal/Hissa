// Port of Extras.kt FriendsTab (+ settle-all flow) and the shared tab scroller with pull-to-refresh.
import { ComponentChildren, JSX } from "preact";
import { createPortal } from "preact/compat";
import { useRef, useState } from "preact/hooks";
import * as api from "../lib/api";
import { settleAll, settleAllPlan, settleNet, pairwise } from "../lib/extras";
import { firstName, money } from "../lib/format";
import { fullName, home, refresh } from "../lib/model";
import { Avatar, BalanceHero, EmptyState, ErrorText, FullScreenLoading, IconButton, PrimaryButton, Spinner, AppSheet, TextButton, runner, say } from "../ui/ui";
import { AddPeopleSheet } from "./AddPeople";

/** Scrollable tab body with pull-to-refresh (PullToRefreshBox). Content runs under the frosted bar, like Android. */
export function TabScroll({ children, pad, onRefresh }: { children: ComponentChildren; pad: number; onRefresh: () => Promise<unknown> }) {
  const el = useRef<HTMLDivElement>(null); const y0 = useRef<number | null>(null);
  const [pull, setPull] = useState(0); const [busy, setBusy] = useState(false);
  const start = (e: JSX.TargetedTouchEvent<HTMLDivElement>) => { y0.current = (el.current?.scrollTop ?? 1) <= 0 ? e.touches[0].clientY : null; };
  const move = (e: JSX.TargetedTouchEvent<HTMLDivElement>) => { if (y0.current == null || busy) return; const d = e.touches[0].clientY - y0.current; if (d > 0) setPull(Math.min(90, d * 0.5)); else { y0.current = null; setPull(0); } };
  const end = async () => { if (y0.current == null) return; y0.current = null; if (pull >= 56) { setBusy(true); setPull(56); try { await onRefresh(); } finally { setBusy(false); setPull(0); } } else setPull(0); };
  return (
    <div ref={el} class="tab-scroll" onTouchStart={start} onTouchMove={move} onTouchEnd={end}>
      {(pull > 8 || busy) && <div class="circle" style={{ position: "absolute", left: "50%", top: pull - 44, marginLeft: -20, width: 40, height: 40, background: "var(--surface-high)", boxShadow: "0 1px 4px rgba(0,0,0,.25)", zIndex: 5 }}><Spinner size={24} /></div>}
      <div style={{ paddingBottom: pad, transform: pull ? `translateY(${pull * 0.4}px)` : undefined, transition: pull ? undefined : "transform .2s" }}>{children}</div>
    </div>
  );
}

/** Renders overlays (sheets, dialogs) on <body> so the tab's transform and the frosted bar can't clip or cover them. */
export const Portal = ({ children }: { children: ComponentChildren }) => createPortal(<>{children}</>, document.body);

/** AlertDialog with default (primary) text buttons, as in Extras.kt. */
function SettleDialog({ title, body, onConfirm, onDismiss }: { title: string; body: string; onConfirm: () => void; onDismiss: () => void }) {
  return <div class="dialog-root"><div class="scrim" onClick={onDismiss} /><div class="dialog" role="alertdialog" aria-label={title}>
    <div class="t-headline-s">{title}</div><div class="t-body-m muted" style={{ marginTop: 16 }}>{body}</div>
    <div class="dialog-actions"><TextButton text="Cancel" onClick={onDismiss} color="var(--primary)" /><TextButton text="Settle" onClick={onConfirm} color="var(--primary)" /></div>
  </div></div>;
}

/** M3 dropdown menu anchored under its trigger. */
export function Menu({ trigger, open, onClose, children, align = "end" }: { trigger: ComponentChildren; open: boolean; onClose: () => void; children: ComponentChildren; align?: "start" | "end" }) {
  return <div class="menu-wrap">{trigger}{open && <><div class="menu-scrim" onClick={onClose} /><div class={"menu " + align} role="menu">{children}</div></>}</div>;
}

const FILTERS = ["All friends", "Outstanding balances", "Friends you owe", "Friends who owe you"];
let savedFilter = 1; // rememberSaveable: survives tab switches

export function FriendsTab() {
  const d = home.data.value; const me = api.me.value ?? "";
  const [open, setOpen] = useState<string | null>(null);
  const [filter, setFilterState] = useState(savedFilter); const setFilter = (i: number) => { savedFilter = i; setFilterState(i); };
  const [menu, setMenu] = useState(false); const [addPeople, setAddPeople] = useState(false);
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null); const [confirm, setConfirm] = useState(false);
  const runSettle = runner({ busy: setBusy, onError: setErr });

  const body = () => {
    if (!d) return <div style={{ height: 240 }}><FullScreenLoading /></div>;
    const pairs = pairwise(me, d);
    const all = [...new Set(Object.values(d.members).flat().filter((x) => x !== me))].sort((a, b) => {
      const pa = (pairs[a] ?? []).some((l) => l.cents !== 0), pb = (pairs[b] ?? []).some((l) => l.cents !== 0);
      return pa !== pb ? (pa ? -1 : 1) : fullName(a).toLowerCase().localeCompare(fullName(b).toLowerCase());
    });
    if (!all.length) return <EmptyState title="No friends yet" body="People you share a group with show up here, with what you owe each other." />;
    // Overall line, only when every balance is in one currency (otherwise adding them would mislead).
    const every = Object.values(pairs).flat(); const curs = [...new Set(every.map((l) => l.group.currency))]; const overall = every.reduce((a, l) => a + l.cents, 0);
    const open0 = all.filter((f) => (pairs[f] ?? []).length > 0); const owing = (f: string) => (pairs[f] ?? []).reduce((a, l) => a + l.cents, 0);
    const friends = filter === 0 ? all : filter === 1 ? open0 : filter === 2 ? all.filter((f) => owing(f) < 0) : all.filter((f) => owing(f) > 0);
    return <>
      {curs.length === 1 && overall !== 0 && <div style={{ padding: "12px 20px 8px" }}><BalanceHero cents={overall} currency={curs[0]} /></div>}
      {friends.length === 0 && <EmptyState title={filter === 1 ? "All settled up" : "No one here"} body={filter === 1 ? "Nobody owes you and you owe nobody." : filter === 2 ? "You don't owe anyone right now." : filter === 3 ? "Nobody owes you right now." : "No friends yet."} />}
      {friends.map((f) => {
        const lines = pairs[f] ?? []; const cur = [...new Set(lines.map((l) => l.group.currency))]; const total = lines.reduce((a, l) => a + l.cents, 0);
        const shared = d.groups.filter((g) => (d.members[g.id] ?? []).includes(f)).length;
        return (
          <button key={f} class="row press" style={{ padding: "10px 20px" }} onClick={() => setOpen(f)}>
            <Avatar name={fullName(f)} id={f} size={46} />
            <div class="row-main" style={{ marginLeft: 14 }}>
              <div class="t-title-m ellipsis">{fullName(f)}</div>
              <div class="t-body-s muted">{shared} shared {shared === 1 ? "group" : "groups"}</div>
            </div>
            <div class="col-end">
              {lines.length === 0 ? <div class="t-label-l muted">settled up</div>
                : cur.length > 1 ? <div class="t-label-l muted">mixed currencies</div>
                : total > 0 ? <><div class="t-label-s" style={{ color: "var(--owed)" }}>owes you</div><div class="t-title-s" style={{ color: "var(--owed)" }}>{money(total, cur[0])}</div></>
                : total < 0 ? <><div class="t-label-s" style={{ color: "var(--owe)" }}>you owe</div><div class="t-title-s" style={{ color: "var(--owe)" }}>{money(total, cur[0])}</div></>
                : <div class="t-label-l muted">settled up</div>}
            </div>
          </button>
        );
      })}
    </>;
  };

  const sheet = () => {
    if (!open) return null; const f = open;
    const lines = d ? pairwise(me, d)[f] ?? [] : []; const plan = settleAllPlan(me, f, lines); const net = settleNet(me, plan);
    const who = firstName(fullName(f));
    const netText = () => Object.entries(net).map(([cur, c]) => (c >= 0 ? `${who} pays you ${money(c, cur)}` : `you pay ${who} ${money(c, cur)}`)).join(" and ");
    return <>
      <AppSheet onDismiss={() => { if (!busy) { setOpen(null); setErr(null); } }} title={fullName(f)}>
        {lines.length === 0 && <div class="t-body-l muted">You two are all settled up.</div>}
        {lines.map((l) => (
          <div key={l.group.id} class="row" style={{ padding: "10px 0" }}>
            <div class="t-title-m row-main">{l.group.name}</div>
            <div class="t-title-s" style={{ color: l.cents > 0 ? "var(--owed)" : "var(--owe)" }}>{(l.cents > 0 ? "owes you " : "you owe ") + money(l.cents, l.group.currency)}</div>
          </div>
        ))}
        {plan.length > 0 && <>
          {plan.length > 1 && <div class="t-body-m muted" style={{ paddingTop: 8 }}>Across groups: {netText()}</div>}
          <ErrorText msg={err} /><div style={{ height: 20 }} />
          <PrimaryButton text={`Settle all with ${who}`} onClick={() => setConfirm(true)} loading={busy} />
        </>}
      </AppSheet>
      {confirm && plan.length > 0 && <SettleDialog title={`Settle up with ${who}?`}
        body={`This records ${plan.length} ${plan.length === 1 ? "payment" : "payments"}, one in each group, so you and ${who} are square everywhere. ${netText().replace(/^./, (c) => c.toUpperCase())}.`}
        onDismiss={() => setConfirm(false)}
        onConfirm={() => { setConfirm(false); runSettle(async () => {
          const r = await settleAll(me, f, plan); await refresh();
          if (r.error == null) { setOpen(null); say(`All settled up with ${who}`); }
          else setErr(r.done === 0 ? r.error : `Settled ${r.done} of ${r.total} groups. ${r.error} Tap Settle all to retry the rest.`);
        }); }} />}
    </>;
  };

  return (
    <TabScroll pad={24} onRefresh={() => refresh()}>
      <div class="tab-head" style={{ paddingRight: 8 }}>
        <h1 class="t-headline-l">Friends</h1>
        <IconButton icon="person_add" label="Add people" onClick={() => setAddPeople(true)} />
        <Menu open={menu} onClose={() => setMenu(false)} trigger={<IconButton icon="tune" label="Filter friends" onClick={() => setMenu(true)} />}>
          {FILTERS.map((label, i) => (
            <button key={label} class="menu-item t-body-l" role="menuitemradio" aria-checked={filter === i} onClick={() => { setFilter(i); setMenu(false); }}>
              <span class={"radio" + (filter === i ? " on" : "")} />{label}
            </button>
          ))}
        </Menu>
      </div>
      {body()}
      {addPeople && <Portal><AddPeopleSheet groups={d?.groups ?? []} initial={null} onDismiss={() => setAddPeople(false)} /></Portal>}
      {open && <Portal>{sheet()}</Portal>}
    </TabScroll>
  );
}
