"use client";

import { useMemo } from "react";

import { createFeedbackApi } from "./api";
import type { FeedobackApi } from "./types";

/**
 * Returns the imperative widget API: `identify`, `open`, `startFeedback`,
 * `destroy` and the rest. The object is stable across renders and works from
 * anywhere the widget is loaded — no provider required, because it proxies to
 * the widget's global. Calls made before the widget finishes loading are
 * queued and replayed.
 *
 * ```tsx
 * const feedback = useFeedback();
 * <button onClick={feedback.startFeedback}>Report a bug</button>
 * ```
 */
export function useFeedback(): FeedobackApi {
  return useMemo(() => createFeedbackApi(), []);
}
