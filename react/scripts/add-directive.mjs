import { readFile, writeFile } from "node:fs/promises";

/**
 * Prepends the `"use client"` directive to the built bundles.
 *
 * esbuild strips a module-level directive when it concatenates modules, and its
 * `banner` option is dropped for the same reason, so the reliable place to add
 * it is here, after the bundle exists. Next.js App Router reads this directive
 * off the shipped file to register the components as client components; without
 * it, importing <FeedbackWidget> from a server component throws.
 */
const DIRECTIVE = '"use client";\n';
const files = ["dist/index.js", "dist/index.cjs"];

for (const file of files) {
  const source = await readFile(file, "utf8");
  if (!source.startsWith('"use client"')) {
    await writeFile(file, DIRECTIVE + source);
  }
}
