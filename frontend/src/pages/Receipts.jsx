import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Stack,
  Group,
  Title,
  Button,
  Card,
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
  SimpleGrid,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import { notifications } from '@mantine/notifications';
import {
  IconUpload,
  IconFileTypePdf,
  IconExternalLink,
  IconAlertTriangle,
  IconCheck,
  IconTrash,
  IconReceipt,
  IconTrendingUp,
  IconInfoCircle,
  IconRefresh,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMediaQuery } from '@mantine/hooks';
import {
  listReceipts,
  uploadReceipt,
  parseReceipt,
  deleteReceipt,
  getReceiptSettings,
} from '../api/index.js';
import { fmtDate } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { queryKeys } from '../queryKeys.js';

export default function Receipts() {
  const isNarrow = useMediaQuery('(max-width: 62em)');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const {
    data: receipts = [],
    isLoading: receiptsLoading,
    isFetching: receiptsFetching,
    error: receiptsQueryError,
    refetch: refetchReceipts,
  } = useQuery({
    queryKey: queryKeys.receipts,
    queryFn: listReceipts,
  });
  const { data: receiptSettings } = useQuery({
    queryKey: queryKeys.receiptSettings,
    queryFn: getReceiptSettings,
  });
  const folderBase = receiptSettings?.folderBase || '';

  const [receiptFile, setReceiptFile] = useState(null);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
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
      queryClient.invalidateQueries({ queryKey: queryKeys.receipts });
    } catch (err) {
      notifications.show({
        title: 'Upload failed',
        message: getErrorMessage(err, 'Receipt upload failed. Please try again.'),
        color: 'red',
      });
    } finally {
      setUploadingReceipt(false);
    }
  };

  const handleParseReceipt = async (itemId) => {
    setParsingReceiptId(itemId);
    try {
      await parseReceipt(itemId);
      // The backend creates the pending item synchronously (status PROCESSING) before
      // parsing runs in the background, so the Review Queue needs an immediate invalidation
      // here rather than waiting for the SSE 'pending-updated' event, which only fires once
      // parsing finishes (READY/FAILED).
      queryClient.invalidateQueries({ queryKey: queryKeys.pendingExpenses });
      notifications.show({
        title: 'Receipt queued',
        message: 'Parsing receipt — review it in the Review Queue tab when it is ready',
        color: 'blue',
        autoClose: 6000,
      });
      navigate('/transactions/review');
    } catch (err) {
      notifications.show({
        title: 'Parse failed',
        message: getErrorMessage(err, 'Could not queue receipt parsing. Please try again.'),
        color: 'red',
      });
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
          queryClient.invalidateQueries({ queryKey: queryKeys.receipts });
        } catch (err) {
          notifications.show({
            title: 'Delete failed',
            message: getErrorMessage(err, 'Could not delete the receipt. Please try again.'),
            color: 'red',
          });
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

  const handleRefreshReceipts = async () => {
    await refetchReceipts();
  };

  const linkedRecord = (r) => {
    if (r.expenseId) return { id: r.expenseId, type: 'expense' };
    if (r.incomeId) return { id: r.incomeId, type: 'income' };
    return null;
  };

  const integrationBlocked =
    receiptsQueryError?.code === 'SERVICE_UNAVAILABLE' ||
    receiptsQueryError?.code === 'OUTLOOK_AUTH_REQUIRED';

  return (
    <Stack gap="lg">
      <Title order={2}>Receipts</Title>

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
          <Button variant="default" size="xs" component={Link} to="/settings">
            Open Settings
          </Button>
        </Group>
        {folderBase ? (
          <Text size="xs" c="dimmed" mb="xs">
            Uploads to: <strong>{folderBase}/pending/</strong> — moved to the correct year folder
            when the entry is saved
          </Text>
        ) : (
          <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={16} />} mb="xs">
            Set a OneDrive receipt folder in{' '}
            <Anchor component={Link} to="/settings" size="xs">
              Settings
            </Anchor>{' '}
            before uploading.
          </Alert>
        )}
        <Text size="xs" c="dimmed" mb="sm">
          Parsed receipts are reviewed in the Review Queue tab.{' '}
          <Anchor component={Link} to="/transactions/review" size="xs">
            Open Review Queue
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
        ) : integrationBlocked ? (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={16} />} m="md">
            <Text size="sm">
              Receipt sync is unavailable while Outlook/OneDrive is disconnected. Use the banner above to reconnect.
            </Text>
          </Alert>
        ) : receiptsQueryError ? (
          <Text ta="center" c="red" py="xl">
            {getErrorMessage(receiptsQueryError, 'Could not load receipts.')}
          </Text>
        ) : receipts.length === 0 ? (
          <Text ta="center" c="dimmed" py="xl">
            No receipts found in OneDrive. Upload a PDF to get started.
          </Text>
        ) : isNarrow ? (
          <Stack p="md" gap="sm">
            {sortedReceipts.map((r) => {
              const linked = linkedRecord(r);
              return (
                <Card key={r.id} withBorder padding="md">
                  <Stack gap="xs">
                    <Group justify="space-between" align="flex-start">
                      <Group gap="xs" wrap="nowrap">
                        <ThemeIcon variant="light" color="red" size="md">
                          <IconFileTypePdf size={14} />
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
                      {r.pending ? (
                        <Badge color="orange" variant="light">
                          Pending
                        </Badge>
                      ) : (
                        <Badge color="teal" variant="light">
                          {r.year}
                        </Badge>
                      )}
                    </Group>
                    <SimpleGrid cols={2} spacing="xs">
                      <Text size="xs" c="dimmed">
                        Uploaded
                      </Text>
                      <Text size="xs">{r.uploadedAt ? fmtDate(r.uploadedAt) : '—'}</Text>
                      <Text size="xs" c="dimmed">
                        Linked
                      </Text>
                      <Text size="xs">
                        {linked
                          ? `${linked.type === 'income' ? 'Income' : 'Expense'} #${linked.id}`
                          : 'Not linked'}
                      </Text>
                    </SimpleGrid>
                    <Group gap="xs">
                      {r.webUrl && (
                        <Button
                          size="sm"
                          variant="default"
                          component="a"
                          href={r.webUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          leftSection={<IconExternalLink size={14} />}
                        >
                          Open
                        </Button>
                      )}
                      {!linked && (
                        <Button
                          size="sm"
                          variant="light"
                          loading={parsingReceiptId === r.id}
                          onClick={() => handleParseReceipt(r.id)}
                        >
                          Create Entry
                        </Button>
                      )}
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        size="lg"
                        aria-label={`Delete receipt ${r.name}`}
                        onClick={() => handleDeleteReceipt(r.id, r.name, !!linked)}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Group>
                  </Stack>
                </Card>
              );
            })}
          </Stack>
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
                            color: 'var(--mantine-color-gray-6)',
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
                                size="lg"
                                aria-label={`Open receipt ${r.name} in OneDrive`}
                              >
                                <IconExternalLink size={16} />
                              </ActionIcon>
                            </Tooltip>
                          )}
                          {!linked && (
                            <Tooltip label="Parse receipt and create expense or income">
                              <Button
                                size="sm"
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
                              size="lg"
                              aria-label={`Delete receipt ${r.name}`}
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
