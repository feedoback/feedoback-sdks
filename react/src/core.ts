/**
 * The framework-agnostic core.
 *
 * Everything that touches `window` or the DOM lives here, behind plain
 * functions that take the target `Window` explicitly. That is what makes the
 * behaviour testable without a browser and keeps the React layer a thin shell
 * over it. Nothing here imports React.
 */
import type { FeedobackApi, FeedobackVisitor, FeedobackContext } from "./types";

export const DEFAULT_HOST = "https://feedoback.com";

/** The global the hosted widget installs. Keep this name in step with widget.js. */
const GLOBAL = "Feedoback" as const;

/** Our marker attribute, so we recognise a script we injected. */
const SDK_ATTRIBUTE = "data-feedoback-sdk";

/** A call queued before the widget booted: `[method, arg?]`, replayed on boot. */
type QueuedCall = [keyof FeedobackApi, unknown?];

/**
 * The shape `window.Feedoback` can take: a bare queue stub before the script
 * loads, or the full API afterwards. Every method is optional because at any
 * instant only one of those two states is present.
 */
type WidgetGlobal = Partial<FeedobackApi> & { q?: QueuedCall[] };

export type WidgetWindow = Window & { [GLOBAL]?: WidgetGlobal };

/**
 * Ensures the queue stub exists without clobbering a real API the widget may
 * have already installed. `??=` leaves an existing value untouched, so calling
 * this repeatedly is safe.
 */
function ensureGlobal(win: WidgetWindow): WidgetGlobal {
  return (win[GLOBAL] ??= { q: [] });
}

/**
 * Forwards a call to the widget, or queues it if the widget has not booted.
 *
 * This is the same queue-or-call pattern the hosted widget replays on boot, so
 * a call made a millisecond before `widget.js` finishes loading behaves
 * identically to one made a second after.
 */
export function dispatch(win: WidgetWindow, method: keyof FeedobackApi, arg?: unknown): void {
  const widget = ensureGlobal(win);
  const handler = widget[method];
  if (typeof handler === "function") {
    (handler as (value?: unknown) => void)(arg);
  } else {
    (widget.q ??= []).push([method, arg]);
  }
}

export type LoadOptions = {
  projectKey: string;
  host?: string;
  version?: string;
  scriptSrc?: string;
  onLoad?: () => void;
  onError?: () => void;
};

function scriptUrl({ host, scriptSrc }: LoadOptions): string {
  if (scriptSrc) return scriptSrc;
  const base = (host ?? DEFAULT_HOST).replace(/\/+$/, "");
  return `${base}/widget.js`;
}

/** Finds a script this SDK already injected for the same project, if any. */
function existingScript(doc: Document, projectKey: string): HTMLScriptElement | null {
  const scripts = doc.querySelectorAll<HTMLScriptElement>(`script[${SDK_ATTRIBUTE}]`);
  for (const script of scripts) {
    if (script.getAttribute("data-project") === projectKey) return script;
  }
  return null;
}

/**
 * Injects `widget.js` with the attributes the widget reads from its own tag
 * (`data-project`, optional `data-version`). Idempotent: a second call for the
 * same project returns the existing tag rather than stacking a duplicate, which
 * matters under React Strict Mode and single-page navigation.
 *
 * The queue stub is created first, so any identity queued in the same tick is
 * in place before the async script evaluates and replays it.
 */
export function loadWidget(win: WidgetWindow, options: LoadOptions): HTMLScriptElement | null {
  const doc = win.document;
  if (!doc) return null;

  ensureGlobal(win);

  const already = existingScript(doc, options.projectKey);
  if (already) return already;

  const script = doc.createElement("script");
  script.src = scriptUrl(options);
  script.async = true;
  script.setAttribute("data-project", options.projectKey);
  if (options.version) script.setAttribute("data-version", options.version);
  script.setAttribute(SDK_ATTRIBUTE, "");
  if (options.onLoad) script.addEventListener("load", options.onLoad, { once: true });
  if (options.onError) script.addEventListener("error", options.onError, { once: true });

  (doc.body ?? doc.head ?? doc.documentElement).appendChild(script);
  return script;
}

/**
 * Removes the widget and the script tag this SDK injected. Uses the widget's
 * own `destroy()` when present so it can tear down its shadow hosts and the
 * global; then drops our tag so a later mount starts clean.
 */
export function unloadWidget(win: WidgetWindow, script: HTMLScriptElement | null): void {
  win[GLOBAL]?.destroy?.();
  script?.remove();
}
