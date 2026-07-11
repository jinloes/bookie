import { describe, expect, it } from 'vitest';
import { buildYearOptions, findMatchingProperty } from './transactionPageUtils.js';

describe('transactionPageUtils', () => {
  it('buildYearOptions returns unique years in descending order', () => {
    expect(
      buildYearOptions([
        { date: '2025-01-01' },
        { date: '2024-01-01' },
        { date: '2025-05-10' },
        { date: null },
      ])
    ).toEqual([
      { value: '2025', label: '2025' },
      { value: '2024', label: '2024' },
    ]);
  });

  it('findMatchingProperty matches by exact name before address fragments', () => {
    const properties = [
      { id: 1, name: 'Oak Street', address: '123 Oak Street' },
      { id: 2, name: 'Maple Avenue', address: '456 Maple Avenue' },
    ];

    expect(findMatchingProperty(properties, 'Oak Street')).toEqual(properties[0]);
    expect(findMatchingProperty(properties, '123 Oak Street Apt 2')).toEqual(properties[0]);
    expect(findMatchingProperty(properties, null)).toBeNull();
  });
});
