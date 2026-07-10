import React from 'react';
import { Group, Select, TextInput } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';

export function ExpensesFilters({
  filterText,
  setFilterText,
  yearOptions,
  filterYear,
  setFilterYear,
  categoryOptions,
  filterCategory,
  setFilterCategory,
  propertyFilterOptions,
  filterPropertyId,
  setFilterPropertyId,
  payerOptions,
  filterPayerId,
  setFilterPayerId,
}) {
  return (
    <Group mb="sm" gap="xs" wrap="wrap">
      <TextInput
        placeholder="Search expenses…"
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
        leftSection={<IconSearch size={14} />}
        size="xs"
        style={{ width: 200 }}
        clearable
      />
      {yearOptions.length > 0 && (
        <Select
          placeholder="All years"
          value={filterYear}
          onChange={setFilterYear}
          data={yearOptions}
          clearable
          size="xs"
          style={{ width: 110 }}
        />
      )}
      {categoryOptions.length > 0 && (
        <Select
          placeholder="All categories"
          value={filterCategory}
          onChange={setFilterCategory}
          data={categoryOptions}
          clearable
          searchable
          size="xs"
          style={{ width: 180 }}
        />
      )}
      {propertyFilterOptions.length > 0 && (
        <Select
          placeholder="All properties"
          value={filterPropertyId}
          onChange={setFilterPropertyId}
          data={propertyFilterOptions}
          clearable
          size="xs"
          style={{ width: 160 }}
        />
      )}
      {payerOptions.length > 0 && (
        <Select
          placeholder="All payers"
          value={filterPayerId}
          onChange={setFilterPayerId}
          data={payerOptions}
          clearable
          size="xs"
          style={{ width: 200 }}
        />
      )}
    </Group>
  );
}
