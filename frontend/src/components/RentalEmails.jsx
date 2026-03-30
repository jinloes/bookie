import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Text, Group, Button, Stack, Anchor, Badge, Loader, Center, Alert } from '@mantine/core'
import { IconAlertCircle } from '@tabler/icons-react'
import { IconMail, IconCheck } from '@tabler/icons-react'
import { getOutlookStatus, getOutlookRentalEmails, convertEmailToExpense } from '../api/index.js'

const formatDate = (iso) => new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

export default function RentalEmails() {
  const [emails, setEmails] = useState([])
  const [connected, setConnected] = useState(false)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [converting, setConverting] = useState(null)
  const [convertError, setConvertError] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    getOutlookStatus()
      .then(({ connected }) => {
        setConnected(connected)
        if (connected) return getOutlookRentalEmails(0).then(data => {
          setEmails(data.emails)
          setHasMore(data.hasMore)
        })
      })
      .finally(() => setLoading(false))
  }, [])

  const goToPage = (newPage) => {
    setLoading(true)
    getOutlookRentalEmails(newPage)
      .then(data => {
        setEmails(data.emails)
        setHasMore(data.hasMore)
        setPage(newPage)
      })
      .finally(() => setLoading(false))
  }

  const handleConvert = async (emailId) => {
    setConverting(emailId)
    setConvertError(null)
    try {
      const suggestion = await convertEmailToExpense(emailId)
      navigate('/expenses', { state: { prefill: suggestion } })
    } catch (err) {
      setConvertError(err.message || 'Failed to parse email. Please try again.')
    } finally {
      setConverting(null)
    }
  }

  if (loading) return <Center mb="xl"><Loader size="sm" /></Center>

  if (!connected) {
    return (
      <Card withBorder mb="xl" p="lg">
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
    <Card withBorder mb="xl" p="lg">
      <Group justify="space-between" mb="md">
        <Group gap="xs">
          <IconMail size={18} />
          <Text fw={600}>Rental Emails</Text>
        </Group>
        <Badge variant="light" color="gray">{emails.length} email{emails.length !== 1 ? 's' : ''}</Badge>
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
              {email.expenseId ? (
                <Group gap="xs">
                  <IconCheck size={14} color="var(--mantine-color-green-6)" />
                  <Text size="xs" c="green" fw={600}>Expense created</Text>
                  <Button size="compact-xs" variant="outline"
                    onClick={() => navigate('/expenses', { state: { highlightId: email.expenseId } })}>
                    View
                  </Button>
                </Group>
              ) : (
                <Button
                  size="compact-xs"
                  loading={converting === email.id}
                  onClick={() => handleConvert(email.id)}
                >
                  Convert to Expense
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