import React, { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Stack, Group, Title, Button, Card, TextInput, NumberInput, Select, Table,
  Text, Loader, Center, Badge, ActionIcon, Modal, Tooltip, ThemeIcon, ScrollArea,
  Tabs, FileInput, Alert, Anchor, Paper, Divider
} from '@mantine/core'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import {
  IconPencil, IconTrash, IconPlus, IconBrandOffice, IconPencilMinus, IconX,
  IconUpload, IconFileTypePdf, IconExternalLink, IconSettings, IconAlertTriangle,
  IconReceipt, IconCheck
} from '@tabler/icons-react'
import {
  getExpenses, createExpense, updateExpense, deleteExpense,
  getExpenseCategories, getProperties, getPayers, createPayer,
  listReceipts, uploadReceipt, parseReceipt, deleteReceipt, getReceiptSettings, updateReceiptSettings
} from '../api/index.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], category: 'OTHER', property: null, sourceType: null, sourceId: null, payer: null }
const EMPTY_PAYER_FORM = { name: '', type: 'COMPANY', aliases: [], accounts: [] }

const CATEGORY_COLORS = { REPAIRS: 'red', UTILITIES: 'blue', INSURANCE: 'violet', TAXES: 'pink', MORTGAGE_INTEREST: 'teal', DEPRECIATION: 'orange' }

export default function Expenses() {
  const [expenses, setExpenses] = useState([])
  const [categories, setCategories] = useState([])
  const [properties, setProperties] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [payers, setPayers] = useState([])
  const [payersLoaded, setPayersLoaded] = useState(false)
  const [propertiesLoaded, setPropertiesLoaded] = useState(false)
  const [pendingPrefill, setPendingPrefill] = useState(null)
  const [payerModalOpen, setPayerModalOpen] = useState(false)
  const [payerForm, setPayerForm] = useState(EMPTY_PAYER_FORM)
  const [payerAccountInput, setPayerAccountInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [saveError, setSaveError] = useState(null)
  const [highlightId, setHighlightId] = useState(null)
  const [activeTab, setActiveTab] = useState('expenses')
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const [receipts, setReceipts] = useState([])
  const [receiptsLoading, setReceiptsLoading] = useState(false)
  const [receiptFile, setReceiptFile] = useState(null)
  const [uploadingReceipt, setUploadingReceipt] = useState(false)
  const [uploadResult, setUploadResult] = useState(null)
  const [receiptsError, setReceiptsError] = useState(null)
  const [folderBase, setFolderBase] = useState('')
  const [folderSettingsOpen, setFolderSettingsOpen] = useState(false)
  const [folderBaseInput, setFolderBaseInput] = useState('')
  const [parsingReceiptId, setParsingReceiptId] = useState(null)
  // year picker removed — year is derived from the expense date when the expense is saved
  const activeTabRef = useRef(activeTab)
  const location = useLocation()

  useEffect(() => { activeTabRef.current = activeTab }, [activeTab])

  useEffect(() => {
    const es = new EventSource('/api/pending-expenses/events')
    es.addEventListener('pending-updated', (e) => {
      const data = JSON.parse(e.data)
      if (data.emailType === 'INCOME') return
      setPendingRefreshKey(k => k + 1)
      if (activeTabRef.current !== 'pending' && data.status === 'READY') {
        notifications.show({
          title: 'Email parsed',
          message: 'A new expense is ready to review in Pending',
          color: 'green',
          autoClose: 6000,
        })
      }
    })
    return () => es.close()
  }, [])

  const load = () => getExpenses().then(setExpenses).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getExpenseCategories().then(setCategories)
    getProperties().then(data => { setProperties(data); setPropertiesLoaded(true) })
    getPayers().then(data => { setPayers(data); setPayersLoaded(true) })
  }, [])

  const loadReceipts = () => {
    setReceiptsLoading(true)
    setReceiptsError(null)
    Promise.all([listReceipts(), getReceiptSettings()])
      .then(([data, settings]) => {
        setReceipts(data)
        setFolderBase(settings.folderBase)
        setFolderBaseInput(settings.folderBase)
      })
      .catch(err => setReceiptsError(err.message))
      .finally(() => setReceiptsLoading(false))
  }

  useEffect(() => {
    if (activeTab === 'receipts') {
      loadReceipts()
    }
  }, [activeTab])

  useEffect(() => {
    const { prefill, highlightId: hid } = location.state || {}
    if (hid) {
      setHighlightId(hid)
      window.history.replaceState({}, '')
      setTimeout(() => setHighlightId(null), 3000)
    }
    if (prefill) {
      setPendingPrefill(prefill)
      window.history.replaceState({}, '')
    }
  }, [location.state])

  useEffect(() => {
    if (!pendingPrefill || !payersLoaded || !propertiesLoaded) return
    const matchedPayer = pendingPrefill.payerName
      ? payers.find(p => p.name.toLowerCase() === pendingPrefill.payerName.toLowerCase()) ?? null
      : null
    const suggestedPropLower = pendingPrefill.propertyName?.trim().toLowerCase()
    const matchedProperty = suggestedPropLower
      ? (properties.find(p => p.name.toLowerCase() === suggestedPropLower) ??
         properties.find(p => p.address?.toLowerCase().includes(suggestedPropLower) || suggestedPropLower.includes(p.address?.toLowerCase() ?? '')) ??
         null)
      : null
    setForm({
      amount: pendingPrefill.amount ?? '',
      description: pendingPrefill.description ?? '',
      date: pendingPrefill.date ?? new Date().toISOString().split('T')[0],
      category: pendingPrefill.category ?? 'OTHER',
      property: matchedProperty ? { id: matchedProperty.id } : null,
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
      payer: matchedPayer ? { id: matchedPayer.id } : null,
    })
    if (pendingPrefill.payerName && !matchedPayer) {
      setPayerForm({ name: pendingPrefill.payerName, type: 'COMPANY', aliases: [], accounts: pendingPrefill.accountNumbers || [] })
      setPayerModalOpen(true)
    }
    setEditing(null)
    setShowForm(true)
    setPendingPrefill(null)
  }, [pendingPrefill, payersLoaded, propertiesLoaded])

  const openPayerModal = () => {
    setPayerForm(EMPTY_PAYER_FORM)
    setPayerModalOpen(true)
  }

  const handlePayerModalSave = async () => {
    const newPayer = await createPayer(payerForm)
    setPayers(prev => [...prev, newPayer])
    setForm(f => ({ ...f, payer: { id: newPayer.id } }))
    setPayerModalOpen(false)
    setPayerForm(EMPTY_PAYER_FORM)
    setPayerAccountInput('')
  }

  const addPayerAccount = () => {
    const trimmed = payerAccountInput.trim()
    if (!trimmed || payerForm.accounts.includes(trimmed)) return
    setPayerForm(f => ({ ...f, accounts: [...f.accounts, trimmed] }))
    setPayerAccountInput('')
  }

  const removePayerAccount = (account) =>
    setPayerForm(f => ({ ...f, accounts: f.accounts.filter(a => a !== account) }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaveError(null)
    const amount = parseFloat(form.amount)
    if (!form.amount || isNaN(amount)) { setSaveError('Amount is required'); return }
    if (!form.description?.trim()) { setSaveError('Description is required'); return }
    const data = { ...form, amount }
    try {
      if (editing) await updateExpense(editing, data)
      else await createExpense(data)
      setForm(EMPTY_FORM)
      setEditing(null)
      setShowForm(false)
      load()
    } catch (err) {
      setSaveError(err.message || 'Save failed')
    }
  }

  const handleEdit = (expense) => {
    setForm({ ...expense })
    setEditing(expense.id)
    setShowForm(true)
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete expense',
      children: <Text size="sm">This expense will be permanently deleted. This action cannot be undone.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        await deleteExpense(id)
        load()
      },
    })
  }

  const cancelForm = () => {
    setShowForm(false)
    setEditing(null)
    setSaveError(null)
  }

  const handlePendingSaved = (expense) => {
    load()
    setHighlightId(expense.id)
    setActiveTab('expenses')
    setTimeout(() => setHighlightId(null), 3000)
  }

  const handleReceiptUpload = async () => {
    if (!receiptFile) return
    setUploadingReceipt(true)
    setUploadResult(null)
    try {
      const result = await uploadReceipt(receiptFile)
      setUploadResult(result)
      setReceiptFile(null)
      loadReceipts()
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
        message: 'Parsing receipt — check Pending tab when ready',
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

  const handleDeleteReceipt = (itemId, name, hasExpense) => {
    modals.openConfirmModal({
      title: 'Delete receipt',
      children: (
        <Text size="sm">
          {hasExpense
            ? `"${name}" and its linked expense will be permanently deleted. This cannot be undone.`
            : `"${name}" will be permanently deleted from OneDrive. This cannot be undone.`}
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deleteReceipt(itemId)
          loadReceipts()
          load()
        } catch (err) {
          notifications.show({ title: 'Delete failed', message: err.message, color: 'red' })
        }
      },
    })
  }

  const handleSaveFolderSettings = async () => {
    try {
      await updateReceiptSettings(folderBaseInput)
      setFolderBase(folderBaseInput)
      setFolderSettingsOpen(false)
      loadReceipts()
    } catch (err) {
      notifications.show({ title: 'Save failed', message: err.message, color: 'red' })
    }
  }

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Expenses</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true); setActiveTab('expenses') }}>+ Add Expense</Button>
      </Group>

      {/* New Payer Modal */}
      <Modal opened={payerModalOpen} onClose={() => { setPayerModalOpen(false); setPayerAccountInput('') }} title="New Payer" size="sm">
        <Stack gap="sm">
          <TextInput
            label="Name"
            value={payerForm.name}
            onChange={e => setPayerForm(f => ({ ...f, name: e.target.value }))}
            required
            autoFocus
          />
          <Select
            label="Type"
            value={payerForm.type}
            onChange={val => setPayerForm(f => ({ ...f, type: val }))}
            data={[{ value: 'COMPANY', label: 'Company' }, { value: 'PERSON', label: 'Person' }]}
          />
          <Group align="flex-end">
            <TextInput
              label="Account Numbers"
              description="Used to auto-identify this payer from future emails"
              placeholder="Add account number"
              value={payerAccountInput}
              onChange={e => setPayerAccountInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addPayerAccount() } }}
              style={{ flex: 1 }}
            />
            <Button variant="default" onClick={addPayerAccount}>Add</Button>
          </Group>
          {payerForm.accounts.length > 0 && (
            <Group gap={4} wrap="wrap">
              {payerForm.accounts.map(a => (
                <Badge key={a} variant="outline" color="cyan" rightSection={
                  <ActionIcon size="xs" variant="transparent" onClick={() => removePayerAccount(a)}>
                    <IconX size={10} />
                  </ActionIcon>
                }>{a}</Badge>
              ))}
            </Group>
          )}
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={() => { setPayerModalOpen(false); setPayerAccountInput('') }}>Cancel</Button>
            <Button disabled={!payerForm.name.trim()} onClick={handlePayerModalSave}>Create &amp; Select</Button>
          </Group>
        </Stack>
      </Modal>

      <Tabs value={activeTab} onChange={setActiveTab}>
        <Tabs.List>
          <Tabs.Tab value="expenses">Expenses</Tabs.Tab>
          <Tabs.Tab
            value="pending"
            rightSection={pendingCount > 0
              ? <Badge color="orange" size="xs" circle>{pendingCount}</Badge>
              : null}
          >
            Pending
          </Tabs.Tab>
          <Tabs.Tab value="receipts" leftSection={<IconReceipt size={14} />}>Receipts</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="expenses" pt="md">
          {showForm && (
            <Card withBorder p="lg" mb="md">
              <Title order={4} mb="md">{editing ? 'Edit Expense' : 'New Expense'}</Title>
              <form onSubmit={handleSubmit}>
                <Stack gap="sm">
                  <Group grow>
                    <NumberInput label="Amount" value={form.amount} onChange={val => setForm(f => ({ ...f, amount: val }))} min={0} decimalScale={2} prefix="$" required />
                    <TextInput label="Description" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} required />
                  </Group>
                  <Group grow>
                    <TextInput label="Date" type="date" value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} required />
                    <Group gap="xs" align="flex-end" wrap="nowrap">
                      <Select
                        label="Payer"
                        style={{ flex: 1 }}
                        value={form.payer?.id ? String(form.payer.id) : null}
                        onChange={val => setForm(f => ({ ...f, payer: val ? { id: Number(val) } : null }))}
                        data={payers.map(p => ({ value: String(p.id), label: `${p.name} (${p.type === 'COMPANY' ? 'Company' : 'Person'})` }))}
                        clearable
                        placeholder="— None —"
                      />
                      <ActionIcon variant="default" size="lg" title="Add new payer" onClick={openPayerModal}>
                        <IconPlus size={16} />
                      </ActionIcon>
                    </Group>
                  </Group>
                  <Group grow>
                    <Select
                      label="Property"
                      value={form.property?.id ? String(form.property.id) : null}
                      onChange={val => setForm(f => ({ ...f, property: val ? { id: Number(val) } : null }))}
                      data={properties.map(p => ({ value: String(p.id), label: p.name }))}
                      clearable
                      placeholder="— None —"
                    />
                    <Select
                      label="Category (Schedule E)"
                      value={form.category}
                      onChange={val => setForm(f => ({ ...f, category: val }))}
                      data={categories.map(c => ({ value: c.value, label: `Line ${c.scheduleELine} — ${c.label}` }))}
                    />
                  </Group>
                  {saveError && <Text c="red" size="sm">{saveError}</Text>}
                  <Group>
                    <Button type="submit">Save</Button>
                    <Button variant="default" onClick={cancelForm}>Cancel</Button>
                  </Group>
                </Stack>
              </form>
            </Card>
          )}

          <Card withBorder p={0}>
            <ScrollArea>
              <Table striped highlightOnHover miw={900}>
                <Table.Thead>
                  <Table.Tr>
                    {['Date', 'Property', 'Payer / Description', 'Amount', 'Category', 'Source', 'Actions'].map(h => (
                      <Table.Th key={h} style={h === 'Source' ? { textAlign: 'center' } : undefined}>{h}</Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {expenses.length === 0 ? (
                    <Table.Tr><Table.Td colSpan={7}><Text ta="center" c="dimmed" py="xl">No expense records yet</Text></Table.Td></Table.Tr>
                  ) : expenses.map(e => (
                    <Table.Tr key={e.id} style={{ background: highlightId === e.id ? 'var(--mantine-color-yellow-0)' : undefined, transition: 'background 0.5s' }}>
                      <Table.Td>{e.date}</Table.Td>
                      <Table.Td c="dimmed">{e.property?.name || '—'}</Table.Td>
                      <Table.Td>
                        <Stack gap={2}>
                          {e.payer ? <Text size="sm" fw={500}>{e.payer.name}</Text> : null}
                          <Text size="xs" c="dimmed">{e.description}</Text>
                        </Stack>
                      </Table.Td>
                      <Table.Td fw={600} c="red">-${Number(e.amount).toFixed(2)}</Table.Td>
                      <Table.Td>
                        <Badge color={CATEGORY_COLORS[e.category] || 'gray'} variant="light" size="sm">
                          {categories.find(c => c.value === e.category)?.label || e.category}
                        </Badge>
                      </Table.Td>
                      <Table.Td style={{ textAlign: 'center' }}>
                        {e.sourceType === 'OUTLOOK_EMAIL'
                          ? <Tooltip label="Outlook Email"><ThemeIcon variant="subtle" color="blue" size="md"><IconBrandOffice size={18} /></ThemeIcon></Tooltip>
                          : e.sourceType === 'MANUAL'
                          ? <Tooltip label="Manual"><ThemeIcon variant="subtle" color="gray" size="md"><IconPencilMinus size={18} /></ThemeIcon></Tooltip>
                          : e.sourceType === 'RECEIPT'
                          ? <Tooltip label={e.receiptFileName ? `Receipt: ${e.receiptFileName}` : 'Receipt'}><ThemeIcon variant="subtle" color="red" size="md"><IconReceipt size={18} /></ThemeIcon></Tooltip>
                          : <Text c="dimmed">—</Text>}
                      </Table.Td>
                      <Table.Td>
                        <Group gap="xs">
                          <ActionIcon variant="subtle" color="gray" onClick={() => handleEdit(e)}><IconPencil size={16} /></ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(e.id)}><IconTrash size={16} /></ActionIcon>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </ScrollArea>
          </Card>
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md" keepMounted>
          <PendingExpenses
            onSaved={handlePendingSaved}
            onCountChange={setPendingCount}
            refreshKey={pendingRefreshKey}
            filterType="EXPENSE"
          />
        </Tabs.Panel>

        <Tabs.Panel value="receipts" pt="md">
          {/* Folder settings modal */}
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

          {/* Upload section */}
          <Card withBorder p="lg" mb="md">
            <Group justify="space-between" mb="md">
              <Title order={4}>Upload Receipt</Title>
              <Tooltip label="Configure OneDrive folder">
                <ActionIcon variant="subtle" color="gray" onClick={() => setFolderSettingsOpen(true)}>
                  <IconSettings size={16} />
                </ActionIcon>
              </Tooltip>
            </Group>
            {folderBase && (
              <Text size="xs" c="dimmed" mb="sm">
                Uploads to: <strong>{folderBase}/pending/</strong> — moved to the correct year folder when the expense is saved
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
                  ? <>This file was already uploaded. {uploadResult.receipt.expenseId
                      ? <span>It is already linked to an expense.</span>
                      : <span>No expense linked yet — click "Create Expense" below.</span>}
                    </>
                  : <>Receipt saved to OneDrive.</>}
              </Alert>
            )}
          </Card>

          {/* Receipts list */}
          <Card withBorder p={0}>
            {receiptsLoading ? (
              <Center py="xl"><Loader /></Center>
            ) : receiptsError ? (
              <Text ta="center" c="red" py="xl">{receiptsError}</Text>
            ) : receipts.length === 0 ? (
              <Text ta="center" c="dimmed" py="xl">
                No receipts found in OneDrive. Upload a PDF to get started.
              </Text>
            ) : (
              <ScrollArea>
                <Table striped highlightOnHover miw={700}>
                  <Table.Thead>
                    <Table.Tr>
                      {['File', 'Status', 'Uploaded', 'Expense', 'Actions'].map(h => (
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
                        const matchedExpense = r.expenseId ? expenses.find(e => e.id === r.expenseId) : null
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
                                {r.uploadedAt ? new Date(r.uploadedAt).toLocaleDateString() : '—'}
                              </Text>
                            </Table.Td>
                            <Table.Td>
                              {matchedExpense ? (
                                <Stack gap={2}>
                                  <Text size="sm" fw={500}>{matchedExpense.description}</Text>
                                  <Text size="xs" c="dimmed">${Number(matchedExpense.amount).toFixed(2)} · {matchedExpense.date}</Text>
                                </Stack>
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
                                {!r.expenseId && (
                                  <Tooltip label="Parse receipt and create expense">
                                    <Button
                                      size="xs"
                                      variant="light"
                                      loading={parsingReceiptId === r.id}
                                      onClick={() => handleParseReceipt(r.id)}
                                    >
                                      Create Expense
                                    </Button>
                                  </Tooltip>
                                )}
                                <Tooltip label={r.expenseId ? 'Delete receipt and linked expense' : 'Delete receipt'}>
                                  <ActionIcon
                                    variant="subtle"
                                    color="red"
                                    onClick={() => handleDeleteReceipt(r.id, r.name, !!r.expenseId)}
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
      </Tabs>
    </Stack>
  )
}