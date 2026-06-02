import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Api, ApiError } from './api.js';

function response({ ok = true, status = 200, body = {} } = {}) {
  return { ok, status, statusText: 'Status', json: async () => body };
}

describe('Api client', () => {
  beforeEach(() => {
    global.fetch = vi.fn().mockResolvedValue(response({ body: {} }));
  });

  it('getIncidents sends playerId AND status as query params (regression: not playerId-as-status)', async () => {
    await Api.getIncidents(1, 2, 'open');
    expect(fetch).toHaveBeenCalledWith('/api/sessions/1/incidents?playerId=2&status=open', expect.anything());
  });

  it('getIncidents with only a playerId omits status', async () => {
    await Api.getIncidents(1, 2);
    expect(fetch).toHaveBeenCalledWith('/api/sessions/1/incidents?playerId=2', expect.anything());
  });

  it('getCells appends playerId (and never a status param)', async () => {
    await Api.getCells(1, 2);
    expect(fetch).toHaveBeenCalledWith('/api/sessions/1/cells?playerId=2', expect.anything());
  });

  it('getCells without a playerId adds no query string', async () => {
    await Api.getCells(1);
    expect(fetch).toHaveBeenCalledWith('/api/sessions/1/cells', expect.anything());
  });

  it('submitAction POSTs the right URL and JSON body', async () => {
    await Api.submitAction(1, 3, 2, 4);
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/sessions/1/incidents/3/actions');
    expect(opts.method).toBe('POST');
    expect(opts.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(opts.body)).toEqual({ playerId: 2, actionId: 4 });
  });

  it('login POSTs the username', async () => {
    await Api.login('ava');
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/users/login');
    expect(JSON.parse(opts.body)).toEqual({ username: 'ava' });
  });

  it('runDiagnostic POSTs the diagnostic to the incident', async () => {
    await Api.runDiagnostic(1, 3, 2, 'TRACE_TRANSPORT');
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toBe('/api/sessions/1/incidents/3/diagnostics');
    expect(opts.method).toBe('POST');
    expect(JSON.parse(opts.body)).toEqual({ playerId: 2, diagnostic: 'TRACE_TRANSPORT' });
  });

  it('getDiagnostics builds the URL with playerId', async () => {
    await Api.getDiagnostics(1, 3, 2);
    expect(fetch).toHaveBeenCalledWith(
      '/api/sessions/1/incidents/3/diagnostics?playerId=2', expect.anything());
  });

  it('throws ApiError carrying the backend status and message on a non-ok response', async () => {
    fetch.mockResolvedValue(response({
      ok: false, status: 400,
      body: { status: 400, error: 'Bad Request', message: 'Incident is not open' },
    }));
    await expect(Api.submitAction(1, 3, 2, 4)).rejects.toBeInstanceOf(ApiError);
    await expect(Api.submitAction(1, 3, 2, 4)).rejects.toMatchObject({
      status: 400, message: 'Incident is not open',
    });
  });

  it('returns null for a 204 No Content', async () => {
    fetch.mockResolvedValue({ ok: true, status: 204, json: async () => { throw new Error('no body'); } });
    await expect(Api.getActions()).resolves.toBeNull();
  });
});
