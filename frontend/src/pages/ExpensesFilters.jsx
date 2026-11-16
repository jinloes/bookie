import React from 'react';
import { Button, Group, Select, TextInput } from '@mantine/core';
import { IconSearch, IconX } from '@tabler/icons-react';

export function ExpensesFilters({ filters }) {
  return (
    <Group gap="xs" wrap="wrap">
      <TextInput
        placeholder="Search expenses…"
        value={filters.text}
        onChange={(event) => filters.setText(event.target.value)}
        leftSection={<IconSearch size={14} />}
        size="xs"
        style={{ width: 200 }}
      />
      {filters.yearOptions.length > 0 && (
        <Select
          placeholder="All years"
          value={filters.year}
          onChange={filters.setYear}
          data={filters.yearOptions}
          clearable
          size="xs"
          style={{ width: 110 }}
        />
      )}
      {filters.categoryOptions.length > 0 && (
        <Select
          placeholder="All categories"
          value={filters.category}
          onChange={filters.setCategory}
          data={filters.categoryOptions}
          clearable
          searchable
          size="xs"
          style={{ width: 170 }}
        />
      )}
      {filters.activityOptions.length > 0 && (
        <Select
          placeholder="All activities"
          value={filters.activityId}
          onChange={filters.setActivityId}
          data={filters.activityOptions}
          clearable
          searchable
          size="xs"
          style={{ width: 170 }}
        />
      )}
      {filters.ownerOptions.length > 0 && (
        <Select
          placeholder="All household members"
          value={filters.ownerId}
          onChange={filters.setOwnerId}
          data={filters.ownerOptions}
          clearable
          size="xs"
          style={{ width: 180 }}
        />
      )}
      {filters.propertyOptions.length > 0 && (
        <Select
          placeholder="All properties"
          value={filters.propertyId}
          onChange={filters.setPropertyId}
          data={filters.propertyOptions}
          clearable
          size="xs"
          style={{ width: 150 }}
        />
      )}
      {filters.payerOptions.length > 0 && (
        <Select
          placeholder="All counterparties"
          value={filters.payerId}
          onChange={filters.setPayerId}
          data={filters.payerOptions}
          clearable
          searchable
          size="xs"
          style={{ width: 180 }}
        />
      )}
      {filters.hasActiveFilters && (
        <Button
          size="xs"
          variant="subtle"
          color="gray"
          leftSection={<IconX size={13} />}
          onClick={filters.clearFilters}
        >
          Clear filters
        </Button>
      )}
    </Group>
  );
}
