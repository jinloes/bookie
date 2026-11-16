import { describe, expect, it } from 'vitest';
import {
  buildActiveActivityOptions,
  buildNeutralCategoryOptions,
  buildOwnerOptions,
  buildYearOptions,
  findMatchingProperty,
  validateTransactionForm,
} from './transactionPageUtils.js';

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

  it('builds deduplicated neutral category, active activity, and owner select options', () => {
    expect(
      buildNeutralCategoryOptions([
        { neutralCategoryId: 10, label: 'Repairs' },
        { neutralCategoryId: 10, label: 'Duplicate Repairs' },
        { neutralCategoryId: null, label: 'Unmapped' },
      ])
    ).toEqual([{ value: '10', label: 'Repairs' }]);

    const activities = [
      { id: 1, name: 'Rental', active: true, owner: { id: 7, name: 'Alex' } },
      { id: 2, name: 'Archived', active: false, owner: { id: 7, name: 'Alex' } },
    ];
    expect(buildActiveActivityOptions(activities)).toEqual([{ value: '1', label: 'Rental' }]);
    expect(buildOwnerOptions(activities)).toEqual([{ value: '7', label: 'Alex' }]);
  });

  it('validateTransactionForm validates required fields and normalizes numeric IDs', () => {
    const valid = validateTransactionForm({
      amount: '42.50',
      description: 'Water bill',
      date: '2026-03-01',
      source: 'Utility',
      activityId: '10',
      categoryId: '20',
      payerId: '3',
    });
    expect(valid.valid).toBe(true);
    expect(valid.parsed).toEqual({
      amount: 42.5,
      description: 'Water bill',
      date: '2026-03-01',
      source: 'Utility',
      activityId: 10,
      categoryId: 20,
      payerId: 3,
    });

    const invalid = validateTransactionForm({
      amount: '',
      description: '',
      date: '',
      source: 'x'.repeat(256),
      activityId: null,
      categoryId: null,
    });
    expect(invalid.valid).toBe(false);
    expect(invalid.errors).toEqual({
      amount: 'Amount must be a positive number',
      description: 'Description is required',
      date: 'Date is required',
      source: 'Source must be at most 255 characters',
      activityId: 'Activity is required',
      categoryId: 'Category is required',
    });
  });
});
