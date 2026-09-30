/// Public types for the Feedoback Flutter SDK.
///
/// Where this package and the other three do the same thing they say it the
/// same way: an identity is at most an id, an email and a name, custom context
/// is a flat map of scalars, and both are the app's own claim about who someone
/// is — the server never treats either as authoritative.
///
/// Where they differ, they differ because a phone is not a page. There is no
/// element picker and no screen recording on mobile, so the panel's three ways
/// in reduce to one and [Feedoback.present] opens the sheet directly.
library;

/// What a thread is. The same three the web widget offers.
enum FeedobackCategory {
  feedback,
  bug,
  idea;

  /// The name the server and the native SDKs know it by.
  String get wire => name;
}

/// Follows the device unless your app forces its own appearance.
enum FeedobackTheme { system, light, dark }

/// Whether a picture of the screen rides along.
enum FeedobackScreenshots { automatic, manual, off }

/// Four corners, named the way a layout is: `end` is the right in English and
/// the left in Arabic. Both native SDKs already speak in start and end, so
/// nothing here has to know which way the device reads.
enum FeedobackLauncherCorner {
  topStart('top-start'),
  topEnd('top-end'),
  bottomStart('bottom-start'),
  bottomEnd('bottom-end');

  const FeedobackLauncherCorner(this.wire);
  final String wire;
}

enum FeedobackLauncherStyle { icon, labelled }

/// How loud the SDK is in the console. Quiet unless asked.
enum FeedobackLogLevel { silent, error, warning, debug }

/// Who the current visitor is. Every field is optional; all are the app's claim.
class FeedobackVisitor {
  const FeedobackVisitor({this.id, this.email, this.name, this.userHash});

  final String? id;
  final String? email;
  final String? name;

  /// Hex HMAC-SHA256 of the id (or the email, when there is no id), computed by
  /// **your server** with the project's identity secret. The only part of an
  /// identity a server can check. The secret never belongs in an app: anything
  /// the app holds, the app can forge.
  final String? userHash;
}

/// The floating button, off unless you ask for it.
class FeedobackLauncher {
  const FeedobackLauncher({
    this.enabled = true,
    this.corner,
    this.offset,
    this.style,
    this.draggable,
    this.hidesWithKeyboard,
  });

  final bool enabled;
  final FeedobackLauncherCorner? corner;

  /// From the safe area, never the raw edge. Density-independent points.
  final ({double x, double y})? offset;
  final FeedobackLauncherStyle? style;

  /// Dragging snaps it to the nearest edge on release.
  final bool? draggable;

  /// Out of the way while somebody is typing.
  final bool? hidesWithKeyboard;
}

/// What your app hands [Feedoback.start].
///
/// Everything the project owner controls — the accent, the word on the
/// launcher, whether stars are asked for — comes from the server and can change
/// without an app release. Everything only the app can know is here.
class FeedobackOptions {
  const FeedobackOptions({
    required this.projectKey,
    this.host,
    this.theme,
    this.categories,
    this.screenshots,
    this.launcher,
    this.logLevel,
  });

  /// The project's public key, `pk_…`. Readable by anyone who unzips the app:
  /// it identifies a project and grants nothing.
  final String projectKey;

  /// Where the app is deployed. Defaults to the hosted service.
  final String? host;

  /// Light and dark. The sheet is native UI inside your app, so this is your
  /// decision — the server is never asked and never tells.
  final FeedobackTheme? theme;

  /// Which categories the sheet offers. With one it shows no picker, because a
  /// control with a single option is not a choice.
  final List<FeedobackCategory>? categories;

  final FeedobackScreenshots? screenshots;
  final FeedobackLauncher? launcher;
  final FeedobackLogLevel? logLevel;
}
