import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as api from '../api/index.js';
import { renderHook, act } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));
vi.mock('../api/index.js');

import { notifications } from '@mantine/notifications';
import { queryKeys } from '../queryKeys.js';
import { STATEMENT_SOURCE, useImportIncomeStatement } from './useImportIncomeStatement.js';

const mockImportBank = vi.mocked(api.importBankToInbox);
const mockImportVenmo = vi.mocked(api.importVenmoToInbox);
const file = new File(['ofx'], 'statement.qfx');

function renderWithClient(queryClient) {
  return renderHook(() => useImportIncomeStatement(), {
    wrapper: createQueryWrapper(queryClient),
  });
}

function fill(result, { source, activityId = null, payerId = null, pick = file }) {
  act(() => {
    result.current.open();
    result.current.importForm.setSource(source);
  });
  act(() => {
    result.current.importForm.setActivityId(activityId);
    result.current.importForm.setPayerId(payerId);
    result.current.importForm.setFile(pick);
  });
}

async function submit(result) {
  await act(async () => {
    await result.current.importForm.onSubmit();
  });
}

describe('useImportIncomeStatement', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it('imports a bank statement with the chosen activity and invalidates income queries', async () => {
    mockImportBank.mockResolvedValue({
      importedRows: 2,
      skippedDuplicateRows: 1,
      skippedDebitRows: 3,
      skippedInvalidRows: 0,
    });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.BANK, activityId: '7', payerId: '9' });

    await submit(result);

    expect(mockImportBank).toHaveBeenCalledWith(file, '7');
    expect(mockImportVenmo).not.toHaveBeenCalled();
    expect(notifications.show).toHaveBeenCalledWith({
      title: 'Bank statement import completed',
      message: 'Imported 2 deposits (1 duplicates, 3 debits skipped, 0 invalid).',
      color: 'green',
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.transactions });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['reports'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.inbox });
    expect(result.current.importForm.opened).toBe(false);
    expect(result.current.importForm.source).toBe(STATEMENT_SOURCE.VENMO);
    expect(result.current.importForm.file).toBeNull();
  });

  it('imports a Venmo CSV with the payer filter and reports the detected property', async () => {
    mockImportVenmo.mockResolvedValue({
      importedRows: 1,
      skippedDuplicateRows: 0,
      skippedSenderRows: 2,
      skippedOutgoingRows: 0,
      skippedInvalidRows: 0,
      propertyName: 'Main St',
    });
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.VENMO, activityId: '7', payerId: '9' });

    await submit(result);

    expect(mockImportVenmo).toHaveBeenCalledWith(file, '9', null, '7');
    expect(mockImportBank).not.toHaveBeenCalled();
    expect(notifications.show).toHaveBeenCalledWith({
      title: 'Venmo import completed',
      message:
        'Imported 1 rows (0 duplicates, 2 sender mismatch, 0 outgoing, 0 invalid). Property: Main St',
      color: 'green',
    });
  });

  it('requires a file before calling the API', async () => {
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.BANK, pick: null });

    await submit(result);

    expect(result.current.importForm.error).toBe('Please select a bank statement file.');
    expect(mockImportBank).not.toHaveBeenCalled();
    expect(invalidateSpy).not.toHaveBeenCalled();
  });

  it('keeps the drawer open with the server message when the import fails', async () => {
    mockImportBank.mockRejectedValue(new Error('Not an OFX bank statement.'));
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.BANK });

    await submit(result);

    expect(result.current.importForm.error).toBe('Not an OFX bank statement.');
    expect(result.current.importForm.opened).toBe(true);
    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(notifications.show).not.toHaveBeenCalled();
  });

  it('falls back to a source-specific message for a bare HTTP failure', async () => {
    mockImportVenmo.mockRejectedValue(new Error('HTTP 500'));
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.VENMO });

    await submit(result);

    expect(result.current.importForm.error).toBe('Could not import Venmo CSV.');
  });

  it('clears the picked file and error when switching source', async () => {
    const { result } = renderWithClient(queryClient);
    fill(result, { source: STATEMENT_SOURCE.VENMO, pick: null });
    await submit(result);

    act(() => {
      result.current.importForm.setFile(file);
      result.current.importForm.setSource(STATEMENT_SOURCE.BANK);
    });

    expect(result.current.importForm.file).toBeNull();
    expect(result.current.importForm.error).toBeNull();
  });
});
