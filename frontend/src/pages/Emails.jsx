import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Stack, Title, Text, Button, Card, Group } from '@mantine/core';
import { IconInbox } from '@tabler/icons-react';
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
              Queued items are reviewed in Inbox, not here.
            </Text>
          </div>
          <Button
            leftSection={<IconInbox size={16} />}
            variant="light"
            onClick={() => navigate('/inbox')}
          >
            Open Inbox
          </Button>
        </Group>
      </Card>

      <RentalEmails onQueued={() => navigate('/inbox')} refreshKey={0} />
    </Stack>
  );
}
