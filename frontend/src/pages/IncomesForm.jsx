import React from 'react';
import {
  Box,
  Button,
  Drawer,
  Group,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { COLORS } from '../designTokens.js';

export function IncomesForm({ incomeForm }) {
  return (
    <Drawer
      opened={incomeForm.opened}
      onClose={incomeForm.onCancel}
      title={incomeForm.editing ? 'Edit Income' : 'New Income'}
      position="right"
      size="lg"
      styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
    >
      <form
        onSubmit={incomeForm.form.onSubmit(incomeForm.onSubmit)}
        style={{ display: 'flex', flexDirection: 'column', flex: 1 }}
      >
        <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
          <Group grow>
            <NumberInput
              label="Amount"
              {...incomeForm.form.getInputProps('amount')}
              min={0}
              decimalScale={2}
              prefix="$"
              required
            />
            <TextInput
              label="Description"
              {...incomeForm.form.getInputProps('description')}
              required
            />
          </Group>
          <Group grow>
            <TextInput
              label="Date"
              type="date"
              {...incomeForm.form.getInputProps('date')}
              required
            />
            <TextInput label="Source" {...incomeForm.form.getInputProps('source')} />
          </Group>
          <Group grow>
            <Select
              label="Property"
              {...incomeForm.form.getInputProps('propertyId')}
              data={incomeForm.propertyOptions}
              clearable
              placeholder="— None —"
            />
            <Select
              label="Payer"
              {...incomeForm.form.getInputProps('payerId')}
              data={incomeForm.payerOptions}
              clearable
              searchable
              placeholder="— None —"
            />
          </Group>
          {incomeForm.saveError && (
            <Text c="red" size="sm">
              {incomeForm.saveError}
            </Text>
          )}
        </Stack>
        <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
          <Group>
            <Button type="submit">Save</Button>
            <Button variant="default" onClick={incomeForm.onCancel}>
              Cancel
            </Button>
          </Group>
        </Box>
      </form>
    </Drawer>
  );
}
