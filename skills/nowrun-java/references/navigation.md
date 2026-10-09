# Navigation recipes

How `openScreen`, `open<Entity>` and `goBack` reach each kind of app structure. Run all of these on the main thread (`MainThread.call`).

The helpers these recipes use are in the code references: `MainThread` and `CurrentActivity` in
[java-code.md](java-code.md) (or [kotlin-code.md](kotlin-code.md)), and `Results` in
[replies.md](replies.md). `openScreen` there shows the whole shape of a navigation function.

## Java

| App structure | Navigate with |
|---|---|
| Separate activities | `activity.startActivity(new Intent(activity, CartActivity.class))`. To go back to an existing one without stacking, add `FLAG_ACTIVITY_CLEAR_TOP \| FLAG_ACTIVITY_SINGLE_TOP`. |
| Bottom navigation or tabs | `bottomNav.setSelectedItemId(R.id.nav_cart)` or `tabLayout.getTabAt(i).select()`. This goes through the same listener a tap would. |
| Navigation component | `Navigation.findNavController(activity, R.id.nav_host).navigate(R.id.cartFragment, args)`. |
| Compose `NavHost` | Hold the `NavController` in an app-level holder and call `navController.navigate("cart")`. |
| Existing deep links | `activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("myapp://product/" + id)).setPackage(activity.getPackageName()))`. |
| Back | See [Back](#back) below. |

`open<Entity>(id)` checks that the id exists (`NOT_FOUND` if not), then opens the detail
screen the same way the list item's click does.

### Back

`CurrentActivity.get()` returns a plain `Activity`, but the back dispatcher belongs to androidx's
`ComponentActivity`, which every `AppCompatActivity` and `FragmentActivity` is. On the app's
first screen, back would close the app, which disconnects it before the reply is sent, so
answer `NOT_ALLOWED` there instead:

```java
@Describe("Goes back, like the system back action. Changes nothing else.")
public String goBack() throws Exception {
    return MainThread.call(() -> {
        Activity activity = CurrentActivity.get();
        if (!(activity instanceof ComponentActivity)) return Results.fail("NOT_ALLOWED", "the app is not in front");
        OnBackPressedDispatcher back = ((ComponentActivity) activity).getOnBackPressedDispatcher();
        // Fragments and the Navigation component register enabled callbacks while they can go back.
        if (activity.isTaskRoot() && !back.hasEnabledCallbacks()) {
            return Results.fail("NOT_ALLOWED", "already at the first screen");
        }
        back.onBackPressed();
        return Results.ok("went back");
    });
}
```

It needs `androidx.activity.ComponentActivity` and `androidx.activity.OnBackPressedDispatcher`.
An app without androidx calls `activity.onBackPressed()` instead, after the same
`isTaskRoot()` check.

## Kotlin and Compose

Views-based Kotlin apps use the same calls as the Java table, in Kotlin syntax:
`activity.startActivity(Intent(activity, CartActivity::class.java))`,
`bottomNav.selectedItemId = R.id.nav_cart`,
`activity.findNavController(R.id.nav_host).navigate(R.id.cartFragment)`.

Back, with the same guard as the [Java recipe](#back):

```kotlin
@Describe("Goes back, like the system back action. Changes nothing else.")
fun goBack(): String = MainThread.call {
    val activity = CurrentActivity.get() as? ComponentActivity
        ?: return@call Results.fail("NOT_ALLOWED", "the app is not in front")
    val back = activity.onBackPressedDispatcher
    // Fragments and the Navigation component register enabled callbacks while they can go back.
    if (activity.isTaskRoot && !back.hasEnabledCallbacks()) {
        return@call Results.fail("NOT_ALLOWED", "already at the first screen")
    }
    back.onBackPressed()
    Results.ok("went back")
}
```

A Compose app navigates through its `NavHostController`, which lives inside a composable.
Hand it to the functions layer through an app-level holder:

```kotlin
object NavHolder {
    @Volatile var controller: NavHostController? = null
}

// In the composable that creates the NavHost:
val nav = rememberNavController()
DisposableEffect(nav) {
    NavHolder.controller = nav
    onDispose { if (NavHolder.controller === nav) NavHolder.controller = null }
}
NavHost(nav, startDestination = "home") { /* ... */ }
```

Then, inside `MainThread.call`:

| Action | Call |
|---|---|
| Open a screen | `NavHolder.controller?.navigate(route) { launchSingleTop = true }`. With type-safe routes, `navigate(Cart)`. |
| Open an entity | `navigate("product/$id")`, or `navigate(Product(id))` with type-safe routes. |
| Back | `NavHolder.controller?.popBackStack()`. It returns `false` when there's nothing to go back to: answer `NOT_ALLOWED`. |
| Current screen, for state | `controller.currentBackStackEntry?.destination?.route`. Push state from `controller.addOnDestinationChangedListener { _, _, _ -> … }`. |

If `NavHolder.controller` is `null`, the Compose UI isn't showing: answer `NOT_ALLOWED`.
