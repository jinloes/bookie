import React from 'react'
import ReactDOM from 'react-dom/client'
import { MantineProvider, createTheme, Table } from '@mantine/core'
import { ModalsProvider } from '@mantine/modals'
import { Notifications } from '@mantine/notifications'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@mantine/core/styles.css'
import '@mantine/dates/styles.css'
import '@mantine/notifications/styles.css'
import './index.css'
import App from './App.jsx'

const theme = createTheme({
  primaryColor: 'violet',
  fontFamily: 'Inter, system-ui, sans-serif',
  defaultRadius: 'md',
  components: {
    Table: Table.extend({
      defaultProps: {
        highlightOnHover: true,
      },
      styles: {
        th: {
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          fontSize: '0.675rem',
          fontWeight: 700,
          color: 'var(--mantine-color-gray-5)',
          paddingTop: 10,
          paddingBottom: 10,
          whiteSpace: 'nowrap',
          borderBottom: '2px solid var(--mantine-color-gray-2)',
        },
        td: {
          fontSize: '0.875rem',
          verticalAlign: 'middle',
        },
      },
    }),
  },
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      retry: 1,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <MantineProvider theme={theme}>
        <Notifications />
        <ModalsProvider>
          <App />
        </ModalsProvider>
      </MantineProvider>
    </QueryClientProvider>
  </React.StrictMode>
)
