import react from "@vitejs/plugin-react-swc";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    // Playwright specs live under e2e/ and run through their own runner.
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
  },
});
