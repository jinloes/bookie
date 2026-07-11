import React from 'react';
import { Group, Select, TextInput } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';

export function IncomesFilters({ filters }) {
  return (
    <Group mb="sm" gap="xs" wrap="wrap">
      <TextInput
        placeholder="Search income…"
        value={filters.text}
        onChange={(event) => filters.setText(event.target.value)}
        leftSection={<IconSearch size={14} />}
        size="xs"
        style={{ width: 200 }}
      />
      <Select
        placeholder="All years"
        data={filters.yearOptions}
        value={filters.year}
        onChange={filters.setYear}
        clearable
        size="xs"
        style={{ width: 110 }}
      />
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
    </Group>
  );
}
