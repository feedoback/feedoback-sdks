import Feedoback
import Foundation
import UIKit

/// The Swift half of the bridge.
///
/// The SDK's surface is Swift enums and structs, none of which Objective-C can
/// see, and a Turbo Module has to be Objective-C++ to conform to the generated
/// protocol. So the dictionary that crossed from JavaScript is turned into the
/// SDK's own types here, where those types exist, and the Objective-C++ module
/// is left with nothing but seven forwarding calls.
///
/// Nothing here invents a default. A key that is absent or unrecognised leaves
/// the SDK's own default in place, which is the one a plain-Swift app gets.
@objc(FeedobackBridge)
public final class FeedobackBridge: NSObject {
    @objc public static func start(_ options: NSDictionary) {
        // Read by the SDK rather than here: Flutter hands over the same
        // dictionary, and a second reading of it is a second thing to keep in
        // step.
        guard let options = options as? [String: Any],
              let configuration = FeedobackConfiguration(options: options)
        else { return }

        DispatchQueue.main.async { Feedoback.shared.start(configuration) }
    }

    @objc public static func present(_ name: String) {
        let chosen = FeedobackCategory(rawValue: name)
        DispatchQueue.main.async {
            // The SDK's own default when JavaScript named nothing, rather than
            // a second default chosen here.
            if let chosen {
                Feedoback.shared.present(category: chosen)
            } else {
                Feedoback.shared.present()
            }
        }
    }

    @objc public static func identify(_ visitor: NSDictionary) {
        let named = FeedobackVisitor(
            id: visitor.string("id"),
            email: visitor.string("email"),
            name: visitor.string("name"),
            userHash: visitor.string("userHash"))
        DispatchQueue.main.async { Feedoback.shared.identify(named) }
    }

    @objc public static func setContext(_ context: NSDictionary) {
        let values = FeedobackCustomContext.from((context as? [String: Any]) ?? [:])
        DispatchQueue.main.async { Feedoback.shared.setContext(values) }
    }

    @objc public static func setScreen(_ route: String, title: String) {
        DispatchQueue.main.async { Feedoback.shared.setScreen(route, title: title) }
    }

    @objc public static func setLauncherHidden(_ hidden: Bool) {
        DispatchQueue.main.async { Feedoback.shared.setLauncherHidden(hidden) }
    }

    @objc public static func reset() {
        DispatchQueue.main.async { Feedoback.shared.reset() }
    }

    /// Marks a view React created. Called from the redact component, which is
    /// the only thing that knows which view it drew.
    ///
    /// Main-actor rather than hopping like the rest: Fabric mounts and updates
    /// props on the main thread, and a hop would mark the view a frame after
    /// it appeared — which is a frame in which a screenshot would catch it.
    @MainActor
    @objc public static func redact(_ view: UIView, redacted: Bool) {
        if redacted {
            Feedoback.shared.redact(view)
        } else {
            Feedoback.shared.unredact(view)
        }
    }
}

private extension NSDictionary {
    /// A non-empty string, or nothing so the caller falls back.
    func string(_ key: String) -> String? {
        guard let value = self[key] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
