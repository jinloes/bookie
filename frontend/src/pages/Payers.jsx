import React, { useMemo, useState } from 'react'
import { Stack, Group, Title, Button, Drawer, TextInput, Select, Table, Text, Loader, Center, Badge, ActionIcon } from '@mantine/core'
import { useForm } from '@mantine/form'
import { modals } from '@mantine/modals'
import { notifications } from '@mantine/notifications'
import { IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
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
    if (editing) await updatePayer(editing, values)
    else await createPayer(values)
    notifications.show({ title: editing ? 'Payer updated' : 'Payer saved', color: 'green' })
    form.reset()
    setAliasInput('')
    setAccountInput('')
    setEditing(null)
    setShowForm(false)
    queryClient.invalidateQueries({ queryKey: ['payers'] })
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
        await deletePayer(id)
        queryClient.invalidateQueries({ queryKey: ['payers'] })
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
      >
        <form onSubmit={form.onSubmit(handleSubmit)}>
          <Stack gap="sm">
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
                  <Badge key={a} variant="outline" color="orange" rightSection={
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
                  <Badge key={a} variant="outline" color="cyan" rightSection={
                    <ActionIcon size="xs" variant="transparent" onClick={() => form.removeListItem('accounts', i)}>
                      <IconX size={10} />
                    </ActionIcon>
                  }>{a}</Badge>
                ))}
              </Group>
            )}
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={cancelForm}>Cancel</Button>
            </Group>
          </Stack>
        </form>
      </Drawer>

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            {['Name', 'Type', 'Aliases', 'Accounts', 'Keywords', 'Actions'].map(h => (
              <Table.Th key={h}>{h}</Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {payers.length === 0 ? (
            <Table.Tr><Table.Td colSpan={6}><Text ta="center" c="dimmed" py="xl">No payers yet</Text></Table.Td></Table.Tr>
          ) : payers.map(p => (
            <Table.Tr key={p.id}>
              <Table.Td fw={600}>{p.name}</Table.Td>
              <Table.Td>
                <Badge color={p.type === 'COMPANY' ? 'blue' : 'green'} variant="light">
                  {p.type === 'COMPANY' ? 'Company' : 'Person'}
                </Badge>
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={p.aliases}
                  color="orange"
                  getKey={a => a}
                  getLabel={a => a}
                />
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={p.accounts}
                  color="cyan"
                  getKey={a => a}
                  getLabel={a => a}
                />
              </Table.Td>
              <Table.Td>
                <CollapsibleBadges
                  items={keywordsByPayerId[p.id]}
                  color="violet"
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
