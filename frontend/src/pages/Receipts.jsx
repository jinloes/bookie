import React, { useState } from 'react'
import {
  Stack, Group, Title, Button, Card, TextInput, Table, Text, Loader, Center,
  Badge, ActionIcon, Modal, Tooltip, ThemeIcon, ScrollArea, Tabs, FileInput, Alert, Anchor
} from '@mantine/core'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import {
  IconUpload, IconFileTypePdf, IconExternalLink, IconSettings, IconAlertTriangle,
  IconCheck, IconTrash, IconReceipt, IconTrendingUp
} from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listReceipts, uploadReceipt, parseReceipt, deleteReceipt,
  getReceiptSettings, updateReceiptSettings
} from '../api/index.js'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import { fmtDate } from '../utils/formatters.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

export default function Receipts() {
  const queryClient = useQueryClient()
  const { data: receipts = [], isLoading: receiptsLoading, error: receiptsQueryError } = useQuery({
    queryKey: ['receipts'],
    queryFn: listReceipts,
  })
  const { data: receiptSettings } = useQuery({
    queryKey: ['receiptSettings'],
    queryFn: getReceiptSettings,
  })
  const folderBase = receiptSettings?.folderBase || ''

  const [receiptFile, setReceiptFile] = useState(null)
  const [uploadingReceipt, setUploadingReceipt] = useState(false)
  const [uploadResult, setUploadResult] = useState(null)
  const [folderSettingsOpen, setFolderSettingsOpen] = useState(false)
  const [folderBaseInput, setFolderBaseInput] = useState('')
  const [parsingReceiptId, setParsingReceiptId] = useState(null)
  const [activeTab, setActiveTab] = useState('receipts')
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)

  usePendingSSE({
    filter: (d) => d.sourceType === 'RECEIPT',
    activeTab,
    notification: { title: 'Receipt parsed', message: 'A new entry is ready to review in the Pending tab', color: 'green' },
    onUpdate: () => setPendingRefreshKey(k => k + 1),
  })

  const handleReceiptUpload = async () => {
    if (!receiptFile) return
    setUploadingReceipt(true)
    setUploadResult(null)
    try {
      const result = await uploadReceipt(receiptFile)
      setUploadResult(result)
      setReceiptFile(null)
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
    } catch (err) {
      notifications.show({ title: 'Upload failed', message: err.message, color: 'red' })
    } finally {
      setUploadingReceipt(false)
    }
  }

  const handleParseReceipt = async (itemId) => {
    setParsingReceiptId(itemId)
    try {
      await parseReceipt(itemId)
      notifications.show({
        title: 'Receipt queued',
        message: 'Parsing receipt — check the Pending tab when ready',
        color: 'blue',
        autoClose: 6000,
      })
      setActiveTab('pending')
    } catch (err) {
      notifications.show({ title: 'Parse failed', message: err.message, color: 'red' })
    } finally {
      setParsingReceiptId(null)
    }
  }

  const handleDeleteReceipt = (itemId, name, hasLinkedRecord) => {
    modals.openConfirmModal({
      title: 'Delete receipt',
      children: (
        <Text size="sm">
          {hasLinkedRecord
            ? `"${name}" and its linked record will be permanently deleted. This cannot be undone.`
            : `"${name}" will be permanently deleted from OneDrive. This cannot be undone.`}
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deleteReceipt(itemId)
          queryClient.invalidateQueries({ queryKey: ['receipts'] })
        } catch (err) {
          notifications.show({ title: 'Delete failed', message: err.message, color: 'red' })
        }
      },
    })
  }

  const handleOpenFolderSettings = () => {
    setFolderBaseInput(folderBase)
    setFolderSettingsOpen(true)
  }

  const handleSaveFolderSettings = async () => {
    try {
      await updateReceiptSettings(folderBaseInput)
      queryClient.invalidateQueries({ queryKey: ['receiptSettings'] })
      setFolderSettingsOpen(false)
    } catch (err) {
      notifications.show({ title: 'Save failed', message: err.message, color: 'red' })
    }
  }

  const handlePendingSaved = () => {
    queryClient.invalidateQueries({ queryKey: ['receipts'] })
    setActiveTab('receipts')
  }

  const linkedRecord = (r) => {
    if (r.expenseId) return { id: r.expenseId, type: 'expense' }
    if (r.incomeId) return { id: r.incomeId, type: 'income' }
    return null
  }

  return (
    <Stack gap="lg">
      <Title order={2}>Receipts</Title>

      <Modal
        opened={folderSettingsOpen}
        onClose={() => setFolderSettingsOpen(false)}
        title="Receipt Folder Settings"
        size="sm"
      >
        <Stack gap="sm">
          <TextInput
            label="OneDrive folder path"
            description="Year subfolders will be created automatically (e.g. bookie/taxes/2024)"
            value={folderBaseInput}
            onChange={e => setFolderBaseInput(e.target.value)}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setFolderSettingsOpen(false)}>Cancel</Button>
            <Button disabled={!folderBaseInput.trim()} onClick={handleSaveFolderSettings}>Save</Button>
          </Group>
        </Stack>
      </Modal>

      <Tabs value={activeTab} onChange={setActiveTab}>
        <Tabs.List>
          <Tabs.Tab value="receipts" leftSection={<IconReceipt size={14} />}>Receipts</Tabs.Tab>
          <Tabs.Tab
            value="pending"
            rightSection={pendingCount > 0
              ? <Badge color="orange" size="xs" circle>{pendingCount}</Badge>
              : null}
          >
            Pending
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="receipts" pt="md">
          <Card withBorder p="lg" mb="md">
            <Group justify="space-between" mb="md">
              <Title order={4}>Upload Receipt</Title>
              <Tooltip label="Configure OneDrive folder">
                <ActionIcon variant="subtle" color="gray" onClick={handleOpenFolderSettings}>
                  <IconSettings size={16} />
                </ActionIcon>
              </Tooltip>
            </Group>
            {folderBase && (
              <Text size="xs" c="dimmed" mb="sm">
                Uploads to: <strong>{folderBase}/pending/</strong> — moved to the correct year folder when the entry is saved
              </Text>
            )}
            <Group align="flex-end" gap="sm">
              <FileInput
                label="PDF file"
                placeholder="Select PDF"
                accept="application/pdf"
                value={receiptFile}
                onChange={setReceiptFile}
                leftSection={<IconFileTypePdf size={16} />}
                style={{ flex: 1 }}
                clearable
              />
              <Button
                leftSection={<IconUpload size={16} />}
                onClick={handleReceiptUpload}
                loading={uploadingReceipt}
                disabled={!receiptFile}
                mt="lg"
              >
                Upload
              </Button>
            </Group>

            {uploadResult && (
              <Alert
                mt="sm"
                color={uploadResult.duplicate ? 'yellow' : 'green'}
                icon={uploadResult.duplicate ? <IconAlertTriangle size={16} /> : <IconCheck size={16} />}
                title={uploadResult.duplicate ? 'File already uploaded' : 'Upload successful'}
              >
                {uploadResult.duplicate
                  ? <>This file was already uploaded. {linkedRecord(uploadResult.receipt)
                      ? <span>It is already linked to a record.</span>
                      : <span>No entry linked yet — click "Create Entry" below.</span>}
                    </>
                  : <>Receipt saved to OneDrive.</>}
              </Alert>
            )}
          </Card>

          <Card withBorder p={0}>
            {receiptsLoading ? (
              <Center py="xl"><Loader /></Center>
            ) : receiptsQueryError ? (
              <Text ta="center" c="red" py="xl">{receiptsQueryError.message}</Text>
            ) : receipts.length === 0 ? (
              <Text ta="center" c="dimmed" py="xl">
                No receipts found in OneDrive. Upload a PDF to get started.
              </Text>
            ) : (
              <ScrollArea>
                <Table striped highlightOnHover miw={700}>
                  <Table.Thead>
                    <Table.Tr>
                      {['File', 'Status', 'Uploaded', 'Linked Record', 'Actions'].map(h => (
                        <Table.Th key={h}>{h}</Table.Th>
                      ))}
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {receipts
                      .sort((a, b) => {
                        if (a.pending !== b.pending) return a.pending ? -1 : 1
                        return b.year - a.year || a.name.localeCompare(b.name)
                      })
                      .map(r => {
                        const linked = linkedRecord(r)
                        return (
                          <Table.Tr key={r.id}>
                            <Table.Td>
                              <Group gap="xs">
                                <ThemeIcon variant="light" color="red" size="sm">
                                  <IconFileTypePdf size={12} />
                                </ThemeIcon>
                                <Anchor href={`/api/receipts/${r.id}/download`} target="_blank" size="sm">
                                  {r.name}
                                </Anchor>
                              </Group>
                            </Table.Td>
                            <Table.Td>
                              {r.pending
                                ? <Badge color="orange" variant="light" size="sm">Pending</Badge>
                                : <Badge color="teal" variant="light" size="sm">{r.year}</Badge>}
                            </Table.Td>
                            <Table.Td>
                              <Text size="xs" c="dimmed">
                                {r.uploadedAt ? fmtDate(r.uploadedAt) : '—'}
                              </Text>
                            </Table.Td>
                            <Table.Td>
                              {linked ? (
                                <Badge
                                  color={linked.type === 'income' ? 'green' : 'red'}
                                  variant="light"
                                  size="sm"
                                  leftSection={linked.type === 'income'
                                    ? <IconTrendingUp size={10} />
                                    : <IconReceipt size={10} />}
                                >
                                  {linked.type === 'income' ? 'Income' : 'Expense'} #{linked.id}
                                </Badge>
                              ) : (
                                <Badge color="gray" variant="outline" size="sm">Not linked</Badge>
                              )}
                            </Table.Td>
                            <Table.Td>
                              <Group gap="xs">
                                {r.webUrl && (
                                  <Tooltip label="Open in OneDrive">
                                    <ActionIcon
                                      variant="subtle"
                                      color="blue"
                                      component="a"
                                      href={r.webUrl}
                                      target="_blank"
                                    >
                                      <IconExternalLink size={16} />
                                    </ActionIcon>
                                  </Tooltip>
                                )}
                                {!linked && (
                                  <Tooltip label="Parse receipt and create expense or income">
                                    <Button
                                      size="xs"
                                      variant="light"
                                      loading={parsingReceiptId === r.id}
                                      onClick={() => handleParseReceipt(r.id)}
                                    >
                                      Create Entry
                                    </Button>
                                  </Tooltip>
                                )}
                                <Tooltip label={linked ? 'Delete receipt and linked record' : 'Delete receipt'}>
                                  <ActionIcon
                                    variant="subtle"
                                    color="red"
                                    onClick={() => handleDeleteReceipt(r.id, r.name, !!linked)}
                                  >
                                    <IconTrash size={16} />
                                  </ActionIcon>
                                </Tooltip>
                              </Group>
                            </Table.Td>
                          </Table.Tr>
                        )
                      })}
                  </Table.Tbody>
                </Table>
              </ScrollArea>
            )}
          </Card>
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md" keepMounted>
          <PendingExpenses
            onSaved={handlePendingSaved}
            onCountChange={setPendingCount}
            refreshKey={pendingRefreshKey}
            filterSource="RECEIPT"
          />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
