import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MantineProvider } from '@mantine/core';

const mockSubmitExpenseToAgent = vi.fn();
const mockCreateExpense = vi.fn();
const mockGetProperties = vi.fn();
const mockGetPayers = vi.fn();
const mockGetExpenseCategories = vi.fn();

vi.mock('../api/index.js', () => ({
  submitExpenseToAgent: (...args) => mockSubmitExpenseToAgent(...args),
  createExpense: (...args) => mockCreateExpense(...args),
  getProperties: (...args) => mockGetProperties(...args),
  getPayers: (...args) => mockGetPayers(...args),
  getExpenseCategories: (...args) => mockGetExpenseCategories(...args),
}));

import Agent from './Agent.jsx';

beforeEach(() => {
  // jsdom doesn't implement matchMedia or scrollTo — Mantine's color-scheme hook and the chat
  // viewport's auto-scroll effect both call into these; stub them so mounting doesn't throw.
  window.matchMedia =
    window.matchMedia ||
    (() => ({
      matches: false,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
    }));
  window.Element.prototype.scrollTo = window.Element.prototype.scrollTo || (() => {});
  global.ResizeObserver =
    global.ResizeObserver ||
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
});

function renderAgent() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <Agent />
      </QueryClientProvider>
    </MantineProvider>
  );
}

async function sendMessage(text) {
  const user = userEvent.setup();
  const input = screen.getByPlaceholderText(/describe an expense/i);
  await user.type(input, text);
  await user.click(screen.getByRole('button', { name: /send/i }));
}

describe('Agent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetProperties.mockResolvedValue([{ id: 1, name: 'Oak Street' }]);
    mockGetPayers.mockResolvedValue([{ id: 2, name: "Joe's Plumbing" }]);
    mockGetExpenseCategories.mockResolvedValue([
      { value: 'REPAIRS', label: 'Repairs', scheduleELine: 14 },
    ]);
  });

  it('shows a review card instead of saving the expense immediately', async () => {
    mockSubmitExpenseToAgent.mockResolvedValue({
      message: 'I found this expense — review the details below and save it if it looks right.',
      proposedExpense: {
        amount: 250,
        description: 'Plumbing repairs',
        date: '2026-01-05',
        category: 'REPAIRS',
        propertyId: 1,
        propertyName: 'Oak Street',
        payerId: 2,
        payerName: "Joe's Plumbing",
      },
    });

    renderAgent();
    await sendMessage('I paid $250 for plumbing at Oak Street');

    await waitFor(() => expect(screen.getByText(/review before saving/i)).toBeTruthy());
    // The proposal must never be committed on its own — createExpense is only called on explicit save.
    expect(mockCreateExpense).not.toHaveBeenCalled();
  });

  it('only calls createExpense after the user clicks Save Expense', async () => {
    mockSubmitExpenseToAgent.mockResolvedValue({
      message: 'Review the details below.',
      proposedExpense: {
        amount: 250,
        description: 'Plumbing repairs',
        date: '2026-01-05',
        category: 'REPAIRS',
        propertyId: 1,
        propertyName: 'Oak Street',
        payerId: 2,
        payerName: "Joe's Plumbing",
      },
    });
    mockCreateExpense.mockResolvedValue({ id: 99, amount: 250 });

    renderAgent();
    await sendMessage('I paid $250 for plumbing at Oak Street');
    await waitFor(() => expect(screen.getByText(/review before saving/i)).toBeTruthy());

    await userEvent.click(screen.getByRole('button', { name: /save expense/i }));

    await waitFor(() => expect(mockCreateExpense).toHaveBeenCalledTimes(1));
    expect(mockCreateExpense).toHaveBeenCalledWith(
      expect.objectContaining({ amount: '250', category: 'REPAIRS', propertyId: 1, payerId: 2 })
    );
    await waitFor(() => expect(screen.getByText(/expense saved/i)).toBeTruthy());
  });

  it('discards the proposal without saving when Discard is clicked', async () => {
    mockSubmitExpenseToAgent.mockResolvedValue({
      message: 'Review the details below.',
      proposedExpense: {
        amount: 100,
        description: 'Supplies',
        date: '2026-01-01',
        category: 'REPAIRS',
        propertyId: null,
        propertyName: null,
        payerId: null,
        payerName: null,
      },
    });

    renderAgent();
    await sendMessage('Bought some supplies');
    await waitFor(() => expect(screen.getByText(/review before saving/i)).toBeTruthy());

    await userEvent.click(screen.getByRole('button', { name: /discard/i }));

    expect(screen.getByText(/discarded — nothing was saved/i)).toBeTruthy();
    expect(mockCreateExpense).not.toHaveBeenCalled();
  });

  it('shows a follow-up question with no review card when the agent needs more info', async () => {
    mockSubmitExpenseToAgent.mockResolvedValue({
      message: 'What was the dollar amount for this expense?',
      proposedExpense: null,
    });

    renderAgent();
    await sendMessage('I paid for plumbing');

    await waitFor(() => expect(screen.getByText(/what was the dollar amount/i)).toBeTruthy());
    expect(screen.queryByText(/review before saving/i)).toBeFalsy();
  });

  it('shows an error message when the agent request fails', async () => {
    mockSubmitExpenseToAgent.mockRejectedValue({ message: 'HTTP 500' });

    renderAgent();
    await sendMessage('I paid $250 for plumbing');

    await waitFor(() => expect(screen.getByText(/could not process that request/i)).toBeTruthy());
  });
});
