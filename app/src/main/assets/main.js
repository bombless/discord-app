// The single editable application script. Compose renders the resulting UI tree.
let clickCount = 0;
let draft = "";

function App() {
  return UI.column({
    children: [
      UI.text({ text: "Hello from main.js", fontSize: 22 }),
      UI.text({ text: "This screen is described in JavaScript and rendered by Jetpack Compose." }),
      UI.card({
        children: [
          UI.text({ text: "Interactive counter" }),
          UI.button({
            label: "Clicked " + clickCount + " times",
            onClick: () => {
              clickCount += 1;
              render(App());
            }
          })
        ]
      }),
      UI.input({
        value: draft,
        placeholder: "Type something…",
        onChange: value => {
          draft = value;
          NativeHost.log("Draft changed (" + draft.length + " chars)");
        }
      }),
      UI.button({
        label: "Publish draft to runtime log",
        onClick: () => NativeHost.log("Draft: " + draft)
      }),
      UI.text({ text: "Edit this file, then push it over the forwarded TCP port." })
    ]
  });
}

render(App());
