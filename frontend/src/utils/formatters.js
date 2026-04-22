import dayjs from 'dayjs'

export const fmtCurrency = (n) => `$${Number(n).toFixed(2)}`
export const fmtDate = (iso) => dayjs(iso).format('MMM D, YYYY')
export const fmtDateTime = (iso) => dayjs(iso).format('MMM D, h:mm A')