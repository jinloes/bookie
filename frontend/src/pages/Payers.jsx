import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, Badge, ActionIcon, Collapse, Anchor } from '@mantine/core'
import { IconPencil, IconTrash } from '@tabler/icons-react'
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

const EMPTY_FORM = { name: '', type: 'PERSON' }

export default function Payers() {
  const [payers, setPayers] = useState([])
  const [types, setTypes] = useState([])
  const [keywordsByPayer, setKeywordsByPayer] = useState({})
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const loadKeywords = () =>
    getPayerKeywords().then(rows => {
      const map = {}
      rows.forEach(r => {
        if (!map[r.payerName]) map[r.payerName] = []
        map[r.payerName].push(r)
      })
      setKeywordsByPayer(map)
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
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (payer) => {
    setForm({ name: payer.name, type: payer.type })
    setEditing(payer.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this payer?')) { await deletePayer(id); load() }
  }

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Payers</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Payer</Button>
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
              {['Name', 'Type', 'Keywords', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {payers.length === 0 ? (
              <Table.Tr><Table.Td colSpan={4}><Text ta="center" c="dimmed" py="xl">No payers yet</Text></Table.Td></Table.Tr>
            ) : payers.map(p => (
              <Table.Tr key={p.id}>
                <Table.Td fw={600}>{p.name}</Table.Td>
                <Table.Td>
                  <Badge color={p.type === 'COMPANY' ? 'blue' : 'green'} variant="light">
                    {p.type === 'COMPANY' ? 'Company' : 'Person'}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <KeywordCell keywords={keywordsByPayer[p.name] || []} color="violet" />
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