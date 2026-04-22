import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, Badge, ActionIcon } from '@mantine/core'
import { modals } from '@mantine/modals'
import { IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { getPayers, createPayer, updatePayer, deletePayer, getPayerTypes, getPayerKeywords } from '../api/index.js'
import { useListField } from '../hooks/useListField.js'
import CollapsibleBadges from '../components/CollapsibleBadges.jsx'

const EMPTY_FORM = { name: '', type: 'PERSON', aliases: [], accounts: [] }

export default function Payers() {
  const [payers, setPayers] = useState([])
  const [types, setTypes] = useState([])
  const [keywordsByPayerId, setKeywordsByPayerId] = useState({})
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const aliases = useListField('aliases', form, setForm)
  const accounts = useListField('accounts', form, setForm)

  const loadKeywords = () =>
    getPayerKeywords().then(rows => {
      const map = {}
      rows.forEach(r => {
        const id = r.payer?.id
        if (id == null) return
        if (!map[id]) map[id] = []
        map[id].push(r)
      })
      setKeywordsByPayerId(map)
    })

  const load = () => getPayers().then(setPayers).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getPayerTypes().then(setTypes)
    loadKeywords()
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (editing) await updatePayer(editing, form)
    else await createPayer(form)
    setForm(EMPTY_FORM)
    aliases.reset()
    accounts.reset()
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (payer) => {
    setForm({ name: payer.name, type: payer.type, aliases: payer.aliases || [], accounts: payer.accounts || [] })
    aliases.reset()
    accounts.reset()
    setEditing(payer.id)
    setShowForm(true)
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete payer',
      children: <Text size="sm">This payer will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => { await deletePayer(id); load() },
    })
  }

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Payers</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); aliases.reset(); accounts.reset(); setEditing(null); setShowForm(true) }}>+ Add Payer</Button>
      </Group>

      {showForm && (
        <Card withBorder p="lg">
          <Title order={4} mb="md">{editing ? 'Edit Payer' : 'New Payer'}</Title>
          <form onSubmit={handleSubmit}>
            <Stack gap="sm">
              <Group grow>
                <TextInput label="Name" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
                <Select
                  label="Type"
                  value={form.type}
                  onChange={val => setForm(f => ({ ...f, type: val }))}
                  data={types.map(t => ({ value: t.value, label: t.label }))}
                />
              </Group>
              <Group align="flex-end">
                <TextInput
                  label="Aliases"
                  description="Alternate names the model may use (e.g. PG&E)"
                  placeholder="Add alias"
                  value={aliases.input}
                  onChange={e => aliases.setInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); aliases.add() } }}
                  style={{ flex: 1 }}
                />
                <Button variant="default" onClick={aliases.add}>Add</Button>
              </Group>
              {form.aliases.length > 0 && (
                <Group gap={4} wrap="wrap">
                  {form.aliases.map(a => (
                    <Badge key={a} variant="outline" color="orange" rightSection={
                      <ActionIcon size="xs" variant="transparent" onClick={() => aliases.remove(a)}>
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
                  value={accounts.input}
                  onChange={e => accounts.setInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); accounts.add() } }}
                  style={{ flex: 1 }}
                />
                <Button variant="default" onClick={accounts.add}>Add</Button>
              </Group>
              {form.accounts.length > 0 && (
                <Group gap={4} wrap="wrap">
                  {form.accounts.map(a => (
                    <Badge key={a} variant="outline" color="cyan" rightSection={
                      <ActionIcon size="xs" variant="transparent" onClick={() => accounts.remove(a)}>
                        <IconX size={10} />
                      </ActionIcon>
                    }>{a}</Badge>
                  ))}
                </Group>
              )}
              <Group>
                <Button type="submit">Save</Button>
                <Button variant="default" onClick={() => { setShowForm(false); setEditing(null) }}>Cancel</Button>
              </Group>
            </Stack>
          </form>
        </Card>
      )}

      <Card withBorder p={0}>
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
      </Card>
    </Stack>
  )
}