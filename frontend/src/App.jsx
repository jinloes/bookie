import React from 'react'
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { AppShell, Group, Text, NavLink as MantineNavLink, Box } from '@mantine/core'
import { IconHome, IconTrendingUp, IconReceipt, IconReceipt2, IconBuilding, IconUsers, IconRobot, IconDatabase, IconMail } from '@tabler/icons-react'
import Dashboard from './pages/Dashboard.jsx'
import Incomes from './pages/Incomes.jsx'
import Expenses from './pages/Expenses.jsx'
import Receipts from './pages/Receipts.jsx'
import Emails from './pages/Emails.jsx'
import Agent from './pages/Agent.jsx'
import Properties from './pages/Properties.jsx'
import Payers from './pages/Payers.jsx'
import Backup from './pages/Backup.jsx'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: IconHome, end: true },
  { to: '/emails', label: 'Emails', icon: IconMail },
  { to: '/incomes', label: 'Income', icon: IconTrendingUp },
  { to: '/expenses', label: 'Expenses', icon: IconReceipt },
  { to: '/receipts', label: 'Receipts', icon: IconReceipt2 },
  { to: '/agent', label: 'AI Agent', icon: IconRobot },
  { to: '/properties', label: 'Properties', icon: IconBuilding },
  { to: '/payers', label: 'Payers', icon: IconUsers },
  { to: '/backup', label: 'Backup', icon: IconDatabase },
]

export default function App() {
  return (
    <BrowserRouter>
      <AppShell
        header={{ height: 56 }}
        padding="xl"
      >
        <AppShell.Header>
          <Group h="100%" px="xl" gap="xs">
            <Text fw={700} size="lg" c="blue.7" mr="sm">🏠 Bookie</Text>
            {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} style={{ textDecoration: 'none' }}>
                {({ isActive }) => (
                  <MantineNavLink
                    label={label}
                    leftSection={<Icon size={16} />}
                    active={isActive}
                    style={{ borderRadius: 6, padding: '6px 12px' }}
                    component="span"
                  />
                )}
              </NavLink>
            ))}
          </Group>
        </AppShell.Header>

        <AppShell.Main>
          <Box maw={1200} mx="auto">
            <Routes>
              <Route path="/" element={<Dashboard />} />
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