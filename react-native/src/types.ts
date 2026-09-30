/**
 * Public types for the Feedoback React Native SDK.
 *
 * Where this package and `feedoback-react` do the same thing they say it the
 * same way: an identity is at most an id, an email and a name, custom context
 * is a flat map of scalars, and both are the app's own claim about who someone
 * is — the server never treats either as authoritative.
 *
 * Where they differ, they differ because a phone is not a page. There is no
 * element picker and no screen recording on mobile, so the panel's three ways
 * in reduce to one and `present()` opens the sheet directly. In exchange there
 * is a screen name, a floating launcher, and a redaction wrapper.
 */

/** What a thread is. The same three the web widget offers. */
export const FEEDBACK_CATEGORIES = ["feedback", "bug", "idea"] as const;
export type FeedobackCategory = (typeof FEEDBACK_CATEGORIES)[number];

/** Who the current visitor is. Every field is optional; all are the app's claim. */
export type FeedobackVisitor = {
  id?: string;
  email?: string;
  name?: string;
  /**
   * Hex HMAC-SHA256 of the id (or the email, when there is no id), computed by
   * **your server** with the project's identity secret. The only part of an
   * identity a server can check. The secret never belongs in an app: anything
   * the app holds, the app can forge.
   */
  userHash?: string;
};

/** Flat, scalar-only context stored alongside a thread (plan, tier, flag…). */
export type FeedobackContext = Record<string, string | number | boolean | null>;

/** Follows the device unless your app forces its own appearance. */
export const THEMES = ["system", "light", "dark"] as const;
export type FeedobackTheme = (typeof THEMES)[number];

/** Whether a picture of the screen rides along. */
export const SCREENSHOT_MODES = ["automatic", "manual", "off"] as const;
export type FeedobackScreenshots = (typeof SCREENSHOT_MODES)[number];

/**
 * Four corners, named the way a layout is: `end` is the right in English and
 * the left in Arabic. Both native SDKs already speak in start and end, so
 * nothing here has to know which way the device reads.
 */
export const LAUNCHER_CORNERS = ["top-start", "top-end", "bottom-start", "bottom-end"] as const;
export type FeedobackLauncherCorner = (typeof LAUNCHER_CORNERS)[number];

export const LAUNCHER_STYLES = ["icon", "labelled"] as const;
export type FeedobackLauncherStyle = (typeof LAUNCHER_STYLES)[number];

export const LOG_LEVELS = ["silent", "error", "warning", "debug"] as const;
export type FeedobackLogLevel = (typeof LOG_LEVELS)[number];

/** The floating button, off unless you ask for it. */
export type FeedobackLauncherOptions = {
  enabled?: boolean;
  corner?: FeedobackLauncherCorner;
  /** From the safe area, never the raw edge. Density-independent points. */
  offset?: { x: number; y: number };
  style?: FeedobackLauncherStyle;
  /** Dragging snaps it to the nearest edge on release. */
  draggable?: boolean;
  /** Out of the way while somebody is typing. */
  hidesWithKeyboard?: boolean;
};

/**
 * What your app hands {@link Feedoback.start}.
 *
 * Everything the project owner controls — the accent, the word on the
 * launcher, whether stars are asked for — comes from the server and can change
 * without an app release. Everything only the app can know is here.
 */
export type FeedobackOptions = {
  /**
   * The project's public key, `pk_…`. Readable by anyone who unzips the app:
   * it identifies a project and grants nothing.
   */
  projectKey: string;
  /** Where the app is deployed. Defaults to the hosted service. */
  host?: string;
  /**
   * Light and dark. The sheet is native UI inside your app, so this is your
   * decision — the server is never asked and never tells.
   */
  theme?: FeedobackTheme;
  /**
   * Which categories the sheet offers. With one it shows no picker, because a
   * control with a single option is not a choice.
   */
  categories?: FeedobackCategory[];
  screenshots?: FeedobackScreenshots;
  launcher?: FeedobackLauncherOptions;
  /** How loud the SDK is in the console. Quiet unless asked. */
  logLevel?: FeedobackLogLevel;
};

/**
 * The imperative surface, returned by `useFeedback()` and available as
 * `Feedoback`. `identify` and `setContext` are the two calls this shares with
 * `feedoback-react`, and they mean exactly what they mean there.
 */
export type FeedobackApi = {
  /** Call once, at launch. Calling it twice does not stack two launchers. */
  start: (options: FeedobackOptions) => void;
  /** Opens the sheet from a control your app already owns. */
  present: (category?: FeedobackCategory) => void;
  /** Tell the SDK who is signed in. */
  identify: (visitor: FeedobackVisitor) => void;
  /** Attach custom context to threads opened from now on. */
  setContext: (context: FeedobackContext) => void;
  /**
   * Name the screen the visitor is on, which is what feedback is filed under.
   * A path reads best — `"checkout/payment"` — because the dashboard folds
   * identifiers out of it the way it does a website's.
   */
  setScreen: (route: string, title?: string) => void;
  /** Takes the launcher out of the way of a screen that wants none. */
  setLauncherHidden: (hidden: boolean) => void;
  /** Forgets the person and anything attached about them. Call it on sign-out. */
  reset: () => void;
};
