// The function list the nowrun agent sees: one ToolSpec per handler in useAppFunctions.ts.
// Choices use `values`, so the SDK refuses anything else before a handler runs.

import type { ToolSpec } from 'nowrun-expo';
import { CATALOG, COLLECTIONS, SLOTS } from '../catalog';
import { TAB_LABELS } from '../engine';

// Valid values, read from the catalog so the list never falls out of step with it.
const COLLECTION_KEYS = COLLECTIONS.map((c) => c.key).filter((k) => k !== 'all');
const TONES = Array.from(new Set(CATALOG.map((s) => s.tone))).sort();
const TAGS = Array.from(new Set(CATALOG.flatMap((s) => s.tags))).sort();
const TABS = Object.keys(TAB_LABELS);

const idsWhere = (keep: (id: string) => boolean) =>
  SLOTS.map((slot) => `${slot}: ${CATALOG.filter((s) => s.slot === slot && keep(s.id)).map((s) => s.id).join(', ')}`)
    .filter((entry) => !entry.endsWith(': '))
    .join('; ');
const TINTABLE_IDS = idsWhere((id) => !!CATALOG.find((s) => s.id === id)?.tintable);

export const TOOLS: ToolSpec[] = [
  {
    functionName: 'get_state',
    returns: 'String',
    functionDescription:
      'Where the user is and what they see: the screen, the outfit (slot to item id), the palette and colour ' +
      'overrides, saved looks with their ids, the try-on status, and the whole catalogue. Same shape as pushed state.',
    args: [],
  },
  {
    functionName: 'search_catalog',
    returns: 'String',
    functionDescription:
      'Finds catalogue items, returning each as {id, name, slot, collection, tone, tags, price}. Read-only: ' +
      'use swap_item or set_outfit to put an item on.',
    args: [
      { name: 'query', type: 'String', description: 'Text to match in item names. Empty to skip.' },
      { name: 'slot', type: 'String', values: SLOTS, description: 'Only items for this slot. Empty for any slot.' },
      { name: 'collection', type: 'String', values: COLLECTION_KEYS, description: 'Only items from this collection. Empty for any.' },
      {
        name: 'tags',
        type: 'String[]',
        values: [...TAGS, ...TONES],
        description: 'Tags an item must all have, e.g. ["beach","summer"]; a tone also matches. Empty to skip.',
      },
    ],
  },
  {
    functionName: 'set_outfit',
    returns: 'String',
    functionDescription:
      'Sets several slots at once. A dress replaces a top and bottom, and the other way round. ' +
      'Use swap_item to change one slot.',
    args: [
      { name: 'replace', type: 'boolean', description: 'true clears every slot first; false only changes the slots given.' },
      {
        name: 'slots',
        type: 'JSONObject',
        fields: SLOTS.map((slot) => ({ name: slot, type: 'String', description: `The ${slot} item id.` })),
        description: 'Slot to item id, e.g. {"top":"alp_01","shoes":"alp_08"}. Each id must be an item for that slot, from search_catalog.',
      },
    ],
  },
  {
    functionName: 'swap_item',
    returns: 'String',
    functionDescription: 'Puts one item into its slot, replacing whatever was there, like tapping "Add to look".',
    args: [
      { name: 'slot', type: 'String', values: SLOTS, description: 'The slot to fill.' },
      { name: 'id', type: 'String', description: 'The item id, from search_catalog or get_state. It must be an item for that slot.' },
    ],
  },
  {
    functionName: 'clear_slot',
    returns: 'String',
    functionDescription: 'Empties one slot of the outfit.',
    args: [{ name: 'slot', type: 'String', values: SLOTS, description: 'The slot to empty.' }],
  },
  {
    functionName: 'set_palette',
    returns: 'String',
    functionDescription:
      'Changes the outfit\'s colours. A mood swaps each item for one of that tone in the same slot; primary and ' +
      'secondary recolour every tintable item. Pass a mood, the two colours, or both.',
    args: [
      { name: 'mood', type: 'String', values: TONES, description: 'The tone to swap items to. Empty to skip.' },
      { name: 'primary', type: 'String', description: 'Colour for tintable tops, dresses and outerwear, as "#RRGGBB". Empty to skip.' },
      { name: 'secondary', type: 'String', description: 'Colour for the other tintable items, as "#RRGGBB". Empty to keep the current one, or to use primary if there is none.' },
    ],
  },
  {
    functionName: 'set_color',
    returns: 'String',
    functionDescription: 'Recolours the tintable item in one slot, without changing the palette.',
    args: [
      { name: 'slot', type: 'String', values: SLOTS, description: `The slot to recolour. It must hold a tintable item: ${TINTABLE_IDS}.` },
      { name: 'color', type: 'String', description: 'The colour, as "#RRGGBB", e.g. "#334455".' },
    ],
  },
  {
    functionName: 'clear_palette',
    returns: 'String',
    functionDescription: 'Removes the palette and every colour override, restoring the catalogue colours.',
    args: [],
  },
  {
    functionName: 'save_look',
    returns: 'String',
    functionDescription: 'Saves the current outfit as a look. Returns the new look\'s id, for open_look.',
    args: [{ name: 'name', type: 'String', description: 'The name to save it under, e.g. "Goa wedding".' }],
  },
  {
    functionName: 'open_look',
    returns: 'String',
    functionDescription: 'Replaces the current outfit with a saved look, like tapping it under "Saved looks".',
    args: [{ name: 'id', type: 'String', description: 'The look id, from get_state or save_look.' }],
  },
  {
    functionName: 'visualize',
    returns: 'String',
    functionDescription:
      'Starts a try-on render of the current outfit and returns at once. The render takes about 40 seconds; ' +
      'get_state reports it as render.status (gen, done or failed). Fails with NOT_ALLOWED when this build ' +
      'has no try-on backend.',
    args: [{ name: 'on', type: 'String', values: ['avatar', 'me'], description: '"avatar" to render on the model, "me" on the user\'s photo.' }],
  },
  {
    functionName: 'switch_tab',
    returns: 'String',
    functionDescription:
      'Shows a screen. Changes nothing else. "shop" is the catalogue, filterable by collection and category, ' +
      'with a detail view per item. "looks" is The Look: the outfit, the palette, the try-on and the saved looks.',
    args: [{ name: 'tab', type: 'String', values: TABS, description: 'The screen to show.' }],
  },
];
