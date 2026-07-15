import React, { useState } from 'react';
import {
  Card,
  Text,
  Group,
  Button,
  Stack,
  Badge,
  Loader,
  Center,
  Alert,
  ActionIcon,
  Switch,
} from '@mantine/core';
import { IconAlertCircle, IconMail, IconClock, IconRefresh, IconX } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getOutlookRentalEmails } from '../api/index.js';
import { fmtDate } from '../utils/formatters.js';
import { PENDING_STATUS } from '../constants.js';
import { queryKeys } from '../queryKeys.js';
import { useOutlookStatus } from '../hooks/useOutlookStatus.js';
import { useParseEmail } from '../hooks/useParseEmail.js';

// Polls every 4s only while at least one email is mid-parse. TanStack Query handles abort,
// stale-while-revalidate, and de-duplication of overlapping in-flight requests for us.
const POLL_MS = 4000;

export default function RentalEmails({ onQueued, refreshKey }) {
  const [page, setPage] = useState(0);
  const [hideQueued, setHideQueued] = useState(false);

  const statusQuery = useOutlookStatus({ refreshKey });
  const connected = statusQuery.connected;

  const emailsQuery = useQuery({
    queryKey: queryKeys.outlookRentalEmails(page, refreshKey),
    queryFn: () => getOutlookRentalEmails(page),
    enabled: connected,
    refetchInterval: (q) => {
      const data = q.state.data;
      const anyInFlight = data?.emails?.some(
        (e) => e.pendingId && e.pendingStatus !== PENDING_STATUS.FAILED
      );
      return anyInFlight ? POLL_MS : false;
    },
  });
  const emails = emailsQuery.data?.emails ?? [];
  const hasMore = emailsQuery.data?.hasMore ?? false;

  const { converting, convertError, handleConvert, clearConvertError } = useParseEmail({
    page,
    refreshKey,
    onQueued,
  });

  if (statusQuery.isChecking)
    return (
      <Center mb="xl">
        <Loader size="sm" />
      </Center>
    );

  if (statusQuery.isDisconnected) {
    return (
      <Card withBorder p="lg">
        <Text size="sm" c="dimmed">
          Email feed is unavailable while Outlook is disconnected. Reconnect from the banner above
          or Settings.
        </Text>
      </Card>
    );
  }

  return (
    <Card withBorder p="lg">
      <Group justify="space-between" mb="md">
        <Group gap="xs">
          <IconMail size={18} />
          <Text fw={600}>Rental Emails</Text>
        </Group>
        <Group gap="xs">
          <Switch
            label="Hide queued"
            checked={hideQueued}
            onChange={(e) => setHideQueued(e.currentTarget.checked)}
            size="xs"
          />
          <Badge variant="light" color="gray">
            {emails.length} email{emails.length !== 1 ? 's' : ''}
          </Badge>
          <ActionIcon
            variant="subtle"
            color="gray"
            onClick={() => emailsQuery.refetch()}
            title="Refresh emails"
            size="lg"
            aria-label="Refresh emails"
          >
            <IconRefresh size={16} />
          </ActionIcon>
        </Group>
      </Group>

      {convertError && (
        <Alert
          icon={<IconAlertCircle size={14} />}
          color="red"
          mb="sm"
          withCloseButton
          onClose={() => clearConvertError()}
        >
          {convertError}
        </Alert>
      )}

      {emailsQuery.isLoading ? (
        <Center py="md">
          <Loader size="sm" />
        </Center>
      ) : emails.length === 0 ? (
        <Text c="dimmed" size="sm">
          No emails tagged as Rental
        </Text>
      ) : (
        <Stack gap={0}>
          {emails
            .filter(
              (email) =>
                !hideQueued || !email.pendingId || email.pendingStatus === PENDING_STATUS.FAILED
            )
            .map((email, i, arr) => (
              <div
                key={email.id}
                style={{
                  borderBottom:
                    i < arr.length - 1 ? '1px solid var(--mantine-color-gray-2)' : 'none',
                  paddingTop: 10,
                  paddingBottom: 10,
                }}
              >
                <Group justify="space-between" mb={2}>
                  <Text fw={600} size="sm">
                    {email.subject}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {fmtDate(email.receivedAt)}
                  </Text>
                </Group>
                <Text size="xs" c="dimmed" mb={2}>
                  {email.sender}
                </Text>
                <Text size="xs" c="dimmed" truncate mb={6}>
                  {email.preview}
                </Text>
                {email.pendingId && email.pendingStatus !== PENDING_STATUS.FAILED ? (
                  <Group gap="xs">
                    <IconClock size={14} color="var(--mantine-color-blue-6)" />
                    <Text size="xs" c="blue" fw={600}>
                      In Review Queue
                    </Text>
                  </Group>
                ) : email.pendingId && email.pendingStatus === PENDING_STATUS.FAILED ? (
                  <Group gap="xs">
                    <IconX size={14} color="var(--mantine-color-red-6)" />
                    <Text size="xs" c="red" fw={600}>
                      Parsing failed
                    </Text>
                    <Button
                      size="sm"
                      variant="subtle"
                      color="red"
                      onClick={() => handleConvert(email)}
                    >
                      Retry
                    </Button>
                  </Group>
                ) : (
                  <Button
                    size="sm"
                    loading={converting === email.id}
                    onClick={() => handleConvert(email)}
                  >
                    Send to Review Queue
                  </Button>
                )}
              </div>
            ))}
          <Group justify="space-between" mt="md">
            <Button
              variant="default"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              ← Prev
            </Button>
            <Text size="xs" c="dimmed">
              Page {page + 1}
            </Text>
            <Button
              variant="default"
              size="sm"
              disabled={!hasMore}
              onClick={() => setPage((p) => p + 1)}
            >
              Next →
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}
