import React, { useEffect, useRef, useState } from 'react'
import { Stack, Group, Title, Badge, Tabs } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import RentalEmails from '../components/RentalEmails.jsx'
import PendingExpenses from '../components/PendingExpenses.jsx'

export default function Emails() {
  const [activeTab, setActiveTab] = useState('emails')
  const [pendingCount, setPendingCount] = useState(0)
  const [emailsKey, setEmailsKey] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const activeTabRef = useRef(activeTab)

  useEffect(() => { activeTabRef.current = activeTab }, [activeTab])

  useEffect(() => {
    const es = new EventSource('/api/pending-expenses/events')
    es.addEventListener('pending-updated', (e) => {
      const data = JSON.parse(e.data)
      setPendingRefreshKey(k => k + 1)
      if (activeTabRef.current !== 'pending' && data.status === 'READY') {
        notifications.show({
          title: 'Email parsed',
          message: 'A new item is ready to review in Pending',
          color: 'green',
          autoClose: 6000,
        })
      }
    })
    return () => es.close()
  }, [])

  const handlePendingSaved = () => {
    setEmailsKey(k => k + 1)
  }

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
            onSaved={handlePendingSaved}
            onCountChange={setPendingCount}
            refreshKey={pendingRefreshKey}
          />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}