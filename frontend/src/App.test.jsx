import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

vi.mock('./NetworkMap3D.jsx', () => ({ NetworkMap: () => <div>Mock network map</div> }));

const { mockCreateBackendStore, storeRef, lobbyEntries } = vi.hoisted(() => ({
  mockCreateBackendStore: vi.fn(),
  storeRef: { current: null, state: null, subscribers: [] },
  lobbyEntries: [],
}));

vi.mock('./store.js', async () => {
  const actual = await vi.importActual('./store.js');
  return {
    ...actual,
    createBackendStore: mockCreateBackendStore,
  };
});

vi.mock('./Lobby.jsx', () => ({
  default: ({ onEnter }) => {
    lobbyEntries.push(onEnter);
    return <button onClick={() => onEnter({ user: { id: 7, username: 'alice' }, session: { id: 11, sessionCode: 'ABC123' }, playerId: 101 })}>Enter mocked lobby</button>;
  },
}));

vi.mock('./GameOver.jsx', () => ({
  default: ({ state, onExit }) => <div><span>Game over for {state.game}</span><button onClick={onExit}>Exit game</button></div>,
}));

import App from './App.jsx';

function makeState(overrides = {}) {
  const now = Date.now();
  return {
    game: 'ABC123',
    sessionStatus: 'ACTIVE',
    endsAt: now + 90_000,
    score: 1234,
    you: { player: 'Alice', team: 'Blue' },
    cells: [
      { id: 'Cell-A', health: 80, signalQuality: 90, userLoad: 40, latency: 20, packetLoss: 1, configStatus: 'STABLE' },
      { id: 'Cell-B', health: 45, signalQuality: 50, userLoad: 90, latency: 120, packetLoss: 8, configStatus: 'DRIFT' },
    ],
    links: [],
    incidents: [
      { id: 5, title: 'Cell overload', cellId: 'Cell-B', severity: 'high', status: 'open', detectedAt: now - 5000, description: 'Overloaded', metrics: { signalQuality: 50, userLoad: 90, latency: 120, packetLoss: 8 }, rec: [] },
      { id: 6, title: 'Recovered alarm', cellId: 'Cell-A', severity: 'low', status: 'resolved', detectedAt: now - 15_000, description: 'Recovered', metrics: { signalQuality: 90, userLoad: 40, latency: 20, packetLoss: 1 }, rec: [] },
    ],
    teams: [
      { id: 'Blue', name: 'Blue', score: 1234, health: 80, resolved: 2, you: true },
      { id: 'Red', name: 'Red', score: 900, health: 60, resolved: 1, you: false },
    ],
    players: [
      { id: 101, name: 'Alice', team: 'Blue', score: 1234, resolved: 2, you: true },
      { id: 102, name: 'Bob', team: 'Red', score: 900, resolved: 1, you: false },
    ],
    activity: [
      { id: 1, kind: 'apply', when: now - 2000, text: 'Alice fixed overload', points: 120, playerId: 101 },
    ],
    ...overrides,
  };
}

function installStore(state = makeState()) {
  storeRef.state = state;
  storeRef.subscribers = [];
  storeRef.current = {
    ACTIONS: { 4: { id: 4, name: 'Rebalance Traffic', desc: 'Move load', icon: 'shuffle' } },
    getState: vi.fn(() => storeRef.state),
    subscribe: vi.fn((fn) => { storeRef.subscribers.push(fn); return vi.fn(); }),
    stop: vi.fn(),
    leave: vi.fn().mockResolvedValue(undefined),
    applyAction: vi.fn(),
  };
  mockCreateBackendStore.mockReturnValue(storeRef.current);
}

async function enterApp() {
  fireEvent.click(screen.getByRole('button', { name: 'Enter mocked lobby' }));
  await screen.findByText('Operations Dashboard');
}

describe('App shell', () => {
  beforeEach(() => {
    localStorage.clear();
    lobbyEntries.length = 0;
    mockCreateBackendStore.mockReset();
    installStore();
    document.exitFullscreen = vi.fn();
    document.documentElement.requestFullscreen = vi.fn();
    Object.defineProperty(document, 'fullscreenElement', { value: null, configurable: true });
  });

  it('starts in the lobby, enters a backend game, renders shell state, and opens tweaks from settings', async () => {
    render(<App />);

    expect(screen.getByRole('button', { name: 'Enter mocked lobby' })).toBeInTheDocument();
    await enterApp();

    expect(mockCreateBackendStore).toHaveBeenCalledWith({ user: { id: 7, username: 'alice' }, session: { id: 11, sessionCode: 'ABC123' }, playerId: 101 });
    expect(screen.getByText('GAME')).toBeInTheDocument();
    expect(screen.getAllByText('1,234').length).toBeGreaterThan(0);
    expect(screen.getByText('ONLINE')).toBeInTheDocument();
    expect(screen.getAllByText('Alice').length).toBeGreaterThan(0);
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#ffb020');

    fireEvent.click(screen.getByText('Settings'));
    expect(await screen.findByText('Session & appearance')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Open Tweaks/ }));
    await waitFor(() => expect(screen.getByText('Accent')).toBeInTheDocument());
  });

  it('navigates, toggles fullscreen, cancels leave, and confirms leave through the store', async () => {
    render(<App />);
    await enterApp();

    fireEvent.click(screen.getByText('Incidents'));
    expect(screen.getByText(/2 incidents/)).toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Fullscreen'));
    expect(document.documentElement.requestFullscreen).toHaveBeenCalledTimes(1);
    Object.defineProperty(document, 'fullscreenElement', { value: document.documentElement, configurable: true });
    fireEvent.click(screen.getByTitle('Fullscreen'));
    expect(document.exitFullscreen).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTitle('Leave match'));
    const dialog = screen.getByText('Leave match?').closest('.panel');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByText('Leave match?')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Leave match'));
    const confirmDialog = screen.getByText('Leave match?').closest('.panel');
    fireEvent.click(within(confirmDialog).getByRole('button', { name: 'Leave match' }));

    await waitFor(() => expect(storeRef.current.leave).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole('button', { name: 'Enter mocked lobby' })).toBeInTheDocument();
  });

  it('renders the game-over screen for ended sessions and exits to the lobby', async () => {
    installStore(makeState({ sessionStatus: 'ENDED' }));
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Enter mocked lobby' }));
    expect(await screen.findByText('Game over for ABC123')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Exit game' }));
    expect(screen.getByRole('button', { name: 'Enter mocked lobby' })).toBeInTheDocument();
  });
});
