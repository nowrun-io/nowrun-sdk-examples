# Functions layer in Kotlin

Complete code for the functions layer in a Kotlin app: the same five files as
[java-code.md](java-code.md) and [replies.md](replies.md), written in Kotlin. Put them in one
package, e.g. `com.example.app.nowrun`.

The SDK exposes what the **compiled** class contains, so in `AppFunctions` every function
that isn't meant for the agent is `private`, and the instance lives in a `companion object`:
its methods compile onto a separate class, so they aren't exposed.

## `Results.kt`: the reply format

```kotlin
package com.example.app.nowrun

import org.json.JSONObject

internal object Results {
    /** Success. response: a short message, or a JSONObject/JSONArray with what the caller needs next. */
    fun ok(response: Any?): String =
        JSONObject().put("success", true).put("response", response ?: JSONObject.NULL).toString()

    /** Failure. message tells the caller how to fix the call: valid values, which function to use first. */
    fun fail(code: String, message: String): String =
        JSONObject().put("success", false)
            .put("error", JSONObject().put("code", code).put("message", message)).toString()
}
```

## `MainThread.kt`: UI work from a call

```kotlin
package com.example.app.nowrun

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal object MainThread {
    private val main = Handler(Looper.getMainLooper())

    /**
     * Runs work on the main thread and returns its result. Work that hasn't started within 2
     * seconds is withdrawn, so it can't run after the call has been answered as failed; work that
     * has started is waited for, so a call is never answered as failed while its work runs.
     */
    fun <T> call(work: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return work()
        val task = FutureTask(Callable { work() })
        val started = AtomicBoolean()
        main.post { if (started.compareAndSet(false, true)) task.run() }
        return try {
            task.get(2, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            if (started.compareAndSet(false, true)) throw e  // it never started, and now never will
            task.get()                                       // it has started: let it finish
        }
    }
}
```

## `CurrentActivity.kt`: the activity in front

```kotlin
package com.example.app.nowrun

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal object CurrentActivity : Application.ActivityLifecycleCallbacks {
    @Volatile private var resumed: Activity? = null
    private var installed = false

    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    fun get(): Activity? = resumed

    override fun onActivityResumed(activity: Activity) { resumed = activity }
    override fun onActivityPaused(activity: Activity) { if (resumed === activity) resumed = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
```

When the SDK starts in the first activity's `onCreate()`, `onActivityResumed` hasn't fired
yet, so `CurrentActivity` catches it.

## `AppFunctions.kt`: the shape

```kotlin
package com.example.app.nowrun

import android.app.Application
import android.content.Context
import io.nowrun.sdk.NowrunSdkService.Describe
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Each function marked with @Describe here is a nowrun function. */
class AppFunctions private constructor(app: Application) {

    private val shop = ShopRepository.get(app)   // the app's own source of truth

    @Describe("Where the user is and what they see: screen, visible items, cart count. Same shape as pushed state.")
    fun getState(): String = Results.ok(StateReporter.snapshot())

    @Describe("Opens a top-level screen. Changes nothing else.")
    fun openScreen(
        @Describe("screen: one of home, search, cart, orders, settings.") screen: String?,
    ): String {
        val target = screen?.trim()?.lowercase(Locale.US).orEmpty()
        if (target !in SCREENS) {
            return Results.fail("INVALID_ARGUMENTS", "screen must be one of ${SCREENS.joinToString()}")
        }
        return MainThread.call {
            val activity = CurrentActivity.get()
                ?: return@call Results.fail("NOT_ALLOWED", "the app is not in front")
            Navigator.open(activity, target)   // the same routine the tab bar or drawer uses
            Results.ok("showing $target")
        }
    }

    @Describe("Finds products by name. Returns up to 20 as {id, name, price}. Empty query lists featured products.")
    fun searchProducts(
        @Describe("query: text to match in product names. Empty for featured products.") query: String?,
    ): String {
        val out = JSONArray()
        for (p in shop.search(query?.trim().orEmpty(), 20)) {
            out.put(JSONObject().put("id", p.id).put("name", p.name).put("price", p.price))
        }
        return Results.ok(out)
    }

    @Describe("Removes every item from the cart. Irreversible.")
    fun clearCart(
        @Describe("confirm: true to clear; false returns what would be removed, changing nothing.") confirm: Boolean,
    ): String {
        val count = shop.cartCount()
        if (!confirm) return Results.fail("NEEDS_CONFIRMATION", "would remove $count items from the cart")
        shop.clearCart()   // the repository notifies observers, so the UI and StateReporter update
        return Results.ok("removed $count items")
    }

    companion object {
        private val SCREENS = listOf("home", "search", "cart", "orders", "settings")

        @Volatile private var instance: AppFunctions? = null

        /** One instance for the process: every activity passes the same one to NowrunSdkService.start. */
        fun get(context: Context): AppFunctions =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext as Application
                    CurrentActivity.install(app)
                    AppFunctions(app).also { instance = it }
                }
            }
    }
}
```

## `Nowrun.kt`: the entry point for activities

```kotlin
package com.example.app.nowrun

import android.app.Activity
import io.nowrun.sdk.NowrunSdkService

object Nowrun {
    /** Call from onCreate() of every activity the app can launch into. */
    fun start(activity: Activity) = NowrunSdkService.start(activity, AppFunctions.get(activity))
}
```

Start the SDK in each launchable activity exactly as in Java:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Nowrun.start(this)
}
```
