import React from 'react';
import { ActionIcon, Group, ScrollArea, Table, Text } from '@mantine/core';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { fmtCurrency } from '../utils/formatters.js';

export function IncomesTable({
  visibleIncomes,
  highlightId,
  filterYear,
  filterText,
  onEdit,
  onDelete,
}) {
  return (
    <ScrollArea>
      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th w={90}>Date</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th w={130}>Source</Table.Th>
            <Table.Th w={150}>Payer</Table.Th>
            <Table.Th w={150}>Property</Table.Th>
            <Table.Th w={110} style={{ textAlign: 'right' }}>
              Amount
            </Table.Th>
            <Table.Th w={72}>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {visibleIncomes.length === 0 ? (
            <Table.Tr>
              <Table.Td colSpan={7}>
                <Text ta="center" c="dimmed" py="xl" size="sm">
                  {filterYear || filterText
                    ? 'No income records match the current filters'
                    : 'No income records yet. Import a Venmo CSV or add income manually.'}
                </Text>
              </Table.Td>
            </Table.Tr>
          ) : (
            visibleIncomes.map((i) => (
              <Table.Tr
                key={i.id}
                style={{
                  background: highlightId === i.id ? 'var(--mantine-color-yellow-0)' : undefined,
                  transition: 'background 0.5s',
                }}
              >
                <Table.Td c="dimmed">{i.date}</Table.Td>
                <Table.Td>{i.description}</Table.Td>
                <Table.Td c="dimmed">{i.source || '—'}</Table.Td>
                <Table.Td c="dimmed">{i.payer?.name || '—'}</Table.Td>
                <Table.Td c="dimmed">{i.property?.name || '—'}</Table.Td>
                <Table.Td
                  fw={600}
                  c="green"
                  style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                >
                  +{fmtCurrency(i.amount)}
                </Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <ActionIcon
                      variant="subtle"
                      color="gray"
                      onClick={() => onEdit(i)}
                      aria-label={`Edit income ${i.description}`}
                    >
                      <IconPencil size={16} />
                    </ActionIcon>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      onClick={() => onDelete(i.id)}
                      aria-label={`Delete income ${i.description}`}
                    >
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))
          )}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}
