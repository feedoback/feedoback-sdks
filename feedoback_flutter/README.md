# feedoback_flutter

Native feedback for Flutter apps. A sheet from a control your app already
owns, or a floating launcher over it.

One `<script>` tag does this on the web; on a phone it is a platform channel
over the [iOS](../ios) and [Android](../android) SDKs. The sheet is native on
each platform and the two cross-platform packages inherit every fix made to it.

```yaml
dependencies:
  feedoback_flutter: ^0.1.0
```

## Start it

```dart
await Feedoback.start(
  const FeedobackOptions(projectKey: 'pk_live_123'),
);
```

From `main()`, after `WidgetsFlutterBinding.ensureInitialized()`. It is
idempotent: starting twice does not stack two launchers, the way a snippet
pasted twice on a page must not.

**Mobile is off until the owner turns it on.** Add the app's bundle id under
Mobile apps on the project, or the SDK stays dormant and says why in the
console. Nothing is drawn and `present()` does nothing — a person holding the
phone misconfigured nothing and is never shown an error.

## Open the sheet

```dart
ListTile(
  title: const Text('Report a problem'),
  onTap: () => Feedoback.present(FeedobackCategory.bug),
)
```

A Settings row and a menu item are two calls to the same sheet. The floating
launcher is opt-in — most apps have a place for this already:

```dart
await Feedoback.start(const FeedobackOptions(
  projectKey: 'pk_live_123',
  launcher: FeedobackLauncher(
    corner: FeedobackLauncherCorner.bottomEnd,
    style: FeedobackLauncherStyle.labelled,
  ),
));
```

`setLauncherHidden(true)` takes it out of the way of a screen that wants none.

## Name the screen

Feedback is filed under the screen it came from, the way a website's is filed
under a page. Call this when a screen appears — from a `RouteObserver`, if you
have one:

```dart
Feedoback.setScreen('checkout/payment', title: 'Payment');
```

A path reads best. Identifiers are folded out of it on the way in, so
`orders/12345` and `orders/67890` are one screen, not two thousand.

## Who the visitor is

```dart
Feedoback.identify(FeedobackVisitor(id: user.id, email: user.email));
Feedoback.reset(); // on sign-out
```

This is your app's word about who someone is, which is all it can be. If the
project asks for verified identity, have your **server** sign the id with the
project's key and pass the result:

```
userHash = HMAC-SHA256(projectIdentitySecret, userId)   // hex, on your server
```

The key never belongs in the app: anything the app holds, the app can forge.

## What else is worth knowing about them

```dart
Feedoback.setContext({'plan': 'pro', 'seats': 12, 'trial': false});
```

Rides on every thread opened from then on, stamped when the visitor finishes
writing rather than when it is sent — so something written offline arrives
under what was true at the time. At most thirty keys.

## Screenshots, and what stays off them

A picture of the screen rides along, and the visitor sees it before it goes and
can take it off. Nothing is sent that the person looking at it did not look at
first.

```dart
FeedobackRedact(child: Text(card.number))
```

It lays out exactly as its child does and changes nothing on screen; what it
changes is the screenshot, where its frame is painted over.

**Wrap obscured fields too.** On iOS, Android and React Native the SDK finds a
secure text field on its own, because it is a native view it can walk to.
Flutter draws its entire interface into one native view, including the dots in
a `TextField(obscureText: true)`, so there is nothing to find. Here that
convenience does not exist and `FeedobackRedact` is the whole of it — which is
the one place this SDK asks more of you than the other three.

Turn the whole thing off with `screenshots: FeedobackScreenshots.off` — your
app knows which of its screens are sensitive, which is why that decision lives
here and not in a dashboard someone else can change.

## Light and dark

Follows the device. Set `theme: FeedobackTheme.light` or `.dark` only if your
app forces its own appearance — the server is never asked and never tells. The
accent is the owner's and arrives from the server, so it can change without an
app release.

## Offline

A thread that cannot be sent is written to disk and goes out on the next
launch, with the identity and context it was written under. Bounded at 20
threads, 20 MB and 7 days, because an unbounded queue on someone else's device
is a bug.

## What it collects

Only what you hand it, plus what any app can read about itself: the identity
and context you pass, the message, the rating, the screenshot the visitor kept,
your bundle id and version, the device model, OS version, locale, time zone,
screen size and orientation, and a random install id that dies with the app.

**Not** collected: the advertising identifier, the vendor identifier, location,
contacts, crashes, network traffic, or anything about other apps. Nothing is
swizzled and nothing is intercepted.

## What the stores ask you to declare

You ship this, so the declarations are yours to make. Both are already written
out:

- **App Store.** `PrivacyInfo.xcprivacy` ships inside the iOS SDK, declaring
  `UserDefaults` under `CA92.1` and no tracking. Xcode aggregates it into your
  app's own manifest, so a submission does not break because of us.
- **Play.** The [Data Safety table](../android/README.md#play-data-safety) in
  the Android SDK's README is the same list in the form's own words, row by
  row.

## Not here

Pointing at an element and screen recording are web-only for now: neither has a
native equivalent that survives a view tree Flutter draws itself. The panel's
three ways in reduce to one, which is a case it already handles — with exactly
one left it opens straight into the form rather than showing a menu of one. The
server does that reducing, so no SDK re-derives it.

Swift Package Manager is not supported yet, only CocoaPods. Flutter integrates
a plugin without a `Package.swift` through CocoaPods even in an app that has
SPM turned on; the iOS SDK cannot become an SPM dependency until it is mirrored
to its own repository, because SPM will not resolve a package from a
subdirectory.

## Running the example

`example/` is the smallest app that exercises all of it. No key is committed;
pass one in:

```bash
cd example
flutter run \
  --dart-define=FEEDOBACK_KEY=pk_… \
  --dart-define=FEEDOBACK_USER=u_1
```

The emulator reaches a dev server on the host at `10.0.2.2`; the simulator
shares `localhost`. A dev server on plain HTTP also needs
`usesCleartextTraffic` on Android, which the example's debug manifest sets.

## Tests

```bash
flutter test         # the Dart that shapes what crosses the channel
flutter analyze
```

The plugin halves are verified by running the example on a simulator and an
emulator: what they do is call into the two SDKs, which have their own suites.
