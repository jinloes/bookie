import React, { useState } from 'react'
import { Stack, Title, Badge, Tabs } from '@mantine/core'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import RentalEmails from '../components/RentalEmails.jsx'
import PendingExpenses from '../components/PendingExpenses.jsx'

export default function Emails() {
  const [activeTab, setActiveTab] = useState('emails')
  const [pendingCount, setPendingCount] = useState(0)
  const [emailsKey, setEmailsKey] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)

  usePendingSSE({
    activeTab,
    notification: { title: 'Email parsed', message: 'A new item is ready to review in Pending', color: 'green' },
    onUpdate: () => setPendingRefreshKey(k => k + 1),
  })

  return (
    <Stack gap="lg">
      <Title order={2}>Emails</Title>

      <Tabs value={activeTab} onChange={setActiveTab}>
        <Tabs.List>
          <Tabs.Tab value="emails">Emails</Tabs.Tab>
          <Tabs.Tab
            value="pending"
            rightSection={pendingCount > 0
              ? <Badge color="orange" size="xs" circle>{pendingCount}</Badge>
              : null}
          >
            Pending
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="emails" pt="md">
          <RentalEmails onQueued={() => setActiveTab('pending')} refreshKey={emailsKey} />
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md" keepMounted>
          <PendingExpenses
            onSaved={() => setEmailsKey(k => k + 1)}
            onCountChange={setPendingCount}
            refreshKey={pendingRefreshKey}
          />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}