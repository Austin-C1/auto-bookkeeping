import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const API_URL = env.VITE_API_URL || 'http://localhost:18001'
  const VERSION = env.VERSION || 'dev'
  const GIT_TAG = env.GIT_TAG || ''
  const GITHUB_REPO_URL = env.GITHUB_REPO_URL || ''

  return {
    plugins: [react()],
    define: {
      'window.__VERSION__': JSON.stringify({
        version: VERSION,
        gitTag: GIT_TAG,
        githubRepoUrl: GITHUB_REPO_URL,
      }),
    },
    build: {
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return undefined
            if (id.includes('antd') || id.includes('@ant-design')) return 'vendor-antd'
            if (id.includes('react')) return 'vendor-react'
            return 'vendor'
          },
        },
      },
    },
    server: {
      port: 18882,
      proxy: {
        '/api': {
          target: API_URL,
          changeOrigin: true,
        },
      },
    },
  }
})
