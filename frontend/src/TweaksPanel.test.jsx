import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

import { TweaksPanel, TweakSection, TweakColor, TweakRadio, TweakSlider, useTweaks } from './TweaksPanel.jsx';

function TweaksHarness({ defaults }) {
  const [values, setTweak] = useTweaks(defaults);
  return (
    <div>
      <span data-testid="accent">{values.accent}</span>
      <span data-testid="density">{values.density}</span>
      <button onClick={() => setTweak('accent', '#fff')}>Set accent</button>
      <button onClick={() => setTweak({ density: 'compact', speed: 2 })}>Set many</button>
    </div>
  );
}

describe('useTweaks', () => {
  beforeEach(() => localStorage.clear());

  it('merges persisted values, writes scalar changes, and writes object patches', () => {
    localStorage.setItem('oran-tweaks', JSON.stringify({ accent: '#35c6ff' }));
    render(<TweaksHarness defaults={{ accent: '#ffb020', density: 'cozy', speed: 1 }} />);

    expect(screen.getByTestId('accent')).toHaveTextContent('#35c6ff');
    expect(screen.getByTestId('density')).toHaveTextContent('cozy');

    fireEvent.click(screen.getByRole('button', { name: 'Set accent' }));
    expect(JSON.parse(localStorage.getItem('oran-tweaks')).accent).toBe('#fff');

    fireEvent.click(screen.getByRole('button', { name: 'Set many' }));
    const saved = JSON.parse(localStorage.getItem('oran-tweaks'));
    expect(saved.density).toBe('compact');
    expect(saved.speed).toBe(2);
  });

  it('falls back to defaults when persisted JSON is invalid', () => {
    localStorage.setItem('oran-tweaks', '{invalid');
    render(<TweaksHarness defaults={{ accent: '#ffb020', density: 'cozy' }} />);

    expect(screen.getByTestId('accent')).toHaveTextContent('#ffb020');
    expect(screen.getByTestId('density')).toHaveTextContent('cozy');
  });
});

describe('TweaksPanel widgets', () => {
  it('opens from the floating button, closes from messages, and renders children', async () => {
    render(<TweaksPanel title="Operator Tweaks"><TweakSection label="Appearance" /></TweaksPanel>);

    expect(screen.queryByText('Operator Tweaks')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Tweaks'));
    expect(screen.getByText('Operator Tweaks')).toBeInTheDocument();
    expect(screen.getByText('Appearance')).toBeInTheDocument();

    window.postMessage({ type: '__deactivate_edit_mode' }, window.location.origin);
    await waitFor(() => expect(screen.queryByText('Operator Tweaks')).not.toBeInTheDocument());

    window.postMessage({ type: '__activate_edit_mode' }, window.location.origin);
    expect(await screen.findByText('Operator Tweaks')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '✕' }));
    expect(screen.queryByText('Operator Tweaks')).not.toBeInTheDocument();
  });

  it('emits color, radio, and numeric slider changes', () => {
    const color = vi.fn();
    const radio = vi.fn();
    const slider = vi.fn();
    render(
      <div>
        <TweakColor label="Accent" value="#111" options={['#111', '#222']} onChange={color} />
        <TweakRadio label="Density" value="cozy" options={[{ label: 'Compact', value: 'compact' }, 'cozy']} onChange={radio} />
        <TweakSlider label="Speed" value={2} min={1} max={5} unit="x" onChange={slider} />
      </div>
    );

    fireEvent.click(screen.getByLabelText('#222'));
    expect(color).toHaveBeenCalledWith('#222');

    fireEvent.click(screen.getByRole('button', { name: 'Compact' }));
    expect(radio).toHaveBeenCalledWith('compact');

    fireEvent.change(screen.getByRole('slider'), { target: { value: '4' } });
    expect(slider).toHaveBeenCalledWith(4);
    expect(screen.getByText('2x')).toBeInTheDocument();
  });
});
