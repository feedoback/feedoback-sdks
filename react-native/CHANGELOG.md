# Changelog

## 0.1.0 — 2026-10-01

First release. A bridge to the iOS and Android SDKs, so the sheet is native on
both.

- `Feedoback.start`, `present`, `identify`, `setContext`, `setScreen`,
  `setLauncherHidden` and `reset`.
- `FeedbackProvider` and `useFeedback()` for the same calls from a component.
- `FeedobackRedact`, which keeps what it wraps out of the screenshot.
- Built on the new architecture (TurboModules and Fabric).
