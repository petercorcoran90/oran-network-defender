import React from 'react';
import { Selectors as GameSelectors, statusOf } from './store.js';
import { Icon, timeAgo, SevTag, StatusTag, NetworkMap, MapLegend, Topology, STATUS_COLOR } from './ui.jsx';

/* ============================================================
   screens.jsx — all 7 screens for O-RAN Network Defender
   Each screen is a component taking { state, store, nav, route }.
   ============================================================ */
const SEV_COLOR = { high: 'var(--crit)', medium: 'var(--warn)', low: 'var(--info)' };
const FEED_STYLE = {
  apply:   { kind: 'good', icon: 'check' },
  ack:     { kind: 'info', icon: 'bell' },
  incident:{ kind: 'crit', icon: 'alert' },
  resolve: { kind: 'good', icon: 'check' },
};

// ---------- reusable widgets ----------
function StatCard({ lbl, big, sub, sublink, accent, bar, barColor, onClick }) {
  return (
    <div className="stat" style={{ '--accent': accent || 'var(--accent)' }}>
      <div className="lbl">{lbl}</div>
      <div className="big">{big}</div>
      {bar != null && <div className="bar"><i style={{ width: bar + '%', background: barColor }} /></div>}
      {sub && <div className={'sub' + (sublink ? ' link' : '')} onClick={onClick} style={sublink ? { cursor: 'pointer' } : null}>{sub}</div>}
    </div>
  );
}

function ActiveIncidents({ state, nav, limit }) {
  const list = GameSelectors.activeIncidents(state).slice(0, limit || 99);
  return (
    <div className="grid" style={{ gap: '9px' }}>
      {list.length === 0 && <div className="empty">No active incidents. Network nominal.</div>}
      {list.map((inc) => (
        <div key={inc.id} className="inc-mini" style={{ '--sev': SEV_COLOR[inc.severity] }} onClick={() => nav('incident', { id: inc.id })}>
          <div className="inc-hdr">
            <span className="t">{inc.title}</span>
            <SevTag sev={inc.severity} />
          </div>
          <div className="m"><span>{inc.cellId} · {inc.id}</span><span>{timeAgo(inc.detectedAt)}</span></div>
        </div>
      ))}
    </div>
  );
}

function ActivityFeed({ state, limit }) {
  return (
    <div>
      {state.activity.slice(0, limit || 99).map((a) => {
        const f = FEED_STYLE[a.kind] || FEED_STYLE.incident;
        return (
          <div key={a.id} className="feed-row">
            <span className="when">{timeAgo(a.when)}</span>
            <span className="feed-icn" style={{ background: 'color-mix(in oklab,' + (f.kind === 'crit' ? 'var(--crit)' : f.kind === 'info' ? 'var(--info)' : 'var(--good)') + ' 16%, transparent)', color: f.kind === 'crit' ? 'var(--crit)' : f.kind === 'info' ? 'var(--info)' : 'var(--good)' }}>
              <Icon name={f.icon} size={13} />
            </span>
            <span className="body">{a.text}</span>
            {a.points !== 0 && <span className={'pts ' + (a.points > 0 ? 'pos' : 'neg')}>{a.points > 0 ? '+' : ''}{a.points}</span>}
          </div>
        );
      })}
    </div>
  );
}

// ============================================================
// 1 · DASHBOARD
// ============================================================
function Dashboard({ state, nav }) {
  const health = GameSelectors.networkHealth(state);
  const hs = statusOf(health);
  const active = GameSelectors.activeIncidents(state);
  const rank = GameSelectors.teamRank(state);
  const teams = [...state.teams].sort((a, b) => b.score - a.score);
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Operations Dashboard</h1><div className="sub">Live status of your O-RAN deployment · Game {state.game}</div></div></div>

      <div className="grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 'var(--gap)' }}>
        <StatCard lbl="Network Health" big={health + '%'} bar={health} barColor={STATUS_COLOR[hs]} accent={STATUS_COLOR[hs]} sub={hs === 'good' ? 'Nominal' : hs === 'warn' ? 'Degraded' : 'Critical'} />
        <StatCard lbl="Active Incidents" big={active.length} sub="View all →" sublink onClick={() => nav('incidents')} />
        <StatCard lbl="Total Score" big={state.score.toLocaleString()} sub={'Rank #' + rank} sublink onClick={() => nav('scoreboard')} />
        <StatCard lbl="Team" big={state.you.team} sub={state.players.filter((p) => p.team === state.you.team).length + ' members'} />
      </div>

      <div className="grid" style={{ gridTemplateColumns: '1fr 320px', marginBottom: 'var(--gap)' }}>
        <div className="panel">
          <div className="panel-head"><h2>Network Map</h2><span className="link" onClick={() => nav('map')}>Open full map →</span></div>
          <div className="panel-pad">
            <NetworkMap cells={state.cells} links={state.links} onSelect={(id) => { const inc = active.find((i) => i.cellId === id); inc ? nav('incident', { id: inc.id }) : nav('map', { cell: id }); }} height={420} />
            <div style={{ marginTop: 16 }}><MapLegend /></div>
          </div>
        </div>
        <div className="panel">
          <div className="panel-head"><h2>Active Incidents</h2><span className="link" onClick={() => nav('incidents')}>View all</span></div>
          <div className="panel-pad"><ActiveIncidents state={state} nav={nav} limit={6} /></div>
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <div className="panel">
          <div className="panel-head"><h2>Scoreboard</h2><span className="link" onClick={() => nav('scoreboard')}>Full board</span></div>
          <table>
            <thead><tr><th style={{ width: 40 }}>#</th><th>Team</th><th>Score</th><th>Health</th></tr></thead>
            <tbody>
              {teams.map((tm, i) => (
                <tr key={tm.id} className={tm.you ? 'me' : ''} onClick={() => nav('scoreboard')}>
                  <td className="rank">{i + 1}</td>
                  <td>{tm.name}{tm.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</td>
                  <td className="mono">{tm.score.toLocaleString()}</td>
                  <td className="mono">{tm.health}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="panel">
          <div className="panel-head"><h2>Live Activity Feed</h2><span className="corner">realtime</span></div>
          <div className="panel-pad" style={{ paddingTop: 4, paddingBottom: 4 }}><ActivityFeed state={state} limit={6} /></div>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// 2 · NETWORK MAP (full page, tabs: Cell Map / Topology)
// ============================================================
function NetworkMapPage({ state, nav, route, store }) {
  const [tab, setTab] = React.useState('cells');
  const [sel, setSel] = React.useState(route.params.cell || null);
  const selCell = state.cells.find((c) => c.id === sel);
  const selIncidents = GameSelectors.activeIncidents(state).filter((i) => i.cellId === sel);
  return (
    <div className="fade-in">
      <div className="page-head">
        <div><h1>Network Map</h1><div className="sub">Radio access topology · {state.cells.length} cells monitored</div></div>
        <div className="seg">
          <button className={tab === 'cells' ? 'on' : ''} onClick={() => setTab('cells')}>Cell Map</button>
          <button className={tab === 'topo' ? 'on' : ''} onClick={() => setTab('topo')}>Topology</button>
        </div>
      </div>

      {tab === 'cells' ? (
        <div className="grid" style={{ gridTemplateColumns: '1fr 300px' }}>
          <div className="panel">
            <div className="panel-head"><h2>Cell Coverage</h2><MapLegend /></div>
            <div className="panel-pad"><NetworkMap cells={state.cells} links={state.links} selectedId={sel} onSelect={setSel} height={520} /></div>
          </div>
          <div className="panel">
            <div className="panel-head"><h2>{selCell ? selCell.id : 'Cell Inspector'}</h2></div>
            <div className="panel-pad">
              {!selCell ? <div className="empty">Select a cell on the map to inspect it.</div> : (() => {
                const st = statusOf(selCell.health);
                return (<div className="fade-in">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
                    <span className="dot-sm" style={{ width: 12, height: 12, background: STATUS_COLOR[st], boxShadow: '0 0 8px ' + STATUS_COLOR[st] }} />
                    <span style={{ fontFamily: 'var(--font-head)', fontSize: 22, color: STATUS_COLOR[st] }}>{selCell.health}%</span>
                    <span className="tag" style={{ color: STATUS_COLOR[st], borderColor: 'transparent', background: 'color-mix(in oklab,' + STATUS_COLOR[st] + ' 13%,transparent)' }}>{st}</span>
                  </div>
                  <div className="kv"><span className="k">Signal quality</span><span className="v mono">{Math.round(selCell.signalQuality)}%</span></div>
                  <div className="kv"><span className="k">User load</span><span className="v mono">{Math.round(selCell.userLoad)}%</span></div>
                  <div className="kv"><span className="k">Latency</span><span className="v mono">{Math.round(selCell.latency)} ms</span></div>
                  <div className="kv"><span className="k">Packet loss</span><span className="v mono">{Math.round(selCell.packetLoss)}%</span></div>
                  <div className="kv"><span className="k">Config status</span><span className="v mono" style={{ color: selCell.configStatus && selCell.configStatus !== 'STABLE' ? 'var(--warn)' : 'inherit' }}>{selCell.configStatus || 'STABLE'}</span></div>
                  <div className="kv"><span className="k">Active incidents</span><span className="v mono">{selIncidents.length}</span></div>
                  {selIncidents.length > 0 && <div style={{ marginTop: 14 }}>
                    <div className="lbl" style={{ fontSize: 10.5, color: 'var(--text-3)', letterSpacing: '.08em', textTransform: 'uppercase', marginBottom: 8 }}>Open on this cell</div>
                    <ActiveIncidents state={{ ...state, incidents: selIncidents }} nav={nav} />
                  </div>}
                </div>);
              })()}
            </div>
          </div>
        </div>
      ) : (
        <div className="panel">
          <div className="panel-head"><h2>O-RAN Topology — Detailed View</h2><span className="corner">SMO · RIC · O-CU · O-DU · O-RU · 5GC</span></div>
          <div className="panel-pad">
            <Topology cells={state.cells} height={400} />
            <div style={{ marginTop: 18, borderTop: '1px solid var(--hair)', paddingTop: 14 }}><MapLegend /></div>
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================================
// 3 · INCIDENTS LIST
// ============================================================
function Incidents({ state, nav }) {
  const [filter, setFilter] = React.useState('all');
  const [q, setQ] = React.useState('');
  const [page, setPage] = React.useState(1);
  const per = 8;
  let rows = state.incidents.filter((i) => filter === 'all' || i.severity === filter);
  if (q) rows = rows.filter((i) => (i.title + i.cellId + i.id).toLowerCase().includes(q.toLowerCase()));
  const pages = Math.max(1, Math.ceil(rows.length / per));
  const cur = Math.min(page, pages);
  const view = rows.slice((cur - 1) * per, cur * per);
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Incidents</h1><div className="sub">{rows.length} incidents · {GameSelectors.activeIncidents(state).length} active</div></div></div>
      <div className="panel">
        <div className="panel-head" style={{ gap: 12, flexWrap: 'wrap' }}>
          <div className="seg">
            {['all', 'high', 'medium', 'low'].map((f) => <button key={f} className={filter === f ? 'on' : ''} onClick={() => { setFilter(f); setPage(1); }}>{f}</button>)}
          </div>
          <div style={{ display: 'flex', gap: 10, marginLeft: 'auto', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', padding: '7px 11px' }}>
              <Icon name="search" size={14} style={{ color: 'var(--text-3)' }} />
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search…" style={{ background: 'none', border: 'none', color: 'var(--text)', outline: 'none', fontFamily: 'var(--font-mono)', fontSize: 12, width: 130 }} />
            </div>
          </div>
        </div>
        <table>
          <thead><tr><th>ID</th><th>Incident</th><th>Cell</th><th>Severity</th><th>Status</th><th>Time</th></tr></thead>
          <tbody>
            {view.map((inc) => (
              <tr key={inc.id} onClick={() => nav('incident', { id: inc.id })}>
                <td className="id">#{inc.id}</td>
                <td style={{ fontWeight: 600 }}>{inc.title}</td>
                <td className="mono">{inc.cellId}</td>
                <td><SevTag sev={inc.severity} /></td>
                <td><StatusTag status={inc.status} /></td>
                <td className="id">{timeAgo(inc.detectedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {view.length === 0 && <div className="empty">No incidents match this filter.</div>}
        <div className="pager">
          <span className="corner" style={{ color: 'var(--text-3)' }}>Showing {view.length ? (cur - 1) * per + 1 : 0}–{(cur - 1) * per + view.length} of {rows.length}</span>
          <div className="pages">
            <button onClick={() => setPage(Math.max(1, cur - 1))} disabled={cur === 1}><Icon name="chevL" size={14} /></button>
            {Array.from({ length: pages }).map((_, i) => <button key={i} className={cur === i + 1 ? 'on' : ''} onClick={() => setPage(i + 1)}>{i + 1}</button>)}
            <button onClick={() => setPage(Math.min(pages, cur + 1))} disabled={cur === pages}><Icon name="chevR" size={14} /></button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// 4 · INCIDENT DETAIL & ACTIONS
// ============================================================
function metricRow(label, value, unit, max, invert) {
  const fill = Math.min(1, value / max);            // bar length reflects the value
  const bad = invert ? 1 - fill : fill;             // "goodness" can be inverted (signal: high = good)
  const col = bad > 0.75 ? 'var(--crit)' : bad > 0.5 ? 'var(--warn)' : 'var(--good)';
  return (
    <div className="metric" key={label}>
      <span className="ml">{label}</span>
      <span className="mbar"><i style={{ width: fill * 100 + '%', background: col }} /></span>
      <span className="mv" style={{ color: col }}>{value}{unit}</span>
    </div>
  );
}

function IncidentDetail({ state, store, nav, route }) {
  const inc = state.incidents.find((i) => i.id === route.params.id);
  // Hooks must run on every render in the same order (rules of hooks), so they are all declared
  // up front — never after the "incident not found" early-return guard, which lives below them.
  const [flash, setFlash] = React.useState(null);
  const [outcome, setOutcome] = React.useState(null);
  const [lesson, setLesson] = React.useState(null);
  const [evidence, setEvidence] = React.useState([]);
  const [running, setRunning] = React.useState(null);
  const [lines, setLines] = React.useState([]);
  const [cmd, setCmd] = React.useState('');
  const [history, setHistory] = React.useState([]);
  const histRef = React.useRef(-1);
  const termRef = React.useRef(null);
  React.useEffect(() => {
    if (!inc) return undefined;
    let active = true;
    setEvidence([]);
    setLines([{ t: "Diagnostic console — type 'help' to list commands." }]);
    store.getDiagnostics(inc.id).then((list) => { if (active) setEvidence(list || []); }).catch(() => {});
    return () => { active = false; };
  }, [inc?.id]);
  React.useEffect(() => {
    if (termRef.current) termRef.current.scrollTop = termRef.current.scrollHeight;
  }, [lines]);

  if (!inc) return <div className="empty">Incident not found. <span className="link" onClick={() => nav('incidents')}>Back to list</span></div>;
  const cell = state.cells.find((c) => c.id === inc.cellId);
  const closed = inc.status !== 'open'; // resolved OR failed — no more actions either way
  const failed = inc.status === 'failed';
  const catalog = Object.keys(store.ACTIONS); // the real 9-action catalog from the backend
  const learnedActions = new Set(state.learnedActions || []);
  async function investigate(name) {
    setRunning(name);
    const ev = await store.runDiagnostic(inc.id, name);
    if (ev) setEvidence((prev) => (prev.some((e) => e.diagnostic === ev.diagnostic) ? prev : [...prev, ev]));
    setRunning(null);
  }
  async function onConsoleKey(e) {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (history.length) { histRef.current = Math.max(0, (histRef.current < 0 ? history.length : histRef.current) - 1); setCmd(history[histRef.current] || ''); }
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (histRef.current >= 0) { histRef.current = Math.min(history.length, histRef.current + 1); setCmd(history[histRef.current] || ''); }
      return;
    }
    if (e.key !== 'Enter') return;
    const c = cmd.trim();
    if (!c) return;
    setCmd(''); histRef.current = -1;
    setHistory((h) => [...h, c]);
    setLines((l) => [...l, { t: '$ ' + c, you: true }]);
    if (c.toLowerCase() === 'clear') { setLines([]); return; }
    const res = await store.runConsole(inc.id, c);
    if (res && res.output) setLines((l) => [...l, { t: res.output }]);
    store.getDiagnostics(inc.id).then((list) => setEvidence(list || [])).catch(() => {});
  }
  const ranNames = new Set(evidence.map((e) => e.diagnostic));
  // Deduction board: candidates, narrowed by the (budget-limited) evidence gathered so far.
  const candidates = inc.candidates || [];
  const confirmedEv = evidence.find((e) => e.result === 'CONFIRMS');
  const eliminated = new Set(evidence.filter((e) => e.result === 'RULES_OUT').map((e) => e.implicated));
  const actByName = Object.fromEntries(Object.values(store.ACTIONS).map((a) => [a.actionName, a]));
  const budget = inc.diagnosticBudget || 0;
  const budgetUsed = evidence.length >= budget;
  async function apply(aid) {
    setFlash(aid);
    const res = await store.applyAction(inc.id, aid);
    if (res) {
      setOutcome(res);
      if (res.justLearned) setLesson(res); // first time using this action -> teach the CLI
    }
  }
  const OUTCOME = {
    SUCCESS: { cls: 'good', icon: 'check', text: 'Correct fix — incident resolved.' },
    FAILED: { cls: 'crit', icon: 'x', text: 'Wrong action — it backfired, the incident failed and the fault spread.' },
    PARTIAL: { cls: 'warn', icon: 'alert', text: 'No effect — that action does not fix this incident. Try another.' },
  };
  return (
    <div className="fade-in">
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, color: 'var(--text-3)', fontSize: 12 }}>
        <span className="link" onClick={() => nav('incidents')} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><Icon name="back" size={14} /> Incidents</span>
        <Icon name="chevRight" size={12} /><span style={{ color: 'var(--text)' }}>{inc.id}</span>
      </div>

      {outcome && (() => {
        const o = OUTCOME[outcome.result] || OUTCOME.PARTIAL;
        const c = o.cls === 'good' ? 'var(--good)' : o.cls === 'crit' ? 'var(--crit)' : 'var(--warn)';
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 'var(--gap)', padding: '11px 14px',
            borderRadius: 'var(--r)', border: '1px solid ' + c, background: 'color-mix(in oklab,' + c + ' 12%, transparent)', color: c, fontSize: 13 }}>
            <Icon name={o.icon} size={16} />
            <span style={{ flex: 1, color: 'var(--text)' }}>{o.text}</span>
            <b style={{ color: c }}>{outcome.pointsAwarded > 0 ? '+' : ''}{outcome.pointsAwarded} pts</b>
          </div>
        );
      })()}

      <div className="panel" style={{ marginBottom: 'var(--gap)', borderLeft: '3px solid ' + SEV_COLOR[inc.severity] }}>
        <div className="panel-pad" style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <span className="action-ic" style={{ width: 44, height: 44, color: SEV_COLOR[inc.severity], borderColor: 'color-mix(in oklab,' + SEV_COLOR[inc.severity] + ' 30%,transparent)' }}><Icon name="alert" size={22} /></span>
          <div style={{ flex: 1 }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: 20, fontWeight: 600 }}>{inc.title}</div>
            <div style={{ display: 'flex', gap: 8, marginTop: 6 }}><SevTag sev={inc.severity} /><StatusTag status={inc.status} /><span className="id" style={{ alignSelf: 'center' }}>{inc.id}</span></div>
          </div>
          {inc.status === 'resolved' && <span className="tag good" style={{ fontSize: 12, padding: '6px 12px' }}><Icon name="check" size={13} /> Resolved</span>}
          {failed && <span className="tag crit" style={{ fontSize: 12, padding: '6px 12px' }}><Icon name="x" size={13} /> Failed — wrong action</span>}
        </div>
      </div>

      {(inc.diagnostics && inc.diagnostics.length > 0) && (
        <div className="panel" style={{ marginBottom: 'var(--gap)' }}>
          <div className="panel-head">
            <h2>Investigation</h2>
            <span className="corner">{inc.symptomGroup || 'Symptom'} · {evidence.length}/{budget} tests used</span>
          </div>
          <div className="panel-pad">
            <div style={{ color: 'var(--text-3)', fontSize: 12.5, marginBottom: 12 }}>
              You can run only {budget} test{budget === 1 ? '' : 's'} here, and each costs points — so you can't
              check everything. Rule out what you can, then decide which cause it is and apply its fix.
            </div>

            {/* Deduction board: each possible cause + its fix, narrowed by the evidence so far. */}
            <div style={{ marginBottom: 12 }}>
              {candidates.map((c) => {
                const confirmed = confirmedEv && c.cause === confirmedEv.implicated;
                const out = !confirmedEv && eliminated.has(c.cause);
                return (
                  <div key={c.cause} className="kv" style={{ alignItems: 'center', gap: 8, opacity: out ? 0.5 : 1 }}>
                    <span className={'tag ' + (confirmed ? 'good' : out ? 'muted' : 'warn')} style={{ minWidth: 72, textAlign: 'center' }}>
                      {confirmed ? 'confirmed' : out ? 'ruled out' : 'possible'}
                    </span>
                    <span style={{ textDecoration: out ? 'line-through' : 'none', color: 'var(--text-2)', fontSize: 13 }}>
                      {c.label} <span style={{ color: 'var(--text-3)' }}>→ fix: {actByName[c.action]?.name || c.action}</span>
                    </span>
                  </div>
                );
              })}
            </div>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: evidence.length ? 12 : 0 }}>
              {inc.diagnostics.map((d) => (
                <button key={d.name} className="btn"
                  disabled={closed || ranNames.has(d.name) || running === d.name || (budgetUsed && !ranNames.has(d.name))}
                  onClick={() => investigate(d.name)}>
                  <Icon name="search" size={13} /> {d.label}{ranNames.has(d.name) ? ' ✓' : ''}
                </button>
              ))}
            </div>
            {budgetUsed && !closed && (
              <div style={{ color: 'var(--text-3)', fontSize: 12, marginBottom: 12 }}>
                No tests left — make your call from the evidence above.
              </div>
            )}

            {/* Console — type authentic commands and read the output (buttons above are the easy mode). */}
            <div className="k" style={{ color: 'var(--text-3)', fontSize: 10.5, letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: 6 }}>Console</div>
            <div ref={termRef} style={{ background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', padding: '10px 12px', fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-2)', maxHeight: 180, overflowY: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
              {lines.map((ln, idx) => (
                <div key={idx} style={{ color: ln.you ? 'var(--accent)' : 'var(--text-2)' }}>{ln.t}</div>
              ))}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', padding: '7px 11px' }}>
              <span style={{ color: 'var(--text-3)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>$</span>
              <input value={cmd} disabled={closed} onChange={(e) => setCmd(e.target.value)} onKeyDown={onConsoleKey}
                placeholder="type a command — e.g. traceroute o-ru   (try 'help')"
                aria-label="diagnostic console"
                style={{ flex: 1, background: 'none', border: 'none', color: 'var(--text)', outline: 'none', fontFamily: 'var(--font-mono)', fontSize: 12 }} />
            </div>
            <div style={{ height: 12 }} />

            {evidence.map((e) => (
              <div key={e.diagnostic} className="kv" style={{ alignItems: 'center', gap: 8 }}>
                <span className={'tag ' + (e.result === 'CONFIRMS' ? 'good' : 'muted')}>
                  {e.result === 'CONFIRMS' ? 'confirms' : 'rules out'}
                </span>
                <span className="v" style={{ color: 'var(--text-2)', fontSize: 12.5 }}>{e.finding}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="detail-grid">
        <div className="panel">
          <div className="panel-head"><h2>Incident Detail</h2></div>
          <div className="panel-pad">
            <div className="kv"><span className="k">Cell</span><span className="v mono">{inc.cellId}{cell && ' · ' + cell.health + '%'}</span></div>
            <div className="kv"><span className="k">Detected</span><span className="v">{timeAgo(inc.detectedAt)}</span></div>
            <div className="kv"><span className="k">Status</span><span className="v"><StatusTag status={inc.status} /></span></div>
            <div className="kv" style={{ flexDirection: 'column', alignItems: 'flex-start', gap: 6 }}>
              <span className="k">Description</span>
              <span style={{ color: 'var(--text-2)', fontSize: 12.5, lineHeight: 1.6 }}>{inc.description}</span>
            </div>
            <div style={{ marginTop: 14 }}>
              <div className="k" style={{ color: 'var(--text-3)', fontSize: 10.5, letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: 6 }}>Live Metrics</div>
              {metricRow('Signal Quality', inc.metrics.signalQuality, '%', 100, true)}
              {metricRow('User Load', inc.metrics.userLoad, '%', 100)}
              {metricRow('Latency', inc.metrics.latency, 'ms', 200)}
              {metricRow('Packet Loss', inc.metrics.packetLoss, '%', 40)}
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-head"><h2>Available Actions</h2><span className="corner">{closed ? 'incident closed' : 'choose remediation'}</span></div>
          <div>
            {catalog.map((aid) => {
              const a = store.ACTIONS[aid];
              const learned = learnedActions.has(a.actionName); // learned -> button retires, use the console
              return (
                <div key={aid} className={'action-row' + (flash === aid ? ' flash' : '')}>
                  <span className="action-ic"><Icon name={a.icon} size={17} /></span>
                  <div style={{ flex: 1 }}>
                    <div className="at">{a.name}{learned && <span className="pts-pill">✓ learned</span>}</div>
                    <div className="ad">{learned ? 'You know this one — apply it from the console.' : a.desc}</div>
                  </div>
                  <button className="btn" disabled={closed || learned} onClick={() => apply(aid)}>Apply</button>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {lesson && (
        <div onClick={() => setLesson(null)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.55)', display: 'grid', placeItems: 'center', zIndex: 50 }}>
          <div onClick={(e) => e.stopPropagation()} className="panel panel-pad" style={{ maxWidth: 520, margin: 16 }}>
            <h2 style={{ marginBottom: 8 }}>Command learned</h2>
            <div style={{ color: 'var(--text-3)', fontSize: 13, marginBottom: 14 }}>
              From now on you apply this fix in the console. Here's how an engineer does it:
            </div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, marginBottom: 12 }}>
              <div style={{ color: 'var(--text-3)', fontSize: 10.5, letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: 4 }}>Fix</div>
              <div style={{ color: 'var(--accent)' }}>$ {lesson.actionCommand}</div>
            </div>
            {lesson.diagnoseCommands && lesson.diagnoseCommands.length > 0 && (
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, marginBottom: 16 }}>
                <div style={{ color: 'var(--text-3)', fontSize: 10.5, letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: 4 }}>Diagnose</div>
                {lesson.diagnoseCommands.map((c, i) => (
                  <div key={i} style={{ color: 'var(--text-2)' }}>$ {c}</div>
                ))}
              </div>
            )}
            <button className="btn primary" onClick={() => setLesson(null)}>Got it</button>
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================================
// 5 · ACTIONS (remediation catalog + applied log)
// ============================================================
// The field manual — fills in with the commands the player has learned (server-filtered).
function Actions({ state, store }) {
  const [manual, setManual] = React.useState(null);
  React.useEffect(() => {
    let active = true;
    store.getManual().then((m) => { if (active) setManual(m); }).catch(() => {});
    return () => { active = false; };
  }, [state.version]); // re-fetch as the player learns more during the match
  const applied = state.activity.filter((a) => a.kind === 'apply');
  const diags = manual ? manual.diagnostics : [];
  const acts = manual ? manual.actions : [];
  const learned = diags.length + acts.length;
  const total = manual ? manual.diagnosticsTotal + manual.actionsTotal : 0;
  return (
    <div className="fade-in">
      <div className="page-head">
        <div><h1>Field Manual</h1><div className="sub">Commands you've learned{manual ? ` · ${manual.tier}` : ''}</div></div>
        {manual && <div className="seg"><button className="on">{learned}/{total} learned</button></div>}
      </div>

      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <div className="panel">
          <div className="panel-head"><h2>Diagnostics</h2><span className="corner">{diags.length}/{manual ? manual.diagnosticsTotal : '–'}</span></div>
          <div className="panel-pad">
            {diags.length === 0 ? <div className="empty">Run diagnostics on incidents to learn them.</div> :
              diags.map((d) => (
                <div key={d.name} style={{ marginBottom: 12 }}>
                  <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', fontSize: 12.5 }}>$ {d.command}</div>
                  <div style={{ color: 'var(--text-3)', fontSize: 12 }}>{d.investigates}</div>
                </div>
              ))}
          </div>
        </div>
        <div className="panel">
          <div className="panel-head"><h2>Fixes</h2><span className="corner">{acts.length}/{manual ? manual.actionsTotal : '–'}</span></div>
          <div className="panel-pad">
            {acts.length === 0 ? <div className="empty">Apply remediations to learn their commands.</div> :
              acts.map((a) => (
                <div key={a.name} style={{ marginBottom: 12 }}>
                  <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 13 }}>{a.label}</div>
                  <div style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', fontSize: 12.5 }}>$ {a.command}</div>
                </div>
              ))}
          </div>
        </div>
      </div>

      <div className="panel" style={{ marginTop: 'var(--gap)' }}>
        <div className="panel-head"><h2>Recently Applied</h2></div>
        <div className="panel-pad" style={{ paddingTop: 4, paddingBottom: 4 }}>
          {applied.length === 0 ? <div className="empty">No actions applied yet.</div> :
            applied.slice(0, 8).map((a) => (
              <div key={a.id} className="feed-row">
                <span className="when">{timeAgo(a.when)}</span>
                <span className="feed-icn" style={{ background: 'color-mix(in oklab,var(--good) 16%,transparent)', color: 'var(--good)' }}><Icon name="check" size={13} /></span>
                <span className="body">{a.text}</span>
                <span className="pts pos">+{a.points}</span>
              </div>
            ))}
        </div>
      </div>
    </div>
  );
}

// ============================================================
// 6 · SCOREBOARD
// ============================================================
function Scoreboard({ state, nav }) {
  const [tab, setTab] = React.useState('teams');
  const teams = [...state.teams].sort((a, b) => b.score - a.score);
  const players = [...state.players].sort((a, b) => b.score - a.score);
  return (
    <div className="fade-in">
      <div className="page-head">
        <div><h1>Scoreboard</h1><div className="sub">Game {state.game} · live ranking</div></div>
        <div className="seg">
          <button className={tab === 'global' ? 'on' : ''} onClick={() => setTab('global')}>Global</button>
          <button className={tab === 'teams' ? 'on' : ''} onClick={() => setTab('teams')}>Teams</button>
        </div>
      </div>
      <div className="panel">
        {tab === 'teams' ? (
          <table>
            <thead><tr><th style={{ width: 50 }}>Rank</th><th>Team</th><th>Score</th><th>Resolved</th></tr></thead>
            <tbody>
              {teams.map((tm, i) => (
                <tr key={tm.id} className={tm.you ? 'me' : ''}>
                  <td className="rank" style={{ color: i === 0 ? 'var(--accent)' : 'inherit' }}>{i + 1}</td>
                  <td style={{ fontWeight: 600 }}>{tm.name}{tm.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</td>
                  <td className="mono" style={{ fontWeight: 600 }}>{tm.score.toLocaleString()}</td>
                  <td className="mono">{tm.resolved}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table>
            <thead><tr><th style={{ width: 50 }}>Rank</th><th>Player</th><th>Team</th><th>Score</th><th>Resolved</th></tr></thead>
            <tbody>
              {players.map((p, i) => (
                <tr key={p.id} className={p.you ? 'me' : ''}>
                  <td className="rank" style={{ color: i === 0 ? 'var(--accent)' : 'inherit' }}>{i + 1}</td>
                  <td style={{ fontWeight: 600 }}>{p.name}{p.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</td>
                  <td className="mono">{p.team}</td>
                  <td className="mono" style={{ fontWeight: 600 }}>{p.score.toLocaleString()}</td>
                  <td className="mono">{p.resolved}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

// ============================================================
// 7 · PLAYERS
// ============================================================
function Players({ state }) {
  const teams = {};
  state.players.forEach((p) => { (teams[p.team] = teams[p.team] || []).push(p); });
  const order = Object.keys(teams).sort((a, b) => (a === state.you.team ? -1 : b === state.you.team ? 1 : 0));
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Players</h1><div className="sub">{state.players.length} players across {Object.keys(teams).length} teams</div></div></div>
      {order.map((tm) => (
        <div key={tm} className="panel" style={{ marginBottom: 'var(--gap)' }}>
          <div className="panel-head"><h2>Team {tm}{tm === state.you.team && <span style={{ color: 'var(--accent)' }}> · your team</span>}</h2><span className="corner">{teams[tm].length} members</span></div>
          <div className="panel-pad grid" style={{ gridTemplateColumns: 'repeat(auto-fill,minmax(220px,1fr))' }}>
            {teams[tm].map((p) => (
              <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)' }}>
                <span className="avatar" style={{ width: 38, height: 38 }}>{p.name[0]}</span>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>{p.name}{p.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</div>
                  <div style={{ fontSize: 11, color: 'var(--text-3)' }}>{p.score.toLocaleString()} pts · {p.resolved} resolved</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

// ============================================================
// 8 · SETTINGS
// ============================================================
function Settings({ state, openTweaks }) {
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Settings</h1><div className="sub">Session &amp; appearance</div></div></div>
      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr', alignItems: 'start' }}>
        <div className="panel">
          <div className="panel-head"><h2>Session</h2></div>
          <div className="panel-pad">
            <div className="kv"><span className="k">Game code</span><span className="v mono">{state.game}</span></div>
            <div className="kv"><span className="k">Player</span><span className="v">{state.you.player}</span></div>
          </div>
        </div>
        <div className="panel panel-pad" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14 }}>
          <div><div className="st">Appearance</div><div className="sd">Accent colour, fonts and density live in the Tweaks panel.</div></div>
          <button className="btn primary" onClick={openTweaks}><Icon name="settings" size={14} /> Open Tweaks</button>
        </div>
      </div>
    </div>
  );
}

export { Dashboard, NetworkMapPage, Incidents, IncidentDetail, Actions, Scoreboard, Players, Settings };
