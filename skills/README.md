# nowrun integration skills

These skills let an AI coding agent integrate nowrun into your Android app: it adds the SDK,
reads your app to work out what it does, and exposes its actions, content and navigation as
functions an AI agent can call through nowrun.

Each skill is a folder with a `SKILL.md` and a `references/` folder, all plain Markdown, so it
works with any coding agent.

| Skill | For apps built with |
|---|---|
| `nowrun-java/` | Java or Kotlin (native Android, Views or Compose) |
| `nowrun-expo/` | React Native: Expo, or bare React Native |

## What you need

- The skill for your app's platform (the whole folder, including `references/`). Each one is
  also published as a zip that unpacks to that folder:
  - Java / Kotlin: `https://downloads.nowrun.io/latest/skills/nowrun-java.zip`
  - React Native: `https://downloads.nowrun.io/latest/skills/nowrun-expo.zip`
- The nowrun SDK file for that platform. The skill downloads it for you:
  - Java / Kotlin: `https://downloads.nowrun.io/latest/nowrun-sdk.aar`
  - React Native: `https://downloads.nowrun.io/latest/nowrun-expo.tgz`
- A coding agent that runs in your app's repository and can read and edit files and run
  shell commands, because it has to run the build.

## Install

Copy the skill folder into your app's repository. If your agent supports skills
(`SKILL.md` folders), put the folder in the directory the agent loads skills from, so the
agent picks it up by itself. Check your agent's docs for that directory; for Claude Code it's
`.claude/skills/`. Otherwise any directory works, e.g. `skills/`:

```bash
# Replace <skills-dir> with your agent's skills directory, or e.g. skills/

# Java / Kotlin app
mkdir -p <skills-dir> && cp -r <path-to>/nowrun-java <skills-dir>/

# React Native app
mkdir -p <skills-dir> && cp -r <path-to>/nowrun-expo <skills-dir>/
```

Commit the skill folder with the app, so everyone working on it gets the same skill.

The skill downloads the SDK file to where the app uses it (`app/libs/` or `vendor/`).

## Use

Start your agent in the app's repository and ask for it, e.g.:

> Integrate nowrun into this app.

An agent that supports skills finds the right one by itself. With any other agent, name the
skill file in the request:

> Follow `skills/nowrun-java/SKILL.md` to integrate nowrun into this app.

The agent will then:
1. add the SDK;
2. read the app and propose the functions to expose, grouped as navigation, content and
   actions, and wait for your go-ahead;
3. write the functions layer and report the app's state;
4. check that the app compiles, and give you a test plan to run on nowrun.

When you add a feature later, ask the agent to expose it too, e.g. "expose the new wishlist
screen to nowrun"; the skill keeps new screens and actions in step with the functions.

## Updating

To update the SDK, ask the agent to download it again; it uses the same URL. To update a
skill, download its zip again from the URL above and replace the skill folder with it.

## License

The skills are licensed under the [PolyForm Shield License 1.0.0](LICENSE.md): you may use them,
and the code they write into your app, for anything except building a product that competes with
nowrun. Each skill folder carries its own copy.
