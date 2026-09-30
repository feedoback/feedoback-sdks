import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const here = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  resolve: {
    alias: {
      // The React Native runtime is the part these tests deliberately do
      // without: what is worth testing here is plain TypeScript over the
      // native module, and the module being absent is a real state anyway.
      "react-native": resolve(here, "test/react-native.stub.ts"),
    },
  },
  test: {
    globals: true,
    environment: "node",
    // The example app is a React Native app with its own Jest setup; it is
    // verified by being run on a simulator, not from here.
    include: ["src/**/*.test.ts"],
  },
});
