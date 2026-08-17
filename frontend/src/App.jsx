import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import AppShell from './components/layout/AppShell'
import { PageLoader } from './components/ui'

import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Accounts from './pages/Accounts'
import Transfer from './pages/Transfer'
import Transactions from './pages/Transactions'
import Beneficiaries from './pages/Beneficiaries'
import Profile from './pages/Profile'
import AdminDashboard from './pages/admin/AdminDashboard'
import AdminUsers from './pages/admin/AdminUsers'
import AdminAccounts from './pages/admin/AdminAccounts'
import AdminAudit from './pages/admin/AdminAudit'
import NotFound from './pages/NotFound'

/**
 * Route guard.
 *
 * Client-side guards are for user experience only - they stop a customer seeing a broken admin
 * screen. They are not a security control: the real check is Spring Security rejecting the
 * request, because anyone can edit JavaScript in their own browser.
 */
function RequireAuth({ children, adminOnly = false }) {
  const { isAuthenticated, isAdmin, loading } = useAuth()
  const location = useLocation()

  if (loading) return <PageLoader label="Restoring your session" />

  if (!isAuthenticated) {
    // Remember where they were headed so login can send them back there.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (adminOnly && !isAdmin) return <Navigate to="/dashboard" replace />

  return children
}

function RedirectIfAuthed({ children }) {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <PageLoader label="Loading" />
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfAuthed>
            <Login />
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthed>
            <Register />
          </RedirectIfAuthed>
        }
      />

      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/accounts" element={<Accounts />} />
        <Route path="/transfer" element={<Transfer />} />
        <Route path="/transactions" element={<Transactions />} />
        <Route path="/beneficiaries" element={<Beneficiaries />} />
        <Route path="/profile" element={<Profile />} />

        <Route
          path="/admin"
          element={
            <RequireAuth adminOnly>
              <AdminDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="/admin/users"
          element={
            <RequireAuth adminOnly>
              <AdminUsers />
            </RequireAuth>
          }
        />
        <Route
          path="/admin/accounts"
          element={
            <RequireAuth adminOnly>
              <AdminAccounts />
            </RequireAuth>
          }
        />
        <Route
          path="/admin/audit"
          element={
            <RequireAuth adminOnly>
              <AdminAudit />
            </RequireAuth>
          }
        />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
