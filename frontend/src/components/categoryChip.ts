// All pairs have contrast >= 4.5:1 (AA); the category name is always shown as text.
const CHIPS = [
  'bg-violet-100 text-violet-900',
  'bg-pink-100 text-pink-900',
  'bg-amber-100 text-amber-900',
];

/** Deterministic chip colors from the category slug. */
export function categoryChipClass(slug: string): string {
  let hash = 0;
  for (const char of slug) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return CHIPS[hash % CHIPS.length];
}
