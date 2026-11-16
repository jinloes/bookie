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
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';

export function ExpensesTable({ table }) {
  const hasFilters = Object.values(table.activeFilters ?? {}).some(Boolean);

  return (
    <ScrollArea>
      <Table miw={840}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th w={105}>Date</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th w={210}>Activity</Table.Th>
            <Table.Th w={165}>Category</Table.Th>
            <Table.Th w={80} style={{ textAlign: 'center' }}>
              Source
            </Table.Th>
            <Table.Th w={120} style={{ textAlign: 'right' }}>
              Amount
            </Table.Th>
            <Table.Th w={84}>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {table.expenses.length === 0 ? (
            <Table.Tr>
              <Table.Td colSpan={7}>
                <Text ta="center" c="dimmed" py="xl" size="sm">
                  {hasFilters ? 'No expenses match the current filters' : 'No expense records yet'}
                </Text>
              </Table.Td>
            </Table.Tr>
          ) : (
            table.expenses.map((expense) => {
              const propertyName = expense.property?.name || expense.activity?.property?.name;
              return (
                <Table.Tr
                  key={expense.id}
                  style={{
                    background: table.highlightId === expense.id ? COLORS.ROW_HIGHLIGHT : undefined,
                    transition: 'background 0.5s',
                  }}
                >
                  <Table.Td c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {expense.date}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={500}>
                      {expense.description}
                    </Text>
                    {expense.payer?.name && (
                      <Text size="xs" c="dimmed">
                        {expense.payer.name}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{expense.activity?.name || 'Needs classification'}</Text>
                    <Group gap={4} wrap="nowrap">
                      <Text size="xs" c="dimmed">
                        {expense.activity?.owner?.name || '—'}
                      </Text>
                      {propertyName && (
                        <>
                          <Text size="xs" c="dimmed">
                            ·
                          </Text>
                          <Text size="xs" c="dimmed">
                            {propertyName}
                          </Text>
                        </>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Badge color="gray" variant="light" size="sm">
                      {expense.neutralCategory?.label || expense.category}
                    </Badge>
                  </Table.Td>
                  <Table.Td style={{ textAlign: 'center' }}>
                    {expense.sourceType === EXPENSE_SOURCE.OUTLOOK_EMAIL ? (
                      <Tooltip label="Outlook Email">
                        <ThemeIcon variant="subtle" color="blue" size="md">
                          <IconBrandOffice size={18} />
                        </ThemeIcon>
                      </Tooltip>
                    ) : expense.sourceType === EXPENSE_SOURCE.MANUAL ? (
                      <Tooltip label="Manual">
                        <ThemeIcon variant="subtle" color="gray" size="md">
                          <IconPencilMinus size={18} />
                        </ThemeIcon>
                      </Tooltip>
                    ) : expense.sourceType === EXPENSE_SOURCE.RECEIPT ? (
                      <Tooltip
                        label={
                          expense.receiptFileName
                            ? `Receipt: ${expense.receiptFileName}`
                            : 'Receipt'
                        }
                      >
                        <ThemeIcon variant="subtle" color="teal" size="md">
                          <IconReceipt size={18} />
                        </ThemeIcon>
                      </Tooltip>
                    ) : (
                      <Text c="dimmed">—</Text>
                    )}
                  </Table.Td>
                  <Table.Td
                    fw={600}
                    c="red"
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    -{fmtCurrency(expense.amount)}
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs" wrap="nowrap">
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        size="md"
                        onClick={() => table.onEdit(expense)}
                        aria-label={`Edit expense ${expense.description}`}
                      >
                        <IconPencil size={16} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="md"
                        onClick={() => table.onDelete(expense.id)}
                        aria-label={`Delete expense ${expense.description}`}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              );
            })
          )}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}
