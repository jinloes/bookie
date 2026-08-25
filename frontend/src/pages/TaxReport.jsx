import React, { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { IconAlertCircle, IconDownload } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { getFinancialActivities } from '../api/index.js';
import { useScheduleEReport } from '../hooks/useReports.js';
import { fmtCurrency } from '../utils/formatters.js';
import { getErrorMessage } from '../utils/errors.js';
import { SummaryPageSkeleton } from '../components/PageLoadingSkeleton.jsx';
import { queryKeys } from '../queryKeys.js';

function downloadCsv(filename, rows) {
  const csv = rows
    .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export default function TaxReport() {
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(String(currentYear));
  const [ownerId, setOwnerId] = useState(null);
  const [activityId, setActivityId] = useState(null);
  const {
    data: financialActivities = [],
    isLoading: activitiesLoading,
    error: activitiesError,
  } = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });
  const {
    data: report,
    isLoading,
    error: reportError,
  } = useScheduleEReport(selectedYear, { ownerId, activityId });
  const yearOptions = useMemo(
    () =>
      Array.from({ length: 8 }, (_, offset) => String(currentYear - offset)).map((year) => ({
        value: year,
        label: year,
      })),
    [currentYear]
  );
  const ownerOptions = useMemo(() => {
    const owners = new Map();
    financialActivities.forEach((activity) => {
      if (activity.owner?.id != null) {
        owners.set(String(activity.owner.id), activity.owner.name);
      }
    });
    return [...owners.entries()].map(([value, label]) => ({ value, label }));
  }, [financialActivities]);
  const activityOptions = useMemo(
    () =>
      financialActivities
        .filter(
          (activity) =>
            activity.taxTreatment === 'SCHEDULE_E' &&
            (!ownerId || String(activity.owner?.id) === String(ownerId))
        )
        .map((activity) => ({ value: String(activity.id), label: activity.name })),
    [financialActivities, ownerId]
  );

  if (reportError || activitiesError) {
    return (
      <Alert icon={<IconAlertCircle size={16} />} color="red" title="Error">
        {getErrorMessage(reportError || activitiesError, 'Could not load the Schedule E report.')}
      </Alert>
    );
  }

  if (isLoading || activitiesLoading || !report) {
    return <SummaryPageSkeleton metricCount={3} cardCount={2} rowCount={5} />;
  }

  const activities = report.activities ?? [];
  const noData = Number(report.rentalIncome) === 0 && Number(report.expenses) === 0;
  const handleOwnerChange = (nextOwnerId) => {
    setOwnerId(nextOwnerId);
    const selectedActivity = financialActivities.find(
      (activity) => String(activity.id) === String(activityId)
    );
    if (activityId && nextOwnerId && String(selectedActivity?.owner?.id) !== String(nextOwnerId)) {
      setActivityId(null);
    }
  };

  const handleExportCsv = () => {
    const rows = [
      [`Bookie Schedule E Report — ${selectedYear}`],
      [],
      ['Activity', 'Owner', 'Property', 'Rental Income', 'Expenses', 'Net Income'],
      ...activities.map((row) => [
        row.activity.name,
        row.activity.owner?.name ?? '',
        row.activity.property?.name ?? '',
        Number(row.rentalIncome).toFixed(2),
        Number(row.expenses).toFixed(2),
        Number(row.netIncome).toFixed(2),
      ]),
      [
        'TOTAL',
        '',
        '',
        Number(report.rentalIncome).toFixed(2),
        Number(report.expenses).toFixed(2),
        Number(report.netIncome).toFixed(2),
      ],
      [],
      ['Activity', 'Schedule E Line', 'Category', 'Amount'],
      ...activities.flatMap((row) =>
        (row.categories ?? []).map((category) => [
          row.activity.name,
          category.category.taxLine ?? '',
          category.category.label,
          Number(category.total).toFixed(2),
        ])
      ),
    ];
    downloadCsv(`bookie-schedule-e-${selectedYear}.csv`, rows);
  };

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-start">
        <div>
          <Title order={2}>Tax Report</Title>
          <Text size="sm" c="dimmed">
            Schedule E rental activity summary calculated by the server.
          </Text>
        </div>
        <Group>
          <Select
            value={selectedYear}
            onChange={(value) => setSelectedYear(value ?? String(currentYear))}
            data={yearOptions}
            size="sm"
            style={{ width: 100 }}
          />
          <Select
            aria-label="Owner"
            placeholder="All owners"
            data={ownerOptions}
            value={ownerId}
            onChange={handleOwnerChange}
            clearable
            searchable
            style={{ width: 160 }}
          />
          <Select
            aria-label="Activity"
            placeholder="All rental activities"
            data={activityOptions}
            value={activityId}
            onChange={setActivityId}
            clearable
            searchable
            style={{ width: 210 }}
          />
          <Button
            leftSection={<IconDownload size={16} />}
            variant="default"
            disabled={noData}
            onClick={handleExportCsv}
          >
            Export CSV
          </Button>
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <Card withBorder>
          <Text size="xs" c="dimmed" fw={600} tt="uppercase">
            Rental Income
          </Text>
          <Text size="xl" fw={800} c="green">
            {fmtCurrency(report.rentalIncome)}
          </Text>
        </Card>
        <Card withBorder>
          <Text size="xs" c="dimmed" fw={600} tt="uppercase">
            Schedule E Expenses
          </Text>
          <Text size="xl" fw={800} c="red">
            {fmtCurrency(report.expenses)}
          </Text>
        </Card>
        <Card withBorder>
          <Text size="xs" c="dimmed" fw={600} tt="uppercase">
            Net Rental Income
          </Text>
          <Text size="xl" fw={800} c={Number(report.netIncome) >= 0 ? 'green' : 'red'}>
            {fmtCurrency(report.netIncome)}
          </Text>
        </Card>
      </SimpleGrid>

      {activities.length === 0 ? (
        <Text ta="center" c="dimmed" py="xl">
          No Schedule E rental activities are configured.
        </Text>
      ) : (
        activities.map((row) => (
          <Card withBorder p={0} key={row.activity.id}>
            <Group p="md" justify="space-between">
              <div>
                <Text fw={700}>{row.activity.name}</Text>
                <Text size="xs" c="dimmed">
                  {row.activity.property?.name ?? 'No property'} ·{' '}
                  {row.activity.owner?.name ?? 'Household'}
                </Text>
              </div>
              <Group gap="lg">
                <Text size="sm" c="green">
                  Income {fmtCurrency(row.rentalIncome)}
                </Text>
                <Text size="sm" c="red">
                  Expenses {fmtCurrency(row.expenses)}
                </Text>
                <Text size="sm" fw={700} c={Number(row.netIncome) >= 0 ? 'green' : 'red'}>
                  Net {fmtCurrency(row.netIncome)}
                </Text>
              </Group>
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={100}>Line</Table.Th>
                  <Table.Th>Category</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Amount</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(row.categories ?? []).length === 0 ? (
                  <Table.Tr>
                    <Table.Td colSpan={3}>
                      <Text size="sm" c="dimmed" ta="center" py="md">
                        No Schedule E expenses in {selectedYear}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                ) : (
                  row.categories.map((category) => (
                    <Table.Tr key={category.category.id}>
                      <Table.Td>
                        <Badge variant="outline" color="gray">
                          {category.category.taxLine ?? '—'}
                        </Badge>
                      </Table.Td>
                      <Table.Td>{category.category.label}</Table.Td>
                      <Table.Td c="red" style={{ textAlign: 'right' }}>
                        {fmtCurrency(category.total)}
                      </Table.Td>
                    </Table.Tr>
                  ))
                )}
              </Table.Tbody>
            </Table>
          </Card>
        ))
      )}

      <Text size="xs" c="dimmed">
        Schedule E line mappings are classification metadata, not filing advice. Consult a tax
        professional before filing.
      </Text>
    </Stack>
  );
}
