import React, { useEffect, useState } from 'react'
import { getPayers, createPayer, updatePayer, deletePayer, getPayerTypes } from '../api/index.js'

const EMPTY_FORM = { name: '', type: 'PERSON' }

const btn = (color = '#2563eb') => ({
  padding: '0.4rem 0.9rem',
  background: color,
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '0.85rem',
})

export default function Payers() {
  const [payers, setPayers] = useState([])
  const [types, setTypes] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getPayers().then(setPayers).finally(() => setLoading(false))
  useEffect(() => {
    load()
    getPayerTypes().then(setTypes)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (editing) {
      await updatePayer(editing, form)
    } else {
      await createPayer(form)
    }
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (payer) => {
    setForm({ name: payer.name, type: payer.type })
    setEditing(payer.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this payer?')) {
      await deletePayer(id)
      load()
    }
  }

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ color: '#1e3a5f' }}>Payers</h1>
        <button style={btn()} onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Payer</button>
      </div>

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.5rem', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1rem', color: '#1e3a5f' }}>{editing ? 'Edit Payer' : 'New Payer'}</h2>
          <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Name</label>
              <input type="text" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }} />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Type</label>
              <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                {types.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
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
              {['Name', 'Type', 'Actions'].map(h => (
                <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {payers.length === 0 ? (
              <tr><td colSpan={3} style={{ padding: '2rem', textAlign: 'center', color: '#94a3b8' }}>No payers yet</td></tr>
            ) : payers.map(p => (
              <tr key={p.id} style={{ borderTop: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', fontWeight: '600', color: '#1e3a5f' }}>{p.name}</td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <span style={{ background: p.type === 'COMPANY' ? '#eff6ff' : '#f0fdf4', color: p.type === 'COMPANY' ? '#2563eb' : '#16a34a',
                    padding: '0.2rem 0.6rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: '600' }}>
                    {p.type === 'COMPANY' ? 'Company' : 'Person'}
                  </span>
                </td>
                <td style={{ padding: '0.8rem 1rem' }}>
                  <button style={{ ...btn('#64748b'), marginRight: '0.4rem' }} onClick={() => handleEdit(p)}>Edit</button>
                  <button style={btn('#ef4444')} onClick={() => handleDelete(p.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
