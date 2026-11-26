// Shared UI primitives. Each one mirrors a composable in app/.../Components.kt: same sizes, colours, radii, motion.
// dp = px. Colours come from theme.css variables (copied from Theme.kt). Do not hardcode colours in screens.
import { ComponentChildren, JSX } from "preact";
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import { signal } from "@preact/signals";
import { ICONS } from "../icons";
import { photos } from "../lib/api";
import { avatarColor, friendly, initials, money } from "../lib/format";
import "./ui.css";

export const cx = (...a: (string | false | null | undefined)[]) => a.filter(Boolean).join(" ");

// ───────── Icon ─────────
/** name = a Material Symbols (Rounded, filled) name listed in src/icon-names.txt. Run `npm run icons` after adding one. */
export function Icon({ name, size = 24, style, class: c }: { name: string; size?: number; style?: JSX.CSSProperties; class?: string }) {
  return <span class={cx("icon", c)} style={{ width: size, height: size, ...style }} aria-hidden="true" dangerouslySetInnerHTML={{ __html: ICONS[name] ?? "" }} />;
}

export function LogoMark({ size }: { size: number }) {
  return <img src="logo.svg" alt="Hissa" width={size} height={size} style={{ width: size, height: size, borderRadius: size * 0.24, display: "block" }} />;
}

// ───────── Snackbar (Components.kt LocalSnack / say) ─────────
const snack = signal<{ msg: string; id: number } | null>(null);
let snackTimer: number | undefined;
export function say(msg: string) {
  snack.value = { msg, id: Date.now() };
  clearTimeout(snackTimer);
  snackTimer = window.setTimeout(() => (snack.value = null), 4000);
}
export function SnackbarHost() {
  const s = snack.value;
  return <div class="snack-host" aria-live="polite">{s && <div class="snack" key={s.id}>{s.msg}</div>}</div>;
}

/** rememberRunner: runs fn, turns failures into a snackbar (or onError for sheets, which sit above it), and flips busy while it runs. */
export function runner(opts: { busy?: (b: boolean) => void; onError?: (m: string | null) => void } = {}) {
  return async (fn: () => Promise<unknown>) => {
    opts.busy?.(true); opts.onError?.(null);
    try { await fn(); } catch (e) { if (opts.onError) opts.onError(friendly(e)); else say(friendly(e)); } finally { opts.busy?.(false); }
  };
}

export function ErrorText({ msg }: { msg: string | null | undefined }) {
  return msg ? <div class="t-body-m error-text" role="status">{msg}</div> : null;
}

// ───────── Avatars ─────────
export function Avatar({ name, id, size = 40, style }: { name: string; id: string; size?: number; style?: JSX.CSSProperties }) {
  const photo = photos.value[id];
  if (photo) return <img class="avatar" src={photo} alt="" style={{ width: size, height: size, ...style }} />;
  return <div class="avatar" style={{ width: size, height: size, background: avatarColor(id), fontSize: size * 0.38, ...style }}>{initials(name)}</div>;
}
export function AvatarStack({ people, size = 28, max = 4 }: { people: [string, string][]; size?: number; max?: number }) {
  const shown = people.slice(0, max); const ring = { border: "2px solid var(--surface-low)", boxSizing: "border-box" as const };
  return (
    <div class="avatar-stack">
      {shown.map(([name, id], i) => <Avatar key={id} name={name} id={id} size={size} style={{ ...ring, marginLeft: i ? -size * 0.22 : 0 }} />)}
      {people.length > max && <div class="avatar t-label-s muted" style={{ width: size, height: size, background: "var(--surface-highest)", marginLeft: -size * 0.22, ...ring }}>+{people.length - max}</div>}
    </div>
  );
}
export function SelectableAvatar({ name, id, selected, label, onClick }: { name: string; id: string; selected: boolean; label?: string; onClick: () => void }) {
  return (
    <button class="press sel-avatar" role="radio" aria-checked={selected} onClick={onClick}>
      <div class="sel-ring" style={{ borderColor: selected ? "var(--primary)" : "transparent", borderWidth: selected ? 2.5 : 0 }}><Avatar name={name} id={id} size={46} /></div>
      <div class="t-label-m ellipsis" style={{ color: selected ? "var(--on-surface)" : "var(--on-surface-variant)", maxWidth: 64 }}>{label ?? name.trim().split(" ")[0]}</div>
    </button>
  );
}

// ───────── Count-up number (BalanceHero animates 650ms FastOutSlowIn from 0, then to each new value) ─────────
export function useCountUp(target: number, ms = 650) {
  const [v, setV] = useState(0); const from = useRef(0);
  useEffect(() => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { from.current = target; setV(target); return; }
    const start = performance.now(), a = from.current; let raf = 0;
    const ease = (t: number) => { // cubic-bezier(.4,0,.2,1)
      let lo = 0, hi = 1, x = t; for (let i = 0; i < 20; i++) { const m = (lo + hi) / 2; const bx = 3 * (1 - m) * (1 - m) * m * 0.4 + 3 * (1 - m) * m * m * 0.2 + m * m * m; if (bx < x) lo = m; else hi = m; }
      const m = (lo + hi) / 2; return 3 * (1 - m) * (1 - m) * m * 0 + 3 * (1 - m) * m * m * 1 + m * m * m;
    };
    const tick = (now: number) => { const t = Math.min(1, (now - start) / ms); const val = t >= 1 ? target : a + (target - a) * ease(t); from.current = val; setV(val); if (t < 1) raf = requestAnimationFrame(tick); };
    raf = requestAnimationFrame(tick); return () => cancelAnimationFrame(raf);
  }, [target]);
  return Math.trunc(v);
}

/** The headline number: a tinted card, green when owed, red when owing, neutral when settled. */
export function BalanceHero({ cents, currency, compact = false, style }: { cents: number; currency: string; compact?: boolean; style?: JSX.CSSProperties }) {
  const [bg, fg, label] = cents > 0 ? ["var(--owed-bg)", "var(--owed)", "You're owed"] : cents < 0 ? ["var(--owe-bg)", "var(--owe)", "You owe"] : ["var(--surface-container)", "var(--on-surface-variant)", "All settled up"];
  const shown = useCountUp(cents);
  const edge = `1px solid color-mix(in srgb, ${fg} 16%, transparent)`;
  if (compact) return (
    <div class="hero-compact" style={{ background: bg, border: edge, color: fg, ...style }}>
      <div class="t-title-m" style={{ flex: 1 }}>{label}</div>
      {cents !== 0 && <div class="t-headline-m">{money(shown, currency)}</div>}
    </div>
  );
  return (
    <div class="hero" style={{ background: bg, border: edge, color: fg, ...style }}>
      <div class="t-label-l">{label}</div>
      {cents !== 0 && <div class="t-display-s">{money(shown, currency)}</div>}
    </div>
  );
}

// ───────── Buttons ─────────
export function Spinner({ size = 22, stroke = 2.5, color = "var(--primary)" }: { size?: number; stroke?: number; color?: string }) {
  return <svg class="spinner" width={size} height={size} viewBox="0 0 48 48" role="progressbar"><circle cx="24" cy="24" r={24 - stroke * (48 / size) / 2} fill="none" stroke={color} stroke-width={stroke * (48 / size)} stroke-linecap="round" /></svg>;
}
export function PrimaryButton({ text, onClick, enabled = true, loading = false, style }: { text: string; onClick: () => void; enabled?: boolean; loading?: boolean; style?: JSX.CSSProperties }) {
  const on = enabled && !loading;
  return (
    <button class={cx("btn press", enabled ? "btn-primary" : "btn-disabled")} disabled={!on} onClick={onClick} style={style}>
      {loading ? <Spinner color="var(--inverse-on-surface)" /> : <span class="t-title-m">{text}</span>}
    </button>
  );
}
export function SecondaryButton({ text, onClick, danger, muted, style }: { text: string; onClick: () => void; danger?: boolean; muted?: boolean; style?: JSX.CSSProperties }) {
  return (
    <button class={cx("btn press", danger ? "btn-danger" : "btn-secondary")} onClick={onClick} style={style}>
      <span class="t-title-m" style={{ color: danger ? "var(--error)" : muted ? "var(--on-surface-variant)" : "var(--on-surface)" }}>{text}</span>
    </button>
  );
}
/** Plain text button (M3 TextButton) */
export function TextButton({ text, onClick, color = "var(--primary)", disabled, style }: { text: string; onClick: () => void; color?: string; disabled?: boolean; style?: JSX.CSSProperties }) {
  return <button class="text-btn t-label-l press" disabled={disabled} onClick={onClick} style={{ color, opacity: disabled ? 0.38 : 1, ...style }}>{text}</button>;
}
export function IconButton({ icon, label, onClick, size = 24, color, style }: { icon: string; label: string; onClick: () => void; size?: number; color?: string; style?: JSX.CSSProperties }) {
  return <button class="icon-btn" aria-label={label} onClick={onClick} style={{ color, ...style }}><Icon name={icon} size={size} /></button>;
}
export function ExtendedFab({ icon, text, onClick }: { icon: string; text: string; onClick: () => void }) {
  return <button class="fab press" onClick={onClick}><Icon name={icon} /><span class="t-title-s">{text}</span></button>;
}

// ───────── Text field (HissaField = OutlinedTextField) ─────────
export function Field({ value, onChange, label, type = "text", inputMode, multiline, error, trailing, leading, centered, autoFocus, autoComplete, maxLength, onEnter, style }: {
  value: string; onChange: (v: string) => void; label: string; type?: string; inputMode?: JSX.HTMLAttributes["inputMode"]; multiline?: boolean; error?: string | null;
  trailing?: ComponentChildren; leading?: ComponentChildren; centered?: boolean; autoFocus?: boolean; autoComplete?: string; maxLength?: number; onEnter?: () => void; style?: JSX.CSSProperties;
}) {
  const [focus, setFocus] = useState(false);
  const floated = focus || value.length > 0;
  const common = {
    value, class: "field-input t-body-l", style: { textAlign: centered ? "center" : "start" } as JSX.CSSProperties, autoFocus, autoComplete, maxLength, autocapitalize: "off" as const, spellcheck: false,
    onInput: (e: any) => onChange(e.currentTarget.value), onFocus: () => setFocus(true), onBlur: () => setFocus(false),
  };
  return (
    <div style={style}>
      <label class={cx("field", focus && "focus", error && "err", floated && "floated", !!leading && "has-lead")}>
        {leading && <span class="field-lead">{leading}</span>}
        {multiline ? <textarea {...common} rows={3} /> : <input {...common} type={type} inputMode={inputMode} onKeyDown={(e) => e.key === "Enter" && onEnter?.()} />}
        <span class="field-label">{label}</span>
        {trailing && <span class="field-trail">{trailing}</span>}
      </label>
      {error && <div class="t-body-s field-support">{error}</div>}
    </div>
  );
}
export function PasswordField(props: Omit<Parameters<typeof Field>[0], "type" | "trailing">) {
  const [show, setShow] = useState(false);
  return <Field {...props} type={show ? "text" : "password"} trailing={<IconButton icon={show ? "visibility_off" : "visibility"} label={show ? "Hide password" : "Show password"} onClick={() => setShow(!show)} color="var(--on-surface-variant)" />} />;
}

// ───────── Segmented (sliding pill) ─────────
export function Segmented({ options, selected, onSelect }: { options: string[]; selected: number; onSelect: (i: number) => void }) {
  return (
    <div class="seg" role="tablist">
      <div class="seg-pill" style={{ width: `calc((100% - 8px) / ${options.length})`, transform: `translateX(${selected * 100}%)` }} />
      {options.map((o, i) => (
        <button key={o} role="tab" aria-selected={i === selected} class="seg-opt press t-label-l" style={{ width: `${100 / options.length}%`, color: i === selected ? "var(--on-surface)" : "var(--on-surface-variant)" }} onClick={() => onSelect(i)}>{o}</button>
      ))}
    </div>
  );
}

// ───────── small pieces ─────────
export const SectionTitle = ({ text, style }: { text: string; style?: JSX.CSSProperties }) => <div class="section-title t-title-s muted" style={style}>{text}</div>;
export const Divider = ({ style }: { style?: JSX.CSSProperties }) => <div class="divider" style={style} />;
export function StatusPill({ text, fg, bg }: { text: string; fg: string; bg: string }) { return <span class="pill t-label-m" style={{ color: fg, background: bg }}>{text}</span>; }
export function FullScreenLoading() { return <div class="center-fill"><Spinner size={48} stroke={4} /></div>; }
export function EmptyState({ title, body, action }: { title: string; body: string; action?: ComponentChildren }) {
  return (
    <div class="empty">
      <LogoMark size={64} />
      <div class="t-title-l" style={{ marginTop: 20, textAlign: "center" }}>{title}</div>
      <div class="t-body-m muted" style={{ marginTop: 6, textAlign: "center" }}>{body}</div>
      {action && <div style={{ marginTop: 20 }}>{action}</div>}
    </div>
  );
}
/** (i) button that shows a one-line explanation on tap */
export function InfoTip({ text }: { text: string }) {
  const [open, setOpen] = useState(false); const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => { if (!open) return; const f = (e: Event) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); }; document.addEventListener("pointerdown", f); return () => document.removeEventListener("pointerdown", f); }, [open]);
  return (
    <span class="infotip" ref={ref}>
      <button class="icon-btn" style={{ width: 44, height: 44 }} aria-label={`More info: ${text}`} onClick={() => setOpen(!open)}><Icon name="info@outlined" size={18} style={{ color: "var(--on-surface-variant)" }} /></button>
      {open && <span class="tooltip t-body-s" onClick={() => setOpen(false)}>{text}</span>}
    </span>
  );
}

/** Fade and rise on first appearance, staggered by position (Components.kt enterIn). Only the first 10 rows animate. */
export const enterIn = (index: number): JSX.CSSProperties | undefined => (index > 9 || index < 0 ? undefined : { animation: "enter 380ms cubic-bezier(.4,0,.2,1) both", animationDelay: `${index * 40}ms` });

/** List items glide to their new place instead of jumping (Compose's animateItem). Put the ref on the list's wrapper and data-flip="id" on each row. */
export function useFlip() {
  const ref = useRef<HTMLDivElement>(null); const prev = useRef(new Map<string, number>());
  useLayoutEffect(() => {
    const el = ref.current; if (!el) return;
    const top = el.getBoundingClientRect().top; const next = new Map<string, number>();
    const still = matchMedia("(prefers-reduced-motion: reduce)").matches;
    el.querySelectorAll<HTMLElement>("[data-flip]").forEach((c) => {
      const k = c.dataset.flip!; const y = c.getBoundingClientRect().top - top; next.set(k, y);
      const was = prev.current.get(k);
      if (!still && was != null && Math.abs(was - y) > 1) c.animate([{ transform: `translateY(${was - y}px)` }, { transform: "none" }], { duration: 320, easing: "cubic-bezier(.2,0,0,1)" });
    });
    prev.current = next;
  });
  return ref;
}

// ───────── Bottom sheet (AppSheet = ModalBottomSheet) ─────────
export function AppSheet({ onDismiss, title, children }: { onDismiss: () => void; title: string; children: ComponentChildren }) {
  const [closing, setClosing] = useState(false);
  const close = () => { setClosing(true); setTimeout(onDismiss, 180); };
  const sheet = useRef<HTMLDivElement>(null); const scrim = useRef<HTMLDivElement>(null);
  // Drag down to dismiss: the sheet follows the finger, a quick flick or a long drag closes it, otherwise it springs back.
  const drag = useRef<{ y: number; t: number; dy: number } | null>(null);
  const down = (e: PointerEvent) => { const s = sheet.current!; if (s.scrollTop > 0) return; drag.current = { y: e.clientY, t: e.timeStamp, dy: 0 }; s.style.transition = "none"; };
  const move = (e: PointerEvent) => {
    const d = drag.current; if (!d) return; const s = sheet.current!;
    d.dy = e.clientY - d.y; if (d.dy < 0) d.dy = 0; else try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); } catch { /* synthetic pointer */ }
    s.style.transform = `translateY(${d.dy}px)`; if (scrim.current) scrim.current.style.opacity = String(Math.max(0, 1 - d.dy / s.offsetHeight));
  };
  const up = (e: PointerEvent) => {
    const d = drag.current; drag.current = null; if (!d) return; const s = sheet.current!;
    const v = d.dy / Math.max(1, e.timeStamp - d.t);
    if (d.dy > s.offsetHeight * 0.35 || (v > 0.5 && d.dy > 24)) { s.style.transition = "transform .2s ease-in"; s.style.transform = "translateY(100%)"; if (scrim.current) { scrim.current.style.transition = "opacity .2s"; scrim.current.style.opacity = "0"; } setTimeout(onDismiss, 200); }
    else { s.style.transition = "transform .3s cubic-bezier(.2,0,0,1)"; s.style.transform = ""; if (scrim.current) { scrim.current.style.transition = "opacity .3s"; scrim.current.style.opacity = ""; } }
  };
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && close(); addEventListener("keydown", k); return () => removeEventListener("keydown", k); }, []);
  useLayoutEffect(() => { const o = document.body.style.overflow; document.body.style.overflow = "hidden"; return () => { document.body.style.overflow = o; }; }, []);
  return (
    <div class={cx("sheet-root", closing && "closing")}>
      <div class="scrim" ref={scrim} onClick={close} />
      <div class="sheet" ref={sheet} role="dialog" aria-modal="true" aria-label={title}>
        <div class="sheet-grab" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}>
          <div class="sheet-handle" />
          <div class="sheet-title t-headline-s">{title}</div>
        </div>
        <div class="sheet-body">
          <div style={{ height: 16 }} />
          {children}
        </div>
      </div>
    </div>
  );
}

/** Centered confirm dialog (M3 AlertDialog). */
export function ConfirmDialog({ title, body, confirm, danger, onConfirm, onDismiss }: { title: string; body?: string; confirm: string; danger?: boolean; onConfirm: () => void; onDismiss: () => void }) {
  return (
    <div class="dialog-root">
      <div class="scrim" onClick={onDismiss} />
      <div class="dialog" role="alertdialog" aria-label={title}>
        <div class="t-headline-s">{title}</div>
        {body && <div class="t-body-m muted" style={{ marginTop: 16 }}>{body}</div>}
        <div class="dialog-actions"><TextButton text="Cancel" onClick={onDismiss} /><TextButton text={confirm} onClick={onConfirm} color={danger ? "var(--error)" : undefined} /></div>
      </div>
    </div>
  );
}

// ───────── Pull to refresh (PullToRefreshBox) ─────────
export function PullToRefresh({ onRefresh, children }: { onRefresh: () => Promise<unknown>; children: ComponentChildren }) {
  const [pull, setPull] = useState(0); const [busy, setBusy] = useState(false); const y0 = useRef<number | null>(null); const ref = useRef<HTMLDivElement>(null);
  const scroller = () => { let n = ref.current?.parentElement ?? null; while (n && n.scrollHeight <= n.clientHeight + 1) n = n.parentElement; return n; };
  const start = (e: TouchEvent) => { const sc = scroller(); if ((sc?.scrollTop ?? 0) <= 0 && window.scrollY <= 0) y0.current = e.touches[0].clientY; };
  const move = (e: TouchEvent) => { if (y0.current == null || busy) return; const d = e.touches[0].clientY - y0.current; if (d > 0) setPull(Math.min(100, d * 0.5)); else y0.current = null; };
  const end = async () => { if (y0.current == null) return; y0.current = null; if (pull >= 56) { setBusy(true); setPull(56); try { await onRefresh(); } finally { setBusy(false); setPull(0); } } else setPull(0); };
  return (
    <div ref={ref} onTouchStart={start} onTouchMove={move} onTouchEnd={end} style={{ position: "relative" }}>
      <div class="ptr" style={{ height: pull, opacity: Math.min(1, pull / 40) }}>{(pull > 8 || busy) && <div class="ptr-dot"><Spinner size={24} stroke={2.5} /></div>}</div>
      {children}
    </div>
  );
}

/** The groups picture, or a coloured letter tile when it has none (GroupSettings.kt GroupTile). */
export function GroupTile({ g, size, image, style }: { g: { id: string; name: string; image?: string | null }; size: number; image?: string | null; style?: JSX.CSSProperties }) {
  const img = image === undefined ? g.image : image; const r = size * 0.28;
  if (img) return <img src={img} alt="" style={{ width: size, height: size, borderRadius: r, objectFit: "cover", display: "block", flex: "none", ...style }} />;
  return <div class="t-title-l" style={{ width: size, height: size, borderRadius: r, background: avatarColor(g.id), color: "#fff", display: "grid", placeItems: "center", flex: "none", ...style }}>{initials(g.name).slice(0, 1)}</div>;
}
