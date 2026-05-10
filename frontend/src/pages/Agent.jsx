import React, { useState, useRef, useEffect } from 'react'
import { Stack, Group, Title, Text, Button, TextInput, Card, Badge, Paper, ScrollArea, SimpleGrid, Loader } from '@mantine/core'
import { IconRobot, IconSend } from '@tabler/icons-react'
import { submitExpenseToAgent } from '../api/index.js'

const EXAMPLES = [
  'I paid $250 for plumbing repairs at Oak Street property last Monday',
  'Spent $120 on landscaping for the Main St duplex today',
  'Property insurance payment of $890 for Maple Ave on March 15th',
  'Paid $75 for cleaning supplies for the downtown apartment yesterday',
  '$1,500 mortgage payment for Oak Street property today',
]

export default function Agent() {
  const [message, setMessage] = useState('')
  const [chat, setChat] = useState([])
  const [loading, setLoading] = useState(false)
  const viewport = useRef(null)

  useEffect(() => {
    if (viewport.current) viewport.current.scrollTo({ top: viewport.current.scrollHeight, behavior: 'smooth' })
  }, [chat, loading])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!message.trim() || loading) return
    const userMsg = message.trim()
    setMessage('')
    setChat(c => [...c, { id: Date.now(), role: 'user', text: userMsg }])
    setLoading(true)
    try {
      const res = await submitExpenseToAgent(userMsg)
      setChat(c => [...c, { id: Date.now(), role: 'assistant', text: res.message, expense: res.createdExpense }])
    } catch (err) {
      setChat(c => [...c, { id: Date.now(), role: 'assistant', text: `Error: ${err.message}`, isError: true }])
    } finally {
      setLoading(false)
    }
  }

  return (
    <Stack gap="lg">
      <div>
        <Title order={2} mb={4}>AI Expense Agent</Title>
        <Text c="dimmed" size="sm">Describe an expense in natural language and the AI will create it automatically.</Text>
      </div>

      <Card withBorder p={0} style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 200px)', minHeight: 420 }}>
        {/* Example prompts — hidden once the conversation starts */}
        {chat.length === 0 && (
          <Card.Section withBorder p="sm" style={{ background: 'var(--mantine-color-gray-0)' }}>
            <Text size="xs" fw={600} c="dimmed" mb={6}>Example prompts:</Text>
            <Group gap="xs" wrap="wrap">
              {EXAMPLES.map((ex, i) => (
                <Badge
                  key={i}
                  variant="light"
                  color="blue"
                  style={{ cursor: 'pointer', maxWidth: 300 }}
                  onClick={() => setMessage(ex)}
                >
                  <Text size="xs" truncate>{ex.length > 50 ? ex.slice(0, 50) + '…' : ex}</Text>
                </Badge>
              ))}
            </Group>
          </Card.Section>
        )}

        {/* Chat messages */}
        <ScrollArea flex={1} p="md" viewportRef={viewport}>
          {chat.length === 0 && (
            <Stack align="center" justify="center" h={200} gap="xs">
              <IconRobot size={40} color="var(--mantine-color-gray-4)" />
              <Text c="dimmed" size="sm">Start by describing an expense above</Text>
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
                  {msg.expense && (
                    <Paper mt="xs" p="xs" radius="sm" withBorder bg="white">
                      <Text size="xs" fw={700} c="dark" mb={4}>Expense Created:</Text>
                      <SimpleGrid cols={2} spacing={2}>
                        <Text size="xs" c="dimmed">Amount:</Text>
                        <Text size="xs" fw={600} c="red">${Number(msg.expense.amount).toFixed(2)}</Text>
                        <Text size="xs" c="dimmed">Category:</Text>
                        <Text size="xs">{msg.expense.category}</Text>
                        <Text size="xs" c="dimmed">Date:</Text>
                        <Text size="xs">{msg.expense.date}</Text>
                        <Text size="xs" c="dimmed">Property:</Text>
                        <Text size="xs">{msg.expense.propertyName || '—'}</Text>
                      </SimpleGrid>
                    </Paper>
                  )}
                </Paper>
              </Group>
            ))}
            {loading && (
              <Group justify="flex-start">
                <Paper p="sm" radius="md" bg="gray.1" style={{ borderBottomLeftRadius: 2 }}>
                  <Group gap="xs">
                    <Loader size="xs" />
                    <Text size="sm" c="dimmed">Thinking...</Text>
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
                onChange={e => setMessage(e.target.value)}
                placeholder="Describe an expense (e.g. 'Paid $200 for roof repair at Oak St property')"
                disabled={loading}
              />
              <Button type="submit" disabled={loading || !message.trim()} rightSection={<IconSend size={16} />}>
                Send
              </Button>
            </Group>
          </form>
        </Card.Section>
      </Card>
    </Stack>
  )
}