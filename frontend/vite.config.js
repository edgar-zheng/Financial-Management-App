import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  // Shared repository-root .env; Vite exposes only VITE_-prefixed values to the browser.
  envDir: fileURLToPath(new URL('..', import.meta.url)),
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
