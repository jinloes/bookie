import React, { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Stack, Group, Title, Button, Card, TextInput, NumberInput, Select, Table,
  Text, Loader, Center, Badge, ActionIcon, Modal
} from '@mantine/core'
import { IconPencil, IconTrash, IconPlus } from '@tabler/icons-react'
import { getExpenses, createExpense, updateExpense, deleteExpense, getExpenseCategories, getProperties, getPayers, createPayer } from '../api/index.js'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], category: 'OTHER', propertyName: '', sourceType: null, sourceId: null, payer: null }
const EMPTY_PAYER_FORM = { name: '', type: 'COMPANY' }

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
  const [loading, setLoading] = useState(true)
  const [saveError, setSaveError] = useState(null)
  const [highlightId, setHighlightId] = useState(null)
  const location = useLocation()

  const load = () => getExpenses().then(setExpenses).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getExpenseCategories().then(setCategories)
    getProperties().then(data => { setProperties(data); setPropertiesLoaded(true) })
    getPayers().then(data => { setPayers(data); setPayersLoaded(true) })
  }, [])

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
      propertyName: matchedProperty ? matchedProperty.name : '',
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
      payer: matchedPayer ? { id: matchedPayer.id } : null,
    })
    if (pendingPrefill.payerName && !matchedPayer) {
      setPayerForm({ name: pendingPrefill.payerName, type: 'COMPANY' })
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
  }

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

  const handleDelete = async (id) => {
    if (confirm('Delete this expense?')) { await deleteExpense(id); load() }
  }

  const cancelForm = () => {
    setShowForm(false)
    setEditing(null)
    setSaveError(null)
  }

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Expenses</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Expense</Button>
      </Group>

      {/* New Payer Modal */}
      <Modal opened={payerModalOpen} onClose={() => setPayerModalOpen(false)} title="New Payer" size="sm">
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
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={() => setPayerModalOpen(false)}>Cancel</Button>
            <Button disabled={!payerForm.name.trim()} onClick={handlePayerModalSave}>Create &amp; Select</Button>
          </Group>
        </Stack>
      </Modal>

      {showForm && (
        <Card withBorder p="lg">
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
                  value={form.propertyName || null}
                  onChange={val => setForm(f => ({ ...f, propertyName: val || '' }))}
                  data={properties.map(p => ({ value: p.name, label: p.name }))}
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
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              {['Date', 'Payer', 'Description', 'Category', 'Property', 'Source', 'Amount', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {expenses.length === 0 ? (
              <Table.Tr><Table.Td colSpan={8}><Text ta="center" c="dimmed" py="xl">No expense records yet</Text></Table.Td></Table.Tr>
            ) : expenses.map(e => (
              <Table.Tr key={e.id} style={{ background: highlightId === e.id ? 'var(--mantine-color-yellow-0)' : undefined, transition: 'background 0.5s' }}>
                <Table.Td>{e.date}</Table.Td>
                <Table.Td>
                  {e.payer ? (
                    <Group gap={4}>
                      <Text size="sm">{e.payer.name}</Text>
                      <Text size="xs" c="dimmed">({e.payer.type === 'COMPANY' ? 'Co.' : 'Person'})</Text>
                    </Group>
                  ) : <Text c="dimmed">—</Text>}
                </Table.Td>
                <Table.Td>{e.description}</Table.Td>
                <Table.Td>
                  <Badge color={CATEGORY_COLORS[e.category] || 'gray'} variant="light" size="sm">
                    {categories.find(c => c.value === e.category)?.label || e.category}
                  </Badge>
                </Table.Td>
                <Table.Td c="dimmed">{e.propertyName || '—'}</Table.Td>
                <Table.Td>
                  {e.sourceType === 'OUTLOOK_EMAIL'
                    ? <Badge color="blue" variant="light" size="sm">Outlook</Badge>
                    : <Text c="dimmed">—</Text>}
                </Table.Td>
                <Table.Td fw={600} c="red">-${Number(e.amount).toFixed(2)}</Table.Td>
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
      </Card>
    </Stack>
  )
}