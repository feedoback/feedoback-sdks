# feedoback-react-native

Native feedback for React Native apps. A sheet from a control your app already
owns, or a floating launcher over it.

One `<script>` tag does this on the web; on a phone it is a Turbo Module over
the [iOS](../ios) and [Android](../android) SDKs. The sheet is native on each
platform and the two cross-platform packages inherit every fix made to it.

```
npm install feedoback-react-native
cd ios && pod install
```

Requires React Native 0.76 or newer, on the New Architecture.

## Start it

```tsx
import { FeedbackProvider } from 'feedoback-react-native';

export default function App() {
  return (
    <FeedbackProvider projectKey="pk_live_123" visitor={user}>
      <Navigation />
    </FeedbackProvider>
  );
}
```

`Feedoback.start({ projectKey })` from `index.js` does the same thing, if you
would rather start before React mounts. Either way it is idempotent: starting
twice does not stack two launchers, the way a snippet pasted twice on a page
must not.

**Mobile is off until the owner turns it on.** Add the app's bundle id under
Mobile apps on the project, or the SDK stays dormant and says why in the
console. Nothing is drawn and `present()` does nothing — a person holding the
phone misconfigured nothing and is never shown an error.

## Open the sheet

```tsx
const feedback = useFeedback();

<Pressable onPress={() => feedback.present('bug')}>
  <Text>Report a problem</Text>
</Pressable>
```

A Settings row and a menu item are two calls to the same sheet. The floating
launcher is opt-in — most apps have a place for this already:

```tsx
<FeedbackProvider
  projectKey="pk_live_123"
  launcher={{ enabled: true, corner: 'bottom-end', style: 'labelled' }}
/>
```

`setLauncherHidden(true)` takes it out of the way of a screen that wants none.

## Name the screen

Feedback is filed under the screen it came from, the way a website's is filed
under a page. Call this when a screen appears — from a navigation listener, if
you have one:

```tsx
feedback.setScreen('checkout/payment', 'Payment');
```

A path reads best. Identifiers are folded out of it on the way in, so
`orders/12345` and `orders/67890` are one screen, not two thousand.

## Who the visitor is

```tsx
feedback.identify({ id: user.id, email: user.email, name: user.name });
feedback.reset(); // on sign-out
```

This is your app's word about who someone is, which is all it can be. If the
project asks for verified identity, have your **server** sign the id with the
project's key and pass the result:

```
userHash = HMAC-SHA256(projectIdentitySecret, userId)   // hex, on your server
```

The key never belongs in the app: anything the app holds, the app can forge.

## What else is worth knowing about them

```tsx
feedback.setContext({ plan: 'pro', seats: 12, trial: false });
```

Rides on every thread opened from then on, stamped when the visitor finishes
writing rather than when it is sent — so something written offline arrives
under what was true at the time. At most thirty keys; anything past that is
dropped rather than costing the visitor their feedback.

## Screenshots, and what stays off them

A picture of the screen rides along, and the visitor sees it before it goes and
can take it off. Nothing is sent that the person looking at it did not look at
first.

A `TextInput` with `secureTextEntry` is hidden without being asked, because
forgetting one of those is the expensive mistake. Wrap anything else:

```tsx
<FeedobackRedact>
  <Text>{card.number}</Text>
</FeedobackRedact>
```

It lays out exactly as a `View` and changes nothing on screen; what it changes
is the screenshot, where its frame is painted over before the bitmap exists.
`redacted={false}` turns it off for a view that is only sometimes sensitive.

Turn the whole thing off with `screenshots="off"` — your app knows which of its
screens are sensitive, which is why that decision lives here and not in a
dashboard someone else can change.

## Light and dark

Follows the device. Set `theme="light"` or `"dark"` only if your app forces its
own appearance — the server is never asked and never tells. The accent is the
owner's and arrives from the server, so it can change without an app release.

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
native equivalent that survives a view tree React Native draws itself. The
panel's three ways in reduce to one, which is a case it already handles — with
exactly one left it opens straight into the form rather than showing a menu of
one. The server does that reducing, so no SDK re-derives it.

Shake to open is not available from JavaScript. The iOS SDK has
`handleShake()`, which a host app forwards `motionEnded` to; there is no
Android equivalent yet.

## Running the example

`example/` is the smallest app that exercises all of it. Point it at a server
by editing `example/feedoback.config.json`, then:

```
cd example
npm install
npm run android    # or: npm run ios
```

The emulator reaches a dev server on the host at `10.0.2.2`; the simulator
shares `localhost`.

## Tests

```
npm test        # the TypeScript that shapes what crosses the bridge
npm run typecheck
```

The native halves are verified by running the example on a simulator and an
emulator: what they do is call into the two SDKs, which have their own suites.
