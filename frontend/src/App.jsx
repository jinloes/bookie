import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, NavLink, useLocation, Link } from 'react-router-dom';
import { Alert, AppShell, Badge, Box, Button, Group, Stack, Text } from '@mantine/core';
import {
  IconBuilding,
  IconDatabase,
  IconFileAnalytics,
  IconHome,
  IconInbox,
  IconMail,
  IconReceipt,
  IconReceipt2,
  IconRobot,
  IconScale,
  IconSettings,
  IconTrendingUp,
  IconUsers,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getOutlookStatus, getPendingExpenses, getPendingIncomes } from './api/index.js';
import { usePendingSSE } from './hooks/usePendingSSE.js';
import { PENDING_STATUS } from './constants.js';
import { queryKeys } from './queryKeys.js';
import Dashboard from './pages/Dashboard.jsx';
import Inbox from './pages/Inbox.jsx';
import Incomes from './pages/Incomes.jsx';
import Expenses from './pages/Expenses.jsx';
import Receipts from './pages/Receipts.jsx';
import Emails from './pages/Emails.jsx';
import Agent from './pages/Agent.jsx';
import Properties from './pages/Properties.jsx';
import Payers from './pages/Payers.jsx';
import Backup from './pages/Backup.jsx';
import Reconciliation from './pages/Reconciliation.jsx';
import Settings from './pages/Settings.jsx';
import TaxReport from './pages/TaxReport.jsx';

// Update the system tray tooltip with the pending item count (Tauri only).
let tauriInvoke = null;
try {
  tauriInvoke = (await import('@tauri-apps/api/core')).invoke;
} catch {
  // Running in browser — no tray to update.
}

function useTrayBadge(count) {
  useEffect(() => {
    if (tauriInvoke) {
      tauriInvoke('update_tray_tooltip', { count }).catch(() => {});
    }
  }, [count]);
}

function InboxBadge() {
  // SSE in AppInner invalidates ['pendingExpenses'] on every update, so polling here
  // would just be redundant network traffic.
  const { data: pendingExpenses = [] } = useQuery({
    queryKey: queryKeys.pendingExpenses,
    queryFn: getPendingExpenses,
  });
  const { data: pendingIncomes = [] } = useQuery({
    queryKey: ['pendingIncomes'],
    queryFn: getPendingIncomes,
  });
  const count =
    pendingExpenses.filter((i) => i.status === PENDING_STATUS.READY).length +
    pendingIncomes.length;
  useTrayBadge(count);
  return count > 0 ? (
    <Badge color="orange" size="xs" circle>
      {count}
    </Badge>
  ) : null;
}

const NAV_SECTIONS = [
  {
    key: 'main',
    items: [
      { to: '/', label: 'Dashboard', icon: IconHome, end: true },
      { to: '/inbox', label: 'Inbox', icon: IconInbox, badge: <InboxBadge /> },
      { to: '/reconciliation', label: 'Reconciliation', icon: IconScale },
    ],
  },
  {
    key: 'financial',
    label: 'Financial',
    items: [
      { to: '/incomes', label: 'Income', icon: IconTrendingUp },
      { to: '/expenses', label: 'Expenses', icon: IconReceipt },
      { to: '/tax-report', label: 'Tax Report', icon: IconFileAnalytics },
      { to: '/receipts', label: 'Receipts', icon: IconReceipt2 },
      { to: '/emails', label: 'Emails', icon: IconMail },
    ],
  },
  {
    key: 'records',
    label: 'Records',
    items: [
      { to: '/properties', label: 'Properties', icon: IconBuilding },
      { to: '/payers', label: 'Payers', icon: IconUsers },
    ],
  },
  {
    key: 'system',
    items: [
      { to: '/agent', label: 'AI Expense Agent', icon: IconRobot },
      { to: '/backup', label: 'Backup', icon: IconDatabase },
      { to: '/settings', label: 'Settings', icon: IconSettings },
    ],
  },
];

function NavItem({ to, label, icon: Icon, end, badge }) {
  return (
    <NavLink to={to} end={end} style={{ textDecoration: 'none' }}>
      {({ isActive }) => (
        <Group
          gap={8}
          px={10}
          py={6}
          style={{
            borderRadius: 6,
            cursor: 'pointer',
            background: isActive ? 'var(--mantine-color-violet-0)' : 'transparent',
            color: isActive ? 'var(--mantine-color-violet-7)' : 'var(--mantine-color-gray-7)',
            transition: 'background 0.1s, color 0.1s',
            userSelect: 'none',
          }}
        >
          <Icon size={16} style={{ flexShrink: 0 }} />
          <Text size="sm" fw={isActive ? 600 : 400} c="inherit" style={{ flex: 1 }}>
            {label}
          </Text>
          {badge}
        </Group>
      )}
    </NavLink>
  );
}

function AppInner() {
  const location = useLocation();
  const queryClient = useQueryClient();
  const outlookStatusQuery = useQuery({
    queryKey: queryKeys.outlookStatus,
    queryFn: getOutlookStatus,
  });
  const outlookConnected = outlookStatusQuery.data?.connected === true;
  usePendingSSE({
    notification: {
      title: 'Item ready',
      message: 'A new item is ready to review in Inbox',
      color: 'green',
    },
    activeTab: location.pathname === '/inbox' ? 'pending' : 'other',
    onUpdate: () => queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses }),
  });
  return (
    <AppShell navbar={{ width: 220, breakpoint: 'sm' }} padding="xl">
      <AppShell.Navbar
        p="md"
        style={{
          background: 'white',
          borderRight: '1px solid var(--mantine-color-gray-2)',
          overflowY: 'auto',
        }}
      >
        <Box mb="xl" px={10} pt={4}>
          <Group gap={8}>
            <Box
              style={{
                width: 28,
                height: 28,
                background: 'var(--mantine-color-violet-6)',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <IconBuilding size={14} color="white" />
            </Box>
            <Text
              fw={700}
              size="sm"
              style={{ letterSpacing: '-0.01em', color: 'var(--mantine-color-gray-9)' }}
            >
              Bookie
            </Text>
          </Group>
        </Box>

        <Stack gap={0}>
          {NAV_SECTIONS.map((section, si) => (
            <Box key={section.key} mb={si < NAV_SECTIONS.length - 1 ? 16 : 0}>
              {section.label && (
                <Text
                  px={10}
                  mb={4}
                  style={{
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.07em',
                    color: 'var(--mantine-color-gray-6)',
                  }}
                >
                  {section.label}
                </Text>
              )}
              <Stack gap={2}>
                {section.items.map((item) => (
                  <NavItem key={item.to} {...item} />
                ))}
              </Stack>
            </Box>
          ))}
        </Stack>
      </AppShell.Navbar>

      <AppShell.Main>
        <Box maw={1200} mx="auto">
          {!outlookConnected && !outlookStatusQuery.isLoading && (
            <Alert color="orange" variant="light" mb="md">
              <Group justify="space-between" align="center" wrap="wrap">
                <Text size="sm">
                  Outlook is disconnected. Import and sync actions may fail until you reconnect.
                </Text>
                <Group gap="xs">
                  <Button size="xs" component="a" href="/api/outlook/connect">
                    Reconnect Outlook
                  </Button>
                  <Button size="xs" variant="default" component={Link} to="/expenses">
                    Continue Manually
                  </Button>
                </Group>
              </Group>
            </Alert>
          )}
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/inbox" element={<Inbox />} />
            <Route path="/reconciliation" element={<Reconciliation />} />
            <Route path="/incomes" element={<Incomes />} />
            <Route path="/expenses" element={<Expenses />} />
            <Route path="/tax-report" element={<TaxReport />} />
            <Route path="/receipts" element={<Receipts />} />
            <Route path="/emails" element={<Emails />} />
            <Route path="/agent" element={<Agent />} />
            <Route path="/properties" element={<Properties />} />
            <Route path="/payers" element={<Payers />} />
            <Route path="/backup" element={<Backup />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppInner />
    </BrowserRouter>
  );
}
