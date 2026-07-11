import React from 'react';
import { Box, Button, Drawer, Group, Select, Stack, Text } from '@mantine/core';
import { IconCheck, IconX } from '@tabler/icons-react';
import { COLORS } from '../designTokens.js';
import { fmtCurrency } from '../utils/formatters.js';

export function PendingIncomeReviewDrawer({ pendingReview }) {
  const pendingIncome = pendingReview.reviewingId
    ? pendingReview.pendingIncomes.find((item) => item.id === pendingReview.reviewingId)
    : null;

  return (
    <Drawer
      opened={pendingReview.reviewingId !== null}
      onClose={pendingReview.onCloseReview}
      title="Review Pending Income"
      position="right"
      size="lg"
      styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
    >
      {pendingReview.reviewingId && (
        <>
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Text size="xs" c="dimmed" style={{ fontStyle: 'italic' }}>
              Imported from Venmo CSV. Accepting will add this to finalized income records.
            </Text>
            <div>
              <Text size="sm" c="dimmed">
                Payer
              </Text>
              <Text fw={500}>{pendingIncome?.payer?.name || '—'}</Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Date
              </Text>
              <Text fw={500}>{pendingIncome?.date}</Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Amount
              </Text>
              <Text fw={500} c="green">
                +{fmtCurrency(pendingIncome?.amount)}
              </Text>
            </div>
            <div>
              <Text size="sm" c="dimmed">
                Description
              </Text>
              <Text fw={500} style={{ wordBreak: 'break-word' }}>
                {pendingIncome?.description}
              </Text>
            </div>
            <Select
              label="Property"
              description={
                pendingIncome?.property
                  ? 'Auto-detected from payer history — adjust if incorrect'
                  : 'No property detected — select one if applicable'
              }
              value={pendingReview.form.propertyId}
              onChange={(value) => pendingReview.setForm({ propertyId: value })}
              data={pendingReview.propertyOptions}
              clearable
              placeholder="— None —"
            />
          </Stack>
          <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
            <Group justify="space-between">
              <Group>
                <Button onClick={pendingReview.onAccept} leftSection={<IconCheck size={16} />}>
                  Accept
                </Button>
                <Button variant="default" onClick={pendingReview.onCloseReview}>
                  Cancel
                </Button>
              </Group>
              <Button
                variant="subtle"
                color="red"
                leftSection={<IconX size={16} />}
                onClick={() => {
                  pendingReview.onCloseReview();
                  pendingReview.onReject(pendingReview.reviewingId);
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
