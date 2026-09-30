import { Feedoback } from "./Feedoback";
import type { FeedobackApi } from "./types";

/**
 * Returns the imperative API: `present`, `identify`, `setContext`, `setScreen`
 * and the rest. The object is stable across renders and works anywhere the SDK
 * has been started — no provider required, because it talks to the native
 * module directly.
 *
 * ```tsx
 * const feedback = useFeedback();
 * <Button title="Send feedback" onPress={() => feedback.present()} />
 * ```
 *
 * The same hook name as `feedoback-react`, and `identify` and `setContext`
 * mean exactly what they mean there.
 */
export function useFeedback(): FeedobackApi {
  return Feedoback;
}
