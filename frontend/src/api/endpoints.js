import api from './client'

/**
 * One module listing every server call the app can make.
 *
 * Keeping URLs out of components means a route rename is a single-file change, and it makes the
 * app's full surface area readable at a glance.
 */

export const authApi = {
  login: (payload) => api.post('/auth/login', payload).then((r) => r.data),
  register: (payload) => api.post('/auth/register', payload).then((r) => r.data),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
  me: () => api.get('/auth/me').then((r) => r.data),
}

export const accountApi = {
  list: () => api.get('/accounts').then((r) => r.data),
  get: (accountNumber) => api.get(`/accounts/${accountNumber}`).then((r) => r.data),
  open: (payload) => api.post('/accounts', payload).then((r) => r.data),
  close: (accountNumber) => api.delete(`/accounts/${accountNumber}`).then((r) => r.data),
  // responseType 'blob' so the CSV arrives as binary and is not mangled by JSON parsing.
  statement: (accountNumber, from, to) =>
    api.get(`/accounts/${accountNumber}/statement`, {
      params: { from, to },
      responseType: 'blob',
    }),
}

export const transactionApi = {
  dashboard: () => api.get('/transactions/dashboard').then((r) => r.data),
  search: (params) => api.get('/transactions', { params }).then((r) => r.data),
  deposit: (payload) => api.post('/transactions/deposit', payload).then((r) => r.data),
  withdraw: (payload) => api.post('/transactions/withdraw', payload).then((r) => r.data),
  transfer: (payload) => api.post('/transactions/transfer', payload).then((r) => r.data),
}

export const beneficiaryApi = {
  list: () => api.get('/beneficiaries').then((r) => r.data),
  add: (payload) => api.post('/beneficiaries', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/beneficiaries/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/beneficiaries/${id}`),
}

export const profileApi = {
  get: () => api.get('/users/me').then((r) => r.data),
  update: (payload) => api.put('/users/me', payload).then((r) => r.data),
  changePassword: (payload) => api.post('/users/me/password', payload),
}

export const adminApi = {
  stats: () => api.get('/admin/stats').then((r) => r.data),
  users: (params) => api.get('/admin/users', { params }).then((r) => r.data),
  accounts: (params) => api.get('/admin/accounts', { params }).then((r) => r.data),
  auditLogs: (params) => api.get('/admin/audit-logs', { params }).then((r) => r.data),
  setUserStatus: (publicId, payload) =>
    api.patch(`/admin/users/${publicId}/status`, payload).then((r) => r.data),
  setKyc: (publicId, verified) =>
    api.patch(`/admin/users/${publicId}/kyc`, null, { params: { verified } }).then((r) => r.data),
  setAccountStatus: (accountNumber, payload) =>
    api.patch(`/admin/accounts/${accountNumber}/status`, payload).then((r) => r.data),
}
