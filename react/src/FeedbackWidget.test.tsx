import { render } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { FeedbackWidget } from "./FeedbackWidget";

type MutableWindow = Window & { Feedoback?: unknown };

function scripts() {
  return document.querySelectorAll<HTMLScriptElement>("script[data-feedoback-sdk]");
}

afterEach(() => {
  delete (window as MutableWindow).Feedoback;
  scripts().forEach((node) => node.remove());
});

describe("<FeedbackWidget>", () => {
  it("renders nothing and injects the script", () => {
    const { container } = render(<FeedbackWidget projectKey="pk_x" host="https://h.test" version="9.9" />);

    expect(container).toBeEmptyDOMElement();
    const script = scripts()[0]!;
    expect(script.src).toBe("https://h.test/widget.js");
    expect(script.getAttribute("data-project")).toBe("pk_x");
    expect(script.getAttribute("data-version")).toBe("9.9");
  });

  it("injects only one script under Strict Mode's double mount", () => {
    render(
      <StrictMode>
        <FeedbackWidget projectKey="pk_x" />
      </StrictMode>,
    );
    expect(scripts()).toHaveLength(1);
  });

  it("queues the visitor and context so the widget replays them on boot", () => {
    render(<FeedbackWidget projectKey="pk_x" visitor={{ id: "u1", email: "a@b.com" }} context={{ plan: "pro" }} />);

    expect((window as MutableWindow).Feedoback).toEqual({
      q: [
        ["identify", { id: "u1", email: "a@b.com" }],
        ["setContext", { plan: "pro" }],
      ],
    });
  });

  it("re-identifies when the visitor changes, without reloading the script", () => {
    const identify = vi.fn();
    const { rerender } = render(<FeedbackWidget projectKey="pk_x" visitor={{ id: "u1" }} />);

    // Simulate the widget booting: a real handler replaces the queue stub.
    (window as MutableWindow).Feedoback = { identify };
    rerender(<FeedbackWidget projectKey="pk_x" visitor={{ id: "u2" }} />);

    expect(identify).toHaveBeenCalledWith({ id: "u2" });
    expect(scripts()).toHaveLength(1);
  });

  it("leaves the widget in place on unmount by default", () => {
    const destroy = vi.fn();
    const { unmount } = render(<FeedbackWidget projectKey="pk_x" />);
    (window as MutableWindow).Feedoback = { destroy };

    unmount();

    expect(destroy).not.toHaveBeenCalled();
    expect(scripts()).toHaveLength(1);
  });

  it("tears the widget down on unmount when asked", () => {
    const destroy = vi.fn();
    const { unmount } = render(<FeedbackWidget projectKey="pk_x" destroyOnUnmount />);
    (window as MutableWindow).Feedoback = { destroy };

    unmount();

    expect(destroy).toHaveBeenCalledOnce();
    expect(scripts()).toHaveLength(0);
  });

  it("reports a load failure through onError", () => {
    const onError = vi.fn();
    render(<FeedbackWidget projectKey="pk_x" onError={onError} />);

    scripts()[0]!.dispatchEvent(new Event("error"));

    expect(onError).toHaveBeenCalledOnce();
  });
});
