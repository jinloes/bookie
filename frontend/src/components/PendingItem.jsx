import React, { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  Collapse,
  Group,
  Loader,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import {
  IconAlertCircle,
  IconCheck,
  IconChevronDown,
  IconChevronRight,
  IconPlus,
  IconRefresh,
  IconTrash,
} from '@tabler/icons-react';
import {
  createPayer,
  dismissPendingExpense,
  getOutlookEmailContent,
  retryPendingExpense,
  savePendingExpense,
  savePendingIncome,
} from '../api/index.js';
import { fmtDateTime } from '../utils/formatters.js';
import { EMAIL_TYPE, EXPENSE_SOURCE, PAYER_TYPE, PENDING_STATUS } from '../constants.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { COLORS } from '../designTokens.js';

const STATUS_COLORS = {
  [PENDING_STATUS.PROCESSING]: 'blue',
  [PENDING_STATUS.READY]: 'green',
  [PENDING_STATUS.FAILED]: 'red',
};
const STATUS_LABELS = {
  [PENDING_STATUS.PROCESSING]: 'Processing…',
  [PENDING_STATUS.READY]: 'Ready',
  [PENDING_STATUS.FAILED]: 'Failed',
};

const HIGHLIGHT_STYLE = {
  backgroundColor: COLORS.KEYWORD_HIGHLIGHT,
  borderRadius: 3,
  padding: '0 2px',
};

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function decodeHtmlEntities(value) {
  if (!value) return '';
  const textarea = document.createElement('textarea');
  textarea.innerHTML = value;
  return textarea.value;
}

function normalizeDateVariants(dateValue) {
  if (!dateValue) return [];
  const trimmed = dateValue.trim();
  if (!trimmed) return [];
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return [trimmed];
  const [, y, m, d] = match;
  return [trimmed, `${m}/${d}/${y}`, `${Number(m)}/${Number(d)}/${y}`];
}

function normalizeAmountVariants(amountValue) {
  if (amountValue == null || amountValue === '') return [];
  const numeric = Number(amountValue);
  if (Number.isNaN(numeric)) return [String(amountValue)];
  const fixed = numeric.toFixed(2);
  const withCommas = numeric.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return [fixed, `$${fixed}`, withCommas, `$${withCommas}`];
}

function buildHighlightTerms(item, form, isIncome) {
  const rawTerms = [
    ...(isIncome
      ? normalizeAmountVariants(form?.amount)
      : normalizeAmountVariants(form?.amount ?? item.amount)),
    ...normalizeDateVariants(form?.date ?? item.date),
    item.payerName,
    item.propertyName,
    item.description,
    isIncome ? form?.source : form?.category,
  ];

  return Array.from(
    new Set(rawTerms.map((t) => (t == null ? '' : String(t).trim())).filter((t) => t.length >= 3))
  ).sort((a, b) => b.length - a.length);
}

function renderHighlightedText(rawText, terms) {
  const decoded = decodeHtmlEntities(rawText);
  if (!decoded) return '(empty body)';
  if (!terms.length) return decoded;

  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi');
  const parts = decoded.split(pattern);
  return parts.map((part, idx) => {
    if (!part) return null;
    const matched = terms.some((term) => part.toLowerCase() === term.toLowerCase());
    return matched ? (
      <mark key={`h-${idx}`} style={HIGHLIGHT_STYLE}>
        {part}
      </mark>
    ) : (
      <React.Fragment key={`t-${idx}`}>{part}</React.Fragment>
    );
  });
}

export default function PendingItem({
  item,
  categories,
  properties,
  payers,
  onSaved,
  onDismissed,
  onPayerCreated,
  onRetried,
}) {
  const [expanded, setExpanded] = useState(false);
  const [form, setForm] = useState(null);
  const initialFormRef = useRef(null);
  const [saving, setSaving] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [creatingPayer, setCreatingPayer] = useState(false);
  const [error, setError] = useState(null);

  const isIncome = item.emailType === EMAIL_TYPE.INCOME;
  const highlightTerms = buildHighlightTerms(item, form, isIncome);
  const canShowOriginalEmail = item.sourceType === EXPENSE_SOURCE.OUTLOOK_EMAIL;

  const {
    data: originalEmail,
    isLoading: loadingOriginalEmail,
    error: originalEmailError,
  } = useQuery({
    queryKey: queryKeys.outlookEmailContent(item.sourceId),
    queryFn: () => getOutlookEmailContent(item.sourceId),
    enabled: expanded && canShowOriginalEmail,
    staleTime: 60_000,
  });

  useEffect(() => {
    if (item.status !== PENDING_STATUS.READY || form) return;
    const matchedProperty = item.propertyName
      ? (properties.find((p) => p.name.toLowerCase() === item.propertyName.toLowerCase()) ??
        properties.find((p) =>
          p.address?.toLowerCase().includes(item.propertyName.toLowerCase())
        ) ??
        null)
      : null;
    let initial;
    if (isIncome) {
      initial = {
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? '',
        source: item.payerName ?? '',
        propertyId: matchedProperty?.id ?? null,
      };
    } else {
      const matchedPayer = item.payerName
        ? (payers.find(
            (p) =>
              p.name.toLowerCase() === item.payerName.toLowerCase() ||
              (p.aliases ?? []).some((a) => a.toLowerCase() === item.payerName.toLowerCase())
          ) ?? null)
        : null;
      initial = {
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? '',
        category: item.category ?? null,
        propertyId: matchedProperty?.id ?? null,
        payerId: matchedPayer?.id ?? null,
        suggestedPayerName: !matchedPayer && item.payerName ? item.payerName : null,
      };
    }
    initialFormRef.current = initial;
    setForm(initial);
  }, [
    form,
    isIncome,
    item.amount,
    item.category,
    item.date,
    item.description,
    item.payerName,
    item.propertyName,
    item.status,
    payers,
    properties,
  ]);

  const handleReset = () => {
    if (initialFormRef.current) {
      setForm(initialFormRef.current);
      setError(null);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      // amount as string so the backend's BigDecimal parses an exact decimal.
      const payload = { ...form, amount: String(form.amount ?? ''), date: form.date };
      const saved = isIncome
        ? await savePendingIncome(item.id, payload)
        : await savePendingExpense(item.id, payload);
      onSaved(item.id, saved);
    } catch (err) {
      setError(
        getErrorMessage(err, 'Could not save this item. Please check the fields and retry.')
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDismiss = async () => {
    modals.openConfirmModal({
      title: 'Dismiss pending item',
      children: (
        <Text size="sm">
          This pending item will be removed from your Review Queue. This action cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Dismiss', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await dismissPendingExpense(item.id);
          onDismissed(item.id);
        } catch (err) {
          notifications.show({
            title: 'Dismiss failed',
            message: getErrorMessage(err, 'Could not dismiss this item. Please retry.'),
            color: 'red',
          });
        }
      },
    });
  };

  const handleRetryParse = async () => {
    setRetrying(true);
    setError(null);
    try {
      await retryPendingExpense(item.id);
      onRetried?.(item.id);
    } catch (err) {
      notifications.show({
        title: 'Retry failed',
        message: getErrorMessage(err, 'Could not retry parsing. Please try again.'),
        color: 'red',
      });
    } finally {
      setRetrying(false);
    }
  };

  const handleCreatePayer = async () => {
    setCreatingPayer(true);
    setError(null);
    try {
      const newPayer = await createPayer({
        name: form.suggestedPayerName,
        type: PAYER_TYPE.COMPANY,
        aliases: [],
        accounts: [],
      });
      setForm((f) => ({ ...f, payerId: newPayer.id, suggestedPayerName: null }));
      onPayerCreated(newPayer);
    } catch (err) {
      setError(getErrorMessage(err, 'Failed to create payer. Please try again.'));
    } finally {
      setCreatingPayer(false);
    }
  };

  const isProcessing = item.status === PENDING_STATUS.PROCESSING;

  return (
    <Card withBorder p="sm">
      <Group justify="space-between" wrap="nowrap" align="flex-start">
        <UnstyledButton
          aria-expanded={expanded}
          aria-controls={`pending-item-content-${item.id}`}
          disabled={isProcessing}
          onClick={() => setExpanded((e) => !e)}
          style={{ flex: 1, cursor: isProcessing ? 'default' : 'pointer' }}
        >
          <Group gap="xs" wrap="nowrap" align="flex-start">
            {!isProcessing ? (
              expanded ? (
                <IconChevronDown size={14} />
              ) : (
                <IconChevronRight size={14} />
              )
            ) : (
              <Loader size={10} color="blue" />
            )}
            <Stack gap={0}>
              <Text fw={600} size="sm">
                {item.subject || '(no subject)'}
              </Text>
              <Text size="xs" c="dimmed">
                Queued {fmtDateTime(item.createdAt)}
              </Text>
            </Stack>
          </Group>
        </UnstyledButton>
        <Group gap="xs">
          <Badge color={STATUS_COLORS[item.status]} variant="light" size="sm">
            {STATUS_LABELS[item.status]}
          </Badge>
          {item.status === PENDING_STATUS.FAILED && (
            <Tooltip label="Retry parse">
              <ActionIcon
                variant="subtle"
                color="blue"
                size="sm"
                loading={retrying}
                onClick={handleRetryParse}
                aria-label="Retry parse"
              >
                <IconRefresh size={14} />
              </ActionIcon>
            </Tooltip>
          )}
          <Tooltip label="Dismiss from Review Queue">
            <ActionIcon
              variant="subtle"
              color="gray"
              size="sm"
              onClick={handleDismiss}
              aria-label="Dismiss pending item"
            >
              <IconTrash size={14} />
            </ActionIcon>
          </Tooltip>
        </Group>
      </Group>

      <Collapse in={expanded} id={`pending-item-content-${item.id}`}>
        {canShowOriginalEmail && (
          <Card withBorder p="xs" mt="sm" bg="gray.0">
            <Stack gap={4}>
              <Group justify="space-between" align="baseline">
                <Text size="xs" fw={600}>
                  Original email
                </Text>
                {loadingOriginalEmail && <Loader size={12} />}
              </Group>
              {originalEmailError && (
                <Text size="xs" c="red">
                  Could not load original email content.
                </Text>
              )}
              {originalEmail && (
                <>
                  <Text size="xs" c="dimmed">
                    Received {originalEmail.receivedDate || 'unknown date'}
                  </Text>
                  <Text size="xs" c="dimmed">
                    Highlighted values: amount, date, payer, property, and extracted fields.
                  </Text>
                  <Text size="xs" fw={500}>
                    {originalEmail.subject || item.subject || '(no subject)'}
                  </Text>
                  <Text size="xs" style={{ whiteSpace: 'pre-wrap' }}>
                    {renderHighlightedText(originalEmail.body, highlightTerms)}
                  </Text>
                </>
              )}
            </Stack>
          </Card>
        )}

        {item.status === PENDING_STATUS.FAILED && (
          <Alert icon={<IconAlertCircle size={14} />} color="red" mt="sm">
            <Group justify="space-between" align="center" wrap="wrap" gap="xs">
              <Text size="xs">{item.errorMessage || 'Parsing failed'}</Text>
              <Button
                size="xs"
                variant="default"
                loading={retrying}
                leftSection={<IconRefresh size={12} />}
                onClick={handleRetryParse}
              >
                Retry Parse
              </Button>
            </Group>
          </Alert>
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
            {!form.date && (
              <Alert icon={<IconAlertCircle size={14} />} color="yellow" p="xs">
                Date could not be extracted. Please enter the receipt/invoice date before saving.
              </Alert>
            )}
            <Group grow>
              <NumberInput
                label="Amount"
                value={form.amount}
                onChange={(val) => setForm((f) => ({ ...f, amount: val }))}
                min={0}
                decimalScale={2}
                prefix="$"
                size="sm"
              />
              <TextInput
                label="Description"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                size="sm"
              />
            </Group>
            <Group grow>
              <TextInput
                label="Date"
                type="date"
                value={form.date}
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                size="sm"
              />
              {isIncome ? (
                <TextInput
                  label="Source (tenant)"
                  value={form.source ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, source: e.target.value }))}
                  size="sm"
                />
              ) : (
                <Select
                  label="Category"
                  placeholder="Select category"
                  withAsterisk
                  value={form.category}
                  onChange={(val) => setForm((f) => ({ ...f, category: val }))}
                  data={categories.map((c) => ({
                    value: c.value,
                    label: `Line ${c.scheduleELine} — ${c.label}`,
                  }))}
                  size="sm"
                />
              )}
            </Group>
            <Group grow>
              <Select
                label="Property"
                value={form.propertyId ? String(form.propertyId) : null}
                onChange={(val) => setForm((f) => ({ ...f, propertyId: val ? Number(val) : null }))}
                data={properties.map((p) => ({ value: String(p.id), label: p.name }))}
                clearable
                placeholder="— None —"
                size="sm"
              />
              {!isIncome && (
                <Group gap="xs" align="flex-end" style={{ flex: 1 }}>
                  <Select
                    label="Payer"
                    style={{ flex: 1 }}
                    value={form.payerId ? String(form.payerId) : null}
                    onChange={(val) =>
                      setForm((f) => ({ ...f, payerId: val ? Number(val) : null }))
                    }
                    data={payers.map((p) => ({ value: String(p.id), label: p.name }))}
                    clearable
                    placeholder={
                      form.suggestedPayerName
                        ? `No match found for "${form.suggestedPayerName}"`
                        : '— None —'
                    }
                    size="sm"
                  />
                  {!form.payerId && form.suggestedPayerName && (
                    <Tooltip label={`Create payer "${form.suggestedPayerName}"`}>
                      <ActionIcon
                        variant="default"
                        size="md"
                        loading={creatingPayer}
                        onClick={handleCreatePayer}
                        aria-label={`Create payer ${form.suggestedPayerName}`}
                      >
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
                disabled={!form.date || (!isIncome && !form.category)}
                onClick={handleSave}
              >
                {isIncome ? 'Save Income' : 'Save Expense'}
              </Button>
              <Button
                size="xs"
                variant="subtle"
                color="gray"
                disabled={saving}
                onClick={handleReset}
              >
                Reset
              </Button>
            </Group>
          </Stack>
        )}
      </Collapse>
    </Card>
  );
}
