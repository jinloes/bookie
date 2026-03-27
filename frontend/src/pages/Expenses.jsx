import React, { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { getExpenses, createExpense, updateExpense, deleteExpense, getExpenseCategories, getProperties, getPayers, createPayer, createProperty } from '../api/index.js'

const EMPTY_FORM = { amount: '', description: '', date: new Date().toISOString().split('T')[0], category: 'OTHER', propertyName: '', sourceType: null, sourceId: null, payer: null }

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
  const [payers, setPayers] = useState([])
  const [payersLoaded, setPayersLoaded] = useState(false)
  const [propertiesLoaded, setPropertiesLoaded] = useState(false)
  const [pendingPrefill, setPendingPrefill] = useState(null)
  const [suggestedPayerName, setSuggestedPayerName] = useState(null)
  const [suggestedPropertyName, setSuggestedPropertyName] = useState(null)
  const [loading, setLoading] = useState(true)
  const [highlightId, setHighlightId] = useState(null)
  const location = useLocation()

  const load = () => getExpenses().then(setExpenses).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getExpenseCategories().then(setCategories)
    getProperties().then(data => { setProperties(data); setPropertiesLoaded(true) })
    getPayers().then(data => { setPayers(data); setPayersLoaded(true) })
  }, [])

  useEffect(() => {
    const { prefill, highlightId: hid } = location.state || {}
    if (hid) {
      setHighlightId(hid)
      window.history.replaceState({}, '')
      setTimeout(() => setHighlightId(null), 3000)
    }
    if (prefill) {
      setPendingPrefill(prefill)
      window.history.replaceState({}, '')
    }
  }, [location.state])

  useEffect(() => {
    if (!pendingPrefill || !payersLoaded || !propertiesLoaded) return
    const matchedPayer = pendingPrefill.payerName
      ? payers.find(p => p.name.toLowerCase() === pendingPrefill.payerName.toLowerCase()) ?? null
      : null
    const suggestedPropLower = pendingPrefill.propertyName?.trim().toLowerCase()
    const matchedProperty = suggestedPropLower
      ? (properties.find(p => p.name.toLowerCase() === suggestedPropLower) ??
         properties.find(p => p.address?.toLowerCase().includes(suggestedPropLower) || suggestedPropLower.includes(p.address?.toLowerCase() ?? '')) ??
         null)
      : null
    setForm({
      amount: pendingPrefill.amount ?? '',
      description: pendingPrefill.description ?? '',
      date: pendingPrefill.date ?? new Date().toISOString().split('T')[0],
      category: pendingPrefill.category ?? 'OTHER',
      propertyName: matchedProperty ? matchedProperty.name : '',
      sourceType: pendingPrefill.sourceType ?? null,
      sourceId: pendingPrefill.sourceId ?? null,
      payer: matchedPayer ? { id: matchedPayer.id } : null,
    })
    if (pendingPrefill.payerName && !matchedPayer) {
      setSuggestedPayerName(pendingPrefill.payerName)
    }
    const isFromEmail = pendingPrefill.sourceType === 'OUTLOOK_EMAIL'
    if (!matchedProperty && (pendingPrefill.propertyName?.trim() || isFromEmail)) {
      setSuggestedPropertyName(pendingPrefill.propertyName?.trim() ?? '')
    }
    setEditing(null)
    setShowForm(true)
    setPendingPrefill(null)
  }, [pendingPrefill, payersLoaded, propertiesLoaded])

  const handleCreateSuggestedPayer = async () => {
    const newPayer = await createPayer({ name: suggestedPayerName, type: 'COMPANY' })
    setPayers(prev => [...prev, newPayer])
    setForm(f => ({ ...f, payer: { id: newPayer.id } }))
    setSuggestedPayerName(null)
  }

  const handleCreateSuggestedProperty = async () => {
    const newProp = await createProperty({ name: suggestedPropertyName, address: '', type: 'SINGLE_FAMILY', notes: '' })
    setProperties(prev => [...prev, newProp])
    setForm(f => ({ ...f, propertyName: newProp.name }))
    setSuggestedPropertyName(null)
  }

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
    setSuggestedPayerName(null)
    setSuggestedPropertyName(null)
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
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Payer</label>
              <select value={form.payer?.id ?? ''} onChange={e => setForm(f => ({ ...f, payer: e.target.value ? { id: Number(e.target.value) } : null }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                <option value="">— None —</option>
                {payers.map(p => <option key={p.id} value={p.id}>{p.name} ({p.type === 'COMPANY' ? 'Company' : 'Person'})</option>)}
              </select>
              {suggestedPayerName && (
                <div style={{ marginTop: '0.4rem', padding: '0.5rem 0.75rem', background: '#fefce8', border: '1px solid #fde047', borderRadius: '6px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span style={{ color: '#854d0e', whiteSpace: 'nowrap' }}>New payer:</span>
                  <input
                    type="text"
                    value={suggestedPayerName}
                    onChange={e => setSuggestedPayerName(e.target.value)}
                    style={{ flex: 1, minWidth: '120px', padding: '0.2rem 0.5rem', border: '1px solid #fde047', borderRadius: '4px', fontSize: '0.8rem', background: '#fff' }}
                  />
                  <button type="button" onClick={handleCreateSuggestedPayer} disabled={!suggestedPayerName.trim()}
                    style={{ padding: '0.2rem 0.6rem', fontSize: '0.75rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                    Create &amp; Select
                  </button>
                  <button type="button" onClick={() => setSuggestedPayerName(null)}
                    style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', background: 'transparent', color: '#94a3b8', border: 'none', cursor: 'pointer' }}>
                    Dismiss
                  </button>
                </div>
              )}
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Property</label>
              <select value={form.propertyName} onChange={e => setForm(f => ({ ...f, propertyName: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                <option value="">— None —</option>
                {properties.map(p => <option key={p.id} value={p.name}>{p.name}</option>)}
              </select>
              {suggestedPropertyName !== null && (
                <div style={{ marginTop: '0.4rem', padding: '0.5rem 0.75rem', background: '#fefce8', border: '1px solid #fde047', borderRadius: '6px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span style={{ color: '#854d0e', whiteSpace: 'nowrap' }}>New property:</span>
                  <input
                    type="text"
                    value={suggestedPropertyName}
                    onChange={e => setSuggestedPropertyName(e.target.value)}
                    style={{ flex: 1, minWidth: '120px', padding: '0.2rem 0.5rem', border: '1px solid #fde047', borderRadius: '4px', fontSize: '0.8rem', background: '#fff' }}
                  />
                  <button type="button" onClick={handleCreateSuggestedProperty} disabled={!suggestedPropertyName.trim()}
                    style={{ padding: '0.2rem 0.6rem', fontSize: '0.75rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                    Create &amp; Select
                  </button>
                  <button type="button" onClick={() => setSuggestedPropertyName(null)}
                    style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', background: 'transparent', color: '#94a3b8', border: 'none', cursor: 'pointer' }}>
                    Dismiss
                  </button>
                </div>
              )}
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
              <button type="button" style={btn('#94a3b8')} onClick={() => { setShowForm(false); setEditing(null); setSuggestedPayerName(null); setSuggestedPropertyName(null) }}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 4px rgba(0,0,0,0.1)', overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: '#f8fafc' }}>
              {['Date', 'Payer', 'Description', 'Category', 'Property', 'Source', 'Amount', 'Actions'].map(h => (
                <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {expenses.length === 0 ? (
              <tr><td colSpan={8} style={{ padding: '2rem', textAlign: 'center', color: '#94a3b8' }}>No expense records yet</td></tr>
            ) : expenses.map(e => (
              <tr key={e.id} style={{ borderTop: '1px solid #f1f5f9', background: highlightId === e.id ? '#fefce8' : 'transparent', transition: 'background 0.5s' }}>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{e.date}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#1e3a5f' }}>
                  {e.payer ? (
                    <span>
                      {e.payer.name}
                      <span style={{ marginLeft: '0.3rem', fontSize: '0.7rem', color: '#94a3b8' }}>
                        ({e.payer.type === 'COMPANY' ? 'Co.' : 'Person'})
                      </span>
                    </span>
                  ) : '—'}
                </td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{e.description}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <span style={{ background: categoryColor(e.category) + '22', color: categoryColor(e.category),
                    padding: '0.2rem 0.6rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: '600' }}>
                    {categories.find(c => c.value === e.category)?.label || e.category}
                  </span>
                </td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>{e.propertyName || '—'}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  {e.sourceType === 'OUTLOOK_EMAIL'
                    ? <span style={{ background: '#eff6ff', color: '#0078d4', padding: '0.2rem 0.6rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: '600' }}>Outlook</span>
                    : <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>—</span>}
                </td>
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
