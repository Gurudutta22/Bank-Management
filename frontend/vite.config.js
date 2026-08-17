import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Dev-time proxy: the browser calls /api on the Vite origin and Vite forwards to Spring Boot,
    // so the production build can use a relative base URL unchanged.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // `changeOrigin` rewrites the Host header but NOT Origin. The browser still sends
          // its own `Origin: http://localhost:<vite port>`, Vite forwards it verbatim, and
          // Spring's CORS filter answers 403 "Invalid CORS request" unless that exact port
          // happens to be in app.cors.allowed-origins. Net effect: running the dev server on
          // any port other than 5173 silently breaks login, with a misleading error.
          //
          // The browser already treats this as same-origin - it called /api on the Vite
          // origin - so presenting the target's own origin is what makes the backend agree.
          // It also makes dev match production, where nginx proxies /api and no cross-origin
          // Origin header ever reaches Spring either.
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('origin', 'http://localhost:8080')
          })
        },
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // Split the heavy, rarely-changing libraries into their own chunks so a code change
        // does not force users to re-download React and Recharts.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
        },
      },
    },
  },
})
