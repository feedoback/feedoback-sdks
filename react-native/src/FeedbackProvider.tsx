import { useEffect, type ReactNode } from "react";

import { Feedoback } from "./Feedoback";
import type { FeedobackContext, FeedobackOptions, FeedobackVisitor } from "./types";

export type FeedbackProviderProps = FeedobackOptions & {
  /** Identify this visitor as soon as the SDK starts, and whenever it changes. */
  visitor?: FeedobackVisitor;
  /** Attach this context as soon as the SDK starts, and whenever it changes. */
  context?: FeedobackContext;
  children?: ReactNode;
};

/**
 * Starts Feedoback and keeps the identity and context in step with your app's
 * state. Render it once, near the root; it draws nothing of its own.
 *
 * ```tsx
 * <FeedbackProvider projectKey="pk_live_123" visitor={user}>
 *   <App />
 * </FeedbackProvider>
 * ```
 *
 * Starting from `index.js` instead is equally fine — `Feedoback.start()` is
 * the same call. What this adds is the two effects: an app that signs someone
 * in three screens later gets `identify()` without wiring it by hand.
 */
export function FeedbackProvider({
  children,
  visitor,
  context,
  ...options
}: FeedbackProviderProps) {
  // Once, on mount. Starting is idempotent in the native SDKs for the same
  // reason a snippet pasted twice must not stack two launchers, so Strict
  // Mode's double mount costs nothing. Changing the project key afterwards
  // does not restart it: a second project mid-session is not a thing an app
  // does, and tearing the launcher down to find out would be worse.
  const { projectKey } = options;
  useEffect(() => {
    Feedoback.start({ ...options, projectKey });
    // The rest of the options are read once, at start, which is the only time
    // the native side looks at them.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectKey]);

  // Its own effect, so it re-applies when the signed-in user changes without
  // restarting anything. A late identify re-asks the server for config, which
  // is how a listed rollout reaches someone who signed in after launch.
  useEffect(() => {
    if (!visitor) return;
    Feedoback.identify(visitor);
  }, [visitor?.id, visitor?.email, visitor?.name, visitor?.userHash]);

  const contextKey = context ? JSON.stringify(context) : null;
  useEffect(() => {
    if (!context) return;
    Feedoback.setContext(context);
    // context is compared by value through contextKey; re-reading the object
    // here is intentional and matches that comparison.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contextKey]);

  return <>{children}</>;
}
