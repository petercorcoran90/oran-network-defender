import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';

const { mockApi, ApiError } = vi.hoisted(() => {
  class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  }
  return {
    ApiError,
    mockApi: {
      login: vi.fn(),
      getHighScores: vi.fn(),
      createSession: vi.fn(),
      joinSession: vi.fn(),
      listSessions: vi.fn(),
      getSessionByCode: vi.fn(),
      getSession: vi.fn(),
      getPlayers: vi.fn(),
      ready: vi.fn(),
    },
  };
});

vi.mock('./api.js', () => ({ Api: mockApi, ApiError }));

import Lobby from './Lobby.jsx';

const user = { id: 7, username: 'alice' };
const waitingSession = { id: 11, name: 'Ops drill', sessionCode: 'L5Y9FS', status: 'WAITING' };
const activeSession = { ...waitingSession, status: 'ACTIVE' };
const player = { id: 101, teamName: 'Blue', ready: false };
const opponent = { id: 102, teamName: 'Red', ready: false };

async function identify(name = '  alice  ') {
  fireEvent.change(screen.getByPlaceholderText('e.g. alice'), { target: { value: name } });
  fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
  await screen.findByText(/Signed in as/);
}

async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('Lobby', () => {
  beforeEach(() => {
    vi.useRealTimers();
    Object.values(mockApi).forEach((fn) => fn.mockReset());
    mockApi.login.mockResolvedValue(user);
    mockApi.getHighScores.mockResolvedValue([]);
    mockApi.createSession.mockResolvedValue(waitingSession);
    mockApi.joinSession.mockResolvedValue(player);
    mockApi.listSessions.mockResolvedValue([waitingSession, { id: 12, name: 'Busy', sessionCode: 'BUSY01', status: 'ACTIVE' }]);
    mockApi.getSessionByCode.mockResolvedValue(waitingSession);
    mockApi.getSession.mockResolvedValue(waitingSession);
    mockApi.getPlayers.mockResolvedValue([player]);
    mockApi.ready.mockResolvedValue({ ...player, ready: true });
  });

  afterEach(() => vi.useRealTimers());

  it('identifies a user, creates a match with chosen settings, polls the room, and leaves back to choose', async () => {
    render(<Lobby onEnter={() => {}} />);

    await identify();
    expect(mockApi.login).toHaveBeenCalledWith('alice');
    expect(screen.getByDisplayValue("alice's match")).toBeInTheDocument();

    fireEvent.change(document.querySelector('input[type="range"]'), { target: { value: '9' } });
    fireEvent.click(screen.getByRole('button', { name: /Hard/ }));
    fireEvent.change(screen.getByDisplayValue("alice's match"), { target: { value: 'Hard mode' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create match' }));

    await screen.findByText('L5Y9FS');
    expect(mockApi.createSession).toHaveBeenCalledWith('Hard mode', 7, 9 * 60, 'HARD');
    expect(mockApi.joinSession).toHaveBeenCalledWith(11, 7, 'alice');
    await waitFor(() => expect(mockApi.getPlayers).toHaveBeenCalledWith(11));
    expect(screen.getByText('Blue (you)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Waiting for opponent/ })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Leave' }));
    expect(screen.getByText(/Start a new match or join one/)).toBeInTheDocument();
  });

  it('shows high scores and returns to identification', async () => {
    mockApi.getHighScores.mockResolvedValue([
      { winnerName: 'Alice', winnerScore: 2200, difficulty: 'HARD', durationSeconds: 900, loserName: 'Bob', forfeit: true },
    ]);
    render(<Lobby onEnter={() => {}} />);

    fireEvent.click(screen.getByRole('button', { name: /Top scorers/ }));

    expect(await screen.findByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('2,200')).toBeInTheDocument();
    expect(screen.getByText('forfeit')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByPlaceholderText('e.g. alice')).toBeInTheDocument();
  });

  it('browses only waiting matches and joins the selected room', async () => {
    render(<Lobby onEnter={() => {}} />);

    await identify('alice');
    fireEvent.click(screen.getByRole('button', { name: 'Or browse open matches' }));

    expect(await screen.findByText('Ops drill')).toBeInTheDocument();
    expect(screen.queryByText('Busy')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Ops drill'));

    await screen.findByText('L5Y9FS');
    expect(mockApi.joinSession).toHaveBeenCalledWith(11, 7, 'alice');
  });

  it('joins by code, enables ready once two players are present, and enters after active countdown', async () => {
    const onEnter = vi.fn();
    mockApi.getSession.mockResolvedValue(activeSession);
    mockApi.getPlayers.mockResolvedValue([
      { ...player, ready: true },
      { ...opponent, ready: true },
    ]);
    render(<Lobby onEnter={onEnter} />);

    await identify('alice');
    vi.useFakeTimers();
    fireEvent.change(screen.getByPlaceholderText('e.g. L5Y9FS'), { target: { value: 'l5y9fs' } });
    expect(screen.getByDisplayValue('L5Y9FS')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Join match' }));

    await flushPromises();
    expect(screen.getAllByText('READY')).toHaveLength(2);
    expect(screen.getByText('3')).toBeInTheDocument();

    for (let i = 0; i < 3; i += 1) {
      await act(async () => { vi.advanceTimersByTime(1000); });
      await flushPromises();
    }
    expect(onEnter).toHaveBeenCalledWith({ user, session: activeSession, playerId: 101 });
  });

  it('surfaces API errors and generic network failures without changing steps', async () => {
    mockApi.login.mockRejectedValueOnce(new ApiError(500, 'Backend rejected login'));
    render(<Lobby onEnter={() => {}} />);

    fireEvent.change(screen.getByPlaceholderText('e.g. alice'), { target: { value: 'alice' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('Backend rejected login')).toBeInTheDocument();

    mockApi.getHighScores.mockRejectedValueOnce(new Error('socket closed'));
    fireEvent.click(screen.getByRole('button', { name: /Top scorers/ }));
    expect(await screen.findByText(/Network error/)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('e.g. alice')).toBeInTheDocument();
  });
});
