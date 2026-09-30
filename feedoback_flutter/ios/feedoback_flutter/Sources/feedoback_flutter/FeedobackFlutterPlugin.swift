import Feedoback
import Flutter
import UIKit

/// The bridge to the iOS SDK, and nothing more.
///
/// Every method here is one call to `Feedoback.shared` with a channel argument
/// turned into something it takes. It holds no state of its own: the SDK is a
/// singleton with the lifetime of the process, which is also what a Flutter hot
/// restart leaves standing, so a plugin that cached anything would be caching a
/// copy of the truth.
///
/// Platform channel calls arrive on the main thread, so unlike the React Native
/// bridge nothing here has to hop.
public class FeedobackFlutterPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "com.feedoback/feedoback", binaryMessenger: registrar.messenger())
        let instance = FeedobackFlutterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let arguments = call.arguments as? [String: Any] ?? [:]

        // Platform channel calls arrive on the main thread, which is where the
        // SDK insists on being. Stated rather than hopped, so `present` still
        // photographs the frame the visitor was looking at when they tapped.
        MainActor.assumeIsolated {
            handle(call.method, arguments)
        }

        result(nil)
    }

    @MainActor
    private func handle(_ method: String, _ arguments: [String: Any]) {
        switch method {
        case "start":
            // Read by the SDK rather than here: React Native hands over the
            // same dictionary, and a second reading of it is a second thing to
            // keep in step.
            if let configuration = FeedobackConfiguration(options: arguments) {
                Feedoback.shared.start(configuration)
            }
        case "present":
            // The SDK's own default when Dart named nothing, rather than a
            // second default chosen here.
            let name = arguments["category"] as? String ?? ""
            if let category = FeedobackCategory(rawValue: name) {
                Feedoback.shared.present(category: category)
            } else {
                Feedoback.shared.present()
            }
        case "identify":
            Feedoback.shared.identify(
                FeedobackVisitor(
                    id: arguments["id"] as? String,
                    email: arguments["email"] as? String,
                    name: arguments["name"] as? String,
                    userHash: arguments["userHash"] as? String))
        case "setContext":
            Feedoback.shared.setContext(FeedobackCustomContext.from(arguments))
        case "setScreen":
            Feedoback.shared.setScreen(
                arguments["route"] as? String ?? "",
                title: arguments["title"] as? String ?? "")
        case "setLauncherHidden":
            Feedoback.shared.setLauncherHidden(arguments["hidden"] as? Bool ?? false)
        case "reset":
            Feedoback.shared.reset()
        case "setRedactedRegions":
            let regions = arguments["regions"] as? [[String: Any]] ?? []
            Feedoback.shared.setRedactedRegions(
                FeedobackFlutterPlugin.windowRects(regions, origin: flutterViewOrigin()))
        default:
            break
        }
    }

    /// Where on screen must never leave the device.
    ///
    /// Flutter draws its whole interface into one view, so there is no view to
    /// mark and Dart sends rectangles instead — in its own logical pixels,
    /// relative to its own view. UIKit points and Flutter's logical pixels are
    /// the same unit, so only the view's own position has to be put back.
    static func windowRects(_ regions: [[String: Any]], origin: CGPoint) -> [CGRect] {
        regions.compactMap { region in
            guard let left = (region["left"] as? NSNumber)?.doubleValue,
                  let top = (region["top"] as? NSNumber)?.doubleValue,
                  let width = (region["width"] as? NSNumber)?.doubleValue,
                  let height = (region["height"] as? NSNumber)?.doubleValue,
                  width > 0, height > 0
            else { return nil }

            return CGRect(
                x: left + origin.x, y: top + origin.y, width: width, height: height)
        }
    }

    /// Flutter's own view does not always start at the top of the window — an
    /// embedded one starts wherever the host put it.
    @MainActor
    private func flutterViewOrigin() -> CGPoint {
        guard let controller = flutterViewController(), let window = controller.view.window
        else { return .zero }
        return controller.view.convert(CGPoint.zero, to: window)
    }

    /// The registrar stopped handing out its view, so it is found the way
    /// anything else in a window is: by walking to it.
    @MainActor
    private func flutterViewController() -> FlutterViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        for scene in scenes {
            for window in scene.windows {
                var next = window.rootViewController
                while let current = next {
                    if let flutter = current as? FlutterViewController { return flutter }
                    next = current.presentedViewController ?? current.children.first
                }
            }
        }
        return nil
    }
}
