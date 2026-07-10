import React from 'react';
import { Group, Select, TextInput } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';

export function IncomesFilters({
  filterText,
  setFilterText,
  yearOptions,
  filterYear,
  setFilterYear,
  propertyOptions,
  filterPropertyId,
  setFilterPropertyId,
}) {
  return (
    <Group mb="sm" gap="xs" wrap="wrap">
      <TextInput
        placeholder="Search income…"
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
        leftSection={<IconSearch size={14} />}
        size="xs"
        style={{ width: 200 }}
      />
      <Select
        placeholder="All years"
        data={yearOptions}
        value={filterYear}
        onChange={setFilterYear}
        clearable
        size="xs"
        style={{ width: 110 }}
      />
      {propertyOptions.length > 0 && (
        <Select
          placeholder="All properties"
          value={filterPropertyId}
          onChange={setFilterPropertyId}
          data={propertyOptions}
          clearable
          size="xs"
          style={{ width: 160 }}
        />
      )}
    </Group>
  );
}
