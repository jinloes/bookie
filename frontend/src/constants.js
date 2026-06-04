// Centralised string literals shared with the backend so we don't sprinkle
// 'PROCESSING' / 'OUTLOOK_EMAIL' / etc. across components.

export const PENDING_STATUS = {
  PROCESSING: 'PROCESSING',
  READY: 'READY',
  FAILED: 'FAILED',
};

export const EXPENSE_SOURCE = {
  MANUAL: 'MANUAL',
  OUTLOOK_EMAIL: 'OUTLOOK_EMAIL',
  RECEIPT: 'RECEIPT',
};

export const EMAIL_TYPE = {
  EXPENSE: 'EXPENSE',
  INCOME: 'INCOME',
};

export const PAYER_TYPE = {
  COMPANY: 'COMPANY',
  PERSON: 'PERSON',
};
