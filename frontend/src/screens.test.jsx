import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// The 3D map needs WebGL, which jsdom doesn't provide — stub it so the screens import cleanly.
vi.mock('./NetworkMap3D.jsx', () => ({ NetworkMap: () => null }));

import { Incidents, Scoreboard, IncidentDetail } from './screens.jsx';

describe('Incidents screen', () => {
  it('lists incidents and shows the active count', () => {
    const state = {
      incidents: [
        { id: 5, title: 'Cell overload', cellId: 'Cell-A', severity: 'high', status: 'open', detectedAt: Date.now(), description: 'x', metrics: {}, rec: [] },
        { id: 6, title: 'Alarm storm', cellId: 'Cell-B', severity: 'low', status: 'resolved', detectedAt: Date.now(), description: 'y', metrics: {}, rec: [] },
      ],
      teams: [], players: [],
    };
    render(<Incidents state={state} nav={() => {}} />);

    expect(screen.getByText('Cell overload')).toBeInTheDocument();
    expect(screen.getByText('#5')).toBeInTheDocument();
    expect(screen.getByText('open')).toBeInTheDocument();      // StatusTag
    expect(screen.getByText(/2 incidents.*1 active/)).toBeInTheDocument();
  });
});

describe('Scoreboard screen', () => {
  it('shows team scores and marks the player as "you"', () => {
    const state = {
      game: 'ABC',
      teams: [
        { id: 'Blue', name: 'Blue', score: 140, resolved: 1, you: true },
        { id: 'Red', name: 'Red', score: 0, resolved: 0, you: false },
      ],
      players: [],
    };
    render(<Scoreboard state={state} nav={() => {}} />);

    expect(screen.getByText('140')).toBeInTheDocument();
    expect(screen.getByText('Red')).toBeInTheDocument();
    expect(screen.getByText(/\(you\)/)).toBeInTheDocument();
  });
});

describe('IncidentDetail action submission', () => {
  const baseState = {
    incidents: [{
      id: 5, title: 'Congestion', cellId: 'Cell-A', severity: 'high', status: 'open',
      detectedAt: Date.now(), description: 'Ambiguous congestion',
      symptomGroup: 'Congestion',
      diagnostics: [{ name: 'INSPECT_AUTOMATION', label: 'Inspect automation logs' }],
      diagnosticBudget: 1,
      candidates: [
        { cause: 'CELL_OVERLOAD', label: 'Cell overload', action: 'REBALANCE_TRAFFIC' },
        { cause: 'ROGUE_AUTOMATION', label: 'Rogue automation', action: 'DISABLE_AUTOMATION' },
      ],
      metrics: { signalQuality: 40, userLoad: 95, latency: 120, packetLoss: 8 }, rec: [],
    }],
    cells: [{ id: 'Cell-A', health: 25 }],
  };

  function storeWith(extra = {}) {
    return {
      ACTIONS: { 4: { id: 4, actionName: 'REBALANCE_TRAFFIC', name: 'Rebalance Traffic', desc: 'Move load', icon: 'shuffle' } },
      applyAction: vi.fn().mockResolvedValue({ result: 'SUCCESS', pointsAwarded: 140 }),
      getDiagnostics: vi.fn().mockResolvedValue([]),
      runDiagnostic: vi.fn(),
      runConsole: vi.fn().mockResolvedValue({ recognised: true, output: '' }),
      ...extra,
    };
  }

  it('submits the chosen action and shows the success outcome', async () => {
    const store = storeWith();
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    // apply(aid) is called with the incident id and the catalog key (a string).
    expect(store.applyAction).toHaveBeenCalledWith(5, '4');
    expect(await screen.findByText(/Correct fix/)).toBeInTheDocument();
    expect(screen.getByText('+140 pts')).toBeInTheDocument();
  });

  it('disables actions once the incident is closed', () => {
    const closedState = { ...baseState, incidents: [{ ...baseState.incidents[0], status: 'resolved' }] };
    const store = storeWith({ applyAction: vi.fn() });
    render(<IncidentDetail state={closedState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
  });

  it('runs a diagnostic and shows the evidence', async () => {
    const store = storeWith({
      runDiagnostic: vi.fn().mockResolvedValue({
        diagnostic: 'INSPECT_AUTOMATION', label: 'Inspect automation logs',
        result: 'RULES_OUT', finding: 'Rogue automation — ruled out.',
      }),
    });
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    const diagBtn = await screen.findByRole('button', { name: /Inspect automation logs/ });
    fireEvent.click(diagBtn);

    expect(store.runDiagnostic).toHaveBeenCalledWith(5, 'INSPECT_AUTOMATION');
    expect(await screen.findByText(/Rogue automation — ruled out/)).toBeInTheDocument();
  });

  it('rules a candidate out, uses up the budget, and does NOT hand over the action', async () => {
    const store = storeWith({
      runDiagnostic: vi.fn().mockResolvedValue({
        diagnostic: 'INSPECT_AUTOMATION', label: 'Inspect automation logs',
        result: 'RULES_OUT', implicated: 'ROGUE_AUTOMATION', finding: 'Rogue automation — ruled out.',
      }),
    });
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    fireEvent.click(await screen.findByRole('button', { name: /Inspect automation logs/ }));

    expect(await screen.findByText(/Rogue automation — ruled out/)).toBeInTheDocument();
    expect(screen.getByText('ruled out')).toBeInTheDocument();          // board updated
    expect(screen.queryByText(/Deduced — apply/)).toBeNull();           // no auto-answer
    // budget is 1 — the test is now spent, so the diagnostic can't be run again.
    expect(screen.getByRole('button', { name: /Inspect automation logs/ })).toBeDisabled();
  });

  it('runs a console command and prints the emulated output', async () => {
    const store = storeWith({
      runConsole: vi.fn().mockResolvedValue({ recognised: true, output: '3  o-ru-07  2.8 ms  57% loss' }),
    });
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    const input = await screen.findByLabelText('diagnostic console');
    fireEvent.change(input, { target: { value: 'traceroute o-ru' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(store.runConsole).toHaveBeenCalledWith(5, 'traceroute o-ru');
    expect(await screen.findByText(/57% loss/)).toBeInTheDocument();
  });

  it('shows the teaching modal the first time an action is used', async () => {
    const store = storeWith({
      applyAction: vi.fn().mockResolvedValue({
        result: 'SUCCESS', pointsAwarded: 140, justLearned: true,
        actionCommand: 'rrmctl rebalance --cell o-ru-07', diagnoseCommands: ['kubectl logs deploy/traffic-steering'],
      }),
    });
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText('Command learned')).toBeInTheDocument();
    expect(screen.getByText(/rrmctl rebalance --cell o-ru-07/)).toBeInTheDocument();
  });

  it('retires a learned action button — you apply it from the console', () => {
    const learnedState = { ...baseState, learnedActions: ['REBALANCE_TRAFFIC'] };
    render(<IncidentDetail state={learnedState} store={storeWith()} nav={() => {}} route={{ params: { id: 5 } }} />);

    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
  });
});
