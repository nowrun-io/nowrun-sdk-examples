# Navigation recipes

How `open_screen`, `open_<entity>` and `go_back` reach each kind of navigator.

Put navigation in one small module (`nowrun/navigation.ts`), so handlers never touch
navigator internals. The names it uses come from the other references: `SCREENS` and `Screen`
from `tools.ts` in [code.md](code.md), and `fail` from `results.ts` in [replies.md](replies.md).
`RootParamList` is your app's React Navigation param list type, and `publishState` stands for
whatever pushes your state, such as `() => notifyState(snapshot())`.

**React Navigation:**

Give each mounted `NavigationContainer` its own ref, and have handlers use the most recently
mounted one. Never share one module-level ref: nowrun can change the display's size, density
and locale while the app runs, which makes Android recreate the activity (Expo's default
`configChanges` don't cover density or locale). The app is then mounted again, and the old
`NavigationContainer` can unmount after the new one has mounted. React then sets the ref it was
given to `null`, so a shared ref stays `null` while the new screen renders normally, and every
navigation call fails as if the app were still starting.

```ts
import { useEffect, useState } from 'react';
import { createNavigationContainerRef } from '@react-navigation/native';

const createRef = () => createNavigationContainerRef<RootParamList>();
let active: ReturnType<typeof createRef> | null = null;   // the most recently mounted container's

export function useNavigationRef() {
  const [ref] = useState(createRef);
  useEffect(() => {
    active = ref;
    return () => { if (active === ref) active = null; };   // clear only our own
  }, [ref]);
  return ref;
}
// const navigationRef = useNavigationRef();
// <NavigationContainer ref={navigationRef} onReady={publishState} onStateChange={publishState}>

const isReady = () => active?.isReady() ?? false;

// Resolves true once a navigator is mounted, false after timeoutMs; await it in handlers
const whenReady = (timeoutMs = 3000) => new Promise<boolean>((resolve) => {
  const started = Date.now();
  const check = () => (isReady() || Date.now() - started >= timeoutMs ? resolve(isReady()) : setTimeout(check, 100));
  check();
});

export const navigation = {
  whenReady,
  openScreen: (screen: Screen) => active!.navigate(ROUTE_FOR[screen] as never),
  openProduct: (id: string) => active!.navigate('Product', { id }),
  back: () => (active!.canGoBack() ? (active!.goBack(), true) : false),
  current: () => (isReady() ? active!.getCurrentRoute() : undefined),   // { name, params } for state
};
```

Handlers that navigate are `async` and start with
`if (!(await navigation.whenReady())) return fail('APP_FUNCTION_FAILED', 'screens not loaded yet; try again');`.
This settles within the timeout, so it never holds the session.

Nested navigators: `active.navigate('Tabs', { screen: 'Cart' })`.

**expo-router:**
```ts
import { router } from 'expo-router';
export const navigation = {
  openScreen: (screen: Screen) => router.navigate(PATH_FOR[screen]),   // e.g. '/cart', '/(tabs)/orders'
  openProduct: (id: string) => router.push(`/product/${id}`),
  back: () => (router.canGoBack() ? (router.back(), true) : false),
};
// Track the current screen for state with usePathname() and useLocalSearchParams() in the root layout.
```

**Tabs or steps held in `useState`** (a screen that switches its content without a route):
move the value into the store, or have the handler call the state setter, e.g. `setTab(tab)`.

`ROUTE_FOR` or `PATH_FOR` maps every value in `SCREENS` to a route, so the declared `values`
and the real routes can't drift apart. Handlers run on the JS thread, so calling navigation
from them is safe.
