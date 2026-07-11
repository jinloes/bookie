import React from 'react';
import { Button, Drawer, FileInput, Group, Select, Stack, Text } from '@mantine/core';

export function IncomesImportDrawer({ importForm }) {
  return (
    <Drawer
      opened={importForm.opened}
      onClose={importForm.onCancel}
      title="Import Venmo CSV"
      position="right"
      size="md"
    >
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          Property will be auto-detected from payer history. Optional: select a payer to import only
          payments received from that payer.
        </Text>
        <Select
          label="Payer filter (optional)"
          value={importForm.payerId}
          onChange={importForm.setPayerId}
          data={importForm.payerOptions}
          clearable
          searchable
          placeholder="All senders"
        />
        <FileInput
          label="Venmo CSV file"
          value={importForm.file}
          onChange={importForm.setFile}
          accept=".csv,text/csv"
          clearable
        />
        {importForm.error && (
          <Text c="red" size="sm">
            {importForm.error}
          </Text>
        )}
        <Group pt="sm">
          <Button onClick={importForm.onSubmit}>Import</Button>
          <Button variant="default" onClick={importForm.onCancel}>
            Cancel
          </Button>
        </Group>
      </Stack>
    </Drawer>
  );
}
