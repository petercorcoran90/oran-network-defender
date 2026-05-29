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
                  <div className="kv"><span className="k">Connected users</span><span className="v mono">{selCell.users}</span></div>
                  <div className="kv"><span className="k">Active incidents</span><span className="v mono">{selIncidents.length}</span></div>
                  <div className="kv"><span className="k">Coordinates</span><span className="v mono">{selCell.x}, {selCell.y}</span></div>
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
          <div className="panel-head"><h2>O-RAN Topology — Detailed View</h2><span className="corner">gNB · RU · DU · CU · RIC · 5GC</span></div>
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
            <button className="btn ghost"><Icon name="filter" size={14} /> Filters</button>
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
function metricRow(label, value, unit, max, danger) {
  const ratio = Math.min(1, value / max);
  const col = ratio > 0.75 ? 'var(--crit)' : ratio > 0.5 ? 'var(--warn)' : 'var(--good)';
  return (
    <div className="metric" key={label}>
      <span className="ml">{label}</span>
      <span className="mbar"><i style={{ width: ratio * 100 + '%', background: col }} /></span>
      <span className="mv" style={{ color: col }}>{value}{unit}</span>
    </div>
  );
}

function IncidentDetail({ state, store, nav, route }) {
  const inc = state.incidents.find((i) => i.id === route.params.id);
  if (!inc) return <div className="empty">Incident not found. <span className="link" onClick={() => nav('incidents')}>Back to list</span></div>;
  const cell = state.cells.find((c) => c.id === inc.cellId);
  const resolved = inc.status === 'resolved';
  const catalog = ['rebalance', 'restart', 'power', 'rollback', 'ignore'];
  const [flash, setFlash] = React.useState(null);
  function apply(aid) { store.applyAction(inc.id, aid); setFlash(aid); }
  return (
    <div className="fade-in">
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, color: 'var(--text-3)', fontSize: 12 }}>
        <span className="link" onClick={() => nav('incidents')} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><Icon name="back" size={14} /> Incidents</span>
        <Icon name="chevRight" size={12} /><span style={{ color: 'var(--text)' }}>{inc.id}</span>
      </div>

      <div className="panel" style={{ marginBottom: 'var(--gap)', borderLeft: '3px solid ' + SEV_COLOR[inc.severity] }}>
        <div className="panel-pad" style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <span className="action-ic" style={{ width: 44, height: 44, color: SEV_COLOR[inc.severity], borderColor: 'color-mix(in oklab,' + SEV_COLOR[inc.severity] + ' 30%,transparent)' }}><Icon name="alert" size={22} /></span>
          <div style={{ flex: 1 }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: 20, fontWeight: 600 }}>{inc.title}</div>
            <div style={{ display: 'flex', gap: 8, marginTop: 6 }}><SevTag sev={inc.severity} /><StatusTag status={inc.status} /><span className="id" style={{ alignSelf: 'center' }}>{inc.id}</span></div>
          </div>
          {!resolved && inc.status === 'open' && <button className="btn" onClick={() => store.acknowledge(inc.id)}><Icon name="bell" size={14} /> Acknowledge</button>}
          {resolved && <span className="tag good" style={{ fontSize: 12, padding: '6px 12px' }}><Icon name="check" size={13} /> Resolved by {inc.resolvedBy}</span>}
        </div>
      </div>

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
              {metricRow('CPU Usage', inc.metrics.cpu, '%', 100)}
              {metricRow('User Load', inc.metrics.userLoad, '%', 130)}
              {metricRow('Latency', inc.metrics.latency, 'ms', 200)}
              {metricRow('Packet Loss', inc.metrics.packetLoss, '%', 40)}
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-head"><h2>Available Actions</h2><span className="corner">{resolved ? 'incident closed' : 'choose remediation'}</span></div>
          <div>
            {catalog.map((aid) => {
              const a = store.ACTIONS[aid];
              const rec = inc.rec.includes(aid);
              return (
                <div key={aid} className={'action-row' + (flash === aid ? ' flash' : '')}>
                  <span className="action-ic"><Icon name={a.icon} size={17} /></span>
                  <div style={{ flex: 1 }}>
                    <div className="at">{a.name}{rec && <span className="pts-pill">★ recommended</span>}</div>
                    <div className="ad">{a.desc}</div>
                  </div>
                  <span className={'pts ' + (a.points >= 0 ? 'pos' : 'neg')} style={{ fontSize: 11, marginRight: 4 }}>{a.points > 0 ? '+' : ''}{a.points}</span>
                  <button className={'btn' + (rec && !resolved ? ' primary' : '')} disabled={resolved} onClick={() => apply(aid)}>Apply</button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// 5 · ACTIONS (remediation catalog + applied log)
// ============================================================
function Actions({ state, store, nav }) {
  const open = GameSelectors.activeIncidents(state);
  const applied = state.activity.filter((a) => a.kind === 'apply');
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Action Center</h1><div className="sub">Remediation playbook · apply the right fix to score points</div></div></div>

      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr', marginBottom: 'var(--gap)' }}>
        {Object.values(store.ACTIONS).map((a) => (
          <div key={a.id} className="panel panel-pad" style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <span className="action-ic" style={{ width: 42, height: 42 }}><Icon name={a.icon} size={20} /></span>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 15 }}>{a.name}</span>
                <span className={'pts ' + (a.points >= 0 ? 'pos' : 'neg')}>{a.points > 0 ? '+' : ''}{a.points} pts</span>
              </div>
              <div style={{ color: 'var(--text-3)', fontSize: 12, marginTop: 4 }}>{a.desc}</div>
              {a.heal > 0 && <div className="tag good" style={{ marginTop: 8 }}>+{a.heal}% cell health</div>}
            </div>
          </div>
        ))}
      </div>

      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <div className="panel">
          <div className="panel-head"><h2>Awaiting Action</h2><span className="corner">{open.length} open</span></div>
          <div className="panel-pad"><ActiveIncidents state={state} nav={nav} limit={8} /></div>
        </div>
        <div className="panel">
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
            <thead><tr><th style={{ width: 50 }}>Rank</th><th>Team</th><th>Score</th><th>Health</th><th>Resolved</th><th>Penalty</th></tr></thead>
            <tbody>
              {teams.map((tm, i) => (
                <tr key={tm.id} className={tm.you ? 'me' : ''}>
                  <td className="rank" style={{ color: i === 0 ? 'var(--accent)' : 'inherit' }}>{i + 1}</td>
                  <td style={{ fontWeight: 600 }}>{tm.name}{tm.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</td>
                  <td className="mono" style={{ fontWeight: 600 }}>{tm.score.toLocaleString()}</td>
                  <td className="mono">{tm.health}%</td>
                  <td className="mono">{tm.resolved}</td>
                  <td className="mono" style={{ color: 'var(--crit)' }}>{tm.penalty}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table>
            <thead><tr><th style={{ width: 50 }}>Rank</th><th>Player</th><th>Team</th><th>Score</th><th>Resolved</th><th>Status</th></tr></thead>
            <tbody>
              {players.map((p, i) => (
                <tr key={p.id} className={p.you ? 'me' : ''}>
                  <td className="rank" style={{ color: i === 0 ? 'var(--accent)' : 'inherit' }}>{i + 1}</td>
                  <td style={{ fontWeight: 600 }}>{p.name}{p.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}</td>
                  <td className="mono">{p.team}</td>
                  <td className="mono" style={{ fontWeight: 600 }}>{p.score.toLocaleString()}</td>
                  <td className="mono">{p.resolved}</td>
                  <td><span className="tag" style={{ color: p.online ? 'var(--good)' : 'var(--text-3)', background: 'transparent', borderColor: 'var(--hair)' }}><i className="dot-sm" style={{ background: p.online ? 'var(--good)' : 'var(--text-3)' }} />{p.online ? 'online' : 'offline'}</span></td>
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
                <span className="dot-sm" style={{ background: p.online ? 'var(--good)' : 'var(--text-3)', boxShadow: p.online ? '0 0 7px var(--good)' : 'none' }} />
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
  const [toggles, setToggles] = React.useState({ notify: true, sound: false, autoack: false, hints: true });
  const T = (k, label, desc) => (
    <div className="set-row">
      <div><div className="st">{label}</div><div className="sd">{desc}</div></div>
      <div className={'switch' + (toggles[k] ? ' on' : '')} onClick={() => setToggles((s) => ({ ...s, [k]: !s[k] }))}><i /></div>
    </div>
  );
  return (
    <div className="fade-in">
      <div className="page-head"><div><h1>Settings</h1><div className="sub">Player &amp; game preferences</div></div></div>
      <div className="grid" style={{ gridTemplateColumns: '1fr 1fr', alignItems: 'start' }}>
        <div className="panel">
          <div className="panel-head"><h2>Gameplay</h2></div>
          {T('notify', 'Incident notifications', 'Toast me when a new incident is detected on my network.')}
          {T('sound', 'Alert sound', 'Play an audio cue for high-severity incidents.')}
          {T('autoack', 'Auto-acknowledge', 'Automatically mark new incidents as investigating.')}
          {T('hints', 'Show recommended actions', 'Highlight the suggested remediation in incident detail.')}
        </div>
        <div className="grid" style={{ gap: 'var(--gap)' }}>
          <div className="panel">
            <div className="panel-head"><h2>Session</h2></div>
            <div className="panel-pad">
              <div className="kv"><span className="k">Game ID</span><span className="v mono">{state.game}</span></div>
              <div className="kv"><span className="k">Player</span><span className="v">{state.you.player}</span></div>
              <div className="kv"><span className="k">Team</span><span className="v">{state.you.team}</span></div>
              <div className="kv"><span className="k">Difficulty</span><span className="v" style={{ textTransform: 'capitalize' }}>{state.config.difficulty}</span></div>
              <div className="kv"><span className="k">Simulation</span><span className="v">{state.config.simSpeed === 0 ? 'Paused' : state.config.simSpeed + '×'}</span></div>
            </div>
          </div>
          <div className="panel panel-pad" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14 }}>
            <div><div className="st">Appearance &amp; difficulty</div><div className="sd">Accent, fonts, density, difficulty and sim speed live in the Tweaks panel.</div></div>
            <button className="btn primary" onClick={openTweaks}><Icon name="settings" size={14} /> Open Tweaks</button>
          </div>
        </div>
      </div>
    </div>
  );
}

export { Dashboard, NetworkMapPage, Incidents, IncidentDetail, Actions, Scoreboard, Players, Settings };
