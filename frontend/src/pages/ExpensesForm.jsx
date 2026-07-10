import React from 'react';
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Drawer,
  FileButton,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import {
  IconPlus,
  IconReceipt,
  IconUpload,
  IconX,
} from '@tabler/icons-react';
import { COLORS } from '../designTokens.js';
import { PAYER_TYPE } from '../constants.js';

export function ExpensesForm({
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
}) {
  return (
    <>
      <Drawer
        opened={showForm}
        onClose={cancelForm}
        title={editing ? 'Edit Expense' : 'New Expense'}
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        <form
          onSubmit={form.onSubmit(handleSubmit)}
          style={{ display: 'flex', flexDirection: 'column', flex: 1 }}
        >
          <Stack gap="sm" style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
            <Group grow>
              <NumberInput
                label="Amount"
                {...form.getInputProps('amount')}
                min={0}
                decimalScale={2}
                prefix="$"
                required
              />
              <TextInput label="Description" {...form.getInputProps('description')} required />
            </Group>
            <Group grow>
              <TextInput label="Date" type="date" {...form.getInputProps('date')} required />
              <Group gap="xs" align="flex-end" wrap="nowrap">
                <Select
                  label="Payer"
                  style={{ flex: 1 }}
                  {...form.getInputProps('payerId')}
                  data={payers.map((p) => ({
                    value: String(p.id),
                    label: `${p.name} (${p.type === PAYER_TYPE.COMPANY ? 'Company' : 'Person'})`,
                  }))}
                  clearable
                  placeholder="— None —"
                />
                <Button
                  variant="default"
                  size="sm"
                  leftSection={<IconPlus size={14} />}
                  onClick={openPayerModal}
                >
                  New
                </Button>
              </Group>
            </Group>
            <Group grow>
              <Select
                label="Property"
                {...form.getInputProps('propertyId')}
                data={properties.map((p) => ({ value: String(p.id), label: p.name }))}
                clearable
                placeholder="— None —"
              />
              <Select
                label="Category (Schedule E)"
                placeholder="Select category"
                withAsterisk
                {...form.getInputProps('category')}
                data={categories.map((c) => ({
                  value: c.value,
                  label: `Line ${c.scheduleELine} — ${c.label}`,
                }))}
              />
            </Group>
            {!editing && (
              <Group align="center">
                {uploadedReceipt ? (
                  <Badge
                    variant="outline"
                    color="green"
                    leftSection={<IconReceipt size={12} />}
                    rightSection={
                      <ActionIcon
                        size="xs"
                        variant="transparent"
                        onClick={() => setUploadedReceipt(null)}
                        aria-label="Remove uploaded receipt"
                      >
                        <IconX size={10} />
                      </ActionIcon>
                    }
                  >
                    {uploadedReceipt.fileName}
                  </Badge>
                ) : (
                  <FileButton onChange={handleReceiptUpload} accept="application/pdf,image/*">
                    {(props) => (
                      <Button
                        {...props}
                        variant="default"
                        size="xs"
                        leftSection={<IconUpload size={14} />}
                        loading={receiptUploading}
                      >
                        Attach Receipt
                      </Button>
                    )}
                  </FileButton>
                )}
              </Group>
            )}
            {saveError && (
              <Text c="red" size="sm">
                {saveError}
              </Text>
            )}
          </Stack>
          <Box
            pt="md"
            style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}
          >
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={cancelForm}>
                Cancel
              </Button>
            </Group>
          </Box>
        </form>
      </Drawer>

      <Modal
        opened={payerModalOpen}
        onClose={() => {
          setPayerModalOpen(false);
          setPayerAccountInput('');
        }}
        title="New Payer"
        size="sm"
      >
        <Stack gap="sm">
          <TextInput label="Name" {...payerForm.getInputProps('name')} required />
          <Select
            label="Type"
            {...payerForm.getInputProps('type')}
            data={[
              { value: PAYER_TYPE.COMPANY, label: 'Company' },
              { value: PAYER_TYPE.PERSON, label: 'Person' },
            ]}
          />
          <Group align="flex-end">
            <TextInput
              label="Account Numbers"
              description="Used to auto-identify this payer from future emails"
              placeholder="Add account number"
              value={payerAccountInput}
              onChange={(e) => setPayerAccountInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addPayerAccount();
                }
              }}
              style={{ flex: 1 }}
            />
            <Button variant="default" onClick={addPayerAccount}>
              Add
            </Button>
          </Group>
          {payerForm.values.accounts.length > 0 && (
            <Group gap={4} wrap="wrap">
              {payerForm.values.accounts.map((a, i) => (
                <Badge
                  key={a}
                  variant="outline"
                  color="gray"
                  rightSection={
                    <ActionIcon
                      size="xs"
                      variant="transparent"
                      onClick={() => payerForm.removeListItem('accounts', i)}
                      aria-label={`Remove account number ${a}`}
                    >
                      <IconX size={10} />
                    </ActionIcon>
                  }
                >
                  {a}
                </Badge>
              ))}
            </Group>
          )}
          <Group justify="flex-end" mt="xs">
            <Button
              variant="default"
              onClick={() => {
                setPayerModalOpen(false);
                setPayerAccountInput('');
              }}
            >
              Cancel
            </Button>
            <Button disabled={!payerForm.values.name.trim()} onClick={handlePayerModalSave}>
              Create &amp; Select
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
