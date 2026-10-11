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
  Badge,
  Checkbox,
  CopyButton,
  Code,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconCloudUpload,
  IconCloudDownload,
  IconAlertCircle,
  IconCheck,
  IconTrash,
  IconLock,
  IconKey,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { triggerBackup, listBackups, deleteBackup } from '../api/index.js';
import { useRestoreBackup } from '../hooks/useRestoreBackup.js';
import { useBackupActions } from '../hooks/useBackupActions.js';
import { fmtDateTime } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';
import { TablePageSkeleton } from '../components/PageLoadingSkeleton.jsx';

const CATEGORY_LABELS = {
  PORTABLE: { label: 'Portable', color: 'blue' },
  LEGACY: { label: 'Legacy', color: 'gray' },
  PRE_MIGRATION: { label: 'Pre-migration', color: 'grape' },
  ROLLBACK: { label: 'Rollback', color: 'orange' },
};

function CategoryBadge({ category }) {
  const meta = CATEGORY_LABELS[category] ?? CATEGORY_LABELS.LEGACY;
  return (
    <Badge variant="light" color={meta.color}>
      {meta.label}
    </Badge>
  );
}

function BackupKeyCard({ revealedKey, pending, onReveal, onConfirm }) {
  const [saved, setSaved] = useState(false);
  return (
    <Alert icon={<IconKey size={16} />} color="yellow" title="Save your backup encryption key">
      <Stack gap="sm">
        <Text size="sm">
          Backups are encrypted. Without this key they cannot be restored, so save it in your
          password manager before Bookie uploads any backups.
        </Text>
        {revealedKey ? (
          <>
            <Group gap="xs" wrap="nowrap">
              <Code style={{ wordBreak: 'break-all' }}>{revealedKey}</Code>
              <CopyButton value={revealedKey}>
                {({ copied, copy }) => (
                  <Button size="xs" variant="light" onClick={copy}>
                    {copied ? 'Copied' : 'Copy key'}
                  </Button>
                )}
              </CopyButton>
            </Group>
            <Checkbox
              label="I saved this key in my password manager"
              checked={saved}
              onChange={(event) => setSaved(event.currentTarget.checked)}
            />
            <Group>
              <Button
                size="xs"
                disabled={!saved}
                loading={pending === 'confirmKey'}
                onClick={onConfirm}
              >
                Confirm key saved
              </Button>
            </Group>
          </>
        ) : (
          <Group>
            <Button size="xs" variant="light" loading={pending === 'revealKey'} onClick={onReveal}>
              Show backup key
            </Button>
          </Group>
        )}
      </Stack>
    </Alert>
  );
}

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
  const {
    status,
    pending,
    revealedKey,
    createPreMigration,
    prepareRollback,
    liftFreeze,
    revealKey,
    confirmKey,
  } = useBackupActions();
  const isSqlServer = status?.vendor === 'sqlserver';
  const keyConfirmed = status?.keyConfirmed === true;
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

  const runAction = async (action, success, failure) => {
    setMessage(null);
    setActionError(null);
    try {
      const result = await action();
      setMessage(success(result));
    } catch (e) {
      setActionError(getErrorMessage(e, failure));
    }
  };

  const handlePreMigration = () => {
    modals.openConfirmModal({
      title: 'Create pre-migration backup',
      children: (
        <Text size="sm">
          Bookie will pause intake, freeze writes and back up every record. Writes stay frozen until
          you restore a backup or lift the freeze, so nothing changes before you migrate.
        </Text>
      ),
      labels: { confirm: 'Create backup', cancel: 'Cancel' },
      onConfirm: () =>
        runAction(
          createPreMigration,
          (result) =>
            `Pre-migration backup verified and uploaded (${result.uploaded.length} files). Local copies: ${result.localDirectory}. Writes are frozen.`,
          'Pre-migration backup failed. Nothing was recorded as migrated.'
        ),
    });
  };

  const handleRollback = () => {
    modals.openConfirmModal({
      title: 'Prepare rollback',
      children: (
        <Text size="sm">
          Bookie will freeze writes and export a verified rollback archive you can restore on the
          local database. Writes stay frozen until you lift the freeze.
        </Text>
      ),
      labels: { confirm: 'Prepare rollback', cancel: 'Cancel' },
      confirmProps: { color: 'orange' },
      onConfirm: () =>
        runAction(
          prepareRollback,
          (result) => `Rollback archive verified and uploaded: ${result.name}. Writes are frozen.`,
          'Could not prepare the rollback archive.'
        ),
    });
  };

  const handleLiftFreeze = () =>
    runAction(liftFreeze, () => 'Writes are enabled again.', 'Could not lift the write freeze.');

  const handleRevealKey = async () => {
    setActionError(null);
    try {
      await revealKey();
    } catch (e) {
      setActionError(getErrorMessage(e, 'Could not show the backup key.'));
    }
  };

  const handleConfirmKey = () =>
    runAction(
      confirmKey,
      () => 'Backup key confirmed. Encrypted backups can now be uploaded.',
      'Could not confirm the backup key.'
    );

  const handleRestore = (fileId, name) => {
    modals.openConfirmModal({
      title: 'Restore backup',
      children: (
        <Text size="sm">
          Restore from <strong>{name}</strong>? Bookie validates the backup and keeps a safety copy
          of the current data before replacing it. Older backups may need a backend restart to
          activate.
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
            setMessage('Database restored and passed integrity validation.');
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
        <Group gap="xs">
          {status && !isSqlServer && (
            <Button
              variant="light"
              onClick={handlePreMigration}
              loading={pending === 'preMigration'}
              disabled={!keyConfirmed || isRestoring || restartPending || status.frozen}
            >
              Create pre-migration backup
            </Button>
          )}
          {isSqlServer && (
            <Button
              variant="light"
              color="orange"
              onClick={handleRollback}
              loading={pending === 'rollback'}
              disabled={!keyConfirmed || isRestoring || restartPending || status.frozen}
            >
              Prepare rollback
            </Button>
          )}
          <Button
            onClick={handleBackup}
            loading={backing}
            disabled={isRestoring || restartPending || (isSqlServer && !keyConfirmed)}
            leftSection={<IconCloudUpload size={16} />}
          >
            Backup Now
          </Button>
        </Group>
      </Group>

      {status && !keyConfirmed && (
        <BackupKeyCard
          revealedKey={revealedKey}
          pending={pending}
          onReveal={handleRevealKey}
          onConfirm={handleConfirmKey}
        />
      )}

      {status?.frozen && (
        <Alert icon={<IconLock size={16} />} color="orange" title="Writes are frozen">
          <Group justify="space-between" align="center">
            <Text size="sm">
              Bookie is read-only while a migration or rollback is in progress. Lift the freeze only
              if you are not migrating now.
            </Text>
            <Button
              size="xs"
              variant="light"
              color="orange"
              loading={pending === 'liftFreeze'}
              onClick={handleLiftFreeze}
            >
              Lift freeze
            </Button>
          </Group>
        </Alert>
      )}

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
              {['File', 'Type', 'Last Modified', 'Size', 'Actions'].map((h) => (
                <Table.Th key={h}>{h}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {backups.length === 0 ? (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text ta="center" c="dimmed" py="xl">
                    No backups yet — click "Backup Now" to create one
                  </Text>
                </Table.Td>
              </Table.Tr>
            ) : (
              backups.map((b) => (
                <Table.Tr key={b.id}>
                  <Table.Td fw={500}>{b.name}</Table.Td>
                  <Table.Td>
                    <CategoryBadge category={b.category} />
                  </Table.Td>
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
