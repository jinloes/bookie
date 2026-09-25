import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as api from '../api/index.js';
import { renderHook, waitFor } from '@testing-library/react';
import { createTestQueryClient, createQueryWrapper } from '../test/render.jsx';

vi.mock('../api/index.js');
const mockGetInboxItems = vi.mocked(api.getInboxItems);

import { usePendingExpensesQuery, usePendingIncomesQuery } from './usePendingQueue.js';

function renderWithClient(hook, queryClient) {
  return renderHook(hook, {
    wrapper: createQueryWrapper(queryClient),
  });
}

function inboxQuery(queryClient) {
  return queryClient.getQueryCache().find({ queryKey: ['inbox'] });
}

describe('usePendingQueue', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  it.each(['RECEIVED', 'QUEUED', 'PROCESSING'])('polls while any item is %s', async (state) => {
    mockGetInboxItems.mockResolvedValue([
      { id: 1, origin: 'RECEIPT', state: 'READY' },
      { id: 2, origin: 'OUTLOOK_EMAIL', state },
    ]);
    const { result } = renderWithClient(() => usePendingExpensesQuery(), queryClient);

    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = inboxQuery(queryClient);
    expect(query.options.refetchInterval(query)).toBe(5000);
  });

  it('stops polling once no item is transient', async () => {
    mockGetInboxItems.mockResolvedValue([
      { id: 1, origin: 'RECEIPT', state: 'READY' },
      { id: 2, origin: 'OUTLOOK_EMAIL', state: 'FAILED' },
    ]);
    const { result } = renderWithClient(() => usePendingExpensesQuery(), queryClient);

    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = inboxQuery(queryClient);
    expect(query.options.refetchInterval(query)).toBe(false);
  });

  it('does not poll before the first inbox response arrives', () => {
    renderWithClient(() => usePendingExpensesQuery(), queryClient);
    mockGetInboxItems.mockReturnValue(new Promise(() => {}));

    const query = inboxQuery(queryClient);
    expect(query.options.refetchInterval({ state: { data: undefined } })).toBe(false);
  });

  it('splits one inbox query into the review queue and imported incomes', async () => {
    mockGetInboxItems.mockResolvedValue([
      { id: 1, origin: 'OUTLOOK_EMAIL', state: 'READY' },
      { id: 2, origin: 'VENMO', state: 'READY' },
      { id: 3, origin: 'RECEIPT', state: 'FAILED' },
    ]);
    const review = renderWithClient(() => usePendingExpensesQuery(), queryClient);
    const incomes = renderWithClient(() => usePendingIncomesQuery(), queryClient);

    await waitFor(() => expect(review.result.current.data).toBeDefined());
    await waitFor(() => expect(incomes.result.current.data).toBeDefined());

    expect(review.result.current.data.map((item) => item.id)).toEqual([1, 3]);
    expect(incomes.result.current.data.map((item) => item.id)).toEqual([2]);
    expect(mockGetInboxItems).toHaveBeenCalledTimes(1);
  });
});
