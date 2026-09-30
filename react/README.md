# feedoback-react

React and Next.js SDK for the [Feedoback](https://feedoback.com) website feedback widget.
Drop one component in and visitors can point at an element and comment, record their screen
or send a message — no manual script tags, no wrapper components to hand-write.

```bash
npm install feedoback-react
```

## Quick start

### Next.js (App Router)

```tsx
// app/layout.tsx
import { FeedbackWidget } from "feedoback-react";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {children}
        <FeedbackWidget projectKey="pk_live_your_key" />
      </body>
    </html>
  );
}
```

`FeedbackWidget` is a client component; you can render it straight from a server layout.

### React (Vite, CRA, anything)

```tsx
// src/App.tsx
import { FeedbackWidget } from "feedoback-react";

export default function App() {
  return (
    <>
      <YourApp />
      <FeedbackWidget projectKey="pk_live_your_key" />
    </>
  );
}
```

Your public key is on the project's **Install** page in the dashboard. It identifies the
project on a page — it is public, not a secret.

## Identify who is signed in

Pass the current user; the widget shows who left each message so you can reply. Every field
is optional.

```tsx
<FeedbackWidget
  projectKey="pk_live_your_key"
  visitor={user ? { id: user.id, email: user.email, name: user.name } : undefined}
/>
```

If your project is rolled out to **listed visitors**, this is also how the widget knows
whether to appear at all.

## Drive it from your own UI

`useFeedback()` returns the imperative API from anywhere in the tree — no provider needed.
Calls made before the widget finishes loading are queued and replayed.

```tsx
import { useFeedback } from "feedoback-react";

function HelpMenu() {
  const feedback = useFeedback();
  return (
    <>
      <button onClick={feedback.startFeedback}>Report a bug</button>
      <button onClick={feedback.startRecording}>Record your screen</button>
    </>
  );
}
```

| Method | Effect |
| --- | --- |
| `identify(visitor)` | Tell the widget who is signed in |
| `setContext(context)` | Attach flat scalar context (plan, route…) to new threads |
| `open()` / `close()` / `toggle()` | Control the panel |
| `startFeedback()` | Start element-pointing feedback |
| `startRecording()` | Open straight into screen recording |
| `destroy()` | Remove the widget (call on sign-out in an SPA) |

## Props

| Prop | Type | Default | Notes |
| --- | --- | --- | --- |
| `projectKey` | `string` | — | Required. `pk_…` from the dashboard |
| `host` | `string` | `https://feedoback.com` | Origin serving `widget.js` |
| `version` | `string` | — | Your release, stored with every thread |
| `scriptSrc` | `string` | — | Full override of the script URL (self-hosting) |
| `visitor` | `{ id?, email?, name? }` | — | Identify on load and on change |
| `context` | `Record<string, string \| number \| boolean \| null>` | — | Custom context |
| `destroyOnUnmount` | `boolean` | `false` | Tear down when the component unmounts |
| `onLoad` / `onError` | `() => void` | — | Script load callbacks |

## Content-Security-Policy

If your site sends a CSP, allow your Feedoback host in `script-src` and `connect-src`, and
add `data:` and `blob:` to `img-src`. The widget needs no `'unsafe-inline'`.

## How it works

The package is a thin, dependency-free loader: it injects the hosted `widget.js` (with the
attributes it reads from its own tag) exactly once, and proxies `useFeedback()` calls to the
widget's global — queueing anything called before it boots. The widget itself is served and
versioned by Feedoback, so it updates without you shipping a new build.

## License

MIT
