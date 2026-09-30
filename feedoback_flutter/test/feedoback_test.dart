import 'dart:ui' show Rect;

import 'package:feedoback_flutter/feedoback_flutter.dart';
import 'package:flutter_test/flutter_test.dart';

/// Stands in for the platform, and records what crossed.
class RecordingChannel implements FeedobackChannel {
  final List<({String method, Object? arguments})> calls = [];

  @override
  Future<void> invoke(String method, [Object? arguments]) async {
    calls.add((method: method, arguments: arguments));
  }

  Map<String, Object?> argumentsOf(String method) =>
      calls.firstWhere((call) => call.method == method).arguments
          as Map<String, Object?>;

  bool sent(String method) => calls.any((call) => call.method == method);
}

void main() {
  late RecordingChannel platform;

  setUp(() {
    platform = RecordingChannel();
    Feedoback.channel = platform;
    Feedoback.resetForTesting();
  });

  group('start', () {
    test('hands the native side what the app configured', () async {
      await Feedoback.start(
        const FeedobackOptions(
          projectKey: 'pk_1',
          launcher: FeedobackLauncher(),
        ),
      );

      expect(platform.argumentsOf('start'), {
        'projectKey': 'pk_1',
        'launcher': {'enabled': true},
      });
    });

    /// The same rule as a snippet pasted twice on a page.
    test('does not start twice', () async {
      await Feedoback.start(const FeedobackOptions(projectKey: 'pk_1'));
      await Feedoback.start(const FeedobackOptions(projectKey: 'pk_2'));

      expect(platform.calls.where((c) => c.method == 'start'), hasLength(1));
      expect(platform.argumentsOf('start')['projectKey'], 'pk_1');
    });

    test('goes quiet without a project key', () async {
      await Feedoback.start(const FeedobackOptions(projectKey: '   '));
      expect(platform.sent('start'), isFalse);
    });

    /// A failed start must not leave the SDK looking started, or the app's own
    /// retry after fixing the key would be ignored.
    test('can be started properly after a bad first attempt', () async {
      await Feedoback.start(const FeedobackOptions(projectKey: ''));
      await Feedoback.start(const FeedobackOptions(projectKey: 'pk_1'));

      expect(platform.argumentsOf('start')['projectKey'], 'pk_1');
    });
  });

  group('the calls', () {
    test(
      'opens the sheet on a category, or leaves the choice to native',
      () async {
        await Feedoback.present(FeedobackCategory.bug);
        expect(platform.argumentsOf('present'), {'category': 'bug'});

        platform.calls.clear();
        await Feedoback.present();
        expect(platform.argumentsOf('present'), {'category': ''});
      },
    );

    test('sends an identity with the empty fields taken out', () async {
      await Feedoback.identify(
        const FeedobackVisitor(id: 'u_1', email: '', name: ' Ada '),
      );
      expect(platform.argumentsOf('identify'), {'id': 'u_1', 'name': 'Ada'});
    });

    test('sends context the channel can carry', () async {
      await Feedoback.setContext({
        'plan': 'pro',
        'tags': ['a'],
      });
      expect(platform.argumentsOf('setContext'), {'plan': 'pro'});
    });

    test('names the screen, with a title only when there is one', () async {
      await Feedoback.setScreen(' checkout/payment ', title: ' Payment ');
      expect(platform.argumentsOf('setScreen'), {
        'route': 'checkout/payment',
        'title': 'Payment',
      });
    });

    /// A screen with no name is not a screen. Sending one would file every
    /// thread written there under the app's root.
    test('ignores a screen with no name', () async {
      await Feedoback.setScreen('   ');
      expect(platform.sent('setScreen'), isFalse);
    });

    test('hides the launcher, and forgets the person', () async {
      await Feedoback.setLauncherHidden(true);
      await Feedoback.reset();

      expect(platform.argumentsOf('setLauncherHidden'), {'hidden': true});
      expect(platform.sent('reset'), isTrue);
    });
  });

  group('redacted regions', () {
    test('sends them in logical pixels', () async {
      await Feedoback.setRedactedRegions([
        const Rect.fromLTWH(10, 20, 100, 40),
      ]);

      expect(platform.argumentsOf('setRedactedRegions'), {
        'regions': [
          {'left': 10.0, 'top': 20.0, 'width': 100.0, 'height': 40.0},
        ],
      });
    });

    /// A widget that has not been laid out reports a zero rectangle, and
    /// filling one paints nothing while still costing a pass over the bitmap.
    test('drops a region with nothing in it', () async {
      await Feedoback.setRedactedRegions([
        const Rect.fromLTWH(10, 20, 0, 40),
        const Rect.fromLTWH(10, 20, 100, 40),
      ]);

      expect(
        (platform.argumentsOf('setRedactedRegions')['regions'] as List),
        hasLength(1),
      );
    });

    /// Replacing rather than adding: an empty list is how an app says nothing
    /// on this screen is sensitive any more.
    test('an empty list is still sent', () async {
      await Feedoback.setRedactedRegions([]);
      expect(platform.argumentsOf('setRedactedRegions'), {'regions': []});
    });
  });
}
