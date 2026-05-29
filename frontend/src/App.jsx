import React, { useState, useEffect, useReducer } from 'react';
import { GameStore, Selectors as GameSelectors } from './store.js';
import { Icon } from './ui.jsx';
import { Dashboard, NetworkMapPage, Incidents, IncidentDetail, Actions, Scoreboard, Players, Settings } from './screens.jsx';
import { useTweaks, TweaksPanel, TweakSection, TweakColor, TweakRadio, TweakSlider } from './TweaksPanel.jsx';

/* ============================================================
   app.jsx — shell, routing, store subscription, tweaks
   ============================================================ */

// live game state hook (re-renders on store change + 1s clock for "time ago")
function useGame() {
  const [, force] = useReducer((x) => x + 1, 0);
  useEffect(() => {
    const unsub = GameStore.subscribe(force);
    const t = setInterval(force, 1000);
    return () => { unsub(); clearInterval(t); };
  }, []);
  return GameStore.getState();
}

const NAV = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
  { id: 'map', label: 'Network Map', icon: 'map' },
  { id: 'incidents', label: 'Incidents', icon: 'alert' },
  { id: 'actions', label: 'Actions', icon: 'actions' },
  { id: 'scoreboard', label: 'Scoreboard', icon: 'trophy' },
  { id: 'players', label: 'Players', icon: 'users' },
  { id: 'settings', label: 'Settings', icon: 'settings' },
];
const SCREENS = { dashboard: Dashboard, map: NetworkMapPage, incidents: Incidents, incident: IncidentDetail, actions: Actions, scoreboard: Scoreboard, players: Players, settings: Settings };
const CRUMB = { dashboard: 'Dashboard', map: 'Network Map', incidents: 'Incidents', incident: 'Incident', actions: 'Actions', scoreboard: 'Scoreboard', players: 'Players', settings: 'Settings' };

const FONT_PAIRS = {
  command:    { head: "'Chakra Petch', sans-serif", mono: "'JetBrains Mono', monospace" },
  industrial: { head: "'Oxanium', sans-serif",      mono: "'IBM Plex Mono', monospace" },
  mono:       { head: "'Space Mono', monospace",    mono: "'Space Mono', monospace" },
};

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "#ffb020",
  "fontPair": "command",
  "density": "cozy",
  "difficulty": "normal",
  "simSpeed": 1
}/*EDITMODE-END*/;

function openTweaks() { window.postMessage({ type: '__activate_edit_mode' }, '*'); }

function App() {
  const state = useGame();
  const [route, setRoute] = useState({ screen: 'dashboard', params: {} });
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const nav = (screen, params = {}) => setRoute({ screen, params });

  // apply visual tweaks
  useEffect(() => {
    const root = document.documentElement;
    root.style.setProperty('--accent', t.accent);
    const fp = FONT_PAIRS[t.fontPair] || FONT_PAIRS.command;
    root.style.setProperty('--font-head', fp.head);
    root.style.setProperty('--font-mono', fp.mono);
    root.setAttribute('data-density', t.density);
  }, [t.accent, t.fontPair, t.density]);

  // apply game-config tweaks to the store
  useEffect(() => { GameStore.setConfig({ difficulty: t.difficulty, simSpeed: t.simSpeed }); }, [t.difficulty, t.simSpeed]);

  const Screen = SCREENS[route.screen] || Dashboard;
  const activeNav = route.screen === 'incident' ? 'incidents' : route.screen;
  const activeCount = GameSelectors.activeIncidents(state).length;
  const me = state.players.find((p) => p.you);

  return (
    <div className="app">
      {/* sidebar */}
      <aside className="sidebar">
        <div className="side-brand">
          <div className="logo"><i /></div>
          <div className="bt">O-RAN<small>Network Defender</small></div>
        </div>
        <nav className="nav">
          {NAV.map((n) => (
            <div key={n.id} className={'nav-item' + (activeNav === n.id ? ' active' : '')} onClick={() => nav(n.id)}>
              <Icon name={n.icon} size={17} />{n.label}
              {n.id === 'incidents' && activeCount > 0 && <span className="badge">{activeCount}</span>}
            </div>
          ))}
        </nav>
        <div className="side-user" onClick={() => nav('players')}>
          <span className="avatar">{state.you.player[0]}</span>
          <div style={{ flex: 1 }}>
            <div className="u-name">{state.you.player}</div>
            <div className="u-team"><i className="dot-sm" style={{ background: 'var(--good)' }} />Team {state.you.team}</div>
          </div>
          <Icon name="chevRight" size={15} style={{ color: 'var(--text-3)' }} />
        </div>
      </aside>

      {/* main */}
      <div className="main">
        <header className="topbar">
          <div className="crumb">{CRUMB[route.screen]}{route.screen === 'incident' && <span className="dim"> · {route.params.id}</span>}</div>
          <div className="spacer" />
          <div className="chip">GAME <b>{state.game}</b></div>
          <div className="online"><span className="dot" />ONLINE</div>
          <div className="chip" style={{ color: 'var(--accent)', borderColor: 'var(--accent-line)' }}><b>{state.score.toLocaleString()}</b> PTS</div>
          <button className="icon-btn" title="Fullscreen" onClick={() => { document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen?.(); }}><Icon name="expand" size={16} /></button>
        </header>
        <main className="content">
          <Screen key={route.screen + (route.params.id || '')} state={state} store={GameStore} nav={nav} route={route} openTweaks={openTweaks} />
        </main>
      </div>

      {/* tweaks */}
      <TweaksPanel title="Tweaks">
        <TweakSection label="Appearance" />
        <TweakColor label="Accent" value={t.accent}
          options={['#ffb020', '#35c6ff', '#36e07f', '#ff5d8f', '#9d7bff']}
          onChange={(v) => setTweak('accent', v)} />
        <TweakRadio label="Type" value={t.fontPair}
          options={[{ label: 'Command', value: 'command' }, { label: 'Industrial', value: 'industrial' }, { label: 'Mono', value: 'mono' }]}
          onChange={(v) => setTweak('fontPair', v)} />
        <TweakRadio label="Density" value={t.density}
          options={[{ label: 'Compact', value: 'compact' }, { label: 'Cozy', value: 'cozy' }, { label: 'Comfy', value: 'comfy' }]}
          onChange={(v) => setTweak('density', v)} />
        <TweakSection label="Simulation" />
        <TweakRadio label="Difficulty" value={t.difficulty}
          options={[{ label: 'Easy', value: 'easy' }, { label: 'Normal', value: 'normal' }, { label: 'Hard', value: 'hard' }]}
          onChange={(v) => setTweak('difficulty', v)} />
        <TweakSlider label="Sim speed" value={t.simSpeed} min={0} max={3} step={0.5} unit="×"
          onChange={(v) => setTweak('simSpeed', v)} />
      </TweaksPanel>
    </div>
  );
}

export default App;
