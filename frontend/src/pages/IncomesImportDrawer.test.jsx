import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/render.jsx';
import { STATEMENT_SOURCE } from '../hooks/useImportIncomeStatement.js';
import { IncomesImportDrawer } from './IncomesImportDrawer.jsx';

function renderDrawer(overrides = {}) {
  const importForm = {
    opened: true,
    source: STATEMENT_SOURCE.VENMO,
    setSource: vi.fn(),
    payerId: null,
    setPayerId: vi.fn(),
    activityId: null,
    setActivityId: vi.fn(),
    activityOptions: [],
    payerOptions: [],
    file: new File(['a,b'], 'venmo-2026.csv', { type: 'text/csv' }),
    setFile: vi.fn(),
    error: null,
    submitting: false,
    onCancel: vi.fn(),
    onSubmit: vi.fn(),
    ...overrides,
  };
  renderWithProviders(<IncomesImportDrawer importForm={importForm} />);
  return importForm;
}

describe('IncomesImportDrawer', () => {
  it('shows the import status and locks the form while importing', async () => {
    renderDrawer({ submitting: true });

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Importing venmo-2026.csv… Large statements can take a minute.'
    );
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Activity context (optional)' })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Payer filter (optional)' })).toBeDisabled();
  });

  it('shows no status and leaves the form editable when idle', async () => {
    renderDrawer();

    expect(await screen.findByRole('button', { name: /^import$/i })).toBeEnabled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeEnabled();
  });
});
