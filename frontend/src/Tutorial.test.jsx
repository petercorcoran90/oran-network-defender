import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Tutorial from './Tutorial.jsx';

describe('Tutorial', () => {
  it('opens on lesson 1 of 8 and prompts the first command', () => {
    render(<Tutorial onExit={() => {}} />);
    expect(screen.getByText(/lesson 1 of 8/)).toBeInTheDocument();
    expect(screen.getByText('Cell overload')).toBeInTheDocument();
    expect(screen.getByText(/Type help/)).toBeInTheDocument();
  });

  it('rejects a wrong command, then advances when the right one is typed', () => {
    render(<Tutorial onExit={() => {}} />);
    const input = screen.getByLabelText('tutorial console');

    fireEvent.change(input, { target: { value: 'wat' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(screen.getByText(/not quite/)).toBeInTheDocument();

    fireEvent.change(input, { target: { value: 'help' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    // advanced to the next step, which teaches the manual command
    expect(screen.getByText(/Type man kubectl logs/)).toBeInTheDocument();
  });

  it('"Show me" advances a step even if you do not know the command', () => {
    render(<Tutorial onExit={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: /Show me/ }));
    expect(screen.getByText(/Type man kubectl logs/)).toBeInTheDocument();
  });

  it('Exit calls onExit', () => {
    const onExit = vi.fn();
    render(<Tutorial onExit={onExit} />);
    fireEvent.click(screen.getByRole('button', { name: 'Exit' }));
    expect(onExit).toHaveBeenCalledTimes(1);
  });
});
