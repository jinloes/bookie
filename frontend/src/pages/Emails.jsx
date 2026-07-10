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
            <Text fw={600}>Review and queue rental emails</Text>
            <Text size="sm" c="dimmed">
              Queued items are reviewed in the Review Queue tab, not here.
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
              variant="light"
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
