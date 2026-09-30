import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Where a call goes.
///
/// One implementation talks to the platform; a test passes its own and reads
/// what crossed. That seam is what lets the whole surface be exercised without
/// a phone, the same way the React Native SDK is built over its native module
/// rather than reaching for it.
abstract class FeedobackChannel {
  Future<void> invoke(String method, [Object? arguments]);
}

/// The platform channel, and the promise that nothing it does throws.
///
/// Every entry point is total, the way the native SDKs are: a bad key, no
/// network, a project that has not opened its mobile channel — none of it
/// throws into someone's `build`. It says so once and goes quiet, which is the
/// native reading of *a misconfigured widget removes itself*.
class MethodChannelFeedoback implements FeedobackChannel {
  MethodChannelFeedoback({
    this.channel = const MethodChannel('com.feedoback/feedoback'),
    this.report = _print,
  });

  final MethodChannel channel;

  /// Where a warning goes. The console, unless a test wants to read them.
  final void Function(String message) report;

  /// Said once and not again. A warning on every frame of a scroll is noise a
  /// developer scrolls past, which is how the thing it was warning about gets
  /// missed.
  final Set<String> _said = <String>{};

  static void _print(String message) => debugPrint(message);

  @override
  Future<void> invoke(String method, [Object? arguments]) async {
    try {
      await channel.invokeMethod<void>(method, arguments);
    } on MissingPluginException {
      _warn(
        'the plugin is not in this build. Rebuild the app — a hot restart is '
        'not enough after adding it — and check it runs on iOS or Android.',
      );
    } on PlatformException catch (error) {
      _warn('$method failed: ${error.message ?? error.code}');
    }
  }

  void _warn(String message) {
    if (!_said.add(message)) return;
    report('[Feedoback] $message');
  }
}
