import { invoke } from '@tauri-apps/api/core';

// In dev, Vite proxies /api to localhost:48763.
// In a Tauri production build the frontend is served from an internal
// origin, so API calls must use the full backend URL from Tauri config.
let BASE;

async function initializeBase() {
  if (import.meta.env.DEV) {
    BASE = '/api';
  } else {
    try {
      const backendUrl = await invoke('get_backend_url');
      BASE = `${backendUrl}/api`;
    } catch {
      // Fallback if Tauri command fails
      BASE = 'http://localhost:48763/api';
    }
  }
}

initializeBase();

export function generateRequestId() {
  // Matches the UUID format the backend generates in RequestCorrelationFilter
  // when a client doesn't supply one, keeping request IDs consistent for log correlation.
  return crypto.randomUUID();
}

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details || {};
    this.requestId = details?.requestId;
  }
}

function parseErrorPayload(contentType, bodyText) {
  if (!bodyText) {
    return { code: null, message: null, details: {} };
  }
  if (contentType?.includes('application/json')) {
    try {
      const parsed = JSON.parse(bodyText);
      return {
        code: parsed?.code ?? null,
        message: parsed?.message ?? parsed?.error ?? null,
        details: parsed?.details ?? {},
      };
    } catch {
      return { code: null, message: null, details: {} };
    }
  }
  return { code: null, message: bodyText, details: {} };
}

async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  const requestId = generateRequestId();
  const res = await fetch(`${BASE}${path}`, {
    headers: isFormData
      ? { 'X-Request-Id': requestId, ...options.headers }
      : { 'Content-Type': 'application/json', 'X-Request-Id': requestId, ...options.headers },
    ...options,
  });
  if (!res.ok) {
    const contentType = res.headers.get('content-type') || '';
    let bodyText = '';
    try {
      bodyText = await res.text();
    } catch {
      bodyText = '';
    }
    const payload = parseErrorPayload(contentType, bodyText);
    const message = payload.message || `HTTP ${res.status}: ${bodyText || 'no body'}`;
    const error = new ApiError(res.status, payload.code, message, payload.details);
    if (res.status === 401 && error.code === 'OUTLOOK_AUTH_REQUIRED') {
      // Replace history so back-button doesn't bounce the user back into a 401 loop.
      window.location.replace('/api/outlook/connect');
    }
    console.error('API error', res.url, message, payload.code, `(requestId: ${error.requestId})`);
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

// Incomes
export const getIncomes = () => request('/incomes');
export const createIncome = (data) =>
  request('/incomes', { method: 'POST', body: JSON.stringify(data) });
export const updateIncome = (id, data) =>
  request(`/incomes/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deleteIncome = (id) => request(`/incomes/${id}`, { method: 'DELETE' });
export const getTotalIncome = () => request('/incomes/total');
export const importVenmoIncomes = (file, payerId, propertyId) => {
  const fd = new FormData();
  fd.append('file', file);
  if (payerId) {
    fd.append('payer', payerId);
  }
  if (propertyId) {
    fd.append('propertyId', propertyId);
  }
  return request('/incomes/import/venmo', { method: 'POST', body: fd });
};

// Expenses
export const getExpenses = () => request('/expenses');
export const createExpense = (data) =>
  request('/expenses', { method: 'POST', body: JSON.stringify(data) });
export const updateExpense = (id, data) =>
  request(`/expenses/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deleteExpense = (id) => request(`/expenses/${id}`, { method: 'DELETE' });
export const getTotalExpenses = () => request('/expenses/total');
export const getExpenseCategories = () => request('/expenses/categories');

// Properties
export const getProperties = () => request('/properties');
export const createProperty = (data) =>
  request('/properties', { method: 'POST', body: JSON.stringify(data) });
export const updateProperty = (id, data) =>
  request(`/properties/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deleteProperty = (id) => request(`/properties/${id}`, { method: 'DELETE' });
export const getPropertyTypes = () => request('/properties/types');
export const getPropertyKeywords = () => request('/properties/keywords');

// Payers
export const getPayers = () => request('/payers');
export const createPayer = (data) =>
  request('/payers', { method: 'POST', body: JSON.stringify(data) });
export const updatePayer = (id, data) =>
  request(`/payers/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const deletePayer = (id) => request(`/payers/${id}`, { method: 'DELETE' });
export const getPayerTypes = () => request('/payers/types');
export const getPayerKeywords = () => request('/payers/keywords');

// Outlook
export const getOutlookStatus = () => request('/outlook/status');
export const getOutlookRentalEmails = (page = 0) => request(`/outlook/emails/rental?page=${page}`);
export const getOutlookEmailContent = (messageId) =>
  request(`/outlook/emails/${encodeURIComponent(messageId)}/content`);
export const parseEmail = (messageId, subject) =>
  request(`/outlook/emails/${encodeURIComponent(messageId)}/parse`, {
    method: 'POST',
    body: JSON.stringify({ subject }),
  });
export const getOutlookAvailableFolders = () => request('/outlook/folders/available');
export const getOutlookFolderSettings = () => request('/outlook/settings/folders');
export const updateOutlookFolderSettings = (folderSettings) =>
  request('/outlook/settings/folders', { method: 'PUT', body: JSON.stringify({ folderSettings }) });
export const getOutlookMoveSettings = () => request('/outlook/settings/move');
export const updateOutlookMoveSettings = (enabled, folderId) =>
  request('/outlook/settings/move', { method: 'PUT', body: JSON.stringify({ enabled, folderId }) });

// Pending expenses
export const getPendingExpenses = () => request('/pending-expenses');
export const savePendingExpense = (id, data) =>
  request(`/pending-expenses/${id}/save`, { method: 'POST', body: JSON.stringify(data) });
export const savePendingIncome = (id, data) =>
  request(`/pending-expenses/${id}/save-income`, { method: 'POST', body: JSON.stringify(data) });
export const dismissPendingExpense = (id) =>
  request(`/pending-expenses/${id}`, { method: 'DELETE' });
export const retryPendingExpense = (id) =>
  request(`/pending-expenses/${id}/retry`, { method: 'POST' });

// Pending income
export const getPendingIncomes = () => request('/incomes/pending');
export const acceptPendingIncome = (id, data) =>
  request(`/incomes/pending/${id}/accept`, { method: 'POST', body: JSON.stringify(data) });
export const rejectPendingIncome = (id) =>
  request(`/incomes/pending/${id}`, { method: 'DELETE' });

// Agent
export const submitExpenseToAgent = (message) =>
  request('/agent/expense', { method: 'POST', body: JSON.stringify({ message }) });

// Backup
export const triggerBackup = () => request('/backup', { method: 'POST' });
export const listBackups = () => request('/backup/list');
export const restoreBackup = (fileId) =>
  request(`/backup/restore/${encodeURIComponent(fileId)}`, { method: 'POST' });
export const deleteBackup = (fileId) =>
  request(`/backup/${encodeURIComponent(fileId)}`, { method: 'DELETE' });

// Receipts
export const listReceipts = () => request('/receipts');
export const deleteReceipt = (itemId) =>
  request(`/receipts/${encodeURIComponent(itemId)}`, { method: 'DELETE' });
export const parseReceipt = (itemId) =>
  request(`/receipts/${encodeURIComponent(itemId)}/parse`, { method: 'POST' });
export const getReceiptSettings = () => request('/receipts/settings');
export const updateReceiptSettings = (folderBase) =>
  request('/receipts/settings', { method: 'PUT', body: JSON.stringify({ folderBase }) });
export const uploadReceipt = (file) => {
  const fd = new FormData();
  fd.append('file', file);
  return request('/receipts/upload', { method: 'POST', body: fd });
};
