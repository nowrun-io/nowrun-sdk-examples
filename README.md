# nowrun SDK samples

Sample Android apps integrated with the nowrun SDK, and the AI coding-agent skills that did the
integration. Each app works normally by touch, and through nowrun an AI agent can call the same
actions. Both go through the same app state, so every change shows on screen whoever made it.

## Sample apps

| App | Built with | What it shows |
|---|---|---|
| [Sudoku](sample-apps/sudoku/) | Java, Android Views | A single-screen game: functions that read and change a board model. |
| [Drape](sample-apps/nowrun-drape/) | Expo (SDK 57), React Native | An outfit-styling app with two tabs: catalogue search, multi-slot edits, saved looks and a long-running render that reports progress through state. |

Each app's README covers where its nowrun integration is, the functions it exposes and how to
build and run it. Each app is pinned to a specific SDK release and downloads that release when
it builds.

## Skills

[`skills/`](skills/) holds the coding-agent skills used to integrate nowrun into these sample
apps: Markdown instructions that let a coding agent add the SDK to an app, propose the functions
to expose and write them.

| Skill | For apps built with | Used for |
|---|---|---|
| [`nowrun-java`](skills/nowrun-java/) | Java or Kotlin (native Android, Views or Compose) | Sudoku |
| [`nowrun-expo`](skills/nowrun-expo/) | React Native: Expo, or bare React Native | Drape |

To integrate nowrun into your own app, download the latest skill from the nowrun docs rather than
copying it from here:
[Android](https://nowrun.io/docs/sdk/get-started/android#let-a-coding-agent-do-it) or
[React Native](https://nowrun.io/docs/sdk/get-started/react-native#let-a-coding-agent-do-it).

## Running the samples

Each app has a `build.sh` that downloads its SDK release and builds an APK. See the app's README
for what it needs.

On an ordinary phone or emulator nowrun isn't installed, so the SDK never connects. The apps
still work normally by touch. To call their functions from an AI agent, run them on nowrun.

## License

Everything here is licensed under the [PolyForm Shield License 1.0.0](skills/LICENSE.md). You
may use it, and code based on it, for anything except building a product that competes with
nowrun. The [`skills/`](skills/LICENSE.md) and [`sample-apps/`](sample-apps/LICENSE.md) folders
each carry a copy.
