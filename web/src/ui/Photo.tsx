// Port of Photos.kt: encodePhoto (1280px long side, JPEG, < 450_000 chars) and the full-screen PhotoViewer.
import { useEffect } from "preact/hooks";
import { IconButton } from "./ui";

/** Turns a picked or captured image into a small JPEG data URL. Returns null when it can't be read. */
export async function encodePhoto(file: File): Promise<string | null> {
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: "from-image" });
    let w = bmp.width, h = bmp.height; const s = Math.min(1, 1280 / Math.max(w, h)); w = Math.round(w * s); h = Math.round(h * s);
    let quality = 0.72;
    for (;;) {
      const c = document.createElement("canvas"); c.width = w; c.height = h;
      c.getContext("2d")!.drawImage(bmp, 0, 0, w, h);
      const url = c.toDataURL("image/jpeg", quality);
      if (url.length < 450_000) return url;
      if (quality > 0.45) quality -= 0.1; else { w = Math.round(w * 0.8); h = Math.round(h * 0.8); }
    }
  } catch { return null; }
}

/** Full-screen photo: tap anywhere or the close button to leave. */
export function PhotoViewer({ src, onClose }: { src: string; onClose: () => void }) {
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && onClose(); addEventListener("keydown", k); return () => removeEventListener("keydown", k); }, []);
  return (
    <div class="photo-viewer" onClick={onClose}>
      <img src={src} alt="Expense photo" />
      <div class="photo-close"><IconButton icon="close" label="Close" onClick={onClose} color="#fff" /></div>
    </div>
  );
}
