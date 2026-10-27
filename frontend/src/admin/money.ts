/**
 * Parses a price typed in reais ("25", "25,5", "25.50") into integer cents.
 * Returns null for empty text and 'invalid' for anything else (3+ decimals, letters, negatives).
 * Works on the digits, never multiplying a float, so there is no rounding error.
 */
export function parseReais(text: string): number | null | 'invalid' {
  const value = text.trim();
  if (value === '') return null;
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(value);
  if (!match) return 'invalid';
  const reais = Number(match[1]);
  const cents = Number((match[2] ?? '').padEnd(2, '0'));
  const total = reais * 100 + cents;
  return Number.isSafeInteger(total) ? total : 'invalid';
}

/** Cents to the text shown in the form: 2550 -> "25,50", 8000 -> "80,00". */
export function formatReais(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return '';
  const reais = Math.trunc(cents / 100);
  const rest = String(cents % 100).padStart(2, '0');
  return `${reais},${rest}`;
}
