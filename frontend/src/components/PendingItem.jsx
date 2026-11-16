import React, { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { modals } from '@mantine/modals';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  Collapse,
  Group,
  Loader,
  Modal,
  NumberInput,
  Select,
  SimpleGrid,
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
  IconExternalLink,
  IconPlus,
  IconRefresh,
  IconTrash,
} from '@tabler/icons-react';
import { downloadReceipt, getFinancialCategories, getOutlookEmailContent } from '../api/index.js';
import { fmtCurrency, fmtDate, fmtDateTime } from '../utils/formatters.js';
import {
  EMAIL_TYPE,
  EXPENSE_SOURCE,
  PENDING_STATUS,
  importedIncomeSourceLabel,
  isImportedIncomeOrigin,
  isTransientInboxState,
} from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { COLORS } from '../designTokens.js';
import { usePendingItemActions } from '../hooks/usePendingItemActions.js';
import { useSavePendingItem } from '../hooks/useSavePendingItem.js';

const STATUS_COLORS = {
  [PENDING_STATUS.RECEIVED]: 'blue',
  [PENDING_STATUS.QUEUED]: 'blue',
  [PENDING_STATUS.PROCESSING]: 'blue',
  [PENDING_STATUS.READY]: 'green',
  [PENDING_STATUS.FAILED]: 'red',
};
const STATUS_LABELS = {
  [PENDING_STATUS.RECEIVED]: 'Queued…',
  [PENDING_STATUS.QUEUED]: 'Queued…',
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

function buildHighlightTerms(item, form, isIncome, activities, categories) {
  const rawTerms = [
    ...(isIncome
      ? normalizeAmountVariants(form?.amount)
      : normalizeAmountVariants(form?.amount ?? item.amount)),
    ...normalizeDateVariants(form?.date ?? item.date),
    item.counterpartyName,
    item.propertyName,
    item.description,
    activities.find((activity) => String(activity.id) === String(form?.activityId))?.name,
    isIncome ? form?.source : item.proposedCategory,
    categories.find((category) => String(category.neutralCategoryId) === String(form?.categoryId))
      ?.label,
  ];

  return Array.from(
    new Set(rawTerms.map((t) => (t == null ? '' : String(t).trim())).filter((t) => t.length >= 3))
  ).sort((a, b) => b.length - a.length);
}

// Ledger saves carry neutral categories, so each compatible financial category is offered by the
// neutral category it maps to.
function neutralCategoryOptions(categories) {
  const options = new Map();
  categories
    .filter((category) => category.neutralCategoryId)
    .forEach((category) => {
      const value = String(category.neutralCategoryId);
      if (!options.has(value)) {
        options.set(value, category.label);
      }
    });
  return [...options].map(([value, label]) => ({ value, label }));
}

function renderHighlightedText(rawText, terms) {
  const text = rawText ?? '';
  if (!text) return '(empty body)';
  if (!terms.length) return text;

  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi');
  const parts = text.split(pattern);
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
  properties,
  payers,
  activities,
  defaultExpanded = false,
  onSaved,
  onDismissed = undefined,
  onPayerCreated = undefined,
  onRetried = undefined,
}) {
  const [expanded, setExpanded] = useState(Boolean(defaultExpanded));
  const [form, setForm] = useState(null);

  useEffect(() => {
    if (defaultExpanded && item.state === PENDING_STATUS.READY) {
      setExpanded(true);
    }
  }, [defaultExpanded, item.state]);
  const initialFormRef = useRef(null);
  const formVersionRef = useRef(null);
  const [receiptInlineOpen, setReceiptInlineOpen] = useState(false);
  const [receiptPreviewOpen, setReceiptPreviewOpen] = useState(false);
  const [receiptObjectUrl, setReceiptObjectUrl] = useState(null);
  const {
    retrying,
    creatingPayer,
    actionError: error,
    clearActionError,
    dismissItem,
    retryParse: handleRetryParse,
    createSuggestedPayer,
  } = usePendingItemActions({
    item,
    onDismissed,
    onRetried,
    onPayerCreated,
  });
  const {
    savePendingItem,
    saving,
    saveError: mutationError,
    clearSaveError,
  } = useSavePendingItem({ itemId: item.id, onSaved });

  const direction =
    form?.direction ??
    (item.proposedDirection === EMAIL_TYPE.INCOME ? EMAIL_TYPE.INCOME : EMAIL_TYPE.EXPENSE);
  const isIncome = direction === EMAIL_TYPE.INCOME;
  const { data: compatibleCategories = [] } = useQuery({
    queryKey: queryKeys.financialCategories(direction, form?.activityId ?? null),
    queryFn: () => getFinancialCategories(direction, form?.activityId ?? null),
    enabled: Boolean(form?.activityId),
  });
  const highlightTerms = buildHighlightTerms(
    item,
    form,
    isIncome,
    activities,
    compatibleCategories
  );
  const canShowOriginalEmail = item.origin === EXPENSE_SOURCE.OUTLOOK_EMAIL;
  const canShowOriginalReceipt = item.origin === EXPENSE_SOURCE.RECEIPT;

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

  const {
    data: receiptContent,
    isLoading: loadingReceiptContent,
    error: receiptContentError,
  } = useQuery({
    queryKey: queryKeys.receiptContent(item.sourceId),
    queryFn: () => downloadReceipt(item.sourceId),
    enabled: (receiptInlineOpen || receiptPreviewOpen) && canShowOriginalReceipt,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (!receiptContent) {
      setReceiptObjectUrl(null);
      return undefined;
    }
    const objectUrl = URL.createObjectURL(receiptContent);
    setReceiptObjectUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [receiptContent]);

  useEffect(() => {
    if (item.state !== PENDING_STATUS.READY) return;
    // A newer server version (e.g. a reparse) replaces the form only while it is unedited.
    const pristine = form === initialFormRef.current;
    if (form && (formVersionRef.current === item.version || !pristine)) return;
    const matchedProperty = item.propertyName
      ? (properties.find((p) => p.name.toLowerCase() === item.propertyName.toLowerCase()) ??
        properties.find((p) =>
          p.address?.toLowerCase().includes(item.propertyName.toLowerCase())
        ) ??
        null)
      : null;
    const matchedActivity =
      activities.find((activity) => String(activity.id) === String(item.activity?.id)) ??
      activities.find(
        (activity) =>
          matchedProperty && String(activity.property?.id) === String(matchedProperty.id)
      ) ??
      null;
    let initial;
    if (isIncome) {
      initial = {
        direction: EMAIL_TYPE.INCOME,
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? '',
        source: item.sourceLabel ?? item.counterpartyName ?? '',
        counterpartyId: item.counterpartyId ?? null,
        propertyId: matchedProperty?.id ?? null,
        activityId: matchedActivity?.id ?? null,
        categoryId: item.category?.neutralCategoryId ?? null,
      };
    } else {
      const matchedPayer = item.counterpartyName
        ? (payers.find(
            (p) =>
              p.name.toLowerCase() === item.counterpartyName.toLowerCase() ||
              (p.aliases ?? []).some((a) => a.toLowerCase() === item.counterpartyName.toLowerCase())
          ) ?? null)
        : null;
      initial = {
        direction: EMAIL_TYPE.EXPENSE,
        amount: item.amount ?? '',
        description: item.description ?? '',
        date: item.date ?? '',
        categoryId: item.category?.neutralCategoryId ?? null,
        propertyId: matchedProperty?.id ?? null,
        activityId: matchedActivity?.id ?? null,
        payerId: matchedPayer?.id ?? null,
        suggestedPayerName: !matchedPayer && item.counterpartyName ? item.counterpartyName : null,
      };
    }
    formVersionRef.current = item.version;
    initialFormRef.current = initial;
    setForm(initial);
  }, [
    form,
    isIncome,
    item.version,
    item.amount,
    item.proposedCategory,
    item.date,
    item.description,
    item.counterpartyName,
    item.counterpartyId,
    item.sourceLabel,
    item.propertyName,
    item.state,
    item.activity?.id,
    item.category?.neutralCategoryId,
    activities,
    payers,
    properties,
  ]);

  const handleReset = () => {
    if (initialFormRef.current) {
      setForm(initialFormRef.current);
      clearActionError();
      clearSaveError();
    }
  };

  const handleSave = async () => {
    clearActionError();
    try {
      await savePendingItem({
        ...form,
        source: form.source ?? item.counterpartyName ?? item.subject ?? '',
      });
    } catch {
      // useSavePendingItem owns the user-visible mutation error.
    }
  };

  const handleDismiss = () => {
    modals.openConfirmModal({
      title: 'Dismiss pending item',
      children: (
        <Text size="sm">
          This pending item will be removed from your Review Queue. This action cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Dismiss', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: dismissItem,
    });
  };

  // Only parsed sources can be reparsed; the backend rejects imported statement rows.
  const canReparseReady =
    item.state === PENDING_STATUS.READY &&
    (item.origin === EXPENSE_SOURCE.OUTLOOK_EMAIL || item.origin === EXPENSE_SOURCE.RECEIPT);

  const handleReparseReady = () => {
    modals.openConfirmModal({
      title: 'Re-parse this item?',
      children: (
        <Text size="sm">
          Bookie will extract the amount, date, payer and category again. Any unsaved edits to this
          item will be replaced.
        </Text>
      ),
      labels: { confirm: 'Re-parse', cancel: 'Cancel' },
      onConfirm: handleRetryParse,
    });
  };

  const handleCreatePayer = async () => {
    const newPayer = await createSuggestedPayer(form.suggestedPayerName);
    if (newPayer) {
      setForm((f) => ({ ...f, payerId: newPayer.id, suggestedPayerName: null }));
    }
  };

  const isProcessing = isTransientInboxState(item.state);
  // Recurring emails share a subject and are often queued together, so the extracted amount, date
  // and counterparty are what tell rows apart.
  const summaryParts = [
    item.amount != null ? fmtCurrency(item.amount) : null,
    item.date ? fmtDate(item.date) : null,
    item.counterpartyName || item.propertyName,
  ].filter(Boolean);
  const selectedActivity =
    activities.find((activity) => String(activity.id) === String(form?.activityId)) ?? null;

  return (
    <Card withBorder p="sm">
      <Modal
        opened={receiptPreviewOpen}
        onClose={() => setReceiptPreviewOpen(false)}
        title={item.subject || 'Receipt preview'}
        size="xl"
        padding="md"
      >
        {loadingReceiptContent && <Loader size="sm" />}
        {receiptContentError && (
          <Text size="sm" c="red">
            Could not load the receipt.
          </Text>
        )}
        {receiptObjectUrl &&
          (receiptContent.type.startsWith('image/') ? (
            <img
              alt={item.subject || 'Receipt'}
              src={receiptObjectUrl}
              style={{
                display: 'block',
                maxWidth: '100%',
                maxHeight: '80vh',
                width: 'auto',
                height: 'auto',
                margin: '0 auto',
                objectFit: 'contain',
              }}
            />
          ) : (
            <iframe
              title={`Preview ${item.subject || 'receipt'}`}
              src={receiptObjectUrl}
              style={{ width: '100%', height: '80vh', border: 0 }}
            />
          ))}
      </Modal>

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
                {item.subject || item.description || item.counterpartyName || '(no subject)'}
              </Text>
              {summaryParts.length > 0 && <Text size="xs">{summaryParts.join(' · ')}</Text>}
              {item.createdAt && (
                <Text size="xs" c="dimmed">
                  Queued {fmtDateTime(item.createdAt)}
                </Text>
              )}
            </Stack>
          </Group>
        </UnstyledButton>
        <Group gap="xs">
          {isImportedIncomeOrigin(item.origin) && (
            <Badge color="teal" variant="outline" size="sm">
              {importedIncomeSourceLabel(item.origin)}
            </Badge>
          )}
          <Badge color={STATUS_COLORS[item.state]} variant="light" size="sm">
            {STATUS_LABELS[item.state]}
          </Badge>
          {item.state === PENDING_STATUS.READY && (
            <Badge color={isIncome ? 'green' : 'red'} variant="light" size="sm">
              {direction}
            </Badge>
          )}
          {item.state === PENDING_STATUS.FAILED && (
            <Tooltip label="Retry parse">
              <ActionIcon
                variant="subtle"
                color="blue"
                size="md"
                loading={retrying}
                onClick={handleRetryParse}
                aria-label="Retry parse"
              >
                <IconRefresh size={15} />
              </ActionIcon>
            </Tooltip>
          )}
          {canReparseReady && (
            <Tooltip label="Re-parse">
              <ActionIcon
                variant="subtle"
                color="blue"
                size="md"
                loading={retrying}
                onClick={handleReparseReady}
                aria-label="Re-parse pending item"
              >
                <IconRefresh size={15} />
              </ActionIcon>
            </Tooltip>
          )}
          <Tooltip label="Dismiss from Review Queue">
            <ActionIcon
              variant="subtle"
              color="gray"
              size="md"
              onClick={handleDismiss}
              aria-label="Dismiss pending item"
            >
              <IconTrash size={15} />
            </ActionIcon>
          </Tooltip>
        </Group>
      </Group>

      <Collapse expanded={expanded} id={`pending-item-content-${item.id}`}>
        {expanded && (
          <SimpleGrid
            cols={{ base: 1, md: canShowOriginalEmail || canShowOriginalReceipt ? 2 : 1 }}
            spacing="md"
            mt="sm"
          >
            {(canShowOriginalEmail || canShowOriginalReceipt) && (
              <div>
                {canShowOriginalEmail && (
                  <Card
                    withBorder
                    p="sm"
                    style={{
                      background: COLORS.SURFACE_SUBTLE,
                      maxHeight: 520,
                      overflowY: 'auto',
                    }}
                  >
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

                {canShowOriginalReceipt && (
                  <Card withBorder p="sm" style={{ background: COLORS.SURFACE_SUBTLE }}>
                    <Stack gap="xs">
                      <Group justify="space-between" align="center">
                        <Text size="xs" fw={600}>
                          Original receipt
                        </Text>
                        <Group gap="xs">
                          {!receiptInlineOpen ? (
                            <Button
                              size="xs"
                              variant="default"
                              onClick={() => setReceiptInlineOpen(true)}
                              leftSection={<IconExternalLink size={12} />}
                            >
                              View receipt
                            </Button>
                          ) : (
                            <Button
                              size="xs"
                              variant="default"
                              onClick={() => setReceiptPreviewOpen(true)}
                              leftSection={<IconExternalLink size={12} />}
                            >
                              Full size
                            </Button>
                          )}
                        </Group>
                      </Group>
                      {!receiptInlineOpen && (
                        <Text size="xs" c="dimmed">
                          Load the receipt side-by-side to verify extracted fields without leaving
                          this form.
                        </Text>
                      )}
                      {receiptInlineOpen && loadingReceiptContent && <Loader size="sm" />}
                      {receiptInlineOpen && receiptContentError && (
                        <Text size="xs" c="red">
                          Could not load the receipt.
                        </Text>
                      )}
                      {receiptInlineOpen &&
                        !receiptPreviewOpen &&
                        receiptObjectUrl &&
                        (receiptContent?.type?.startsWith('image/') ? (
                          <img
                            alt={item.subject || 'Receipt'}
                            src={receiptObjectUrl}
                            style={{
                              display: 'block',
                              maxWidth: '100%',
                              maxHeight: 440,
                              width: 'auto',
                              height: 'auto',
                              margin: '0 auto',
                              objectFit: 'contain',
                              borderRadius: 6,
                            }}
                          />
                        ) : (
                          <iframe
                            title={`Preview ${item.subject || 'receipt'}`}
                            src={receiptObjectUrl}
                            style={{ width: '100%', height: 440, border: 0, borderRadius: 6 }}
                          />
                        ))}
                    </Stack>
                  </Card>
                )}
              </div>
            )}

            <div>
              {item.state === PENDING_STATUS.FAILED && (
                <Alert icon={<IconAlertCircle size={14} />} color="red">
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

              {item.state === PENDING_STATUS.READY && form && (
                <Stack gap="sm">
                  {(error || mutationError) && (
                    <Alert icon={<IconAlertCircle size={14} />} color="red" p="xs">
                      {error || mutationError}
                    </Alert>
                  )}
                  {item.classificationAmbiguous && (
                    <Alert icon={<IconAlertCircle size={14} />} color="yellow" p="xs">
                      Classification needs review. Confirm direction, activity, and category before
                      saving.
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
                      Date could not be extracted. Please enter the receipt/invoice date before
                      saving.
                    </Alert>
                  )}
                  <Group grow align="flex-start">
                    <Select
                      label="Direction"
                      aria-label="Transaction direction"
                      withAsterisk
                      value={direction}
                      onChange={(value) =>
                        setForm((current) => ({
                          ...current,
                          direction: value ?? EMAIL_TYPE.EXPENSE,
                          categoryId: null,
                        }))
                      }
                      data={[
                        { value: EMAIL_TYPE.INCOME, label: 'Income' },
                        { value: EMAIL_TYPE.EXPENSE, label: 'Expense' },
                      ]}
                      size="sm"
                    />
                    <NumberInput
                      label="Amount"
                      value={form.amount}
                      onChange={(val) => setForm((f) => ({ ...f, amount: val }))}
                      min={0}
                      decimalScale={2}
                      prefix="$"
                      size="sm"
                    />
                  </Group>
                  <TextInput
                    label="Description"
                    value={form.description}
                    onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                    size="sm"
                  />
                  <Group grow>
                    <TextInput
                      label="Date"
                      type="date"
                      value={form.date}
                      onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                      size="sm"
                    />
                    {isIncome && (
                      <TextInput
                        label="Source"
                        value={form.source ?? ''}
                        onChange={(e) => setForm((f) => ({ ...f, source: e.target.value }))}
                        size="sm"
                      />
                    )}
                  </Group>
                  <Group grow>
                    <Select
                      label="Activity"
                      withAsterisk
                      searchable
                      value={form.activityId ? String(form.activityId) : null}
                      onChange={(val) => {
                        const activity = activities.find(
                          (candidate) => String(candidate.id) === String(val)
                        );
                        setForm((current) => ({
                          ...current,
                          activityId: val ? Number(val) : null,
                          propertyId: activity?.property?.id ?? null,
                          categoryId: null,
                        }));
                      }}
                      data={activities
                        .filter((activity) => activity.active)
                        .map((activity) => ({ value: String(activity.id), label: activity.name }))}
                      placeholder="Select activity"
                      size="sm"
                    />
                    <Select
                      label="Category"
                      aria-label="Transaction category"
                      withAsterisk
                      searchable
                      value={form.categoryId ? String(form.categoryId) : null}
                      onChange={(val) =>
                        setForm((current) => ({
                          ...current,
                          categoryId: val ? Number(val) : null,
                        }))
                      }
                      data={neutralCategoryOptions(compatibleCategories)}
                      placeholder={form.activityId ? 'Select category' : 'Select an activity first'}
                      disabled={!form.activityId}
                      size="sm"
                    />
                  </Group>
                  <Group grow>
                    <TextInput
                      label="Owner"
                      value={selectedActivity?.owner?.name ?? ''}
                      readOnly
                      description="Derived from activity"
                    />
                    {selectedActivity?.property && (
                      <TextInput
                        label="Rental property"
                        value={selectedActivity.property.name}
                        readOnly
                        description="Derived from activity"
                      />
                    )}
                  </Group>
                  {!isIncome && (
                    <Group>
                      <Group gap="xs" align="flex-end" style={{ flex: 1 }}>
                        <Select
                          label="Counterparty"
                          style={{ flex: 1 }}
                          value={form.payerId ? String(form.payerId) : null}
                          onChange={(val) =>
                            setForm((f) => ({ ...f, payerId: val ? Number(val) : null }))
                          }
                          data={payers.map((p) => ({ value: String(p.id), label: p.name }))}
                          clearable
                          searchable
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
                    </Group>
                  )}
                  <Group>
                    <Button
                      size="xs"
                      leftSection={<IconCheck size={14} />}
                      loading={saving}
                      disabled={
                        !form.amount ||
                        !form.description?.trim() ||
                        !form.date ||
                        !form.activityId ||
                        !form.categoryId
                      }
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
            </div>
          </SimpleGrid>
        )}
      </Collapse>
    </Card>
  );
}
