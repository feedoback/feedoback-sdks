import 'dart:ui' show Rect;

import 'package:flutter/foundation.dart';

import 'channel.dart';
import 'options.dart';
import 'types.dart';

/// The whole SDK, from an app's point of view.
///
/// A thin, total shell over the two native SDKs, with the same four calls they
/// have: [start] once at launch, [present] from a control the app already owns,
/// [identify] when the app knows who this is, and [reset] on sign-out.
class Feedoback {
  Feedoback._();

  /// Swapped for a recorder in tests. There is nothing else to inject: the
  /// class holds a started flag and forwards.
  @visibleForTesting
  static FeedobackChannel channel = MethodChannelFeedoback();

  static bool _started = false;

  /// Put back between tests, along with the channel.
  @visibleForTesting
  static void resetForTesting() => _started = false;

  /// Call once, at launch. Calling it twice does not stack two launchers, the
  /// way a snippet pasted twice must not stack two on a page.
  ///
  /// A project key that is missing is the one thing native cannot fall back
  /// from, because it is the whole configuration.
  static Future<void> start(FeedobackOptions options) async {
    if (_started) return;
    if (options.projectKey.trim().isEmpty) return;
    _started = true;
    await channel.invoke('start', encodeOptions(options));
  }

  /// Opens the sheet from a control your app already owns: a Settings row, a
  /// menu item.
  ///
  /// Passing no category leaves the choice to the native SDK, which falls back
  /// the same way it does for a Swift app that passed nothing.
  static Future<void> present([FeedobackCategory? category]) =>
      channel.invoke('present', {'category': category?.wire ?? ''});

  /// Who the app says the visitor is.
  ///
  /// Declared, never authoritative — it is the app's own word. Pass a
  /// `userHash` when the project asks for signatures and your server has
  /// computed one; that is the only part of it the server can check.
  static Future<void> identify(FeedobackVisitor visitor) =>
      channel.invoke('identify', encodeVisitor(visitor));

  /// Attaches custom context to every thread opened from now on: the plan
  /// someone is on, the flag they have, the tier they bought. At most thirty
  /// keys, and what exceeds them is dropped rather than costing the visitor
  /// their feedback.
  static Future<void> setContext(Map<String, Object?> context) =>
      channel.invoke('setContext', encodeContext(context));

  /// Names the screen the visitor is on, which is what feedback is filed
  /// under. A path reads best — `checkout/payment` — because the dashboard
  /// folds identifiers out of it the way it does a website's.
  ///
  /// A screen with no name is not a screen: sending one would file every
  /// thread written there under the app's root.
  static Future<void> setScreen(String route, {String title = ''}) async {
    if (route.trim().isEmpty) return;
    await channel.invoke('setScreen', {
      'route': route.trim(),
      'title': title.trim(),
    });
  }

  /// Takes the launcher out of the way of a screen that wants none: a video
  /// player, a camera.
  static Future<void> setLauncherHidden(bool hidden) =>
      channel.invoke('setLauncherHidden', {'hidden': hidden});

  /// Forgets the person and anything attached about them; keeps the device.
  /// Call it on sign-out.
  static Future<void> reset() => channel.invoke('reset');

  /// Where on screen must never leave the device, in logical pixels.
  ///
  /// Flutter draws its whole interface into one native view, so there is no
  /// view to hand the native SDK and a rectangle is the only thing that can be
  /// said. [FeedobackRedact] keeps this in step with where its widgets are;
  /// an app that draws its own sensitive thing on a canvas can call it too.
  ///
  /// Replaces whatever was marked before: the caller is the only thing that
  /// knows where all of them are.
  static Future<void> setRedactedRegions(List<Rect> regions) =>
      channel.invoke('setRedactedRegions', {
        'regions': [
          for (final region in regions)
            if (region.width > 0 && region.height > 0)
              {
                'left': region.left,
                'top': region.top,
                'width': region.width,
                'height': region.height,
              },
        ],
      });
}
