import React from 'react';
import { TablePageSkeleton } from '../components/PageLoadingSkeleton.jsx';
import { useIncomesPage } from '../hooks/useIncomesPage.js';
import { IncomesPageContent } from './IncomesPageContent.jsx';

export default function Incomes() {
  const page = useIncomesPage();

  if (page.isLoading) {
    return <TablePageSkeleton actionCount={2} filterCount={3} rowCount={6} />;
  }

  return <IncomesPageContent {...page} />;
}
