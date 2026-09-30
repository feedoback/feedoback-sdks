import { describe, expect, it } from "vitest";

import {
  normalizeCategory,
  normalizeContext,
  normalizeLauncher,
  normalizeStartOptions,
  normalizeVisitor,
} from "./options";

describe("normalizeStartOptions", () => {
  it("keeps only what the app actually set", () => {
    expect(normalizeStartOptions({ projectKey: "pk_live_1" })).toEqual({
      projectKey: "pk_live_1",
    });
  });

  it("carries every option the native SDKs understand", () => {
    expect(
      normalizeStartOptions({
        projectKey: "pk_live_1",
        host: "https://feedback.acme.com",
        theme: "dark",
        categories: ["bug", "idea"],
        screenshots: "off",
        logLevel: "debug",
      }),
    ).toEqual({
      projectKey: "pk_live_1",
      host: "https://feedback.acme.com",
      theme: "dark",
      categories: ["bug", "idea"],
      screenshots: "off",
      logLevel: "debug",
    });
  });

  /** A value we do not know is left out so native falls back to its own
   *  default, rather than overwritten with a default chosen twice. */
  it("leaves out anything the native side would not recognise", () => {
    const options = normalizeStartOptions({
      projectKey: "pk_live_1",
      // @ts-expect-error the point is what a JavaScript caller can pass
      theme: "midnight",
      // @ts-expect-error same
      screenshots: 3,
      // @ts-expect-error same
      categories: ["bug", "support", null],
    });

    expect(options).toEqual({ projectKey: "pk_live_1", categories: ["bug"] });
  });

  it("drops a trailing slash, which would double every path", () => {
    expect(normalizeStartOptions({ projectKey: "pk_1", host: "https://acme.com//" })?.host).toBe(
      "https://acme.com",
    );
  });

  it("refuses to start without a project key", () => {
    expect(normalizeStartOptions({ projectKey: "   " })).toBeNull();
    // @ts-expect-error a JavaScript caller can leave it out entirely
    expect(normalizeStartOptions({})).toBeNull();
    // @ts-expect-error and can pass nothing at all
    expect(normalizeStartOptions(undefined)).toBeNull();
  });

  it("keeps a category list free of repeats", () => {
    expect(normalizeStartOptions({ projectKey: "pk_1", categories: ["bug", "bug"] })).toEqual({
      projectKey: "pk_1",
      categories: ["bug"],
    });
  });

  it("leaves out a list that ends up empty", () => {
    // @ts-expect-error a JavaScript caller can pass names we do not know
    expect(normalizeStartOptions({ projectKey: "pk_1", categories: ["support"] })).toEqual({
      projectKey: "pk_1",
    });
  });
});

describe("normalizeLauncher", () => {
  it("carries the corner, the style and the switches", () => {
    expect(
      normalizeLauncher({
        enabled: true,
        corner: "bottom-start",
        style: "labelled",
        draggable: false,
        hidesWithKeyboard: false,
      }),
    ).toEqual({
      enabled: true,
      corner: "bottom-start",
      style: "labelled",
      draggable: false,
      hidesWithKeyboard: false,
    });
  });

  /** Half an offset would put the button somewhere nobody asked for on the
   *  other axis. */
  it("takes an offset only when both numbers are there", () => {
    expect(normalizeLauncher({ enabled: true, offset: { x: 16, y: 24 } })?.offset).toEqual({
      x: 16,
      y: 24,
    });
    expect(normalizeLauncher({ enabled: true, offset: { x: 16 } })?.offset).toBeUndefined();
    expect(
      normalizeLauncher({ enabled: true, offset: { x: Number.NaN, y: 24 } })?.offset,
    ).toBeUndefined();
  });

  it("is left out entirely when nothing usable was passed", () => {
    expect(normalizeLauncher(undefined)).toBeUndefined();
    expect(normalizeLauncher({})).toBeUndefined();
    expect(normalizeLauncher({ corner: "middle" })).toBeUndefined();
  });
});

describe("normalizeCategory", () => {
  it("takes the three the widget offers", () => {
    expect(normalizeCategory("bug")).toBe("bug");
    expect(normalizeCategory("idea")).toBe("idea");
    expect(normalizeCategory("feedback")).toBe("feedback");
  });

  /** Retired when the widget stopped offering a support door. The server
   *  refuses one, so this must not quietly become feedback. */
  it("does not know support any more", () => {
    expect(normalizeCategory("support")).toBeNull();
    expect(normalizeCategory(undefined)).toBeNull();
  });
});

describe("normalizeVisitor", () => {
  it("takes the four fields an identity can have", () => {
    expect(
      normalizeVisitor({ id: "u_1", email: "ada@example.com", name: "Ada", userHash: "abc" }),
    ).toEqual({ id: "u_1", email: "ada@example.com", name: "Ada", userHash: "abc" });
  });

  /** What a signed-out app passes without meaning to. An empty id would name
   *  somebody. */
  it("drops an empty field rather than sending it", () => {
    expect(normalizeVisitor({ id: "", email: "  ", name: "Ada" })).toEqual({ name: "Ada" });
  });

  it("keeps nothing that is not a string", () => {
    // @ts-expect-error the point is what a JavaScript caller can pass
    expect(normalizeVisitor({ id: 42, email: { address: "x" }, name: null })).toEqual({});
  });
});

describe("normalizeContext", () => {
  it("takes the scalars the server stores", () => {
    expect(normalizeContext({ plan: "pro", seats: 12, trial: false, invitedBy: null })).toEqual({
      plan: "pro",
      seats: 12,
      trial: false,
      invitedBy: null,
    });
  });

  /** Stringifying one would file `[object Object]` under somebody's feedback. */
  it("drops what has no shape the server stores", () => {
    expect(
      // @ts-expect-error the point is what a JavaScript caller can pass
      normalizeContext({ tags: ["a"], nested: { a: 1 }, at: new Date(), plan: "pro" }),
    ).toEqual({ plan: "pro" });
  });

  /** A function or a symbol is what takes a bridge down rather than a thread. */
  it("drops what the bridge cannot carry at all", () => {
    // @ts-expect-error the point is what a JavaScript caller can pass
    expect(normalizeContext({ onSave: () => {}, tag: Symbol("x"), plan: "pro" })).toEqual({
      plan: "pro",
    });
  });

  it("drops undefined, which is not a value the server has", () => {
    // @ts-expect-error the point is what a JavaScript caller can pass
    expect(normalizeContext({ plan: undefined, seats: 12 })).toEqual({ seats: 12 });
  });

  it("drops a number that is not finite", () => {
    expect(normalizeContext({ ratio: Number.POSITIVE_INFINITY, seats: 12 })).toEqual({ seats: 12 });
    expect(normalizeContext({ ratio: Number.NaN })).toEqual({});
  });

  it("answers with nothing for something that is not an object", () => {
    // @ts-expect-error the point is what a JavaScript caller can pass
    expect(normalizeContext(["plan"])).toEqual({});
    // @ts-expect-error same
    expect(normalizeContext(null)).toEqual({});
  });
});
