# Functions layer in Java

Complete code for the functions layer in a Java app: `Results` is in [replies.md](replies.md); these are the other four files. For a Kotlin app use [kotlin-code.md](kotlin-code.md) instead.

## `MainThread`: UI work from a call

Views and navigation must be touched on the main thread. A call must also report what
actually happened, so run the work there and wait for it:

```java
package com.example.app.nowrun;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class MainThread {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private MainThread() {}

    /**
     * Runs work on the main thread and returns its result. Work that hasn't started within 2
     * seconds is withdrawn, so it can't run after the call has been answered as failed; work that
     * has started is waited for, so a call is never answered as failed while its work runs.
     */
    static <T> T call(Callable<T> work) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return work.call();
        FutureTask<T> task = new FutureTask<>(work);
        AtomicBoolean started = new AtomicBoolean();
        MAIN.post(() -> { if (started.compareAndSet(false, true)) task.run(); });
        try {
            return task.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (started.compareAndSet(false, true)) throw e;  // it never started, and now never will
            return task.get();                                // it has started: let it finish
        }
    }
}
```

## `CurrentActivity`: the activity in front

```java
package com.example.app.nowrun;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

final class CurrentActivity implements Application.ActivityLifecycleCallbacks {
    private static volatile Activity resumed;
    private static boolean installed;

    static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new CurrentActivity());
    }

    static Activity get() { return resumed; }

    @Override public void onActivityResumed(Activity a) { resumed = a; }
    @Override public void onActivityPaused(Activity a) { if (resumed == a) resumed = null; }
    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
```

When the SDK starts in the first activity's `onCreate()`, `onActivityResumed` hasn't fired
yet, so `CurrentActivity` catches it.

## `AppFunctions`: the shape

```java
package com.example.app.nowrun;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import io.nowrun.sdk.NowrunSdkService.Describe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class AppFunctions {
    private static volatile AppFunctions instance;

    /**
     * One instance for the process. Package-private: activities go through Nowrun.start.
     */
    static AppFunctions get(Context context) {
        if (instance == null) {
            synchronized (AppFunctions.class) {
                if (instance == null) {
                    Application app = (Application) context.getApplicationContext();
                    CurrentActivity.install(app);
                    instance = new AppFunctions(app);
                }
            }
        }
        return instance;
    }

    private static final String[] SCREENS = {"home", "search", "cart", "orders", "settings"};

    private final Application app;
    private final ShopRepository shop;   // the app's own source of truth

    private AppFunctions(Application app) {
        this.app = app;
        this.shop = ShopRepository.get(app);
    }

    @Describe("Where the user is and what they see: screen, visible items, cart count. Same shape as pushed state.")
    public String getState() {
        return Results.ok(StateReporter.snapshot());
    }

    @Describe("Opens a top-level screen. Changes nothing else.")
    public String openScreen(
            @Describe("screen: one of home, search, cart, orders, settings.") String screen) throws Exception {
        final String target = screen == null ? "" : screen.trim().toLowerCase(Locale.US);
        if (!java.util.Arrays.asList(SCREENS).contains(target)) {
            return Results.fail("INVALID_ARGUMENTS", "screen must be one of " + TextUtils.join(", ", SCREENS));
        }
        return MainThread.call(() -> {
            Activity activity = CurrentActivity.get();
            if (activity == null) return Results.fail("NOT_ALLOWED", "the app is not in front");
            Navigator.open(activity, target);   // the same routine the tab bar or drawer uses
            return Results.ok("showing " + target);
        });
    }

    @Describe("Finds products by name. Returns up to 20 as {id, name, price}. Empty query lists featured products.")
    public String searchProducts(
            @Describe("query: text to match in product names. Empty for featured products.") String query) {
        JSONArray out = new JSONArray();
        for (Product p : shop.search(query == null ? "" : query.trim(), 20)) {
            try {
                out.put(new JSONObject().put("id", p.id).put("name", p.name).put("price", p.price));
            } catch (org.json.JSONException ignored) {
            }
        }
        return Results.ok(out);
    }

    @Describe("Removes every item from the cart. Irreversible.")
    public String clearCart(
            @Describe("confirm: true to clear; false returns what would be removed, changing nothing.") boolean confirm) {
        int count = shop.cartCount();
        if (!confirm) return Results.fail("NEEDS_CONFIRMATION", "would remove " + count + " items from the cart");
        shop.clearCart();   // the repository notifies observers, so the UI and StateReporter update
        return Results.ok("removed " + count + " items");
    }
}
```

## `Nowrun`: the entry point for activities

Activities live in another package, so they can't reach the package-private
`AppFunctions.get`. They call this instead. It isn't the functions object, so its public
method isn't published.

```java
package com.example.app.nowrun;

import android.app.Activity;

import io.nowrun.sdk.NowrunSdkService;

public final class Nowrun {
    private Nowrun() {}

    /** Call from onCreate() of every activity the app can launch into. */
    public static void start(Activity activity) {
        NowrunSdkService.start(activity, AppFunctions.get(activity));
    }
}
```
