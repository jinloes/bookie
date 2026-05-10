import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Anchor, Stack, Card, Group, Text, Badge, Loader, ActionIcon, Button, NumberInput,
  TextInput, Select, Alert, Tooltip, Collapse
} from '@mantine/core'
import { IconAlertCircle, IconCheck, IconChevronDown, IconChevronRight, IconPlus, IconRefresh, IconTrash } from '@tabler/icons-react'
import {
  getPendingExpenses, savePendingExpense, savePendingIncome, dismissPendingExpense,
  getExpenseCategories, getProperties, getPayers, createPayer, parseReceipt
} from '../api/index.js'

import { fmtDateTime } from '../utils/formatters.js'

const STATUS_COLORS = { PROCESSING: 'blue', READY: 'green', FAILED: 'red' }
const STATUS_LABELS = { PROCESSING: 'Processing…', READY: 'Ready', FAILED: 'Failed' }

function PendingItem({ item, categories, properties, payers, onSaved, onDismissed, onPayerCreated }) {
  const [expanded, setExpanded] = useState(false)
  const [form, setForm] = useState(null)
  const initialFormRef = useRef(null)
  const [saving, setSaving] = useState(false)
  const [rescanning, setRescanning] = useState(false)
  const [creatingPayer, setCreatingPayer] = useState(false)
  const [error, setError] = useState(null)

  const isIncome = item.emailType === 'INCOME'

  useEffect(() => {
    if (item.status !== 'READY' || form) return
    const matchedProperty = item.propertyName
      ? (properties.find(p => p.name.toLowerCase() === item.propertyName.toLowerCase()) ??
         properties.find(p => p.address?.toLowerCase().includes(item.propertyName.toLowerCase())) ??
         null)
      : null
    let initial
    if (isIncome) {
      initial = {
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? new Date().toISOString().split('T')[0],
        source: item.payerName ?? '',
        propertyId: matchedProperty?.id ?? null,
      }
    } else {
      const matchedPayer = item.payerName
        ? payers.find(p => p.name.toLowerCase() === item.payerName.toLowerCase()) ?? null
        : null
      initial = {
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? new Date().toISOString().split('T')[0],
        category: item.category ?? null,
        propertyId: matchedProperty?.id ?? null,
        payerId: matchedPayer?.id ?? null,
        suggestedPayerName: !matchedPayer && item.payerName ? item.payerName : null,
      }
    }
    initialFormRef.current = initial
    setForm(initial)
  }, [item.status, properties, payers])

  const handleReset = () => {
    if (initialFormRef.current) {
      setForm(initialFormRef.current)
      setError(null)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    try {
      const payload = { ...form, amount: parseFloat(form.amount), date: form.date }
      const saved = isIncome
        ? await savePendingIncome(item.id, payload)
        : await savePendingExpense(item.id, payload)
      onSaved(item.id, saved)
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

  const handleRescan = async () => {
    setRescanning(true)
    try {
      await parseReceipt(item.sourceId)
      onDismissed(item.id)
    } catch (err) {
      setError(err.message || 'Rescan failed')
    } finally {
      setRescanning(false)
    }
  }

  const handleCreatePayer = async () => {
    setCreatingPayer(true)
    setError(null)
    try {
      const newPayer = await createPayer({ name: form.suggestedPayerName, type: 'COMPANY', aliases: [], accounts: [] })
      setForm(f => ({ ...f, payerId: newPayer.id, suggestedPayerName: null }))
      onPayerCreated(newPayer)
    } catch (err) {
      setError(err.message || 'Failed to create payer')
    } finally {
      setCreatingPayer(false)
    }
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
            <Text size="xs" c="dimmed">Queued {fmtDateTime(item.createdAt)}</Text>
          </Stack>
        </Group>
        <Group gap="xs" onClick={e => e.stopPropagation()}>
          <Badge color={STATUS_COLORS[item.status]} variant="light" size="sm">
            {STATUS_LABELS[item.status]}
          </Badge>
          {item.sourceType === 'RECEIPT' && (
            <Tooltip label="Rescan receipt with latest extraction rules">
              <ActionIcon variant="subtle" color="blue" size="sm" loading={rescanning} onClick={handleRescan}>
                <IconRefresh size={14} />
              </ActionIcon>
            </Tooltip>
          )}
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
            {(form.amount === 0 || form.amount === '') && (
              <Alert icon={<IconAlertCircle size={14} />} color="yellow" p="xs">
                {isIncome
                  ? 'Amount could not be extracted — the receipt may be in a PDF attachment. Please enter it manually.'
                  : 'Amount could not be extracted — the bill may be in a PDF attachment. Please enter it manually.'}
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
              {isIncome ? (
                <TextInput
                  label="Source (tenant)"
                  value={form.source ?? ''}
                  onChange={e => setForm(f => ({ ...f, source: e.target.value }))}
                  size="xs"
                />
              ) : (
                <Select
                  label="Category"
                  placeholder="Select category"
                  withAsterisk
                  value={form.category}
                  onChange={val => setForm(f => ({ ...f, category: val }))}
                  data={categories.map(c => ({ value: c.value, label: `Line ${c.scheduleELine} — ${c.label}` }))}
                  size="xs"
                />
              )}
            </Group>
            <Group grow>
              <Select
                label="Property"
                value={form.propertyId ? String(form.propertyId) : null}
                onChange={val => setForm(f => ({ ...f, propertyId: val ? Number(val) : null }))}
                data={properties.map(p => ({ value: String(p.id), label: p.name }))}
                clearable placeholder="— None —" size="xs"
              />
              {!isIncome && (
                <Group gap="xs" align="flex-end" style={{ flex: 1 }}>
                  <Select
                    label="Payer"
                    style={{ flex: 1 }}
                    value={form.payerId ? String(form.payerId) : null}
                    onChange={val => setForm(f => ({ ...f, payerId: val ? Number(val) : null }))}
                    data={payers.map(p => ({ value: String(p.id), label: p.name }))}
                    clearable
                    placeholder={form.suggestedPayerName ? `No match found for "${form.suggestedPayerName}"` : '— None —'}
                    size="xs"
                  />
                  {!form.payerId && form.suggestedPayerName && (
                    <Tooltip label={`Create payer "${form.suggestedPayerName}"`}>
                      <ActionIcon variant="default" size="md" loading={creatingPayer} onClick={handleCreatePayer}>
                        <IconPlus size={14} />
                      </ActionIcon>
                    </Tooltip>
                  )}
                </Group>
              )}
            </Group>
            <Group>
              <Button
                size="xs"
                leftSection={<IconCheck size={14} />}
                loading={saving}
                disabled={!isIncome && !form.category}
                onClick={handleSave}
              >
                {isIncome ? 'Save Income' : 'Save Expense'}
              </Button>
              <Button size="xs" variant="subtle" color="gray" disabled={saving} onClick={handleReset}>
                Reset
              </Button>
            </Group>
          </Stack>
        )}
      </Collapse>
    </Card>
  )
}

export default function PendingExpenses({ onSaved, onCountChange, filterType, filterSource }) {
  const queryClient = useQueryClient()
  const { data: items = [], isLoading } = useQuery({
    queryKey: ['pendingExpenses'],
    queryFn: getPendingExpenses,
    refetchInterval: (query) => query.state.data?.some(i => i.status === 'PROCESSING') ? 5000 : false,
  })
  const { data: categories = [] } = useQuery({ queryKey: ['expenseCategories'], queryFn: getExpenseCategories })
  const { data: properties = [] } = useQuery({ queryKey: ['properties'], queryFn: getProperties })
  const { data: payers = [] } = useQuery({ queryKey: ['payers'], queryFn: getPayers })

  const filteredItems = useMemo(() => {
    let result = items
    if (filterSource) result = result.filter(i => i.sourceType === filterSource)
    if (filterType === 'INCOME') result = result.filter(i => i.emailType === 'INCOME')
    else if (filterType === 'EXPENSE') result = result.filter(i => i.emailType !== 'INCOME')
    return result
  }, [items, filterType, filterSource])

  useEffect(() => {
    onCountChange?.(filteredItems.filter(i => i.status === 'READY').length)
  }, [filteredItems, onCountChange])

  const handleSaved = (pendingId, expense) => {
    queryClient.setQueryData(['pendingExpenses'], prev => (prev ?? []).filter(i => i.id !== pendingId))
    onSaved?.(expense)
  }

  const handleDismissed = (id) => {
    queryClient.setQueryData(['pendingExpenses'], prev => (prev ?? []).filter(i => i.id !== id))
  }

  const handlePayerCreated = (newPayer) => {
    queryClient.setQueryData(['payers'], prev => [...(prev ?? []), newPayer])
  }

  const emptyMessage = filterSource === 'RECEIPT' ? 'No pending receipt entries'
    : filterType === 'INCOME' ? 'No pending income'
    : filterType === 'EXPENSE' ? 'No pending expenses'
    : 'No pending items'

  const contextNote = filterType === 'INCOME' ? 'Showing income items only.'
    : filterType === 'EXPENSE' ? 'Showing expense items only.'
    : filterSource === 'RECEIPT' ? 'Showing receipt items only.'
    : null

  if (isLoading) return <Stack pt="md"><Loader size="sm" /></Stack>

  return (
    <Stack gap="sm" pt="md">
      <Group justify="space-between">
        {contextNote ? (
          <Text size="xs" c="dimmed">
            {contextNote}{' '}
            <Anchor component={Link} to="/inbox" size="xs">View all in Inbox →</Anchor>
          </Text>
        ) : <span />}
        <Button variant="subtle" size="xs" leftSection={<IconRefresh size={14} />}
          onClick={() => queryClient.invalidateQueries({ queryKey: ['pendingExpenses'] })}>
          Refresh
        </Button>
      </Group>
      {filteredItems.length === 0 ? (
        <Text c="dimmed" size="sm" ta="center" py="xl">{emptyMessage}</Text>
      ) : (
        filteredItems.map(item => (
          <PendingItem
            key={item.id}
            item={item}
            categories={categories}
            properties={properties}
            payers={payers}
            onSaved={handleSaved}
            onDismissed={handleDismissed}
            onPayerCreated={handlePayerCreated}
          />
        ))
      )}
    </Stack>
  )
}