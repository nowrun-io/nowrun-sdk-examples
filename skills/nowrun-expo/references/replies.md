# Replies and errors

How every handler answers, the error codes to use, and how nowrun reads a reply.

## `results.ts`: the reply format

nowrun reads a reply as an envelope. Only `response` reaches the caller; any other field
is dropped. A handler that throws becomes a generic `APP_FUNCTION_FAILED`, so return `fail()`
for anything you can explain.

```ts
export type ErrorCode =
  | 'INVALID_ARGUMENTS' | 'NOT_FOUND' | 'AMBIGUOUS' | 'NEEDS_CONFIRMATION' | 'NOT_ALLOWED' | 'APP_FUNCTION_FAILED';

/** response: a short message, or an object with what the caller needs next. */
export const ok = (response: unknown) => ({ success: true, response });

/** message tells the caller how to fix the call: valid values, which function to use first. */
export const fail = (code: ErrorCode, message: string) => ({ success: false, error: { code, message } });
```

**Error codes.** Use these, so agents can handle every app the same way:

| Code | When |
|---|---|
| `INVALID_ARGUMENTS` | Out of range, bad format. Name the valid values. The SDK uses this code for type failures. |
| `NOT_FOUND` | Unknown id. Say which `search_…` function finds valid ids. |
| `AMBIGUOUS` | More than one match. List the candidates with their ids. |
| `NEEDS_CONFIRMATION` | Destructive action called with `confirm` false. Summarize the effect. |
| `NOT_ALLOWED` | The UI wouldn't allow it right now: login needed, wrong screen, locked item. Say what's needed. |
| `APP_FUNCTION_FAILED` | Anything else. |

A successful `response` should say what happened and include what the caller needs next:
`{ message: 'Added Blue Water Bottle', cart_count: 3 }`. Keep replies under 128 KiB: a bigger
one fails the call with `RESULT_TOO_LARGE`. For lists that can grow large, return a bounded
number and take a `limit` or `page` argument.

## How nowrun reads a reply

nowrun reads each reply one of two ways:

| Reply | Read as |
|---|---|
| A JSON object with a boolean `success` | **An envelope.** `success: true`: the call succeeded and only `response` is kept (a missing `response` becomes `null`). `success: false`: the call failed with `error.code` and `error.message`. A missing code becomes `APP_FUNCTION_FAILED`, and a missing message falls back to the `response` text. |
| Anything else | **A success.** JSON text becomes that value, and other text stays a string. Only an envelope can report a failure. |

So:
- **Always reply through `ok()` / `fail()`.** A failure only counts as one when it's an
  envelope.
- **Never return a raw object that has a boolean `success` field of its own,** e.g. a
  `{"success": true, "items": [...]}` from the app's API. It's read as an envelope and
  everything except `response` is lost. Wrap it: `return ok(apiReply)`.
- **Keep replies under 128 KiB.** A bigger one fails the call with `RESULT_TOO_LARGE`.
- **Answer within 60 seconds.** After that nowrun reports the call as `unknown` with
  `APP_RESULT_TIMEOUT`. An answer that arrives later still replaces it, but the caller may
  have moved on.

**`INVALID_ARGUMENTS` can come from two places,** with the same result for the caller: the
code and a message saying how to fix the call.
- **The SDK and nowrun,** before your code runs: a wrong number of arguments, a value of the
  wrong type or outside `values`. Your code never sees such a call.
- **Your own checks:** ranges, formats, ids. Reply with `fail('INVALID_ARGUMENTS', …)` and name the
  valid values.
