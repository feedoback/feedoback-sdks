import { afterEach, describe, expect, it, vi } from "vitest";

import { createFeedbackApi } from "./api";

type MutableWindow = Window & { Feedoback?: unknown };

afterEach(() => {
  delete (window as MutableWindow).Feedoback;
});

describe("createFeedbackApi", () => {
  it("proxies every method to the widget global", () => {
    const handlers = {
      identify: vi.fn(),
      setContext: vi.fn(),
      open: vi.fn(),
      close: vi.fn(),
      toggle: vi.fn(),
      startFeedback: vi.fn(),
      startRecording: vi.fn(),
      destroy: vi.fn(),
    };
    (window as MutableWindow).Feedoback = handlers;
    const api = createFeedbackApi(() => window);

    api.identify({ id: "u1" });
    api.setContext({ plan: "pro" });
    api.open();
    api.close();
    api.toggle();
    api.startFeedback();
    api.startRecording();
    api.destroy();

    expect(handlers.identify).toHaveBeenCalledWith({ id: "u1" });
    expect(handlers.setContext).toHaveBeenCalledWith({ plan: "pro" });
    expect(handlers.open).toHaveBeenCalledOnce();
    expect(handlers.destroy).toHaveBeenCalledOnce();
  });

  it("queues when the widget has not booted yet", () => {
    const api = createFeedbackApi(() => window);
    api.identify({ id: "u2" });

    expect((window as MutableWindow).Feedoback).toEqual({ q: [["identify", { id: "u2" }]] });
  });

  it("is a no-op with no window (server render), never throwing", () => {
    const api = createFeedbackApi(() => undefined);
    expect(() => api.open()).not.toThrow();
  });
});
