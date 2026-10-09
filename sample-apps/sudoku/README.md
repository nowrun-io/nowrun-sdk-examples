# Sudoku: nowrun sample app (Java)

A single-screen Sudoku game built with Android Views. A person fills in the board by touch, and an
AI agent can do the same through nowrun functions. Both go through the same board model, so
every change shows on screen whoever made it.

Use it as a reference for integrating the nowrun SDK into a native Android app. The full guide is
the [nowrun-java skill](../../skills/nowrun-java/SKILL.md).

## Where the nowrun integration is

All of it is in [`app/src/main/java/com/example/sudoku/nowrun/`](app/src/main/java/com/example/sudoku/nowrun):

| File | Holds |
|---|---|
| `AppFunctions.java` | The functions object: each method marked with `@Describe` is a function the agent can call. |
| `Nowrun.java` | The entry point the activity calls: `Nowrun.start(this)` and `Nowrun.reportState()`. |
| `StateReporter.java` | The state snapshot, pushed on every board change and returned by `getState()`. |
| `Results.java` | The reply helpers, `ok()` and `fail()`. |

## Run it

Needs macOS or Linux, a JDK 17 or newer (`JAVA_HOME`, or `java` on the `PATH`), the Android SDK
(`ANDROID_HOME`, or `sdk.dir` in `local.properties`) and `curl`.

```bash
./build.sh                # downloads the SDK release this sample is pinned to, then builds a debug APK
./build.sh install        # ...and installs it with adb, which must be on the PATH
```

The SDK is downloaded into `app/libs/`, under the file name `app/build.gradle` compiles against.

On an ordinary phone or emulator nowrun isn't there, so the SDK never connects.
`adb logcat -s NowrunSdkService` shows two `nowrun service not found` lines each time the app
comes to the front, and that's expected. The game itself works normally by touch.

## License

[PolyForm Shield License 1.0.0](../LICENSE.md).
