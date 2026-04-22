import React, { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Stack, Group, Title, Button, Card, TextInput, NumberInput, Select, Table,
  Text, Loader, Center, ActionIcon, Badge, Tabs, ScrollArea
} from '@mantine/core'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import { IconPencil, IconTrash } from '@tabler/icons-react'
import { getIncomes, createIncome, updateIncome, deleteIncome, getProperties } from '../api/index.js'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import { fmtCurrency } from '../utils/formatters.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], source: '', property: null }

export default function Incomes() {
  const [incomes, setIncomes] = useState([])
  const [properties, setProperties] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saveError, setSaveError] = useState(null)
  const [propertiesLoaded, setPropertiesLoaded] = useState(false)
  const [pendingPrefill, setPendingPrefill] = useState(null)
  const [highlightId, setHighlightId] = useState(null)
  const [activeTab, setActiveTab] = useState('income')
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const location = useLocation()

  usePendingSSE({
    filter: (d) => d.emailType === 'INCOME',
    activeTab,
    notification: { title: 'Email parsed', message: 'A new income is ready to review in Pending', color: 'teal' },
    onUpdate: () => setPendingRefreshKey(k => k + 1),
  })

  const load = () => getIncomes().then(setIncomes).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getProperties().then(data => { setProperties(data); setPropertiesLoaded(true) })
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
    if (!pendingPrefill || !propertiesLoaded) return
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
      source: pendingPrefill.payerName ?? '',
      property: matchedProperty ? { id: matchedProperty.id } : null,
    })
    setEditing(null)
    setShowForm(true)
    setActiveTab('income')
    setPendingPrefill(null)
  }, [pendingPrefill, propertiesLoaded])

  const propertyOptions = useMemo(
    () => properties.map(p => ({ value: String(p.id), label: p.name })),
    [properties]
  )

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaveError(null)
    try {
      const data = { ...form, amount: parseFloat(form.amount) }
      if (editing) await updateIncome(editing, data)
      else await createIncome(data)
      setForm(EMPTY_FORM)
      setEditing(null)
      setShowForm(false)
      load()
    } catch (err) {
      setSaveError(err.message || 'Save failed')
    }
  }

  const handleEdit = (income) => {
    setForm({ ...income })
    setEditing(income.id)
    setShowForm(true)
    setActiveTab('income')
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete income',
      children: <Text size="sm">This income record will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => { await deleteIncome(id); load() },
    })
  }

  const handlePendingSaved = (income) => {
    load()
    setHighlightId(income.id)
    setActiveTab('income')
    setTimeout(() => setHighlightId(null), 3000)
  }

  const set = (key) => (val) => setForm(f => ({ ...f, [key]: val }))

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true); setActiveTab('income') }}>+ Add Income</Button>
      </Group>

      <Tabs value={activeTab} onChange={setActiveTab}>
        <Tabs.List>
          <Tabs.Tab value="income">Income</Tabs.Tab>
          <Tabs.Tab
            value="pending"
            rightSection={pendingCount > 0
              ? <Badge color="orange" size="xs" circle>{pendingCount}</Badge>
              : null}
          >
            Pending
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="income" pt="md">
          {showForm && (
            <Card withBorder p="lg" mb="md">
              <Title order={4} mb="md">{editing ? 'Edit Income' : 'New Income'}</Title>
              <form onSubmit={handleSubmit}>
                <Stack gap="sm">
                  <Group grow>
                    <NumberInput label="Amount" value={form.amount} onChange={set('amount')} min={0} decimalScale={2} prefix="$" required />
                    <TextInput label="Description" value={form.description} onChange={e => set('description')(e.target.value)} required />
                  </Group>
                  <Group grow>
                    <TextInput label="Date" type="date" value={form.date} onChange={e => set('date')(e.target.value)} required />
                    <TextInput label="Source" value={form.source} onChange={e => set('source')(e.target.value)} />
                  </Group>
                  <Group grow>
                    <Select
                      label="Property"
                      value={form.property?.id ? String(form.property.id) : null}
                      onChange={val => setForm(f => ({ ...f, property: val ? { id: Number(val) } : null }))}
                      data={propertyOptions}
                      clearable
                      placeholder="— None —"
                    />
                    <div />
                  </Group>
                  {saveError && <Text c="red" size="sm">{saveError}</Text>}
                  <Group>
                    <Button type="submit">Save</Button>
                    <Button variant="default" onClick={() => { setShowForm(false); setEditing(null); setSaveError(null) }}>Cancel</Button>
                  </Group>
                </Stack>
              </form>
            </Card>
          )}

          <Card withBorder p={0}>
            <ScrollArea>
              <Table striped highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    {['Date', 'Description', 'Source', 'Property', 'Amount', 'Actions'].map(h => (
                      <Table.Th key={h}>{h}</Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {incomes.length === 0 ? (
                    <Table.Tr><Table.Td colSpan={6}><Text ta="center" c="dimmed" py="xl">No income records yet</Text></Table.Td></Table.Tr>
                  ) : incomes.map(i => (
                    <Table.Tr key={i.id} style={{ background: highlightId === i.id ? 'var(--mantine-color-yellow-0)' : undefined, transition: 'background 0.5s' }}>
                      <Table.Td>{i.date}</Table.Td>
                      <Table.Td>{i.description}</Table.Td>
                      <Table.Td c="dimmed">{i.source || '—'}</Table.Td>
                      <Table.Td c="dimmed">{i.property?.name || '—'}</Table.Td>
                      <Table.Td fw={600} c="green">+{fmtCurrency(i.amount)}</Table.Td>
                      <Table.Td>
                        <Group gap="xs">
                          <ActionIcon variant="subtle" color="gray" onClick={() => handleEdit(i)}><IconPencil size={16} /></ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(i.id)}><IconTrash size={16} /></ActionIcon>
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
            filterType="INCOME"
          />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}