# Feedoback for Android

Feedback from inside your app, filed next to the feedback from your website.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Feedoback.start(this, FeedobackConfiguration(projectKey = "pk_…"))
    }
}
```

That is the whole setup. Everything the owner controls — the accent, the word
on the launcher, whether stars are asked for, who may send — comes from the
dashboard, so it changes without an app release.

minSdk 24. No dependencies beyond coroutines and core-ktx, and no permission
beyond the internet your app already has.

## Install

```kotlin
implementation("com.feedoback:feedoback-android:0.1.0")
```

## Opening the sheet

From a control you already own — a Settings row, a menu item:

```kotlin
Feedoback.present()                              // "Send feedback"
Feedoback.present(FeedbackCategory.BUG)          // "Report a problem"
```

Or let the SDK draw a floating button. It is off unless you ask, because an
app that did not ask for one should not get a button over its own interface:

```kotlin
FeedobackConfiguration(
    projectKey = "pk_…",
    launcher = LauncherOptions(enabled = true, corner = LauncherCorner.BOTTOM_END),
)
```

The button is a view in your Activity, never a `TYPE_APPLICATION_OVERLAY`
window: this SDK will not ask your app for `SYSTEM_ALERT_WINDOW`, and it draws
over nothing that is not already yours. `setLauncherHidden(true)` takes it out
of the way of a screen that wants none.

## Naming the screen

Feedback is filed under the screen it came from, the way a website's is filed
under a page:

```kotlin
Feedoback.setScreen("checkout/payment", "Payment")
```

A path reads best. Identifiers are folded out of it on the way in, so
`orders/12345` and `orders/67890` are one screen, not two thousand.

## Who the visitor is

```kotlin
Feedoback.identify(FeedbackVisitor(id = user.id, email = user.email, name = user.name))
Feedoback.reset()   // on sign-out
```

This is your app's word about who someone is, which is all it can be. If the
project asks for verified identity, have your **server** sign the id with the
project's key and pass the result:

```
userHash = HMAC-SHA256(projectIdentitySecret, userId)   // hex, on your server
```

The key never belongs in the app: anything the app holds, the app can forge.

## What else is worth knowing about them

Whatever you will want to read beside the words — the plan they are on, the
flag they have, the tier they bought:

```kotlin
Feedoback.setContext(mapOf("plan" to "pro", "seats" to 12))
```

It rides on every thread opened from then on, and is stamped when the visitor
finishes writing rather than when it is sent, so something written offline
arrives under what was true at the time. At most thirty keys; anything past
that is dropped rather than costing the visitor their feedback. `reset()`
clears it along with the identity.

## Screenshots

A picture of the screen rides along, and the visitor sees it before it goes
and can take it off. Nothing is sent that the person looking at it did not
look at first.

Password fields are hidden without being asked. Mark anything else:

```kotlin
Feedoback.redact(cardNumberView)
```

A window with `FLAG_SECURE` is never captured at all: an app that set it meant
it, and a feedback SDK is not an exception to it.

## Light and dark

Follows the device. Set `theme = Theme.LIGHT` or `DARK` only if your app
forces its own appearance — the server is never asked and never tells.

## Offline

A thread that cannot be sent is written to disk and goes out on the next
foreground, with the identity it was written under. Bounded at 20 threads,
20 MB and 7 days, because an unbounded queue on someone else's device is a bug.

## What it collects

Only what you hand it, plus what any app can read about itself:

- the identity you pass to `identify()` and the context you pass to `setContext()`
- the message, the rating, and the screenshot the visitor kept
- your package name, version and build
- the device model, OS version, locale, time zone, screen size and orientation
- a random install id, minted here and stored in `SharedPreferences`

**Not** collected: the advertising id, `ANDROID_ID`, location, contacts,
crashes, network traffic, or anything at all about other apps. Nothing is
instrumented and nothing is intercepted. The network type is reported only
when your app already holds `ACCESS_NETWORK_STATE`; this SDK does not ask for
it, and reports nothing rather than guessing.

## Play Data Safety

Play makes *you* declare this, not us, so here is the same list in the form's
own words. Everything marked optional is only collected if you call the thing
that produces it.

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Personal info → Name | Only if you call `identify()` | No | App functionality | Yes |
| Personal info → Email address | Only if you call `identify()`, or the visitor types one | No | App functionality, Customer support | Yes |
| Personal info → User IDs | Only if you call `identify()` | No | App functionality | Yes |
| Messages → Other in-app messages | Yes — the feedback itself | No | Customer support | No |
| Photos and videos → Photos | Only the screenshot, and only if the visitor keeps it | No | Customer support | Yes |
| App activity → Other actions | Only what you pass to `setContext()` | No | App functionality | Yes |
| App info and performance → Other app performance data | Yes — your version and build, the device model, OS version, locale, time zone and screen size | No | App functionality | No |

And the three questions above the table:

- **Is all of the user data collected by your app encrypted in transit?** Yes.
  Everything goes over HTTPS.
- **Do you provide a way for users to request that their data be deleted?**
  Yes, through the project owner.
- **Device or other IDs:** not collected. The install id this SDK mints is
  random, lives in your app's own `SharedPreferences`, dies when the app is
  uninstalled, and cannot identify a device across apps — which is what that
  question is asking about.

## Running the example

```bash
FEEDOBACK_KEY=pk_… ./example/run.sh     # builds and installs on a running emulator
```

## Size

```bash
./gradlew :feedoback:assembleRelease && ./size.sh
```

150 kB of dex and no dependencies. Measured as dex rather than as the jar,
because a jar is JVM bytecode no phone runs and what lands in an APK is what
d8 makes of it.

## Tests

```bash
./gradlew :feedoback:testDebugUnitTest
```

The wire tests read `packages/protocol/fixtures`, which the server reads and
the iOS SDK reads. A field added on one side and not the other fails a build
rather than a customer.
