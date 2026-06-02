import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock the REST client so the store maps canned backend shapes without real network.
vi.mock('./api.js', () => ({
  Api: {
    getSession: vi.fn(),
    getPlayers: vi.fn(),
    getCells: vi.fn(),
    getIncidents: vi.fn(),
    getScoreEvents: vi.fn(),
    getActions: vi.fn(),
    getUserSkills: vi.fn(),
    getManual: vi.fn(),
    submitAction: vi.fn(),
    runDiagnostic: vi.fn(),
    getDiagnostics: vi.fn(),
    runConsole: vi.fn(),
    leaveSession: vi.fn(),
  },
}));

import { Api } from './api.js';
import { Selectors, createBackendStore, statusOf } from './store.js';

describe('statusOf', () => {
  it('maps health to a status band', () => {
    expect(statusOf(0)).toBe('down');
    expect(statusOf(-1)).toBe('down');
    expect(statusOf(49)).toBe('crit');
    expect(statusOf(50)).toBe('warn');
    expect(statusOf(79)).toBe('warn');
    expect(statusOf(80)).toBe('good');
    expect(statusOf(100)).toBe('good');
  });
});

describe('Selectors', () => {
  it('networkHealth averages cell health, defaulting to 100 when empty', () => {
    expect(Selectors.networkHealth({ cells: [] })).toBe(100);
    expect(Selectors.networkHealth({ cells: [{ health: 90 }, { health: 70 }] })).toBe(80);
  });

  it('activeIncidents keeps only open incidents', () => {
    const s = { incidents: [{ status: 'open' }, { status: 'resolved' }, { status: 'open' }] };
    expect(Selectors.activeIncidents(s)).toHaveLength(2);
  });

  it('teamRank is the 1-based position of the "you" team by score', () => {
    const s = { teams: [{ score: 10, you: false }, { score: 30, you: true }, { score: 20, you: false }] };
    expect(Selectors.teamRank(s)).toBe(1);
  });
});

describe('createBackendStore', () => {
  let store;

  beforeEach(() => {
    Api.getSession.mockResolvedValue({ id: 1, sessionCode: 'ABC', status: 'ACTIVE' });
    Api.getPlayers.mockResolvedValue([
      { id: 2, teamName: 'Blue', score: 140 },
      { id: 3, teamName: 'Red', score: 0 },
    ]);
    Api.getCells.mockResolvedValue([
      { id: 10, cellName: 'Cell-A', healthStatus: 'WARNING', signalQuality: 80, userLoad: 50, latency: 30, packetLoss: 2, configStatus: 'STABLE' },
    ]);
    Api.getIncidents.mockResolvedValue([
      { id: 5, cellId: 10, incidentType: 'Congestion', severity: 'HIGH', status: 'RESOLVED',
        symptomGroup: 'Congestion',
        availableDiagnostics: [{ name: 'INSPECT_AUTOMATION', label: 'Inspect automation logs' }],
        createdAt: '2026-06-01T10:00:00Z', description: 'Overloaded' },
    ]);
    Api.getScoreEvents.mockResolvedValue([
      { id: 1, playerId: 2, points: 140, reason: 'Cell overload / CORRECT', createdAt: '2026-06-01T10:00:00Z' },
    ]);
    Api.getActions.mockResolvedValue([{ id: 4, actionName: 'REBALANCE_TRAFFIC', description: 'Move load' }]);
    Api.getUserSkills.mockResolvedValue({ learnedActions: ['REBALANCE_TRAFFIC'], learnedDiagnostics: [], tier: 'TRAINEE' });
  });

  afterEach(() => store?.stop());

  it('maps backend shapes to UI state and never exposes the root cause', async () => {
    store = createBackendStore({ session: { id: 1, sessionCode: 'ABC', status: 'ACTIVE' }, user: { username: 'ava' }, playerId: 2 });
    await vi.waitFor(() => expect(store.getState().incidents).toHaveLength(1));

    const s = store.getState();
    const inc = s.incidents[0];
    expect(inc.title).toBe('Congestion');
    expect(inc.severity).toBe('high');      // lowercased for the UI
    expect(inc.status).toBe('resolved');
    expect(inc.rec).toEqual([]);            // the hidden root cause is never sent to the client
    expect(inc.symptomGroup).toBe('Congestion');
    expect(inc.diagnostics).toEqual([{ name: 'INSPECT_AUTOMATION', label: 'Inspect automation logs' }]);

    expect(s.cells[0].health).toBe(58);     // WARNING -> 58
    const me = s.players.find((p) => p.you);
    expect(me.id).toBe(2);
    expect(me.score).toBe(140);

    expect(s.learnedActions).toEqual(['REBALANCE_TRAFFIC']);  // progression folded into state
    expect(s.tier).toBe('TRAINEE');
  });

  it('applyAction submits to the backend with numeric ids and returns the outcome', async () => {
    Api.submitAction.mockResolvedValue({ result: 'SUCCESS', pointsAwarded: 140 });
    store = createBackendStore({ session: { id: 1, sessionCode: 'ABC', status: 'ACTIVE' }, user: { username: 'ava' }, playerId: 2 });
    await vi.waitFor(() => expect(store.getState().incidents).toHaveLength(1));

    const outcome = await store.applyAction('5', '4');
    expect(Api.submitAction).toHaveBeenCalledWith(1, 5, 2, 4);
    expect(outcome).toEqual({ result: 'SUCCESS', pointsAwarded: 140 });
  });

  it('runDiagnostic / getDiagnostics call the API with numeric ids', async () => {
    Api.runDiagnostic.mockResolvedValue({ diagnostic: 'INSPECT_AUTOMATION', result: 'RULES_OUT' });
    Api.getDiagnostics.mockResolvedValue([]);
    store = createBackendStore({ session: { id: 1, sessionCode: 'ABC', status: 'ACTIVE' }, user: { username: 'ava' }, playerId: 2 });
    await vi.waitFor(() => expect(store.getState().incidents).toHaveLength(1));

    const ev = await store.runDiagnostic('5', 'INSPECT_AUTOMATION');
    expect(Api.runDiagnostic).toHaveBeenCalledWith(1, 5, 2, 'INSPECT_AUTOMATION');
    expect(ev).toEqual({ diagnostic: 'INSPECT_AUTOMATION', result: 'RULES_OUT' });

    await store.getDiagnostics('5');
    expect(Api.getDiagnostics).toHaveBeenCalledWith(1, 5, 2);
  });

  it('runConsole sends the command with numeric ids, and returns errors as printable output', async () => {
    Api.runConsole.mockResolvedValue({ recognised: true, output: '57% loss' });
    store = createBackendStore({ session: { id: 1, sessionCode: 'ABC', status: 'ACTIVE' }, user: { username: 'ava' }, playerId: 2 });
    await vi.waitFor(() => expect(store.getState().incidents).toHaveLength(1));

    const ok = await store.runConsole('5', 'traceroute o-ru');
    expect(Api.runConsole).toHaveBeenCalledWith(1, 5, 2, 'traceroute o-ru');
    expect(ok.output).toBe('57% loss');

    Api.runConsole.mockRejectedValue(new Error('Investigation budget used up'));
    const err = await store.runConsole('5', 'kubectl logs x');
    expect(err.recognised).toBe(false);
    expect(err.output).toContain('budget');
  });

  it('getManual fetches the field manual for the user', async () => {
    Api.getManual.mockResolvedValue({ tier: 'TRAINEE', diagnostics: [], actions: [], diagnosticsTotal: 6, actionsTotal: 9 });
    store = createBackendStore({ session: { id: 1, sessionCode: 'ABC', status: 'ACTIVE' }, user: { username: 'ava', id: 7 }, playerId: 2 });
    await vi.waitFor(() => expect(store.getState().incidents).toHaveLength(1));

    const m = await store.getManual();
    expect(Api.getManual).toHaveBeenCalledWith(7);
    expect(m.tier).toBe('TRAINEE');
  });
});
