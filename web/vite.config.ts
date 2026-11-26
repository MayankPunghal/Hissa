import { defineConfig } from "vite";
import preact from "@preact/preset-vite";
import { VitePWA } from "vite-plugin-pwa";

// base "./" so the same build works at /Hissa/app/ on GitHub Pages and at the root in dev.
export default defineConfig({
  base: "./",
  plugins: [
    preact(),
    VitePWA({
      registerType: "autoUpdate",
      manifest: {
        name: "Hissa", short_name: "Hissa", description: "Split bills with friends. Free and open source.",
        start_url: "./", scope: "./", display: "standalone", orientation: "portrait",
        background_color: "#0B0B0C", theme_color: "#FFB020",
        icons: [
          { src: "icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      workbox: { navigateFallback: "index.html", globPatterns: ["**/*.{js,css,html,svg,png,ttf,woff2}"] },
    }),
  ],
});
