import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, Table, Text, Loader, Center, Alert } from '@mantine/core'
import { IconCloudUpload, IconCloudDownload, IconAlertCircle, IconCheck } from '@tabler/icons-react'
import { triggerBackup, listBackups, restoreBackup } from '../api/index.js'

export default function Backup() {
  const [backups, setBackups] = useState([])
  const [loading, setLoading] = useState(true)
  const [backing, setBacking] = useState(false)
  const [restoring, setRestoring] = useState(null)
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)

  const load = () => {
    setLoading(true)
    listBackups()
      .then(setBackups)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleBackup = async () => {
    setBacking(true)
    setMessage(null)
    setError(null)
    try {
      const result = await triggerBackup()
      setMessage(`Backup created: ${result.name}`)
      load()
    } catch (e) {
      setError(e.message)
    } finally {
      setBacking(false)
    }
  }

  const handleRestore = async (fileId, name) => {
    if (!confirm(`Restore from "${name}"? All current data will be replaced. The app may need a refresh after restoring.`)) return
    setRestoring(fileId)
    setMessage(null)
    setError(null)
    try {
      await restoreBackup(fileId)
      setMessage('Database restored successfully. Please refresh the page to see restored data.')
    } catch (e) {
      setError(e.message)
    } finally {
      setRestoring(null)
    }
  }

  if (loading) return <Center h={200}><Loader /></Center>

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
      {error && (
        <Alert icon={<IconAlertCircle size={16} />} color="red" withCloseButton onClose={() => setError(null)}>
          {error}
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
                <Table.Td c="dimmed">{b.lastModified ? new Date(b.lastModified).toLocaleString() : '—'}</Table.Td>
                <Table.Td c="dimmed">{b.size ? `${(b.size / 1024).toFixed(1)} KB` : '—'}</Table.Td>
                <Table.Td>
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
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  )
}