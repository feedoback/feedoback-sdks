/**
 * Shaping what crosses the bridge.
 *
 * The web SDK shapes `identify()` and `setContext()` twice, for two reasons:
 * `widget/identity.ts` only makes the payload safe to `postMessage`, and
 * `lib/widget/visitor.ts` does the validation that counts. The split here is
 * the same one. This file makes a value safe to hand the bridge — a function,
 * a Date, an undefined or a NaN is dropped rather than throwing out of
 * someone's `useEffect` — and the native SDKs bound what is left against the
 * server's own limits. Neither half is the other's backstop.
 *
 * Nothing here fills in a default. Both native SDKs already have defaults and
 * they are the ones the plain-Swift and plain-Kotlin apps get; a second set
 * here would be a second thing to keep in step. An option this drops is an
 * option native never hears about, which is exactly how it falls back.
 */
import {
  FEEDBACK_CATEGORIES,
  LAUNCHER_CORNERS,
  LAUNCHER_STYLES,
  LOG_LEVELS,
  SCREENSHOT_MODES,
  THEMES,
  type FeedobackCategory,
  type FeedobackContext,
  type FeedobackOptions,
  type FeedobackVisitor,
} from "./types";

/** What the native module is handed: the same names, nothing undefined. */
export type NativeStartOptions = {
  projectKey: string;
  host?: string;
  theme?: string;
  categories?: string[];
  screenshots?: string;
  launcher?: NativeLauncherOptions;
  logLevel?: string;
};

export type NativeLauncherOptions = {
  enabled?: boolean;
  corner?: string;
  offset?: { x: number; y: number };
  style?: string;
  draggable?: boolean;
  hidesWithKeyboard?: boolean;
};

/** A member of a known list, or nothing at all so native keeps its default. */
function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return typeof value === "string" && (allowed as readonly string[]).includes(value)
    ? (value as T)
    : undefined;
}

function boolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

/** Infinity and NaN cross the bridge and land as something unusable. */
function finite(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function text(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

/** Only set the key when there is something to set, so `{}` stays `{}`. */
function put<T extends object, K extends keyof T>(target: T, key: K, value: T[K] | undefined) {
  if (value !== undefined) target[key] = value;
}

/**
 * One category name, or null. `present()` takes a category the same way the
 * web panel opens on one, and a name we do not know is not silently turned
 * into feedback: native falls back, and the fallback is its own.
 */
export function normalizeCategory(value: unknown): FeedobackCategory | null {
  return oneOf(value, FEEDBACK_CATEGORIES) ?? null;
}

export function normalizeLauncher(input: unknown): NativeLauncherOptions | undefined {
  if (typeof input !== "object" || input === null) return undefined;
  const value = input as Record<string, unknown>;
  const launcher: NativeLauncherOptions = {};

  put(launcher, "enabled", boolean(value.enabled));
  put(launcher, "corner", oneOf(value.corner, LAUNCHER_CORNERS));
  put(launcher, "style", oneOf(value.style, LAUNCHER_STYLES));
  put(launcher, "draggable", boolean(value.draggable));
  put(launcher, "hidesWithKeyboard", boolean(value.hidesWithKeyboard));

  // Both numbers or neither: an offset with one axis missing would put the
  // button somewhere nobody asked for on the other.
  const offset = value.offset as Record<string, unknown> | undefined;
  const x = finite(offset?.x);
  const y = finite(offset?.y);
  if (x !== undefined && y !== undefined) launcher.offset = { x, y };

  return Object.keys(launcher).length ? launcher : undefined;
}

function normalizeList<T extends string>(input: unknown, allowed: readonly T[]): T[] | undefined {
  if (!Array.isArray(input)) return undefined;
  const kept: T[] = [];
  for (const entry of input) {
    const value = oneOf(entry, allowed);
    if (value && !kept.includes(value)) kept.push(value);
  }
  return kept.length ? kept : undefined;
}

/**
 * Everything `start()` was given, minus what the bridge cannot carry.
 *
 * Null when there is no project key at all, which is the one thing native
 * cannot fall back from — it is the whole configuration.
 */
export function normalizeStartOptions(input: FeedobackOptions): NativeStartOptions | null {
  const value = (input ?? {}) as Record<string, unknown>;
  const projectKey = text(value.projectKey);
  if (!projectKey) return null;

  const options: NativeStartOptions = { projectKey };

  // A trailing slash would make every URL the transport builds a double one.
  const host = text(value.host)?.replace(/\/+$/, "");
  put(options, "host", host);
  put(options, "theme", oneOf(value.theme, THEMES));
  put(options, "categories", normalizeList(value.categories, FEEDBACK_CATEGORIES));
  put(options, "screenshots", oneOf(value.screenshots, SCREENSHOT_MODES));
  put(options, "launcher", normalizeLauncher(value.launcher));
  put(options, "logLevel", oneOf(value.logLevel, LOG_LEVELS));

  return options;
}

/**
 * The four fields an identity can have, and nothing else.
 *
 * An empty string is dropped rather than sent: it is what a signed-out app
 * passes without meaning to, and `""` as an id would name somebody.
 */
export function normalizeVisitor(input: FeedobackVisitor): Record<string, string> {
  const value = (input ?? {}) as Record<string, unknown>;
  const visitor: Record<string, string> = {};
  for (const field of ["id", "email", "name", "userHash"] as const) {
    const entry = text(value[field]);
    if (entry !== undefined) visitor[field] = entry;
  }
  return visitor;
}

/**
 * A flat map of scalars, which is what the server stores and all the bridge
 * can carry. A nested object or an array is dropped rather than stringified:
 * filing `[object Object]` under somebody's feedback helps nobody.
 *
 * `undefined` is dropped too. It is not a value the server has, and on the
 * bridge it is indistinguishable from a key that was never set.
 */
export function normalizeContext(input: FeedobackContext): Record<string, unknown> {
  if (typeof input !== "object" || input === null || Array.isArray(input)) return {};
  const context: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (!key) continue;
    if (value === null || typeof value === "string" || typeof value === "boolean") {
      context[key] = value;
    } else if (typeof value === "number" && Number.isFinite(value)) {
      context[key] = value;
    }
  }
  return context;
}
