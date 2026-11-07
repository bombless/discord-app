# Discord App Runtime

An Android-first experimental client shell for developing a script-driven UI. Kotlin + Jetpack Compose renders native components; a local WebView V8 context executes the single editable main.js file and forwards event callbacks by ID.

> This is a development runtime scaffold, not an implementation of Discord private APIs. Use authorized integrations and accounts; do not automate normal user accounts as self-bots.

## Stack

- Gradle Wrapper 9.1.0
- Android Gradle Plugin 9.0.0
- Kotlin built into AGP 9 + Compose compiler plugin 2.2.10
- Jetpack Compose Material 3 renderer
- Android WebView JavaScript runtime (V8)
- TCP script sync bound to 127.0.0.1:8765

## Build

Use JDK 17 and Android SDK Platform 36.

~~~bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

## Connect the live script channel

With the app open:

~~~bash
adb forward tcp:8765 tcp:8765
~~~

Copy the pairing token displayed on the app home screen, then run:

~~~bash
python tools/sync.py --token YOUR_TOKEN status
python tools/sync.py --token YOUR_TOKEN push app/src/main/assets/main.js
python tools/sync.py --token YOUR_TOKEN list
python tools/sync.py --token YOUR_TOKEN rollback 1
~~~

The host-side source of truth is one file: app/src/main/assets/main.js. Every successful upload is saved as an immutable version under the app private files directory. The app displays the active version and revision history.

## Script API

The runtime exposes UI, render(tree), and NativeHost.log(message).

~~~javascript
let count = 0;
function App() {
  return UI.column({
    children: [
      UI.text({ text: "Live screen", fontSize: 22 }),
      UI.button({
        label: "Count: " + count,
        onClick: () => { count += 1; render(App()); }
      })
    ]
  });
}
render(App());
~~~

Supported native components in this starter: column, row, text, button, input, card, and spacer. Event handlers such as onClick and onChange are registered in the JS runtime. Compose receives callback IDs and dispatches events back into JavaScript.

## TCP protocol

One newline-delimited JSON request per TCP connection. Every request requires the pairing token.

- status: { "cmd": "status", "token": "..." }
- push: { "cmd": "push", "token": "...", "source": "...", "note": "..." }
- list: { "cmd": "list", "token": "..." }
- rollback: { "cmd": "rollback", "token": "...", "version": 1 }

Uploads are limited to 1 MiB. The server binds only to device loopback; connect through adb forward. The token is a per-launch pairing secret. Do not expose this port through a public network tunnel.

## Runtime notes

- Uploads create immutable revisions and activate them immediately.
- Rollback changes the active pointer; it does not delete history.
- The JS runtime is hosted in a hidden WebView; Compose owns the visible UI.
- This first iteration reloads the script module as a whole. Persistent JS closures/state are not guaranteed across uploads.
- The initial renderer is intentionally small; add virtualized native list components before rendering large chat histories.
