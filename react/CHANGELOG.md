# Changelog

## 0.2.0 — 2026-09-11

- **Breaking:** `openSupport()` is gone from `useFeedback()` and the types. The
  widget stopped offering support threads, and the method had nothing left to
  open.

## 0.1.0 — 2026-09-06

First release.

- `<FeedbackWidget>` and `<FeedbackProvider>`, which load the widget once, so
  client-side navigation never stacks two.
- `useFeedback()` for `open`, `close`, `toggle`, `startFeedback`,
  `startRecording`, `identify`, `setContext` and `destroy` from any component,
  queued until the script has loaded.
