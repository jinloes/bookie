// Centralised string literals shared with the backend so we don't sprinkle
// 'PROCESSING' / 'OUTLOOK_EMAIL' / etc. across components.

// Durable inbox states. RECEIVED, QUEUED and PROCESSING are transient: the item is still being
// parsed and the UI keeps polling until it settles.
export const PENDING_STATUS = {
  RECEIVED: 'RECEIVED',
  QUEUED: 'QUEUED',
  PROCESSING: 'PROCESSING',
  READY: 'READY',
  FAILED: 'FAILED',
};

export const TRANSIENT_INBOX_STATES = [
  PENDING_STATUS.RECEIVED,
  PENDING_STATUS.QUEUED,
  PENDING_STATUS.PROCESSING,
];

export const isTransientInboxState = (state) => TRANSIENT_INBOX_STATES.includes(state);

export const EXPENSE_SOURCE = {
  MANUAL: 'MANUAL',
  OUTLOOK_EMAIL: 'OUTLOOK_EMAIL',
  RECEIPT: 'RECEIPT',
  VENMO: 'VENMO',
  BANK: 'BANK',
};

// Statement imports that arrive as ready income awaiting acceptance rather than parsed review items.
const IMPORTED_INCOME_ORIGINS = [EXPENSE_SOURCE.VENMO, EXPENSE_SOURCE.BANK];

export const isImportedIncomeOrigin = (origin) => IMPORTED_INCOME_ORIGINS.includes(origin);

export const importedIncomeSourceLabel = (origin) =>
  origin === EXPENSE_SOURCE.BANK ? 'bank statement' : 'Venmo CSV';

export const EMAIL_TYPE = {
  EXPENSE: 'EXPENSE',
  INCOME: 'INCOME',
};

export const PAYER_TYPE = {
  COMPANY: 'COMPANY',
  PERSON: 'PERSON',
};
