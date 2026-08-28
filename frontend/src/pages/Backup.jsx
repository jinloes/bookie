import React, { useState } from 'react';
import {
  Stack,
  Group,
  Title,
  Button,
  Card,
  Table,
  Text,
  Alert,
  ActionIcon,
  Tooltip,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconCloudUpload,
  IconCloudDownload,
  IconAlertCircle,
  IconCheck,
  IconTrash,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { triggerBackup, listBackups, deleteBackup } from '../api/index.js';
import { useRestoreBackup } from '../hooks/useRestoreBackup.js';
import { fmtDateTime } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { TablePageSkeleton } from '../components/PageLoadingSkeleton.jsx';

export default function Backup() {
  const queryClient = useQueryClient();
  const {
    data: backups = [],
    isLoading,
    error: backupsError,
  } = useQuery({
    queryKey: queryKeys.backups,
    queryFn: listBackups,
  });
  const { confirmRestore, runRestore, restoringId, isRestoring } = useRestoreBackup();
  const [backing, setBacking] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [message, setMessage] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [pendingRestoreId, setPendingRestoreId] = useState(null);
  const [checkingRestore, setCheckingRestore] = useState(false);
  const restartPending = pendingRestoreId !== null;

  const handleBackup = async () => {
    setBacking(true);
    setMessage(null);
    setActionError(null);
    try {
      const result = await triggerBackup();
      setMessage(`Backup created: ${result.name}`);
      queryClient.invalidateQueries({ queryKey: queryKeys.backups });
    } catch (e) {
      setActionError(getErrorMessage(e, 'Backup failed. Please try again.'));
    } finally {
      setBacking(false);
    }
  };

  const handleRestore = (fileId, name) => {
    modals.openConfirmModal({
      title: 'Restore backup',
      children: (
        <Text size="sm">
          Restore from <strong>{name}</strong>? Bookie will validate an isolated copy first, retain
          the current database for rollback, and restart the managed backend before activating it.
        </Text>
      ),
      labels: { confirm: 'Restore', cancel: 'Cancel' },
      confirmProps: { color: 'orange' },
      onConfirm: async () => {
        setMessage(null);
        setActionError(null);
        try {
          const result = await runRestore(fileId);
          if (result.outcome === 'manual-restart') {
            setPendingRestoreId(result.status.restoreId);
            setMessage(
              `Backup validated and staged. ${
                result.message ||
                'Fully stop and restart Bookie (or the backend process) to activate it.'
              } The active database has not changed yet. After restarting, check the restore status here.`
            );
          } else {
            setMessage('Database restored and passed post-start integrity validation.');
          }
        } catch (e) {
          setActionError(getErrorMessage(e, 'Restore failed. Please try again.'));
        }
      },
    });
  };

  const handleCheckRestore = async () => {
    setCheckingRestore(true);
    setActionError(null);
    try {
      await confirmRestore(pendingRestoreId);
      setPendingRestoreId(null);
      setMessage('Database restored and passed post-start integrity validation.');
    } catch (e) {
      setActionError(getErrorMessage(e, 'Could not confirm the restore status.'));
    } finally {
      setCheckingRestore(false);
    }
  };

  const handleDelete = (fileId, name) => {
    modals.openConfirmModal({
      title: 'Delete backup',
      children: (
        <Text size="sm">
          Delete <strong>{name}</strong> from OneDrive? This cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        setDeleting(fileId);
        setMessage(null);
        setActionError(null);
        try {
          await deleteBackup(fileId);
          setMessage(`Deleted: ${name}`);
          queryClient.invalidateQueries({ queryKey: queryKeys.backups });
        } catch (e) {
          setActionError(getErrorMessage(e, 'Delete failed. Please try again.'));
        } finally {
          setDeleting(null);
        }
      },
    });
  };

  if (isLoading) {
    return <TablePageSkeleton filterCount={0} rowCount={5} />;
  }

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <div>
          <Title order={2}>Backups</Title>
          <Text size="sm" c="dimmed">
            Automatically backed up daily at 2 AM (when Outlook is connected). Use "Backup Now" to
            create an on-demand backup.
          </Text>
        </div>
        <Button
          onClick={handleBackup}
          loading={backing}
          disabled={isRestoring || restartPending}
          leftSection={<IconCloudUpload size={16} />}
        >
          Backup Now
        </Button>
      </Group>

      {message && (
        <Alert
          icon={restartPending ? <IconAlertCircle size={16} /> : <IconCheck size={16} />}
          color={restartPending ? 'yellow' : 'green'}
          withCloseButton={!restartPending}
          onClose={() => setMessage(null)}
        >
          <Group justify="space-between" align="center">
            <Text size="sm">{message}</Text>
            {restartPending && (
              <Button
                size="xs"
                variant="light"
                color="yellow"
                loading={checkingRestore}
                onClick={handleCheckRestore}
              >
                Check Restore Status
              </Button>
            )}
          </Group>
        </Alert>
      )}
      {(actionError || backupsError) && (
        <Alert
          icon={<IconAlertCircle size={16} />}
          color="red"
          withCloseButton
          onClose={() => setActionError(null)}
        >
          {actionError || getErrorMessage(backupsError, 'Failed to load backups.')}
        </Alert>
      )}

      <Card withBorder p={0}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              {['File', 'Last Modified', 'Size', 'Actions'].map((h) => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {backups.length === 0 ? (
              <Table.Tr>
                <Table.Td colSpan={4}>
                  <Text ta="center" c="dimmed" py="xl">
                    No backups yet — click "Backup Now" to create one
                  </Text>
                </Table.Td>
              </Table.Tr>
            ) : (
              backups.map((b) => (
                <Table.Tr key={b.id}>
                  <Table.Td fw={500}>{b.name}</Table.Td>
                  <Table.Td c="dimmed">
                    {b.lastModified ? fmtDateTime(b.lastModified) : '—'}
                  </Table.Td>
                  <Table.Td c="dimmed">
                    {b.size ? `${(b.size / 1024).toFixed(1)} KB` : '—'}
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs">
                      <Button
                        size="sm"
                        variant="light"
                        color="orange"
                        leftSection={<IconCloudDownload size={14} />}
                        loading={restoringId === b.id}
                        disabled={isRestoring || restartPending}
                        onClick={() => handleRestore(b.id, b.name)}
                      >
                        Restore
                      </Button>
                      <Tooltip label="Delete backup">
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          loading={deleting === b.id}
                          disabled={isRestoring || restartPending}
                          onClick={() => handleDelete(b.id, b.name)}
                          size="lg"
                          aria-label={`Delete backup ${b.name}`}
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
