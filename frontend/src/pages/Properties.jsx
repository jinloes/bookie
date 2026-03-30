import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, ActionIcon } from '@mantine/core'
import { IconPencil, IconTrash } from '@tabler/icons-react'
import { getProperties, createProperty, updateProperty, deleteProperty, getPropertyTypes } from '../api/index.js'

const EMPTY_FORM = { name: '', address: '', type: 'SINGLE_FAMILY', notes: '' }

export default function Properties() {
  const [properties, setProperties] = useState([])
  const [types, setTypes] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getProperties().then(setProperties).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getPropertyTypes().then(setTypes)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (editing) await updateProperty(editing, form)
    else await createProperty(form)
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (property) => {
    setForm({ name: property.name, address: property.address, type: property.type, notes: property.notes || '' })
    setEditing(property.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this property?')) { await deleteProperty(id); load() }
  }

  const set = (key) => (e) => setForm(f => ({ ...f, [key]: typeof e === 'string' ? e : e.target.value }))

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Properties</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Property</Button>
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
              {['Name', 'Address', 'Type', 'Notes', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {properties.length === 0 ? (
              <Table.Tr><Table.Td colSpan={5}><Text ta="center" c="dimmed" py="xl">No properties yet</Text></Table.Td></Table.Tr>
            ) : properties.map(p => (
              <Table.Tr key={p.id}>
                <Table.Td fw={600}>{p.name}</Table.Td>
                <Table.Td>{p.address}</Table.Td>
                <Table.Td c="dimmed">{types.find(t => t.value === p.type)?.label || p.type}</Table.Td>
                <Table.Td c="dimmed">{p.notes || '—'}</Table.Td>
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