import React, { useCallback, useEffect, useState } from 'react'
import { Card, Text, Group, Button, Stack, Anchor, Badge, Loader, Center, Alert, ActionIcon } from '@mantine/core'
import { IconAlertCircle, IconMail, IconClock, IconRefresh } from '@tabler/icons-react'
import { getOutlookStatus, getOutlookRentalEmails, parseEmail } from '../api/index.js'

const formatDate = (iso) => new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

export default function RentalEmails({ onQueued, refreshKey }) {
  const [emails, setEmails] = useState([])
  const [connected, setConnected] = useState(false)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [converting, setConverting] = useState(null)
  const [convertError, setConvertError] = useState(null)

  const loadPage = useCallback((pageNum) =>
    getOutlookRentalEmails(pageNum).then(data => {
      setEmails(data.emails)
      setHasMore(data.hasMore)
    }), [])

  useEffect(() => {
    setLoading(true)
    getOutlookStatus()
      .then(({ connected }) => {
        setConnected(connected)
        if (connected) return loadPage(0).then(() => setPage(0))
      })
      .finally(() => setLoading(false))
  }, [refreshKey])

  // Poll while any email is still pending (has pendingId but no expenseId)
  useEffect(() => {
    if (emails.some(e => e.pendingId)) {
      const id = setInterval(() => loadPage(page), 4000)
      return () => clearInterval(id)
    }
  }, [emails, page, loadPage])

  const goToPage = (newPage) => {
    setLoading(true)
    loadPage(newPage)
      .then(() => setPage(newPage))
      .finally(() => setLoading(false))
  }

  const handleConvert = async (email) => {
    setConverting(email.id)
    setConvertError(null)
    try {
      const result = await parseEmail(email.id, email.subject)
      setEmails(prev => prev.map(e => e.id === email.id ? { ...e, pendingId: result.id } : e))
      onQueued?.()
    } catch (err) {
      setConvertError(err.message || 'Failed to queue email. Please try again.')
    } finally {
      setConverting(null)
    }
  }

  if (loading) return <Center mb="xl"><Loader size="sm" /></Center>

  if (!connected) {
    return (
      <Card withBorder p="lg">
        <Group justify="space-between" align="center">
          <div>
            <Text fw={600} c="dark">Rental Emails</Text>
            <Text size="sm" c="dimmed">Connect Outlook to see emails tagged as Rental</Text>
          </div>
          <Anchor href="/api/outlook/connect" underline="never">
            <Button color="blue" size="sm">Connect Outlook</Button>
          </Anchor>
        </Group>
      </Card>
    )
  }

  return (
    <Card withBorder p="lg">
      <Group justify="space-between" mb="md">
        <Group gap="xs">
          <IconMail size={18} />
          <Text fw={600}>Rental Emails</Text>
        </Group>
        <Group gap="xs">
          <Badge variant="light" color="gray">{emails.length} email{emails.length !== 1 ? 's' : ''}</Badge>
          <ActionIcon variant="subtle" color="gray" onClick={() => goToPage(page)} title="Refresh emails">
            <IconRefresh size={16} />
          </ActionIcon>
        </Group>
      </Group>

      {convertError && (
        <Alert icon={<IconAlertCircle size={14} />} color="red" mb="sm" withCloseButton onClose={() => setConvertError(null)}>
          {convertError}
        </Alert>
      )}

      {emails.length === 0 ? (
        <Text c="dimmed" size="sm">No emails tagged as Rental</Text>
      ) : (
        <Stack gap={0}>
          {emails.map((email, i) => (
            <div key={email.id} style={{ borderBottom: i < emails.length - 1 ? '1px solid var(--mantine-color-gray-2)' : 'none', paddingTop: 10, paddingBottom: 10 }}>
              <Group justify="space-between" mb={2}>
                <Text fw={600} size="sm">{email.subject}</Text>
                <Text size="xs" c="dimmed">{formatDate(email.receivedAt)}</Text>
              </Group>
              <Text size="xs" c="dimmed" mb={2}>{email.sender}</Text>
              <Text size="xs" c="dimmed" truncate mb={6}>{email.preview}</Text>
              {email.pendingId ? (
                <Group gap="xs">
                  <IconClock size={14} color="var(--mantine-color-blue-6)" />
                  <Text size="xs" c="blue" fw={600}>Queued for processing</Text>
                </Group>
              ) : (
                <Button
                  size="compact-xs"
                  loading={converting === email.id}
                  onClick={() => handleConvert(email)}
                >
                  Parse Email
                </Button>
              )}
            </div>
          ))}
          <Group justify="space-between" mt="md">
            <Button variant="default" size="xs" disabled={page === 0} onClick={() => goToPage(page - 1)}>
              ← Prev
            </Button>
            <Text size="xs" c="dimmed">Page {page + 1}</Text>
            <Button variant="default" size="xs" disabled={!hasMore} onClick={() => goToPage(page + 1)}>
              Next →
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  )
}