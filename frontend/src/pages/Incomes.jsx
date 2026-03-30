import React, { useEffect, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, NumberInput, Select, Table, Text, Loader, Center, ActionIcon } from '@mantine/core'
import { IconPencil, IconTrash } from '@tabler/icons-react'
import { getIncomes, createIncome, updateIncome, deleteIncome, getProperties } from '../api/index.js'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], source: '', property: null }

export default function Incomes() {
  const [incomes, setIncomes] = useState([])
  const [properties, setProperties] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getIncomes().then(setIncomes).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getProperties().then(setProperties)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    const data = { ...form, amount: parseFloat(form.amount) }
    if (editing) await updateIncome(editing, data)
    else await createIncome(data)
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (income) => {
    setForm({ ...income })
    setEditing(income.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this income?')) { await deleteIncome(id); load() }
  }

  const set = (key) => (val) => setForm(f => ({ ...f, [key]: val }))

  if (loading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Button onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Income</Button>
      </Group>

      {showForm && (
        <Card withBorder p="lg">
          <Title order={4} mb="md">{editing ? 'Edit Income' : 'New Income'}</Title>
          <form onSubmit={handleSubmit}>
            <Stack gap="sm">
              <Group grow>
                <NumberInput label="Amount" value={form.amount} onChange={set('amount')} min={0} decimalScale={2} prefix="$" required />
                <TextInput label="Description" value={form.description} onChange={e => set('description')(e.target.value)} required />
              </Group>
              <Group grow>
                <TextInput label="Date" type="date" value={form.date} onChange={e => set('date')(e.target.value)} required />
                <TextInput label="Source" value={form.source} onChange={e => set('source')(e.target.value)} />
              </Group>
              <Select
                label="Property"
                value={form.property?.id ? String(form.property.id) : null}
                onChange={val => setForm(f => ({ ...f, property: val ? { id: Number(val) } : null }))}
                data={properties.map(p => ({ value: String(p.id), label: p.name }))}
                clearable
                placeholder="— None —"
              />
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
              {['Date', 'Description', 'Source', 'Property', 'Amount', 'Actions'].map(h => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {incomes.length === 0 ? (
              <Table.Tr><Table.Td colSpan={6}><Text ta="center" c="dimmed" py="xl">No income records yet</Text></Table.Td></Table.Tr>
            ) : incomes.map(i => (
              <Table.Tr key={i.id}>
                <Table.Td>{i.date}</Table.Td>
                <Table.Td>{i.description}</Table.Td>
                <Table.Td c="dimmed">{i.source || '—'}</Table.Td>
                <Table.Td c="dimmed">{i.property?.name || '—'}</Table.Td>
                <Table.Td fw={600} c="green">+${Number(i.amount).toFixed(2)}</Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <ActionIcon variant="subtle" color="gray" onClick={() => handleEdit(i)}><IconPencil size={16} /></ActionIcon>
                    <ActionIcon variant="subtle" color="red" onClick={() => handleDelete(i.id)}><IconTrash size={16} /></ActionIcon>
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