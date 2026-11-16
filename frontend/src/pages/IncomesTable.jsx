import React from 'react';
import { ActionIcon, Badge, Group, ScrollArea, Table, Text } from '@mantine/core';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';

export function IncomesTable({ table }) {
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
            <Table.Th w={130}>Source</Table.Th>
            <Table.Th w={120} style={{ textAlign: 'right' }}>
              Amount
            </Table.Th>
            <Table.Th w={84}>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {table.incomes.length === 0 ? (
            <Table.Tr>
              <Table.Td colSpan={7}>
                <Text ta="center" c="dimmed" py="xl" size="sm">
                  {hasFilters
                    ? 'No income records match the current filters'
                    : 'No income records yet. Import a Venmo CSV or bank statement, or add income manually.'}
                </Text>
              </Table.Td>
            </Table.Tr>
          ) : (
            table.incomes.map((income) => {
              const propertyName = income.property?.name || income.activity?.property?.name;
              return (
                <Table.Tr
                  key={income.id}
                  style={{
                    background: table.highlightId === income.id ? COLORS.ROW_HIGHLIGHT : undefined,
                    transition: 'background 0.5s',
                  }}
                >
                  <Table.Td c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {income.date}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={500}>
                      {income.description}
                    </Text>
                    {income.payer?.name && (
                      <Text size="xs" c="dimmed">
                        {income.payer.name}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{income.activity?.name || 'Needs classification'}</Text>
                    <Group gap={4} wrap="nowrap">
                      <Text size="xs" c="dimmed">
                        {income.activity?.owner?.name || '—'}
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
                      {income.neutralCategory?.label || 'Unclassified'}
                    </Badge>
                  </Table.Td>
                  <Table.Td c="dimmed">{income.source || '—'}</Table.Td>
                  <Table.Td
                    fw={600}
                    c="green"
                    style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  >
                    +{fmtCurrency(income.amount)}
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs" wrap="nowrap">
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        size="md"
                        onClick={() => table.onEdit(income)}
                        aria-label={`Edit income ${income.description}`}
                      >
                        <IconPencil size={16} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="md"
                        onClick={() => table.onDelete(income.id)}
                        aria-label={`Delete income ${income.description}`}
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
