import React from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';

vi.mock('@tanstack/react-query', () => ({
  useQuery: () => ({
    isLoading: false,
    error: null,
    data: [
      {
        id: 10,
        name: 'Oak Street rental',
        taxTreatment: 'SCHEDULE_E',
        owner: { id: 1, name: 'Household' },
      },
    ],
  }),
}));

vi.mock('../hooks/useReports.js', () => ({
  useScheduleEReport: () => ({
    isLoading: false,
    error: null,
    data: {
      year: 2026,
      rentalIncome: 1000,
      expenses: 1200,
      netIncome: -200,
      activities: [
        {
          activity: {
            id: 10,
            name: 'Oak Street rental',
            owner: { id: 1, name: 'Household' },
            property: { id: 2, name: 'Oak Street' },
          },
          rentalIncome: 1000,
          expenses: 1200,
          netIncome: -200,
          categories: [
            {
              category: { id: 20, label: 'Repairs', taxLine: '14' },
              total: 1200,
            },
          ],
        },
      ],
    },
  }),
}));

import TaxReport from './TaxReport.jsx';

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

describe('TaxReport', () => {
  it('renders server totals and preserves a negative net rental result', () => {
    render(
      <MantineProvider>
        <TaxReport />
      </MantineProvider>
    );

    expect(screen.getAllByText('Oak Street rental').length).toBeGreaterThan(0);
    expect(screen.getByText('Repairs')).toBeTruthy();
    expect(screen.getAllByText('-$200.00').length).toBeGreaterThan(0);
  });
});
