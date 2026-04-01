import React, { useCallback, useEffect, useState } from 'react'
import {
  Stack, Card, Group, Text, Badge, Loader, ActionIcon, Button, NumberInput,
  TextInput, Select, Alert, Tooltip, Collapse
} from '@mantine/core'
import { IconAlertCircle, IconCheck, IconChevronDown, IconChevronRight, IconRefresh, IconTrash } from '@tabler/icons-react'
import {
  getPendingExpenses, savePendingExpense, dismissPendingExpense,
  getExpenseCategories, getProperties, getPayers
} from '../api/index.js'

const formatDate = (iso) => new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })

const STATUS_COLORS = { PROCESSING: 'blue', READY: 'green', FAILED: 'red' }
const STATUS_LABELS = { PROCESSING: 'Processing…', READY: 'Ready', FAILED: 'Failed' }

function PendingItem({ item, categories, properties, payers, onSaved, onDismissed }) {
  const [expanded, setExpanded] = useState(false)
  const [form, setForm] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (item.status !== 'READY' || form) return
    const matchedProperty = item.propertyName
      ? (properties.find(p => p.name.toLowerCase() === item.propertyName.toLowerCase()) ??
         properties.find(p => p.address?.toLowerCase().includes(item.propertyName.toLowerCase())) ??
         null)
      : null
    const matchedPayer = item.payerName
      ? payers.find(p => p.name.toLowerCase() === item.payerName.toLowerCase()) ?? null
      : null
    setForm({
      amount: item.amount ?? '',
      description: item.description ?? '',
      date: item.date ?? new Date().toISOString().split('T')[0],
      category: item.category ?? 'OTHER',
      propertyId: matchedProperty?.id ?? null,
      payerId: matchedPayer?.id ?? null,
    })
  }, [item.status, properties, payers])

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    try {
      const expense = await savePendingExpense(item.id, {
        ...form,
        amount: parseFloat(form.amount),
        date: form.date,
      })
      onSaved(item.id, expense)
    } catch (err) {
      setError(err.message || 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  const handleDismiss = async () => {
    await dismissPendingExpense(item.id)
    onDismissed(item.id)
  }

  return (
    <Card withBorder p="sm">
      <Group justify="space-between" style={{ cursor: item.status !== 'PROCESSING' ? 'pointer' : 'default' }}
        onClick={() => item.status !== 'PROCESSING' && setExpanded(e => !e)}>
        <Group gap="xs">
          {item.status !== 'PROCESSING'
            ? (expanded ? <IconChevronDown size={14} /> : <IconChevronRight size={14} />)
            : <Loader size={10} color="blue" />}
          <Stack gap={0}>
            <Text fw={600} size="sm">{item.subject || '(no subject)'}</Text>
            <Text size="xs" c="dimmed">Queued {formatDate(item.createdAt)}</Text>
          </Stack>
        </Group>
        <Group gap="xs" onClick={e => e.stopPropagation()}>
          <Badge color={STATUS_COLORS[item.status]} variant="light" size="sm">
            {STATUS_LABELS[item.status]}
          </Badge>
          <Tooltip label="Dismiss">
            <ActionIcon variant="subtle" color="gray" size="sm" onClick={handleDismiss}>
              <IconTrash size={14} />
            </ActionIcon>
          </Tooltip>
        </Group>
      </Group>

      <Collapse in={expanded}>
        {item.status === 'FAILED' && (
          <Text size="xs" c="red" mt="xs">{item.errorMessage || 'Parsing failed'}</Text>
        )}

        {item.status === 'READY' && form && (
          <Stack gap="sm" mt="sm">
            {error && (
              <Alert icon={<IconAlertCircle size={14} />} color="red" p="xs">
                {error}
              </Alert>
            )}
            <Group grow>
              <NumberInput
                label="Amount"
                value={form.amount}
                onChange={val => setForm(f => ({ ...f, amount: val }))}
                min={0} decimalScale={2} prefix="$" size="xs"
              />
              <TextInput
                label="Description"
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                size="xs"
              />
            </Group>
            <Group grow>
              <TextInput
                label="Date"
                type="date"
                value={form.date}
                onChange={e => setForm(f => ({ ...f, date: e.target.value }))}
                size="xs"
              />
              <Select
                label="Category"
                value={form.category}
                onChange={val => setForm(f => ({ ...f, category: val }))}
                data={categories.map(c => ({ value: c.value, label: `Line ${c.scheduleELine} — ${c.label}` }))}
                size="xs"
              />
            </Group>
            <Group grow>
              <Select
                label="Property"
                value={form.propertyId ? String(form.propertyId) : null}
                onChange={val => setForm(f => ({ ...f, propertyId: val ? Number(val) : null }))}
                data={properties.map(p => ({ value: String(p.id), label: p.name }))}
                clearable placeholder="— None —" size="xs"
              />
              <Select
                label="Payer"
                value={form.payerId ? String(form.payerId) : null}
                onChange={val => setForm(f => ({ ...f, payerId: val ? Number(val) : null }))}
                data={payers.map(p => ({ value: String(p.id), label: p.name }))}
                clearable placeholder="— None —" size="xs"
              />
            </Group>
            <Group>
              <Button size="xs" leftSection={<IconCheck size={14} />} loading={saving} onClick={handleSave}>
                Save Expense
              </Button>
            </Group>
          </Stack>
        )}
      </Collapse>
    </Card>
  )
}

export default function PendingExpenses({ onSaved, onCountChange, refreshKey }) {
  const [items, setItems] = useState([])
  const [categories, setCategories] = useState([])
  const [properties, setProperties] = useState([])
  const [payers, setPayers] = useState([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(() =>
    getPendingExpenses().then(data => {
      setItems(data)
      onCountChange?.(data.filter(d => d.status === 'READY').length)
    }), [onCountChange])

  useEffect(() => {
    Promise.all([load(), getExpenseCategories(), getProperties(), getPayers()])
      .then(([, cats, props, pays]) => {
        setCategories(cats)
        setProperties(props)
        setPayers(pays)
      })
      .finally(() => setLoading(false))
  }, [])

  // Refresh when the parent SSE listener signals an update
  useEffect(() => {
    if (refreshKey > 0) {
      load()
    }
  }, [refreshKey])

  const handleSaved = (pendingId, expense) => {
    setItems(prev => {
      const next = prev.filter(i => i.id !== pendingId)
      onCountChange?.(next.filter(i => i.status === 'READY').length)
      return next
    })
    onSaved?.(expense)
  }

  const handleDismissed = (id) => {
    setItems(prev => {
      const next = prev.filter(i => i.id !== id)
      onCountChange?.(next.filter(i => i.status === 'READY').length)
      return next
    })
  }

  if (loading) return <Stack pt="md"><Loader size="sm" /></Stack>

  return (
    <Stack gap="sm" pt="md">
      <Group justify="flex-end">
        <Button variant="subtle" size="xs" leftSection={<IconRefresh size={14} />} onClick={load}>
          Refresh
        </Button>
      </Group>
      {items.length === 0 ? (
        <Text c="dimmed" size="sm" ta="center" py="xl">No pending expenses</Text>
      ) : (
        items.map(item => (
          <PendingItem
            key={item.id}
            item={item}
            categories={categories}
            properties={properties}
            payers={payers}
            onSaved={handleSaved}
            onDismissed={handleDismissed}
          />
        ))
      )}
    </Stack>
  )
}