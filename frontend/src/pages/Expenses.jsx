import React, { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Stack, Group, Title, Button, Card, TextInput, NumberInput, Select, Table,
  Text, Loader, Center, Badge, ActionIcon, Modal, Tooltip, ThemeIcon, ScrollArea,
  Tabs, FileButton
} from '@mantine/core'
import { useForm } from '@mantine/form'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import {
  IconPencil, IconTrash, IconPlus, IconBrandOffice, IconPencilMinus, IconX,
  IconReceipt, IconUpload
} from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getExpenses, createExpense, updateExpense, deleteExpense,
  getExpenseCategories, getProperties, getPayers, createPayer, uploadReceipt
} from '../api/index.js'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import { fmtCurrency } from '../utils/formatters.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

const EMPTY_FORM = {
  amount: '',
  description: '',
  date: new Date().toISOString().split('T')[0],
  category: 'OTHER',
  propertyId: null,
  payerId: null,
  sourceType: null,
  sourceId: null,
}
const EMPTY_PAYER_FORM = { name: '', type: 'COMPANY', aliases: [], accounts: [] }
const CATEGORY_COLORS = { REPAIRS: 'red', UTILITIES: 'blue', INSURANCE: 'violet', TAXES: 'pink', MORTGAGE_INTEREST: 'teal', DEPRECIATION: 'orange' }

export default function Expenses() {
  const queryClient = useQueryClient()
  const { data: expenses = [], isLoading } = useQuery({ queryKey: ['expenses'], queryFn: getExpenses })
  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: getExpenseCategories })
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({ queryKey: ['properties'], queryFn: getProperties })
  const { data: payers = [], isFetched: payersFetched } = useQuery({ queryKey: ['payers'], queryFn: getPayers })

  const form = useForm({ initialValues: EMPTY_FORM })
  const payerForm = useForm({ initialValues: EMPTY_PAYER_FORM })

  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [pendingPrefill, setPendingPrefill] = useState(null)
  const [payerModalOpen, setPayerModalOpen] = useState(false)
  const [payerAccountInput, setPayerAccountInput] = useState('')
  const [uploadedReceipt, setUploadedReceipt] = useState(null)
  const [receiptUploading, setReceiptUploading] = useState(false)
  const [highlightId, setHighlightId] = useState(null)
  const [filterPayerId, setFilterPayerId] = useState(null)
  const [activeTab, setActiveTab] = useState('expenses')
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const location = useLocation()

  usePendingSSE({
    filter: (d) => d.emailType !== 'INCOME' && d.sourceType !== 'RECEIPT',
    activeTab,
    notification: { title: 'Email parsed', message: 'A new expense is ready to review in Pending', color: 'green' },
    onUpdate: () => setPendingRefreshKey(k => k + 1),
  })

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
    if (!pendingPrefill || !payersFetched || !propertiesFetched) return
    const matchedPayer = pendingPrefill.payerName
      ? payers.find(p => p.name.toLowerCase() === pendingPrefill.payerName.toLowerCase()) ?? null
      : null
    const suggestedPropLower = pendingPrefill.propertyName?.trim().toLowerCase()
    const matchedProperty = suggestedPropLower
      ? (properties.find(p => p.name.toLowerCase() === suggestedPropLower) ??
         properties.find(p => p.address?.toLowerCase().includes(suggestedPropLower) || suggestedPropLower.includes(p.address?.toLowerCase() ?? '')) ??
         null)
      : null
    form.setValues({
      amount: pendingPrefill.amount ?? '',
      description: pendingPrefill.description ?? '',
      date: pendingPrefill.date ?? new Date().toISOString().split('T')[0],
      category: pendingPrefill.category ?? 'OTHER',
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
      payerId: matchedPayer ? String(matchedPayer.id) : null,
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
    })
    if (pendingPrefill.payerName && !matchedPayer) {
      payerForm.setValues({ name: pendingPrefill.payerName, type: 'COMPANY', aliases: [], accounts: pendingPrefill.accountNumbers || [] })
      setPayerModalOpen(true)
    }
    setEditing(null)
    setShowForm(true)
    setPendingPrefill(null)
  }, [pendingPrefill, payersFetched, propertiesFetched, payers, properties])

  const openPayerModal = () => {
    payerForm.reset()
    setPayerModalOpen(true)
  }

  const handlePayerModalSave = async () => {
    const newPayer = await createPayer(payerForm.values)
    queryClient.setQueryData(['payers'], (old = []) => [...old, newPayer])
    form.setFieldValue('payerId', String(newPayer.id))
    setPayerModalOpen(false)
    payerForm.reset()
    setPayerAccountInput('')
  }

  const addPayerAccount = () => {
    const trimmed = payerAccountInput.trim()
    if (!trimmed || payerForm.values.accounts.includes(trimmed)) return
    payerForm.insertListItem('accounts', trimmed)
    setPayerAccountInput('')
  }

  const handleReceiptUpload = async (file) => {
    if (!file) return
    setReceiptUploading(true)
    setSaveError(null)
    try {
      const dot = file.name.lastIndexOf('.')
      const base = dot >= 0 ? file.name.slice(0, dot) : file.name
      const ext = dot >= 0 ? file.name.slice(dot) : ''
      const date = form.values.date || new Date().toISOString().split('T')[0]
      const renamedFile = new File([file], `${base}_${date}${ext}`, { type: file.type })
      const result = await uploadReceipt(renamedFile)
      setUploadedReceipt({ itemId: result.receipt.id, fileName: result.receipt.name })
    } catch (err) {
      setSaveError(`Receipt upload failed: ${err.message}`)
    } finally {
      setReceiptUploading(false)
    }
  }

  const handleSubmit = async (values) => {
    setSaveError(null)
    const data = {
      amount: parseFloat(values.amount),
      description: values.description,
      date: values.date,
      category: values.category,
      property: values.propertyId ? { id: Number(values.propertyId) } : null,
      payer: values.payerId ? { id: Number(values.payerId) } : null,
      sourceType: values.sourceType,
      sourceId: values.sourceId,
      ...(uploadedReceipt ? {
        receiptOneDriveId: uploadedReceipt.itemId,
        receiptFileName: uploadedReceipt.fileName,
        sourceType: 'RECEIPT',
      } : {}),
    }
    try {
      if (editing) await updateExpense(editing, data)
      else await createExpense(data)
      form.reset()
      form.setFieldValue('date', new Date().toISOString().split('T')[0])
      setEditing(null)
      setShowForm(false)
      setUploadedReceipt(null)
      queryClient.invalidateQueries({ queryKey: ['expenses'] })
      queryClient.invalidateQueries({ queryKey: ['totalExpenses'] })
    } catch (err) {
      setSaveError(err.message || 'Save failed')
    }
  }

  const handleEdit = (expense) => {
    form.setValues({
      amount: expense.amount,
      description: expense.description,
      date: expense.date,
      category: expense.category,
      propertyId: expense.property?.id ? String(expense.property.id) : null,
      payerId: expense.payer?.id ? String(expense.payer.id) : null,
      sourceType: expense.sourceType,
      sourceId: expense.sourceId,
    })
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
        queryClient.invalidateQueries({ queryKey: ['expenses'] })
        queryClient.invalidateQueries({ queryKey: ['totalExpenses'] })
      },
    })
  }

  const cancelForm = () => {
    setShowForm(false)
    setEditing(null)
    setSaveError(null)
    setUploadedReceipt(null)
    form.reset()
  }

  const handlePendingSaved = (expense) => {
    queryClient.invalidateQueries({ queryKey: ['expenses'] })
    queryClient.invalidateQueries({ queryKey: ['totalExpenses'] })
    setHighlightId(expense.id)
    setActiveTab('expenses')
    setTimeout(() => setHighlightId(null), 3000)
  }

  const payerOptions = useMemo(() =>
    expenses
      .filter(e => e.payer)
      .reduce((acc, e) => {
        if (!acc.some(o => o.value === String(e.payer.id))) {
          acc.push({ value: String(e.payer.id), label: e.payer.name })
        }
        return acc
      }, [])
      .sort((a, b) => a.label.localeCompare(b.label)),
    [expenses]
  )

  const visibleExpenses = useMemo(() =>
    filterPayerId
      ? expenses.filter(e => e.payer && String(e.payer.id) === filterPayerId)
      : expenses,
    [expenses, filterPayerId]
  )

  if (isLoading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Expenses</Title>
        <Button onClick={() => {
          form.reset()
          form.setFieldValue('date', new Date().toISOString().split('T')[0])
          setEditing(null)
          setShowForm(true)
          setUploadedReceipt(null)
          setActiveTab('expenses')
        }}>+ Add Expense</Button>
      </Group>

      {/* New Payer Modal */}
      <Modal opened={payerModalOpen} onClose={() => { setPayerModalOpen(false); setPayerAccountInput('') }} title="New Payer" size="sm">
        <Stack gap="sm">
          <TextInput
            label="Name"
            {...payerForm.getInputProps('name')}
            required
            autoFocus
          />
          <Select
            label="Type"
            {...payerForm.getInputProps('type')}
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
          {payerForm.values.accounts.length > 0 && (
            <Group gap={4} wrap="wrap">
              {payerForm.values.accounts.map((a, i) => (
                <Badge key={a} variant="outline" color="cyan" rightSection={
                  <ActionIcon size="xs" variant="transparent" onClick={() => payerForm.removeListItem('accounts', i)}>
                    <IconX size={10} />
                  </ActionIcon>
                }>{a}</Badge>
              ))}
            </Group>
          )}
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={() => { setPayerModalOpen(false); setPayerAccountInput('') }}>Cancel</Button>
            <Button disabled={!payerForm.values.name.trim()} onClick={handlePayerModalSave}>Create &amp; Select</Button>
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
        </Tabs.List>

        <Tabs.Panel value="expenses" pt="md">
          {showForm && (
            <Card withBorder p="lg" mb="md">
              <Title order={4} mb="md">{editing ? 'Edit Expense' : 'New Expense'}</Title>
              <form onSubmit={form.onSubmit(handleSubmit)}>
                <Stack gap="sm">
                  <Group grow>
                    <NumberInput label="Amount" {...form.getInputProps('amount')} min={0} decimalScale={2} prefix="$" required />
                    <TextInput label="Description" {...form.getInputProps('description')} required />
                  </Group>
                  <Group grow>
                    <TextInput label="Date" type="date" {...form.getInputProps('date')} required />
                    <Group gap="xs" align="flex-end" wrap="nowrap">
                      <Select
                        label="Payer"
                        style={{ flex: 1 }}
                        {...form.getInputProps('payerId')}
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
                      {...form.getInputProps('propertyId')}
                      data={properties.map(p => ({ value: String(p.id), label: p.name }))}
                      clearable
                      placeholder="— None —"
                    />
                    <Select
                      label="Category (Schedule E)"
                      {...form.getInputProps('category')}
                      data={categories.map(c => ({ value: c.value, label: `Line ${c.scheduleELine} — ${c.label}` }))}
                    />
                  </Group>
                  {!editing && (
                    <Group align="center">
                      {uploadedReceipt ? (
                        <Badge
                          variant="outline"
                          color="green"
                          leftSection={<IconReceipt size={12} />}
                          rightSection={
                            <ActionIcon size="xs" variant="transparent" onClick={() => setUploadedReceipt(null)}>
                              <IconX size={10} />
                            </ActionIcon>
                          }
                        >
                          {uploadedReceipt.fileName}
                        </Badge>
                      ) : (
                        <FileButton onChange={handleReceiptUpload} accept="application/pdf">
                          {(props) => (
                            <Button
                              {...props}
                              variant="default"
                              size="xs"
                              leftSection={<IconUpload size={14} />}
                              loading={receiptUploading}
                            >
                              Attach Receipt
                            </Button>
                          )}
                        </FileButton>
                      )}
                    </Group>
                  )}
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
            {payerOptions.length > 0 && (
              <Group p="sm" pb={0}>
                <Select
                  placeholder="All payers"
                  value={filterPayerId}
                  onChange={setFilterPayerId}
                  data={payerOptions}
                  clearable
                  size="xs"
                  style={{ width: 220 }}
                />
              </Group>
            )}
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
                  {visibleExpenses.length === 0 ? (
                    <Table.Tr><Table.Td colSpan={7}><Text ta="center" c="dimmed" py="xl">{filterPayerId ? 'No expenses for this payer' : 'No expense records yet'}</Text></Table.Td></Table.Tr>
                  ) : visibleExpenses.map(e => (
                    <Table.Tr key={e.id} style={{ background: highlightId === e.id ? 'var(--mantine-color-yellow-0)' : undefined, transition: 'background 0.5s' }}>
                      <Table.Td>{e.date}</Table.Td>
                      <Table.Td c="dimmed">{e.property?.name || '—'}</Table.Td>
                      <Table.Td>
                        <Stack gap={2}>
                          {e.payer ? <Text size="sm" fw={500}>{e.payer.name}</Text> : null}
                          <Text size="xs" c="dimmed">{e.description}</Text>
                        </Stack>
                      </Table.Td>
                      <Table.Td fw={600} c="red">-{fmtCurrency(e.amount)}</Table.Td>
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
      </Tabs>
    </Stack>
  )
}
