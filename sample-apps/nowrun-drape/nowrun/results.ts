import type { ErrorCode } from '../engine';

/** Success. response: a short message, or an object with what the caller needs next. */
export const ok = (response: unknown) => ({ success: true, response });

/** Failure. message tells the caller how to fix the call: valid values, which function to use first. */
export const fail = (code: ErrorCode, message: string) => ({ success: false, error: { code, message } });
