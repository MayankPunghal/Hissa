// Port of Crop.kt: full-screen crop. Drag and pinch the photo under a fixed square frame; "Use photo" returns a 256 px JPEG (quality 80) data URL.
import { useEffect, useRef, useState } from "preact/hooks";

/** Decodes the picked photo (at most ~2000 px on its long side) and applies its EXIF rotation. */
async function loadForCrop(file: File): Promise<HTMLCanvasElement | null> {
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: "from-image" });
    const k = Math.min(1, 2000 / Math.max(bmp.width, bmp.height));
    const c = document.createElement("canvas"); c.width = Math.max(1, Math.round(bmp.width * k)); c.height = Math.max(1, Math.round(bmp.height * k));
    c.getContext("2d")!.drawImage(bmp, 0, 0, c.width, c.height); return c;
  } catch { return null; }
}

function encodeSquare(src: HTMLCanvasElement, x: number, y: number, side: number): string {
  const out = document.createElement("canvas"); out.width = out.height = 256;
  const g = out.getContext("2d")!; g.imageSmoothingQuality = "high"; g.drawImage(src, x, y, side, side, 0, 0, 256, 256);
  return out.toDataURL("image/jpeg", 0.8);
}

export function CropDialog({ file, onCancel, onDone }: { file: File; onCancel: () => void; onDone: (dataUrl: string) => void }) {
  const [bmp, setBmp] = useState<HTMLCanvasElement | null>(null);
  const [size, setSize] = useState({ w: innerWidth, h: innerHeight });
  const [view, setView] = useState({ zoom: 1, x: 0, y: 0 });
  const cv = useRef<HTMLCanvasElement>(null); const ptrs = useRef(new Map<number, { x: number; y: number }>());

  useEffect(() => { let live = true; loadForCrop(file).then((b) => { if (!live) return; if (b) setBmp(b); else onCancel(); }); return () => { live = false; }; }, [file]);
  useEffect(() => { const r = () => setSize({ w: innerWidth, h: innerHeight }); addEventListener("resize", r); return () => removeEventListener("resize", r); }, []);
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && onCancel(); addEventListener("keydown", k); return () => removeEventListener("keydown", k); }, []);

  const frame = Math.min(size.w, 640) * 0.88;
  const base = bmp ? frame / Math.min(bmp.width, bmp.height) : 1;
  const clamp = (x: number, y: number, z: number) => {
    const mx = Math.max(0, ((bmp?.width ?? 0) * base * z - frame) / 2), my = Math.max(0, ((bmp?.height ?? 0) * base * z - frame) / 2);
    return { x: Math.min(mx, Math.max(-mx, x)), y: Math.min(my, Math.max(-my, y)) };
  };

  useEffect(() => {
    const c = cv.current; if (!c || !bmp) return; const dpr = devicePixelRatio || 1;
    c.width = size.w * dpr; c.height = size.h * dpr; const g = c.getContext("2d")!; g.scale(dpr, dpr);
    g.fillStyle = "#000"; g.fillRect(0, 0, size.w, size.h);
    const s = base * view.zoom; g.imageSmoothingQuality = "medium";
    g.drawImage(bmp, size.w / 2 + view.x - (bmp.width * s) / 2, size.h / 2 + view.y - (bmp.height * s) / 2, bmp.width * s, bmp.height * s);
    const l = (size.w - frame) / 2, t = (size.h - frame) / 2;
    g.fillStyle = "rgba(0,0,0,.62)"; g.beginPath(); g.rect(0, 0, size.w, size.h); g.rect(l, t, frame, frame); g.fill("evenodd");
    g.strokeStyle = "#fff"; g.lineWidth = 2; g.strokeRect(l, t, frame, frame);
    g.strokeStyle = "rgba(255,255,255,.35)"; g.lineWidth = 1; g.beginPath();
    for (let i = 1; i <= 2; i++) { g.moveTo(l + (frame * i) / 3, t); g.lineTo(l + (frame * i) / 3, t + frame); g.moveTo(l, t + (frame * i) / 3); g.lineTo(l + frame, t + (frame * i) / 3); }
    g.stroke();
  }, [bmp, size, view, frame, base]);

  const dist = () => { const p = [...ptrs.current.values()]; return p.length < 2 ? 0 : Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y); };
  const down = (e: PointerEvent) => { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); ptrs.current.set(e.pointerId, { x: e.clientX, y: e.clientY }); };
  const move = (e: PointerEvent) => {
    const prev = ptrs.current.get(e.pointerId); if (!prev) return;
    const d0 = dist(); ptrs.current.set(e.pointerId, { x: e.clientX, y: e.clientY }); const d1 = dist();
    const gz = d0 && d1 ? d1 / d0 : 1;
    setView((v) => { const z = Math.min(6, Math.max(1, v.zoom * gz)); const c = clamp(v.x + (ptrs.current.size > 1 ? 0 : e.clientX - prev.x), v.y + (ptrs.current.size > 1 ? 0 : e.clientY - prev.y), z); return { zoom: z, ...c }; });
  };
  const up = (e: PointerEvent) => ptrs.current.delete(e.pointerId);
  const wheel = (e: WheelEvent) => { e.preventDefault(); setView((v) => { const z = Math.min(6, Math.max(1, v.zoom * Math.exp(-e.deltaY * 0.002))); return { zoom: z, ...clamp(v.x, v.y, z) }; }); };

  const use = () => {
    if (!bmp) return; const s = base * view.zoom, m = Math.min(bmp.width, bmp.height);
    const side = Math.min(m, Math.max(1, Math.trunc(frame / s)));
    const x = Math.min(bmp.width - side, Math.max(0, Math.trunc(bmp.width / 2 - view.x / s - side / 2)));
    const y = Math.min(bmp.height - side, Math.max(0, Math.trunc(bmp.height / 2 - view.y / s - side / 2)));
    onDone(encodeSquare(bmp, x, y, side));
  };

  if (!bmp) return null;
  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 80, background: "#000" }} role="dialog" aria-label="Crop photo">
      <canvas ref={cv} style={{ width: "100%", height: "100%", display: "block", touchAction: "none", cursor: "grab" }} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up} onWheel={wheel} />
      <div class="t-body-m" style={{ position: "absolute", top: 20, left: 0, right: 0, textAlign: "center", color: "rgba(255,255,255,.8)", pointerEvents: "none" }}>Drag and pinch to adjust</div>
      <div style={{ position: "absolute", left: "50%", top: "50%", transform: `translate(-50%, ${frame / 2 + 52}px)`, width: "100%", maxWidth: 640, padding: "0 20px", display: "flex", alignItems: "center" }}>
        <button class="t-label-l press" style={{ height: 52, padding: "0 12px", color: "#fff" }} onClick={onCancel}>Cancel</button>
        <div style={{ flex: 1 }} />
        <button class="t-label-l press" style={{ height: 52, padding: "0 24px", borderRadius: 26, background: "#fff", color: "#000" }} onClick={use}>Use photo</button>
      </div>
    </div>
  );
}
