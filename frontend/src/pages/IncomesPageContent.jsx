import React from 'react';
import { Button, Group, Stack, Tabs, Text, Title } from '@mantine/core';
import { IncomesFilters } from './IncomesFilters.jsx';
import { IncomesForm } from './IncomesForm.jsx';
import { IncomesImportDrawer } from './IncomesImportDrawer.jsx';
import { IncomesTable } from './IncomesTable.jsx';
import { PendingIncomeReviewDrawer } from './PendingIncomeReviewDrawer.jsx';
import { PendingIncomesPanel } from './PendingIncomesPanel.jsx';

export function IncomesPageContent({
  setShowImportForm,
  openCreateForm,
  showForm,
  cancelForm,
  editing,
  form,
  handleSubmit,
  propertyOptions,
  payerOptions,
  saveError,
  showImportForm,
  cancelImportForm,
  importPayerId,
  setImportPayerId,
  importFile,
  setImportFile,
  importError,
  handleImportSubmit,
  visibleIncomes,
  pendingIncomes,
  filterText,
  setFilterText,
  yearOptions,
  filterYear,
  setFilterYear,
  filterPropertyId,
  setFilterPropertyId,
  highlightId,
  handleEdit,
  handleDelete,
  pendingLoading,
  handleAcceptAllPending,
  openPendingReview,
  reviewingPendingId,
  closePendingReview,
  reviewingForm,
  setReviewingForm,
  handleAcceptPending,
  handleRejectPending,
}) {
  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>Income</Title>
        <Group>
          <Button variant="default" onClick={() => setShowImportForm(true)}>
            Import Venmo CSV
          </Button>
          <Button onClick={openCreateForm}>+ Add Income</Button>
        </Group>
      </Group>

      <Text size="sm" c="dimmed">
        Finalized income records live here. New email and receipt items are reviewed in the Review
        Queue tab.
      </Text>

      <IncomesForm
        showForm={showForm}
        cancelForm={cancelForm}
        editing={editing}
        form={form}
        handleSubmit={handleSubmit}
        propertyOptions={propertyOptions}
        payerOptions={payerOptions}
        saveError={saveError}
      />
      <IncomesImportDrawer
        showImportForm={showImportForm}
        cancelImportForm={cancelImportForm}
        importPayerId={importPayerId}
        setImportPayerId={setImportPayerId}
        payerOptions={payerOptions}
        importFile={importFile}
        setImportFile={setImportFile}
        importError={importError}
        handleImportSubmit={handleImportSubmit}
      />

      <Tabs defaultValue="finalized">
        <Tabs.List>
          <Tabs.Tab value="finalized">Finalized ({visibleIncomes.length})</Tabs.Tab>
          <Tabs.Tab value="pending">Pending ({pendingIncomes.length})</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="finalized" pt="md">
          <IncomesFilters
            filterText={filterText}
            setFilterText={setFilterText}
            yearOptions={yearOptions}
            filterYear={filterYear}
            setFilterYear={setFilterYear}
            propertyOptions={propertyOptions}
            filterPropertyId={filterPropertyId}
            setFilterPropertyId={setFilterPropertyId}
          />
          <IncomesTable
            visibleIncomes={visibleIncomes}
            highlightId={highlightId}
            filterYear={filterYear}
            filterText={filterText}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        </Tabs.Panel>

        <Tabs.Panel value="pending" pt="md">
          <PendingIncomesPanel
            pendingLoading={pendingLoading}
            pendingIncomes={pendingIncomes}
            handleAcceptAllPending={handleAcceptAllPending}
            onReview={openPendingReview}
          />
        </Tabs.Panel>
      </Tabs>

      <PendingIncomeReviewDrawer
        reviewingPendingId={reviewingPendingId}
        closePendingReview={closePendingReview}
        reviewingForm={reviewingForm}
        setReviewingForm={setReviewingForm}
        pendingIncomes={pendingIncomes}
        propertyOptions={propertyOptions}
        handleAcceptPending={handleAcceptPending}
        handleRejectPending={handleRejectPending}
      />
    </Stack>
  );
}
