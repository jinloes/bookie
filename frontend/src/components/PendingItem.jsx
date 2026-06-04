import React, { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  ActionIcon, Alert, Badge, Button, Card, Collapse, Group, Loader, NumberInput, Select,
  Stack, Text, TextInput, Tooltip,
} from '@mantine/core'
import {
  IconAlertCircle, IconCheck, IconChevronDown, IconChevronRight, IconPlus, IconRefresh, IconTrash,
} from '@tabler/icons-react'
import {
  createPayer,
  dismissPendingExpense,
  getOutlookEmailContent,
  parseReceipt,
  savePendingExpense,
  savePendingIncome,
} from '../api/index.js'
import { fmtDateTime, todayISO } from '../utils/formatters.js'
import { EMAIL_TYPE, EXPENSE_SOURCE, PAYER_TYPE, PENDING_STATUS } from '../constants.js'

const STATUS_COLORS = {
  [PENDING_STATUS.PROCESSING]: 'blue',
  [PENDING_STATUS.READY]: 'green',
  [PENDING_STATUS.FAILED]: 'red',
}
const STATUS_LABELS = {
  [PENDING_STATUS.PROCESSING]: 'Processing…',
  [PENDING_STATUS.READY]: 'Ready',
  [PENDING_STATUS.FAILED]: 'Failed',
}

const HIGHLIGHT_STYLE = {
  backgroundColor: 'rgba(255, 212, 59, 0.5)',
  borderRadius: 3,
  padding: '0 2px',
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function decodeHtmlEntities(value) {
  if (!value) return ''
  const textarea = document.createElement('textarea')
  textarea.innerHTML = value
  return textarea.value
}

function normalizeDateVariants(dateValue) {
  if (!dateValue) return []
  const trimmed = dateValue.trim()
  if (!trimmed) return []
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return [trimmed]
  const [, y, m, d] = match
  return [trimmed, `${m}/${d}/${y}`, `${Number(m)}/${Number(d)}/${y}`]
}

function normalizeAmountVariants(amountValue) {
  if (amountValue == null || amountValue === '') return []
  const numeric = Number(amountValue)
  if (Number.isNaN(numeric)) return [String(amountValue)]
  const fixed = numeric.toFixed(2)
  const withCommas = numeric.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return [fixed, `$${fixed}`, withCommas, `$${withCommas}`]
}

function buildHighlightTerms(item, form, isIncome) {
  const rawTerms = [
    ...(isIncome ? normalizeAmountVariants(form?.amount) : normalizeAmountVariants(form?.amount ?? item.amount)),
    ...normalizeDateVariants(form?.date ?? item.date),
    item.payerName,
    item.propertyName,
    item.description,
    isIncome ? form?.source : form?.category,
  ]

  return Array.from(
    new Set(
      rawTerms
        .map(t => (t == null ? '' : String(t).trim()))
        .filter(t => t.length >= 3),
    ),
  ).sort((a, b) => b.length - a.length)
}

function renderHighlightedText(rawText, terms) {
  const decoded = decodeHtmlEntities(rawText)
  if (!decoded) return '(empty body)'
  if (!terms.length) return decoded

  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi')
  const parts = decoded.split(pattern)
  return parts.map((part, idx) => {
    if (!part) return null
    const matched = terms.some(term => part.toLowerCase() === term.toLowerCase())
    return matched
      ? <mark key={`h-${idx}`} style={HIGHLIGHT_STYLE}>{part}</mark>
      : <React.Fragment key={`t-${idx}`}>{part}</React.Fragment>
  })
}

export default function PendingItem({ item, categories, properties, payers, onSaved, onDismissed, onPayerCreated }) {
  const [expanded, setExpanded] = useState(false)
  const [form, setForm] = useState(null)
  const initialFormRef = useRef(null)
  const [saving, setSaving] = useState(false)
  const [rescanning, setRescanning] = useState(false)
  const [creatingPayer, setCreatingPayer] = useState(false)
  const [error, setError] = useState(null)

  const isIncome = item.emailType === EMAIL_TYPE.INCOME
  const highlightTerms = buildHighlightTerms(item, form, isIncome)
  const canShowOriginalEmail = item.sourceType === EXPENSE_SOURCE.OUTLOOK_EMAIL

  const {
    data: originalEmail,
    isLoading: loadingOriginalEmail,
    error: originalEmailError,
  } = useQuery({
    queryKey: ['outlookEmailContent', item.sourceId],
    queryFn: () => getOutlookEmailContent(item.sourceId),
    enabled: expanded && canShowOriginalEmail,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (item.status !== PENDING_STATUS.READY || form) return
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
        date: item.date ?? todayISO(),
        source: item.payerName ?? '',
        propertyId: matchedProperty?.id ?? null,
      }
     } else {
       const matchedPayer = item.payerName
         ? payers.find(p =>
             p.name.toLowerCase() === item.payerName.toLowerCase() ||
             (p.aliases ?? []).some(a => a.toLowerCase() === item.payerName.toLowerCase())
           ) ?? null
         : null
      initial = {
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? todayISO(),
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
      // amount as string so the backend's BigDecimal parses an exact decimal.
      const payload = { ...form, amount: String(form.amount ?? ''), date: form.date }
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
      const newPayer = await createPayer({
        name: form.suggestedPayerName,
        type: PAYER_TYPE.COMPANY,
        aliases: [],
        accounts: [],
      })
      setForm(f => ({ ...f, payerId: newPayer.id, suggestedPayerName: null }))
      onPayerCreated(newPayer)
    } catch (err) {
      setError(err.message || 'Failed to create payer')
    } finally {
      setCreatingPayer(false)
    }
  }

  const isProcessing = item.status === PENDING_STATUS.PROCESSING

  return (
    <Card withBorder p="sm">
      <Group
        justify="space-between"
        style={{ cursor: !isProcessing ? 'pointer' : 'default' }}
        onClick={() => !isProcessing && setExpanded(e => !e)}
      >
        <Group gap="xs">
          {!isProcessing
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
          {item.sourceType === EXPENSE_SOURCE.RECEIPT && (
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
        {canShowOriginalEmail && (
          <Card withBorder p="xs" mt="sm" bg="gray.0">
            <Stack gap={4}>
              <Group justify="space-between" align="baseline">
                <Text size="xs" fw={600}>Original email</Text>
                {loadingOriginalEmail && <Loader size={12} />}
              </Group>
              {originalEmailError && (
                <Text size="xs" c="red">Could not load original email content.</Text>
              )}
              {originalEmail && (
                <>
                  <Text size="xs" c="dimmed">
                    Received {originalEmail.receivedDate || 'unknown date'}
                  </Text>
                  <Text size="xs" c="dimmed">
                    Highlighted values: amount, date, payer, property, and extracted fields.
                  </Text>
                  <Text size="xs" fw={500}>{originalEmail.subject || item.subject || '(no subject)'}</Text>
                  <Text size="xs" style={{ whiteSpace: 'pre-wrap' }}>
                    {renderHighlightedText(originalEmail.body, highlightTerms)}
                  </Text>
                </>
              )}
            </Stack>
          </Card>
        )}

        {item.status === PENDING_STATUS.FAILED && (
          <Text size="xs" c="red" mt="xs">{item.errorMessage || 'Parsing failed'}</Text>
        )}

        {item.status === PENDING_STATUS.READY && form && (
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
