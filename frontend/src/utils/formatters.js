import dayjs from 'dayjs';

const CURRENCY_FMT = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export const fmtCurrency = (n) => CURRENCY_FMT.format(Number(n) || 0);
export const fmtDate = (iso) => dayjs(iso).format('MMM D, YYYY');
export const fmtDateTime = (iso) => dayjs(iso).format('MMM D, h:mm A');

/** Today's date in YYYY-MM-DD (local time). */
export const todayISO = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

/**
 * Groups items into cents buckets so per-bucket sums stay exact, then divides at the end.
 * Avoids the 0.1+0.2 float trap.
 *
 * @param items     array of records to aggregate
 * @param keyFn     (item) => bucket key, or undefined to skip
 * @param amountFn  (item) => numeric amount
 * @returns         Map<string, number> of bucket → dollar sum
 */
export function sumByKey(items, keyFn, amountFn) {
  const cents = new Map();
  for (const item of items) {
    const key = keyFn(item);
    if (key == null) continue;
    const amt = Math.round((Number(amountFn(item)) || 0) * 100);
    cents.set(key, (cents.get(key) ?? 0) + amt);
  }
  const out = new Map();
  for (const [k, v] of cents) out.set(k, v / 100);
  return out;
}
