/**
 * Public types for the Feedoback React SDK.
 *
 * These mirror the contract of the hosted `widget.js`: an identity is at most
 * an id, an email and a name, and custom context is a flat map of scalars.
 * Everything the customer passes is their own claim about who someone is — the
 * server never treats it as authoritative — so the shapes stay deliberately
 * small.
 */

/** Who the current visitor is. Every field is optional; all are the page's claim. */
export type FeedobackVisitor = {
  id?: string;
  email?: string;
  name?: string;
};

/** Flat, scalar-only context stored alongside a thread (plan, route, tier…). */
export type FeedobackContext = Record<string, string | number | boolean | null>;

/**
 * The imperative surface returned by {@link useFeedback}. It proxies to the
 * hosted widget on `window.Feedoback`, queueing calls made before the script
 * has finished loading and forwarding them once it has.
 */
export type FeedobackApi = {
  /** Tell the widget who is signed in. Safe before the script has loaded. */
  identify: (visitor: FeedobackVisitor) => void;
  /** Attach custom context to threads opened from now on. */
  setContext: (context: FeedobackContext) => void;
  /** Open the panel on its menu. */
  open: () => void;
  /** Close the panel. */
  close: () => void;
  /** Toggle the panel. */
  toggle: () => void;
  /** Start element-pointing feedback (the picker). */
  startFeedback: () => void;
  /** Open the panel straight into screen recording. */
  startRecording: () => void;
  /** Remove the widget from the page. Call on sign-out in a single-page app. */
  destroy: () => void;
};

/** Configuration shared by {@link FeedbackWidget} and {@link FeedbackProvider}. */
export type FeedbackWidgetProps = {
  /**
   * The project's public key, `pk_…`. It identifies the project on a page; it
   * is public, not a secret, and is used exactly as given.
   */
  projectKey: string;
  /**
   * Origin that serves `widget.js`, without a trailing slash.
   * Defaults to `https://feedoback.com`.
   */
  host?: string;
  /**
   * Your release string, stored with every thread so feedback is filed under
   * the build it came from. Wire it from your build (e.g. a package version).
   */
  version?: string;
  /**
   * Full override of the script URL. Takes precedence over {@link host}; use it
   * for self-hosted or proxied deployments.
   */
  scriptSrc?: string;
  /** Identify this visitor as soon as the widget loads, and whenever it changes. */
  visitor?: FeedobackVisitor;
  /** Attach this context as soon as the widget loads, and whenever it changes. */
  context?: FeedobackContext;
  /**
   * Remove the widget when the component unmounts. Off by default: the widget
   * is normally mounted once for the app's lifetime.
   */
  destroyOnUnmount?: boolean;
  /** Called once `widget.js` has loaded. */
  onLoad?: () => void;
  /** Called if `widget.js` fails to load (blocked, offline, wrong host). */
  onError?: () => void;
};
