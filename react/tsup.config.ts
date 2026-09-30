import { defineConfig } from "tsup";

/**
 * Dual ESM + CJS with type declarations. React stays external — it is a peer,
 * never bundled. The `"use client"` directive is re-added to the output by
 * scripts/add-directive.mjs: esbuild strips source directives and its banner
 * when it bundles, and Next.js App Router needs the directive on the shipped
 * file for the components to register as client components.
 */
export default defineConfig({
  entry: ["src/index.ts"],
  format: ["esm", "cjs"],
  dts: true,
  sourcemap: true,
  clean: true,
  treeshake: true,
  target: "es2020",
  external: ["react"],
  outExtension({ format }) {
    return { js: format === "cjs" ? ".cjs" : ".js" };
  },
  onSuccess: "node scripts/add-directive.mjs",
});
