// @ts-check
// Thin, stable-named wrappers around the generated OpenAPI client (frontend/src/generated/api),
// instantiated in ./client.js. Every page/hook in the app imports from here rather than the
// generated classes directly, so this file is the one seam that absorbs the difference between
// the generated client's `{ id, xRequest: {...} }` parameter-object shape and the flatter
// `(id, data)` call style the rest of the app uses.
import {
  agentApi,
  backupApi,
  expenseApi,
  incomeApi,
  outlookApi,
  payerApi,
  pendingExpenseApi,
  propertyApi,
  rawMultipartRequest,
  receiptApi,
} from './client.js';

export { ApiError, generateRequestId } from './client.js';

/**
 * @typedef {import('../generated/api/models/ExpenseResponse').ExpenseResponse} ExpenseResponse
 * @typedef {import('../generated/api/models/CreateExpenseRequest').CreateExpenseRequest} CreateExpenseRequest
 * @typedef {import('../generated/api/models/UpdateExpenseRequest').UpdateExpenseRequest} UpdateExpenseRequest
 * @typedef {import('../generated/api/models/IncomeResponse').IncomeResponse} IncomeResponse
 * @typedef {import('../generated/api/models/CreateIncomeRequest').CreateIncomeRequest} CreateIncomeRequest
 * @typedef {import('../generated/api/models/UpdateIncomeRequest').UpdateIncomeRequest} UpdateIncomeRequest
 * @typedef {import('../generated/api/models/PropertyResponse').PropertyResponse} PropertyResponse
 * @typedef {import('../generated/api/models/CreatePropertyRequest').CreatePropertyRequest} CreatePropertyRequest
 * @typedef {import('../generated/api/models/UpdatePropertyRequest').UpdatePropertyRequest} UpdatePropertyRequest
 * @typedef {import('../generated/api/models/PayerResponse').PayerResponse} PayerResponse
 * @typedef {import('../generated/api/models/UpsertPayerRequest').UpsertPayerRequest} UpsertPayerRequest
 * @typedef {import('../generated/api/models/PendingExpenseResponse').PendingExpenseResponse} PendingExpenseResponse
 * @typedef {import('../generated/api/models/PendingIncomeResponse').PendingIncomeResponse} PendingIncomeResponse
 * @typedef {import('../generated/api/models/SavePendingExpenseRequest').SavePendingExpenseRequest} SavePendingExpenseRequest
 * @typedef {import('../generated/api/models/SavePendingIncomeRequest').SavePendingIncomeRequest} SavePendingIncomeRequest
 * @typedef {import('../generated/api/models/ReceiptDto').ReceiptDto} ReceiptDto
 * @typedef {import('../generated/api/models/UploadReceiptResponse').UploadReceiptResponse} UploadReceiptResponse
 */

// Incomes
/** @returns {Promise<IncomeResponse[]>} */
export const getIncomes = () => incomeApi.getIncomes();
/** @param {CreateIncomeRequest} data @returns {Promise<IncomeResponse>} */
export const createIncome = (data) => incomeApi.createIncome({ createIncomeRequest: data });
/** @param {number|string} id @param {UpdateIncomeRequest} data @returns {Promise<IncomeResponse>} */
export const updateIncome = (id, data) =>
  incomeApi.updateIncome({ id: Number(id), updateIncomeRequest: data });
export const deleteIncome = (id) => incomeApi.deleteIncome({ id: Number(id) });
export const getTotalIncome = () => incomeApi.getIncomesTotal();
export const importVenmoIncomes = (file, payerId, propertyId) => {
  const fd = new FormData();
  fd.append('file', file);
  if (payerId) {
    fd.append('payer', payerId);
  }
  if (propertyId) {
    fd.append('propertyId', propertyId);
  }
  return rawMultipartRequest('/api/incomes/import/venmo', fd);
};

// Expenses
/** @returns {Promise<ExpenseResponse[]>} */
export const getExpenses = () => expenseApi.getExpenses();
/** @param {CreateExpenseRequest} data @returns {Promise<ExpenseResponse>} */
export const createExpense = (data) => expenseApi.createExpense({ createExpenseRequest: data });
/** @param {number|string} id @param {UpdateExpenseRequest} data @returns {Promise<ExpenseResponse>} */
export const updateExpense = (id, data) =>
  expenseApi.updateExpense({ id: Number(id), updateExpenseRequest: data });
export const deleteExpense = (id) => expenseApi.deleteExpense({ id: Number(id) });
export const getTotalExpenses = () => expenseApi.getExpensesTotal();
export const getExpenseCategories = () => expenseApi.getExpenseCategories();

// Properties
/** @returns {Promise<PropertyResponse[]>} */
export const getProperties = () => propertyApi.getProperties();
/** @param {CreatePropertyRequest} data @returns {Promise<PropertyResponse>} */
export const createProperty = (data) => propertyApi.createProperty({ createPropertyRequest: data });
/** @param {number|string} id @param {UpdatePropertyRequest} data @returns {Promise<PropertyResponse>} */
export const updateProperty = (id, data) =>
  propertyApi.updateProperty({ id: Number(id), updatePropertyRequest: data });
export const deleteProperty = (id) => propertyApi.deleteProperty({ id: Number(id) });
export const getPropertyTypes = () => propertyApi.getPropertyTypes();
export const getPropertyKeywords = () => propertyApi.getPropertyKeywords();

// Payers
/** @returns {Promise<PayerResponse[]>} */
export const getPayers = () => payerApi.getPayers();
/** @param {UpsertPayerRequest} data @returns {Promise<PayerResponse>} */
export const createPayer = (data) => payerApi.createPayer({ upsertPayerRequest: data });
/** @param {number|string} id @param {UpsertPayerRequest} data @returns {Promise<PayerResponse>} */
export const updatePayer = (id, data) =>
  payerApi.updatePayer({ id: Number(id), upsertPayerRequest: data });
export const deletePayer = (id) => payerApi.deletePayer({ id: Number(id) });
export const getPayerTypes = () => payerApi.getPayerTypes();
export const getPayerKeywords = () => payerApi.getPayerKeywords();

// Outlook
export const getOutlookStatus = () => outlookApi.getOutlookStatus();
export const getOutlookRentalEmails = (page = 0) => outlookApi.getOutlookRentalEmails({ page });
export const getOutlookEmailContent = (messageId) =>
  outlookApi.getOutlookEmailContent({ messageId });
export const parseEmail = (messageId, subject) =>
  outlookApi.parseOutlookEmail({ messageId, requestBody: { subject } });
export const getOutlookAvailableFolders = () => outlookApi.getOutlookAvailableFolders();
export const getOutlookFolderSettings = () => outlookApi.getOutlookFolderSettings();
export const updateOutlookFolderSettings = (folderSettings) =>
  outlookApi.updateOutlookFolderSettings({ requestBody: { folderSettings } });
export const getOutlookMoveSettings = () => outlookApi.getOutlookMoveSettings();
export const updateOutlookMoveSettings = (enabled, folderId) =>
  outlookApi.updateOutlookMoveSettings({ requestBody: { enabled, folderId } });

// Pending expenses
/** @returns {Promise<PendingExpenseResponse[]>} */
export const getPendingExpenses = () => pendingExpenseApi.getPendingExpenses();
/** @param {number|string} id @param {SavePendingExpenseRequest} data @returns {Promise<ExpenseResponse>} */
export const savePendingExpense = (id, data) =>
  pendingExpenseApi.createExpenseFromPendingExpense({
    id: Number(id),
    savePendingExpenseRequest: data,
  });
/** @param {number|string} id @param {SavePendingIncomeRequest} data @returns {Promise<IncomeResponse>} */
export const savePendingIncome = (id, data) =>
  pendingExpenseApi.createIncomeFromPendingExpense({
    id: Number(id),
    savePendingIncomeRequest: data,
  });
export const dismissPendingExpense = (id) =>
  pendingExpenseApi.dismissPendingExpense({ id: Number(id) });
export const retryPendingExpense = (id) =>
  pendingExpenseApi.retryPendingExpense({ id: Number(id) });

// Pending income
/** @returns {Promise<PendingIncomeResponse[]>} */
export const getPendingIncomes = () => incomeApi.getPendingIncomes();
/** @param {number|string} id @param {SavePendingIncomeRequest} data @returns {Promise<IncomeResponse>} */
export const acceptPendingIncome = (id, data) =>
  incomeApi.acceptPendingIncome({ id: Number(id), updateIncomeRequest: data });
export const rejectPendingIncome = (id) => incomeApi.rejectPendingIncome({ id: Number(id) });

// Agent
export const submitExpenseToAgent = (message) =>
  agentApi.processExpenseAgentMessage({ requestBody: { message } });

// Backup
export const triggerBackup = () => backupApi.createBackup();
export const listBackups = () => backupApi.getBackups();
export const restoreBackup = (fileId) => backupApi.restoreBackup({ fileId });
export const deleteBackup = (fileId) => backupApi.deleteBackup({ fileId });

// Receipts
/** @returns {Promise<ReceiptDto[]>} */
export const listReceipts = () => receiptApi.getReceipts();
export const deleteReceipt = (itemId) => receiptApi.deleteReceipt({ itemId });
export const parseReceipt = (itemId) => receiptApi.parseReceipt({ itemId });
export const getReceiptSettings = () => receiptApi.getReceiptSettings();
export const updateReceiptSettings = (folderBase, importFolders = []) =>
  receiptApi.updateReceiptSettings({ requestBody: { folderBase, importFolders } });
/** @param {File} file @returns {Promise<UploadReceiptResponse>} */
export const uploadReceipt = (file) => receiptApi.uploadReceipt({ file });
