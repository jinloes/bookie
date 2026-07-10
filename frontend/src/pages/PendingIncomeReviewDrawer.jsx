import React from 'react';
import { Box, Button, Drawer, Group, Select, Stack, Text } from '@mantine/core';
import { IconCheck, IconX } from '@tabler/icons-react';
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';

export function PendingIncomeReviewDrawer({
  reviewingPendingId,
  closePendingReview,
  reviewingForm,
  setReviewingForm,
  pendingIncomes,
  propertyOptions,
  handleAcceptPending,
  handleRejectPending,
}) {
  const pending = reviewingPendingId
    ? pendingIncomes.find((item) => item.id === reviewingPendingId)
    : null;

  return (
    <Drawer
      opened={reviewingPendingId !== null}
      onClose={closePendingReview}
      title="Review Pending Income"
      position="right"
      size="lg"
      styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
    >
      {reviewingPendingId && (
        <>
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Text size="xs" c="dimmed" style={{ fontStyle: 'italic' }}>
              Imported from Venmo CSV. Accepting will add this to finalized income records.
            </Text>
            <div>
              <Text size="sm" c="dimmed">
                Payer
              </Text>
              <Text fw={500}>{pending?.payer?.name || '—'}</Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Date
              </Text>
              <Text fw={500}>{pending?.date}</Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Amount
              </Text>
              <Text fw={500} c="green">
                +{fmtCurrency(pending?.amount)}
              </Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Description
              </Text>
              <Text fw={500} style={{ wordBreak: 'break-word' }}>
                {pending?.description}
              </Text>
            </div>
            <Select
              label="Property"
              description={
                pending?.property
                  ? 'Auto-detected from payer history — adjust if incorrect'
                  : 'No property detected — select one if applicable'
              }
              value={reviewingForm.propertyId}
              onChange={(val) => setReviewingForm({ propertyId: val })}
              data={propertyOptions}
              clearable
              placeholder="— None —"
            />
          </Stack>
          <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
            <Group justify="space-between">
              <Group>
                <Button onClick={handleAcceptPending} leftSection={<IconCheck size={16} />}>
                  Accept
                </Button>
                <Button variant="default" onClick={closePendingReview}>
                  Cancel
                </Button>
              </Group>
              <Button
                variant="subtle"
                color="red"
                leftSection={<IconX size={16} />}
                onClick={() => {
                  closePendingReview();
                  handleRejectPending(reviewingPendingId);
                }}
              >
                Reject
              </Button>
            </Group>
          </Box>
        </>
      )}
    </Drawer>
  );
}
