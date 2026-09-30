"use client";

import { useEffect, useRef } from "react";

import { dispatch, loadWidget, unloadWidget } from "./core";
import type { FeedbackWidgetProps } from "./types";

/**
 * Loads the Feedoback widget on the page. Render it once, near the root of the
 * app; it draws nothing itself and returns `null`. In Next.js App Router it is
 * a client component, so it can live in a server layout directly.
 *
 * ```tsx
 * <FeedbackWidget projectKey="pk_live_123" />
 * ```
 */
export function FeedbackWidget({
  projectKey,
  host,
  version,
  scriptSrc,
  visitor,
  context,
  destroyOnUnmount = false,
  onLoad,
  onError,
}: FeedbackWidgetProps): null {
  const scriptRef = useRef<HTMLScriptElement | null>(null);

  // Inject the script. Re-runs only when what the tag encodes changes; the
  // load is idempotent, so Strict Mode's double mount does not stack a second
  // launcher. onLoad/onError are read through refs so a new closure each render
  // does not retrigger the injection.
  const onLoadRef = useRef(onLoad);
  const onErrorRef = useRef(onError);
  onLoadRef.current = onLoad;
  onErrorRef.current = onError;

  useEffect(() => {
    if (typeof window === "undefined") return;
    scriptRef.current = loadWidget(window, {
      projectKey,
      host,
      version,
      scriptSrc,
      onLoad: () => onLoadRef.current?.(),
      onError: () => onErrorRef.current?.(),
    });
    return () => {
      if (destroyOnUnmount) {
        unloadWidget(window, scriptRef.current);
        scriptRef.current = null;
      }
    };
  }, [projectKey, host, version, scriptSrc, destroyOnUnmount]);

  // Identity is kept in its own effect so it re-applies when the signed-in user
  // changes without reloading the script. Queued before boot, forwarded after.
  useEffect(() => {
    if (typeof window === "undefined" || !visitor) return;
    dispatch(window, "identify", visitor);
  }, [visitor?.id, visitor?.email, visitor?.name]);

  const contextKey = context ? JSON.stringify(context) : null;
  useEffect(() => {
    if (typeof window === "undefined" || !context) return;
    dispatch(window, "setContext", context);
    // context is compared by value through contextKey; re-reading the object
    // here is intentional and matches that comparison.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contextKey]);

  return null;
}
