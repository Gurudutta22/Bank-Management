import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          <App />
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 3800,
              // A toast is a floating layer, so it belongs on the overlay tier with --elev-3.
              // The old single-radius pure-black shadow did nothing over the dark page, and the
              // raw hexes below it made a dark-mode variant impossible - toasts are transient,
              // so that kind of drift survives review indefinitely.
              style: {
                background: 'var(--surface-overlay)',
                color: 'var(--text-primary)',
                border: '1px solid var(--surface-border)',
                borderRadius: '0.75rem',
                fontSize: '0.875rem',
                boxShadow: 'var(--elev-3)',
              },
              success: { iconTheme: { primary: 'var(--accent-positive-solid)', secondary: '#ffffff' } },
              error: { iconTheme: { primary: 'var(--accent-danger-solid)', secondary: '#ffffff' } },
            }}
          />
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
)
