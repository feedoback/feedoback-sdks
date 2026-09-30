"use client";

import type { PropsWithChildren } from "react";

import { FeedbackWidget } from "./FeedbackWidget";
import type { FeedbackWidgetProps } from "./types";

/**
 * Convenience wrapper for teams who prefer wrapping the app over dropping a
 * component in a layout. It renders its children and loads the widget beside
 * them; there is no context to consume, because {@link useFeedback} talks to
 * the widget's global directly and works anywhere below (or beside) this.
 *
 * ```tsx
 * <FeedbackProvider projectKey="pk_live_123" visitor={user}>
 *   <App />
 * </FeedbackProvider>
 * ```
 */
export function FeedbackProvider({ children, ...props }: PropsWithChildren<FeedbackWidgetProps>) {
  return (
    <>
      {children}
      <FeedbackWidget {...props} />
    </>
  );
}
