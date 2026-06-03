import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';

vi.mock('./NetworkMap3D.jsx', () => ({
  NetworkMap: ({ onSelect, selectedId, height }) => (
    <button data-selected={selectedId || ''} data-height={height} onClick={() => onSelect?.('Cell-B')}>Mock network map</button>
  ),
}));

import { Actions, Dashboard, Incidents, NetworkMapPage, Players, Scoreboard, Settings } from './screens.jsx';

function sampleState(overrides = {}) {
  const now = Date.now();
  const incidents = [
    { id: 1, title: 'Cell overload', cellId: 'Cell-B', severity: 'high', status: 'open', detectedAt: now - 5_000, description: 'Cell is overloaded', metrics: { signalQuality: 45, userLoad: 95, latency: 120, packetLoss: 10 }, rec: ['4'] },
    { id: 2, title: 'Configuration drift', cellId: 'Cell-A', severity: 'medium', status: 'open', detectedAt: now - 70_000, description: 'Parameter drift', metrics: { signalQuality: 55, userLoad: 70, latency: 80, packetLoss: 5 }, rec: [] },
    { id: 3, title: 'Recovered alarm', cellId: 'Cell-C', severity: 'low', status: 'resolved', detectedAt: now - 90_000, description: 'Recovered', metrics: { signalQuality: 90, userLoad: 20, latency: 20, packetLoss: 1 }, rec: [] },
  ];
  return {
    game: 'ABC123',
    score: 1530,
    you: { player: 'Alice', team: 'Blue' },
    cells: [
      { id: 'Cell-A', health: 85, signalQuality: 92, userLoad: 35, latency: 24, packetLoss: 1, configStatus: 'STABLE' },
      { id: 'Cell-B', health: 36, signalQuality: 45, userLoad: 95, latency: 120, packetLoss: 10, configStatus: 'DRIFT' },
      { id: 'Cell-C', health: 72, signalQuality: 78, userLoad: 60, latency: 50, packetLoss: 3, configStatus: 'STABLE' },
      { id: 'Cell-D', health: 50, signalQuality: 70, userLoad: 65, latency: 60, packetLoss: 4, configStatus: 'STABLE' },
    ],
    links: [{ a: 'Cell-A', b: 'Cell-B' }],
    incidents,
    teams: [
      { id: 'Blue', name: 'Blue', score: 1530, health: 80, resolved: 4, you: true },
      { id: 'Red', name: 'Red', score: 900, health: 60, resolved: 2, you: false },
    ],
    players: [
      { id: 101, name: 'Alice', team: 'Blue', score: 1530, resolved: 4, you: true },
      { id: 102, name: 'Ava', team: 'Blue', score: 300, resolved: 1, you: false },
      { id: 103, name: 'Bob', team: 'Red', score: 900, resolved: 2, you: false },
    ],
    activity: [
      { id: 1, kind: 'incident', when: now - 5_000, text: 'Incident detected', points: 0 },
      { id: 2, kind: 'apply', when: now - 4_000, text: 'Alice applied fix', points: 120 },
      { id: 3, kind: 'ack', when: now - 3_000, text: 'Bob acknowledged alarm', points: -20 },
    ],
    ...overrides,
  };
}

const store = {
  ACTIONS: {
    4: { id: 4, name: 'Rebalance Traffic', desc: 'Move load to neighbours', icon: 'shuffle' },
    8: { id: 8, name: 'Rollback Config', desc: 'Restore stable parameters', icon: 'rewind' },
  },
  applyAction: vi.fn(),
  // The Actions screen is now the Field Manual; it asks the store for the learned commands.
  getManual: vi.fn().mockResolvedValue({
    tier: 'TRAINEE', diagnostics: [], actions: [], diagnosticsTotal: 6, actionsTotal: 9,
  }),
};

describe('additional screen coverage', () => {
  it('Dashboard renders all summary panels and routes through stat links and map selection', () => {
    const nav = vi.fn();
    render(<Dashboard state={sampleState()} nav={nav} />);

    expect(screen.getByText('Operations Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Network Health')).toBeInTheDocument();
    expect(screen.getAllByText('Active Incidents').length).toBeGreaterThan(0);
    expect(screen.getByText('Live Activity Feed')).toBeInTheDocument();

    fireEvent.click(screen.getByText('View all →'));
    expect(nav).toHaveBeenCalledWith('incidents');
    fireEvent.click(screen.getByText('Full board'));
    expect(nav).toHaveBeenCalledWith('scoreboard');
    fireEvent.click(screen.getByRole('button', { name: 'Mock network map' }));
    expect(nav).toHaveBeenCalledWith('incident', { id: 1 });
  });

  it('NetworkMapPage inspects a selected cell, opens incident detail from the inspector, and switches to topology', () => {
    const nav = vi.fn();
    render(<NetworkMapPage state={sampleState()} store={store} nav={nav} route={{ params: { cell: 'Cell-B' } }} />);

    expect(screen.getByText('Cell-B')).toBeInTheDocument();
    expect(screen.getByText('36%')).toBeInTheDocument();
    expect(screen.getByText('DRIFT')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Cell overload'));
    expect(nav).toHaveBeenCalledWith('incident', { id: 1 });

    fireEvent.click(screen.getByRole('button', { name: 'Topology' }));
    expect(screen.getByText(/O-RAN Topology/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('O-DU'));
    expect(screen.getByText(/Distributed Unit/)).toBeInTheDocument();
  });

  it('NetworkMapPage shows the empty inspector before a map selection and routes to map for healthy cells', () => {
    render(<NetworkMapPage state={sampleState({ incidents: [] })} store={store} nav={() => {}} route={{ params: {} }} />);

    expect(screen.getByText(/Select a cell/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Mock network map' }));
    expect(screen.getByText('Cell-B')).toBeInTheDocument();
  });

  it('Incidents filters by severity, searches, paginates, and handles empty results', () => {
    const many = Array.from({ length: 10 }, (_, i) => ({
      id: i + 10,
      title: `Generated alarm ${i}`,
      cellId: `Cell-${i}`,
      severity: i === 9 ? 'medium' : 'low',
      status: i % 2 === 0 ? 'open' : 'resolved',
      detectedAt: Date.now() - i * 1000,
      description: 'Generated',
      metrics: { signalQuality: 80, userLoad: 20, latency: 20, packetLoss: 0 },
      rec: [],
    }));
    const nav = vi.fn();
    render(<Incidents state={sampleState({ incidents: many })} nav={nav} />);

    expect(screen.getByText(/10 incidents/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '2' }));
    expect(screen.getByText('Generated alarm 8')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'medium' }));
    expect(screen.getByText('Generated alarm 9')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('Search…'), { target: { value: 'missing' } });
    expect(screen.getByText('No incidents match this filter.')).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('Search…'), { target: { value: '9' } });
    fireEvent.click(screen.getByText('Generated alarm 9'));
    expect(nav).toHaveBeenCalledWith('incident', { id: 19 });
  });

  it('Field Manual shows the learned commands, the applied log, and the empty applied state', async () => {
    const nav = vi.fn();
    store.getManual.mockResolvedValueOnce({
      tier: 'OPERATOR',
      diagnostics: [{ name: 'TRACE_TRANSPORT', command: 'traceroute o-ru', investigates: 'Transport link fault' }],
      actions: [{ name: 'REBALANCE_TRAFFIC', label: 'Rebalance Traffic', command: 'rrmctl rebalance --cell o-ru-07' }],
      diagnosticsTotal: 6, actionsTotal: 9,
    });
    const { rerender } = render(<Actions state={sampleState()} store={store} nav={nav} />);

    expect(screen.getByText('Field Manual')).toBeInTheDocument();
    expect(screen.getByText('Recently Applied')).toBeInTheDocument();
    // the learned commands arrive asynchronously from getManual()
    expect(await screen.findByText('$ traceroute o-ru')).toBeInTheDocument();
    expect(screen.getByText('Rebalance Traffic')).toBeInTheDocument();

    rerender(<Actions state={sampleState({ activity: [] })} store={store} nav={nav} />);
    expect(screen.getByText('No actions applied yet.')).toBeInTheDocument();
  });

  it('Scoreboard switches between teams and global player ranking', () => {
    render(<Scoreboard state={sampleState()} nav={() => {}} />);

    expect(screen.getByText('Game ABC123 · live ranking')).toBeInTheDocument();
    expect(screen.getByText('Blue')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Global' }));
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });

  it('Players groups by team and Settings opens the tweak bridge', () => {
    const openTweaks = vi.fn();
    const { rerender } = render(<Players state={sampleState()} />);

    expect(screen.getByText('3 players across 2 teams')).toBeInTheDocument();
    expect(screen.getByText(/Team Blue/)).toBeInTheDocument();
    expect(screen.getByText(/your team/)).toBeInTheDocument();

    rerender(<Settings state={sampleState()} openTweaks={openTweaks} />);
    expect(screen.getByText('Session & appearance')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Open Tweaks/ }));
    expect(openTweaks).toHaveBeenCalledTimes(1);
  });
});
