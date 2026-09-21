// Shared TanStack Start + Tailwind preset. Do not add duplicate plugins
// (tanstackStart, react, tailwindcss, tsConfigPaths, nitro) or the build breaks.
import { defineConfig } from "@lovable.dev/vite-tanstack-config";

export default defineConfig({
  tanstackStart: {
    // Redirect TanStack Start's bundled server entry to src/server.ts (our SSR error wrapper).
    // nitro/vite builds from this
    server: { entry: "server" },
  },
});
