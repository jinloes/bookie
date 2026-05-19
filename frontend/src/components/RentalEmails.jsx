import React, { useState } from 'react'
import {
  Card, Text, Group, Button, Stack, Anchor, Badge, Loader, Center, Alert, ActionIcon, Modal,
  MultiSelect, Checkbox, Switch, Select, Divider,
} from '@mantine/core'
import { IconAlertCircle, IconMail, IconClock, IconRefresh, IconX, IconSettings } from '@tabler/icons-react'
import { notifications } from '@mantine/notifications'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getOutlookStatus, getOutlookRentalEmails, parseEmail, getOutlookAvailableFolders,
  getOutlookFolderSettings, updateOutlookFolderSettings, getOutlookMoveSettings, updateOutlookMoveSettings,
} from '../api/index.js'
import { fmtDate } from '../utils/formatters.js'
import { PENDING_STATUS } from '../constants.js'

// Polls every 4s only while at least one email is mid-parse. TanStack Query handles abort,
// stale-while-revalidate, and de-duplication of overlapping in-flight requests for us.
const POLL_MS = 4000

export default function RentalEmails({ onQueued, refreshKey }) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [converting, setConverting] = useState(null)
  const [convertError, setConvertError] = useState(null)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [savingFolders, setSavingFolders] = useState(false)
  // Local edits to settings (synced from the queries when the modal opens).
  const [folderSettings, setFolderSettings] = useState([])
  const [moveEnabled, setMoveEnabled] = useState(false)
  const [moveDestinationFolderId, setMoveDestinationFolderId] = useState(null)

  const statusQuery = useQuery({
    queryKey: ['outlook-status', refreshKey],
    queryFn: getOutlookStatus,
  })
  const connected = statusQuery.data?.connected === true

  const emailsQuery = useQuery({
    queryKey: ['outlook-rental-emails', page, refreshKey],
    queryFn: () => getOutlookRentalEmails(page),
    enabled: connected,
    refetchInterval: (q) => {
      const data = q.state.data
      const anyInFlight = data?.emails?.some(
        e => e.pendingId && e.pendingStatus !== PENDING_STATUS.FAILED
      )
      return anyInFlight ? POLL_MS : false
    },
  })
  const emails = emailsQuery.data?.emails ?? []
  const hasMore = emailsQuery.data?.hasMore ?? false

  const settingsQueriesEnabled = settingsOpen
  const availableQuery = useQuery({
    queryKey: ['outlook-available-folders'],
    queryFn: getOutlookAvailableFolders,
    enabled: settingsQueriesEnabled,
  })
  const foldersQuery = useQuery({
    queryKey: ['outlook-folder-settings'],
    queryFn: getOutlookFolderSettings,
    enabled: settingsQueriesEnabled,
  })
  const moveQuery = useQuery({
    queryKey: ['outlook-move-settings'],
    queryFn: getOutlookMoveSettings,
    enabled: settingsQueriesEnabled,
  })
  const availableFolders = (availableQuery.data ?? []).map(f => ({ value: f.id, label: f.displayPath }))
  const loadingFolders = availableQuery.isLoading || foldersQuery.isLoading || moveQuery.isLoading

  // Sync server settings into local edit state once they arrive (or when modal reopens).
  React.useEffect(() => {
    if (!settingsOpen) return
    if (foldersQuery.data) setFolderSettings(foldersQuery.data)
    if (moveQuery.data) {
      setMoveEnabled(moveQuery.data.enabled)
      setMoveDestinationFolderId(moveQuery.data.folderId || null)
    }
  }, [settingsOpen, foldersQuery.data, moveQuery.data])

  const handleConvert = async (email) => {
    setConverting(email.id)
    setConvertError(null)
    try {
      const result = await parseEmail(email.id, email.subject)
      // Optimistically reflect the new pending state without waiting for the next poll.
      queryClient.setQueryData(['outlook-rental-emails', page, refreshKey], prev =>
        prev ? {
          ...prev,
          emails: prev.emails.map(e => e.id === email.id ? { ...e, pendingId: result.id } : e),
        } : prev
      )
      onQueued?.()
    } catch (err) {
      setConvertError(err.message || 'Failed to queue email. Please try again.')
    } finally {
      setConverting(null)
    }
  }

  const selectedFolderIds = folderSettings.map(fs => fs.folderId)

  const handleFolderSelectionChange = (newIds) => {
    const existingMap = Object.fromEntries(folderSettings.map(fs => [fs.folderId, fs]))
    setFolderSettings(newIds.map(id => existingMap[id] ?? { folderId: id, expandSubfolders: false }))
  }

  const toggleExpandSubfolders = (folderId) => {
    setFolderSettings(prev =>
      prev.map(fs => fs.folderId === folderId ? { ...fs, expandSubfolders: !fs.expandSubfolders } : fs)
    )
  }

  const saveFolderSettings = async () => {
    setSavingFolders(true)
    try {
      // allSettled so a failure in one save doesn't strand the other; we report any errors.
      const results = await Promise.allSettled([
        updateOutlookFolderSettings(folderSettings),
        updateOutlookMoveSettings(moveEnabled, moveDestinationFolderId),
      ])
      const failures = results.filter(r => r.status === 'rejected')
      if (failures.length > 0) {
        throw new Error(failures.map(f => f.reason?.message).filter(Boolean).join('; '))
      }
      setSettingsOpen(false)
      // Force the rental list to refresh against the new folder/move settings.
      setPage(0)
      queryClient.invalidateQueries({ queryKey: ['outlook-rental-emails'] })
    } catch (err) {
      notifications.show({ title: 'Failed to save settings', message: err.message, color: 'red' })
    } finally {
      setSavingFolders(false)
    }
  }

  if (statusQuery.isLoading) return <Center mb="xl"><Loader size="sm" /></Center>

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
          <ActionIcon variant="subtle" color="gray" onClick={() => emailsQuery.refetch()} title="Refresh emails">
            <IconRefresh size={16} />
          </ActionIcon>
          <ActionIcon variant="subtle" color="gray" onClick={() => setSettingsOpen(true)} title="Configure folders">
            <IconSettings size={16} />
          </ActionIcon>
        </Group>
      </Group>

      {convertError && (
        <Alert icon={<IconAlertCircle size={14} />} color="red" mb="sm" withCloseButton onClose={() => setConvertError(null)}>
          {convertError}
        </Alert>
      )}

      {emailsQuery.isLoading ? (
        <Center py="md"><Loader size="sm" /></Center>
      ) : emails.length === 0 ? (
        <Text c="dimmed" size="sm">No emails tagged as Rental</Text>
      ) : (
        <Stack gap={0}>
          {emails.map((email, i) => (
            <div key={email.id} style={{ borderBottom: i < emails.length - 1 ? '1px solid var(--mantine-color-gray-2)' : 'none', paddingTop: 10, paddingBottom: 10 }}>
              <Group justify="space-between" mb={2}>
                <Text fw={600} size="sm">{email.subject}</Text>
                <Text size="xs" c="dimmed">{fmtDate(email.receivedAt)}</Text>
              </Group>
              <Text size="xs" c="dimmed" mb={2}>{email.sender}</Text>
              <Text size="xs" c="dimmed" truncate mb={6}>{email.preview}</Text>
              {email.pendingId && email.pendingStatus !== PENDING_STATUS.FAILED ? (
                <Group gap="xs">
                  <IconClock size={14} color="var(--mantine-color-blue-6)" />
                  <Text size="xs" c="blue" fw={600}>Queued for processing</Text>
                </Group>
              ) : email.pendingId && email.pendingStatus === PENDING_STATUS.FAILED ? (
                <Group gap="xs">
                  <IconX size={14} color="var(--mantine-color-red-6)" />
                  <Text size="xs" c="red" fw={600}>Parsing failed</Text>
                  <Button size="compact-xs" variant="subtle" color="red" onClick={() => handleConvert(email)}>
                    Retry
                  </Button>
                </Group>
              ) : (
                <Button
                  size="compact-xs"
                  loading={converting === email.id}
                  onClick={() => handleConvert(email)}
                >
                  Import Email
                </Button>
              )}
            </div>
          ))}
          <Group justify="space-between" mt="md">
            <Button variant="default" size="xs" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
              ← Prev
            </Button>
            <Text size="xs" c="dimmed">Page {page + 1}</Text>
            <Button variant="default" size="xs" disabled={!hasMore} onClick={() => setPage(p => p + 1)}>
              Next →
            </Button>
          </Group>
        </Stack>
      )}

      <Modal opened={settingsOpen} onClose={() => setSettingsOpen(false)} title="Email Settings" size="md">
        {loadingFolders ? (
          <Center py="xl"><Loader size="sm" /></Center>
        ) : (
          <Stack gap="md">
            <Text size="sm" c="dimmed">
              Select which Outlook folders to include when fetching rental emails. Leave empty to use the defaults (inbox, Rent Expenses, Taxes).
            </Text>
            <MultiSelect
              data={availableFolders}
              value={selectedFolderIds}
              onChange={handleFolderSelectionChange}
              placeholder="Select folders…"
              searchable
              clearable
            />
            {folderSettings.length > 0 && (
              <Stack gap="xs">
                {folderSettings.map(fs => {
                  const folder = availableFolders.find(f => f.value === fs.folderId)
                  return (
                    <Group key={fs.folderId} justify="space-between">
                      <Text size="sm">{folder?.label ?? fs.folderId}</Text>
                      <Checkbox
                        label="Include subfolders"
                        size="xs"
                        checked={fs.expandSubfolders}
                        onChange={() => toggleExpandSubfolders(fs.folderId)}
                      />
                    </Group>
                  )
                })}
              </Stack>
            )}
            <Divider />
            <Switch
              label="Move email to folder after saving"
              checked={moveEnabled}
              onChange={e => {
                setMoveEnabled(e.currentTarget.checked)
                if (!e.currentTarget.checked) setMoveDestinationFolderId(null)
              }}
            />
            {moveEnabled && (
              <Select
                label="Destination folder"
                placeholder="Select a folder…"
                data={availableFolders}
                value={moveDestinationFolderId}
                onChange={setMoveDestinationFolderId}
                searchable
              />
            )}
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setSettingsOpen(false)}>Cancel</Button>
              <Button
                loading={savingFolders}
                onClick={saveFolderSettings}
                disabled={moveEnabled && !moveDestinationFolderId}
              >
                Save
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </Card>
  )
}
