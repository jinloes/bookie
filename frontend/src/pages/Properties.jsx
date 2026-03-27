import React, { useEffect, useState } from 'react'
import { getProperties, createProperty, updateProperty, deleteProperty, getPropertyTypes } from '../api/index.js'

const EMPTY_FORM = { name: '', address: '', type: 'SINGLE_FAMILY', notes: '' }

const btn = (color = '#2563eb') => ({
  padding: '0.4rem 0.9rem',
  background: color,
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '0.85rem',
})

export default function Properties() {
  const [properties, setProperties] = useState([])
  const [types, setTypes] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [editing, setEditing] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = () => getProperties().then(setProperties).finally(() => setLoading(false))

  useEffect(() => {
    load()
    getPropertyTypes().then(setTypes)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (editing) {
      await updateProperty(editing, form)
    } else {
      await createProperty(form)
    }
    setForm(EMPTY_FORM)
    setEditing(null)
    setShowForm(false)
    load()
  }

  const handleEdit = (property) => {
    setForm({ name: property.name, address: property.address, type: property.type, notes: property.notes || '' })
    setEditing(property.id)
    setShowForm(true)
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this property?')) {
      await deleteProperty(id)
      load()
    }
  }

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ color: '#1e3a5f' }}>Properties</h1>
        <button style={btn()} onClick={() => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true) }}>+ Add Property</button>
      </div>

      {showForm && (
        <div style={{ background: '#fff', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.5rem', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' }}>
          <h2 style={{ marginBottom: '1rem', fontSize: '1rem', color: '#1e3a5f' }}>{editing ? 'Edit Property' : 'New Property'}</h2>
          <form onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.8rem' }}>
            {[
              { key: 'name', label: 'Name' },
              { key: 'address', label: 'Address' },
            ].map(({ key, label }) => (
              <div key={key}>
                <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>{label}</label>
                <input
                  type="text"
                  value={form[key]}
                  onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
                  required
                  style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}
                />
              </div>
            ))}
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Type</label>
              <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}>
                {types.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.3rem', color: '#64748b' }}>Notes</label>
              <input
                type="text"
                value={form.notes}
                onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                style={{ width: '100%', padding: '0.5rem', border: '1px solid #e2e8f0', borderRadius: '6px', fontSize: '0.9rem' }}
              />
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
              {['Name', 'Address', 'Type', 'Notes', 'Actions'].map(h => (
                <th key={h} style={{ padding: '0.8rem 1rem', textAlign: 'left', fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {properties.length === 0 ? (
              <tr><td colSpan={5} style={{ padding: '2rem', textAlign: 'center', color: '#94a3b8' }}>No properties yet</td></tr>
            ) : properties.map(p => (
              <tr key={p.id} style={{ borderTop: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', fontWeight: '600' }}>{p.name}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem' }}>{p.address}</td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>
                  {types.find(t => t.value === p.type)?.label || p.type}
                </td>
                <td style={{ padding: '0.8rem 1rem', fontSize: '0.9rem', color: '#64748b' }}>{p.notes || '—'}</td>
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