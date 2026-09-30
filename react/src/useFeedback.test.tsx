import { render, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { FeedbackWidget } from "./FeedbackWidget";
import { useFeedback } from "./useFeedback";

type MutableWindow = Window & { Feedoback?: unknown };

afterEach(() => {
  delete (window as MutableWindow).Feedoback;
  document.querySelectorAll("script[data-feedoback-sdk]").forEach((n) => n.remove());
});

describe("useFeedback", () => {
  it("returns a stable object across renders", () => {
    const { result, rerender } = renderHook(() => useFeedback());
    const first = result.current;
    rerender();
    expect(result.current).toBe(first);
  });

  it("drives the widget without any provider", () => {
    const startFeedback = vi.fn();
    (window as MutableWindow).Feedoback = { startFeedback };

    const { result } = renderHook(() => useFeedback());
    result.current.startFeedback();

    expect(startFeedback).toHaveBeenCalledOnce();
  });

  it("queues before load, so the widget replays once it boots", () => {
    // No provider, no widget yet: the call lands in the queue for boot.
    const { result } = renderHook(() => useFeedback());
    result.current.startRecording();

    expect((window as MutableWindow).Feedoback).toEqual({ q: [["startRecording", undefined]] });
  });

  it("works alongside <FeedbackWidget> in the same tree", () => {
    function App() {
      const feedback = useFeedback();
      return (
        <>
          <FeedbackWidget projectKey="pk_x" />
          <button onClick={feedback.open}>Open</button>
        </>
      );
    }
    const { getByText } = render(<App />);
    // The widget stub queue exists; clicking queues an open call.
    getByText("Open").click();

    const global = (window as MutableWindow).Feedoback as { q: unknown[] };
    expect(global.q).toContainEqual(["open", undefined]);
  });
});
