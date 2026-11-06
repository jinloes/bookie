import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const BACKEND_URL = 'http://localhost:48763';

export default defineConfig({
  plugins: [react()],
  // Tauri CLI sets TAURI_* env vars; expose them to the app bundle
  envPrefix: ['VITE_', 'TAURI_'],
  // Keep the terminal clean when Tauri is managing the dev server
  clearScreen: false,
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    // Fixed port so Tauri's devUrl always matches
    port: 5173,
    strictPort: true,
    // Cargo's build output under src-tauri/target is locked mid-build on Windows, which crashes
    // the watcher; Tauri rebuilds the Rust side itself.
    watch: {
      ignored: ['**/src-tauri/**'],
    },
    proxy: {
      '/api': BACKEND_URL,
    },
  },
})
