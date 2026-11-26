// Tiny hash router. Hash routes keep GitHub Pages happy (no server rewrites) and give the browser Back button the same meaning as Android back.
//   #/            Groups tab        #/friends  #/activity  #/account   (tabs)
//   #/g/:gid                          a group           #/g/:gid/e/:eid   a group with an expense highlighted
//   #/g/:gid/add  #/g/:gid/edit/:eid  add / edit expense  #/g/:gid/settings   group settings
//   #/join/:code                      invite link (handled in App: stored as pendingInvite, then goes to #/)
import { signal } from "@preact/signals";

const read = () => (location.hash.replace(/^#/, "") || "/");
export const path = signal(read());
addEventListener("hashchange", () => (path.value = read()));

export const segs = () => path.value.split("/").filter(Boolean);
let depth = 0; // pushes made inside this app session; 0 means we arrived on this route directly (deep link / reload)
export const go = (p: string) => { depth++; location.hash = p; };
export const replace = (p: string) => { history.replaceState(null, "", "#" + p); path.value = p; };
/** Back: previous screen if we navigated here, else the fallback route (deep link). */
export const back = (fallback = "/") => { if (depth > 0) { depth--; history.back(); } else replace(fallback); };

export const TABS = ["", "friends", "activity", "account"] as const;
export const tabIndex = () => { const i = TABS.indexOf(segs()[0] as any); return segs()[0] === "g" ? -1 : Math.max(0, i); };
