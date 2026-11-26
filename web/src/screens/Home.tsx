// Port of Home.kt: bottom nav shell (Groups / Friends / Activity / Account), sheets, and the tabs themselves. Friends is in Friends.tsx.
import { ComponentChildren } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import "../home.css";
import * as api from "../lib/api";
import { catOf } from "../lib/categories";
import { Check, agoLabel, dayLabel, extractInviteCode, money, symbol } from "../lib/format";
import { fullName, home, name, refresh } from "../lib/model";
import { go, replace, segs } from "../lib/nav";
import { netFor, payerNames } from "../lib/types";
import type { Group, HomeData } from "../lib/types";
import { CropDialog } from "../ui/Crop";
import {
  AppSheet, Avatar, BalanceHero, Divider, EmptyState, ErrorText, ExtendedFab, Field, FullScreenLoading, GroupTile, Icon, IconButton, InfoTip,
  PasswordField, PrimaryButton, SecondaryButton, SectionTitle, Segmented, enterIn, runner, say, useFlip,
} from "../ui/ui";
import { FriendsTab, Menu, Portal, TabScroll } from "./Friends";

// "Install app": Chrome/Edge/Android offer a real install prompt; iPhone Safari has none, so we show the Share-sheet steps.
let installEvt: any = null;
addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); installEvt = e; });
const standalone = () => matchMedia("(display-mode: standalone)").matches || (navigator as any).standalone === true;
const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const VERSION = (import.meta.env.VITE_APP_VERSION as string | undefined) ?? "";
const TAB_ROUTES = ["/", "/friends", "/activity", "/account"];
const currentTab = () => { const s = segs()[0]; return s === "friends" ? 1 : s === "activity" ? 2 : s === "account" ? 3 : 0; };

export function Home({ openServer }: { openServer: () => void }) {
  const tab = currentTab();
  const [sheet, setSheet] = useState<"" | "new" | "join">("");
  // A tapped invite link opens the join sheet with its code filled in.
  useEffect(() => { if (api.pendingInvite.value) setSheet("join"); }, [api.pendingInvite.value]);
  const tabs: [string, string][] = [["Groups", "groups"], ["Friends", "person"], ["Activity", "receipt_long"], ["Account", "account_circle"]];
  return (
    <div class="home">
      <div key={tab} class="tab-in" style={{ position: "absolute", inset: 0 }}>
        {tab === 0 ? <GroupsTab onJoin={() => setSheet("join")} /> : tab === 1 ? <FriendsTab /> : tab === 2 ? <ActivityTab /> : <AccountTab openServer={openServer} />}
      </div>
      {tab === 0 && <ExtendedFab icon="add" text="New group" onClick={() => setSheet("new")} />}
      <div class="navbar-wrap"><nav class="navbar" role="tablist">
        {tabs.map(([label, icon], i) => (
          <button key={label} class="nav-item" role="tab" aria-selected={tab === i} onClick={() => replace(TAB_ROUTES[i])}>
            <span class="nav-ind"><Icon name={icon} /></span>
            <span class="t-label-m">{label}</span>
          </button>
        ))}
      </nav></div>
      {sheet === "new" && <NewGroupSheet onDismiss={() => setSheet("")} />}
      {sheet === "join" && <JoinSheet onDismiss={() => { setSheet(""); api.pendingInvite.value = null; }} initial={api.pendingInvite.value ?? ""} />}
    </div>
  );
}

const Loading = () => <div style={{ height: 240 }}><FullScreenLoading /></div>;

// ───────────────────────────── Groups ─────────────────────────────
function GroupsTab({ onJoin }: { onJoin: () => void }) {
  const flip = useFlip(); const d = home.data.value; const failed = home.failed.value;
  return (
    <TabScroll pad={88} onRefresh={() => refresh()}>
      <div class="tab-head">
        <h1 class="t-headline-l">Groups</h1>
        <button class="join-pill press" onClick={onJoin}><Icon name="key" size={18} /><span class="t-label-l">Join</span></button>
      </div>
      {!d && failed ? <EmptyState title="Couldn't load your groups" body={failed} action={<SecondaryButton text="Try again" onClick={() => refresh()} style={{ width: 180 }} />} />
        : !d ? <Loading />
        : <>
          {d.offline && <OfflineNote />}
          <Summary d={d} />
          {d.groups.length === 0 && <EmptyState title="No groups yet" body="Start one for your next trip, flat or dinner, or join a friend's with their invite code." />}
          <div ref={flip}>{d.groups.map((g, i) => <GroupCard key={g.id} g={g} d={d} i={i} />)}</div>
        </>}
    </TabScroll>
  );
}

function Summary({ d }: { d: HomeData }) {
  const cur = d.groups[0]?.currency ?? "INR";
  const net = d.groups.filter((g) => g.currency === cur).reduce((a, g) => a + (d.myNet[g.id] ?? 0), 0);
  if (!d.groups.length) return null;
  return (
    <div style={{ padding: "12px 20px", display: "flex", alignItems: "center" }}>
      <div style={{ flex: 1 }}><BalanceHero cents={net} currency={cur} /></div>
      {d.groups.some((g) => g.currency !== cur) && <InfoTip text="Groups in other currencies are not included." />}
    </div>
  );
}

function GroupCard({ g, d, i }: { g: Group; d: HomeData; i: number }) {
  const net = d.myNet[g.id] ?? 0; const count = d.members[g.id]?.length ?? 0;
  return (
    <button class="row press" data-flip={g.id} style={{ padding: "12px 20px", ...enterIn(i) }} onClick={() => go("/g/" + g.id)}>
      <GroupTile g={g} size={56} />
      <div class="row-main" style={{ marginLeft: 16 }}>
        <div class="t-title-m ellipsis">{g.name}</div>
        <div class="t-body-s muted">{count} {count === 1 ? "person" : "people"}</div>
      </div>
      <div class="col-end" style={{ marginLeft: 8 }}>
        {net > 0 ? <><div class="t-label-s" style={{ color: "var(--owed)" }}>you're owed</div><div class="t-title-m" style={{ color: "var(--owed)" }}>{money(net, g.currency)}</div></>
          : net < 0 ? <><div class="t-label-s" style={{ color: "var(--owe)" }}>you owe</div><div class="t-title-m" style={{ color: "var(--owe)" }}>{money(net, g.currency)}</div></>
          : <div class="t-label-l muted">settled up</div>}
      </div>
    </button>
  );
}

// ───────────────────────────── Activity ─────────────────────────────
function ActivityTab() {
  const flip = useFlip(); const d = home.data.value; const me = api.me.value ?? "";
  const [query, setQuery] = useState(""); const [searching, setSearching] = useState(false);
  const q = query.trim().toLowerCase(); const recent = d?.recent ?? [];
  const groupOf = (id: string) => d?.groups.find((g) => g.id === id);
  const shown = !q ? recent : recent.filter((e) => e.description.toLowerCase().includes(q) || catOf(e.category).label.toLowerCase().includes(q) || payerNames(e, name).toLowerCase().includes(q) || (groupOf(e.group_id)?.name ?? "").toLowerCase().includes(q));
  const days = new Map<string, typeof shown>(); shown.forEach((e) => { const k = dayLabel(e.expense_date); days.set(k, [...(days.get(k) ?? []), e]); });
  return (
    <TabScroll pad={24} onRefresh={() => refresh()}>
      <div class="tab-head" style={{ paddingRight: 12 }}>
        <h1 class="t-headline-l">Activity</h1>
        {recent.length > 3 && <IconButton icon={searching ? "close" : "search"} label={searching ? "Close search" : "Search activity"} onClick={() => { setSearching(!searching); if (searching) setQuery(""); }} />}
      </div>
      {searching && <Field value={query} onChange={setQuery} label="Search activity" autoFocus leading={<Icon name="search" />} style={{ padding: "4px 20px" }} />}
      {d?.offline && <OfflineNote />}
      {!d ? <Loading />
        : recent.length === 0 ? <EmptyState title="Nothing yet" body="Expenses and payments from all your groups will show up here." />
        : shown.length === 0 ? <EmptyState title="No matches" body={`Nothing found for "${query.trim()}".`} />
        : <div ref={flip}>{[...days].map(([day, list]) => (
          <div key={day}>
            <SectionTitle text={day} />
            {list.map((e, i) => {
              const group = groupOf(e.group_id); const cur = group?.currency ?? "INR"; const net = netFor(e, me);
              const to = e.expense_splits.find((s) => s.share_cents > 0 && s.user_id !== e.paid_by)?.user_id;
              return (
                <button key={e.id} data-flip={e.id} class="row press" style={{ padding: "10px 20px", ...enterIn(i) }} onClick={() => group && go(`/g/${group.id}/e/${e.id}`)}>
                  {e.is_settlement
                    ? <div class="circle" style={{ width: 44, height: 44, background: "var(--primary-container)", color: "var(--on-primary-container)" }}><Icon name="check" /></div>
                    : <div class="circle" style={{ width: 44, height: 44, background: "var(--surface-container)" }}><Icon name={catOf(e.category).icon} size={22} /></div>}
                  <div class="row-main" style={{ marginLeft: 14 }}>
                    <div class="t-title-m ellipsis">{e.is_settlement ? `${name(e.paid_by)} paid ${name(to ?? "")}` : e.description}</div>
                    <div class="t-body-s muted ellipsis">{e.is_settlement ? `in ${group?.name ?? "a group"}, ${agoLabel(e.created_at)}` : `${payerNames(e, name)} paid, in ${group?.name ?? "a group"}`}</div>
                  </div>
                  <div class="col-end" style={{ marginLeft: 8 }}>
                    <div class="t-title-m">{money(e.amount_cents, cur)}</div>
                    {e.is_settlement && net > 0 ? <div class="t-label-l muted">you paid</div>
                      : e.is_settlement && net < 0 ? <div class="t-label-l muted">you received</div>
                      : net > 0 ? <div class="t-label-s" style={{ color: "var(--owed)" }}>you're owed {money(net, cur)}</div>
                      : net < 0 ? <div class="t-label-s" style={{ color: "var(--owe)" }}>you owe {money(net, cur)}</div>
                      : <div class="t-label-s muted">not involved</div>}
                  </div>
                </button>
              );
            })}
          </div>
        ))}</div>}
    </TabScroll>
  );
}

// ───────────────────────────── Account ─────────────────────────────
function SettingsRow({ icon, title, subtitle, danger, accent, onClick }: { icon: string; title: string; subtitle?: string; danger?: boolean; accent?: boolean; onClick?: () => void }) {
  const tint = danger ? "var(--error)" : accent ? "var(--on-primary-container)" : "var(--on-surface)";
  const bg = danger ? "var(--error-container)" : accent ? "var(--primary-container)" : "var(--surface-container)";
  const inner = <>
    <div class="circle" style={{ width: 40, height: 40, background: bg, color: tint }}><Icon name={icon} size={20} /></div>
    <div class="row-main" style={{ marginLeft: 16 }}>
      <div class="t-title-m" style={{ color: danger ? "var(--error)" : "var(--on-surface)" }}>{title}</div>
      {subtitle != null && <div class="t-body-s muted ellipsis">{subtitle}</div>}
    </div>
  </>;
  return onClick ? <button class="row press" style={{ padding: "14px 20px" }} onClick={onClick}>{inner}</button> : <div class="row" style={{ padding: "14px 20px" }}>{inner}</div>;
}
const Label = ({ children }: { children: ComponentChildren }) => <div class="t-title-s muted" style={{ padding: "16px 0 4px 20px" }}>{children}</div>;

function AccountTab({ openServer }: { openServer: () => void }) {
  const run = runner();
  const [sheet, setSheet] = useState<"" | "password" | "appearance" | "upi" | "phone" | "install">("");
  const [upi, setUpi] = useState(""); const [phone, setPhone] = useState("");
  const [menu, setMenu] = useState(false); const [crop, setCrop] = useState<File | null>(null); const file = useRef<HTMLInputElement>(null);
  useEffect(() => { api.myUpi().then(setUpi, () => {}); api.myPhone().then(setPhone, () => {}); }, []);
  const me = api.me.value ?? ""; const nm = home.data.value?.names[me] ?? "You"; const hasPhoto = !!api.photos.value[me];
  const choose = () => file.current?.click();
  const modes = ["Match phone", "Light", "Dark"];
  return (
    <TabScroll pad={24} onRefresh={() => refresh()}>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", padding: "24px 0" }}>
        <input ref={file} type="file" accept="image/*" hidden onChange={(e) => { const f = e.currentTarget.files?.[0]; e.currentTarget.value = ""; if (f) setCrop(f); }} />
        {crop && <Portal><CropDialog file={crop} onCancel={() => setCrop(null)} onDone={(img) => { setCrop(null); run(() => api.setPhoto(img)); }} /></Portal>}
        <Menu align="start" open={menu} onClose={() => setMenu(false)} trigger={
          <div style={{ position: "relative" }}>
            <button class="press" style={{ borderRadius: "50%", display: "block" }} aria-label="Change photo" onClick={() => (hasPhoto ? setMenu(true) : choose())}><Avatar name={nm} id={me} size={92} /></button>
            <div class="badge-cam" style={{ pointerEvents: "none" }}><Icon name="photo_camera" size={18} /></div>
          </div>}>
          <button class="menu-item t-body-l" role="menuitem" onClick={() => { setMenu(false); choose(); }}>Choose a photo</button>
          <button class="menu-item t-body-l" role="menuitem" style={{ color: "var(--error)" }} onClick={() => { setMenu(false); run(() => api.setPhoto(null)); }}>Remove photo</button>
        </Menu>
        <div style={{ height: 16 }} />
        <div class="t-headline-m">{nm}</div>
        <div class="t-body-m muted">{api.myEmail.value}</div>
      </div>
      <Divider />
      <Label>Preferences</Label>
      <SettingsRow icon="palette" title="Appearance" subtitle={modes[api.themeMode.value]} onClick={() => setSheet("appearance")} />
      <SettingsRow icon="payments" title="UPI ID" subtitle={upi || "Add yours so friends can pay you"} accent onClick={() => setSheet("upi")} />
      <SettingsRow icon="call" title="Mobile number" subtitle={phone || "Add it so friends can pay you by number"} onClick={() => setSheet("phone")} />
      <SettingsRow icon="lock" title="Change password" onClick={() => setSheet("password")} />
      {!standalone() && <SettingsRow icon="download" title="Install the app" subtitle={isIos ? "Add Hissa to your Home Screen" : "Add Hissa to your home screen"} accent onClick={async () => {
        if (installEvt) { installEvt.prompt(); await installEvt.userChoice.catch(() => {}); installEvt = null; } else setSheet("install");
      }} />}
      <Label>About</Label>
      {!api.isBaked() && <SettingsRow icon="cloud" title="Server" subtitle={api.serverUrl.value.replace(/^https:\/\//, "")} onClick={openServer} />}
      <SettingsRow icon="info" title={`Hissa${VERSION ? " " + VERSION : ""}`} subtitle="Free and open source" />
      <Divider />
      <SettingsRow icon="logout" title="Sign out" onClick={() => run(() => api.signOut())} />
      <Portal>{sheet === "password" && <PasswordSheet onDismiss={() => setSheet("")} />}
      {sheet === "upi" && <UpiSheet onDismiss={() => setSheet("")} current={upi} onSaved={setUpi} />}
      {sheet === "phone" && <PhoneSheet onDismiss={() => setSheet("")} current={phone} onSaved={setPhone} />}
      {sheet === "install" && <AppSheet onDismiss={() => setSheet("")} title="Install Hissa">
        <div class="t-body-l">{isIos ? "On iPhone and iPad, use Safari:" : "In your browser menu, choose:"}</div>
        <div style={{ height: 12 }} />
        {(isIos ? ["Tap the Share button at the bottom of Safari", "Scroll down and tap Add to Home Screen", "Tap Add. Hissa opens full screen like any app"] : ["Open the browser menu (three dots)", "Tap Install app or Add to Home screen", "Confirm. Hissa opens full screen like any app"]).map((s, i) => <div key={i} class="t-body-m" style={{ padding: "6px 0" }}>{i + 1}. {s}</div>)}
        <div style={{ height: 8 }} />
      </AppSheet>}
      {sheet === "appearance" && <AppSheet onDismiss={() => setSheet("")} title="Appearance"><Segmented options={modes} selected={api.themeMode.value} onSelect={(i) => api.applyTheme(i)} /><div style={{ height: 8 }} /></AppSheet>}</Portal>
    </TabScroll>
  );
}

// ───────────────────────────── Sheets ─────────────────────────────
const CODES = ["INR", "USD", "EUR", "GBP"];
function NewGroupSheet({ onDismiss }: { onDismiss: () => void }) {
  const [nm, setNm] = useState(""); const [cur, setCur] = useState(0); const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  return (
    <AppSheet onDismiss={onDismiss} title="New group">
      <Field value={nm} onChange={(v) => { setNm(v.slice(0, 80)); setErr(null); }} label="Group name, like Goa trip" error={Check.groupName(nm)} />
      <div style={{ height: 16 }} />
      <div class="t-title-s muted">Currency</div><div style={{ height: 8 }} />
      <Segmented options={CODES.map((c) => `${symbol(c).trim()} ${c}`)} selected={cur} onSelect={setCur} />
      <ErrorText msg={err} /><div style={{ height: 24 }} />
      <PrimaryButton text="Create group" enabled={nm.trim().length >= 2} loading={busy} onClick={() => run(async () => { await api.createGroup(nm.trim(), CODES[cur]); await refresh(); onDismiss(); })} />
    </AppSheet>
  );
}

function JoinSheet({ onDismiss, initial }: { onDismiss: () => void; initial: string }) {
  const [code, setCode] = useState(initial); const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  const paste = async () => { try { const t = await navigator.clipboard.readText(); setCode(extractInviteCode(t)); setErr(null); } catch { setErr("Couldn't read the clipboard. Paste the code into the box."); } };
  return (
    <AppSheet onDismiss={onDismiss} title="Join with a code">
      {initial ? <div class="t-body-m muted">You were invited to a group. Join it?</div>
        : <div style={{ display: "flex", alignItems: "center" }}><span class="t-title-s muted">Invite code</span><InfoTip text="Paste the code or the whole invite message. You can also just open a friend's invite link." /></div>}
      <div style={{ height: 16 }} />
      {/* Pasting a whole invite message (link, "Code: ..." text) is fine: the code is picked out of it. */}
      <Field value={code} onChange={(v) => { setCode(extractInviteCode(v)); setErr(null); }} label="Invite code" centered />
      <ErrorText msg={err} /><div style={{ height: 12 }} />
      <SecondaryButton text="Paste from clipboard" onClick={paste} /><div style={{ height: 12 }} />
      <PrimaryButton text="Join group" enabled={code.length >= 6} loading={busy} onClick={() => run(async () => { await api.joinGroup(code); await refresh(); onDismiss(); })} />
    </AppSheet>
  );
}

function PasswordSheet({ onDismiss }: { onDismiss: () => void }) {
  const [p1, setP1] = useState(""); const [p2, setP2] = useState(""); const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  return (
    <AppSheet onDismiss={onDismiss} title="Change password">
      <PasswordField value={p1} onChange={(v) => setP1(v.slice(0, 72))} label="New password (8+)" error={Check.password(p1)} />
      <div style={{ height: 12 }} />
      <PasswordField value={p2} onChange={setP2} label="Repeat new password" />
      {p2 !== "" && p1 !== p2 && <div class="t-body-s" style={{ color: "var(--error)", paddingTop: 8 }}>Passwords don't match.</div>}
      <ErrorText msg={err} /><div style={{ height: 24 }} />
      <PrimaryButton text="Update password" enabled={p1 !== "" && !Check.password(p1) && p1 === p2} loading={busy} onClick={() => run(async () => { await api.changePassword(p1); onDismiss(); say("Password updated"); })} />
    </AppSheet>
  );
}

export function OfflineNote() {
  return (
    <div class="offline">
      <Icon name="cloud_off" size={20} />
      <div class="t-body-m" style={{ flex: 1 }}>Offline. Showing saved data.</div>
      <InfoTip text="Changes need a connection. What you see was saved on this phone." />
    </div>
  );
}

function PhoneSheet({ onDismiss, current, onSaved }: { onDismiss: () => void; current: string; onSaved: (v: string) => void }) {
  const [v, setV] = useState(current); const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  return (
    <AppSheet onDismiss={onDismiss} title="Mobile number">
      <div style={{ display: "flex", alignItems: "center" }}><span class="t-title-s muted">Your mobile number</span><InfoTip text="People in your groups can see this number, so they can pay you by number in any UPI app. Leave empty to remove it." /></div>
      <div style={{ height: 16 }} />
      <Field value={v} onChange={(x) => { setV(x.replace(/\D/g, "").slice(0, 10)); setErr(null); }} label="10-digit mobile number" inputMode="tel" />
      <ErrorText msg={err} /><div style={{ height: 24 }} />
      <PrimaryButton text="Save" enabled={v === "" || v.length === 10} loading={busy} onClick={() => run(async () => { await api.setPhone(v); onSaved(v); onDismiss(); })} />
    </AppSheet>
  );
}

function UpiSheet({ onDismiss, current, onSaved }: { onDismiss: () => void; current: string; onSaved: (v: string) => void }) {
  const [v, setV] = useState(current); const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const run = runner({ busy: setBusy, onError: setErr });
  const ok = v === "" || /^[\w.-]{2,}@[A-Za-z][\w.-]{1,}$/.test(v);
  return (
    <AppSheet onDismiss={onDismiss} title="UPI ID">
      <div style={{ display: "flex", alignItems: "center" }}><span class="t-title-s muted">Your UPI ID</span><InfoTip text="Friends who owe you can pay from a UPI app when they settle up. Leave empty to remove it." /></div>
      <div style={{ height: 16 }} />
      <Field value={v} onChange={(x) => { setV(x.trim().slice(0, 60)); setErr(null); }} label="UPI ID (for example name@bank)" />
      <ErrorText msg={err} /><div style={{ height: 24 }} />
      <PrimaryButton text="Save" enabled={ok} loading={busy} onClick={() => run(async () => { await api.setUpi(v); onSaved(v); onDismiss(); })} />
    </AppSheet>
  );
}
