package com.example.sudoku.nowrun;

import org.json.JSONException;
import org.json.JSONObject;

/** The reply envelope every function returns. Only response reaches the caller. */
final class Results {
    private Results() {}

    /** Success. response: a short message, or a JSONObject/JSONArray with what the caller needs next. */
    static String ok(Object response) {
        try {
            return new JSONObject().put("success", true).put("response", response).toString();
        } catch (JSONException e) {
            return fail("APP_FUNCTION_FAILED", "could not encode the result");
        }
    }

    /** Failure. message tells the caller how to fix the call: valid values, which function to use first. */
    static String fail(String code, String message) {
        try {
            return new JSONObject().put("success", false)
                    .put("error", new JSONObject().put("code", code).put("message", message)).toString();
        } catch (JSONException e) {
            return "{\"success\":false,\"error\":{\"code\":\"APP_FUNCTION_FAILED\",\"message\":\"could not encode the failure\"}}";
        }
    }
}
