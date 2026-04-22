import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, ActionIcon, Badge } from '@mantine/core'
import { modals } from '@mantine/modals'
import { IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { getProperties, createProperty, updateProperty, deleteProperty, getPropertyTypes, getPropertyKeywords } from '../api/index.js'
import { useListField } from '../hooks/useListField.js'
import CollapsibleBadges from '../components/CollapsibleBadges.jsx'

const EMPTY_FORM = { name: '', address: '', type: 'SINGLE_FAMILY', notes: '', accounts: [] }

export default function Properties() {
  const [properties, setProperties] = useState([])
  const [types, setTypes] = useState([])
  const [keywordsByProperty, setKeywordsByProperty] = useState({})
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const accounts = useListField('accounts', form, setForm)

  const loadKeywords = () =>
    getPropertyKeywords().then(rows => {
      const map = {}
      rows.forEach(r => {
        if (!map[r.property.id]) map[r.property.id] = []
        map[r.property.id].push(r)
      })
      setKeywordsByProperty(map)
    })

  const load = () => getProperties().then(setProperties).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getPropertyTypes().then(setTypes)
    loadKeywords()
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (editing) await updateProperty(editing, form)
    else await createProperty(form)
    setForm(EMPTY_FORM)
    accounts.reset()
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (property) => {
    setForm({ name: property.name, address: property.address, type: property.type, notes: property.notes || '', accounts: property.accounts || [] })
    accounts.reset()
    setEditing(property.id)
    setShowForm(true)
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete property',
      children: <Text size="sm">This property will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => { await deleteProperty(id); load() },
    })
  }

  const set = (key) => (e) => setForm(f => ({ ...f, [key]: typeof e === 'string' ? e : e.target.value }))

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Properties</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); accounts.reset(); setEditing(null); setShowForm(true) }}>+ Add Property</Button>
      </Group>

      {showForm && (
        <Card withBorder p="lg">
          <Title order={4} mb="md">{editing ? 'Edit Property' : 'New Property'}</Title>
          <form onSubmit={handleSubmit}>
            <Stack gap="sm">
              <Group grow>
                <TextInput label="Name" value={form.name} onChange={set('name')} required />
                <TextInput label="Address" value={form.address} onChange={set('address')} required />
              </Group>
              <Group grow>
                <Select
                  label="Type"
                  value={form.type}
                  onChange={val => setForm(f => ({ ...f, type: val }))}
                  data={types.map(t => ({ value: t.value, label: t.label }))}
                />
                <TextInput label="Notes" value={form.notes} onChange={set('notes')} />
              </Group>
              <Stack gap={4}>
                <Text size="sm" fw={500}>Account Numbers</Text>
                <Group gap="xs">
                  <TextInput
                    placeholder="Add account number"
                    value={accounts.input}
                    onChange={e => accounts.setInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), accounts.add())}
                    style={{ flex: 1 }}
                  />
                  <Button variant="default" onClick={accounts.add}>Add</Button>
                </Group>
                {form.accounts.length > 0 && (
                  <Group gap={4} wrap="wrap">
                    {form.accounts.map(a => (
                      <Badge key={a} variant="outline" color="cyan" size="sm" rightSection={
                        <ActionIcon size={14} variant="transparent" color="cyan" onClick={() => accounts.remove(a)}><IconX size={10} /></ActionIcon>
                      }>{a}</Badge>
                    ))}
                  </Group>
                )}
              </Stack>
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
              {['Name', 'Address', 'Type', 'Notes', 'Accounts', 'Keywords', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {properties.length === 0 ? (
              <Table.Tr><Table.Td colSpan={7}><Text ta="center" c="dimmed" py="xl">No properties yet</Text></Table.Td></Table.Tr>
            ) : properties.map(p => (
              <Table.Tr key={p.id}>
                <Table.Td fw={600}>{p.name}</Table.Td>
                <Table.Td>{p.address}</Table.Td>
                <Table.Td c="dimmed">{types.find(t => t.value === p.type)?.label || p.type}</Table.Td>
                <Table.Td c="dimmed">{p.notes || '—'}</Table.Td>
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
                    items={keywordsByProperty[p.id]}
                    color="teal"
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