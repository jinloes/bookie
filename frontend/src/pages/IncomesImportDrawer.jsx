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
          Choose an activity to classify every imported payment deterministically. If no activity is
          selected, rental property may be auto-detected from payer history. Optionally select a
          payer to import only payments received from that payer.
        </Text>
        <Select
          label="Activity context (optional)"
          value={importForm.activityId}
          onChange={importForm.setActivityId}
          data={importForm.activityOptions}
          clearable
          searchable
          placeholder="Use payer/property history"
        />
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
