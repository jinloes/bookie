import { describe, it, expect, vi } from 'vitest';
import { fmtCurrency, fmtDate, fmtDateTime, sumByKey, todayISO } from './formatters.js';

describe('fmtCurrency', () => {
  it('formats integer with two decimal places', () => {
    expect(fmtCurrency(5)).toBe('$5.00');
  });

  it('pads single decimal', () => {
    expect(fmtCurrency(10.5)).toBe('$10.50');
  });

  it('accepts string numbers', () => {
    expect(fmtCurrency('48.52')).toBe('$48.52');
  });

  it('formats zero', () => {
    expect(fmtCurrency(0)).toBe('$0.00');
  });

  it('rounds to two decimals', () => {
    expect(fmtCurrency(1.009)).toBe('$1.01');
  });

  it('inserts thousands separators', () => {
    expect(fmtCurrency(1000)).toBe('$1,000.00');
    expect(fmtCurrency(1234567.89)).toBe('$1,234,567.89');
  });

  it('treats non-numeric input as zero rather than NaN', () => {
    expect(fmtCurrency('abc')).toBe('$0.00');
    expect(fmtCurrency(null)).toBe('$0.00');
    expect(fmtCurrency(undefined)).toBe('$0.00');
  });
});

describe('todayISO', () => {
  it('returns YYYY-MM-DD format', () => {
    expect(todayISO()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('uses local date parts rather than UTC toISOString date', () => {
    const RealDate = Date;
    class FakeDate extends RealDate {
      getFullYear() {
        return 2026;
      }
      getMonth() {
        return 4;
      }
      getDate() {
        return 1;
      }
      toISOString() {
        return '2026-05-02T00:30:00.000Z';
      }
    }
    vi.stubGlobal('Date', FakeDate);
    try {
      expect(todayISO()).toBe('2026-05-01');
    } finally {
      vi.stubGlobal('Date', RealDate);
    }
  });
});

describe('sumByKey', () => {
  const items = [
    { name: 'A', amount: 10 },
    { name: 'B', amount: 5 },
    { name: 'A', amount: 2.5 },
  ];

  it('groups and sums by key', () => {
    const result = sumByKey(
      items,
      (i) => i.name,
      (i) => i.amount
    );
    expect(result.get('A')).toBe(12.5);
    expect(result.get('B')).toBe(5);
  });

  it('avoids float accumulation errors (0.1 + 0.2)', () => {
    const result = sumByKey(
      [
        { k: 'x', v: 0.1 },
        { k: 'x', v: 0.2 },
      ],
      (i) => i.k,
      (i) => i.v
    );
    expect(result.get('x')).toBe(0.3);
  });

  it('skips items whose key is null or undefined', () => {
    const result = sumByKey(
      [
        { k: null, v: 5 },
        { k: undefined, v: 3 },
        { k: 'x', v: 7 },
      ],
      (i) => i.k,
      (i) => i.v
    );
    expect(result.get('x')).toBe(7);
    expect(result.size).toBe(1);
  });

  it('treats non-numeric amounts as zero', () => {
    const result = sumByKey(
      [
        { k: 'x', v: 'abc' },
        { k: 'x', v: null },
        { k: 'x', v: 5 },
      ],
      (i) => i.k,
      (i) => i.v
    );
    expect(result.get('x')).toBe(5);
  });

  it('returns an empty Map for an empty input', () => {
    const result = sumByKey(
      [],
      (i) => i.k,
      (i) => i.v
    );
    expect(result.size).toBe(0);
  });
});

describe('fmtDate', () => {
  it('formats ISO date string', () => {
    // Use noon to avoid UTC midnight rolling back a day in negative-offset zones
    expect(fmtDate('2026-04-22T12:00:00')).toBe('Apr 22, 2026');
  });
});

describe('fmtDateTime', () => {
  it('formats ISO datetime to readable time', () => {
    expect(fmtDateTime('2026-04-22T15:30:00')).toBe('Apr 22, 3:30 PM');
  });

  it('formats midnight correctly', () => {
    expect(fmtDateTime('2026-01-01T00:00:00')).toBe('Jan 1, 12:00 AM');
  });
});
