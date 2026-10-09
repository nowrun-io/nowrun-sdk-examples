# Common argument types

The types every exposed parameter must use, and why. Read this before writing any function signature.

Use only these types. Each one converts the same way every time, so calls don't fail on type
problems. The same table is in the nowrun-expo skill, so apps on both platforms expose the
same shapes.

| Kind of value | Java parameter | Send it as | Rules |
|---|---|---|---|
| Entity id | `String` | `"p_1042"` | Always a string, even for numeric ids: 64-bit numbers lose precision in JavaScript agents. |
| Free text | `String` | `"red shoes"` | Trim it; treat `""` as "not given". |
| Choice (enum) | `String` | `"price_low"` | List the exact values in `@Describe`. Match case-insensitively, and on a miss return `INVALID_ARGUMENTS` naming the valid values. Don't use Java `enum` parameters: the contract shows only the enum's class name, and matching is case-sensitive. |
| Whole number | `int` | `3` | State the range and whether it's 1-based. Positions shown to users are 1-based. |
| Decimal | `double` | `4.5` | Never `float`. |
| Yes/no | `boolean` | `true` | Also accepts `1/0`, `yes/no`, `on/off`. |
| Money | `double` amount (+ `String` currency if the app has several) | `19.99`, `"USD"` | Major units. Name the currency in the description. |
| Date | `String` | `"2026-09-25"` | ISO 8601 `YYYY-MM-DD`; parse with `LocalDate.parse` (API 26+) or `SimpleDateFormat`. |
| Date and time | `String` | `"2026-09-25T14:30:00+05:30"` | ISO 8601 with an offset; never epoch numbers. |
| Duration | `int` seconds | `90` | Put the unit in the name: `durationSeconds`. |
| Color | `String` | `"#1E88E5"` | `#RRGGBB`. |
| URL | `String` | `"https://…"` | Absolute. |
| List | `String[]` or `List<String>` | `["sale","new"]` | Ids, tags. A JSON array; `"sale,new"` also works. |
| Optional number / flag | `Integer`, `Double`, `Boolean` (boxed) | `null` to skip | Primitives can't be null. Say "null to skip" in the description. |
| Optional text | `String` | `""` to skip | Say "empty to skip". You receive `""`, or `null` if the caller sent null. |

**Avoid:** `char`, `byte`, `short`, `float`, `long` for ids, `Map`, `JSONObject`/`JSONArray`
parameters, your own classes as parameters (the contract doesn't list their fields, so an LLM
has to guess them), abstract or polymorphic types (sealed classes, `@type`), overloaded method
names (the SDK publishes only the first, with a warning), varargs, and long parameter lists
(SKILL.md 3.2). Flatten structured input into separate parameters instead.

For a value that doesn't fit its type, the SDK refuses the call before your code runs and
answers `INVALID_ARGUMENTS` with a reason such as `arg1 expects a valid int`. Your own checks
(ranges, choices, ids) must answer in that same shape.
