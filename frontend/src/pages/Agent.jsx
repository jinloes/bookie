import React, { useState } from 'react'
import { submitExpenseToAgent } from '../api/index.js'

const EXAMPLES = [
  'I paid $250 for plumbing repairs at Oak Street property last Monday',
  'Spent $120 on landscaping for the Main St duplex today',
  'Property insurance payment of $890 for Maple Ave on March 15th',
  'Paid $75 for cleaning supplies for the downtown apartment yesterday',
  '$1,500 mortgage payment for Oak Street property today',
]

export default function Agent() {
  const [message, setMessage] = useState('')
  const [chat, setChat] = useState([])
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!message.trim() || loading) return

    const userMsg = message.trim()
    setMessage('')
    setChat(c => [...c, { role: 'user', text: userMsg }])
    setLoading(true)

    try {
      const res = await submitExpenseToAgent(userMsg)
      setChat(c => [...c, {
        role: 'assistant',
        text: res.message,
        expense: res.createdExpense,
      }])
    } catch (err) {
      setChat(c => [...c, {
        role: 'assistant',
        text: `Error: ${err.message}`,
        isError: true,
      }])
    } finally {
      setLoading(false)
    }
  }

  const useExample = (ex) => setMessage(ex)

  return (
    <div>
      <h1 style={{ color: '#1e3a5f', marginBottom: '0.5rem' }}>AI Expense Agent</h1>
      <p style={{ color: '#64748b', marginBottom: '1.5rem', fontSize: '0.9rem' }}>
        Describe an expense in natural language and the AI will create it for you automatically.
      </p>

      <div style={{ background: '#fff', borderRadius: '12px', boxShadow: '0 1px 4px rgba(0,0,0,0.1)', marginBottom: '1rem', overflow: 'hidden' }}>
        <div style={{ padding: '1rem 1.5rem', borderBottom: '1px solid #f1f5f9', background: '#f8fafc' }}>
          <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>Example prompts:</span>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
            {EXAMPLES.map((ex, i) => (
              <button key={i} onClick={() => useExample(ex)}
                style={{ background: '#e0f2fe', color: '#0369a1', border: 'none', borderRadius: '999px',
                  padding: '0.3rem 0.8rem', fontSize: '0.75rem', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                {ex.length > 50 ? ex.slice(0, 50) + '...' : ex}
              </button>
            ))}
          </div>
        </div>

        <div style={{ height: '400px', overflowY: 'auto', padding: '1rem 1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {chat.length === 0 && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#94a3b8', textAlign: 'center' }}>
              <div>
                <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🤖</div>
                <p>Start by describing an expense above</p>
              </div>
            </div>
          )}
          {chat.map((msg, i) => (
            <div key={i} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
              <div style={{
                maxWidth: '75%',
                background: msg.role === 'user' ? '#2563eb' : msg.isError ? '#fee2e2' : '#f1f5f9',
                color: msg.role === 'user' ? '#fff' : msg.isError ? '#dc2626' : '#1e293b',
                padding: '0.8rem 1rem',
                borderRadius: msg.role === 'user' ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
                fontSize: '0.9rem',
              }}>
                {msg.text}
                {msg.expense && (
                  <div style={{ marginTop: '0.6rem', padding: '0.6rem', background: '#fff', borderRadius: '8px', fontSize: '0.8rem', border: '1px solid #e2e8f0' }}>
                    <strong style={{ display: 'block', marginBottom: '0.3rem', color: '#1e3a5f' }}>Expense Created:</strong>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.2rem', color: '#475569' }}>
                      <span>Amount:</span><span style={{ color: '#dc2626', fontWeight: '600' }}>${Number(msg.expense.amount).toFixed(2)}</span>
                      <span>Category:</span><span>{msg.expense.category}</span>
                      <span>Date:</span><span>{msg.expense.date}</span>
                      <span>Property:</span><span>{msg.expense.propertyName}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
          {loading && (
            <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
              <div style={{ background: '#f1f5f9', padding: '0.8rem 1rem', borderRadius: '12px 12px 12px 2px', color: '#64748b', fontSize: '0.9rem' }}>
                Thinking...
              </div>
            </div>
          )}
        </div>

        <div style={{ padding: '1rem 1.5rem', borderTop: '1px solid #f1f5f9' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              type="text"
              value={message}
              onChange={e => setMessage(e.target.value)}
              placeholder="Describe an expense (e.g. 'Paid $200 for roof repair at Oak St property')"
              disabled={loading}
              style={{ flex: 1, padding: '0.7rem 1rem', border: '1px solid #e2e8f0', borderRadius: '8px', fontSize: '0.9rem', outline: 'none' }}
            />
            <button type="submit" disabled={loading || !message.trim()}
              style={{ padding: '0.7rem 1.5rem', background: loading ? '#94a3b8' : '#2563eb', color: '#fff',
                border: 'none', borderRadius: '8px', cursor: loading ? 'not-allowed' : 'pointer', fontWeight: '600' }}>
              Send
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
