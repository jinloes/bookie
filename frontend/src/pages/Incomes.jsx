import React, { useEffect, useState } from 'react'
import { getIncomes, createIncome, updateIncome, deleteIncome, getProperties } from '../api/index.js'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], source: '', propertyName: '' }

const btn = (color = '#2563eb') => ({
  padding: '0.4rem 0.9rem',
  background: color,
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '0.85rem',
})

export default function Incomes() {
  const [incomes, setIncomes] = useState([])
  const [properties, setProperties] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getIncomes().then(setIncomes).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getProperties().then(setProperties)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    const data = { ...form, amount: parseFloat(form.amount) }
    if (editing) {
      await updateIncome(editing, data)
    } else {
      await createIncome(data)
    }
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (income) => {
    setForm({ ...income, date: income.date })
    setEditing(income.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this income?')) {
      await deleteIncome(id)
      load()
    }
  }

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ color: '#1e3a5f' }}>Income</h1>
        <button style={btn()} onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Income</button>
      </div>

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.5rem', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1rem', color: '#1e3a5f' }}>{editing ? 'Edit Income' : 'New Income'}</h2>
          <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
            {['amount', 'description', 'date', 'source'].map(field => (
              <div key={field}>
                <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b', textTransform: 'capitalize' }}>
                  {field}
                </label>
                <input
                  type={field === 'amount' ? 'number' : field === 'date' ? 'date' : 'text'}
                  step={field === 'amount' ? '0.01' : undefined}
                  value={form[field]}
                  onChange={e => setForm(f => ({ ...f, [field]: e.target.value }))}
                  required={['amount', 'description', 'date'].includes(field)}
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
              {['Date', 'Description', 'Source', 'Property', 'Amount', 'Actions'].map(h => (
                <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {incomes.length === 0 ? (
              <tr><td colSpan={6} style={{ padding: '2rem', textAlign: 'center', color: '#94a3b8' }}>No income records yet</td></tr>
            ) : incomes.map(i => (
              <tr key={i.id} style={{ borderTop: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{i.date}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{i.description}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>{i.source || '—'}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>{i.propertyName || '—'}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#16a34a', fontWeight: '600' }}>+${Number(i.amount).toFixed(2)}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <button style={{ ...btn('#64748b'), marginRight: '0.4rem' }} onClick={() => handleEdit(i)}>Edit</button>
                  <button style={btn('#ef4444')} onClick={() => handleDelete(i.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
