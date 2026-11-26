// Port of Format.kt + Validate.kt
export const symbol = (cur: string) => ({ INR: "₹", USD: "$", EUR: "€", GBP: "£" } as Record<string, string>)[cur] ?? `${cur} `;

function indian(n: number): string {
  const s = String(n);
  if (s.length <= 3) return s;
  let rest = s.slice(0, -3), out = "," + s.slice(-3);
  while (rest.length > 2) { out = "," + rest.slice(-2) + out; rest = rest.slice(0, -2); }
  return rest + out;
}
/** Amount with symbol. INR uses Indian grouping; the sign is dropped (callers say owe / owed in words). */
export function money(cents: number, cur = "INR"): string {
  const a = Math.abs(Math.trunc(cents));
  const whole = Math.floor(a / 100);
  const w = cur === "INR" ? indian(whole) : whole.toLocaleString("en-US");
  return `${symbol(cur)}${w}.${String(a % 100).padStart(2, "0")}`;
}
export const initials = (name: string) => name.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join("") || "?";
export const firstName = (name: string) => name.trim().split(" ")[0] || name;

const MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
export const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };
/** "Today", "Yesterday", "12 Nov", "12 Nov 2025". */
export function dayLabel(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso); if (!m) return iso;
  const d = new Date(+m[1], +m[2] - 1, +m[3]); const t = new Date(); t.setHours(0, 0, 0, 0);
  const diff = Math.round((t.getTime() - d.getTime()) / 864e5);
  if (diff === 0) return "Today"; if (diff === 1) return "Yesterday";
  return d.getFullYear() === t.getFullYear() ? `${d.getDate()} ${MON[d.getMonth()]}` : `${d.getDate()} ${MON[d.getMonth()]} ${d.getFullYear()}`;
}
export function agoLabel(iso: string): string {
  const t = Date.parse(iso); if (isNaN(t)) return "";
  const m = Math.floor((Date.now() - t) / 60000);
  if (m < 1) return "just now"; if (m < 60) return `${m}m ago`; if (m < 1440) return `${Math.floor(m / 60)}h ago`; if (m < 10080) return `${Math.floor(m / 1440)}d ago`;
  return dayLabel(iso);
}
/** Short, human error text. */
export function friendly(e: unknown): string {
  const msg: string = ((e as any)?.message ?? String(e ?? "")).split("\n")[0]; const m = msg.toLowerCase(); const has = (s: string) => m.includes(s);
  if (has("check constraint") || has("violates")) return "Please fill in every field and try again.";
  if (has("schema cache") || has("could not find the function")) return "This isn't available right now. Please try again later.";
  if (has("invalid login")) return "Wrong email or password.";
  if (has("not confirmed")) return "Please confirm your email first.";
  if (has("already registered") || has("already been registered") || has("user_already_exists")) return "An account with this email already exists. Try signing in instead.";
  if (has("failed to fetch") || has("networkerror") || has("load failed") || has("timeout")) return "No internet connection.";
  if (has("rate limit") || has("too many")) return "Too many attempts. Please wait a few minutes and try again.";
  if (has("weak") && has("password")) return "That password is too weak. Use 8+ characters with letters and numbers.";
  if (has("invalid") && has("email")) return "Enter a valid email address.";
  if (has("invalid invite code")) return "That invite code doesn't match any group.";
  if (has("expired") || (has("invalid") && has("token"))) return "That code is wrong or has expired.";
  if (!msg || msg.length > 90) return "Something went wrong. Please try again.";
  return msg;
}
/** Finds the invite code in a link, a "Code: abc12345" line, or the bare code. */
export function extractInviteCode(text: string): string {
  const t = text.trim();
  let m = /(?:join\/|code=)([A-Za-z0-9]{6,16})/.exec(t); if (m) return m[1].toLowerCase();
  m = /code[^a-z0-9]+([a-z0-9]{6,16})/i.exec(t); if (m) return m[1].toLowerCase();
  return t.replace(/[^A-Za-z0-9]/g, "").toLowerCase().slice(0, 12);
}

export const Check = {
  email: (v: string) => (!v.trim() ? null : v.trim().length > 254 || !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(v.trim()) ? "Enter a valid email, like name@example.com." : null),
  password: (v: string) => (!v ? null : v.length < 8 ? "Use at least 8 characters." : v.length > 72 ? "Use 72 characters or fewer." : !/\p{L}/u.test(v) || !/\d/.test(v) ? "Mix letters and numbers." : null),
  personName: (v: string) => (!v.trim() ? null : v.trim().length < 2 ? "Enter your full name." : !/\p{L}/u.test(v) ? "A name needs letters." : null),
  groupName: (v: string) => (v.trim() && v.trim().length < 2 ? "Use at least 2 characters." : null),
};

// Avatar colours (Components.kt avatarColor). Java String.hashCode so a user keeps the same colour as on Android.
const PALETTE = ["#2F80ED", "#E5484D", "#12A18A", "#E9692C", "#D6409F", "#7C5CFF", "#5B6B7A", "#7A8F00"];
export function avatarColor(key: string): string {
  let h = 0; for (let i = 0; i < key.length; i++) h = (Math.imul(31, h) + key.charCodeAt(i)) | 0;
  return PALETTE[(h & 0x7fffffff) % PALETTE.length];
}
