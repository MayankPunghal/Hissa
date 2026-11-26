// Port of Contacts.kt: invite message, SMS / WhatsApp invites and the "Add people" sheet.
// Android's own contact picker has no equivalent on iPhone/Safari, so the Contact Picker API is used when the browser has it
// and otherwise the person types a name and number.
import { useState } from "preact/hooks";
import { inviteLink } from "../lib/api";
import type { Group } from "../lib/types";
import { AppSheet, ErrorText, Field, PrimaryButton, SecondaryButton, say } from "../ui/ui";

/** The message people receive: what the group is and how to get in. */
export const inviteMessage = (g: Group) =>
  `Join "${g.name}" on Hissa and split bills with me.\n\nTap to join:\n${inviteLink(g.invite_code)}\n\nInvite code:\n${g.invite_code}`;

/** Digits only, with India's +91 added when the number has no country code. Used for WhatsApp links. */
export function whatsappNumber(raw: string): string {
  const d = raw.replace(/\D/g, "");
  return d.length === 10 ? "91" + d : d.replace(/^0+/, "");
}

export function inviteBySms(number: string, g: Group) {
  const sep = /iPhone|iPad|iPod|Macintosh/i.test(navigator.userAgent) ? "&" : "?"; // iOS wants &body=, Android ?body=
  location.href = `sms:${encodeURIComponent(number)}${sep}body=${encodeURIComponent(inviteMessage(g))}`;
}
export function inviteByWhatsApp(number: string, g: Group) {
  window.open(`https://wa.me/${whatsappNumber(number)}?text=${encodeURIComponent(inviteMessage(g))}`, "_blank", "noopener");
}
/** Share sheet when the browser has one, else copy the message. */
export async function shareInvite(g: Group) {
  const text = inviteMessage(g);
  try {
    if (navigator.share) { await navigator.share({ text }); return; }
    await navigator.clipboard.writeText(text); say("Copied");
  } catch (e: any) { if (e?.name !== "AbortError") say("Couldn't share. Copy the code instead."); }
}

const canPickContact = () => "contacts" in navigator && "ContactsManager" in window;

/** "Add people": pick a group, then either choose someone from contacts or share the link. They are separate buttons. */
export function AddPeopleSheet({ groups, initial, onDismiss }: { groups: Group[]; initial: Group | null; onDismiss: () => void }) {
  const [sel, setSel] = useState<Group | null>(initial ?? groups[0] ?? null);
  const [picked, setPicked] = useState<[string, string] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [typedName, setTypedName] = useState(""); const [typedNum, setTypedNum] = useState("");
  const g = sel;

  async function pick() {
    if (!canPickContact()) { setPicked(null); return; }
    try {
      const r = await (navigator as any).contacts.select(["name", "tel"], { multiple: false });
      if (r?.length) { setPicked([r[0].name?.[0] ?? "", r[0].tel?.[0] ?? ""]); setErr(null); }
    } catch { setErr("Couldn't read that contact."); }
  }

  return (
    <AppSheet onDismiss={onDismiss} title="Add people">
      {!g ? <div class="t-body-l muted">Create a group first, then invite friends to it.</div> : <>
        {groups.length > 1 && <>
          <div class="t-title-s muted">Add to</div>
          <div style={{ height: 8 }} />
          <div style={{ display: "flex", gap: 8, overflowX: "auto", scrollbarWidth: "none" }}>
            {groups.map((x) => { const on = x.id === g.id; return (
              <button key={x.id} class="press t-label-l ellipsis" onClick={() => { setSel(x); setPicked(null); }}
                style={{ flex: "none", borderRadius: 999, padding: "10px 16px", whiteSpace: "nowrap", background: on ? "var(--primary)" : "var(--surface-container)", color: on ? "var(--on-primary)" : "var(--on-surface)" }}>{x.name}</button>
            ); })}
          </div>
          <div style={{ height: 20 }} />
        </>}

        {picked == null ? (canPickContact()
          ? <PrimaryButton text="Choose from contacts" onClick={pick} />
          : <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <Field value={typedName} onChange={(v) => setTypedName(v.slice(0, 60))} label="Name (optional)" autoComplete="off" />
              <Field value={typedNum} onChange={(v) => setTypedNum(v.replace(/[^\d+\s-]/g, "").slice(0, 20))} label="Mobile number" type="tel" inputMode="tel" autoComplete="off" />
              <PrimaryButton text="Continue" enabled={typedNum.replace(/\D/g, "").length >= 7} onClick={() => { setPicked([typedName.trim(), typedNum.trim()]); setErr(null); }} />
            </div>)
        : <>
          <div class="t-title-m">{picked[0] || picked[1]}</div>
          {picked[0] && <div class="t-body-m muted">{picked[1]}</div>}
          <div style={{ height: 12 }} />
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><PrimaryButton text="Message" onClick={() => { inviteBySms(picked[1], g); onDismiss(); }} /></div>
            <div style={{ flex: 1 }}><SecondaryButton text="WhatsApp" onClick={() => { inviteByWhatsApp(picked[1], g); onDismiss(); }} /></div>
          </div>
          <div style={{ height: 8 }} />
          <SecondaryButton text="Choose someone else" onClick={() => (canPickContact() ? pick() : setPicked(null))} />
        </>}

        <ErrorText msg={err} />
        <div style={{ height: 12 }} />
        <SecondaryButton text="Share invite link" onClick={() => { shareInvite(g); onDismiss(); }} />
      </>}
    </AppSheet>
  );
}
