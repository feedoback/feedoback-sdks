import { afterEach, describe, expect, it, vi } from "vitest";

import { DEFAULT_HOST, dispatch, loadWidget, unloadWidget } from "./core";

type MutableWindow = Window & { Feedoback?: unknown };

function reset() {
  delete (window as MutableWindow).Feedoback;
  document.querySelectorAll("script[data-feedoback-sdk]").forEach((node) => node.remove());
}

afterEach(reset);

describe("dispatch", () => {
  it("queues calls before the widget has booted", () => {
    dispatch(window, "identify", { id: "u1" });
    dispatch(window, "open");

    const global = (window as MutableWindow).Feedoback as { q: unknown[] };
    expect(global.q).toEqual([
      ["identify", { id: "u1" }],
      ["open", undefined],
    ]);
  });

  it("forwards to the real handler once the widget is present", () => {
    const identify = vi.fn();
    (window as MutableWindow).Feedoback = { identify };

    dispatch(window, "identify", { email: "a@b.com" });

    expect(identify).toHaveBeenCalledWith({ email: "a@b.com" });
  });

  it("does not clobber an API the widget already installed", () => {
    const existing = { open: vi.fn() };
    (window as MutableWindow).Feedoback = existing;

    dispatch(window, "close"); // no such handler yet -> queued, not replaced

    expect((window as MutableWindow).Feedoback).toBe(existing);
    expect((existing as { q?: unknown[] }).q).toEqual([["close", undefined]]);
  });
});

describe("loadWidget", () => {
  it("injects one script with the attributes the widget reads from its tag", () => {
    loadWidget(window, { projectKey: "pk_abc", version: "1.2.3" });

    const script = document.querySelector<HTMLScriptElement>("script[data-feedoback-sdk]")!;
    expect(script).not.toBeNull();
    expect(script.getAttribute("data-project")).toBe("pk_abc");
    expect(script.getAttribute("data-version")).toBe("1.2.3");
    expect(script.async).toBe(true);
    expect(script.src).toBe(`${DEFAULT_HOST}/widget.js`);
  });

  it("omits data-version when none is given", () => {
    loadWidget(window, { projectKey: "pk_abc" });
    const script = document.querySelector<HTMLScriptElement>("script[data-feedoback-sdk]")!;
    expect(script.hasAttribute("data-version")).toBe(false);
  });

  it("is idempotent for the same project", () => {
    const first = loadWidget(window, { projectKey: "pk_abc" });
    const second = loadWidget(window, { projectKey: "pk_abc" });

    expect(second).toBe(first);
    expect(document.querySelectorAll("script[data-feedoback-sdk]")).toHaveLength(1);
  });

  it("strips a trailing slash from host", () => {
    loadWidget(window, { projectKey: "pk_abc", host: "https://example.com/" });
    const script = document.querySelector<HTMLScriptElement>("script[data-feedoback-sdk]")!;
    expect(script.src).toBe("https://example.com/widget.js");
  });

  it("honours a full scriptSrc override", () => {
    loadWidget(window, { projectKey: "pk_abc", host: "https://ignored.com", scriptSrc: "https://cdn.test/w.js" });
    const script = document.querySelector<HTMLScriptElement>("script[data-feedoback-sdk]")!;
    expect(script.src).toBe("https://cdn.test/w.js");
  });

  it("creates the queue stub before the async script can evaluate", () => {
    loadWidget(window, { projectKey: "pk_abc" });
    expect((window as MutableWindow).Feedoback).toEqual({ q: [] });
  });
});

describe("unloadWidget", () => {
  it("calls the widget's destroy and removes our script", () => {
    const destroy = vi.fn();
    const script = loadWidget(window, { projectKey: "pk_abc" });
    (window as MutableWindow).Feedoback = { destroy };

    unloadWidget(window, script);

    expect(destroy).toHaveBeenCalledOnce();
    expect(document.querySelector("script[data-feedoback-sdk]")).toBeNull();
  });

  it("is safe when there is nothing to remove", () => {
    expect(() => unloadWidget(window, null)).not.toThrow();
  });
});
