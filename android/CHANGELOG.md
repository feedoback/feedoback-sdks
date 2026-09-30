# Changelog

## 0.1.0 — unreleased

First release.

- The feedback sheet, opened from a control the app already owns, or from an
  optional floating launcher drawn inside the app's own Activity.
- Screenshots of what the screen composites, with redaction: password fields
  are found on their own, and `Feedoback.redact(view)` marks anything else.
- `identify()` and `setContext()`, with an offline queue that survives a
  restart.
- `setScreen()`, so feedback is filed under the screen it came from.
- Appearance — the accent, the launcher's word and mark, whether stars are
  asked for — read from the project, so it changes without an app release.
