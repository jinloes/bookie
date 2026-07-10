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

export function IncomesForm({
  showForm,
  cancelForm,
  editing,
  form,
  handleSubmit,
  propertyOptions,
  payerOptions,
  saveError,
}) {
  return (
    <Drawer
      opened={showForm}
      onClose={cancelForm}
      title={editing ? 'Edit Income' : 'New Income'}
      position="right"
      size="lg"
      styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
    >
      <form
        onSubmit={form.onSubmit(handleSubmit)}
        style={{ display: 'flex', flexDirection: 'column', flex: 1 }}
      >
        <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
          <Group grow>
            <NumberInput
              label="Amount"
              {...form.getInputProps('amount')}
              min={0}
              decimalScale={2}
              prefix="$"
              required
            />
            <TextInput label="Description" {...form.getInputProps('description')} required />
          </Group>
          <Group grow>
            <TextInput label="Date" type="date" {...form.getInputProps('date')} required />
            <TextInput label="Source" {...form.getInputProps('source')} />
          </Group>
          <Group grow>
            <Select
              label="Property"
              {...form.getInputProps('propertyId')}
              data={propertyOptions}
              clearable
              placeholder="— None —"
            />
            <Select
              label="Payer"
              {...form.getInputProps('payerId')}
              data={payerOptions}
              clearable
              searchable
              placeholder="— None —"
            />
          </Group>
          {saveError && (
            <Text c="red" size="sm">
              {saveError}
            </Text>
          )}
        </Stack>
        <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
          <Group>
            <Button type="submit">Save</Button>
            <Button variant="default" onClick={cancelForm}>
              Cancel
            </Button>
          </Group>
        </Box>
      </form>
    </Drawer>
  );
}
