// Port of GroupSettings.kt (the screen; the Import.kt picker is never launched in the Android UI either, see lib/import.ts).
import { useEffect, useRef, useState } from "preact/hooks";
import * as api from "../lib/api";
import { Check, dayLabel, money } from "../lib/format";
import { fullName, refresh } from "../lib/model";
import type { Group, RecurringRow } from "../lib/types";
import { AddPeopleSheet } from "./AddPeople";
import { CropDialog } from "../ui/Crop";
import { Avatar, ErrorText, Field, GroupTile, Icon, IconButton, InfoTip, Divider, runner, say } from "../ui/ui";
import "../group.css";

function Switch({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) {
  return <button class="switch" role="switch" aria-checked={on} aria-label="Simplify group debts" onClick={() => onChange(!on)}><i /></button>;
}
const SettingsTitle = ({ text }: { text: string }) => <div class="t-title-s muted s-title">{text}</div>;

export function GroupSettings({ g, members, net, onClose, onGone }: { g: Group; members: string[]; net: Record<string, number>; onClose: () => void; onGone: () => void }) {
  const me = api.me.value ?? "";
  const [name, setName] = useState(g.name);
  const [image, setImage] = useState<string | null>(g.image ?? null);
  const [busy, setBusy] = useState(false); const [err, setErr] = useState<string | null>(null);
  const [sureLeave, setSureLeave] = useState(false); const [sureDelete, setSureDelete] = useState(false);
  const [addPeople, setAddPeople] = useState(false);
  const [crop, setCrop] = useState<File | null>(null);
  const [rec, setRec] = useState<RecurringRow[]>([]);
  const [simplify, setSimplify] = useState(api.simplifyDebts(g.id));
  const run = runner({ busy: setBusy, onError: setErr });
  const file = useRef<HTMLInputElement>(null);
  useEffect(() => { api.recurring(g.id).then(setRec).catch(() => setRec([])); }, [g.id]);
  const dirty = name.trim() !== g.name || image !== (g.image ?? null);
  const canSave = dirty && name.trim().length >= 2 && !busy;
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && !crop && !addPeople && onClose(); addEventListener("keydown", k); return () => removeEventListener("keydown", k); }, [crop, addPeople]);

  return (
    <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
      <div class="g-row" style={{ padding: "4px 8px" }}>
        <IconButton icon="close" label="Close" onClick={onClose} />
        <div class="t-title-m" style={{ flex: 1, textAlign: "center" }}>Group settings</div>
        <button class="text-btn t-title-s press" disabled={!canSave} style={{ color: canSave ? "var(--primary)" : "color-mix(in srgb, var(--on-surface) 38%, transparent)" }}
          onClick={() => run(async () => { await api.updateGroup(g.id, name, image); await refresh(); onClose(); })}>Save</button>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: "0 20px calc(32px + var(--safe-bottom))" }}>
        <div style={{ height: 12 }} />
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
          <button class="press s-pic" aria-label="Change group picture" onClick={() => file.current?.click()}>
            <GroupTile g={g} size={112} image={image} />
            <span class="s-badge"><Icon name="photo_camera" size={18} /></span>
          </button>
          <input ref={file} type="file" accept="image/*" hidden onChange={(e) => { const f = e.currentTarget.files?.[0]; e.currentTarget.value = ""; if (f) setCrop(f); }} />
          {image && <button class="text-btn t-label-l press" style={{ color: "var(--on-surface-variant)" }} onClick={() => setImage(null)}>Remove picture</button>}
        </div>
        <div style={{ height: 12 }} />
        <Field value={name} onChange={(v) => { setName(v.slice(0, 80)); setErr(null); }} label="Group name" error={Check.groupName(name)} />

        <SettingsTitle text="Group members" />
        <button class="press s-action" onClick={() => setAddPeople(true)}>
          <div class="g-icon-circle" style={{ background: "var(--primary-container)" }}><Icon name="person_add" size={22} style={{ color: "var(--on-primary-container)" }} /></div>
          <div style={{ width: 14 }} />
          <div class="g-grow"><div class="t-title-m">Invite people</div><div class="t-body-s muted ellipsis">From contacts or with a link</div></div>
        </button>
        {addPeople && <AddPeopleSheet groups={[g]} initial={g} onDismiss={() => setAddPeople(false)} />}
        {members.map((m) => {
          const v = net[m] ?? 0;
          return (
            <div key={m} class="g-row" style={{ padding: "8px 0" }}>
              <Avatar name={fullName(m)} id={m} size={44} /><div style={{ width: 14 }} />
              <div class="g-grow">
                <div class="t-title-m">{fullName(m) + (m === me ? " (you)" : "")}</div>
                {m === g.created_by && <div class="t-body-s muted">Group creator</div>}
              </div>
              {v > 0 ? <div class="g-right"><div class="t-label-s" style={{ color: "var(--owed)" }}>{m === me ? "get back" : "gets back"}</div><div class="t-title-s" style={{ color: "var(--owed)" }}>{money(v, g.currency)}</div></div>
                : v < 0 ? <div class="g-right"><div class="t-label-s" style={{ color: "var(--owe)" }}>{m === me ? "owe" : "owes"}</div><div class="t-title-s" style={{ color: "var(--owe)" }}>{money(v, g.currency)}</div></div>
                : <div class="t-label-l muted">settled up</div>}
            </div>
          );
        })}

        <SettingsTitle text="Settings" />
        <div class="g-row" style={{ padding: "8px 0" }}>
          <div class="g-grow" style={{ paddingRight: 12 }}>
            <div class="g-row"><span class="t-title-m">Simplify group debts</span><InfoTip text="Combines debts so fewer payments clear everyone. If you owe Asha and Asha owes Ravi, you pay Ravi directly. Kept on this phone." /></div>
            <div class="t-body-s muted">Fewer payments to settle up</div>
          </div>
          <Switch on={simplify} onChange={(v) => { setSimplify(v); api.setSimplifyDebts(g.id, v); }} />
        </div>

        {rec.length > 0 && <SettingsTitle text="Repeating expenses" />}
        {rec.map((r) => (
          <div key={r.id} class="g-row" style={{ padding: "6px 0" }}>
            <div class="g-grow">
              <div class="t-title-m">{r.description}</div>
              <div class="t-body-s muted">{`${money(r.amount_cents, g.currency)} every ${r.every}, next ${dayLabel(r.next_date)}`}</div>
            </div>
            <button class="text-btn t-label-l press" style={{ color: "var(--error)" }} onClick={() => run(async () => { await api.deleteRecurring(r.id); setRec(rec.filter((x) => x.id !== r.id)); say("Stopped repeating"); })}>Stop</button>
          </div>
        ))}

        <ErrorText msg={err} />
        <div style={{ height: 16 }} /><Divider /><div style={{ height: 8 }} />
        <button class="press s-danger" style={{ color: "var(--on-surface-variant)" }} onClick={() => { if (!sureLeave) setSureLeave(true); else run(async () => { await api.leaveGroup(g.id); await refresh(); say(`You left ${g.name}`); onGone(); }); }}>
          <Icon name="exit_to_app" size={22} /><span class="t-title-m">{sureLeave ? "Tap again to leave" : "Leave group"}</span>
        </button>
        {g.created_by === me && (
          <button class="press s-danger" style={{ color: "var(--error)" }} onClick={() => { if (!sureDelete) setSureDelete(true); else run(async () => { await api.deleteGroup(g.id); await refresh(); say("Group deleted"); onGone(); }); }}>
            <Icon name="delete" size={22} /><span class="t-title-m">{sureDelete ? "Tap again to delete for everyone" : "Delete group"}</span>
          </button>
        )}
      </div>
      {crop && <CropDialog file={crop} onCancel={() => setCrop(null)} onDone={(d) => { setImage(d); setErr(null); setCrop(null); }} />}
    </div>
  );
}
