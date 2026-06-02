import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

import GameOver from './GameOver.jsx';

const baseState = {
  game: 'ABC123',
  forfeitedBy: null,
  cells: [{ health: 80 }, { health: 60 }],
  players: [
    { id: 1, name: 'Alice', score: 240, resolved: 2, you: true },
    { id: 2, name: 'Bob', score: 120, resolved: 1, you: false },
  ],
  activity: [
    { id: 10, playerId: 1, text: 'Fixed overload', points: 160 },
    { id: 11, playerId: 1, text: 'Wrong reboot', points: -40 },
    { id: 12, playerId: 2, text: 'Opponent action', points: 20 },
  ],
};

describe('GameOver', () => {
  it('shows a win summary, ranked players, decision analysis, and exits', () => {
    const onExit = vi.fn();
    render(<GameOver state={baseState} onExit={onExit} />);

    expect(screen.getByText('YOU WIN')).toBeInTheDocument();
    expect(screen.getByText('GAME ABC123')).toBeInTheDocument();
    expect(screen.getByText(/Alice/)).toBeInTheDocument();
    expect(screen.getByText('WINNER')).toBeInTheDocument();
    expect(screen.getByText('70%')).toBeInTheDocument();
    expect(screen.getByText(/Fixed overload/)).toBeInTheDocument();
    expect(screen.getByText(/Wrong reboot/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Return to lobby' }));
    expect(onExit).toHaveBeenCalledTimes(1);
  });

  it('treats equal top scores as a draw', () => {
    render(<GameOver state={{ ...baseState, players: baseState.players.map((p) => ({ ...p, score: 100 })) }} onExit={() => {}} />);

    expect(screen.getByText('DRAW')).toBeInTheDocument();
    expect(screen.queryByText('WINNER')).not.toBeInTheDocument();
  });

  it('awards the match when the opponent forfeits regardless of score', () => {
    render(<GameOver state={{ ...baseState, forfeitedBy: 2, players: [
      { id: 1, name: 'Alice', score: 10, resolved: 0, you: true },
      { id: 2, name: 'Bob', score: 999, resolved: 8, you: false },
    ] }} onExit={() => {}} />);

    expect(screen.getByText('YOU WIN')).toBeInTheDocument();
    expect(screen.getByText('Your opponent forfeited the match.')).toBeInTheDocument();
    expect(screen.getByText('WINNER')).toBeInTheDocument();
  });

  it('shows a loss when the current player forfeited and handles no personal activity', () => {
    render(<GameOver state={{ ...baseState, forfeitedBy: 1, activity: [] }} onExit={() => {}} />);

    expect(screen.getByText('YOU LOSE')).toBeInTheDocument();
    expect(screen.getByText('You forfeited the match.')).toBeInTheDocument();
    expect(screen.getAllByText('—')).toHaveLength(2);
  });
});
