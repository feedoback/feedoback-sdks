/**
 * The whole SDK, from an app's point of view.
 *
 * A thin, total shell over the two native SDKs. Every entry point here does
 * what they do: a bad key, no network, a project that has not opened its
 * mobile channel — none of it throws into someone's `index.js`. It says so
 * once and goes quiet, which is the native reading of *a misconfigured widget
 * removes itself*.
 *
 * Built over the native module rather than reaching for it, the way
 * `createFeedbackApi` is built over a `Window`. That is what lets the whole
 * surface be tested without a phone.
 */
import NativeFeedoback, { type Spec } from "./spec/NativeFeedoback";
import {
  normalizeCategory,
  normalizeContext,
  normalizeStartOptions,
  normalizeVisitor,
} from "./options";
import type {
  FeedobackApi,
  FeedobackCategory,
  FeedobackContext,
  FeedobackOptions,
  FeedobackVisitor,
} from "./types";

/** Where a warning goes. The console, unless a test wants to read them. */
export type Reporter = (message: string) => void;

export function createFeedoback(
  module: Spec | null,
  report: Reporter = (message) => console.warn(message),
): FeedobackApi {
  /**
   * Said once and not again. A warning on every render of a screen is noise a
   * developer scrolls past, which is how the thing it was warning about gets
   * missed.
   */
  const said = new Set<string>();
  let started = false;

  function warn(message: string): void {
    if (said.has(message)) return;
    said.add(message);
    report(`[Feedoback] ${message}`);
  }

  /** Null means the native side was never built in — a pod that was not
   *  installed, a Gradle sync that never ran, or react-native-web, where
   *  there is no native side to build in. */
  function available(): Spec | null {
    if (module) return module;
    warn(
      "the native module is not in this build. Run `pod install` in ios/ and rebuild, " +
        "or re-sync Gradle and rebuild the Android app.",
    );
    return null;
  }

  return {
    start(options: FeedobackOptions): void {
      if (started) return;
      const normalized = normalizeStartOptions(options);
      if (!normalized) {
        warn("start() needs a project key; Feedoback is off");
        return;
      }
      const native = available();
      if (!native) return;

      started = true;
      native.start(normalized);
    },

    present(category?: FeedobackCategory): void {
      // An empty string leaves the choice to native, which falls back the same
      // way it does for a Swift app that passed nothing.
      available()?.present(normalizeCategory(category) ?? "");
    },

    identify(visitor: FeedobackVisitor): void {
      available()?.identify(normalizeVisitor(visitor));
    },

    setContext(context: FeedobackContext): void {
      available()?.setContext(normalizeContext(context));
    },

    setScreen(route: string, title = ""): void {
      if (typeof route !== "string" || !route.trim()) return;
      available()?.setScreen(route.trim(), typeof title === "string" ? title.trim() : "");
    },

    setLauncherHidden(hidden: boolean): void {
      available()?.setLauncherHidden(Boolean(hidden));
    },

    reset(): void {
      available()?.reset();
    },
  };
}

/** The one every app uses, over whatever the platform provides. */
export const Feedoback: FeedobackApi = createFeedoback(NativeFeedoback);
