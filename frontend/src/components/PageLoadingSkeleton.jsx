import React from 'react';
import { Card, Group, SimpleGrid, Skeleton, Stack } from '@mantine/core';

function SkeletonRows({ rowCount = 5, includeSecondaryLine = false }) {
  return (
    <Stack gap="sm">
      {Array.from({ length: rowCount }).map((_, index) => (
        <Group key={index} justify="space-between" align="flex-start" wrap="nowrap">
          <Stack gap={6} style={{ flex: 1 }}>
            <Skeleton height={14} width={index % 2 === 0 ? '38%' : '52%'} />
            {includeSecondaryLine && (
              <Skeleton height={11} width={index % 2 === 0 ? '24%' : '30%'} />
            )}
          </Stack>
          <Skeleton height={14} width={72} />
        </Group>
      ))}
    </Stack>
  );
}

export function TablePageSkeleton({
  showDescription = true,
  actionCount = 1,
  filterCount = 4,
  rowCount = 5,
  includeSecondaryLine = true,
}) {
  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-start">
        <Stack gap="xs" style={{ flex: 1 }}>
          <Skeleton height={30} width="12rem" />
          {showDescription && <Skeleton height={14} width="24rem" maw="100%" />}
        </Stack>
        <Group>
          {Array.from({ length: actionCount }).map((_, index) => (
            <Skeleton key={index} height={36} width={index === 0 ? 120 : 132} radius="md" />
          ))}
        </Group>
      </Group>

      {filterCount > 0 && (
        <Group align="stretch" wrap="wrap">
          {Array.from({ length: filterCount }).map((_, index) => (
            <Skeleton key={index} height={36} w={index === 0 ? 220 : 140} radius="md" />
          ))}
        </Group>
      )}

      <Card withBorder>
        <SkeletonRows rowCount={rowCount} includeSecondaryLine={includeSecondaryLine} />
      </Card>
    </Stack>
  );
}

export function SummaryPageSkeleton({
  metricCount = 3,
  cardCount = 2,
  rowCount = 4,
  showDescription = true,
  actionCount = 1,
}) {
  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-start">
        <Stack gap="xs" style={{ flex: 1 }}>
          <Skeleton height={30} width="12rem" />
          {showDescription && <Skeleton height={14} width="28rem" maw="100%" />}
        </Stack>
        <Group>
          {Array.from({ length: actionCount }).map((_, index) => (
            <Skeleton key={index} height={36} width={index === 0 ? 96 : 120} radius="md" />
          ))}
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 1, sm: Math.min(metricCount, 3), md: metricCount }}>
        {Array.from({ length: metricCount }).map((_, index) => (
          <Card key={index} withBorder>
            <Stack gap="xs">
              <Skeleton height={12} width="42%" />
              <Skeleton height={28} width={index % 2 === 0 ? '58%' : '46%'} />
            </Stack>
          </Card>
        ))}
      </SimpleGrid>

      {Array.from({ length: cardCount }).map((_, index) => (
        <Card key={index} withBorder>
          <Stack gap="md">
            <Group justify="space-between">
              <Skeleton height={16} width={index === 0 ? '34%' : '28%'} />
              <Skeleton height={14} width={72} />
            </Group>
            <SkeletonRows rowCount={rowCount} includeSecondaryLine />
          </Stack>
        </Card>
      ))}
    </Stack>
  );
}
