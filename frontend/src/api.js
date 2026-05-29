/* ============================================================
   api.js — thin REST client for the O-RAN backend.
   All calls go through the Vite dev proxy (/api -> http://localhost:8080,
   see vite.config.js); in production nginx serves the same /api path.
   Methods mirror the Spring controllers 1:1.
   ============================================================ */

const BASE = '/api';

/** Error carrying the HTTP status and the backend's { status, error, message } body. */
export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let detail = null;
    try { detail = await res.json(); } catch { /* non-JSON error body */ }
    throw new ApiError(res.status, detail?.message || res.statusText, detail);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const Api = {
  // --- action catalog ---
  getActions: () => request('/actions'),

  // --- users ---
  createUser: (username, role = 'PLAYER') =>
    request('/users', { method: 'POST', body: { username, role } }),
  getUser: (id) => request(`/users/${id}`),

  // --- sessions ---
  createSession: (name, createdByUserId, durationSeconds) =>
    request('/sessions', { method: 'POST', body: { name, createdByUserId, durationSeconds } }),
  listSessions: () => request('/sessions'),
  getSession: (id) => request(`/sessions/${id}`),
  joinSession: (id, userId, teamName) =>
    request(`/sessions/${id}/join`, { method: 'POST', body: { userId, teamName } }),
  startSession: (id) => request(`/sessions/${id}/start`, { method: 'POST' }),
  getPlayers: (id) => request(`/sessions/${id}/players`),

  // --- network cells ---
  getCells: (sessionId) => request(`/sessions/${sessionId}/cells`),
  getCell: (sessionId, cellId) => request(`/sessions/${sessionId}/cells/${cellId}`),

  // --- incidents ---
  getIncidents: (sessionId, status) =>
    request(`/sessions/${sessionId}/incidents${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  getIncident: (sessionId, incidentId) =>
    request(`/sessions/${sessionId}/incidents/${incidentId}`),
  submitAction: (sessionId, incidentId, playerId, actionId) =>
    request(`/sessions/${sessionId}/incidents/${incidentId}/actions`,
      { method: 'POST', body: { playerId, actionId } }),
  getIncidentActions: (sessionId, incidentId) =>
    request(`/sessions/${sessionId}/incidents/${incidentId}/actions`),

  // --- scores ---
  getScoreboard: (sessionId) => request(`/sessions/${sessionId}/scores`),
  getScoreEvents: (sessionId) => request(`/sessions/${sessionId}/scores/events`),
};
