import React from 'react';
import { Button, Group, Stack, Text, Title } from '@mantine/core';
import { ExpensesFilters } from './ExpensesFilters.jsx';
import { ExpensesForm } from './ExpensesForm.jsx';
import { ExpensesTable } from './ExpensesTable.jsx';

export function ExpensesPageContent({
  openCreateForm,
  showForm,
  cancelForm,
  editing,
  form,
  handleSubmit,
  payers,
  properties,
  categories,
  openPayerModal,
  payerModalOpen,
  setPayerModalOpen,
  payerForm,
  payerAccountInput,
  setPayerAccountInput,
  addPayerAccount,
  handlePayerModalSave,
  uploadedReceipt,
  setUploadedReceipt,
  handleReceiptUpload,
  receiptUploading,
  saveError,
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
  visibleExpenses,
  highlightId,
  handleEdit,
  handleDelete,
}) {
  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Expenses</Title>
        <Button onClick={openCreateForm}>+ Add Expense</Button>
      </Group>

      <Text size="sm" c="dimmed">
        Finalized expenses live here. New receipt and email items are reviewed in the Review Queue tab.
      </Text>

      <ExpensesForm
        showForm={showForm}
        cancelForm={cancelForm}
        editing={editing}
        form={form}
        handleSubmit={handleSubmit}
        payers={payers}
        properties={properties}
        categories={categories}
        openPayerModal={openPayerModal}
        payerModalOpen={payerModalOpen}
        setPayerModalOpen={setPayerModalOpen}
        payerForm={payerForm}
        payerAccountInput={payerAccountInput}
        setPayerAccountInput={setPayerAccountInput}
        addPayerAccount={addPayerAccount}
        handlePayerModalSave={handlePayerModalSave}
        uploadedReceipt={uploadedReceipt}
        setUploadedReceipt={setUploadedReceipt}
        handleReceiptUpload={handleReceiptUpload}
        receiptUploading={receiptUploading}
        saveError={saveError}
      />

      <ExpensesFilters
        filterText={filterText}
        setFilterText={setFilterText}
        yearOptions={yearOptions}
        filterYear={filterYear}
        setFilterYear={setFilterYear}
        categoryOptions={categoryOptions}
        filterCategory={filterCategory}
        setFilterCategory={setFilterCategory}
        propertyFilterOptions={propertyFilterOptions}
        filterPropertyId={filterPropertyId}
        setFilterPropertyId={setFilterPropertyId}
        payerOptions={payerOptions}
        filterPayerId={filterPayerId}
        setFilterPayerId={setFilterPayerId}
      />

      <ExpensesTable
        visibleExpenses={visibleExpenses}
        categories={categories}
        highlightId={highlightId}
        filterPayerId={filterPayerId}
        filterYear={filterYear}
        filterText={filterText}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />
    </Stack>
  );
}
