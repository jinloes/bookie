import React from 'react';
import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MantineProvider } from '@mantine/core';

const mockDismissPendingExpense = vi.fn();
const mockRetryPendingExpense = vi.fn();

vi.mock('../api/index.js', () => ({
  createPayer: vi.fn(),
  dismissPendingExpense: (...args) => mockDismissPendingExpense(...args),
  getOutlookEmailContent: vi.fn(),
  retryPendingExpense: (...args) => mockRetryPendingExpense(...args),
  savePendingExpense: vi.fn(),
  savePendingIncome: vi.fn(),
}));

const mockOpenConfirmModal = vi.fn();
vi.mock('@mantine/modals', () => ({
  modals: {
    openConfirmModal: (...args) => mockOpenConfirmModal(...args),
  },
}));

const mockNotify = vi.fn();
vi.mock('@mantine/notifications', () => ({
  notifications: {
    show: (...args) => mockNotify(...args),
  },
}));

import { EMAIL_TYPE, EXPENSE_SOURCE, PENDING_STATUS } from '../constants.js';
import PendingItem from './PendingItem.jsx';

beforeAll(() => {
  window.matchMedia =
    window.matchMedia ||
    (() => ({
      matches: false,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
    }));
  global.ResizeObserver =
    global.ResizeObserver ||
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
});

beforeEach(() => {
  vi.clearAllMocks();
});

function renderItem(itemOverrides = {}) {
  const item = {
    id: 1,
    sourceId: 'receipt-1',
    sourceType: EXPENSE_SOURCE.RECEIPT,
    emailType: EMAIL_TYPE.EXPENSE,
    subject: 'test receipt',
    status: PENDING_STATUS.READY,
    amount: 100,
    description: 'desc',
    date: '2026-07-01',
    category: 'SUPPLIES',
    propertyName: null,
    payerName: null,
    createdAt: '2026-07-18T23:00:00',
    ...itemOverrides,
  };
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <PendingItem
          item={item}
          categories={[{ value: 'SUPPLIES', label: 'Supplies', scheduleELine: 15 }]}
          properties={[]}
          payers={[]}
          onSaved={vi.fn()}
          onDismissed={vi.fn()}
          onPayerCreated={vi.fn()}
          onRetried={vi.fn()}
        />
      </QueryClientProvider>
    </MantineProvider>
  );
}

describe('PendingItem', () => {
  it('shows dismiss failure notification even when item is collapsed', async () => {
    const user = userEvent.setup();
    mockDismissPendingExpense.mockRejectedValueOnce(new Error('boom'));
    renderItem();

    await user.click(screen.getByRole('button', { name: /dismiss pending item/i }));
    const modalConfig = mockOpenConfirmModal.mock.calls[0][0];
    await modalConfig.onConfirm();

    await waitFor(() =>
      expect(mockNotify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Dismiss failed',
          color: 'red',
        })
      )
    );
  });

  it('shows retry failure notification for failed items', async () => {
    const user = userEvent.setup();
    mockRetryPendingExpense.mockRejectedValueOnce(new Error('retry boom'));
    renderItem({ status: PENDING_STATUS.FAILED, errorMessage: 'Parsing failed' });

    await user.click(screen.getByRole('button', { name: /retry parse/i }));

    await waitFor(() =>
      expect(mockNotify).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Retry failed',
          color: 'red',
        })
      )
    );
  });
});
