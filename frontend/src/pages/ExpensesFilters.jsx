import React from 'react';
import { Group, Select, TextInput } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';

export function ExpensesFilters({ filters }) {
  return (
    <Group mb="sm" gap="xs" wrap="wrap">
      <TextInput
        placeholder="Search expenses…"
        value={filters.text}
        onChange={(event) => filters.setText(event.target.value)}
        leftSection={<IconSearch size={14} />}
        size="xs"
        style={{ width: 200 }}
        clearable
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
          style={{ width: 160 }}
        />
      )}
      {filters.payerOptions.length > 0 && (
        <Select
          placeholder="All payers"
          value={filters.payerId}
          onChange={filters.setPayerId}
          data={filters.payerOptions}
          clearable
          size="xs"
          style={{ width: 200 }}
        />
      )}
    </Group>
  );
}
