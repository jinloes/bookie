import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Stack, Title, Text, Button, Card, Group } from '@mantine/core';
import { IconInbox, IconSettings } from '@tabler/icons-react';
import RentalEmails from '../components/RentalEmails.jsx';

export default function Emails() {
  const navigate = useNavigate();

  return (
    <Stack gap="lg">
      <Title order={2}>Emails</Title>
      <Card withBorder p="md">
        <Group justify="space-between" align="flex-start">
          <div>
            <Text fw={600}>Import rental emails into the Review Queue</Text>
            <Text size="sm" c="dimmed">
              This page is only for selecting source emails. Final review and saving happens in the
              Review Queue.
            </Text>
          </div>
          <Group gap="xs">
            <Button
              leftSection={<IconSettings size={16} />}
              variant="default"
              onClick={() => navigate('/settings')}
            >
              Email Settings
            </Button>
            <Button
              leftSection={<IconInbox size={16} />}
              onClick={() => navigate('/transactions/review')}
            >
              Open Review Queue
            </Button>
          </Group>
        </Group>
      </Card>

      <RentalEmails onQueued={() => navigate('/transactions/review')} refreshKey={0} />
    </Stack>
  );
}
