/**
 * feedoback-react — the React and Next.js SDK for the Feedoback widget.
 *
 * Drop {@link FeedbackWidget} in once to load the widget, and reach for
 * {@link useFeedback} anywhere to drive it. No manual script tags, no wrapper
 * components to hand-write.
 */
export { FeedbackWidget } from "./FeedbackWidget";
export { FeedbackProvider } from "./FeedbackProvider";
export { useFeedback } from "./useFeedback";
export { createFeedbackApi } from "./api";
export { DEFAULT_HOST } from "./core";
export type {
  FeedbackWidgetProps,
  FeedobackApi,
  FeedobackVisitor,
  FeedobackContext,
} from "./types";
