/**
 * Stands in for the React Native runtime.
 *
 * Everything in `src/` that is worth testing is plain TypeScript over the
 * native module, so the runtime is exactly the part these tests do without.
 * `TurboModuleRegistry.get` answering null is also the real answer on a build
 * that never installed the pod, which is a path the SDK has to survive.
 */
export const TurboModuleRegistry = {
  get: () => null,
  getEnforcing: () => {
    throw new Error("not available in tests");
  },
};

export type TurboModule = object;
