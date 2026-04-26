import React, { useMemo, useState } from 'react'
import { Stack, Group, Title, Button, Card, TextInput, Select, Table, Text, Loader, Center, ActionIcon, Badge } from '@mantine/core'
import { useForm } from '@mantine/form'
import { modals } from '@mantine/modals'
import { IconPencil, IconTrash, IconX } from '@tabler/icons-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getProperties, createProperty, updateProperty, deleteProperty, getPropertyTypes, getPropertyKeywords } from '../api/index.js'
import CollapsibleBadges from '../components/CollapsibleBadges.jsx'

const EMPTY_FORM = { name: '', address: '', type: 'SINGLE_FAMILY', notes: '', accounts: [] }

export default function Properties() {
  const queryClient = useQueryClient()
  const { data: properties = [], isLoading } = useQuery({ queryKey: ['properties'], queryFn: getProperties })
  const { data: types = [] } = useQuery({ queryKey: ['propertyTypes'], queryFn: getPropertyTypes })
  const { data: keywordsRaw = [] } = useQuery({ queryKey: ['propertyKeywords'], queryFn: getPropertyKeywords })

  const keywordsByProperty = useMemo(() => {
    const map = {}
    keywordsRaw.forEach(r => {
      if (!map[r.property.id]) map[r.property.id] = []
      map[r.property.id].push(r)
    })
    return map
  }, [keywordsRaw])

  const form = useForm({
    initialValues: EMPTY_FORM,
    validate: {
      name: (v) => !v?.trim() ? 'Name is required' : null,
      address: (v) => !v?.trim() ? 'Address is required' : null,
    },
  })
  const [accountInput, setAccountInput] = useState('')
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)

  const addAccount = () => {
    const trimmed = accountInput.trim()
    if (!trimmed || form.values.accounts.includes(trimmed)) return
    form.insertListItem('accounts', trimmed)
    setAccountInput('')
  }

  const handleSubmit = async (values) => {
    if (editing) await updateProperty(editing, values)
    else await createProperty(values)
    form.reset()
    setAccountInput('')
    setEditing(null)
    setShowForm(false)
    queryClient.invalidateQueries({ queryKey: ['properties'] })
  }

  const handleEdit = (property) => {
    form.setValues({
      name: property.name,
      address: property.address,
      type: property.type,
      notes: property.notes || '',
      accounts: property.accounts || [],
    })
    setAccountInput('')
    setEditing(property.id)
    setShowForm(true)
  }

  const handleDelete = (id) => {
    modals.openConfirmModal({
      title: 'Delete property',
      children: <Text size="sm">This property will be permanently deleted.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        await deleteProperty(id)
        queryClient.invalidateQueries({ queryKey: ['properties'] })
      },
    })
  }

  if (isLoading) return <Center h={200}><Loader /></Center>

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Properties</Title>
        <Button onClick={() => { form.reset(); setAccountInput(''); setEditing(null); setShowForm(true) }}>+ Add Property</Button>
      </Group>

      {showForm && (
        <Card withBorder p="lg">
          <Title order={4} mb="md">{editing ? 'Edit Property' : 'New Property'}</Title>
          <form onSubmit={form.onSubmit(handleSubmit)}>
            <Stack gap="sm">
              <Group grow>
                <TextInput label="Name" {...form.getInputProps('name')} required />
                <TextInput label="Address" {...form.getInputProps('address')} required />
              </Group>
              <Group grow>
                <Select
                  label="Type"
                  {...form.getInputProps('type')}
                  data={types.map(t => ({ value: t.value, label: t.label }))}
                />
                <TextInput label="Notes" {...form.getInputProps('notes')} />
              </Group>
              <Stack gap={4}>
                <Text size="sm" fw={500}>Account Numbers</Text>
                <Group gap="xs">
                  <TextInput
                    placeholder="Add account number"
                    value={accountInput}
                    onChange={e => setAccountInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addAccount())}
                    style={{ flex: 1 }}
                  />
                  <Button variant="default" onClick={addAccount}>Add</Button>
                </Group>
                {form.values.accounts.length > 0 && (
                  <Group gap={4} wrap="wrap">
                    {form.values.accounts.map((a, i) => (
                      <Badge key={a} variant="outline" color="cyan" size="sm" rightSection={
                        <ActionIcon size={14} variant="transparent" color="cyan" onClick={() => form.removeListItem('accounts', i)}><IconX size={10} /></ActionIcon>
                      }>{a}</Badge>
                    ))}
                  </Group>
                )}
              </Stack>
              <Group>
                <Button type="submit">Save</Button>
                <Button variant="default" onClick={() => { setShowForm(false); setEditing(null); form.reset(); setAccountInput('') }}>Cancel</Button>
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
