// Port of MainActivity.kt App()/Main(): server setup -> auth -> Home, plus invite-link handling.
import { useEffect, useState } from "preact/hooks";
import { authReady, client, me, pendingInvite, session } from "./lib/api";
import { extractInviteCode } from "./lib/format";
import { path, replace, segs } from "./lib/nav";
import * as model from "./lib/model";
import { AuthScreen, ResetScreen, SetupScreen } from "./screens/Auth";
import { Home } from "./screens/Home";
import { GroupScreen } from "./screens/Group";
import { FullScreenLoading, SnackbarHost } from "./ui/ui";

/** #/join/CODE (from the invite page) or ?code=CODE: remember the code, then land on Groups, which offers to join. */
function takeInvite() {
  const s = segs();
  let code = "";
  if (s[0] === "join" && s[1]) code = extractInviteCode(decodeURIComponent(s[1]));
  else { const q = new URLSearchParams(location.search).get("code"); if (q) code = extractInviteCode(q); }
  if (code) { pendingInvite.value = code; if (location.search) history.replaceState(null, "", location.pathname); replace("/"); }
}

export function App() {
  const [settings, setSettings] = useState(false);
  const [reset, setReset] = useState(false);
  const c = client.value;
  const signedIn = session.value != null;
  void path.value;

  useEffect(() => { takeInvite(); }, [path.value]);
  // Home data + realtime start when someone signs in and stop when they sign out (HomeModel in Home.kt).
  useEffect(() => {
    if (!me.value) { model.reset(); return; }
    model.refresh(); return model.startLive();
  }, [me.value]);

  let body;
  if (c == null || settings) body = <SetupScreen canCancel={c != null} onDone={() => setSettings(false)} />;
  else if (!authReady.value) body = <FullScreenLoading />;
  else if (signedIn) body = <Signed openSettings={() => setSettings(true)} />;
  else body = reset ? <ResetScreen onBack={() => setReset(false)} /> : <AuthScreen openSettings={() => setSettings(true)} onForgot={() => setReset(true)} />;

  return (
    <div class="frame"><div class="frame-inner">
      <div key={c == null || settings ? "setup" : signedIn ? "home" : "auth"} class="cross-in" style={{ height: "100%" }}>{body}</div>
      <SnackbarHost />
    </div></div>
  );
}

function Signed({ openSettings }: { openSettings: () => void }) {
  const s = segs();
  if (s[0] === "g" && s[1]) return <div class="screen-in" style={{ height: "100%" }} key={"g" + s[1]}><GroupScreen gid={s[1]} rest={s.slice(2)} /></div>;
  return <Home openServer={openSettings} />;
}
