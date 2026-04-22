import { describe, it, expect } from 'vitest'
import { fmtCurrency, fmtDate, fmtDateTime } from './formatters.js'

describe('fmtCurrency', () => {
  it('formats integer with two decimal places', () => {
    expect(fmtCurrency(5)).toBe('$5.00')
  })

  it('pads single decimal', () => {
    expect(fmtCurrency(10.5)).toBe('$10.50')
  })

  it('accepts string numbers', () => {
    expect(fmtCurrency('48.52')).toBe('$48.52')
  })

  it('formats zero', () => {
    expect(fmtCurrency(0)).toBe('$0.00')
  })

  it('rounds to two decimals', () => {
    expect(fmtCurrency(1.009)).toBe('$1.01')
  })
})

describe('fmtDate', () => {
  it('formats ISO date string', () => {
    // Use noon to avoid UTC midnight rolling back a day in negative-offset zones
    expect(fmtDate('2026-04-22T12:00:00')).toBe('Apr 22, 2026')
  })
})

describe('fmtDateTime', () => {
  it('formats ISO datetime to readable time', () => {
    expect(fmtDateTime('2026-04-22T15:30:00')).toBe('Apr 22, 3:30 PM')
  })

  it('formats midnight correctly', () => {
    expect(fmtDateTime('2026-01-01T00:00:00')).toBe('Jan 1, 12:00 AM')
  })
})