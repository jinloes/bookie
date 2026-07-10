import React from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Stack, Tabs } from '@mantine/core';
import { IconInbox, IconReceipt, IconReceipt2, IconTrendingUp } from '@tabler/icons-react';
import Inbox from './Inbox.jsx';
import Expenses from './Expenses.jsx';
import Incomes from './Incomes.jsx';
import Receipts from './Receipts.jsx';

// Unifies the intake → categorize → finalize task flow (previously split across the
// separate Inbox/Expenses/Income/Receipts nav items) into one tabbed workspace so users
// don't have to bounce between unrelated sidebar sections to complete a single workflow.
const TABS = [
  { value: 'review', label: 'Review Queue', icon: IconInbox, Component: Inbox },
  { value: 'expenses', label: 'Expenses', icon: IconReceipt, Component: Expenses },
  { value: 'income', label: 'Income', icon: IconTrendingUp, Component: Incomes },
  { value: 'receipts', label: 'Receipts', icon: IconReceipt2, Component: Receipts },
];

export default function Transactions() {
  const location = useLocation();
  const navigate = useNavigate();
  const active = TABS.find((t) => location.pathname.endsWith(`/${t.value}`))?.value ?? 'review';

  return (
    <Stack gap="lg">
      <Tabs value={active} onChange={(value) => navigate(`/transactions/${value}`)}>
        <Tabs.List>
          {TABS.map(({ value, label, icon: Icon }) => (
            <Tabs.Tab key={value} value={value} leftSection={<Icon size={14} />}>
              {label}
            </Tabs.Tab>
          ))}
        </Tabs.List>
      </Tabs>

      <Routes>
        <Route index element={<Navigate to="review" replace />} />
        {TABS.map(({ value, Component }) => (
          <Route key={value} path={value} element={<Component />} />
        ))}
      </Routes>
    </Stack>
  );
}
