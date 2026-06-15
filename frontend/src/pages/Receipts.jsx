import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Stack,
  Group,
  Title,
  Button,
  Card,
  TextInput,
  Table,
  Text,
  Loader,
  Center,
  Badge,
  ActionIcon,
  Modal,
  Tooltip,
  ThemeIcon,
  ScrollArea,
  FileInput,
  Alert,
  Anchor,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  IconUpload,
  IconFileTypePdf,
  IconExternalLink,
  IconSettings,
  IconAlertTriangle,
  IconCheck,
  IconTrash,
  IconReceipt,
  IconTrendingUp,
  IconInfoCircle,
  IconRefresh,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  listReceipts,
  uploadReceipt,
  parseReceipt,
  deleteReceipt,
  getReceiptSettings,
  updateReceiptSettings,
} from '../api/index.js';
import { fmtDate } from '../utils/formatters.js';

export default function Receipts() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const {
    data: receipts = [],
    isLoading: receiptsLoading,
    isFetching: receiptsFetching,
    error: receiptsQueryError,
    refetch: refetchReceipts,
  } = useQuery({
    queryKey: ['receipts'],
    queryFn: listReceipts,
  });
  const { data: receiptSettings } = useQuery({
    queryKey: ['receiptSettings'],
    queryFn: getReceiptSettings,
  });
  const folderBase = receiptSettings?.folderBase || '';

  const [receiptFile, setReceiptFile] = useState(null);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [folderSettingsOpen, setFolderSettingsOpen] = useState(false);
  const [folderBaseInput, setFolderBaseInput] = useState('');
  const [parsingReceiptId, setParsingReceiptId] = useState(null);
  const [previewReceipt, setPreviewReceipt] = useState(null);
  const sortedReceipts = useMemo(
    () =>
      [...receipts].sort((a, b) => {
        if (a.pending !== b.pending) return a.pending ? -1 : 1;
        return b.year - a.year || a.name.localeCompare(b.name);
      }),
    [receipts]
  );

  const handleReceiptUpload = async () => {
    if (!receiptFile) return;
    setUploadingReceipt(true);
    setUploadResult(null);
    try {
      const result = await uploadReceipt(receiptFile);
      setUploadResult(result);
      setReceiptFile(null);
      queryClient.invalidateQueries({ queryKey: ['receipts'] });
    } catch (err) {
      notifications.show({ title: 'Upload failed', message: err.message, color: 'red' });
    } finally {
      setUploadingReceipt(false);
    }
  };

  const handleParseReceipt = async (itemId) => {
    setParsingReceiptId(itemId);
    try {
      await parseReceipt(itemId);
      notifications.show({
        title: 'Receipt queued',
        message: 'Parsing receipt — review it in Inbox when it is ready',
        color: 'blue',
        autoClose: 6000,
      });
      navigate('/inbox');
    } catch (err) {
      notifications.show({ title: 'Parse failed', message: err.message, color: 'red' });
    } finally {
      setParsingReceiptId(null);
    }
  };

  const handleDeleteReceipt = (itemId, name, hasLinkedRecord) => {
    modals.openConfirmModal({
      title: 'Delete receipt',
      children: (
        <Text size="sm">
          {hasLinkedRecord
            ? `"${name}" and its linked record will be permanently deleted. This cannot be undone.`
            : `"${name}" will be permanently deleted from OneDrive. This cannot be undone.`}
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await deleteReceipt(itemId);
          queryClient.invalidateQueries({ queryKey: ['receipts'] });
        } catch (err) {
          notifications.show({ title: 'Delete failed', message: err.message, color: 'red' });
        }
      },
    });
  };

  const handlePreviewReceipt = (receipt) => {
    setPreviewReceipt(receipt);
  };

  const isImageReceipt = (name) => {
    if (!name) return false;
    const lower = name.toLowerCase();
    return (
      lower.endsWith('.jpg') ||
      lower.endsWith('.jpeg') ||
      lower.endsWith('.png') ||
      lower.endsWith('.gif')
    );
  };

  const handleOpenFolderSettings = () => {
    setFolderBaseInput(folderBase);
    setFolderSettingsOpen(true);
  };

  const handleSaveFolderSettings = async () => {
    try {
      await updateReceiptSettings(folderBaseInput);
      queryClient.invalidateQueries({ queryKey: ['receiptSettings'] });
      setFolderSettingsOpen(false);
    } catch (err) {
      notifications.show({ title: 'Save failed', message: err.message, color: 'red' });
    }
  };

  const handleRefreshReceipts = async () => {
    await refetchReceipts();
  };

  const linkedRecord = (r) => {
    if (r.expenseId) return { id: r.expenseId, type: 'expense' };
    if (r.incomeId) return { id: r.incomeId, type: 'income' };
    return null;
  };

  return (
    <Stack gap="lg">
      <Title order={2}>Receipts</Title>

      <Modal
        opened={folderSettingsOpen}
        onClose={() => setFolderSettingsOpen(false)}
        title="Receipt Folder Settings"
        size="sm"
      >
        <Stack gap="sm">
          <TextInput
            label="OneDrive folder path"
            description="Year subfolders will be created automatically (e.g. bookie/taxes/2024)"
            value={folderBaseInput}
            onChange={(e) => setFolderBaseInput(e.target.value)}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setFolderSettingsOpen(false)}>
              Cancel
            </Button>
            <Button disabled={!folderBaseInput.trim()} onClick={handleSaveFolderSettings}>
              Save
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={previewReceipt != null}
        onClose={() => setPreviewReceipt(null)}
        title={previewReceipt?.name || 'Receipt Preview'}
        size="xl"
        padding="md"
      >
        {previewReceipt &&
          (isImageReceipt(previewReceipt.name) ? (
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                width: '100%',
                maxHeight: '80vh',
                overflow: 'hidden',
              }}
            >
              <img
                alt={previewReceipt.name}
                src={`/api/receipts/${encodeURIComponent(previewReceipt.id)}/download`}
                style={{
                  display: 'block',
                  maxWidth: '100%',
                  maxHeight: '80vh',
                  width: 'auto',
                  height: 'auto',
                  objectFit: 'contain',
                }}
              />
            </div>
          ) : (
            <iframe
              title={`Preview ${previewReceipt.name}`}
              src={`/api/receipts/${encodeURIComponent(previewReceipt.id)}/download`}
              style={{ width: '100%', height: '80vh', border: 0 }}
            />
          ))}
      </Modal>
      <Card withBorder p="lg" mb="md">
        <Group justify="space-between" mb="md">
          <Title order={4}>Upload Receipt</Title>
          <Tooltip label="Configure OneDrive folder">
            <ActionIcon variant="subtle" color="gray" onClick={handleOpenFolderSettings}>
              <IconSettings size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>
        {folderBase ? (
          <Text size="xs" c="dimmed" mb="xs">
            Uploads to: <strong>{folderBase}/pending/</strong> — moved to the correct year folder
            when the entry is saved
          </Text>
        ) : (
          <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={16} />} mb="xs">
            Set a OneDrive receipt folder before uploading.
          </Alert>
        )}
        <Text size="xs" c="dimmed" mb="sm">
          Parsed receipts are reviewed in Inbox.{' '}
          <Anchor component={Link} to="/inbox" size="xs">
            Open Inbox
          </Anchor>
        </Text>
        <Group align="flex-end" gap="sm">
          <FileInput
            label="Receipt file"
            placeholder="Select PDF or image"
            accept="application/pdf,image/*"
            value={receiptFile}
            onChange={setReceiptFile}
            leftSection={<IconReceipt size={16} />}
            style={{ flex: 1 }}
            clearable
          />
          <Button
            leftSection={<IconUpload size={16} />}
            onClick={handleReceiptUpload}
            loading={uploadingReceipt}
            disabled={!receiptFile}
            mt="lg"
          >
            Upload
          </Button>
        </Group>

        {uploadResult && (
          <Alert
            mt="sm"
            color={uploadResult.duplicate ? 'yellow' : 'green'}
            icon={
              uploadResult.duplicate ? <IconAlertTriangle size={16} /> : <IconCheck size={16} />
            }
            title={uploadResult.duplicate ? 'File already uploaded' : 'Upload successful'}
          >
            {uploadResult.duplicate ? (
              <>
                This file was already uploaded.{' '}
                {linkedRecord(uploadResult.receipt) ? (
                  <span>It is already linked to a record.</span>
                ) : (
                  <span>No entry linked yet — click &quot;Create Entry&quot; below.</span>
                )}
              </>
            ) : (
              <>Receipt saved to OneDrive.</>
            )}
          </Alert>
        )}
      </Card>

      <Card withBorder p={0}>
        <Group justify="flex-end" p="md" pb={0}>
          <Button
            variant="default"
            leftSection={<IconRefresh size={14} />}
            onClick={handleRefreshReceipts}
            loading={receiptsFetching && !receiptsLoading}
          >
            Refresh from OneDrive
          </Button>
        </Group>
        {receiptsLoading ? (
          <Center py="xl">
            <Loader />
          </Center>
        ) : receiptsQueryError ? (
          <Text ta="center" c="red" py="xl">
            {receiptsQueryError.message}
          </Text>
        ) : receipts.length === 0 ? (
          <Text ta="center" c="dimmed" py="xl">
            No receipts found in OneDrive. Upload a PDF to get started.
          </Text>
        ) : (
          <ScrollArea>
            <Table striped highlightOnHover miw={700}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>
                    <Group gap={4} wrap="nowrap">
                      File
                      <Tooltip
                        label="Sorted by: pending first, then by year (newest first), then by filename."
                        multiline
                        w={220}
                      >
                        <IconInfoCircle
                          size={13}
                          style={{
                            color: 'var(--mantine-color-gray-4)',
                            cursor: 'help',
                            flexShrink: 0,
                          }}
                        />
                      </Tooltip>
                    </Group>
                  </Table.Th>
                  {['Status', 'Uploaded', 'Linked Record', 'Actions'].map((h) => (
                    <Table.Th key={h}>{h}</Table.Th>
                  ))}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {sortedReceipts.map((r) => {
                  const linked = linkedRecord(r);
                  return (
                    <Table.Tr key={r.id}>
                      <Table.Td>
                        <Group gap="xs">
                          <ThemeIcon variant="light" color="red" size="sm">
                            <IconFileTypePdf size={12} />
                          </ThemeIcon>
                          <Anchor
                            href="#"
                            size="sm"
                            onClick={(e) => {
                              e.preventDefault();
                              handlePreviewReceipt(r);
                            }}
                          >
                            {r.name}
                          </Anchor>
                        </Group>
                      </Table.Td>
                      <Table.Td>
                        {r.pending ? (
                          <Badge color="orange" variant="light" size="sm">
                            Pending
                          </Badge>
                        ) : (
                          <Badge color="teal" variant="light" size="sm">
                            {r.year}
                          </Badge>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <Text size="xs" c="dimmed">
                          {r.uploadedAt ? fmtDate(r.uploadedAt) : '—'}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        {linked ? (
                          <Badge
                            color={linked.type === 'income' ? 'green' : 'red'}
                            variant="light"
                            size="sm"
                            leftSection={
                              linked.type === 'income' ? (
                                <IconTrendingUp size={10} />
                              ) : (
                                <IconReceipt size={10} />
                              )
                            }
                          >
                            {linked.type === 'income' ? 'Income' : 'Expense'} #{linked.id}
                          </Badge>
                        ) : (
                          <Badge color="gray" variant="outline" size="sm">
                            Not linked
                          </Badge>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <Group gap="xs">
                          {r.webUrl && (
                            <Tooltip label="Open in OneDrive">
                              <ActionIcon
                                variant="subtle"
                                color="blue"
                                component="a"
                                href={r.webUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                              >
                                <IconExternalLink size={16} />
                              </ActionIcon>
                            </Tooltip>
                          )}
                          {!linked && (
                            <Tooltip label="Parse receipt and create expense or income">
                              <Button
                                size="xs"
                                variant="light"
                                loading={parsingReceiptId === r.id}
                                onClick={() => handleParseReceipt(r.id)}
                              >
                                Create Entry
                              </Button>
                            </Tooltip>
                          )}
                          <Tooltip
                            label={linked ? 'Delete receipt and linked record' : 'Delete receipt'}
                          >
                            <ActionIcon
                              variant="subtle"
                              color="red"
                              onClick={() => handleDeleteReceipt(r.id, r.name, !!linked)}
                            >
                              <IconTrash size={16} />
                            </ActionIcon>
                          </Tooltip>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        )}
      </Card>
    </Stack>
  );
}
