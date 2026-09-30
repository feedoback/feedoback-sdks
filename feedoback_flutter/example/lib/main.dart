/// The smallest app that exercises the SDK the way a customer would: a
/// Settings-style list with rows that open the sheet, the floating launcher, an
/// identity, a screen name, some context, and two kinds of thing that must not
/// appear in a screenshot.
///
/// Point it at a server with --dart-define, so no key is ever committed:
///
///   flutter run --dart-define=FEEDOBACK_KEY=pk_… --dart-define=FEEDOBACK_USER=u_1
library;

import 'dart:io' show Platform;

import 'package:feedoback_flutter/feedoback_flutter.dart';
import 'package:flutter/material.dart';

const projectKey = String.fromEnvironment(
  'FEEDOBACK_KEY',
  defaultValue: 'pk_missing',
);
const visitorId = String.fromEnvironment('FEEDOBACK_USER');
const visitorEmail = String.fromEnvironment('FEEDOBACK_EMAIL');
const userHash = String.fromEnvironment('FEEDOBACK_HASH');

/// The emulator reaches the host machine at 10.0.2.2; the simulator shares
/// localhost with it.
String get host =>
    Platform.isAndroid ? 'http://10.0.2.2:3000' : 'http://localhost:3000';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Feedoback.start(
    FeedobackOptions(
      projectKey: projectKey,
      host: host,
      categories: const [
        FeedobackCategory.feedback,
        FeedobackCategory.bug,
        FeedobackCategory.idea,
      ],
      launcher: const FeedobackLauncher(
        style: FeedobackLauncherStyle.labelled,
        corner: FeedobackLauncherCorner.bottomEnd,
      ),
      logLevel: FeedobackLogLevel.debug,
    ),
  );

  if (visitorId.isNotEmpty) {
    await Feedoback.identify(
      const FeedobackVisitor(
        id: visitorId,
        email: visitorEmail,
        name: 'Ada Lovelace',
        userHash: userHash,
      ),
    );
  }
  await Feedoback.setContext({'plan': 'pro', 'seats': 12, 'trial': false});

  runApp(const ExampleApp());
}

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Feedoback',
    theme: ThemeData(colorSchemeSeed: const Color(0xFF0F6E56)),
    darkTheme: ThemeData.dark(useMaterial3: true),
    home: const SettingsPage(),
  );
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _password = TextEditingController(text: 'hunter2hunter2');

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    Feedoback.setScreen('settings', title: 'Settings');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 40, 20, 20),
        children: [
          Text('Settings', style: Theme.of(context).textTheme.headlineMedium),
          const _Legend('Account'),
          _Row(
            label: 'Password',
            // Wrapped, unlike the other SDKs' examples. Flutter draws the dots
            // itself rather than laying out a native secure field, so there is
            // nothing for the SDK to find.
            trailing: FeedobackRedact(
              child: SizedBox(
                width: 170,
                child: TextField(
                  obscureText: true,
                  enabled: false,
                  controller: _password,
                  decoration: const InputDecoration(border: InputBorder.none),
                  textAlign: TextAlign.right,
                ),
              ),
            ),
          ),
          _Row(
            label: 'Card',
            // Nothing marks this one out to the SDK, so the app says so.
            trailing: const FeedobackRedact(
              child: Text(
                '4242 4242 4242 4242',
                style: TextStyle(fontSize: 17),
              ),
            ),
          ),
          const _Legend('Feedback'),
          for (final (label, category) in const [
            ('Send feedback', FeedobackCategory.feedback),
            ('Report a problem', FeedobackCategory.bug),
            ('Suggest an idea', FeedobackCategory.idea),
          ])
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(label, style: const TextStyle(fontSize: 17)),
              onTap: () => Feedoback.present(category),
            ),
        ],
      ),
    ),
  );
}

class _Legend extends StatelessWidget {
  const _Legend(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 28, bottom: 8),
    child: Opacity(
      opacity: 0.6,
      child: Text(text, style: const TextStyle(fontSize: 13)),
    ),
  );
}

class _Row extends StatelessWidget {
  const _Row({required this.label, required this.trailing});

  final String label;
  final Widget trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 17)),
        trailing,
      ],
    ),
  );
}
