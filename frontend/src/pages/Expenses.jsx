import React from 'react';
import { TablePageSkeleton } from '../components/PageLoadingSkeleton.jsx';
import { useExpensesPage } from '../hooks/useExpensesPage.js';
import { ExpensesPageContent } from './ExpensesPageContent.jsx';

export default function Expenses() {
  const page = useExpensesPage();

  if (page.isLoading) {
    return <TablePageSkeleton filterCount={5} rowCount={6} />;
  }

  return <ExpensesPageContent {...page} />;
}
