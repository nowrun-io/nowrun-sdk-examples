# Drape: nowrun sample app (Expo)

A small outfit-styling app built with Expo (SDK 57) and React Native. The user picks clothes from a
catalogue and builds a look, and an AI agent can do the same through nowrun functions. Both
drive the same app state, so every change is visible on screen whoever made it.

Use it as a reference for integrating `nowrun-expo` into a React Native app. The full guide is the
[nowrun-expo skill](../../skills/nowrun-expo/SKILL.md).

## Screens

- **Shop:** the catalogue, filterable by collection and category, with a detail view for
  each item.
- **The Look:** the current outfit, one cell per slot (top, bottom, dress, outerwear, shoes,
  accessory), plus the palette, a try-on preview and saved looks.

## Where the nowrun integration is

It follows the layout from the [nowrun-expo skill](../../skills/nowrun-expo/SKILL.md):

| File | Holds |
|---|---|
| [`nowrun/tools.ts`](nowrun/tools.ts) | The function list (`TOOLS: ToolSpec[]`). Choices are declared with `values`. |
| [`nowrun/useAppFunctions.ts`](nowrun/useAppFunctions.ts) | One handler per function, each answering with `ok()` or `fail()`. |
| [`nowrun/results.ts`](nowrun/results.ts) | The `ok()` / `fail()` reply helpers. |
| [`App.tsx`](App.tsx) | `useNowrun(...)`, and the state pushed with `notifyState` on every change and on return to the foreground. |

The handlers don't reimplement anything. They call the app's one dispatcher, `applyCommand` in
[`engine.ts`](engine.ts), which the buttons use too, so a function call and a tap always behave
the same. When a command is refused, the dispatcher returns an error code (`NOT_FOUND`,
`NOT_ALLOWED`, `INVALID_ARGUMENTS`) and a message saying how to fix the call.

## Functions

| Function | What it does |
|---|---|
| `get_state` | The screen, outfit, palette, saved looks, try-on status and catalogue. |
| `search_catalog(query, slot, collection, tags)` | Finds catalogue items. Read-only. |
| `set_outfit(replace, slots)` | Sets several slots at once from a map of slot to item id. |
| `swap_item(slot, id)` | Puts one item into one slot. |
| `clear_slot(slot)` | Empties one slot. |
| `set_palette(mood, primary, secondary)` | Sets the colour mood and two accent colours. |
| `set_color(slot, color)` | Recolours one tintable item. |
| `clear_palette` | Removes the palette and any colour overrides. |
| `save_look(name)` | Saves the current outfit and returns the look's id. |
| `open_look(id)` | Reopens a saved look. |
| `visualize(on)` | Starts a try-on render and returns at once; progress shows in state. |
| `switch_tab(tab)` | Switches between `shop` and `looks`. |

## Run it

Needs macOS or Linux, Node.js and npm, a JDK 17 or newer (`JAVA_HOME`, or `java` on the `PATH`),
the Android SDK (`ANDROID_HOME`) and `curl`.

The module is installed from `vendor/`, like any app using `nowrun-expo`. `package.json` names the
release this sample is pinned to; download that tarball, then install and run:

```bash
TGZ=$(node -p "require('./package.json').dependencies['nowrun-expo'].replace(/^file:/, '')")
VERSION=${TGZ#vendor/nowrun-expo-}; VERSION=${VERSION%.tgz}
curl -fL --create-dirs -o "$TGZ" "https://downloads.nowrun.io/releases/$VERSION/nowrun-expo-$VERSION.tgz"
npm install

npx expo run:android      # builds and runs on a connected phone or emulator
```

`./build.sh` does the same download and builds a release APK instead.

On an ordinary phone or emulator nowrun isn't there, so the SDK never
connects. `adb logcat -s NowrunSdkService` shows two `nowrun service not found` lines each
time the app comes to the front, and that's expected. The app itself works normally by touch.

## Try-on

The try-on render (`visualize`, and the "Try it on" sheet) needs a backend, which isn't part of
the nowrun integration. Without one, try-on is off: the sheet says so, and `visualize` replies
`NOT_ALLOWED`. To turn it on:

1. Deploy `api/` (Vercel functions) with your own Magnific account,
   setting `MAGNIFIC_FOLDER_ID` (the project renders go into), `MAGNIFIC_CLIENT_ID` and
   `MAGNIFIC_REFRESH_TOKEN` (or a short-lived `MAGNIFIC_ACCESS_TOKEN`).
2. Build the app with `EXPO_PUBLIC_DRAPE_API_URL` set to that deployment's URL, in the
   environment or in a `.env` file.

`api/creations-map.json` maps each catalogue item to the Magnific creation used as its reference
image. The ids in it belong to the account the sample was made with, so a different account
needs its own.

## Files

| File | Holds |
|---|---|
| `App.tsx` | The UI, the dispatcher wiring and the state push. |
| `nowrun/` | The function list, the handlers and the reply helpers. |
| `engine.ts` | App state and the command dispatcher that both the UI and the functions use. |
| `catalog.ts` | The catalogue items. |
| `catalogImages.ts` | The map from item to product image. |
| `api/` | Vercel functions behind the try-on render (`visualize`, `selfie`). Not part of the nowrun integration; see [Try-on](#try-on). |

## License

[PolyForm Shield License 1.0.0](../LICENSE.md).
