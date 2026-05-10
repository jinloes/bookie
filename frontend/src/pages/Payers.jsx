import React, { useMemo, useState } from 'react'
import { Stack, Group, Title, Button, Drawer, Box, TextInput, Select, Table, Text, Loader, Center, Badge, ActionIcon, Tooltip } from '@mantine/core'
import { useForm } from '@mantine/form'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import { IconPencil, IconTrash, IconX, IconSearch, IconInfoCircle } from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSessionState } from '../hooks/useSessionState.js'
import { getPayers, createPayer, updatePayer, deletePayer, getPayerTypes, getPayerKeywords } from '../api/index.js'
import CollapsibleBadges from '../components/CollapsibleBadges.jsx'

const EMPTY_FORM = { name: '', type: 'PERSON', aliases: [], accounts: [] }

export default function Payers() {
  const queryClient = useQueryClient()
  const { data: payers = [], isLoading } = useQuery({ queryKey: ['payers'], queryFn: getPayers })
  const { data: types = [] } = useQuery({ queryKey: ['payerTypes'], queryFn: getPayerTypes })
  const { data: keywordsRaw = [] } = useQuery({ queryKey: ['payerKeywords'], queryFn: getPayerKeywords })

  const keywordsByPayerId = useMemo(() => {
    const map = {}
    keywordsRaw.forEach(r => {
      const id = r.payer?.id
      if (id == null) return
      if (!map[id]) map[id] = []
      map[id].push(r)
    })
    return map
  }, [keywordsRaw])

  const form = useForm({
    initialValues: EMPTY_FORM,
    validate: { name: (v) => !v?.trim() ? 'Name is required' : null },
  })
  const [aliasInput, setAliasInput] = useState('')
  const [accountInput, setAccountInput] = useState('')
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [filterText, setFilterText] = useSessionState('payers.filterText', '')

  const visiblePayers = useMemo(() => {
    if (!filterText) return payers
    const q = filterText.toLowerCase()
    return payers.filter(p =>
      p.name?.toLowerCase().includes(q) ||
      p.aliases?.some(a => a.toLowerCase().includes(q)) ||
      p.accounts?.some(a => a.toLowerCase().includes(q))
    )
  }, [payers, filterText])

  const addAlias = () => {
    const trimmed = aliasInput.trim()
    if (!trimmed || form.values.aliases.includes(trimmed)) return
    form.insertListItem('aliases', trimmed)
    setAliasInput('')
  }

  const addAccount = () => {
    const trimmed = accountInput.trim()
    if (!trimmed || form.values.accounts.includes(trimmed)) return
    form.insertListItem('accounts', trimmed)
    setAccountInput('')
  }

  const cancelForm = () => {
    setShowForm(false)
    setEditing(null)
    form.reset()
    setAliasInput('')
    setAccountInput('')
  }

  const handleSubmit = async (values) => {
    try {
      if (editing) await updatePayer(editing, values)
      else await createPayer(values)
      notifications.show({ title: editing ? 'Payer updated' : 'Payer saved', color: 'green' })
      form.reset()
      setAliasInput('')
      setAccountInput('')
      setEditing(null)
      setShowForm(false)
      queryClient.invalidateQueries({ queryKey: ['payers'] })
    } catch (err) {
      notifications.show({ title: 'Save failed', message: err.message, color: 'red' })
    }
  }

  const handleEdit = (payer) => {
    form.setValues({
      name: payer.name,
      type: payer.type,
      aliases: payer.aliases || [],
      accounts: payer.accounts || [],
    })
    setAliasInput('')
    setAccountInput('')
    setEditing(payer.id)
    setShowForm(true)
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete payer',
      children: <Text size="sm">This payer will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deletePayer(id)
          queryClient.invalidateQueries({ queryKey: ['payers'] })
        } catch (err) {
          notifications.show({ title: 'Delete failed', message: err.message, color: 'red' })
        }
      },
    })
  }

  if (isLoading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Payers</Title>
        <Button onClick={() => { form.reset(); setAliasInput(''); setAccountInput(''); setEditing(null); setShowForm(true) }}>+ Add Payer</Button>
      </Group>

      <Drawer
        opened={showForm}
        onClose={cancelForm}
        title={editing ? 'Edit Payer' : 'New Payer'}
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        <form onSubmit={form.onSubmit(handleSubmit)} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Group grow>
              <TextInput label="Name" {...form.getInputProps('name')} required />
              <Select
                label="Type"
                {...form.getInputProps('type')}
                data={types.map(t => ({ value: t.value, label: t.label }))}
              />
            </Group>
            <Group align="flex-end">
              <TextInput
                label="Aliases"
                description="Alternate names the model may use (e.g. PG&E)"
                placeholder="Add alias"
                value={aliasInput}
                onChange={e => setAliasInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addAlias() } }}
                style={{ flex: 1 }}
              />
              <Button variant="default" onClick={addAlias}>Add</Button>
            </Group>
            {form.values.aliases.length > 0 && (
              <Group gap={4} wrap="wrap">
                {form.values.aliases.map((a, i) => (
                  <Badge key={a} variant="outline" color="gray" rightSection={
                    <ActionIcon size="xs" variant="transparent" onClick={() => form.removeListItem('aliases', i)}>
                      <IconX size={10} />
                    </ActionIcon>
                  }>{a}</Badge>
                ))}
              </Group>
            )}
            <Group align="flex-end">
              <TextInput
                label="Account Numbers"
                description="Account numbers used to auto-identify this payer from emails"
                placeholder="Add account number"
                value={accountInput}
                onChange={e => setAccountInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addAccount() } }}
                style={{ flex: 1 }}
              />
              <Button variant="default" onClick={addAccount}>Add</Button>
            </Group>
            {form.values.accounts.length > 0 && (
              <Group gap={4} wrap="wrap">
                {form.values.accounts.map((a, i) => (
                  <Badge key={a} variant="outline" color="gray" rightSection={
                    <ActionIcon size="xs" variant="transparent" onClick={() => form.removeListItem('accounts', i)}>
                      <IconX size={10} />
                    </ActionIcon>
                  }>{a}</Badge>
                ))}
              </Group>
            )}
          </Stack>
          <Box pt="md" style={{ borderTop: '1px solid var(--mantine-color-gray-2)', flexShrink: 0 }}>
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={cancelForm}>Cancel</Button>
            </Group>
          </Box>
        </form>
      </Drawer>

      <Group mb="xs">
        <TextInput
          placeholder="Search payers…"
          value={filterText}
          onChange={e => setFilterText(e.target.value)}
          leftSection={<IconSearch size={14} />}
          size="xs"
          style={{ width: 220 }}
        />
      </Group>

      <Table>
        <Table.Thead>
          <Table.Tr>
            {['Name', 'Type', 'Aliases', 'Accounts'].map(h => (
              <Table.Th key={h}>{h}</Table.Th>
            ))}
            <Table.Th>
              <Group gap={4} wrap="nowrap">
                Auto-matched
                <Tooltip
                  label="Terms automatically learned from processed emails. Used to identify this payer in future transactions. Hover each term to see how many times it has been matched."
                  multiline
                  w={280}
                >
                  <IconInfoCircle size={13} style={{ color: 'var(--mantine-color-gray-4)', cursor: 'help', flexShrink: 0 }} />
                </Tooltip>
              </Group>
            </Table.Th>
            <Table.Th>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {visiblePayers.length === 0 ? (
            <Table.Tr>
              <Table.Td colSpan={6}>
                <Text ta="center" c="dimmed" py="xl" size="sm">
                  {filterText ? 'No payers match the current search' : 'No payers yet'}
                </Text>
              </Table.Td>
            </Table.Tr>
          ) : visiblePayers.map(p => (
            <Table.Tr key={p.id}>
              <Table.Td fw={500}>{p.name}</Table.Td>
              <Table.Td>
                <Badge color="gray" variant="light" size="sm">
                  {p.type === 'COMPANY' ? 'Company' : 'Person'}
                </Badge>
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={p.aliases}
                  color="gray"
                  getKey={a => a}
                  getLabel={a => a}
                />
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={p.accounts}
                  color="gray"
                  getKey={a => a}
                  getLabel={a => a}
                />
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={keywordsByPayerId[p.id]}
                  color="gray"
                  variant="dot"
                  getKey={k => k.keyword}
                  getLabel={k => k.keyword}
                  getTitle={k => `${k.occurrences} occurrence${k.occurrences !== 1 ? 's' : ''}`}
                />
              </Table.Td>
              <Table.Td>
                <Group gap="xs">
                  <ActionIcon variant="subtle" color="gray" onClick={() => handleEdit(p)}><IconPencil size={16} /></ActionIcon>
                  <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(p.id)}><IconTrash size={16} /></ActionIcon>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  )
}
