import React from 'react';
import {
  Button,
  Drawer,
  FileInput,
  Group,
  SegmentedControl,
  Select,
  Stack,
  Text,
} from '@mantine/core';
import { STATEMENT_SOURCE } from '../hooks/useImportIncomeStatement.js';

const SOURCES = {
  [STATEMENT_SOURCE.VENMO]: {
    fileLabel: 'Venmo CSV file',
    accept: '.csv,text/csv',
    help: 'If no activity is selected, rental property may be auto-detected from payer history. Optionally select a payer to import only payments received from that payer.',
  },
  [STATEMENT_SOURCE.BANK]: {
    fileLabel: 'Bank statement file (OFX/QFX)',
    accept: '.ofx,.qfx',
    help: 'Download an OFX or QFX statement from your bank. Every deposit is imported for review; debits are skipped. The file itself is not stored.',
  },
};

export function IncomesImportDrawer({ importForm }) {
  const source = SOURCES[importForm.source];
  const isVenmo = importForm.source === STATEMENT_SOURCE.VENMO;

  return (
    <Drawer
      opened={importForm.opened}
      onClose={importForm.onCancel}
      title="Import income statement"
      position="right"
      size="md"
    >
      <Stack gap="sm">
        <SegmentedControl
          value={importForm.source}
          onChange={importForm.setSource}
          data={[
            { value: STATEMENT_SOURCE.VENMO, label: 'Venmo CSV' },
            { value: STATEMENT_SOURCE.BANK, label: 'Bank statement (OFX/QFX)' },
          ]}
        />
        <Text size="sm" c="dimmed">
          Choose an activity to classify every imported payment deterministically. {source.help}
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
        {isVenmo && (
          <Select
            label="Payer filter (optional)"
            value={importForm.payerId}
            onChange={importForm.setPayerId}
            data={importForm.payerOptions}
            clearable
            searchable
            placeholder="All senders"
          />
        )}
        <FileInput
          label={source.fileLabel}
          value={importForm.file}
          onChange={importForm.setFile}
          accept={source.accept}
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
