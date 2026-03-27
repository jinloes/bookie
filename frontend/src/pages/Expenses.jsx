import React, { useEffect, useState } from 'react'
import { getExpenses, createExpense, updateExpense, deleteExpense, getExpenseCategories, getProperties } from '../api/index.js'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], category: 'OTHER', propertyName: '' }

const btn = (color = '#2563eb') => ({
  padding: '0.4rem 0.9rem',
  background: color,
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '0.85rem',
})

const categoryColor = (cat) => {
  const map = { REPAIRS: '#ef4444', UTILITIES: '#3b82f6', INSURANCE: '#8b5cf6',
    TAXES: '#ec4899', MORTGAGE_INTEREST: '#14b8a6', DEPRECIATION: '#f97316' }
  return map[cat] || '#94a3b8'
}

export default function Expenses() {
  const [expenses, setExpenses] = useState([])
  const [categories, setCategories] = useState([])
  const [properties, setProperties] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getExpenses().then(setExpenses).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getExpenseCategories().then(setCategories)
    getProperties().then(setProperties)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    const data = { ...form, amount: parseFloat(form.amount) }
    if (editing) {
      await updateExpense(editing, data)
    } else {
      await createExpense(data)
    }
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (expense) => {
    setForm({ ...expense, date: expense.date })
    setEditing(expense.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this expense?')) {
      await deleteExpense(id)
      load()
    }
  }

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ color: '#1e3a5f' }}>Expenses</h1>
        <button style={btn()} onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Expense</button>
      </div>

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.5rem', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1rem', color: '#1e3a5f' }}>{editing ? 'Edit Expense' : 'New Expense'}</h2>
          <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
            {[
              { key: 'amount', type: 'number' },
              { key: 'description', type: 'text' },
              { key: 'date', type: 'date' },
            ].map(({ key, type }) => (
              <div key={key}>
                <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b', textTransform: 'capitalize' }}>
                  {key}
                </label>
                <input
                  type={type}
                  step={key === 'amount' ? '0.01' : undefined}
                  value={form[key]}
                  onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
                  required
                  style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}
                />
              </div>
            ))}
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Property</label>
              <select value={form.propertyName} onChange={e => setForm(f => ({ ...f, propertyName: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                <option value="">— None —</option>
                {properties.map(p => <option key={p.id} value={p.name}>{p.name}</option>)}
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Category (Schedule E)</label>
              <select value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                {categories.map(c => <option key={c.value} value={c.value}>Line {c.scheduleELine} — {c.label}</option>)}
              </select>
            </div>
            <div style={{ gridColumn: '1/-1', display: 'flex', gap: '0.5rem' }}>
              <button type="submit" style={btn()}>Save</button>
              <button type="button" style={btn('#94a3b8')} onClick={() => { setShowForm(false); setEditing(null) }}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 4px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: '#f8fafc' }}>
              {['Date', 'Description', 'Category', 'Property', 'Amount', 'Actions'].map(h => (
                <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {expenses.length === 0 ? (
              <tr><td colSpan={6} style={{ padding: '2rem', textAlign: 'center', color: '#94a3b8' }}>No expense records yet</td></tr>
            ) : expenses.map(e => (
              <tr key={e.id} style={{ borderTop: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{e.date}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{e.description}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <span style={{ background: categoryColor(e.category) + '22', color: categoryColor(e.category),
                    padding: '0.2rem 0.6rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: '600' }}>
                    {categories.find(c => c.value === e.category)?.label || e.category}
                  </span>
                </td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>{e.propertyName || '—'}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#dc2626', fontWeight: '600' }}>-${Number(e.amount).toFixed(2)}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <button style={{ ...btn('#64748b'), marginRight: '0.4rem' }} onClick={() => handleEdit(e)}>Edit</button>
                  <button style={btn('#ef4444')} onClick={() => handleDelete(e.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
