const BASE = '/api'

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(err || `HTTP ${res.status}`)
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

// Agent
export const submitExpenseToAgent = (message) =>
  request('/agent/expense', { method: 'POST', body: JSON.stringify({ message }) })
