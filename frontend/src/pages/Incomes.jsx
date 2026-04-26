import React, { useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Stack, Group, Title, Button, Drawer, Box, TextInput, NumberInput, Select, Table,
  Text, Loader, Center, ActionIcon, Badge, Tabs, ScrollArea
} from '@mantine/core'
import { useForm } from '@mantine/form'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import { IconPencil, IconTrash } from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getIncomes, createIncome, updateIncome, deleteIncome, getProperties } from '../api/index.js'
import { usePendingSSE } from '../hooks/usePendingSSE.js'
import { fmtCurrency } from '../utils/formatters.js'
import PendingExpenses from '../components/PendingExpenses.jsx'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], source: '', propertyId: null }

export default function Incomes() {
  const queryClient = useQueryClient()
  const { data: incomes = [], isLoading: incomesLoading } = useQuery({ queryKey: ['incomes'], queryFn: getIncomes })
  const { data: properties = [], isFetched: propertiesFetched } = useQuery({ queryKey: ['properties'], queryFn: getProperties })

  const form = useForm({ initialValues: EMPTY_FORM })
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [pendingPrefill, setPendingPrefill] = useState(null)
  const [highlightId, setHighlightId] = useState(null)
  const [activeTab, setActiveTab] = useState('income')
  const [pendingCount, setPendingCount] = useState(0)
  const [pendingRefreshKey, setPendingRefreshKey] = useState(0)
  const [filterYear, setFilterYear] = useState(null)
  const location = useLocation()

  const propertyOptions = useMemo(
    () => properties.map(p => ({ value: String(p.id), label: p.name })),
    [properties]
  )

  const yearOptions = useMemo(() => {
    const years = [...new Set(incomes.map(i => i.date?.slice(0, 4)).filter(Boolean))].sort().reverse()
    return years.map(y => ({ value: y, label: y }))
  }, [incomes])

  const visibleIncomes = useMemo(
    () => filterYear ? incomes.filter(i => i.date?.startsWith(filterYear)) : incomes,
    [incomes, filterYear]
  )

  usePendingSSE({
    filter: (d) => d.emailType === 'INCOME',
    activeTab,
    notification: { title: 'Email parsed', message: 'A new income is ready to review in Pending', color: 'teal' },
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
    if (!pendingPrefill || !propertiesFetched) return
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
      source: pendingPrefill.payerName ?? '',
      propertyId: matchedProperty ? String(matchedProperty.id) : null,
    })
    setEditing(null)
    setShowForm(true)
    setActiveTab('income')
    setPendingPrefill(null)
  }, [pendingPrefill, propertiesFetched, properties])

  const handleSubmit = async (values) => {
    setSaveError(null)
    const data = {
      amount: parseFloat(values.amount),
      description: values.description,
      date: values.date,
      source: values.source,
      property: values.propertyId ? { id: Number(values.propertyId) } : null,
    }
    try {
      if (editing) await updateIncome(editing, data)
      else await createIncome(data)
      notifications.show({ title: editing ? 'Income updated' : 'Income saved', color: 'green' })
      form.reset()
      form.setFieldValue('date', new Date().toISOString().split('T')[0])
      setEditing(null)
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['incomes'] })
      queryClient.invalidateQueries({ queryKey: ['totalIncome'] })
    } catch (err) {
      setSaveError(err.message || 'Save failed')
    }
  }

  const handleEdit = (income) => {
    form.setValues({
      amount: income.amount,
      description: income.description,
      date: income.date,
      source: income.source || '',
      propertyId: income.property?.id ? String(income.property.id) : null,
    })
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
      onConfirm: async () => {
        await deleteIncome(id)
        queryClient.invalidateQueries({ queryKey: ['incomes'] })
        queryClient.invalidateQueries({ queryKey: ['totalIncome'] })
      },
    })
  }

  const handlePendingSaved = (income) => {
    queryClient.invalidateQueries({ queryKey: ['incomes'] })
    queryClient.invalidateQueries({ queryKey: ['totalIncome'] })
    setHighlightId(income.id)
    setActiveTab('income')
    setTimeout(() => setHighlightId(null), 3000)
  }

  const cancelForm = () => {
    setShowForm(false)
    setEditing(null)
    setSaveError(null)
    form.reset()
  }

  if (incomesLoading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Button onClick={() => { form.reset(); form.setFieldValue('date', new Date().toISOString().split('T')[0]); setEditing(null); setShowForm(true); setActiveTab('income') }}>
          + Add Income
        </Button>
      </Group>

      <Drawer
        opened={showForm}
        onClose={cancelForm}
        title={editing ? 'Edit Income' : 'New Income'}
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        <form onSubmit={form.onSubmit(handleSubmit)} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Group grow>
              <NumberInput label="Amount" {...form.getInputProps('amount')} min={0} decimalScale={2} prefix="$" required />
              <TextInput label="Description" {...form.getInputProps('description')} required />
            </Group>
            <Group grow>
              <TextInput label="Date" type="date" {...form.getInputProps('date')} required />
              <TextInput label="Source" {...form.getInputProps('source')} />
            </Group>
            <Group grow>
              <Select
                label="Property"
                {...form.getInputProps('propertyId')}
                data={propertyOptions}
                clearable
                placeholder="— None —"
              />
              <div />
            </Group>
            {saveError && <Text c="red" size="sm">{saveError}</Text>}
          </Stack>
          <Box pt="md" style={{ borderTop: '1px solid var(--mantine-color-gray-2)', flexShrink: 0 }}>
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={cancelForm}>Cancel</Button>
            </Group>
          </Box>
        </form>
      </Drawer>

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
          <Group mb="sm">
            <Select
              placeholder="All years"
              data={yearOptions}
              value={filterYear}
              onChange={setFilterYear}
              clearable
              size="xs"
              style={{ width: 110 }}
            />
          </Group>

          <ScrollArea>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={90}>Date</Table.Th>
                  <Table.Th>Description</Table.Th>
                  <Table.Th w={130}>Source</Table.Th>
                  <Table.Th w={150}>Property</Table.Th>
                  <Table.Th w={110} style={{ textAlign: 'right' }}>Amount</Table.Th>
                  <Table.Th w={72}>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {visibleIncomes.length === 0 ? (
                  <Table.Tr>
                    <Table.Td colSpan={6}>
                      <Text ta="center" c="dimmed" py="xl" size="sm">
                        {filterYear ? 'No income records match the current filters' : 'No income records yet'}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                ) : visibleIncomes.map(i => (
                  <Table.Tr
                    key={i.id}
                    style={{
                      background: highlightId === i.id ? 'var(--mantine-color-yellow-0)' : undefined,
                      transition: 'background 0.5s',
                    }}
                  >
                    <Table.Td c="dimmed">{i.date}</Table.Td>
                    <Table.Td>{i.description}</Table.Td>
                    <Table.Td c="dimmed">{i.source || '—'}</Table.Td>
                    <Table.Td c="dimmed">{i.property?.name || '—'}</Table.Td>
                    <Table.Td
                      fw={600}
                      c="green"
                      style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                    >
                      +{fmtCurrency(i.amount)}
                    </Table.Td>
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
