const brl = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

type PriceInput = {
  isFree: boolean;
  priceMinCents?: number | null;
  priceMaxCents?: number | null;
};

const format = (cents: number) => brl.format(cents / 100);

/** "Grátis", "R$ x", "R$ x – R$ y" ou texto vazio quando não há preço. */
export function formatPrice({
  isFree,
  priceMinCents,
  priceMaxCents,
}: PriceInput): string {
  if (isFree) return 'Grátis';
  const min = priceMinCents ?? null;
  const max = priceMaxCents ?? null;
  if (min === null && max === null) return '';
  if (min === null || max === null || min === max) {
    return format((min ?? max) as number);
  }
  return `${format(min)} – ${format(max)}`;
}
