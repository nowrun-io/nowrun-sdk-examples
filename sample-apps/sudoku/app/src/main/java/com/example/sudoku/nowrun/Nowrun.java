package com.example.sudoku.nowrun;

import android.app.Activity;

import io.nowrun.sdk.NowrunSdkService;

/** The entry point activities call. It isn't the functions object, so its methods aren't published. */
public final class Nowrun {
    private Nowrun() {}

    /** Call from onCreate() of every activity the app can launch into. */
    public static void start(Activity activity) {
        NowrunSdkService.start(activity, AppFunctions.get());
    }

    /** Call from onResume(): the player clears its copy of the state each time the app connects. */
    public static void reportState() {
        StateReporter.push();
    }
}
