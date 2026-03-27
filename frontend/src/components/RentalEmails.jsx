import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getOutlookStatus, getOutlookRentalEmails, convertEmailToExpense } from '../api/index.js'

const formatDate = (iso) => new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

export default function RentalEmails() {
  const [emails, setEmails] = useState([])
  const [connected, setConnected] = useState(false)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [converting, setConverting] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    getOutlookStatus()
      .then(({ connected }) => {
        setConnected(connected)
        if (connected) return getOutlookRentalEmails(0).then(data => {
          setEmails(data.emails)
          setHasMore(data.hasMore)
        })
      })
      .finally(() => setLoading(false))
  }, [])

  const goToPage = (newPage) => {
    setLoading(true)
    getOutlookRentalEmails(newPage)
      .then(data => {
        setEmails(data.emails)
        setHasMore(data.hasMore)
        setPage(newPage)
      })
      .finally(() => setLoading(false))
  }

  const handleConvert = async (emailId) => {
    setConverting(emailId)
    try {
      const suggestion = await convertEmailToExpense(emailId)
      navigate('/expenses', { state: { prefill: suggestion } })
    } finally {
      setConverting(null)
    }
  }

  const cardStyle = {
    background: '#fff',
    borderRadius: '12px',
    padding: '1.5rem',
    boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
    marginBottom: '2rem',
  }

  if (loading) return null

  if (!connected) {
    return (
      <div style={{ ...cardStyle, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ fontWeight: '600', color: '#1e3a5f', marginBottom: '0.25rem' }}>Rental Emails</div>
          <div style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Connect Outlook to see emails tagged as Rental</div>
        </div>
        <a href="/api/outlook/connect"
          style={{ padding: '0.5rem 1rem', background: '#0078d4', color: '#fff', borderRadius: '6px', textDecoration: 'none', fontSize: '0.85rem', fontWeight: '600' }}>
          Connect Outlook
        </a>
      </div>
    )
  }

  return (
    <div style={cardStyle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ fontSize: '1rem', color: '#1e3a5f' }}>Rental Emails</h2>
        <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{emails.length} email{emails.length !== 1 ? 's' : ''}</span>
      </div>
      {emails.length === 0 ? (
        <p style={{ color: '#94a3b8', fontSize: '0.9rem' }}>No emails tagged as Rental</p>
      ) : (
        <>
          <ul style={{ listStyle: 'none' }}>
            {emails.map((email, i) => (
              <li key={i} style={{ padding: '0.75rem 0', borderBottom: i < emails.length - 1 ? '1px solid #f1f5f9' : 'none' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.2rem' }}>
                  <span style={{ fontWeight: '600', fontSize: '0.9rem', color: '#1e3a5f' }}>{email.subject}</span>
                  <span style={{ fontSize: '0.75rem', color: '#94a3b8', whiteSpace: 'nowrap', marginLeft: '1rem' }}>{formatDate(email.receivedAt)}</span>
                </div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '0.2rem' }}>{email.sender}</div>
                <div style={{ fontSize: '0.8rem', color: '#94a3b8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{email.preview}</div>
                <div style={{ marginTop: '0.4rem' }}>
                  {email.expenseId ? (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.75rem', color: '#16a34a', fontWeight: '600' }}>
                      <span>Expense created</span>
                      <button onClick={() => navigate('/expenses', { state: { highlightId: email.expenseId } })}
                        style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', background: 'transparent', color: '#2563eb', border: '1px solid #2563eb', borderRadius: '4px', cursor: 'pointer' }}>
                        View
                      </button>
                    </span>
                  ) : (
                    <button onClick={() => handleConvert(email.id)} disabled={converting === email.id}
                      style={{ padding: '0.25rem 0.6rem', fontSize: '0.75rem', background: converting === email.id ? '#e2e8f0' : '#2563eb', color: converting === email.id ? '#94a3b8' : '#fff', border: 'none', borderRadius: '4px', cursor: converting === email.id ? 'default' : 'pointer' }}>
                      {converting === email.id ? 'Converting...' : 'Convert to Expense'}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem' }}>
            <button onClick={() => goToPage(page - 1)} disabled={page === 0}
              style={{ padding: '0.3rem 0.8rem', fontSize: '0.8rem', border: '1px solid #e2e8f0', borderRadius: '6px', cursor: page === 0 ? 'default' : 'pointer', color: page === 0 ? '#cbd5e1' : '#1e3a5f', background: '#fff' }}>
              ← Prev
            </button>
            <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Page {page + 1}</span>
            <button onClick={() => goToPage(page + 1)} disabled={!hasMore}
              style={{ padding: '0.3rem 0.8rem', fontSize: '0.8rem', border: '1px solid #e2e8f0', borderRadius: '6px', cursor: !hasMore ? 'default' : 'pointer', color: !hasMore ? '#cbd5e1' : '#1e3a5f', background: '#fff' }}>
              Next →
            </button>
          </div>
        </>
      )}
    </div>
  )
}