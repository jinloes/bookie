import React, { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Checkbox,
  Drawer,
  Group,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconPencil } from '@tabler/icons-react';
import { useActivitiesPage } from '../hooks/useActivitiesPage.js';
import { getErrorMessage } from '../utils/errors.js';
import { TablePageSkeleton } from '../components/PageLoadingSkeleton.jsx';

const EMPTY_ACTIVITY = {
  name: '',
  activityType: 'EMPLOYMENT',
  taxTreatment: 'W2',
  ownerId: null,
  propertyId: null,
  active: true,
};
const EMPTY_MEMBER = { name: '', active: true };

export default function Activities() {
  const page = useActivitiesPage();
  const [activityDrawerOpen, setActivityDrawerOpen] = useState(false);
  const [memberDrawerOpen, setMemberDrawerOpen] = useState(false);
  const [editingActivityId, setEditingActivityId] = useState(null);
  const [editingMemberId, setEditingMemberId] = useState(null);
  const [error, setError] = useState(null);
  const activityForm = useForm({
    initialValues: EMPTY_ACTIVITY,
    validate: {
      name: (value) => (!value.trim() ? 'Name is required' : null),
      ownerId: (value) => (!value ? 'Owner is required' : null),
      propertyId: (value, values) =>
        values.activityType === 'RENTAL' && !value ? 'Property is required' : null,
    },
  });
  const memberForm = useForm({
    initialValues: EMPTY_MEMBER,
    validate: { name: (value) => (!value.trim() ? 'Name is required' : null) },
  });

  if (page.isLoading) {
    return <TablePageSkeleton actionCount={2} rowCount={6} />;
  }

  const openActivity = (activity = null) => {
    activityForm.setValues(
      activity
        ? {
            name: activity.name,
            activityType: activity.activityType,
            taxTreatment: activity.taxTreatment,
            ownerId: activity.owner?.id ? String(activity.owner.id) : null,
            propertyId: activity.property?.id ? String(activity.property.id) : null,
            active: activity.active,
          }
        : EMPTY_ACTIVITY
    );
    setEditingActivityId(activity?.id ?? null);
    setError(null);
    setActivityDrawerOpen(true);
  };

  const openMember = (member = null) => {
    memberForm.setValues(member ? { name: member.name, active: member.active } : EMPTY_MEMBER);
    setEditingMemberId(member?.id ?? null);
    setError(null);
    setMemberDrawerOpen(true);
  };

  const submitActivity = async (values) => {
    try {
      await page.saveActivity(editingActivityId, {
        ...values,
        ownerId: Number(values.ownerId),
        propertyId:
          values.activityType === 'RENTAL' && values.propertyId ? Number(values.propertyId) : null,
      });
      setActivityDrawerOpen(false);
      activityForm.reset();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Could not save the activity.'));
    }
  };

  const submitMember = async (values) => {
    try {
      await page.saveMember(editingMemberId, values);
      setMemberDrawerOpen(false);
      memberForm.reset();
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Could not save the household member.'));
    }
  };

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <div>
          <Title order={2}>Activities</Title>
          <Text size="sm" c="dimmed">
            Group household income and expenses by rental, job, business, or other activity.
          </Text>
        </div>
        <Group>
          <Button variant="default" onClick={() => openMember()}>
            + Add Household Member
          </Button>
          <Button onClick={() => openActivity()}>+ Add Activity</Button>
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 1, lg: 3 }}>
        <Card withBorder p={0} style={{ gridColumn: 'span 2' }}>
          <Text fw={600} p="md" pb={0}>
            Financial Activities
          </Text>
          <Table mt="xs">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>Owner</Table.Th>
                <Table.Th>Type</Table.Th>
                <Table.Th>Tax treatment</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th>Actions</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {page.activities.map((activity) => (
                <Table.Tr key={activity.id}>
                  <Table.Td fw={500}>{activity.name}</Table.Td>
                  <Table.Td>{activity.owner?.name ?? '—'}</Table.Td>
                  <Table.Td>
                    {page.activityTypes.find((type) => type.value === activity.activityType)
                      ?.label ?? activity.activityType}
                  </Table.Td>
                  <Table.Td>
                    {page.taxTreatments.find(
                      (treatment) => treatment.value === activity.taxTreatment
                    )?.label ?? activity.taxTreatment}
                  </Table.Td>
                  <Table.Td>
                    <Badge color={activity.active ? 'green' : 'gray'} variant="light">
                      {activity.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    {!activity.needsClassification && (
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        onClick={() => openActivity(activity)}
                        aria-label={`Edit activity ${activity.name}`}
                      >
                        <IconPencil size={16} />
                      </ActionIcon>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>

        <Card withBorder p={0}>
          <Text fw={600} p="md" pb={0}>
            Household Members
          </Text>
          <Table mt="xs">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Name</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {page.members.map((member) => (
                <Table.Tr key={member.id}>
                  <Table.Td>{member.name}</Table.Td>
                  <Table.Td>{member.active ? 'Active' : 'Inactive'}</Table.Td>
                  <Table.Td>
                    {!member.system && (
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        onClick={() => openMember(member)}
                        aria-label={`Edit household member ${member.name}`}
                      >
                        <IconPencil size={16} />
                      </ActionIcon>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      </SimpleGrid>

      <Drawer
        opened={activityDrawerOpen}
        onClose={() => setActivityDrawerOpen(false)}
        title={editingActivityId ? 'Edit Activity' : 'New Activity'}
        position="right"
        size="lg"
      >
        <form onSubmit={activityForm.onSubmit(submitActivity)}>
          <Stack>
            <TextInput label="Name" required {...activityForm.getInputProps('name')} />
            <Select
              label="Owner"
              required
              data={page.members
                .filter((member) => member.active)
                .map((member) => ({ value: String(member.id), label: member.name }))}
              {...activityForm.getInputProps('ownerId')}
            />
            <Select
              label="Activity type"
              data={page.activityTypes.filter(
                (type) => type.value !== 'RENTAL' || activityForm.values.activityType === 'RENTAL'
              )}
              disabled={activityForm.values.activityType === 'RENTAL'}
              description={
                activityForm.values.activityType === 'RENTAL'
                  ? 'Rental activities are created automatically with each property.'
                  : undefined
              }
              {...activityForm.getInputProps('activityType')}
            />
            <Select
              label="Tax treatment"
              data={page.taxTreatments}
              disabled={activityForm.values.activityType === 'RENTAL'}
              description={
                activityForm.values.activityType === 'RENTAL'
                  ? 'Rental activities always use Schedule E.'
                  : undefined
              }
              {...activityForm.getInputProps('taxTreatment')}
            />
            {activityForm.values.activityType === 'RENTAL' && (
              <Select
                label="Property"
                required
                data={[]}
                description="Rental activities are created automatically with each property."
                disabled
                {...activityForm.getInputProps('propertyId')}
              />
            )}
            <Checkbox
              label="Active"
              {...activityForm.getInputProps('active', { type: 'checkbox' })}
            />
            {error && (
              <Text c="red" size="sm">
                {error}
              </Text>
            )}
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={() => setActivityDrawerOpen(false)}>
                Cancel
              </Button>
            </Group>
          </Stack>
        </form>
      </Drawer>

      <Drawer
        opened={memberDrawerOpen}
        onClose={() => setMemberDrawerOpen(false)}
        title={editingMemberId ? 'Edit Household Member' : 'New Household Member'}
        position="right"
      >
        <form onSubmit={memberForm.onSubmit(submitMember)}>
          <Stack>
            <TextInput label="Name" required {...memberForm.getInputProps('name')} />
            <Checkbox
              label="Active"
              {...memberForm.getInputProps('active', { type: 'checkbox' })}
            />
            {error && (
              <Text c="red" size="sm">
                {error}
              </Text>
            )}
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={() => setMemberDrawerOpen(false)}>
                Cancel
              </Button>
            </Group>
          </Stack>
        </form>
      </Drawer>
    </Stack>
  );
}
