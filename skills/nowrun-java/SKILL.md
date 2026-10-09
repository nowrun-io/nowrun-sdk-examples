---
name: nowrun-java
description: Integrate the nowrun SDK (io.nowrun.sdk.NowrunSdkService) into a native Android app written in Java or Kotlin, then analyse the app and expose its actions, content and navigation as functions an LLM agent can call. Use when adding nowrun to an existing Android app or to one being built, when exposing or reviewing an app's nowrun functions, or when a nowrun function call fails on its arguments. Not for React Native apps, whether Expo or bare React Native; use nowrun-expo for those.
---

# nowrun SDK for native Android apps (Java / Kotlin)

The nowrun SDK lets an AI agent (today, ChatGPT with the nowrun plugin) operate an Android app on the user's behalf, through functions the app exposes. The
app hands the SDK one object, and each public method on it marked with `@Describe` becomes a function.
nowrun connects to whichever app is in the foreground, reads its
function list, calls functions, and receives the app's state as it changes.

The functions are chosen by the agent, an LLM: it decides which function to call,
and with what arguments, from the app's state and the function list the app publishes, meaning
each function's name and description and each argument's name, type and description. It can't
see the app's code. So write the list like instructions for that agent: vague descriptions
lead to wrong calls (see 3.6).

Follow the same seven phases whether the app already exists or is being built now. For an app
still in development, run phases 3 to 6 again whenever a feature is added, so its functions
ship with the feature.

1. Integrate the SDK
2. Analyse the app
3. Design the functions
4. Implement the functions layer
5. Report state
6. Verify
7. Hand over

---

## Phase 1 — Integrate the SDK

**Get the AAR.** The SDK is one file. It isn't on Maven; download it into the app module:

```bash
mkdir -p app/libs && curl -fL -o app/libs/nowrun-sdk.aar https://downloads.nowrun.io/latest/nowrun-sdk.aar
```

If `app/libs/nowrun-sdk.aar` is already there, this is an update: see below. If the download
fails, ask the user; don't substitute another library.

Commit `app/libs/nowrun-sdk.aar` with the app, so teammates and automated build servers (CI)
build without downloading it again. Then add the dependency:

```groovy
// app/build.gradle
dependencies { implementation files('libs/nowrun-sdk.aar') }
```
```kotlin
// app/build.gradle.kts
dependencies { implementation(files("libs/nowrun-sdk.aar")) }
```

Requirements: `minSdk` 21 or higher, and JDK 17 (the AAR targets Java 17).

**Updating the SDK:** run the same download command to replace `app/libs/nowrun-sdk.aar`, and
rebuild. The manifest merge stays the same.

**Nothing to add to the manifest.** The AAR's manifest merges in the `NowrunSdkService`
`<service>` and the `<queries>` entry for nowrun. Don't declare either yourself.

**Start the SDK** in `onCreate()` of every activity the app can launch into: the launcher
activity, deep-link targets, and anything reachable from a notification. A shared
`BaseActivity` is the easiest place. Repeat calls are safe; pass the same instance each time.

```java
@Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Nowrun.start(this);   // calls NowrunSdkService.start(activity, AppFunctions.get(activity))
}
```

`Nowrun` is a small entry-point class in the functions package (phase 4), so activities don't
depend on `AppFunctions` itself.

Don't call it from `Application.onCreate()`. It starts a service, and Android 8+ throws when a
process started in the background (by a receiver or a job) starts one.

**R8 / ProGuard.** The functions object is reached only by reflection. With
`minifyEnabled true`, add this to the app's `proguard-rules.pro`:

```proguard
-keep class com.example.app.nowrun.AppFunctions { public <methods>; }
```

The AAR's own consumer rules cover the SDK classes and annotations.

**Check that it compiles.** Your job is to confirm the app compiles with the SDK and the
functions layer:

```bash
./gradlew assembleDebug
```

Stop at this check. Getting the app onto nowrun is covered separately, not by this skill,
so don't attempt it or make up steps for it, and don't change the app's build setup.

A local phone or emulator is fine for UI work, but nowrun isn't there, so the
SDK never connects.

**How it behaves at runtime:**
- Only the app in the foreground is connected. Going to the background disconnects it, and
  the next resume reconnects.
- Calls run on SDK worker threads named `nowrun-call-N`, up to 8 at once. Never on the main
  thread.
- On a device without nowrun (any ordinary phone or emulator), the SDK never
  connects. Each time the app comes to the foreground it retries once a second, up to 10
  times, logging two lines: `nowrun service not found, retrying every 1000ms, up to 10 times`
  and, 10 seconds later, `nowrun service not found, gave up after 10 attempts`. That's
  expected, not an error.
- `start()` throws `IllegalArgumentException` if it's given a JSON string, a `JSONObject` or
  a boxed value instead of the functions object.

---

## Phase 2 — Analyse the app

Read the code before designing anything. Build an inventory of four things. The same
questions apply to a finished app and to a plan for a new one.

| Inventory | What it is | Where to look |
|---|---|---|
| **Screens** | Every place a user can be: activities, fragments, tabs, bottom-nav items, drawer entries, dialogs that act like pages, settings pages | `AndroidManifest.xml` activities, navigation graphs (`res/navigation/*.xml`), `BottomNavigationView`/`TabLayout` menus, fragment transactions, Compose `NavHost` routes |
| **Entities** | The app's own things with an identity that users see and act on: products, songs, notes, orders, levels, contacts, files | Model/data classes with an id, Room `@Entity`, repositories, API DTOs, adapters' item types |
| **Actions** | Everything a user can do: every button, menu item, swipe, long-press, form submit, toggle | `setOnClickListener`, `onOptionsItemSelected`, Compose `onClick`, ViewModel methods the UI calls |
| **Choices** | Finite option sets: sort orders, categories, sizes, modes, difficulty, filters | `enum`s, `@IntDef`/`@StringDef`, string-array resources, spinner adapters, constants |

Also note:
- **Deep links:** `<intent-filter>`s with `<data>`, and Navigation `<deepLink>`s. They show
  how the app already opens specific content, so reuse them for navigation.
- **Gated flows:** login, payment, account deletion, and anything that sends to other people.
- **Long operations:** uploads, downloads, rendering, syncing.
- **The single source of truth:** the ViewModel, repository or game model that both the UI
  and your functions must go through.

Write the inventory down. For an existing app, show it to the user with the function list
from phase 3 before implementing, so they can cut or add functions.

---

## Phase 3 — Design the functions

Each part of the inventory maps to a kind of function: actions become action functions,
entities become search and open functions, choices become fixed value lists, and screens
become navigation.

### 3.1 The baseline every app exposes

| Function | Purpose |
|---|---|
| `getState()` | Where the user is and what they see. Return exactly the payload phase 5 pushes. |
| `openScreen(screen)` | Go to a top-level screen. `screen` must be one of a fixed list that covers every screen from the inventory. |
| `goBack()` | The system back action. |
| `open<Entity>(id)` | Open an entity's detail screen, for each entity that has one, e.g. `openProduct(id)`. |
| `search<Entities>(query, …filters)` | Find entities and return their ids. Every other function takes ids, not names. |

Navigation functions move the user and change nothing else. An agent can't press a button
on a screen it can't reach, so full navigation coverage comes first.

### 3.2 Actions

- **One function per distinct action.** Use parameters for its variations. Write
  `setSort(order)`, not `sortByPrice()` plus `sortByName()`.
- **Name it verb + noun** in the platform's style: Java methods are `camelCase`
  (`addToCart`, `setCell`). The method name is the function name.
- **Go through the same code the UI uses**, the ViewModel or model method that the button's
  click handler calls. Never reimplement the logic.
- **Respect the UI's rules.** If the UI requires login, a selected item or an unlocked level,
  the function does too, and returns an error explaining what's missing.
- **Keep the parameters few.** nowrun refuses a call unless it has exactly one value per
  parameter, sent in declaration order, so every parameter must be sent on every call, including
  optional ones (as `""` or `null`). Each extra parameter is another chance for the agent to get
  a call wrong. If a function needs many, it's usually several actions: split it.
- **Put an entity's id in the parameter, not its name.** When only a name is known, the agent
  calls `search…` first.

### 3.3 Content (entities)

For each entity that users act on:
- `search<entities>(query, …filters)` returns the matching items, each
  `{"id", "name", …}` with a few key fields, matched by text, with suggested results when `query`
  is empty. The whole reply must stay under 128 KiB, or the call fails with
  `RESULT_TOO_LARGE`, so for lists that can grow large, return a bounded number and take a
  `limit` or `page` argument.
- `get<Entity>(id)`, if the detail isn't obvious from search results.
- **Ids must be stable.** Use the database or server id, never a position in a list. The
  agent gets an id from one call and uses it in a later one, e.g. `searchProducts("mug")`
  returns `[{"id": "p_1042", "name": "Blue Mug"}, {"id": "p_2201", "name": "Red Mug"}]`,
  then `addToCart("p_1042", 1)`. A position ("item 0") changes whenever the list is
  re-sorted, filtered or added to, so by the second call it can point at a different item,
  and the wrong one gets changed without any error.
- **Put what's on screen in state,** as `{"id", "name"}` in display order. Users talk about
  what they see, and state is how the app tells the agent what that is. With
  `"visible": [{"id": "p_1042", "name": "Blue Mug"}, {"id": "p_2201", "name": "Red Mug"}]`,
  "open the second one" becomes `openProduct("p_2201")`, and "add the red mug" matches the name.
  Use the same stable ids, so the agent can pass them straight to other functions.

### 3.4 Safety: confirmation, permissions, undo

- **Irreversible or outward-facing actions** (delete, purchase, send, post, overwrite) take a
  final `boolean confirm` parameter. With `confirm == false`, change nothing and return
  `NEEDS_CONFIRMATION` with a one-line summary of what would happen.
- **Never expose** credential entry, payment-detail entry, security or permission settings,
  or anything that bypasses a paywall or login.
- **Ambiguity:** if a lookup matches more than one entity, return `AMBIGUOUS` with the
  candidates (`id` and `name`) instead of guessing.
- **Undo:** if the app has undo, expose `undo()`.

### 3.5 Long operations

nowrun accepts one unresolved call per session and answers any other with `BUSY`, so
functions must return quickly, ideally under 2 seconds. For long work (uploads, renders),
start it, return `{"started": true}` right away, and report progress and the outcome through
state (`"upload": {"status": "running", "percent": 40}`).

**Every call must be answered.** A function that blocks, e.g. waiting on the main thread
without a limit or on user input, holds the session, and every later call gets `BUSY` for
60 seconds. Then nowrun gives up on it and reports it as `unknown` with
`APP_RESULT_TIMEOUT`, so the agent may never learn whether it worked. Wait with a timeout (`MainThread.call`, in the code references, withdraws work that hasn't
started within 2 seconds and gives up, and waits only for work already running), and never
wait for the user.

### 3.6 Descriptions

The descriptions are what the LLM reads, so write them as instructions:
- **Function** (`@Describe` on the method): what it does, its side effects, what it returns,
  and when to use it instead of a similar function.
- **Parameter** (`@Describe` on the parameter): **start with the parameter's name**, then its
  meaning, format, range or allowed values, how to skip it, and an example. The contract
  names Java parameters `arg0`, `arg1`, … whatever they're called in the code, so the
  description is the only place the real name appears.

```java
@Describe("Adds a product to the cart, or raises its quantity if already there. Returns the cart's item count.")
public String addToCart(
        @Describe("productId: the product's id, from searchProducts or getState. e.g. \"p_1042\"") String productId,
        @Describe("quantity: how many to add, 1-99.") int quantity) { ... }
```

Keep the whole function list under 128 KiB; a bigger one is refused with `CATALOG_TOO_LARGE`
and no function can be called. Don't paste every valid id into a description; that's what
`search…` is for.

---

## Argument types

Read [references/argument-types.md](references/argument-types.md) before writing any function
signature. It has the full table; each type in it converts the same way every time, so calls
don't fail on their arguments. In short:
- ids, free text, choices, dates (ISO 8601), colors (`#RRGGBB`) and URLs are `String`;
- whole numbers are `int`, decimals `double`, yes/no `boolean`, lists `String[]` or
  `List<String>`;
- optional numbers and flags are boxed (`Integer`, `Double`, `Boolean`) and sent as `null` to
  skip; optional text is sent as `""` to skip;
- no Java `enum`s, no classes of your own, no `Map` or JSON parameters, no overloads or
  varargs, and as few parameters as the action needs.

## Phase 4 — Implement the functions layer

Create one package, e.g. `com.example.app.nowrun`, holding five files, in Java or Kotlin to
match the app:
- `AppFunctions`, the exposed object;
- `Nowrun`, the entry point activities call;
- `Results`, the reply helpers;
- `MainThread`, for running UI work from a call;
- `CurrentActivity`, tracking the activity in front.

### Rules for the exposed class

- **Only methods marked with `@Describe` are functions.** Each public method declared in the
  class that carries `@Describe` is published, static ones included; inherited methods are
  not. Every other public method stays unpublished, and the SDK logs
  `not publishing <method>: it has no @Describe` for it. Put `@Describe` on functions only.
- **Kotlin:** what counts is the compiled class, not the Kotlin source. So:
  - don't put `@Describe` on an `internal` function: it compiles to a public method with a
    mangled name, which the SDK skips with a logcat warning;
  - don't use `@JvmOverloads` on a function: it adds overloads, and the SDK publishes only the
    first one of a name (with a warning);
  - don't expose `suspend` functions (the hidden continuation becomes an argument) or rely
    on default arguments (every argument is always sent).
- **Hold no `Activity`.** The object outlives activities. Reach the current one through
  `CurrentActivity`, and application state through your repository or ViewModel store.
- **Be thread-safe.** Calls arrive concurrently on worker threads. Synchronize shared state,
  or funnel every change through the main thread with `MainThread.call`.
- **Always return `String`**, built with `Results`.

### Replies

Every function returns a `String` built with `Results.ok(response)` or
`Results.fail(code, message)`. Only `response` reaches the caller. Use these error codes, with a
message that says how to fix the call: `INVALID_ARGUMENTS`, `NOT_FOUND`, `AMBIGUOUS`,
`NEEDS_CONFIRMATION`, `NOT_ALLOWED`, `APP_FUNCTION_FAILED`.

Read [references/replies.md](references/replies.md) for the `Results` code, when to use each
code, and how nowrun reads a reply: only an envelope can fail a call, and a raw object
with its own `success` field is misread.

### Code

- **Java app:** copy `MainThread`, `CurrentActivity`, `Nowrun` and the `AppFunctions` shape from
  [references/java-code.md](references/java-code.md), and `Results` from
  [references/replies.md](references/replies.md).
- **Kotlin app:** use [references/kotlin-code.md](references/kotlin-code.md), which has all
  five files in Kotlin.
- **Navigation:** [references/navigation.md](references/navigation.md) has a recipe for each
  app structure: activities, bottom navigation or tabs, the Navigation component, Compose,
  deep links, and back.

## Phase 5 — Report state

Push state whenever anything the user could see changes, and on every `onResume()`:

```java
NowrunSdkService.notifyStateChange(StateReporter.snapshot().toString());
```

nowrun gives the pushed state to the agent: it says what's on screen and what it means (ids,
selection, work in progress). `getState()` returns the same object, so pulled and pushed state never disagree.
Keep it small: nowrun refuses a state over 128 KiB.

```json
{
  "screen": "product",
  "screenParams": {"id": "p_1042"},
  "visible": [{"id": "p_1042", "name": "Blue Water Bottle"}],
  "selection": null,
  "cartCount": 3,
  "signedIn": true,
  "lastAction": "Added Blue Water Bottle to cart",
  "tasks": {"upload": {"status": "running", "percent": 40}}
}
```

- `screen` uses the same names `openScreen` accepts; detail screens add `screenParams`.
- `visible` lists the entities on screen in display order: only what's actually visible,
  not the whole list behind it.
- `lastAction` is a human-readable line saying what just changed.
- `tasks` holds long operations in progress.

A good place to call it is wherever the source of truth already notifies the UI: a
`LiveData`/`StateFlow` observer, a repository listener, or the model method that every change
goes through. If the app changes state rapidly, debounce the push to
about 200 ms. It's safe to call while nothing is connected: state reported while disconnected
is held and delivered when the app connects.

nowrun clears its copy of the app's state each time the app connects, which happens on
every return to the foreground. State already delivered is not sent again, so push it on
every `onResume()` as well, or nowrun has no state until the next change.

---

## Phase 6 — Verify

1. **Check that it compiles:** `./gradlew assembleDebug` must succeed.
2. **Check that the SDK starts,** if a phone or emulator is connected (`adb devices`); skip
   this otherwise. Install the debug APK, bring the app to the foreground and run
   `adb logcat -s NowrunSdkService`. You should see
   `nowrun service not found, retrying every 1000ms, up to 10 times`, then `gave up after 10 attempts`.
   That confirms `start()` ran while the app was in front; nowrun isn't there locally.
   If there's no output at all, `start()` isn't being called from the activity that was
   launched. Also look for `not publishing …` warnings: the SDK left out a public method
   without `@Describe`, a Kotlin `internal` function or an overload.
3. **Check what the SDK will expose.** It publishes each public method of the *compiled*
   `AppFunctions` class that carries `@Describe`, and step 2's log names every other public
   method. Without a device, list the public methods from the build output:
   ```bash
   find . -path '*/build/*' -name AppFunctions.class
   javap -public -cp <classes dir> com.example.app.nowrun.AppFunctions
   ```
   Don't guess the folder: it depends on the module, the build variant and the Android Gradle
   Plugin version, e.g. `app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes`
   or `build/intermediates/javac/<variant>/classes` for Java, and
   `build/tmp/kotlin-classes/<variant>` for Kotlin. Split the path `find` prints in two: the
   part before the package folders (`com/example/…`) is `-cp`, a folder, never the `.class`
   file; the rest, with `/` turned into `.` and `.class` dropped, is the class name. That's the
   Java package from the `package` line, which can differ from the app's `applicationId`.
   Ignore methods named `access$…`: they're compiler-generated and the SDK skips them. Check
   that:
   - every intended function carries `@Describe`, and no other method does;
   - no name appears twice: the SDK publishes only the first overload, and a name containing
     `$` (a Kotlin `internal` function) not at all;
   - in the source, every function and parameter has a `@Describe`, and each parameter's
     description starts with its name.

**Testing on nowrun is done by the user.** Getting the app onto nowrun and having nowrun
call the functions aren't covered by this skill: don't attempt them or invent steps for
them. Instead, write the user a test plan to run on nowrun, covering:
- **each function** with valid arguments; a wrong type (`"abc"` for an `int`); an
  out-of-range value; an unknown id; an unknown choice; and, for destructive functions,
  `confirm=false`. Each failure must come back with a code from the error-code table and a
  message saying how to fix the call; after each success, the screen and pushed state must
  match the reply;
- **navigation:** `openScreen` for every value, `open<Entity>` for every entity, and
  `goBack()`. Each must land on the right screen and report it in state;
- **if the app is minified:** that the minified build still connects and lists its
  functions.

---

## Phase 7 — Hand over

Tell the user:
- that the app compiles, and the test plan from phase 6;
- which functions were added, grouped as navigation, content and actions;
- what was deliberately left out and why (credentials, payments, anything gated);
- where the functions layer lives, and the rule for new features: every new screen gets an
  `openScreen` value, and every new user action gets a function or a parameter on an
  existing one;
- that the SDK is the file `app/libs/nowrun-sdk.aar`: to update, download it again from
  `https://downloads.nowrun.io/latest/nowrun-sdk.aar` over that file, and rebuild.

## Checklist

- [ ] `nowrun-sdk.aar` is in `app/libs` and `implementation files(...)` is added; no manual manifest entries.
- [ ] `Nowrun.start(this)` runs in every launchable activity, not in `Application`; `AppFunctions` has no public static methods.
- [ ] Keep rules are in place if minified.
- [ ] The app compiles (`./gradlew assembleDebug`); no build setup changes were made.
- [ ] Baseline is complete: `getState`, `openScreen` covering every screen, `goBack`, `open<Entity>`, `search<Entities>`.
- [ ] Every function and parameter has an `@Describe`; parameter descriptions start with the name.
- [ ] Only types from the common-types table; few parameters; no overloads; ids are strings.
- [ ] All functions return `Results.ok`/`Results.fail` with the standard error codes.
- [ ] UI work goes through `MainThread.call`; no `Activity` held; shared state is thread-safe; no function blocks without a timeout.
- [ ] Destructive actions take `confirm`; credentials and payments aren't exposed.
- [ ] State is pushed on every change and on resume, and `getState()` returns the same shape.
- [ ] The user has a test plan covering every function (valid and invalid arguments) and every navigation target.
