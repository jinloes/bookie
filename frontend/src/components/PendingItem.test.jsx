import React from 'react';
import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import * as api from '../api/index.js';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';

vi.mock('../api/index.js');

const mockDismissInboxItem = vi.mocked(api.dismissInboxItem);
const mockDownloadReceipt = vi.mocked(api.downloadReceipt);
const mockGetOutlookEmailContent = vi.mocked(api.getOutlookEmailContent);
const mockGetFinancialCategories = vi.mocked(api.getFinancialCategories);
const mockReparseInboxItem = vi.mocked(api.reparseInboxItem);
const mockSaveInboxItem = vi.mocked(api.saveInboxItem);

vi.mock('@mantine/modals', () => ({ modals: { openConfirmModal: vi.fn() } }));
import { modals } from '@mantine/modals';
const mockOpenConfirmModal = vi.mocked(modals.openConfirmModal);

vi.mock('@mantine/notifications', () => ({ notifications: { show: vi.fn() } }));
import { notifications } from '@mantine/notifications';
const mockNotify = vi.mocked(notifications.show);

import { EMAIL_TYPE, EXPENSE_SOURCE, PENDING_STATUS } from '../constants.js';
import PendingItem from './PendingItem.jsx';

beforeAll(() => {
  URL.createObjectURL = vi.fn(() => 'blob:receipt-preview');
  URL.revokeObjectURL = vi.fn();
});

beforeEach(() => {
  vi.clearAllMocks();
  mockGetFinancialCategories.mockImplementation((direction) =>
    Promise.resolve([
      direction === 'INCOME'
        ? {
            id: 21,
            key: 'OTHER_INCOME',
            label: 'Other income',
            direction: 'INCOME',
            neutralCategoryId: 121,
          }
        : {
            id: 20,
            key: 'SUPPLIES',
            label: 'Supplies',
            direction: 'EXPENSE',
            neutralCategoryId: 120,
          },
    ])
  );
  mockSaveInboxItem.mockResolvedValue({ id: 1, state: 'SAVED' });
  mockDownloadReceipt.mockResolvedValue(new Blob(['receipt'], { type: 'application/pdf' }));
  mockGetOutlookEmailContent.mockResolvedValue({
    subject: 'PayPal receipts - receipt-two.pdf',
    body: 'Shared email context\nSECOND RECEIPT 20.00',
    receivedDate: '2026-08-20',
  });
});

function renderItem(itemOverrides = {}) {
  const item = {
    id: 1,
    sourceId: 'receipt-1',
    origin: EXPENSE_SOURCE.RECEIPT,
    proposedDirection: EMAIL_TYPE.EXPENSE,
    subject: 'test receipt',
    state: PENDING_STATUS.READY,
    amount: 100,
    description: 'desc',
    date: '2026-07-01',
    proposedCategory: 'SUPPLIES',
    propertyName: null,
    counterpartyName: null,
    activity: { id: 10, name: 'Household', active: true },
    category: { id: 20, label: 'Supplies', neutralCategoryId: 120 },
    createdAt: '2026-07-18T23:00:00',
    ...itemOverrides,
  };
  return renderWithProviders(
    <PendingItem
      item={item}
      properties={[]}
      payers={[]}
      activities={[{ id: 10, name: 'Household', active: true }]}
      onSaved={vi.fn()}
      onDismissed={vi.fn()}
      onPayerCreated={vi.fn()}
      onRetried={vi.fn()}
    />
  );
}

describe('PendingItem', () => {
  it('summarizes the extracted amount, date and counterparty in the collapsed row', async () => {
    renderItem({ amount: 350, date: '2026-07-23', counterpartyName: 'Synergize' });

    expect(await screen.findByText('$350.00 · Jul 23, 2026 · Synergize')).toBeInTheDocument();
    expect(screen.getByText('Queued Jul 18, 11:00 PM')).toBeInTheDocument();
  });

  it('falls back to the property name when there is no counterparty', async () => {
    renderItem({ amount: null, date: null, counterpartyName: null, propertyName: 'Maple St' });

    expect(await screen.findByText('Maple St')).toBeInTheDocument();
  });

  it('omits the summary line when nothing has been extracted yet', () => {
    renderItem({
      state: PENDING_STATUS.PROCESSING,
      amount: null,
      date: null,
      counterpartyName: null,
      propertyName: null,
    });

    expect(screen.getByText('test receipt').parentElement.children).toHaveLength(2);
  });

  it('shows dismiss failure notification even when item is collapsed', async () => {
    const { user } = renderItem();
    mockDismissInboxItem.mockRejectedValueOnce(new Error('boom'));

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
    const { user } = renderItem({ state: PENDING_STATUS.FAILED, errorMessage: 'Parsing failed' });
    mockReparseInboxItem.mockRejectedValueOnce(new Error('retry boom'));

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

  it('requeues a failed item through the inbox reparse command', async () => {
    const onRetried = vi.fn();
    mockReparseInboxItem.mockResolvedValueOnce({ id: 1, state: PENDING_STATUS.QUEUED });
    const { user } = renderWithProviders(
      <PendingItem
        item={{
          id: 1,
          sourceId: 'receipt-1',
          origin: EXPENSE_SOURCE.RECEIPT,
          subject: 'failed receipt',
          state: PENDING_STATUS.FAILED,
          errorMessage: 'Parsing failed',
          createdAt: '2026-07-18T23:00:00',
        }}
        properties={[]}
        payers={[]}
        activities={[]}
        onSaved={vi.fn()}
        onDismissed={vi.fn()}
        onPayerCreated={vi.fn()}
        onRetried={onRetried}
      />
    );

    await user.click(screen.getByRole('button', { name: /retry parse/i }));

    await waitFor(() =>
      expect(onRetried).toHaveBeenCalledWith(1, { id: 1, state: PENDING_STATUS.QUEUED })
    );
    expect(mockReparseInboxItem).toHaveBeenCalledWith(1);
  });

  it.each([PENDING_STATUS.RECEIVED, PENDING_STATUS.QUEUED, PENDING_STATUS.PROCESSING])(
    'shows %s items as in progress without a save form',
    (state) => {
      renderItem({ state });

      expect(
        screen.getByText(state === PENDING_STATUS.PROCESSING ? 'Processing…' : 'Queued…')
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /save expense/i })).not.toBeInTheDocument();
    }
  );

  it('shows the exact original receipt in an in-app preview', async () => {
    const { user } = renderItem();

    await user.click(screen.getByText('test receipt'));
    await user.click(await screen.findByRole('button', { name: /view receipt/i }));

    await waitFor(() => expect(mockDownloadReceipt).toHaveBeenCalledWith('receipt-1'));
    expect((await screen.findByTitle('Preview test receipt')).getAttribute('src')).toBe(
      'blob:receipt-preview'
    );
  });

  it('shows ambiguity and saves with an edited direction only after Save is clicked', async () => {
    const { user } = renderItem({ classificationAmbiguous: true });

    await user.click(screen.getByText('test receipt'));
    expect(await screen.findByText(/classification needs review/i)).toBeInTheDocument();
    expect(mockSaveInboxItem).not.toHaveBeenCalled();

    await user.click(document.querySelector('input[aria-label="Transaction direction"]'));
    await user.keyboard('{ArrowUp}{Enter}');
    await waitFor(() => expect(mockGetFinancialCategories).toHaveBeenCalledWith('INCOME', 10));
    await user.click(document.querySelector('input[aria-label="Transaction category"]'));
    await user.keyboard('{ArrowDown}{Enter}');
    await user.click(screen.getByRole('button', { name: /save income/i }));

    await waitFor(() => expect(mockSaveInboxItem).toHaveBeenCalledTimes(1));
    expect(mockSaveInboxItem).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        direction: 'INCOME',
        amount: 100,
        description: 'desc',
        activityId: 10,
        neutralCategoryId: 121,
      })
    );
  });

  it('loads attachment-specific original content without persisting before explicit Save', async () => {
    const { user } = renderItem({
      sourceId: 'outlook-attachment:second',
      origin: EXPENSE_SOURCE.OUTLOOK_EMAIL,
      subject: 'PayPal receipts - receipt-two.pdf',
      description: 'Second receipt',
      amount: 20,
    });

    await user.click(screen.getByText('PayPal receipts - receipt-two.pdf'));

    await waitFor(() =>
      expect(mockGetOutlookEmailContent).toHaveBeenCalledWith('outlook-attachment:second')
    );
    await waitFor(() => expect(document.body.textContent).toContain('SECOND RECEIPT 20.00'));
    expect(document.body.textContent).not.toContain('FIRST RECEIPT');
    expect(mockSaveInboxItem).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /save expense/i }));

    await waitFor(() => expect(mockSaveInboxItem).toHaveBeenCalledTimes(1));
    expect(mockSaveInboxItem).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ direction: 'EXPENSE', neutralCategoryId: 120, amount: 20 })
    );
  });
});
