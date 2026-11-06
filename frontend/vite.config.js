import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

export default defineConfig(({ command, mode }) => {
  const envDir = fileURLToPath(new URL('..', import.meta.url))
  const env = loadEnv(mode, envDir, 'VITE_')
  if (command === 'build' && process.env.VERCEL === '1') {
    const base = env.VITE_API_BASE_URL?.trim()
    if (!base) throw new Error('Set VITE_API_BASE_URL to the HTTPS backend origin in Vercel before building.')
    const url = new URL(base)
    if (url.protocol !== 'https:' || url.pathname !== '/' || url.search || url.hash || url.username || url.password) {
      throw new Error('VITE_API_BASE_URL must be an HTTPS origin without an /api path, query, or credentials.')
    }
  }
  return {
    // Shared repository-root .env; Vite exposes only VITE_-prefixed values to the browser.
    envDir,
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: { '/api': 'http://localhost:8080' },
    },
  }
})
