const BASE = '/api'

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (!res.ok) {
    const body = await res.text()
    const msg = `HTTP ${res.status}: ${body || 'no body'}`
    console.error('API error', res.url, msg)
    throw new Error(msg)
  }
  if (res.status === 204) return null
  return res.json()
}

// Incomes
export const getIncomes = () => request('/incomes')
export const createIncome = (data) => request('/incomes', { method: 'POST', body: JSON.stringify(data) })
export const updateIncome = (id, data) => request(`/incomes/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteIncome = (id) => request(`/incomes/${id}`, { method: 'DELETE' })
export const getTotalIncome = () => request('/incomes/total')

// Expenses
export const getExpenses = () => request('/expenses')
export const createExpense = (data) => request('/expenses', { method: 'POST', body: JSON.stringify(data) })
export const updateExpense = (id, data) => request(`/expenses/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteExpense = (id) => request(`/expenses/${id}`, { method: 'DELETE' })
export const getTotalExpenses = () => request('/expenses/total')
export const getExpenseCategories = () => request('/expenses/categories')

// Properties
export const getProperties = () => request('/properties')
export const createProperty = (data) => request('/properties', { method: 'POST', body: JSON.stringify(data) })
export const updateProperty = (id, data) => request(`/properties/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteProperty = (id) => request(`/properties/${id}`, { method: 'DELETE' })
export const getPropertyTypes = () => request('/properties/types')

// Payers
export const getPayers = () => request('/payers')
export const createPayer = (data) => request('/payers', { method: 'POST', body: JSON.stringify(data) })
export const updatePayer = (id, data) => request(`/payers/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deletePayer = (id) => request(`/payers/${id}`, { method: 'DELETE' })
export const getPayerTypes = () => request('/payers/types')

// Outlook
export const getOutlookStatus = () => request('/outlook/status')
export const getOutlookRentalEmails = (page = 0) => request(`/outlook/emails/rental?page=${page}`)
export const convertEmailToExpense = (messageId) => request(`/outlook/emails/${messageId}/to-expense`, { method: 'POST' })

// Agent
export const submitExpenseToAgent = (message) =>
  request('/agent/expense', { method: 'POST', body: JSON.stringify({ message }) })

// Backup
export const triggerBackup = () => request('/backup', { method: 'POST' })
export const listBackups = () => request('/backup/list')
export const restoreBackup = (fileId) => request(`/backup/restore/${encodeURIComponent(fileId)}`, { method: 'POST' })
