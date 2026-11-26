// Port of HomeModel (Home.kt): home data + a live-change counter other screens can key on.
import { signal } from "@preact/signals";
import type { HomeData } from "./types";
import * as api from "./api";
import { friendly } from "./format";

export const home = {
  data: signal<HomeData | null>(null),
  loading: signal(false),
  failed: signal<string | null>(null),
  /** Increments on every live (realtime) refresh so open screens can reload. */
  tick: signal(0),
};

export async function refresh(live = false) {
  if (!live) home.loading.value = true;
  try {
    home.data.value = await api.loadHome(); home.failed.value = null; if (live) home.tick.value++;
  } catch (e) { if (!home.data.value) home.failed.value = friendly(e); }
  finally { home.loading.value = false; }
}
export const name = (id: string) => (id === api.me.value ? "You" : home.data.value?.names[id] ?? "Friend");
export const fullName = (id: string) => home.data.value?.names[id] ?? "Friend";
export function reset() { home.data.value = null; home.failed.value = null; home.tick.value = 0; }

/** Live updates are a bonus; pull-to-refresh still works. Returns the unsubscribe function. */
export function startLive(): () => void {
  let timer: number | undefined; let off: (() => void) | undefined;
  try { off = api.liveChannel("home", () => { clearTimeout(timer); timer = window.setTimeout(() => refresh(true), 400); }); } catch { /* ignore */ }
  return () => { clearTimeout(timer); off?.(); };
}
