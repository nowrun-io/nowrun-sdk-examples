# Functions layer code

Complete example of `nowrun/tools.ts` and `nowrun/useAppFunctions.ts`. `nowrun/results.ts` is in [replies.md](replies.md), and `nowrun/navigation.ts` in [navigation.md](navigation.md).

## `tools.ts`: the declarations

```ts
import type { ToolSpec } from 'nowrun-expo';

export const SCREENS = ['home', 'search', 'cart', 'orders', 'settings'] as const;
export type Screen = (typeof SCREENS)[number];

export const TOOLS: ToolSpec[] = [
  { functionName: 'get_state', args: [], returns: 'String',
    functionDescription: 'Where the user is and what they see: screen, visible items, cart count. Same shape as pushed state.' },
  { functionName: 'open_screen', returns: 'String',
    functionDescription: 'Opens a top-level screen. Changes nothing else.',
    args: [{ name: 'screen', type: 'String', values: [...SCREENS], description: 'The screen to show.' }] },
  { functionName: 'go_back', args: [], returns: 'String', functionDescription: 'Goes back one screen, like the back button.' },
  { functionName: 'open_product', returns: 'String',
    functionDescription: 'Opens a product\'s detail screen.',
    args: [{ name: 'id', type: 'String', description: 'Product id, from search_products or get_state, e.g. "p_1042".' }] },
  { functionName: 'search_products', returns: 'String',
    functionDescription: 'Finds products by name. Returns up to 20 as {id, name, price}. Read-only.',
    args: [{ name: 'query', type: 'String', description: 'Text to match in product names. Empty for featured products.' }] },
  { functionName: 'add_to_cart', returns: 'String',
    functionDescription: 'Adds a product to the cart, or raises its quantity if already there. Returns the cart\'s item count.',
    args: [
      { name: 'product_id', type: 'String', description: 'Product id, from search_products or get_state.' },
      { name: 'quantity', type: 'int', description: 'How many to add, 1-99.' },
    ] },
  { functionName: 'clear_cart', returns: 'String',
    functionDescription: 'Removes every item from the cart. Irreversible.',
    args: [{ name: 'confirm', type: 'boolean', description: 'true to clear; false returns what would be removed, changing nothing.' }] },
];
```

## `useAppFunctions.ts`: the handlers

The handler keys must equal the `functionName`s. In development, the module warns about any
mismatch.

```ts
import { useMemo } from 'react';
import type { Handlers } from 'nowrun-expo';
import { useShop } from '../store';            // the app's own store (zustand here)
import { navigation } from './navigation';     // see navigation.md
import { SCREENS, type Screen } from './tools';
import { ok, fail } from './results';
import { snapshot } from './state';

export function useAppFunctions(): Handlers {
  return useMemo<Handlers>(() => ({
    get_state: () => ok(snapshot()),

    open_screen: ({ screen }) => {             // the SDK has already checked it against SCREENS
      navigation.openScreen(screen as Screen);
      return ok(`showing ${screen}`);
    },

    go_back: () => (navigation.back() ? ok('went back') : fail('NOT_ALLOWED', 'already at the first screen')),

    open_product: ({ id }) => {
      if (!useShop.getState().products[id]) return fail('NOT_FOUND', `no product ${id}; use search_products`);
      navigation.openProduct(id);
      return ok(`showing product ${id}`);
    },

    search_products: ({ query = '' }) =>
      ok(useShop.getState().search(String(query).trim(), 20).map(({ id, name, price }) => ({ id, name, price }))),

    add_to_cart: ({ product_id, quantity }) => {
      if (quantity < 1 || quantity > 99) return fail('INVALID_ARGUMENTS', 'quantity must be 1-99');
      const shop = useShop.getState();
      if (!shop.products[product_id]) return fail('NOT_FOUND', `no product ${product_id}; use search_products`);
      shop.addToCart(product_id, quantity);     // the same action the "Add" button calls
      return ok({ message: `Added ${shop.products[product_id].name}`, cart_count: useShop.getState().cartCount() });
    },

    clear_cart: ({ confirm }) => {
      const count = useShop.getState().cartCount();
      if (!confirm) return fail('NEEDS_CONFIRMATION', `would remove ${count} items from the cart`);
      useShop.getState().clearCart();
      return ok(`removed ${count} items`);
    },
  }), []);
}
```
