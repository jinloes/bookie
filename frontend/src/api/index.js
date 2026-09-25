// @ts-check
// Thin, stable-named wrappers around the generated OpenAPI client (frontend/src/generated/api),
// instantiated in ./client.js. Every page/hook in the app imports from here rather than the
// generated classes directly, so this file is the one seam that absorbs the difference between
// the generated client's `{ id, xRequest: {...} }` parameter-object shape and the flatter
// `(id, data)` call style the rest of the app uses.
import {
  agentApi,
  backupApi,
  classificationHistoryApi,
  counterpartyApi,
  financialActivityApi,
  financialCategoryApi,
  householdMemberApi,
  inboxApi,
  outlookApi,
  propertyApi,
  rawMultipartRequest,
  receiptApi,
  reportApi,
  transactionApi,
} from './client.js';

export { ApiError, generateRequestId } from './client.js';

/**
 * @typedef {import('../generated/api/models/PropertyResponse').PropertyResponse} PropertyResponse
 * @typedef {import('../generated/api/models/CreatePropertyRequest').CreatePropertyRequest} CreatePropertyRequest
 * @typedef {import('../generated/api/models/UpdatePropertyRequest').UpdatePropertyRequest} UpdatePropertyRequest
 * @typedef {import('../generated/api/models/CounterpartyResponse').CounterpartyResponse} CounterpartyResponse
 * @typedef {import('../generated/api/models/UpsertCounterpartyRequest').UpsertCounterpartyRequest} UpsertCounterpartyRequest
 * @typedef {import('../generated/api/models/InboxItemResponse').InboxItemResponse} InboxItemResponse
 * @typedef {import('../generated/api/models/SaveInboxItemRequest').SaveInboxItemRequest} SaveInboxItemRequest
 * @typedef {import('../generated/api/models/VenmoImportResponse').VenmoImportResponse} VenmoImportResponse
 * @typedef {import('../generated/api/models/ReceiptDto').ReceiptDto} ReceiptDto
 * @typedef {import('../generated/api/models/UploadReceiptResponse').UploadReceiptResponse} UploadReceiptResponse
 * @typedef {import('../generated/api/models/FinancialActivityResponse').FinancialActivityResponse} FinancialActivityResponse
 * @typedef {import('../generated/api/models/UpsertFinancialActivityRequest').UpsertFinancialActivityRequest} UpsertFinancialActivityRequest
 * @typedef {import('../generated/api/models/HouseholdMemberResponse').HouseholdMemberResponse} HouseholdMemberResponse
 * @typedef {import('../generated/api/models/UpsertHouseholdMemberRequest').UpsertHouseholdMemberRequest} UpsertHouseholdMemberRequest
 * @typedef {import('../generated/api/models/TransactionResponse').TransactionResponse} TransactionResponse
 * @typedef {import('../generated/api/models/CreateTransactionRequest').CreateTransactionRequest} CreateTransactionRequest
 * @typedef {import('../generated/api/models/UpdateTransactionRequest').UpdateTransactionRequest} UpdateTransactionRequest
 */

// Unified transactions (v2)
/** @returns {Promise<TransactionResponse[]>} */
export const getTransactions = () => transactionApi.getTransactions();
/** @param {number|string} id @returns {Promise<TransactionResponse>} */
export const getTransactionById = (id) => transactionApi.getTransactionById({ id: Number(id) });
/** @param {CreateTransactionRequest} data @returns {Promise<TransactionResponse>} */
export const createTransaction = (data) =>
  transactionApi.createTransaction({ createTransactionRequest: data });
/** @param {number|string} id @param {UpdateTransactionRequest} data @returns {Promise<TransactionResponse>} */
export const updateTransaction = (id, data) =>
  transactionApi.updateTransaction({
    id: Number(id),
    updateTransactionRequest: data,
  });
/** @param {number|string} id @param {number} version */
export const deleteTransaction = (id, version) =>
  transactionApi.deleteTransaction({ id: Number(id), version });

/**
 * Flattens a unified transaction into the row shape the ledger tables and forms render. The
 * transaction references its property and counterparty only by ID, so the caller supplies the
 * catalogs it already loaded; the first import reference and attachment carry the row's source.
 *
 * @param {TransactionResponse} transaction
 * @param {{ properties?: PropertyResponse[], counterparties?: CounterpartyResponse[] }} [catalogs]
 */
export function toLedgerRow(transaction, { properties = [], counterparties = [] } = {}) {
  const reference = transaction.importReferences?.[0] ?? null;
  const attachment = transaction.attachments?.[0] ?? null;
  return {
    ...transaction,
    property:
      properties.find((property) => property.id === transaction.propertyId) ??
      transaction.activity?.property ??
      null,
    payer:
      counterparties.find((counterparty) => counterparty.id === transaction.counterpartyId) ?? null,
    financialCategory: transaction.category ?? null,
    category: transaction.category?.key ?? null,
    sourceType: reference?.origin ?? null,
    sourceId: reference?.externalId ?? null,
    source: reference?.sourceLabel ?? null,
    receiptOneDriveId: attachment?.externalId ?? null,
    receiptFileName: attachment?.fileName ?? null,
  };
}

/**
 * Builds a create/update body that keeps a row's existing source identity and attachment, since
 * an update replaces both with whatever the request carries.
 *
 * @param {ReturnType<typeof toLedgerRow> | null} existing
 */
export function preservedTransactionSource(existing) {
  const reference = existing?.importReferences?.[0] ?? null;
  const attachment = existing?.attachments?.[0] ?? null;
  return {
    // Import references store the origin as free text; it is always one of the request enum values.
    origin: /** @type {CreateTransactionRequest['origin']} */ (reference?.origin ?? undefined),
    externalId: reference?.externalId ?? undefined,
    attachmentExternalId: attachment?.externalId ?? undefined,
    attachmentFileName: attachment?.fileName ?? undefined,
    attachmentSha256: attachment?.sha256 ?? undefined,
  };
}

// Financial activities and household members
/** @returns {Promise<FinancialActivityResponse[]>} */
export const getFinancialActivities = () => financialActivityApi.getFinancialActivities();
/** @param {UpsertFinancialActivityRequest} data @returns {Promise<FinancialActivityResponse>} */
export const createFinancialActivity = (data) =>
  financialActivityApi.createFinancialActivity({ upsertFinancialActivityRequest: data });
/** @param {number|string} id @param {UpsertFinancialActivityRequest} data */
export const updateFinancialActivity = (id, data) =>
  financialActivityApi.updateFinancialActivity({
    id: Number(id),
    upsertFinancialActivityRequest: data,
  });
export const getFinancialActivityTypes = () => financialActivityApi.getFinancialActivityTypes();
export const getTaxTreatments = () => financialActivityApi.getTaxTreatments();
export const getFinancialCategories = (direction, activityId) =>
  financialCategoryApi.getFinancialCategories({
    direction,
    activityId: activityId ? Number(activityId) : undefined,
  });
export const getCashflowReport = (from, to, ownerId, activityId) =>
  reportApi.getCashflowReport({
    from,
    to,
    ownerId: ownerId ? Number(ownerId) : undefined,
    activityId: activityId ? Number(activityId) : undefined,
  });
export const getScheduleEReport = (year, ownerId, activityId) =>
  reportApi.getScheduleEReport({
    year: Number(year),
    ownerId: ownerId ? Number(ownerId) : undefined,
    activityId: activityId ? Number(activityId) : undefined,
  });

/** @returns {Promise<HouseholdMemberResponse[]>} */
export const getHouseholdMembers = () => householdMemberApi.getHouseholdMembers();
/** @param {UpsertHouseholdMemberRequest} data @returns {Promise<HouseholdMemberResponse>} */
export const createHouseholdMember = (data) =>
  householdMemberApi.createHouseholdMember({ upsertHouseholdMemberRequest: data });
/** @param {number|string} id @param {UpsertHouseholdMemberRequest} data */
export const updateHouseholdMember = (id, data) =>
  householdMemberApi.updateHouseholdMember({
    id: Number(id),
    upsertHouseholdMemberRequest: data,
  });

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
export const getPropertyKeywords = () => classificationHistoryApi.getPropertyKeywords();

// Counterparties
/** @returns {Promise<CounterpartyResponse[]>} */
export const getCounterparties = () => counterpartyApi.getCounterparties();
/** @param {UpsertCounterpartyRequest} data @returns {Promise<CounterpartyResponse>} */
export const createCounterparty = (data) =>
  counterpartyApi.createCounterparty({ upsertCounterpartyRequest: data });
/** @param {number|string} id @param {UpsertCounterpartyRequest} data @returns {Promise<CounterpartyResponse>} */
export const updateCounterparty = (id, data) =>
  counterpartyApi.updateCounterparty({ id: Number(id), upsertCounterpartyRequest: data });
export const deleteCounterparty = (id) => counterpartyApi.deleteCounterparty({ id: Number(id) });
export const getCounterpartyTypes = () => counterpartyApi.getCounterpartyTypes();
export const getCounterpartyKeywords = () => classificationHistoryApi.getCounterpartyKeywords();

// Outlook
export const getOutlookStatus = () => outlookApi.getOutlookStatus();
export const getOutlookRentalEmails = (page = 0) => outlookApi.getOutlookRentalEmails({ page });
export const getOutlookEmailContent = (messageId) =>
  outlookApi.getOutlookEmailContent({ messageId });
export const parseEmail = (messageId, subject, activityId) =>
  outlookApi.parseOutlookEmail({
    messageId,
    parseEmailRequest: { subject, activityId: activityId ? Number(activityId) : undefined },
  });
export const getOutlookAvailableFolders = () => outlookApi.getOutlookAvailableFolders();
export const getOutlookFolderSettings = () => outlookApi.getOutlookFolderSettings();
export const updateOutlookFolderSettings = (folderSettings) =>
  outlookApi.updateOutlookFolderSettings({ requestBody: { folderSettings } });
export const getOutlookMoveSettings = () => outlookApi.getOutlookMoveSettings();
export const updateOutlookMoveSettings = (enabled, folderId) =>
  outlookApi.updateOutlookMoveSettings({ requestBody: { enabled, folderId } });

// Inbox
/** @returns {Promise<InboxItemResponse[]>} */
export const getInboxItems = () => inboxApi.getInboxItems();
/** @param {number|string} id @param {SaveInboxItemRequest} data @returns {Promise<InboxItemResponse>} */
export const saveInboxItem = (id, data) =>
  inboxApi.saveInboxItem({ id: Number(id), saveInboxItemRequest: data });
export const dismissInboxItem = (id) => inboxApi.dismissInboxItem({ id: Number(id) });
/** @param {number|string} id @returns {Promise<InboxItemResponse>} */
export const reparseInboxItem = (id) => inboxApi.reparseInboxItem({ id: Number(id) });
/** @returns {Promise<VenmoImportResponse>} */
export const importVenmoToInbox = (file, counterpartyId, propertyId, activityId) => {
  const fd = new FormData();
  fd.append('file', file);
  if (counterpartyId) {
    fd.append('counterpartyId', counterpartyId);
  }
  if (propertyId) {
    fd.append('propertyId', propertyId);
  }
  if (activityId) {
    fd.append('activityId', activityId);
  }
  return rawMultipartRequest('/api/v2/inbox/imports/venmo', fd);
};

// Agent
export const submitTransactionToAgent = (message) =>
  agentApi.processAgentMessage({ requestBody: { message } });

// Backup
export const triggerBackup = () => backupApi.createBackup();
export const listBackups = () => backupApi.getBackups();
export const restoreBackup = (fileId) => backupApi.restoreBackup({ fileId });
export const getRestoreStatus = () => backupApi.getRestoreStatus();
export const deleteBackup = (fileId) => backupApi.deleteBackup({ fileId });

// Receipts
/** @returns {Promise<ReceiptDto[]>} */
export const listReceipts = () => receiptApi.getReceipts();
export const downloadReceipt = (itemId) => receiptApi.downloadReceipt({ itemId });
export const deleteReceipt = (itemId) => receiptApi.deleteReceipt({ itemId });
export const parseReceipt = (itemId) => receiptApi.parseReceipt({ itemId });
export const getReceiptSettings = () => receiptApi.getReceiptSettings();
export const updateReceiptSettings = (folderBase, importFolders = []) =>
  receiptApi.updateReceiptSettings({ requestBody: { folderBase, importFolders } });
/** @param {File} file @returns {Promise<UploadReceiptResponse>} */
export const uploadReceipt = (file) => receiptApi.uploadReceipt({ file });
