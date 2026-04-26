import React, { useState } from 'react'
import { Stack, Group, Title, Button, Card, Table, Text, Loader, Center, Alert, ActionIcon, Tooltip } from '@mantine/core'
import { modals } from '@mantine/modals'
import { IconCloudUpload, IconCloudDownload, IconAlertCircle, IconCheck, IconTrash } from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { triggerBackup, listBackups, restoreBackup, deleteBackup } from '../api/index.js'
import { fmtDateTime } from '../utils/formatters.js'

export default function Backup() {
  const queryClient = useQueryClient()
  const { data: backups = [], isLoading, error: backupsError } = useQuery({
    queryKey: ['backups'],
    queryFn: listBackups,
  })
  const [backing, setBacking] = useState(false)
  const [restoring, setRestoring] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [message, setMessage] = useState(null)
  const [actionError, setActionError] = useState(null)

  const handleBackup = async () => {
    setBacking(true)
    setMessage(null)
    setActionError(null)
    try {
      const result = await triggerBackup()
      setMessage(`Backup created: ${result.name}`)
      queryClient.invalidateQueries({ queryKey: ['backups'] })
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBacking(false)
    }
  }

  const handleRestore = (fileId, name) => {
    modals.openConfirmModal({
      title: 'Restore backup',
      children: (
        <Text size="sm">
          Restore from <strong>{name}</strong>? All current data will be replaced. The app may need a
          refresh after restoring.
        </Text>
      ),
      labels: { confirm: 'Restore', cancel: 'Cancel' },
      confirmProps: { color: 'orange' },
      onConfirm: async () => {
        setRestoring(fileId)
        setMessage(null)
        setActionError(null)
        try {
          await restoreBackup(fileId)
          setMessage('Database restored successfully. Please refresh the page to see restored data.')
        } catch (e) {
          setActionError(e.message)
        } finally {
          setRestoring(null)
        }
      },
    })
  }

  const handleDelete = (fileId, name) => {
    modals.openConfirmModal({
      title: 'Delete backup',
      children: (
        <Text size="sm">
          Delete <strong>{name}</strong> from OneDrive? This cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        setDeleting(fileId)
        setMessage(null)
        setActionError(null)
        try {
          await deleteBackup(fileId)
          setMessage(`Deleted: ${name}`)
          queryClient.invalidateQueries({ queryKey: ['backups'] })
        } catch (e) {
          setActionError(e.message)
        } finally {
          setDeleting(null)
        }
      },
    })
  }

  if (isLoading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>OneDrive Backups</Title>
        <Button onClick={handleBackup} loading={backing} leftSection={<IconCloudUpload size={16} />}>
          Backup Now
        </Button>
      </Group>

      {message && (
        <Alert icon={<IconCheck size={16} />} color="green" withCloseButton onClose={() => setMessage(null)}>
          {message}
        </Alert>
      )}
      {(actionError || backupsError) && (
        <Alert icon={<IconAlertCircle size={16} />} color="red" withCloseButton onClose={() => setActionError(null)}>
          {actionError || backupsError?.message}
        </Alert>
      )}

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              {['File', 'Last Modified', 'Size', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {backups.length === 0 ? (
              <Table.Tr>
                <Table.Td colSpan={4}>
                  <Text ta="center" c="dimmed" py="xl">No backups yet — click "Backup Now" to create one</Text>
                </Table.Td>
              </Table.Tr>
            ) : backups.map(b => (
              <Table.Tr key={b.id}>
                <Table.Td fw={500}>{b.name}</Table.Td>
                <Table.Td c="dimmed">{b.lastModified ? fmtDateTime(b.lastModified) : '—'}</Table.Td>
                <Table.Td c="dimmed">{b.size ? `${(b.size / 1024).toFixed(1)} KB` : '—'}</Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <Button
                      size="xs"
                      variant="light"
                      color="orange"
                      leftSection={<IconCloudDownload size={14} />}
                      loading={restoring === b.id}
                      onClick={() => handleRestore(b.id, b.name)}
                    >
                      Restore
                    </Button>
                    <Tooltip label="Delete backup">
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        loading={deleting === b.id}
                        onClick={() => handleDelete(b.id, b.name)}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Tooltip>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  )
}
