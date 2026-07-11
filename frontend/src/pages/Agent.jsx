import React, { useState, useRef, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Stack,
  Group,
  Title,
  Text,
  Button,
  TextInput,
  Card,
  UnstyledButton,
  Paper,
  ScrollArea,
  Loader,
  Alert,
  NumberInput,
  Select,
} from '@mantine/core';
import { IconRobot, IconSend, IconAlertCircle, IconCheck, IconX } from '@tabler/icons-react';
import {
  submitExpenseToAgent,
  createExpense,
  getProperties,
  getPayers,
  getExpenseCategories,
} from '../api/index.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';

const EXAMPLES = [
  'I paid $250 for plumbing repairs at Oak Street property last Monday',
  'Spent $120 on landscaping for the Main St duplex today',
  'Property insurance payment of $890 for Maple Ave on March 15th',
  'Paid $75 for cleaning supplies for the downtown apartment yesterday',
  '$1,500 mortgage payment for Oak Street property today',
];

/**
 * Editable proposal card shown before an expense extracted by the AI agent is saved. Nothing is
 * written to the database until the user clicks "Save Expense" — see AgentService.ProposedExpense
 * on the backend for why this confirmation step exists (a misheard amount or wrong category must
 * never become a committed record with no review).
 */
function ProposalCard({ proposal, categories, properties, payers, onSaved, onDiscarded }) {
  const [form, setForm] = useState({
    amount: proposal.amount ?? '',
    description: proposal.description ?? '',
    date: proposal.date ?? '',
    category: proposal.category ?? null,
    propertyId: proposal.propertyId ?? null,
    payerId: proposal.payerId ?? null,
  });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [discarded, setDiscarded] = useState(false);
  const [error, setError] = useState(null);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const created = await createExpense({
        amount: String(form.amount ?? ''),
        description: form.description,
        date: form.date,
        category: form.category,
        propertyId: form.propertyId,
        payerId: form.payerId,
      });
      setSaved(true);
      onSaved(created);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not save this expense. Please check the fields.'));
    } finally {
      setSaving(false);
    }
  };

  const handleDiscard = () => {
    setDiscarded(true);
    onDiscarded();
  };

  if (saved) {
    return (
      <Paper mt="xs" p="xs" radius="sm" withBorder bg="white">
        <Group gap="xs">
          <IconCheck size={14} color="var(--mantine-color-green-6)" />
          <Text size="xs" fw={600} c="dark">
            Expense saved
          </Text>
        </Group>
      </Paper>
    );
  }

  if (discarded) {
    return (
      <Paper mt="xs" p="xs" radius="sm" withBorder bg="white">
        <Text size="xs" c="dimmed">
          Discarded — nothing was saved.
        </Text>
      </Paper>
    );
  }

  return (
    <Paper mt="xs" p="sm" radius="sm" withBorder bg="white">
      <Text size="xs" fw={700} c="dark" mb={6}>
        Review before saving:
      </Text>
      {error && (
        <Alert mb="xs" icon={<IconAlertCircle size={14} />} color="red" p="xs">
          {error}
        </Alert>
      )}
      <Stack gap="xs">
        <Group grow>
          <NumberInput
            label="Amount"
            value={form.amount}
            onChange={(val) => setForm((f) => ({ ...f, amount: val }))}
            min={0}
            decimalScale={2}
            prefix="$"
            size="xs"
          />
          <TextInput
            label="Description"
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
            size="xs"
          />
        </Group>
        <Group grow>
          <TextInput
            label="Date"
            type="date"
            value={form.date}
            onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
            size="xs"
          />
          <Select
            label="Category"
            placeholder="Select category"
            value={form.category}
            onChange={(val) => setForm((f) => ({ ...f, category: val }))}
            data={categories.map((c) => ({
              value: c.value,
              label: `Line ${c.scheduleELine} — ${c.label}`,
            }))}
            size="xs"
          />
        </Group>
        <Group grow>
          <Select
            label="Property"
            value={form.propertyId ? String(form.propertyId) : null}
            onChange={(val) => setForm((f) => ({ ...f, propertyId: val ? Number(val) : null }))}
            data={properties.map((p) => ({ value: String(p.id), label: p.name }))}
            clearable
            placeholder={
              proposal.propertyName ? `No match for "${proposal.propertyName}"` : '— None —'
            }
            size="xs"
          />
          <Select
            label="Payer"
            value={form.payerId ? String(form.payerId) : null}
            onChange={(val) => setForm((f) => ({ ...f, payerId: val ? Number(val) : null }))}
            data={payers.map((p) => ({ value: String(p.id), label: p.name }))}
            clearable
            placeholder={proposal.payerName ? `No match for "${proposal.payerName}"` : '— None —'}
            size="xs"
          />
        </Group>
        <Group gap="xs">
          <Button
            size="xs"
            leftSection={<IconCheck size={14} />}
            loading={saving}
            disabled={!form.date || !form.category || !form.amount}
            onClick={handleSave}
          >
            Save Expense
          </Button>
          <Button
            size="xs"
            variant="subtle"
            color="gray"
            leftSection={<IconX size={14} />}
            disabled={saving}
            onClick={handleDiscard}
          >
            Discard
          </Button>
        </Group>
      </Stack>
    </Paper>
  );
}

export default function Agent() {
  const [message, setMessage] = useState('');
  const [chat, setChat] = useState([]);
  const [loading, setLoading] = useState(false);
  const viewport = useRef(null);
  const queryClient = useQueryClient();
  // Monotonic counter so two messages in the same millisecond can't collide on React key.
  const messageIdRef = useRef(0);
  const nextMessageId = () => ++messageIdRef.current;

  const { data: categories = [] } = useQuery({
    queryKey: queryKeys.categories,
    queryFn: getExpenseCategories,
  });
  const { data: properties = [] } = useQuery({
    queryKey: queryKeys.properties,
    queryFn: getProperties,
  });
  const { data: payers = [] } = useQuery({ queryKey: queryKeys.payers, queryFn: getPayers });

  useEffect(() => {
    if (viewport.current)
      viewport.current.scrollTo({ top: viewport.current.scrollHeight, behavior: 'smooth' });
  }, [chat, loading]);

  const handleExpenseSaved = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
    queryClient.invalidateQueries({ queryKey: queryKeys.totalExpenses });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!message.trim() || loading) return;
    const userMsg = message.trim();
    setMessage('');
    setChat((c) => [...c, { id: nextMessageId(), role: 'user', text: userMsg }]);
    setLoading(true);
    try {
      const res = await submitExpenseToAgent(userMsg);
      setChat((c) => [
        ...c,
        {
          id: nextMessageId(),
          role: 'assistant',
          text: res.message,
          proposal: res.proposedExpense,
        },
      ]);
    } catch (err) {
      const messageText = getErrorMessage(
        err,
        'I could not process that request. Please retry or save the expense manually.'
      );
      setChat((c) => [
        ...c,
        { id: nextMessageId(), role: 'assistant', text: messageText, isError: true },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack gap="lg">
      <div>
        <Title order={2} mb={4}>
          AI Expense Agent
        </Title>
        <Text c="dimmed" size="sm">
          Describe an expense in natural language, then review and confirm before it's saved.
        </Text>
      </div>

      <Card
        withBorder
        p={0}
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: 'calc(100vh - 200px)',
          minHeight: 420,
        }}
      >
        {/* Example prompts — hidden once the conversation starts */}
        {chat.length === 0 && (
          <Card.Section withBorder p="sm" style={{ background: 'var(--mantine-color-gray-0)' }}>
            <Text size="xs" fw={600} c="dimmed" mb={6}>
              Example prompts:
            </Text>
            <Group gap="xs" wrap="wrap">
              {EXAMPLES.map((ex, i) => (
                <UnstyledButton
                  key={i}
                  onClick={() => setMessage(ex)}
                  aria-label={`Use example prompt: ${ex}`}
                  style={{
                    maxWidth: 300,
                    padding: '4px 10px',
                    borderRadius: 'var(--mantine-radius-xl)',
                    background: 'var(--mantine-color-blue-0)',
                    color: 'var(--mantine-color-blue-7)',
                  }}
                >
                  <Text size="xs" truncate>
                    {ex.length > 50 ? ex.slice(0, 50) + '…' : ex}
                  </Text>
                </UnstyledButton>
              ))}
            </Group>
          </Card.Section>
        )}

        {/* Chat messages */}
        <ScrollArea flex={1} p="md" viewportRef={viewport}>
          {chat.length === 0 && (
            <Stack align="center" justify="center" h={200} gap="xs">
              <IconRobot size={40} color="var(--mantine-color-gray-4)" />
              <Text c="dimmed" size="sm">
                Start by describing an expense above
              </Text>
            </Stack>
          )}
          <Stack gap="md">
            {chat.map((msg) => (
              <Group key={msg.id} justify={msg.role === 'user' ? 'flex-end' : 'flex-start'}>
                <Paper
                  p="sm"
                  radius="md"
                  maw="75%"
                  bg={msg.role === 'user' ? 'blue.6' : msg.isError ? 'red.0' : 'gray.1'}
                  style={{
                    borderBottomRightRadius: msg.role === 'user' ? 2 : undefined,
                    borderBottomLeftRadius: msg.role === 'assistant' ? 2 : undefined,
                  }}
                >
                  <Text size="sm" c={msg.role === 'user' ? 'white' : msg.isError ? 'red' : 'dark'}>
                    {msg.text}
                  </Text>
                  {msg.isError && (
                    <Alert
                      mt="xs"
                      variant="light"
                      color="red"
                      icon={<IconAlertCircle size={14} />}
                      title="Try this"
                    >
                      Check your request has amount, category context, and date; then retry.
                    </Alert>
                  )}
                  {msg.proposal && (
                    <ProposalCard
                      proposal={msg.proposal}
                      categories={categories}
                      properties={properties}
                      payers={payers}
                      onSaved={handleExpenseSaved}
                      onDiscarded={() => {}}
                    />
                  )}
                </Paper>
              </Group>
            ))}
            {loading && (
              <Group justify="flex-start">
                <Paper p="sm" radius="md" bg="gray.1" style={{ borderBottomLeftRadius: 2 }}>
                  <Group gap="xs">
                    <Loader size="xs" />
                    <Text size="sm" c="dimmed">
                      Thinking...
                    </Text>
                  </Group>
                </Paper>
              </Group>
            )}
          </Stack>
        </ScrollArea>

        {/* Input */}
        <Card.Section withBorder p="sm">
          <form onSubmit={handleSubmit}>
            <Group gap="xs">
              <TextInput
                flex={1}
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder="Describe an expense (e.g. 'Paid $200 for roof repair at Oak St property')"
                disabled={loading}
              />
              <Button
                type="submit"
                disabled={loading || !message.trim()}
                rightSection={<IconSend size={16} />}
              >
                Send
              </Button>
            </Group>
          </form>
        </Card.Section>
      </Card>
    </Stack>
  );
}
