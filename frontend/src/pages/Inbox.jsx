import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Menu, Stack } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconChevronDown,
  IconFileSpreadsheet,
  IconMail,
  IconReceipt2,
  IconRefresh,
  IconUpload,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getCounterparties, getFinancialActivities } from '../api/index.js';
import InboxQueue from '../components/InboxQueue.jsx';
import { IncomesImportDrawer } from './IncomesImportDrawer.jsx';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { useImportIncomeStatement } from '../hooks/useImportIncomeStatement.js';
import { useRunAutoImport } from '../hooks/useRunAutoImport.js';

function CheckForNewItemsButton() {
  const { run, running } = useRunAutoImport();

  const handleClick = async () => {
    try {
      const queued = await run();
      notifications.show({
        title: queued > 0 ? `Found ${queued} new item${queued === 1 ? '' : 's'}` : 'No new items',
        message: queued > 0 ? 'They will appear here once parsed.' : undefined,
        color: 'green',
      });
    } catch (err) {
      notifications.show({
        title: 'Check failed',
        message: getErrorMessage(err, 'Could not check for new items.'),
        color: 'red',
      });
    }
  };

  return (
    <Button
      variant="light"
      size="xs"
      leftSection={<IconRefresh size={14} />}
      loading={running}
      onClick={handleClick}
    >
      Check for new items
    </Button>
  );
}

export default function Inbox() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const importStatement = useImportIncomeStatement();
  const { data: payers = [] } = useQuery({
    queryKey: queryKeys.counterparties,
    queryFn: getCounterparties,
  });
  const { data: activities = [] } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });

  const payerOptions = useMemo(
    () => payers.map((payer) => ({ value: String(payer.id), label: payer.name })),
    [payers]
  );
  const activityOptions = useMemo(
    () =>
      activities
        .filter((activity) => activity.active)
        .map((activity) => ({ value: String(activity.id), label: activity.name })),
    [activities]
  );

  const handleSaved = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  const toolbarActions = (
    <>
      <Menu position="bottom-end" withinPortal>
        <Menu.Target>
          <Button
            variant="default"
            size="xs"
            leftSection={<IconUpload size={14} />}
            rightSection={<IconChevronDown size={14} />}
          >
            Import
          </Button>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item leftSection={<IconFileSpreadsheet size={15} />} onClick={importStatement.open}>
            Income statement (Venmo / Bank)
          </Menu.Item>
          <Menu.Item leftSection={<IconMail size={15} />} onClick={() => navigate('/emails')}>
            Outlook emails
          </Menu.Item>
          <Menu.Item
            leftSection={<IconReceipt2 size={15} />}
            onClick={() => navigate('/transactions/receipts')}
          >
            Upload receipt
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      <CheckForNewItemsButton />
    </>
  );

  return (
    <Stack gap="lg">
      <IncomesImportDrawer
        importForm={{
          ...importStatement.importForm,
          payerOptions,
          activityOptions,
        }}
      />
      <InboxQueue onSaved={handleSaved} toolbarActions={toolbarActions} />
    </Stack>
  );
}
