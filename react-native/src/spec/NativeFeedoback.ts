/**
 * The bridge, and nothing else.
 *
 * Deliberately dumb: strings and plain objects, no unions and no shapes for
 * codegen to marshal. Everything worth knowing about an option has already
 * been decided in `options.ts` by the time it reaches here, and the native
 * SDKs apply their own defaults for whatever this does not carry. A typed
 * struct here would only be a third place that has to agree with the other
 * two.
 */
import { TurboModuleRegistry, type TurboModule } from "react-native";
import type { UnsafeObject } from "react-native/Libraries/Types/CodegenTypes";

export interface Spec extends TurboModule {
  start(options: UnsafeObject): void;
  present(category: string): void;
  identify(visitor: UnsafeObject): void;
  setContext(context: UnsafeObject): void;
  setScreen(route: string, title: string): void;
  setLauncherHidden(hidden: boolean): void;
  reset(): void;
}

/**
 * `get`, not `getEnforcing`: a missing module means the native side was not
 * built in — a pod that was never installed, a Gradle sync that never ran —
 * and that is a mistake to say out loud once, not to throw out of an import
 * and take the whole app down with.
 */
export default TurboModuleRegistry.get<Spec>("Feedoback");
