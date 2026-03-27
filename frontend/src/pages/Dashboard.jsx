import React, { useEffect, useState } from 'react'
import { getTotalIncome, getTotalExpenses, getIncomes, getExpenses } from '../api/index.js'
import RentalEmails from '../components/RentalEmails.jsx'

const card = {
  background: '#fff',
  borderRadius: '12px',
  padding: '1.5rem',
  boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
  flex: '1',
  minWidth: '180px',
}

export default function Dashboard() {
  const [totalIncome, setTotalIncome] = useState(0)
  const [totalExpenses, setTotalExpenses] = useState(0)
  const [recentIncomes, setRecentIncomes] = useState([])
  const [recentExpenses, setRecentExpenses] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getTotalIncome(), getTotalExpenses(), getIncomes(), getExpenses()])
      .then(([inc, exp, incomes, expenses]) => {
        setTotalIncome(inc.total || 0)
        setTotalExpenses(exp.total || 0)
        setRecentIncomes(incomes.slice(-5).reverse())
        setRecentExpenses(expenses.slice(-5).reverse())
      })
      .finally(() => setLoading(false))
  }, [])

  const net = (totalIncome - totalExpenses).toFixed(2)

  if (loading) return <p>Loading...</p>

  return (
    <div>
      <h1 style={{ marginBottom: '1.5rem', color: '#1e3a5f' }}>Dashboard</h1>
      <RentalEmails />

      <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', flexWrap: 'wrap' }}>
        <div style={{ ...card, borderLeft: '4px solid #22c55e' }}>
          <div style={{ color: '#64748b', fontSize: '0.85rem', marginBottom: '0.4rem' }}>Total Income</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#16a34a' }}>${Number(totalIncome).toFixed(2)}</div>
        </div>
        <div style={{ ...card, borderLeft: '4px solid #ef4444' }}>
          <div style={{ color: '#64748b', fontSize: '0.85rem', marginBottom: '0.4rem' }}>Total Expenses</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: '#dc2626' }}>${Number(totalExpenses).toFixed(2)}</div>
        </div>
        <div style={{ ...card, borderLeft: `4px solid ${net >= 0 ? '#3b82f6' : '#f97316'}` }}>
          <div style={{ color: '#64748b', fontSize: '0.85rem', marginBottom: '0.4rem' }}>Net Income</div>
          <div style={{ fontSize: '1.8rem', fontWeight: '700', color: net >= 0 ? '#2563eb' : '#ea580c' }}>
            {net >= 0 ? '+' : ''} ${net}
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        <div style={{ ...card }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '1rem', color: '#1e3a5f' }}>Recent Income</h2>
          {recentIncomes.length === 0 ? <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>No income yet</p> : (
            <ul style={{ listStyle: 'none' }}>
              {recentIncomes.map(i => (
                <li key={i.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.9rem' }}>
                  <span>{i.description}</span>
                  <span style={{ color: '#16a34a', fontWeight: '600' }}>+${Number(i.amount).toFixed(2)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div style={{ ...card }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '1rem', color: '#1e3a5f' }}>Recent Expenses</h2>
          {recentExpenses.length === 0 ? <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>No expenses yet</p> : (
            <ul style={{ listStyle: 'none' }}>
              {recentExpenses.map(e => (
                <li key={e.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.9rem' }}>
                  <span>{e.description}</span>
                  <span style={{ color: '#dc2626', fontWeight: '600' }}>-${Number(e.amount).toFixed(2)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
