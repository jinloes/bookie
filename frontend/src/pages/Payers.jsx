import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, Badge, ActionIcon, Collapse, Anchor } from '@mantine/core'
import { IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { getPayers, createPayer, updatePayer, deletePayer, getPayerTypes, getPayerKeywords } from '../api/index.js'

function KeywordCell({ keywords, color }) {
  const [open, setOpen] = useState(false)
  if (keywords.length === 0) return <Text c="dimmed" size="sm">—</Text>
  return (
    <Stack gap={4}>
      <Anchor size="sm" onClick={() => setOpen(o => !o)}>
        {open ? 'Hide' : `Show ${keywords.length}`}
      </Anchor>
      <Collapse in={open}>
        <Group gap={4} wrap="wrap">
          {keywords.map(k => (
            <Badge key={k.keyword} variant="dot" color={color} size="sm" title={`${k.occurrences} occurrence${k.occurrences !== 1 ? 's' : ''}`}>
              {k.keyword}
            </Badge>
          ))}
        </Group>
      </Collapse>
    </Stack>
  )
}

function AliasCell({ aliases }) {
  const [open, setOpen] = useState(false)
  if (!aliases?.length) return <Text c="dimmed" size="sm">—</Text>
  return (
    <Stack gap={4}>
      <Anchor size="sm" onClick={() => setOpen(o => !o)}>
        {open ? 'Hide' : `Show ${aliases.length}`}
      </Anchor>
      <Collapse in={open}>
        <Group gap={4} wrap="wrap">
          {aliases.map(a => (
            <Badge key={a} variant="outline" color="orange" size="sm">{a}</Badge>
          ))}
        </Group>
      </Collapse>
    </Stack>
  )
}

function AccountCell({ accounts }) {
  const [open, setOpen] = useState(false)
  if (!accounts?.length) return <Text c="dimmed" size="sm">—</Text>
  return (
    <Stack gap={4}>
      <Anchor size="sm" onClick={() => setOpen(o => !o)}>
        {open ? 'Hide' : `Show ${accounts.length}`}
      </Anchor>
      <Collapse in={open}>
        <Group gap={4} wrap="wrap">
          {accounts.map(a => (
            <Badge key={a} variant="outline" color="cyan" size="sm">{a}</Badge>
          ))}
        </Group>
      </Collapse>
    </Stack>
  )
}

const EMPTY_FORM = { name: '', type: 'PERSON', aliases: [], accounts: [] }

export default function Payers() {
  const [payers, setPayers] = useState([])
  const [types, setTypes] = useState([])
  const [keywordsByPayerId, setKeywordsByPayerId] = useState({})
  const [form, setForm] = useState(EMPTY_FORM)
  const [aliasInput, setAliasInput] = useState('')
  const [accountInput, setAccountInput] = useState('')
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

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
    setAliasInput('')
    setAccountInput('')
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (payer) => {
    setForm({ name: payer.name, type: payer.type, aliases: payer.aliases || [], accounts: payer.accounts || [] })
    setAliasInput('')
    setAccountInput('')
    setEditing(payer.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this payer?')) { await deletePayer(id); load() }
  }

  const addAlias = () => {
    const trimmed = aliasInput.trim()
    if (!trimmed || form.aliases.includes(trimmed)) return
    setForm(f => ({ ...f, aliases: [...f.aliases, trimmed] }))
    setAliasInput('')
  }

  const removeAlias = (alias) => setForm(f => ({ ...f, aliases: f.aliases.filter(a => a !== alias) }))

  const addAccount = () => {
    const trimmed = accountInput.trim()
    if (!trimmed || form.accounts.includes(trimmed)) return
    setForm(f => ({ ...f, accounts: [...f.accounts, trimmed] }))
    setAccountInput('')
  }

  const removeAccount = (account) => setForm(f => ({ ...f, accounts: f.accounts.filter(a => a !== account) }))

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Payers</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setAliasInput(''); setAccountInput(''); setEditing(null); setShowForm(true) }}>+ Add Payer</Button>
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
                  value={aliasInput}
                  onChange={e => setAliasInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addAlias() } }}
                  style={{ flex: 1 }}
                />
                <Button variant="default" onClick={addAlias}>Add</Button>
              </Group>
              {form.aliases.length > 0 && (
                <Group gap={4} wrap="wrap">
                  {form.aliases.map(a => (
                    <Badge key={a} variant="outline" color="orange" rightSection={
                      <ActionIcon size="xs" variant="transparent" onClick={() => removeAlias(a)}>
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
              {form.accounts.length > 0 && (
                <Group gap={4} wrap="wrap">
                  {form.accounts.map(a => (
                    <Badge key={a} variant="outline" color="cyan" rightSection={
                      <ActionIcon size="xs" variant="transparent" onClick={() => removeAccount(a)}>
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
                  <AliasCell aliases={p.aliases} />
                </Table.Td>
                <Table.Td>
                  <AccountCell accounts={p.accounts} />
                </Table.Td>
                <Table.Td>
                  <KeywordCell keywords={keywordsByPayerId[p.id] || []} color="violet" />
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