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
      id: 5, title: 'Cell overload', cellId: 'Cell-A', severity: 'high', status: 'open',
      detectedAt: Date.now(), description: 'Overloaded',
      metrics: { signalQuality: 40, userLoad: 95, latency: 120, packetLoss: 8 }, rec: [],
    }],
    cells: [{ id: 'Cell-A', health: 25 }],
  };

  it('submits the chosen action and shows the success outcome', async () => {
    const store = {
      ACTIONS: { 4: { id: 4, name: 'Rebalance Traffic', desc: 'Move load to neighbours', icon: 'shuffle' } },
      applyAction: vi.fn().mockResolvedValue({ result: 'SUCCESS', pointsAwarded: 140 }),
    };
    render(<IncidentDetail state={baseState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    // apply(aid) is called with the incident id and the catalog key (a string).
    expect(store.applyAction).toHaveBeenCalledWith(5, '4');
    expect(await screen.findByText(/Correct fix/)).toBeInTheDocument();
    expect(screen.getByText('+140 pts')).toBeInTheDocument();
  });

  it('disables actions once the incident is closed', () => {
    const closedState = { ...baseState, incidents: [{ ...baseState.incidents[0], status: 'resolved' }] };
    const store = { ACTIONS: { 4: { id: 4, name: 'Rebalance Traffic', desc: 'x', icon: 'shuffle' } }, applyAction: vi.fn() };
    render(<IncidentDetail state={closedState} store={store} nav={() => {}} route={{ params: { id: 5 } }} />);

    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
  });
});
