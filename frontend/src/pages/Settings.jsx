import React, { useEffect, useState } from 'react'
import {
  Anchor, Badge, Button, Card, Center, Checkbox, Divider, Group, Loader,
  MultiSelect, Select, Stack, Switch, Text, TextInput, Title,
} from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getOutlookStatus,
  getOutlookAvailableFolders,
  getOutlookFolderSettings,
  updateOutlookFolderSettings,
  getOutlookMoveSettings,
  updateOutlookMoveSettings,
  getReceiptSettings,
  updateReceiptSettings,
} from '../api/index.js'

function OutlookSection() {
  const [connected, setConnected] = useState(null)
  const [availableFolders, setAvailableFolders] = useState([])
  const [folderSettings, setFolderSettings] = useState([])
  const [moveEnabled, setMoveEnabled] = useState(false)
  const [moveDestinationFolderId, setMoveDestinationFolderId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setLoading(true)
    getOutlookStatus()
      .then(({ connected: isConnected }) => {
        setConnected(isConnected)
        if (!isConnected) return
        return Promise.all([
          getOutlookAvailableFolders(),
          getOutlookFolderSettings(),
          getOutlookMoveSettings(),
        ]).then(([available, configured, moveSettings]) => {
          setAvailableFolders(available.map(f => ({ value: f.id, label: f.displayPath })))
          setFolderSettings(configured)
          setMoveEnabled(moveSettings.enabled)
          setMoveDestinationFolderId(moveSettings.folderId || null)
        })
      })
      .catch(err => notifications.show({ title: 'Failed to load Outlook settings', message: err.message, color: 'red' }))
      .finally(() => setLoading(false))
  }, [])

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

  const handleSave = async () => {
    setSaving(true)
    try {
      await Promise.all([
        updateOutlookFolderSettings(folderSettings),
        updateOutlookMoveSettings(moveEnabled, moveDestinationFolderId),
      ])
      notifications.show({ title: 'Outlook settings saved', color: 'green' })
    } catch (err) {
      notifications.show({ title: 'Failed to save settings', message: err.message, color: 'red' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card withBorder p="lg">
      <Group justify="space-between" mb="md">
        <Text fw={600}>Outlook Integration</Text>
        {connected !== null && (
          <Badge color={connected ? 'green' : 'gray'} variant="light">
            {connected ? 'Connected' : 'Not connected'}
          </Badge>
        )}
      </Group>

      {loading ? (
        <Center py="xl"><Loader size="sm" /></Center>
      ) : !connected ? (
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            Connect your Outlook account to import rental expense emails and enable auto-move after saving.
          </Text>
          <Anchor href="/api/outlook/connect" underline="never">
            <Button size="sm">Connect Outlook</Button>
          </Anchor>
        </Stack>
      ) : (
        <Stack gap="md">
          <div>
            <Text size="sm" fw={500} mb={4}>Watched folders</Text>
            <Text size="xs" c="dimmed" mb="xs">
              Select which Outlook folders to include when fetching rental emails. Leave empty to use the defaults (Inbox, Rent Expenses, Taxes).
            </Text>
            <MultiSelect
              data={availableFolders}
              value={selectedFolderIds}
              onChange={handleFolderSelectionChange}
              placeholder="Select folders…"
              searchable
              clearable
            />
          </div>

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

          <Stack gap="xs">
            <Text size="sm" fw={500}>Auto-move</Text>
            <Switch
              label="Move email to a folder after saving as an expense or income"
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
          </Stack>

          <Group justify="flex-end">
            <Button
              loading={saving}
              disabled={moveEnabled && !moveDestinationFolderId}
              onClick={handleSave}
            >
              Save Outlook Settings
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  )
}

function ReceiptsSection() {
  const queryClient = useQueryClient()
  const { data: receiptSettings, isLoading } = useQuery({
    queryKey: ['receiptSettings'],
    queryFn: getReceiptSettings,
  })
  const [folderBase, setFolderBase] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (receiptSettings?.folderBase) setFolderBase(receiptSettings.folderBase)
  }, [receiptSettings])

  const handleSave = async () => {
    setSaving(true)
    try {
      await updateReceiptSettings(folderBase)
      queryClient.invalidateQueries({ queryKey: ['receiptSettings'] })
      notifications.show({ title: 'Receipt settings saved', color: 'green' })
    } catch (err) {
      notifications.show({ title: 'Failed to save settings', message: err.message, color: 'red' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card withBorder p="lg">
      <Text fw={600} mb="md">Receipts</Text>
      {isLoading ? (
        <Center py="xl"><Loader size="sm" /></Center>
      ) : (
        <Stack gap="md">
          <div>
            <TextInput
              label="OneDrive folder path"
              description="Root folder in your OneDrive where receipts are stored. Uploads go to the pending/ subfolder and are moved to a year subfolder when the entry is saved."
              placeholder="e.g. Receipts/Rentals"
              value={folderBase}
              onChange={e => setFolderBase(e.target.value)}
            />
          </div>
          <Group justify="flex-end">
            <Button loading={saving} disabled={!folderBase.trim()} onClick={handleSave}>
              Save Receipt Settings
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  )
}

export default function Settings() {
  return (
    <Stack gap="xl">
      <Title order={2}>Settings</Title>
      <OutlookSection />
      <ReceiptsSection />
    </Stack>
  )
}
