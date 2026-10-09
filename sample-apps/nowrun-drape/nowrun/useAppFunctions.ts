// The handlers: one per function in tools.ts, each going through the app's own dispatcher.

import { useMemo } from 'react';
import type { Handlers } from 'nowrun-expo';
import type { Command, CommandResult } from '../engine';
import type { Sku } from '../catalog';
import { fail, ok } from './results';
import { TOOLS } from './tools';

/**
 * run: the app's dispatcher, the same one the buttons use. It applies a command synchronously
 * and returns the result, so a handler can answer with what actually happened.
 * snapshot: the state the app pushes, so get_state and pushed state never disagree.
 */
export function useAppFunctions(run: (cmd: Command) => CommandResult, snapshot: () => unknown): Handlers {
  return useMemo<Handlers>(() => {
    const handlers: Handlers = Object.fromEntries(
      TOOLS.map(({ functionName }) => [
        functionName,
        (args: Record<string, unknown>) => {
          const res = run({ tool: functionName, ...args } as Command);
          if (res.error) return fail(res.error.code, res.error.message);
          return ok(res.data === undefined ? res.reply : { message: res.reply, ...(res.data as object) });
        },
      ]),
    );

    handlers.get_state = () => ok(snapshot());

    // Just the fields an agent needs to pick an item, not the rendering details.
    handlers.search_catalog = (args) => {
      const res = run({ tool: 'search_catalog', ...args } as Command);
      const items = (res.data as Sku[]).map(({ id, name, slot, collection, tone, tags, price }) => ({
        id, name, slot, collection, tone, tags, price,
      }));
      return ok(items);
    };

    return handlers;
    // run and snapshot read the app's latest state through refs, so the handlers never go stale.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}
