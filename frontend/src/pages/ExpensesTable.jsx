import React from 'react';
import {
  ActionIcon,
  Badge,
  Group,
  ScrollArea,
  Table,
  Text,
  ThemeIcon,
  Tooltip,
} from '@mantine/core';
import {
  IconBrandOffice,
  IconPencil,
  IconPencilMinus,
  IconReceipt,
  IconTrash,
} from '@tabler/icons-react';
import { EXPENSE_SOURCE } from '../constants.js';
import { fmtCurrency } from '../utils/formatters.js';

export function ExpensesTable({
  visibleExpenses,
  categories,
  highlightId,
  filterPayerId,
  filterYear,
  filterText,
  onEdit,
  onDelete,
}) {
  return (
    <ScrollArea>
      <Table miw={960}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th w={90}>Date</Table.Th>
            <Table.Th w={130}>Property</Table.Th>
            <Table.Th w={150}>Payer</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th w={110} style={{ textAlign: 'right' }}>
              Amount
            </Table.Th>
            <Table.Th w={140}>Category</Table.Th>
            <Table.Th w={60} style={{ textAlign: 'center' }}>
              Source
            </Table.Th>
            <Table.Th w={72}>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {visibleExpenses.length === 0 ? (
            <Table.Tr>
              <Table.Td colSpan={8}>
                <Text ta="center" c="dimmed" py="xl" size="sm">
                  {filterPayerId || filterYear || filterText
                    ? 'No expenses match the current filters'
                    : 'No expense records yet'}
                </Text>
              </Table.Td>
            </Table.Tr>
          ) : (
            visibleExpenses.map((e) => (
              <Table.Tr
                key={e.id}
                style={{
                  background: highlightId === e.id ? 'var(--mantine-color-yellow-0)' : undefined,
                  transition: 'background 0.5s',
                }}
              >
                <Table.Td c="dimmed">{e.date}</Table.Td>
                <Table.Td c="dimmed">{e.property?.name || '—'}</Table.Td>
                <Table.Td fw={500}>{e.payer?.name || '—'}</Table.Td>
                <Table.Td c="dimmed">{e.description}</Table.Td>
                <Table.Td
                  fw={600}
                  c="red"
                  style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                >
                  -{fmtCurrency(e.amount)}
                </Table.Td>
                <Table.Td>
                  <Badge color="gray" variant="light" size="sm">
                    {categories.find((c) => c.value === e.category)?.label || e.category}
                  </Badge>
                </Table.Td>
                <Table.Td style={{ textAlign: 'center' }}>
                  {e.sourceType === EXPENSE_SOURCE.OUTLOOK_EMAIL ? (
                    <Tooltip label="Outlook Email">
                      <ThemeIcon variant="subtle" color="blue" size="md">
                        <IconBrandOffice size={18} />
                      </ThemeIcon>
                    </Tooltip>
                  ) : e.sourceType === EXPENSE_SOURCE.MANUAL ? (
                    <Tooltip label="Manual">
                      <ThemeIcon variant="subtle" color="gray" size="md">
                        <IconPencilMinus size={18} />
                      </ThemeIcon>
                    </Tooltip>
                  ) : e.sourceType === EXPENSE_SOURCE.RECEIPT ? (
                    <Tooltip label={e.receiptFileName ? `Receipt: ${e.receiptFileName}` : 'Receipt'}>
                      <ThemeIcon variant="subtle" color="teal" size="md">
                        <IconReceipt size={18} />
                      </ThemeIcon>
                    </Tooltip>
                  ) : (
                    <Text c="dimmed">—</Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <ActionIcon
                      variant="subtle"
                      color="gray"
                      onClick={() => onEdit(e)}
                      aria-label={`Edit expense ${e.description}`}
                    >
                      <IconPencil size={16} />
                    </ActionIcon>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      onClick={() => onDelete(e.id)}
                      aria-label={`Delete expense ${e.description}`}
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
