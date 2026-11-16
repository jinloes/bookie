import React, { Suspense, lazy, useEffect, useRef } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, Link } from 'react-router-dom';
import {
  Alert,
  AppShell,
  Badge,
  Box,
  Burger,
  Button,
  Center,
  Group,
  Loader,
  NavLink as MantineNavLink,
  Stack,
  Text,
} from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import {
  IconBuilding,
  IconDatabase,
  IconFileAnalytics,
  IconHome,
  IconMail,
  IconArrowsExchange,
  IconRobot,
  IconScale,
  IconSettings,
  IconUsers,
  IconBriefcase,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getInboxItems, resolveApiUrl } from './api/index.js';
import { openOutlookConnect } from './utils/links.js';
import { usePendingSSE } from './hooks/usePendingSSE.js';
import { useBackendHealth } from './hooks/useBackendHealth.js';
import { useOutlookStatus } from './hooks/useOutlookStatus.js';
import { PENDING_STATUS } from './constants.js';
import { queryKeys } from './queryKeys.js';
import ErrorBoundary from './components/ErrorBoundary.jsx';
import Dashboard from './pages/Dashboard.jsx';
import { COLORS } from './designTokens.js';

const Transactions = lazy(() => import('./pages/Transactions.jsx'));
const Emails = lazy(() => import('./pages/Emails.jsx'));
const Agent = lazy(() => import('./pages/Agent.jsx'));
const Properties = lazy(() => import('./pages/Properties.jsx'));
const Payers = lazy(() => import('./pages/Payers.jsx'));
const Backup = lazy(() => import('./pages/Backup.jsx'));
const Reconciliation = lazy(() => import('./pages/Reconciliation.jsx'));
const Settings = lazy(() => import('./pages/Settings.jsx'));
const TaxReport = lazy(() => import('./pages/TaxReport.jsx'));
const Activities = lazy(() => import('./pages/Activities.jsx'));

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

function TransactionsBadge() {
  // SSE in AppInner invalidates the inbox on every update, so polling here would just be
  // redundant network traffic.
  const { data: inboxItems = [] } = useQuery({
    queryKey: queryKeys.inbox,
    queryFn: getInboxItems,
  });
  const count = inboxItems.filter((item) => item.state === PENDING_STATUS.READY).length;
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
      {
        to: '/transactions',
        label: 'Transactions',
        icon: IconArrowsExchange,
        badge: <TransactionsBadge />,
      },
      { to: '/reconciliation', label: 'Reconciliation', icon: IconScale },
    ],
  },
  {
    key: 'financial',
    label: 'Financial',
    items: [
      { to: '/tax-report', label: 'Tax Report', icon: IconFileAnalytics },
      { to: '/emails', label: 'Emails', icon: IconMail },
    ],
  },
  {
    key: 'records',
    label: 'Records',
    items: [
      { to: '/properties', label: 'Properties', icon: IconBuilding },
      { to: '/activities', label: 'Activities', icon: IconBriefcase },
      { to: '/payers', label: 'Counterparties', icon: IconUsers },
    ],
  },
  {
    key: 'system',
    items: [
      { to: '/agent', label: 'Financial Assistant', icon: IconRobot },
      { to: '/backup', label: 'Backup', icon: IconDatabase },
      { to: '/settings', label: 'Settings', icon: IconSettings },
    ],
  },
];

function BrandLogo() {
  return (
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
      <Text fw={700} size="sm" style={{ letterSpacing: '-0.01em', color: COLORS.TEXT_STRONG }}>
        Bookie
      </Text>
    </Group>
  );
}

function NavItem({ to, label, icon: Icon, end, badge, pathname, onNavigate }) {
  const isActive = end ? pathname === to : pathname === to || pathname.startsWith(`${to}/`);
  return (
    <MantineNavLink
      component={Link}
      to={to}
      active={isActive}
      aria-current={isActive ? 'page' : undefined}
      label={label}
      leftSection={<Icon size={16} />}
      rightSection={badge}
      color="violet"
      variant="light"
      onClick={onNavigate}
      styles={{
        root: {
          borderRadius: 6,
          padding: '6px 10px',
          userSelect: 'none',
        },
        label: {
          fontSize: 'var(--mantine-font-size-sm)',
          fontWeight: isActive ? 600 : 400,
        },
      }}
    />
  );
}

function AppInner() {
  const location = useLocation();
  const queryClient = useQueryClient();
  const backendStatus = useBackendHealth();
  const previousBackendStatus = useRef(backendStatus);
  const outlookStatus = useOutlookStatus();
  const isMobile = useMediaQuery('(max-width: 47.99em)');
  const [mobileNavOpened, { toggle: toggleMobileNav, close: closeMobileNav }] =
    useDisclosure(false);

  useEffect(() => {
    if (previousBackendStatus.current !== 'up' && backendStatus === 'up') {
      queryClient.refetchQueries({ type: 'active' });
    }
    previousBackendStatus.current = backendStatus;
  }, [backendStatus, queryClient]);

  usePendingSSE({
    notification: {
      title: 'Item ready',
      message: 'A new item is ready to review in the Review Queue tab',
      color: 'green',
    },
    activeTab: location.pathname.startsWith('/transactions') ? 'pending' : 'other',
    onUpdate: () => queryClient.invalidateQueries({ queryKey: queryKeys.inbox }),
    queryClient,
    reconnectTrigger: backendStatus === 'up' ? 'up' : null,
  });
  return (
    <AppShell
      header={{ height: 52, collapsed: !isMobile }}
      navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !mobileNavOpened } }}
      padding="xl"
    >
      <AppShell.Header px="md" style={{ borderBottom: `1px solid ${COLORS.BORDER}` }}>
        <Group h="100%" gap="sm">
          <Burger
            opened={mobileNavOpened}
            onClick={toggleMobileNav}
            size="sm"
            aria-label="Toggle navigation"
          />
          <BrandLogo />
        </Group>
      </AppShell.Header>

      <AppShell.Navbar
        p="md"
        style={{
          background: 'var(--mantine-color-body)',
          borderRight: `1px solid ${COLORS.BORDER}`,
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Box mb="xl" px={10} pt={4} visibleFrom="sm">
          <BrandLogo />
        </Box>

        <Stack gap={0}>
          {NAV_SECTIONS.map((section, si) => (
            <Box key={section.key} mb={si < NAV_SECTIONS.length - 1 ? 16 : 0}>
              {section.label && (
                <Text
                  px={10}
                  mb={4}
                  style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                    color: 'var(--mantine-color-dimmed)',
                  }}
                >
                  {section.label}
                </Text>
              )}
              <Stack gap={2}>
                {section.items.map((item) => (
                  <NavItem
                    key={item.to}
                    {...item}
                    pathname={location.pathname}
                    onNavigate={closeMobileNav}
                  />
                ))}
              </Stack>
            </Box>
          ))}
        </Stack>

        <Box style={{ flex: 1 }} />

        <Box px={10} py="xs" style={{ borderTop: `1px solid ${COLORS.BORDER}` }}>
          <Badge
            size="xs"
            color={backendStatus === 'up' ? 'green' : backendStatus === 'down' ? 'red' : 'gray'}
            variant="light"
            style={{ width: '100%', justifyContent: 'center' }}
          >
            Backend: {backendStatus === 'unknown' ? 'checking...' : backendStatus}
          </Badge>
        </Box>
      </AppShell.Navbar>

      <AppShell.Main style={{ background: COLORS.CANVAS_BG }}>
        <Box maw={1200} mx="auto">
          {outlookStatus.isDisconnected && (
            <Alert color="orange" variant="light" mb="md">
              <Group justify="space-between" align="center" wrap="wrap">
                <Text size="sm">
                  Outlook is disconnected. Import and sync actions may fail until you reconnect.
                </Text>
                <Group gap="xs">
                  <Button size="xs" onClick={() => openOutlookConnect(resolveApiUrl)}>
                    Reconnect Outlook
                  </Button>
                  <Button size="xs" variant="default" component={Link} to="/transactions/expenses">
                    Continue Manually
                  </Button>
                </Group>
              </Group>
            </Alert>
          )}
          <Suspense
            fallback={
              <Center mih={320}>
                <Loader size="md" />
              </Center>
            }
          >
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/transactions/*" element={<Transactions />} />
              {/* Legacy paths — redirect so existing bookmarks and internal links keep working. */}
              <Route path="/inbox" element={<Navigate to="/transactions/review" replace />} />
              <Route path="/incomes" element={<Navigate to="/transactions/income" replace />} />
              <Route path="/expenses" element={<Navigate to="/transactions/expenses" replace />} />
              <Route path="/receipts" element={<Navigate to="/transactions/receipts" replace />} />
              <Route path="/reconciliation" element={<Reconciliation />} />
              <Route path="/tax-report" element={<TaxReport />} />
              <Route path="/emails" element={<Emails />} />
              <Route path="/agent" element={<Agent />} />
              <Route path="/properties" element={<Properties />} />
              <Route path="/activities" element={<Activities />} />
              <Route path="/payers" element={<Payers />} />
              <Route path="/backup" element={<Backup />} />
              <Route path="/settings" element={<Settings />} />
            </Routes>
          </Suspense>
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppInner />
      </BrowserRouter>
    </ErrorBoundary>
  );
}
