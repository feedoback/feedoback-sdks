import { type ReactNode } from "react";

import FeedobackRedactView from "./spec/FeedobackRedactViewNativeComponent";
import type { ViewProps } from "react-native";

export type FeedobackRedactProps = ViewProps & {
  /** Off for a view that is only sometimes sensitive. */
  redacted?: boolean;
  children?: ReactNode;
};

/**
 * Wraps anything whose contents must never leave the device — a card number,
 * an address, a medical record. It lays out exactly as a `View` does and
 * changes nothing on screen; what it changes is the screenshot, where its
 * frame is painted over before the bitmap exists.
 *
 * ```tsx
 * <FeedobackRedact>
 *   <Text>{card.number}</Text>
 * </FeedobackRedact>
 * ```
 *
 * Secure text inputs need no wrapper: they are found on their own, because
 * forgetting one of those is the expensive mistake. This is for everything
 * else.
 */
export function FeedobackRedact({ redacted = true, children, ...props }: FeedobackRedactProps) {
  return (
    <FeedobackRedactView {...props} redacted={redacted}>
      {children}
    </FeedobackRedactView>
  );
}
