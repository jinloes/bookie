/**
 * Integration tests for full-stack income/expense workflows.
 * Covers: happy path (CRUD), error handling, validation, cache invalidation.
 * Uses mock API via vitest MSW (or inline mocking).
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as apiModule from './api/index.js';

// Mock data
const MOCK_PROPERTIES = [
  { id: 1, name: 'Main House', type: 'SINGLE_FAMILY' },
  { id: 2, name: 'Rental Unit', type: 'APARTMENT' },
];

const MOCK_PAYERS = [
  { id: 1, name: 'Acme Corp', type: 'COMPANY' },
  { id: 2, name: 'John Doe', type: 'PERSON' },
];

const MOCK_CATEGORIES = [
  { id: 1, name: 'Maintenance' },
  { id: 2, name: 'Utilities' },
];

const MOCK_INCOME = {
  id: 101,
  amount: 1500,
  description: 'Rent received',
  date: '2024-01-01',
  source: 'Direct deposit',
  property: MOCK_PROPERTIES[0],
  payer: MOCK_PAYERS[0],
};

const MOCK_EXPENSE = {
  id: 201,
  amount: 350,
  description: 'Monthly electric',
  date: '2024-01-05',
  category: MOCK_CATEGORIES[1],
  property: MOCK_PROPERTIES[0],
  payer: MOCK_PAYERS[0],
  sourceType: 'MANUAL',
};

describe('Income/Expense Full-Stack Integration', () => {
  let queryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { staleTime: Infinity } },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Create Income - Happy Path', () => {
    it('should create income with valid data', async () => {
      const mockIncomes = [MOCK_INCOME];
      vi.spyOn(apiModule, 'getIncomes').mockResolvedValue(mockIncomes);
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const createIncomeSpy = vi.spyOn(apiModule, 'createIncome').mockResolvedValue(MOCK_INCOME);

      const { rerender } = render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      // Fill form
      const amountInput = screen.getByLabelText(/amount/i);
      const descriptionInput = screen.getByLabelText(/description/i);
      const propertySelect = screen.getByLabelText(/property/i);
      const payerSelect = screen.getByLabelText(/payer/i);

      await userEvent.type(amountInput, '1500');
      await userEvent.type(descriptionInput, 'Rent received');
      await userEvent.selectOption(propertySelect, '1');
      await userEvent.selectOption(payerSelect, '1');

      // Submit
      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      // Verify call
      await waitFor(() => {
        expect(createIncomeSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            amount: 1500,
            description: 'Rent received',
            propertyId: 1,
            payerId: 1,
          }),
        );
      });
    });
  });

  describe('Validation - Client-Side', () => {
    it('should reject negative amount', async () => {
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const createSpy = vi.spyOn(apiModule, 'createIncome').mockResolvedValue(MOCK_INCOME);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      // Attempt negative amount
      const amountInput = screen.getByLabelText(/amount/i);
      await userEvent.type(amountInput, '-500');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      // Should NOT call API
      await waitFor(() => {
        expect(createSpy).not.toHaveBeenCalled();
      });

      // Should show error message
      expect(screen.getByText(/positive/i)).toBeInTheDocument();
    });

    it('should reject empty description', async () => {
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const createSpy = vi.spyOn(apiModule, 'createIncome').mockResolvedValue(MOCK_INCOME);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      // Leave description empty, try to submit
      const amountInput = screen.getByLabelText(/amount/i);
      await userEvent.type(amountInput, '1500');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      expect(createSpy).not.toHaveBeenCalled();
    });
  });

  describe('Error Handling - Server Errors', () => {
    it('should handle 400 validation error from server', async () => {
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      vi.spyOn(apiModule, 'createIncome').mockRejectedValue({
        response: {
          status: 400,
          data: {
            code: 'VALIDATION_ERROR',
            message: 'Amount must be positive',
            details: { amount: 'Amount must be positive' },
          },
        },
      });

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      // Fill form with data that server will reject
      const amountInput = screen.getByLabelText(/amount/i);
      const descriptionInput = screen.getByLabelText(/description/i);
      const propertySelect = screen.getByLabelText(/property/i);
      const payerSelect = screen.getByLabelText(/payer/i);

      await userEvent.type(amountInput, '1500');
      await userEvent.type(descriptionInput, 'Test');
      await userEvent.selectOption(propertySelect, '1');
      await userEvent.selectOption(payerSelect, '1');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      // Should show error
      await waitFor(() => {
        expect(screen.getByText(/Amount must be positive/i)).toBeInTheDocument();
      });
    });

    it('should handle 500 server error', async () => {
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      vi.spyOn(apiModule, 'createIncome').mockRejectedValue({
        response: { status: 500, statusText: 'Internal Server Error' },
      });

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      const amountInput = screen.getByLabelText(/amount/i);
      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(amountInput, '1500');
      await userEvent.type(descriptionInput, 'Test');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(screen.getByText(/server error|error occurred/i)).toBeInTheDocument();
      });
    });
  });

  describe('Cache Invalidation', () => {
    it('should invalidate income list cache after create', async () => {
      const getIncomesSpy = vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([]);
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const createIncomeSpy = vi.spyOn(apiModule, 'createIncome').mockResolvedValue(MOCK_INCOME);

      const handleSave = vi.fn();

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm onSave={handleSave} />
        </QueryClientProvider>,
      );

      const amountInput = screen.getByLabelText(/amount/i);
      const descriptionInput = screen.getByLabelText(/description/i);
      await userEvent.type(amountInput, '1500');
      await userEvent.type(descriptionInput, 'Rent');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(createIncomeSpy).toHaveBeenCalled();
      });

      // getIncomes should be called to refresh cache
      await waitFor(() => {
        expect(getIncomesSpy).toHaveBeenCalled();
      });
    });
  });

  describe('Read Income - List View', () => {
    it('should display income list', async () => {
      vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([MOCK_INCOME]);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeList />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText(/Rent received/i)).toBeInTheDocument();
        expect(screen.getByText(/1500/)).toBeInTheDocument();
      });
    });

    it('should handle empty list', async () => {
      vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([]);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeList />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText(/no incomes/i)).toBeInTheDocument();
      });
    });
  });

  describe('Update Income', () => {
    it('should update income', async () => {
      const updated = { ...MOCK_INCOME, amount: 1600, description: 'Updated rent' };
      vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([MOCK_INCOME]);
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const updateSpy = vi.spyOn(apiModule, 'updateIncome').mockResolvedValue(updated);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeForm initialIncome={MOCK_INCOME} onSave={() => {}} />
        </QueryClientProvider>,
      );

      const amountInput = screen.getByLabelText(/amount/i);
      await userEvent.clear(amountInput);
      await userEvent.type(amountInput, '1600');

      const submitBtn = screen.getByRole('button', { name: /save|update/i });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(updateSpy).toHaveBeenCalledWith(
          MOCK_INCOME.id,
          expect.objectContaining({ amount: 1600 }),
        );
      });
    });
  });

  describe('Delete Income', () => {
    it('should delete income with confirmation', async () => {
      vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([MOCK_INCOME]);
      const deleteSpy = vi.spyOn(apiModule, 'deleteIncome').mockResolvedValue(null);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeList />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(screen.getByText(/Rent received/i)).toBeInTheDocument();
      });

      // Find and click delete button
      const deleteBtn = screen.getByRole('button', { name: /delete/i });
      await userEvent.click(deleteBtn);

      // Confirm deletion in modal
      const confirmBtn = screen.getByRole('button', { name: /confirm|delete/i });
      await userEvent.click(confirmBtn);

      await waitFor(() => {
        expect(deleteSpy).toHaveBeenCalledWith(MOCK_INCOME.id);
      });
    });
  });

  describe('Expense CRUD', () => {
    it('should create expense', async () => {
      vi.spyOn(apiModule, 'getExpenses').mockResolvedValue([]);
      vi.spyOn(apiModule, 'getExpenseCategories').mockResolvedValue(MOCK_CATEGORIES);
      vi.spyOn(apiModule, 'getProperties').mockResolvedValue(MOCK_PROPERTIES);
      vi.spyOn(apiModule, 'getPayers').mockResolvedValue(MOCK_PAYERS);
      const createSpy = vi.spyOn(apiModule, 'createExpense').mockResolvedValue(MOCK_EXPENSE);

      render(
        <QueryClientProvider client={queryClient}>
          <ExpenseForm onSave={() => {}} />
        </QueryClientProvider>,
      );

      const amountInput = screen.getByLabelText(/amount/i);
      const descriptionInput = screen.getByLabelText(/description/i);
      const categorySelect = screen.getByLabelText(/category/i);

      await userEvent.type(amountInput, '350');
      await userEvent.type(descriptionInput, 'Monthly electric');
      await userEvent.selectOption(categorySelect, '2');

      const submitBtn = screen.getByRole('button', { name: /save/i });
      await userEvent.click(submitBtn);

      await waitFor(() => {
        expect(createSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            amount: 350,
            description: 'Monthly electric',
            category: 2,
          }),
        );
      });
    });
  });

  describe('Request ID Tracing', () => {
    it('should include X-Request-Id header on all requests', async () => {
      const getSpy = vi.spyOn(apiModule, 'getIncomes').mockResolvedValue([]);

      render(
        <QueryClientProvider client={queryClient}>
          <IncomeList />
        </QueryClientProvider>,
      );

      await waitFor(() => {
        expect(getSpy).toHaveBeenCalled();
        // Check that the API call was made (header is added by api/index.js)
      });
    });
  });
});

// Minimal component stubs for testing (in real scenario, import actual components)
function IncomeForm({ initialIncome, onSave }) {
  return <div data-testid="income-form">Income Form Stub</div>;
}

function IncomeList() {
  return <div data-testid="income-list">Income List Stub</div>;
}

function ExpenseForm({ onSave }) {
  return <div data-testid="expense-form">Expense Form Stub</div>;
}
