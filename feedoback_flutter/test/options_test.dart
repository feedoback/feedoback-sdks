import 'package:feedoback_flutter/feedoback_flutter.dart';
import 'package:feedoback_flutter/src/options.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('encodeOptions', () {
    test('keeps only what the app actually set', () {
      expect(encodeOptions(const FeedobackOptions(projectKey: 'pk_live_1')), {
        'projectKey': 'pk_live_1',
      });
    });

    test('carries every option the native SDKs understand', () {
      expect(
        encodeOptions(
          const FeedobackOptions(
            projectKey: 'pk_live_1',
            host: 'https://feedback.acme.com',
            theme: FeedobackTheme.dark,
            categories: [FeedobackCategory.bug, FeedobackCategory.idea],
            screenshots: FeedobackScreenshots.off,
            logLevel: FeedobackLogLevel.debug,
          ),
        ),
        {
          'projectKey': 'pk_live_1',
          'host': 'https://feedback.acme.com',
          'theme': 'dark',
          'screenshots': 'off',
          'logLevel': 'debug',
          'categories': ['bug', 'idea'],
        },
      );
    });

    test('drops a trailing slash, which would double every path', () {
      final encoded = encodeOptions(
        const FeedobackOptions(projectKey: 'pk_1', host: 'https://acme.com//'),
      );
      expect(encoded['host'], 'https://acme.com');
    });

    test('leaves out a host that is only whitespace', () {
      final encoded = encodeOptions(
        const FeedobackOptions(projectKey: 'pk_1', host: '   '),
      );
      expect(encoded.containsKey('host'), isFalse);
    });

    test('keeps a category list free of repeats', () {
      final encoded = encodeOptions(
        const FeedobackOptions(
          projectKey: 'pk_1',
          categories: [FeedobackCategory.bug, FeedobackCategory.bug],
        ),
      );
      expect(encoded['categories'], ['bug']);
    });

    test('leaves out a category list with nothing in it', () {
      final encoded = encodeOptions(
        const FeedobackOptions(projectKey: 'pk_1', categories: []),
      );
      expect(encoded.containsKey('categories'), isFalse);
    });
  });

  group('encodeLauncher', () {
    test('carries the corner, the style and the switches', () {
      expect(
        encodeLauncher(
          const FeedobackLauncher(
            corner: FeedobackLauncherCorner.bottomStart,
            style: FeedobackLauncherStyle.labelled,
            draggable: false,
            hidesWithKeyboard: false,
          ),
        ),
        {
          'enabled': true,
          'corner': 'bottom-start',
          'style': 'labelled',
          'draggable': false,
          'hidesWithKeyboard': false,
        },
      );
    });

    /// Half an offset would put the button somewhere nobody asked for on the
    /// other axis.
    test('takes an offset only when both numbers are finite', () {
      expect(
        encodeLauncher(
          const FeedobackLauncher(offset: (x: 16, y: 24)),
        )['offset'],
        {'x': 16.0, 'y': 24.0},
      );
      expect(
        encodeLauncher(const FeedobackLauncher(offset: (x: double.nan, y: 24)))
            .containsKey('offset'),
        isFalse,
      );
    });

    /// A launcher passed at all is one the app asked for.
    test('is enabled unless the app says otherwise', () {
      expect(encodeLauncher(const FeedobackLauncher())['enabled'], isTrue);
      expect(
        encodeLauncher(const FeedobackLauncher(enabled: false))['enabled'],
        isFalse,
      );
    });
  });

  group('encodeVisitor', () {
    test('takes the four fields an identity can have', () {
      expect(
        encodeVisitor(
          const FeedobackVisitor(
            id: 'u_1',
            email: 'ada@example.com',
            name: 'Ada',
            userHash: 'abc',
          ),
        ),
        {
          'id': 'u_1',
          'email': 'ada@example.com',
          'name': 'Ada',
          'userHash': 'abc',
        },
      );
    });

    /// What a signed-out app passes without meaning to. An empty id would name
    /// somebody.
    test('drops an empty field rather than sending it', () {
      expect(
        encodeVisitor(const FeedobackVisitor(id: '', email: '  ', name: 'Ada')),
        {'name': 'Ada'},
      );
    });
  });

  group('encodeContext', () {
    test('takes the scalars the server stores', () {
      expect(
        encodeContext({
          'plan': 'pro',
          'seats': 12,
          'trial': false,
          'invitedBy': null,
        }),
        {'plan': 'pro', 'seats': 12, 'trial': false, 'invitedBy': null},
      );
    });

    /// Stringifying one would file `Instance of 'Order'` under somebody's
    /// feedback.
    test('drops what has no shape the server stores', () {
      expect(
        encodeContext({
          'tags': ['a'],
          'nested': {'a': 1},
          'at': DateTime(2026),
          'plan': 'pro',
        }),
        {'plan': 'pro'},
      );
    });

    test('drops a number that is not finite', () {
      expect(encodeContext({'ratio': double.infinity, 'seats': 12}), {
        'seats': 12,
      });
      expect(encodeContext({'ratio': double.nan}), isEmpty);
    });

    test('drops an empty key', () {
      expect(encodeContext({'': 'x', 'plan': 'pro'}), {'plan': 'pro'});
    });
  });
}
