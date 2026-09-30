import { beforeEach, describe, expect, it, vi } from "vitest";

import { createFeedoback } from "./Feedoback";
import type { Spec } from "./spec/NativeFeedoback";

/** Stands in for the native module, and records what crossed the bridge. */
function stub() {
  return {
    start: vi.fn(),
    present: vi.fn(),
    identify: vi.fn(),
    setContext: vi.fn(),
    setScreen: vi.fn(),
    setLauncherHidden: vi.fn(),
    reset: vi.fn(),
    getConstants: vi.fn(),
    // The satisfies is the point: a method added to the spec and not stubbed
    // here fails to compile rather than passing a test that never called it.
  } satisfies Record<keyof Spec, unknown> as unknown as Spec &
    Record<string, ReturnType<typeof vi.fn>>;
}

describe("start", () => {
  it("hands the native side what the app configured", () => {
    const native = stub();
    createFeedoback(native).start({ projectKey: "pk_1", launcher: { enabled: true } });

    expect(native.start).toHaveBeenCalledWith({
      projectKey: "pk_1",
      launcher: { enabled: true },
    });
  });

  /** The same rule as a snippet pasted twice on a page. */
  it("does not start twice", () => {
    const native = stub();
    const feedoback = createFeedoback(native);

    feedoback.start({ projectKey: "pk_1" });
    feedoback.start({ projectKey: "pk_2" });

    expect(native.start).toHaveBeenCalledTimes(1);
    expect(native.start).toHaveBeenCalledWith({ projectKey: "pk_1" });
  });

  it("says what is wrong and goes quiet without a project key", () => {
    const native = stub();
    const said: string[] = [];
    createFeedoback(native, (message) => said.push(message)).start({ projectKey: "" });

    expect(native.start).not.toHaveBeenCalled();
    expect(said).toEqual(["[Feedoback] start() needs a project key; Feedoback is off"]);
  });

  /** A failed start must not leave the SDK looking started, or the app's own
   *  retry after fixing the key would be ignored. */
  it("can be started properly after a bad first attempt", () => {
    const native = stub();
    const feedoback = createFeedoback(native, () => {});

    feedoback.start({ projectKey: "" });
    feedoback.start({ projectKey: "pk_1" });

    expect(native.start).toHaveBeenCalledWith({ projectKey: "pk_1" });
  });
});

describe("with no native module", () => {
  /** A pod that was never installed, a Gradle sync that never ran, or
   *  react-native-web, where there is no native side to build in. */
  it("says so once and never throws", () => {
    const said: string[] = [];
    const feedoback = createFeedoback(null, (message) => said.push(message));

    expect(() => {
      feedoback.start({ projectKey: "pk_1" });
      feedoback.present();
      feedoback.identify({ id: "u_1" });
      feedoback.setContext({ plan: "pro" });
      feedoback.setScreen("cart");
      feedoback.setLauncherHidden(true);
      feedoback.reset();
    }).not.toThrow();

    expect(said).toHaveLength(1);
    expect(said[0]).toContain("pod install");
  });
});

describe("the calls", () => {
  let native: ReturnType<typeof stub>;
  let feedoback: ReturnType<typeof createFeedoback>;

  beforeEach(() => {
    native = stub();
    feedoback = createFeedoback(native, () => {});
    feedoback.start({ projectKey: "pk_1" });
  });

  it("opens the sheet on a category, or leaves the choice to native", () => {
    feedoback.present("bug");
    expect(native.present).toHaveBeenCalledWith("bug");

    feedoback.present();
    expect(native.present).toHaveBeenLastCalledWith("");
  });

  it("sends an identity with the empty fields taken out", () => {
    feedoback.identify({ id: "u_1", email: "", name: " Ada " });
    expect(native.identify).toHaveBeenCalledWith({ id: "u_1", name: "Ada" });
  });

  it("sends context the bridge can carry", () => {
    // @ts-expect-error the point is what a JavaScript caller can pass
    feedoback.setContext({ plan: "pro", onSave: () => {} });
    expect(native.setContext).toHaveBeenCalledWith({ plan: "pro" });
  });

  it("names the screen, with a title only when there is one", () => {
    feedoback.setScreen(" checkout/payment ", " Payment ");
    expect(native.setScreen).toHaveBeenCalledWith("checkout/payment", "Payment");

    feedoback.setScreen("cart");
    expect(native.setScreen).toHaveBeenLastCalledWith("cart", "");
  });

  /** A screen with no name is not a screen. Sending "" would file every
   *  thread written there under the app's root. */
  it("ignores a screen with no name", () => {
    feedoback.setScreen("   ");
    expect(native.setScreen).not.toHaveBeenCalled();
  });

  it("hides and shows the launcher", () => {
    feedoback.setLauncherHidden(true);
    expect(native.setLauncherHidden).toHaveBeenCalledWith(true);
  });

  it("forgets the person on sign-out", () => {
    feedoback.reset();
    expect(native.reset).toHaveBeenCalled();
  });
});
