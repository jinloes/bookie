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
import { IconPlus, IconReceipt, IconUpload, IconX } from '@tabler/icons-react';
import { COLORS } from '../designTokens.js';
import { PAYER_TYPE } from '../constants.js';

export function ExpensesForm({ expenseForm }) {
  const { editing, form, onCancel, onSubmit, opened, options, payerModal, receipt, saveError } =
    expenseForm;

  return (
    <>
      <Drawer
        opened={opened}
        onClose={onCancel}
        title={editing ? 'Edit Expense' : 'New Expense'}
        position="right"
        size="lg"
        styles={{ body: { display: 'flex', flexDirection: 'column', height: 'calc(100% - 60px)' } }}
      >
        <form
          onSubmit={form.onSubmit(onSubmit)}
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
                  data={options.payers.map((payer) => ({
                    value: String(payer.id),
                    label: `${payer.name} (${payer.type === PAYER_TYPE.COMPANY ? 'Company' : 'Person'})`,
                  }))}
                  clearable
                  placeholder="— None —"
                />
                <Button
                  variant="default"
                  size="sm"
                  leftSection={<IconPlus size={14} />}
                  onClick={payerModal.open}
                >
                  New
                </Button>
              </Group>
            </Group>
            <Group grow>
              <Select
                label="Property"
                {...form.getInputProps('propertyId')}
                data={options.properties.map((property) => ({
                  value: String(property.id),
                  label: property.name,
                }))}
                clearable
                placeholder="— None —"
              />
              <Select
                label="Category (Schedule E)"
                placeholder="Select category"
                withAsterisk
                {...form.getInputProps('category')}
                data={options.categories.map((category) => ({
                  value: category.value,
                  label: `Line ${category.scheduleELine} — ${category.label}`,
                }))}
              />
            </Group>
            {!editing && (
              <Group align="center">
                {receipt.uploadedReceipt ? (
                  <Badge
                    variant="outline"
                    color="green"
                    leftSection={<IconReceipt size={12} />}
                    rightSection={
                      <ActionIcon
                        size="xs"
                        variant="transparent"
                        onClick={() => receipt.setUploadedReceipt(null)}
                        aria-label="Remove uploaded receipt"
                      >
                        <IconX size={10} />
                      </ActionIcon>
                    }
                  >
                    {receipt.uploadedReceipt.fileName}
                  </Badge>
                ) : (
                  <FileButton onChange={receipt.onUpload} accept="application/pdf,image/*">
                    {(props) => (
                      <Button
                        {...props}
                        variant="default"
                        size="xs"
                        leftSection={<IconUpload size={14} />}
                        loading={receipt.uploading}
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
          <Box pt="md" style={{ borderTop: `1px solid ${COLORS.BORDER}`, flexShrink: 0 }}>
            <Group>
              <Button type="submit">Save</Button>
              <Button variant="default" onClick={onCancel}>
                Cancel
              </Button>
            </Group>
          </Box>
        </form>
      </Drawer>

      <Modal opened={payerModal.opened} onClose={payerModal.close} title="New Payer" size="sm">
        <Stack gap="sm">
          <TextInput label="Name" {...payerModal.form.getInputProps('name')} required />
          <Select
            label="Type"
            {...payerModal.form.getInputProps('type')}
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
              value={payerModal.accountInput}
              onChange={(event) => payerModal.setAccountInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  payerModal.onAddAccount();
                }
              }}
              style={{ flex: 1 }}
            />
            <Button variant="default" onClick={payerModal.onAddAccount}>
              Add
            </Button>
          </Group>
          {payerModal.form.values.accounts.length > 0 && (
            <Group gap={4} wrap="wrap">
              {payerModal.form.values.accounts.map((account, index) => (
                <Badge
                  key={account}
                  variant="outline"
                  color="gray"
                  rightSection={
                    <ActionIcon
                      size="xs"
                      variant="transparent"
                      onClick={() => payerModal.form.removeListItem('accounts', index)}
                      aria-label={`Remove account number ${account}`}
                    >
                      <IconX size={10} />
                    </ActionIcon>
                  }
                >
                  {account}
                </Badge>
              ))}
            </Group>
          )}
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={payerModal.close}>
              Cancel
            </Button>
            <Button disabled={!payerModal.form.values.name.trim()} onClick={payerModal.onSave}>
              Create &amp; Select
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
