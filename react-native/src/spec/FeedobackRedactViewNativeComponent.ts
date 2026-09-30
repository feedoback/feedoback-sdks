/**
 * A view whose contents never leave the device.
 *
 * A real native view rather than a marker prop on an ordinary one, because
 * redaction is a property of the view the screenshot pass walks, and the
 * screenshot pass is native. Wrapping is what makes it survive a re-render:
 * the native SDK is handed the view once, when it is created, and the mark
 * lives on the view rather than in a list of tags that a reconciler can
 * invalidate underneath it.
 */
import type { ViewProps } from "react-native";
import type { WithDefault } from "react-native/Libraries/Types/CodegenTypes";
import codegenNativeComponent from "react-native/Libraries/Utilities/codegenNativeComponent";

export interface NativeProps extends ViewProps {
  /** Off for a view that is only sometimes sensitive. */
  redacted?: WithDefault<boolean, true>;
}

export default codegenNativeComponent<NativeProps>("FeedobackRedactView");
