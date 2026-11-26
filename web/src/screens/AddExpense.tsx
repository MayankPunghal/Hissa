// Port of AddExpense.kt: add or edit an expense. Several people can pay (equally, by amount, percent or shares) and the cost can be
// split the same ways, or item by item from a receipt. `initial` turns it into an edit.
import { useEffect, useRef, useState } from "preact/hooks";
import type { Expense, ExpenseInput, Group } from "../lib/types";
import { payersOf } from "../lib/types";
import { MODE_LABEL, Mode, parseCents } from "../lib/debts";
import { dayLabel, firstName, money, symbol, todayIso } from "../lib/format";
import { CATS, Cat, catOf, guessCategory } from "../lib/categories";
import * as api from "../lib/api";
import { me as meSig, setSplitDefault, splitDefault } from "../lib/api";
import { PhotoViewer, encodePhoto } from "../ui/Photo";
import { fullName, name as nameOf } from "../lib/model";
import { ItemRow, SplitState, cleanAmount, editState, fixed2, itemsSplit, newState, picked, stateResult } from "../lib/split";
import { AppSheet, Avatar, ErrorText, Icon, IconButton, InfoTip, Field, PrimaryButton, SecondaryButton, SelectableAvatar, Segmented, Spinner, TextButton, runner, say, cx } from "../ui/ui";
import "../addexpense.css";

const MODES: Mode[] = ["EQUAL", "EXACT", "PERCENT", "SHARES"];
const nm = (id: string) => (id === meSig.value ? "You" : firstName(fullName(id)));

function AmountBox({ value, onChange, hint }: { value: string; onChange: (v: string) => void; hint: string }) {
  return <input class="amount-box" inputMode="decimal" value={value} placeholder={hint} onInput={(e) => { const v = cleanAmount(e.currentTarget.value); e.currentTarget.value = v; onChange(v); }} />;
}
const Check = ({ on }: { on: boolean }) => <span class={cx("cb", on && "on")}><i><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7" /></svg></i></span>;

/** The "who owes / who paid" rows without a second mode switch. */
function PeopleRowsBody({ members, st, setSt, total, cur }: { members: string[]; st: SplitState; setSt: (s: SplitState) => void; total: number | null; cur: string }) {
  const res = total != null ? stateResult(st, total, members) : null;
  const unit = st.mode === "PERCENT" ? "%" : "×";
  const toggle = (id: string) => setSt({ ...st, chosen: st.chosen.includes(id) ? st.chosen.filter((c) => c !== id) : [...st.chosen, id] });
  return <>{members.map((id) => {
    const on = st.chosen.includes(id); const mine = res?.ok ? res.v[id] : undefined;
    return (
      <div key={id} class="prow press" onClick={() => toggle(id)}>
        <Check on={on} />
        <Avatar name={fullName(id)} id={id} size={40} />
        <div class="prow-name">
          <div class="t-title-m ellipsis">{nameOf(id)}</div>
          {on && mine != null && <div class="t-body-s muted">{money(mine, cur)}</div>}
        </div>
        {on && st.mode !== "EQUAL" && <span onClick={(e) => e.stopPropagation()}><AmountBox value={st.vals[id] ?? ""} onChange={(v) => setSt(editState(st, id, v, members, total))} hint={st.mode === "EXACT" ? "0.00" : unit} /></span>}
      </div>
    );
  })}</>;
}
function PeopleRows(p: Parameters<typeof PeopleRowsBody>[0]) {
  return <><Segmented options={MODES.map((m) => MODE_LABEL[m])} selected={MODES.indexOf(p.st.mode)} onSelect={(i) => p.setSt({ ...p.st, mode: MODES[i] })} /><div style={{ height: 8 }} /><PeopleRowsBody {...p} /></>;
}

function ItemsEditor({ members, items, setItems }: { members: string[]; items: ItemRow[]; setItems: (i: ItemRow[]) => void }) {
  const set = (i: number, r: ItemRow) => setItems(items.map((x, j) => (j === i ? r : x)));
  return (
    <>
      <div style={{ display: "flex", alignItems: "center" }}><span class="t-title-s muted">Items</span><InfoTip text="Add each item and tick who shared it. Tax, tip and fees are shared in proportion." /></div>
      <div style={{ height: 8 }} />
      {items.map((it, i) => (
        <div class="item-card" key={i}>
          <div class="item-top">
            <div style={{ flex: 1, minWidth: 0 }}><Field value={it.name} onChange={(v) => set(i, { ...it, name: v.slice(0, 60) })} label="Item" /></div>
            <AmountBox value={it.price} onChange={(v) => set(i, { ...it, price: v })} hint="0.00" />
            <IconButton icon="close" label="Remove item" onClick={() => setItems(items.filter((_, j) => j !== i))} />
          </div>
          <div style={{ height: 8 }} />
          <div class="hscroll">
            {members.map((id) => <SelectableAvatar key={id} name={fullName(id)} id={id} selected={it.who.includes(id)} label={nm(id)} onClick={() => set(i, { ...it, who: it.who.includes(id) ? it.who.filter((w) => w !== id) : [...it.who, id] })} />)}
          </div>
        </div>
      ))}
      <div style={{ paddingTop: 4 }}><SecondaryButton text="Add item" onClick={() => setItems([...items, { name: "", price: "", who: [...members] }])} /></div>
    </>
  );
}

function CategorySheet({ current, onPick, onDismiss }: { current: Cat; onPick: (c: Cat) => void; onDismiss: () => void }) {
  const rows: Cat[][] = []; for (let i = 0; i < CATS.length; i += 3) rows.push(CATS.slice(i, i + 3));
  return (
    <AppSheet onDismiss={onDismiss} title="Category">
      {rows.map((row, r) => (
        <div class="cat-grid" key={r}>
          {row.map((c) => <button key={c.id} class={cx("cat-cell press", c.id === current.id && "on")} onClick={() => onPick(c)}><Icon name={c.icon} size={26} /><span class="t-label-m">{c.label}</span></button>)}
          {Array.from({ length: 3 - row.length }, (_, k) => <div key={"s" + k} style={{ flex: 1 }} />)}
        </div>
      ))}
    </AppSheet>
  );
}

const P = (d: string) => <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d={d} /></svg>;
const pad = (n: number) => String(n).padStart(2, "0");
/** Material3 DatePickerDialog: headline, month pager, weekday row, day grid, Cancel / OK. */
function DatePickerDialog({ value, onOk, onCancel }: { value: string; onOk: (iso: string) => void; onCancel: () => void }) {
  const [sel, setSel] = useState(value); const [typing, setTyping] = useState(false); const [years, setYears] = useState(false);
  const [y0, m0] = value.split("-").map(Number); const [shown, setShown] = useState({ y: y0, m: m0 - 1 });
  const d = new Date(shown.y, shown.m, 1); const lead = d.getDay(); const days = new Date(shown.y, shown.m + 1, 0).getDate();
  const iso = (day: number) => `${shown.y}-${pad(shown.m + 1)}-${pad(day)}`; const today = todayIso();
  const go = (n: number) => setShown(({ y, m }) => { const t = new Date(y, m + n, 1); return { y: t.getFullYear(), m: t.getMonth() }; });
  const sd = new Date(sel + "T00:00:00");
  return (
    <div class="dp-root">
      <div class="scrim" onClick={onCancel} />
      <div class="dp" role="dialog" aria-label="Select date">
        <div class="dp-head"><div class="t-label-m muted">Select date</div>
          <div class="dp-sel"><span class="dp-title">{sd.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}</span>
            <button class="icon-btn" aria-label={typing ? "Switch to calendar" : "Switch to text input"} onClick={() => setTyping(!typing)}><Icon name={typing ? "calendar_month" : "edit"} /></button></div></div>
        <div class="dp-div" />
        {typing ? <div style={{ padding: "16px 24px 0" }}><input class="dp-input" type="date" value={sel} onInput={(e) => e.currentTarget.value && setSel(e.currentTarget.value)} /></div> : <>
          <div class="dp-nav">
            <button class="dp-month t-label-l press" onClick={() => setYears(!years)}>{d.toLocaleDateString("en-US", { month: "long", year: "numeric" })}<span style={{ transform: years ? "rotate(180deg)" : "none", display: "flex" }}>{P("M7 10l5 5 5-5z")}</span></button>
            {!years && <><button class="icon-btn" aria-label="Previous month" onClick={() => go(-1)}>{P("M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z")}</button><button class="icon-btn" aria-label="Next month" onClick={() => go(1)}>{P("M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")}</button></>}
          </div>
          {years ? <div class="dp-years">{Array.from({ length: 201 }, (_, i) => 1926 + i).map((y) => <button key={y} ref={y === shown.y ? (el) => el?.scrollIntoView({ block: "center" }) : undefined} class={cx("dp-year t-body-l", y === shown.y && "on")} onClick={() => { setShown({ ...shown, y }); setYears(false); }}>{y}</button>)}</div> :
            <div class="dp-grid">
              {"SMTWTFS".split("").map((c, i) => <div key={"w" + i} class="dp-wd t-body-l">{c}</div>)}
              {Array.from({ length: lead }, (_, i) => <div key={"b" + i} />)}
              {Array.from({ length: days }, (_, i) => i + 1).map((n) => <button key={n} class={cx("dp-day t-body-l", iso(n) === sel && "on", iso(n) === today && "today")} onClick={() => setSel(iso(n))}>{n}</button>)}
            </div>}</>}
        <div class="dp-actions"><TextButton text="Cancel" onClick={onCancel} /><TextButton text="OK" onClick={() => onOk(sel)} /></div>
      </div>
    </div>
  );
}

export function AddExpenseScreen({ g, members, initial, onClose, onSave }: { g: Group; members: string[]; initial: Expense | null; onClose: () => void; onSave: (x: ExpenseInput, every: string | null) => Promise<void> }) {
  const me = meSig.value ?? ""; const editing = initial != null;
  const first = members.includes(me) ? me : members[0] ?? "";
  const [amount, setAmount] = useState(initial ? fixed2(initial.amount_cents) : "");
  const [desc, setDesc] = useState(initial?.description ?? "");
  const [category, setCategory] = useState<Cat>(catOf(initial?.category));
  const [catPicked, setCatPicked] = useState(initial?.category != null);
  const [date, setDate] = useState(initial?.expense_date?.slice(0, 10) || todayIso());
  const [notes, setNotes] = useState(initial?.notes ?? ""); const [showNotes, setShowNotes] = useState(!!initial?.notes?.trim());
  const [showCats, setShowCats] = useState(false); const [repeat, setRepeat] = useState(0); const [keepSplit, setKeepSplit] = useState(false);
  const [saving, setSaving] = useState(false); const [photo, setPhoto] = useState<string | null>(null); const [photoChanged, setPhotoChanged] = useState(false); const [photoBusy, setPhotoBusy] = useState(false); const [viewPhoto, setViewPhoto] = useState(false); const [scanMenu, setScanMenu] = useState(false);
  const [items, setItems] = useState<ItemRow[]>([]); const [itemsMode, setItemsMode] = useState(false);
  const [owes, setOwes] = useState<SplitState>(() => newState([...members]));
  const [paid, setPaid] = useState<SplitState>(() => newState(first ? [first] : []));
  const [multi, setMulti] = useState(false); const [payer, setPayer] = useState(first);
  const [showDate, setShowDate] = useState(false); const camRef = useRef<HTMLInputElement>(null); const picRef = useRef<HTMLInputElement>(null);

  // Start from the saved default split for this group, or from the expense being edited.
  useEffect(() => {
    if (!members.length) return;
    setOwes(newState([...members])); setPaid(newState([members.includes(me) ? me : members[0]])); setPayer(members.includes(me) ? me : members[0]);
    if (initial) {
      const ps = payersOf(initial);
      if (ps.length > 1) { setMulti(true); setPaid({ mode: "EXACT", chosen: ps.map((p) => p.user_id), vals: Object.fromEntries(ps.map((p) => [p.user_id, fixed2(p.paid_cents)])) }); }
      else setPayer(initial.paid_by);
      const sp = (initial.expense_splits ?? []);
      setOwes({ mode: "EXACT", chosen: sp.filter((s) => s.share_cents > 0).map((s) => s.user_id), vals: Object.fromEntries(sp.map((s) => [s.user_id, fixed2(s.share_cents)])) });
    } else {
      const d = splitDefault(g.id); if (!d) return;
      const parts = d.split("|"); const m = MODES.find((x) => x === parts[0]); if (!m) return;
      const chosen = (parts[1] ?? "").split(",").filter((x) => members.includes(x)); if (!chosen.length) return;
      const vals: Record<string, string> = {};
      (parts[2] ?? "").split(",").forEach((kv) => { const p = kv.split(":"); if (p.length === 2) vals[p[0]] = p[1]; });
      setOwes({ mode: m, chosen, vals }); setKeepSplit(true);
    }
  }, [members.join(","), initial?.id]);

  const cents = (() => { const c = parseCents(amount); return c != null && c <= 10_00_00_000_00 ? c : null; })();
  const ownRes = cents != null ? (itemsMode ? itemsSplit(cents, items) : stateResult(owes, cents, members)) : null;
  const payRes = cents != null ? (multi ? stateResult(paid, cents, members) : ({ ok: true, v: { [payer]: cents } } as const)) : null;

  async function attach(file: File) {
    setPhotoBusy(true);
    const enc = await encodePhoto(file);
    if (enc == null) say("Couldn't use that photo."); else { setPhoto(enc); setPhotoChanged(true); }
    setPhotoBusy(false);
  }
  // Editing: show the photo already saved with this expense.
  useEffect(() => { if (initial) api.expensePhoto(initial.id).then((p) => p && setPhoto((cur) => cur ?? p)).catch(() => {}); }, [initial?.id]);

  const ownerPicked = picked(owes, members);
  let hint: string | null = null;
  if (cents != null && cents > 0 && desc.trim()) {
    if (multi && !picked(paid, members).length) hint = "Choose who paid";
    else if (multi && payRes && !payRes.ok) hint = "Paid by: " + payRes.msg;
    else if (!itemsMode && !ownerPicked.length) hint = "Choose at least one person to split with";
    else if (ownRes && !ownRes.ok) {
      if (owes.mode === "EXACT" && !itemsMode) {
        const e = ownerPicked.reduce((a, id) => a + (parseCents(owes.vals[id] ?? "") ?? 0), 0);
        hint = e < cents ? `${money(cents - e, g.currency)} left to assign` : e > cents ? `${money(e - cents, g.currency)} over the total` : ownRes.msg;
      } else hint = ownRes.msg;
    }
  }
  const ready = !!ownRes?.ok && !!payRes?.ok && desc.trim() !== "" && !saving;
  const run = runner({ busy: setSaving });
  const save = () => {
    if (!ready || cents == null || !payRes?.ok || !ownRes?.ok) return;
    const x: ExpenseInput = { desc: desc.trim(), cents, payers: payRes.v, shares: ownRes.v, date, category: category.id || null, notes: notes.trim() || null, photo, photoChanged };
    if (!editing && !itemsMode) setSplitDefault(g.id, keepSplit ? owes.mode + "|" + ownerPicked.join(",") + "|" + Object.entries(owes.vals).map(([k, v]) => k + ":" + v).join(",") : null);
    run(() => onSave(x, [null, "week", "month"][repeat]));
  };
  const labels = [...MODES.map((m) => MODE_LABEL[m]), "Items"];
  const hintErr = cents != null && desc.trim() !== "" && ownRes != null && !ownRes.ok;

  return (
    <div class="ax">
      <div class="ax-top">
        <IconButton icon="close" label="Close" onClick={onClose} />
        <div class="t-title-m">{editing ? "Edit expense" : "New expense"}</div>
        <div style={{ position: "relative" }}>
          <IconButton icon="add_a_photo" label="Add a photo" onClick={() => !photoBusy && setScanMenu(true)} style={{ opacity: photoBusy ? 0.38 : 1 }} />
          {scanMenu && <>
            <div style={{ position: "fixed", inset: 0, zIndex: 39 }} onClick={() => setScanMenu(false)} />
            <div class="menu">
              <button class="menu-item t-body-l" onClick={() => { setScanMenu(false); camRef.current?.click(); }}><Icon name="add_a_photo" />Take a photo</button>
              <button class="menu-item t-body-l" onClick={() => { setScanMenu(false); picRef.current?.click(); }}><Icon name="photo_library" />Choose a photo</button>
            </div></>}
          <input ref={camRef} class="hidden-input" type="file" accept="image/*" capture="environment" onChange={(e) => { const f = e.currentTarget.files?.[0]; e.currentTarget.value = ""; if (f) attach(f); }} />
          <input ref={picRef} class="hidden-input" type="file" accept="image/*" onChange={(e) => { const f = e.currentTarget.files?.[0]; e.currentTarget.value = ""; if (f) attach(f); }} />
        </div>
      </div>
      <div class="ax-scroll">
        <div style={{ height: 16 }} />
        <div class="ax-amount">
          <span class="t-display-s muted">{symbol(g.currency).trim()}</span>
          <input inputMode="decimal" placeholder="0" value={amount} autoFocus={!editing} aria-label="Amount" onInput={(e) => { const v = cleanAmount(e.currentTarget.value); e.currentTarget.value = v; setAmount(v); }} />
        </div>
        <div style={{ height: 20 }} />
        <Field value={desc} onChange={(v) => { const d = v.slice(0, 200); setDesc(d); if (!catPicked) setCategory(guessCategory(d) ?? CATS[0]); }} label="What was it for?" />

        <div style={{ height: 14 }} />
        <div class="hscroll">
          <button class={cx("chip press", category.id !== "" && "chip-on")} onClick={() => setShowCats(true)}><Icon name={category.icon} /><span class="t-label-l">{category.label}</span></button>
          <button class="chip press" onClick={() => setShowDate(true)}><Icon name="calendar_month" /><span class="t-label-l">{dayLabel(date)}</span></button>
          {!showNotes && <button class="chip press" onClick={() => setShowNotes(true)}><Icon name="add" /><span class="t-label-l">Add a note</span></button>}
        </div>
        {showNotes && <><div style={{ height: 12 }} /><Field value={notes} onChange={(v) => setNotes(v.slice(0, 500))} label="Note" multiline /></>}
        {(photo || photoBusy) && <>
          <div style={{ height: 12 }} />
          <div class="photo-card">
            {photo ? <img class="photo-thumb" src={photo} alt="Attached photo" onClick={() => setViewPhoto(true)} /> : <div class="photo-thumb"><Spinner size={24} /></div>}
            <div style={{ flex: 1, marginLeft: 12 }}>
              <div class="t-title-s">{photoBusy ? "Adding photo…" : "Photo attached"}</div>
              {!photoBusy && <div class="t-body-s muted">Tap to view</div>}
            </div>
            <IconButton icon="close" label="Remove photo" onClick={() => { if (!photoBusy) { setPhoto(null); setPhotoChanged(true); } }} />
          </div></>}

        <div style={{ height: 24 }} />
        <div style={{ display: "flex", alignItems: "center" }}>
          <span class="t-title-s muted" style={{ flex: 1 }}>Paid by</span>
          <button class="mini-pill t-label-l press" onClick={() => { setMulti(!multi); if (!multi && !paid.chosen.length) setPaid({ ...paid, chosen: [payer] }); }}>{multi ? "Just one person" : "Several people"}</button>
        </div>
        <div style={{ height: 8 }} />
        {!multi && <div class="hscroll" role="radiogroup">{members.map((id) => <SelectableAvatar key={id} name={fullName(id)} id={id} selected={payer === id} label={nm(id)} onClick={() => setPayer(id)} />)}</div>}
        {multi && <PeopleRows members={members} st={paid} setSt={setPaid} total={cents} cur={g.currency} />}

        <div style={{ height: 24 }} />
        <div class="t-title-s muted">Split between</div>
        <div style={{ height: 8 }} />
        <Segmented options={labels} selected={itemsMode ? labels.length - 1 : MODES.indexOf(owes.mode)} onSelect={(i) => {
          if (i === labels.length - 1) { setItemsMode(true); if (!items.length) setItems([{ name: "", price: "", who: [...members] }]); } else { setItemsMode(false); setOwes({ ...owes, mode: MODES[i] }); }
        }} />
        <div style={{ height: 8 }} />
        {itemsMode ? <>
          <ItemsEditor members={members} items={items} setItems={setItems} />
          {ownRes?.ok && <><div style={{ height: 8 }} />{members.filter((id) => (ownRes.v[id] ?? 0) > 0).map((id) => (
            <div key={id} style={{ display: "flex", padding: "4px 0" }}><span class="t-body-l" style={{ flex: 1 }}>{nameOf(id)}</span><span class="t-title-s">{money(ownRes.v[id], g.currency)}</span></div>
          ))}</>}
        </> : <PeopleRowsBody members={members} st={owes} setSt={setOwes} total={cents} cur={g.currency} />}

        {!editing && <>
          <div style={{ height: 24 }} />
          <div class="t-title-s muted">Repeat</div>
          <div style={{ height: 8 }} />
          <Segmented options={["Never", "Every week", "Every month"]} selected={repeat} onSelect={setRepeat} />
          {!itemsMode && <div class="prow" style={{ marginTop: 12, padding: 0, cursor: "default" }}><span class="press" style={{ borderRadius: 24, cursor: "pointer" }} onClick={() => setKeepSplit(!keepSplit)}><Check on={keepSplit} /></span><span class="t-body-m">Use this split by default in {g.name}</span></div>}
        </>}
        <div style={{ height: 24 }} />
      </div>
      <div class="ax-bottom">
        {hint != null && <div class="t-body-m ax-hint" role="status" style={{ color: hintErr ? "var(--error)" : "var(--on-surface-variant)" }}>{hint}</div>}
        <PrimaryButton text={editing ? "Save changes" : "Save expense"} loading={saving} enabled={ready} onClick={save} />
      </div>
      {viewPhoto && photo && <PhotoViewer src={photo} onClose={() => setViewPhoto(false)} />}
      {showDate && <DatePickerDialog value={date} onOk={(d) => { setDate(d); setShowDate(false); }} onCancel={() => setShowDate(false)} />}
      {showCats && <CategorySheet current={category} onPick={(c) => { setCategory(c); setCatPicked(true); setShowCats(false); }} onDismiss={() => setShowCats(false)} />}
    </div>
  );
}
