# Common argument types

The types every declared argument must use, and why. Read this before writing any `ToolSpec`.

Use only these types. Each one converts the same way every time, so calls don't fail on type
problems. The same table is in the nowrun-java skill, so apps on both platforms expose the
same shapes. The SDK checks every declared type before your handler runs, and the handler
receives the converted value.

| Kind of value | Declare as | Handler receives | Rules |
|---|---|---|---|
| Entity id | `type: 'String'` | `string` | Always a string, even for numeric ids: 64-bit numbers lose precision in JavaScript. |
| Free text | `type: 'String'` | `string` | Trim it. |
| Choice (enum) | `type: 'String'`, `values: [...]` | `string` | The SDK refuses anything outside `values`, naming the valid ones. Matching is exact, so use lowercase `snake_case` values. |
| Whole number | `type: 'int'` | `number` | State the range and whether it's 1-based. Positions shown to users are 1-based. |
| Decimal | `type: 'number'` | `number` | Never `float`. |
| Yes/no | `type: 'boolean'` | `boolean` | Also accepts `1/0`, `yes/no`, `on/off`. |
| Money | `type: 'number'` amount (+ `'String'` currency with `values` if the app has several) | `number` | Major units. Name the currency in the description. |
| Date | `type: 'String'` | `string` | ISO 8601 `YYYY-MM-DD`. |
| Date and time | `type: 'String'` | `string` | ISO 8601 with an offset, e.g. `2026-09-25T14:30:00+05:30`; never epoch numbers. |
| Duration | `type: 'int'`, seconds | `number` | Put the unit in the name: `duration_seconds`. |
| Color | `type: 'String'` | `string` | `#RRGGBB`. |
| URL | `type: 'String'` | `string` | Absolute. |
| List | `type: 'String[]'` (or `'int[]'`) | `string[]` | Ids, tags. A JSON array; `"sale,new"` also works. A choice list is `'String[]'` with `values`: each element is checked. |
| Small structured value | `type: 'JSONObject'`, `fields: [...]` | object | Each field is declared and checked, and an unknown field is refused. One level deep; prefer separate arguments. |
| Optional text | `type: 'String'` | key missing | Tell the caller "empty to skip". An empty argument is left out of `args`. |
| Optional number / flag / list | its type | key missing | Tell the caller "null to skip". A null argument is left out of `args` too. |

**Avoid:** `long` for ids, `short`, `byte`, `float`, `JSONObject` without `fields`,
`JSONArray`, custom type names (they're passed through unchecked), and long argument
lists (SKILL.md 3.2).

**Always pass `tools` to `useNowrun`**, with one `ToolSpec` for every handler:
`useNowrun(handlers, { tools: TOOLS })`. `tools` is where argument names, types and
descriptions come from: JavaScript can't report them, so without it every function is
published as taking one untyped `args` object. The agent then has to guess what to send, and
the SDK can't check it.

For a value that doesn't fit its type, the SDK refuses the call before your handler runs and
answers `INVALID_ARGUMENTS` with a reason such as `quantity expects a valid Integer` or
`screen expects one of ["home","cart"]`. Your own checks (ranges, ids) must answer in that same shape.
