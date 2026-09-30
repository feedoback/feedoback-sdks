/**
 * Builds the imperative {@link FeedobackApi}. Each method is a thin proxy over
 * {@link dispatch}, so the same object works before and after the widget loads
 * and is safe to hold onto across renders.
 *
 * The window is resolved lazily through `getWindow` rather than captured, which
 * keeps the API defined on the server (where there is no window) and lets it
 * start working the moment one exists.
 */
import { dispatch, type WidgetWindow } from "./core";
import type { FeedobackApi } from "./types";

function resolveWindow(): WidgetWindow | undefined {
  return typeof window !== "undefined" ? (window as WidgetWindow) : undefined;
}

export function createFeedbackApi(getWindow: () => WidgetWindow | undefined = resolveWindow): FeedobackApi {
  const send = (method: keyof FeedobackApi, arg?: unknown) => {
    const win = getWindow();
    if (win) dispatch(win, method, arg);
  };

  return {
    identify: (visitor) => send("identify", visitor),
    setContext: (context) => send("setContext", context),
    open: () => send("open"),
    close: () => send("close"),
    toggle: () => send("toggle"),
    startFeedback: () => send("startFeedback"),
    startRecording: () => send("startRecording"),
    destroy: () => send("destroy"),
  };
}
