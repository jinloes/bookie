import React from 'react'
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { AppShell, Badge, Box, Group, Stack, Text } from '@mantine/core'
import {
  IconBuilding, IconDatabase, IconHome, IconInbox, IconMail,
  IconReceipt, IconReceipt2, IconRobot, IconTrendingUp, IconUsers
} from '@tabler/icons-react'
import { useQuery } from '@tanstack/react-query'
import { getPendingExpenses } from './api/index.js'
import Dashboard from './pages/Dashboard.jsx'
import Inbox from './pages/Inbox.jsx'
import Incomes from './pages/Incomes.jsx'
import Expenses from './pages/Expenses.jsx'
import Receipts from './pages/Receipts.jsx'
import Emails from './pages/Emails.jsx'
import Agent from './pages/Agent.jsx'
import Properties from './pages/Properties.jsx'
import Payers from './pages/Payers.jsx'
import Backup from './pages/Backup.jsx'

function InboxBadge() {
  const { data = [] } = useQuery({
    queryKey: ['pendingExpenses'],
    queryFn: getPendingExpenses,
    refetchInterval: 15000,
  })
  const count = data.filter(i => i.status === 'READY').length
  return count > 0 ? <Badge color="orange" size="xs" circle>{count}</Badge> : null
}

const NAV_SECTIONS = [
  {
    items: [
      { to: '/', label: 'Dashboard', icon: IconHome, end: true },
      { to: '/inbox', label: 'Inbox', icon: IconInbox, badge: <InboxBadge /> },
    ],
  },
  {
    label: 'Financial',
    items: [
      { to: '/incomes', label: 'Income', icon: IconTrendingUp },
      { to: '/expenses', label: 'Expenses', icon: IconReceipt },
      { to: '/receipts', label: 'Receipts', icon: IconReceipt2 },
      { to: '/emails', label: 'Emails', icon: IconMail },
    ],
  },
  {
    label: 'Manage',
    items: [
      { to: '/agent', label: 'AI Agent', icon: IconRobot },
      { to: '/properties', label: 'Properties', icon: IconBuilding },
      { to: '/payers', label: 'Payers', icon: IconUsers },
      { to: '/backup', label: 'Backup', icon: IconDatabase },
    ],
  },
]

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
  )
}

export default function App() {
  return (
    <BrowserRouter>
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
              <Box key={si} mb={si < NAV_SECTIONS.length - 1 ? 16 : 0}>
                {section.label && (
                  <Text
                    px={10}
                    mb={4}
                    style={{
                      fontSize: '0.65rem',
                      fontWeight: 700,
                      textTransform: 'uppercase',
                      letterSpacing: '0.07em',
                      color: 'var(--mantine-color-gray-4)',
                    }}
                  >
                    {section.label}
                  </Text>
                )}
                <Stack gap={2}>
                  {section.items.map(item => (
                    <NavItem key={item.to} {...item} />
                  ))}
                </Stack>
              </Box>
            ))}
          </Stack>
        </AppShell.Navbar>

        <AppShell.Main>
          <Box maw={1200} mx="auto">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/inbox" element={<Inbox />} />
              <Route path="/incomes" element={<Incomes />} />
              <Route path="/expenses" element={<Expenses />} />
              <Route path="/receipts" element={<Receipts />} />
              <Route path="/emails" element={<Emails />} />
              <Route path="/agent" element={<Agent />} />
              <Route path="/properties" element={<Properties />} />
              <Route path="/payers" element={<Payers />} />
              <Route path="/backup" element={<Backup />} />
            </Routes>
          </Box>
        </AppShell.Main>
      </AppShell>
    </BrowserRouter>
  )
}
