import React from 'react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

vi.mock('./NetworkMap3D.jsx', () => ({
  NetworkMap: ({ onSelect }) => <button onClick={() => onSelect?.('Cell-A')}>Mock map</button>,
}));

import { Icon, MapLegend, NetworkMap, SevTag, StatusTag, Topology, timeAgo } from './ui.jsx';

describe('ui primitives', () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date('2026-06-02T12:00:00Z') }));
  afterEach(() => vi.useRealTimers());

  it('formats relative time across seconds, minutes, and hours', () => {
    const now = Date.now();
    expect(timeAgo(now - 12_000)).toBe('12s ago');
    expect(timeAgo(now - 12 * 60_000)).toBe('12m ago');
    expect(timeAgo(now - 3 * 60 * 60_000)).toBe('3h ago');
  });

  it('renders icons, tags, map exports, and the map legend', () => {
    const onSelect = vi.fn();
    const { container } = render(
      <div>
        <Icon name="dashboard" size={18} data-testid="known" />
        <Icon name="missing" data-testid="missing" />
        <SevTag sev="high" />
        <SevTag sev="unknown" />
        <StatusTag status="resolved" />
        <StatusTag status="stale" />
        <MapLegend />
        <NetworkMap onSelect={onSelect} />
      </div>
    );

    expect(container.querySelector('[data-testid="known"] rect')).toBeTruthy();
    expect(container.querySelector('[data-testid="missing"]')).toBeTruthy();
    expect(screen.getByText('high')).toHaveClass('crit');
    expect(screen.getByText('unknown')).toHaveClass('muted');
    expect(screen.getByText('resolved')).toHaveClass('good');
    expect(screen.getByText('stale')).toHaveClass('muted');
    expect(screen.getByText(/Good 80/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Mock map' }));
    expect(onSelect).toHaveBeenCalledWith('Cell-A');
  });

  it('renders O-RAN topology and explains selected components', () => {
    render(<Topology cells={[{ health: 90 }, { health: 30 }, { health: 70 }, { health: 50 }]} height={320} />);

    expect(screen.getByText(/Click a component/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('SMO'));
    expect(screen.getByText(/Service Management/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('Near-RT RIC'));
    expect(screen.getByText(/fast \(10ms/)).toBeInTheDocument();
  });
});
