import { useState } from 'react'

/**
 * Manages a string-list field inside a form object.
 * @param {string} key - the key in form whose value is a string[]
 * @param {object} form
 * @param {function} setForm
 */
export function useListField(key, form, setForm) {
  const [input, setInput] = useState('')

  const add = () => {
    const trimmed = input.trim()
    if (!trimmed || (form[key] ?? []).includes(trimmed)) return
    setForm(f => ({ ...f, [key]: [...(f[key] ?? []), trimmed] }))
    setInput('')
  }

  const remove = (item) =>
    setForm(f => ({ ...f, [key]: (f[key] ?? []).filter(x => x !== item) }))

  const reset = () => setInput('')

  return { input, setInput, add, remove, reset }
}