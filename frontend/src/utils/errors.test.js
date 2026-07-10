import { describe, it, expect } from 'vitest';
import { getErrorMessage } from './errors';

describe('getErrorMessage', () => {
  it('returns the fallback when error is falsy', () => {
    expect(getErrorMessage(null, 'fallback')).toBe('fallback');
    expect(getErrorMessage(undefined)).toBe('Something went wrong. Please try again.');
  });

  it('prefers the backend-supplied message over the generic per-code copy', () => {
    const error = { code: 'CONFLICT', message: 'This item has already been saved as Expense #84.' };
    expect(getErrorMessage(error)).toBe('This item has already been saved as Expense #84.');
  });

  it('falls back to the generic per-code copy when the backend message is a raw HTTP string', () => {
    const error = { code: 'CONFLICT', message: 'HTTP 409: no body' };
    expect(getErrorMessage(error)).toBe(
      'This action conflicts with current data. Refresh and retry.'
    );
  });

  it('falls back to the generic per-code copy when no message is supplied', () => {
    const error = { code: 'NOT_FOUND', message: '' };
    expect(getErrorMessage(error)).toBe(
      'The requested item no longer exists. Refresh and try again.'
    );
  });

  it('uses the raw message when no known code is present', () => {
    const error = { message: 'Some unmapped backend message' };
    expect(getErrorMessage(error)).toBe('Some unmapped backend message');
  });

  it('uses the fallback when neither a usable message nor a known code is present', () => {
    const error = { message: 'HTTP 500: no body' };
    expect(getErrorMessage(error, 'fallback')).toBe('fallback');
  });

  it('appends a short request-id reference when present, for log correlation', () => {
    const error = {
      code: 'CONFLICT',
      message: 'This item has already been saved as Expense #84.',
      requestId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    };
    expect(getErrorMessage(error)).toBe(
      'This item has already been saved as Expense #84. (Ref: a1b2c3d4)'
    );
  });
});
