import React from 'react'
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import Incomes from './pages/Incomes.jsx'
import Expenses from './pages/Expenses.jsx'
import Agent from './pages/Agent.jsx'
import Properties from './pages/Properties.jsx'
import Payers from './pages/Payers.jsx'

const navStyle = {
  display: 'flex',
  gap: '1rem',
  padding: '1rem 2rem',
  background: '#1e3a5f',
  alignItems: 'center',
}

const linkStyle = {
  color: '#cbd5e1',
  textDecoration: 'none',
  padding: '0.4rem 0.8rem',
  borderRadius: '6px',
  fontSize: '0.9rem',
}

const activeLinkStyle = {
  ...linkStyle,
  background: '#2563eb',
  color: '#fff',
}

export default function App() {
  return (
    <BrowserRouter>
      <nav style={navStyle}>
        <span style={{ color: '#fff', fontWeight: '700', fontSize: '1.2rem', marginRight: '1rem' }}>
          🏠 Bookie
        </span>
        <NavLink to="/" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle} end>Dashboard</NavLink>
        <NavLink to="/incomes" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Income</NavLink>
        <NavLink to="/expenses" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Expenses</NavLink>
        <NavLink to="/agent" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>AI Agent</NavLink>
        <NavLink to="/properties" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Properties</NavLink>
        <NavLink to="/payers" style={({ isActive }) => isActive ? activeLinkStyle : linkStyle}>Payers</NavLink>
      </nav>
      <main style={{ padding: '2rem', maxWidth: '1100px', margin: '0 auto' }}>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/incomes" element={<Incomes />} />
          <Route path="/expenses" element={<Expenses />} />
          <Route path="/agent" element={<Agent />} />
          <Route path="/properties" element={<Properties />} />
          <Route path="/payers" element={<Payers />} />
        </Routes>
      </main>
    </BrowserRouter>
  )
}
