// Port of Auth.kt: sign in / sign up, email-code confirm, password reset, and the "connect to your server" screen.
import { ComponentChildren } from "preact";
import { useState } from "preact/hooks";
import * as api from "../lib/api";
import { Check } from "../lib/format";
import { Field, IconButton, InfoTip, LogoMark, PasswordField, PrimaryButton, Segmented, TextButton, runner, say } from "../ui/ui";

const AuthColumn = ({ children }: { children: ComponentChildren }) => <div class="auth-col">{children}</div>;
const BackRow = ({ onBack }: { onBack: () => void }) => <div style={{ paddingTop: 8 }}><IconButton icon="arrow_back" label="Back" onClick={onBack} /></div>;
const Gap = ({ h }: { h: number }) => <div style={{ height: h }} />;

export function AuthScreen({ openSettings, onForgot }: { openSettings: () => void; onForgot: () => void }) {
  const [mode, setMode] = useState(0); const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [pass, setPass] = useState("");
  const [busy, setBusy] = useState(false); const [confirming, setConfirming] = useState(false);
  const run = runner({ busy: setBusy });
  if (confirming) return <ConfirmScreen email={email} onBack={() => setConfirming(false)} />;
  const signup = mode === 1;
  const emailErr = Check.email(email), passErr = signup ? Check.password(pass) : null, nameErr = Check.personName(name);
  const valid = email.trim() !== "" && !emailErr && pass !== "" && !passErr && (!signup || (name.trim() !== "" && !nameErr));
  const submit = () => run(async () => {
    if (!signup) {
      try { await api.signIn(email, pass); } catch (e: any) {
        // Account exists but was never confirmed: send a fresh code and ask for it.
        if (String(e?.message).toLowerCase().includes("not confirmed")) { await api.resendSignupCode(email); setConfirming(true); } else throw e;
      }
    } else if (!(await api.signUp(name, email, pass))) setConfirming(true);
  });
  return (
    <AuthColumn>
      <Gap h={48} /><LogoMark size={60} /><Gap h={20} />
      <div class="t-display-s">Hissa</div><Gap h={4} />
      <div class="t-body-l muted">Hisaab barabar, dosti kayam.</div><Gap h={32} />
      <Segmented options={["Sign in", "Sign up"]} selected={mode} onSelect={setMode} /><Gap h={20} />
      <div class="stack-12">
        {signup && <Field value={name} onChange={(v) => setName(v.slice(0, 60))} label="Full name" error={nameErr} autoComplete="name" />}
        <Field value={email} onChange={(v) => setEmail(v.replace(/ /g, "").slice(0, 254))} label="Email" type="email" inputMode="email" error={emailErr} autoComplete="email" />
        <PasswordField value={pass} onChange={(v) => setPass(v.slice(0, 72))} label="Password" error={passErr} autoComplete={signup ? "new-password" : "current-password"} onEnter={() => valid && !busy && submit()} />
      </div>
      {!signup ? <div style={{ display: "flex", justifyContent: "flex-end", padding: "4px 0" }}><TextButton text="Forgot password?" onClick={onForgot} /></div> : <Gap h={16} />}
      <PrimaryButton text={signup ? "Create account" : "Sign in"} enabled={valid} loading={busy} onClick={submit} /><Gap h={16} />
      {!api.isBaked() && <div style={{ display: "flex", justifyContent: "center", padding: "4px 0" }}><TextButton text="Server settings" onClick={openSettings} color="var(--on-surface-variant)" /></div>}<Gap h={16} />
    </AuthColumn>
  );
}

export function ConfirmScreen({ email, onBack }: { email: string; onBack: () => void }) {
  const [code, setCode] = useState(""); const [busy, setBusy] = useState(false); const run = runner({ busy: setBusy });
  return (
    <AuthColumn>
      <BackRow onBack={onBack} /><Gap h={24} />
      <div class="t-headline-l">Check your email</div><Gap h={8} />
      <div class="t-body-l muted">Enter the code we sent to {email}.</div><Gap h={28} />
      <Field value={code} onChange={(v) => setCode(v.replace(/\D/g, "").slice(0, 10))} label="Code" inputMode="numeric" centered autoComplete="one-time-code" />
      <Gap h={20} />
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center" }}><span class="t-body-m muted">Nothing yet?</span><InfoTip text="It can take a minute and may land in spam." /></div>
      <PrimaryButton text="Confirm" enabled={code.length >= 6} loading={busy} onClick={() => run(() => api.confirmSignup(email, code))} /><Gap h={8} />
      <div style={{ display: "flex", justifyContent: "center" }}><TextButton text="Resend code" onClick={() => run(async () => { await api.resendSignupCode(email); say("New code sent"); })} /></div>
    </AuthColumn>
  );
}

export function ResetScreen({ onBack }: { onBack: () => void }) {
  const [email, setEmail] = useState(""); const [code, setCode] = useState(""); const [pass, setPass] = useState(""); const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false); const run = runner({ busy: setBusy });
  const ready = sent ? code.length >= 6 && !Check.password(pass) && pass !== "" : email.trim() !== "" && !Check.email(email);
  return (
    <AuthColumn>
      <BackRow onBack={onBack} /><Gap h={24} />
      <div class="t-headline-l">Reset password</div><Gap h={8} />
      <div class="t-body-l muted">{sent ? "Enter the code from your email and choose a new password." : "We will email you a code."}</div><Gap h={28} />
      <div class="stack-12">
        <Field value={email} onChange={(v) => setEmail(v.replace(/ /g, "").slice(0, 254))} label="Email" type="email" inputMode="email" error={Check.email(email)} />
        {sent && <Field value={code} onChange={(v) => setCode(v.replace(/\D/g, "").slice(0, 10))} label="Code" inputMode="numeric" centered />}
        {sent && <PasswordField value={pass} onChange={(v) => setPass(v.slice(0, 72))} label="New password (8+)" error={Check.password(pass)} />}
      </div>
      <Gap h={20} />
      <PrimaryButton text={sent ? "Set new password" : "Email me a code"} enabled={ready} loading={busy} onClick={() => run(async () => { if (!sent) { await api.sendResetCode(email); setSent(true); } else await api.resetPassword(email, code, pass); })} />
    </AuthColumn>
  );
}

export function SetupScreen({ canCancel, onDone }: { canCancel: boolean; onDone: () => void }) {
  const [url, setUrl] = useState(""); const [key, setKey] = useState(""); const run = runner();
  return (
    <AuthColumn>
      {canCancel ? <BackRow onBack={onDone} /> : <Gap h={48} />}
      <Gap h={16} /><LogoMark size={52} /><Gap h={20} />
      <div class="t-headline-l">Connect to your server</div><Gap h={8} />
      <div class="t-body-l muted">Paste the Project URL and the anon / publishable key from Supabase, under Project Settings, API.</div><Gap h={28} />
      <div class="stack-12">
        <Field value={url} onChange={setUrl} label="Project URL" type="url" inputMode="url" />
        <Field value={key} onChange={setKey} label="Anon / publishable key" multiline />
      </div>
      <Gap h={20} />
      <PrimaryButton text="Connect" enabled={url.trim() !== "" && key.trim() !== ""} onClick={() => run(async () => { api.configure(url, key); onDone(); })} />
    </AuthColumn>
  );
}
