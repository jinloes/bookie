import React from 'react';
import { Button, Drawer, FileInput, Group, Select, Stack, Text } from '@mantine/core';

export function IncomesImportDrawer({
  showImportForm,
  cancelImportForm,
  importPayerId,
  setImportPayerId,
  payerOptions,
  importFile,
  setImportFile,
  importError,
  handleImportSubmit,
}) {
  return (
    <Drawer
      opened={showImportForm}
      onClose={cancelImportForm}
      title="Import Venmo CSV"
      position="right"
      size="md"
    >
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          Property will be auto-detected from payer history. Optional: select a payer to import only payments received from that payer.
        </Text>
        <Select
          label="Payer filter (optional)"
          value={importPayerId}
          onChange={setImportPayerId}
          data={payerOptions}
          clearable
          searchable
          placeholder="All senders"
        />
        <FileInput
          label="Venmo CSV file"
          value={importFile}
          onChange={setImportFile}
          accept=".csv,text/csv"
          clearable
        />
        {importError && (
          <Text c="red" size="sm">
            {importError}
          </Text>
        )}
        <Group pt="sm">
          <Button onClick={handleImportSubmit}>Import</Button>
          <Button variant="default" onClick={cancelImportForm}>
            Cancel
          </Button>
        </Group>
      </Stack>
    </Drawer>
  );
}
