import { build } from "esbuild";
import { mkdir } from "node:fs/promises";

/**
 * Bundles the fixture app before the browser tests run. It imports the built
 * package from dist/, so the bundle here is what npm would ship wired into a
 * real React tree — not the source. Run `npm run build` first (the test:e2e
 * script does).
 */
export default async function globalSetup(): Promise<void> {
  await mkdir("e2e/.artifacts", { recursive: true });
  await build({
    entryPoints: ["e2e/fixtures/app.tsx"],
    outfile: "e2e/.artifacts/app.js",
    bundle: true,
    format: "iife",
    target: ["es2020"],
    jsx: "automatic",
    logLevel: "error",
  });
}
