import React from 'react';
import { statusOf } from './store.js';
import { NetworkMap } from './NetworkMap3D.jsx';

/* ============================================================
   ui.jsx — shared UI primitives for O-RAN Network Defender
   ============================================================ */

// ---------- icon set (simple stroke glyphs) ----------
function Icon({ name, size = 16, ...p }) {
  const P = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', ...p };
  const g = {
    dashboard: <><rect x="3" y="3" width="7" height="9" rx="1" /><rect x="14" y="3" width="7" height="5" rx="1" /><rect x="14" y="12" width="7" height="9" rx="1" /><rect x="3" y="16" width="7" height="5" rx="1" /></>,
    map: <><path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z" /><path d="M9 4v14M15 6v14" /></>,
    alert: <><path d="M12 3 2 20h20L12 3Z" /><path d="M12 10v4M12 17.5v.5" /></>,
    actions: <><path d="M4 6h16M4 12h16M4 18h16" /><circle cx="9" cy="6" r="2" fill="var(--inset)" /><circle cx="15" cy="12" r="2" fill="var(--inset)" /><circle cx="8" cy="18" r="2" fill="var(--inset)" /></>,
    trophy: <><path d="M7 4h10v4a5 5 0 0 1-10 0V4Z" /><path d="M7 6H4v1a3 3 0 0 0 3 3M17 6h3v1a3 3 0 0 1-3 3M9 14v3h6v-3M8 21h8" /></>,
    users: <><circle cx="9" cy="8" r="3" /><path d="M3 20a6 6 0 0 1 12 0" /><path d="M16 5.5a3 3 0 0 1 0 5M21 20a6 6 0 0 0-4-5.6" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" /></>,
    tower: <><path d="M12 9v11" /><path d="M8.5 13 12 10l3.5 3" /><path d="M7 7a7 7 0 0 1 10 0" opacity=".7" /><path d="M5 5a10 10 0 0 1 14 0" opacity=".4" /></>,
    radio: <><circle cx="12" cy="12" r="2" /><path d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 16.2a6 6 0 0 0 0-8.4M5 5a10 10 0 0 0 0 14M19 19a10 10 0 0 0 0-14" /></>,
    shuffle: <><path d="M16 4h4v4M20 4l-7 7M4 20l5-5M16 20h4v-4M4 4l16 16" opacity="0" /><path d="M3 17h3l9-10h3M3 7h3l3 3.5M14.5 13 18 17M18 13l2 2-2 2M18 5l2 2-2 2" /></>,
    power: <><path d="M12 3v8" /><path d="M7 6a8 8 0 1 0 10 0" /></>,
    zap: <><path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z" /></>,
    rewind: <><path d="M11 6 4 12l7 6V6Z" /><path d="M20 6l-7 6 7 6V6Z" /></>,
    x: <><path d="M6 6l12 12M18 6 6 18" /></>,
    check: <><path d="M4 12l5 5L20 6" /></>,
    chevL: <><path d="M15 5l-7 7 7 7" /></>,
    chevR: <><path d="M9 5l7 7-7 7" /></>,
    chevRight: <><path d="M9 6l6 6-6 6" /></>,
    search: <><circle cx="11" cy="11" r="7" /><path d="M21 21l-4-4" /></>,
    filter: <><path d="M3 5h18l-7 8v5l-4 2v-7L3 5Z" /></>,
    expand: <><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" /></>,
    back: <><path d="M19 12H5M11 6l-6 6 6 6" /></>,
    bell: <><path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" /><path d="M10.5 20a2 2 0 0 0 3 0" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    learn: <><path d="M12 4 2 9l10 5 10-5-10-5Z" /><path d="M6 11v4c0 1.1 2.7 2.5 6 2.5s6-1.4 6-2.5v-4" /><path d="M22 9v4" /></>,
  };
  return <svg {...P}>{g[name] || null}</svg>;
}

// ---------- helpers ----------
function timeAgo(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60) return s + 's ago';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  return h + 'h ago';
}
const SEV_KIND = { high: 'crit', medium: 'warn', low: 'info' };
const STATUS_COLOR = { good: 'var(--good)', warn: 'var(--warn)', crit: 'var(--crit)', down: 'var(--text-3)' };

function SevTag({ sev }) {
  return <span className={'tag ' + (SEV_KIND[sev] || 'muted')}>{sev}</span>;
}
function StatusTag({ status }) {
  const map = { open: 'crit', investigating: 'warn', resolved: 'good', failed: 'crit' };
  return <span className={'tag ' + (map[status] || 'muted')}>{status}</span>;
}

function MapLegend() {
  return (
    <div className="legend">
      <span><i className="dot-sm" style={{ background: 'var(--good)' }} />Good 80–100%</span>
      <span><i className="dot-sm" style={{ background: 'var(--warn)' }} />Warning 50–79%</span>
      <span><i className="dot-sm" style={{ background: 'var(--crit)' }} />Critical 0–49%</span>
      <span><i className="dot-sm" style={{ background: 'var(--text-3)' }} />Offline</span>
    </div>
  );
}

// ---------- O-RAN topology ----------
// Simplified but architecturally accurate O-RAN layout:
//   data path:  O-RU --Open FH--> O-DU --F1--> O-CU --N2/N3--> 5GC (AMF/UPF)
//   control:    SMO (hosts Non-RT RIC/rApps) --A1--> Near-RT RIC (xApps) --E2--> O-CU/O-DU
//               SMO --O1--> managed RAN nodes
// The gNB is the O-CU + O-DU + O-RU together, so it isn't drawn as a separate box.
// Solid edges = user/data plane; dashed = control/management plane.
function Topology({ cells, height = 360 }) {
  const avg = Math.round(cells.reduce((a, c) => a + c.health, 0) / cells.length);
  const worst = Math.min(...cells.map((c) => c.health));
  const half = Math.ceil(cells.length / 2);
  const a1 = Math.round(cells.slice(0, half).reduce((a, c) => a + c.health, 0) / half);
  const a2 = Math.round(cells.slice(half).reduce((a, c) => a + c.health, 0) / (cells.length - half));
  const N = {
    smo:   { x: 50, y: 9,  label: 'SMO', sub: 'orchestration', st: 'good' },
    nonrt: { x: 32, y: 26, label: 'Non-RT RIC', sub: 'rApps', st: 'good' },
    nrt:   { x: 60, y: 40, label: 'Near-RT RIC', sub: 'xApps', st: worst < 40 ? 'warn' : 'good' },
    ru1:   { x: 13, y: 60, label: 'O-RU', sub: 'radio unit', st: statusOf(a1) },
    ru2:   { x: 13, y: 86, label: 'O-RU', sub: 'radio unit', st: statusOf(a2) },
    odu:   { x: 40, y: 73, label: 'O-DU', sub: 'distributed unit', st: statusOf(avg) },
    ocu:   { x: 64, y: 73, label: 'O-CU', sub: 'central unit', st: statusOf(avg) },
    amf:   { x: 89, y: 60, label: 'AMF', sub: '5GC · control', st: 'good' },
    upf:   { x: 89, y: 86, label: 'UPF', sub: '5GC · user', st: 'good' },
  };
  // [from, to, interface label, isControlPlane]
  const E = [
    ['ru1', 'odu', 'Open FH', false], ['ru2', 'odu', '', false],
    ['odu', 'ocu', 'F1', false],
    ['ocu', 'amf', 'N2', true], ['ocu', 'upf', 'N3', false],
    ['nrt', 'ocu', 'E2', true], ['nrt', 'odu', '', true],
    ['nonrt', 'nrt', 'A1', true],
    ['smo', 'nonrt', '', true],
    ['smo', 'odu', 'O1', true],
  ];
  const [sel, setSel] = React.useState(null);
  const DESC = {
    smo: ['SMO', 'Service Management & Orchestration — the top layer that monitors, configures and optimises the whole RAN, and hosts the Non-RT RIC.'],
    nonrt: ['Non-RT RIC', 'Non-Real-Time RAN Intelligent Controller — runs rApps for policy & analytics (loops slower than 1s), guiding the Near-RT RIC over the A1 interface.'],
    nrt: ['Near-RT RIC', 'Near-Real-Time RIC — runs xApps for fast (10ms–1s) optimisation, steering the O-CU/O-DU over the E2 interface.'],
    ocu: ['O-CU', 'O-RAN Central Unit — upper-layer baseband (RRC/PDCP). Connects to the O-DU over F1 and to the 5G core over NG (N2/N3).'],
    odu: ['O-DU', 'O-RAN Distributed Unit — lower-layer baseband (RLC/MAC). Connects the O-RUs over the Open Fronthaul interface.'],
    ru1: ['O-RU', 'O-RAN Radio Unit — the radio/antenna front end (RF + Low-PHY). Your network cells live here; its status reflects their health.'],
    ru2: ['O-RU', 'O-RAN Radio Unit — the radio/antenna front end (RF + Low-PHY). Your network cells live here; its status reflects their health.'],
    amf: ['AMF', '5G Core control plane — Access & Mobility Management Function (UE registration, connection and mobility management).'],
    upf: ['UPF', '5G Core user plane — User Plane Function that carries subscriber data traffic.'],
  };
  return (
    <div>
      <div className="topo" style={{ height }}>
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
          {E.map(([a, b, , ctrl], i) => (
            <line key={i} x1={N[a].x} y1={N[a].y} x2={N[b].x} y2={N[b].y}
              stroke="var(--text-3)" strokeWidth=".7"
              strokeDasharray={ctrl ? '1.5 1.5' : undefined} vectorEffect="non-scaling-stroke" />
          ))}
        </svg>
        {E.filter(([, , lbl]) => lbl).map(([a, b, lbl], i) => (
          <span key={'l' + i} style={{
            position: 'absolute', left: (N[a].x + N[b].x) / 2 + '%', top: (N[a].y + N[b].y) / 2 + '%',
            transform: 'translate(-50%,-50%)', fontSize: 11, letterSpacing: '.04em', color: 'var(--text-2)',
            background: 'var(--panel)', padding: '0 4px', borderRadius: 2, whiteSpace: 'nowrap', pointerEvents: 'none',
          }}>{lbl}</span>
        ))}
        {Object.entries(N).map(([k, n]) => (
          <div key={k} className="topo-node" onClick={() => setSel(k)}
            style={{ left: n.x + '%', top: n.y + '%', borderColor: sel === k ? 'var(--accent)' : undefined }}>
            <span className="stat-dot" style={{ background: STATUS_COLOR[n.st], boxShadow: '0 0 7px ' + STATUS_COLOR[n.st] }} />
            <div className="tn">{n.label}</div>
            <div className="ts">{n.sub}</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 12, padding: '11px 13px', background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', fontSize: 12.5, color: 'var(--text-2)', lineHeight: 1.55 }}>
        {sel
          ? <><b style={{ color: 'var(--text)' }}>{DESC[sel][0]}</b> — {DESC[sel][1]}</>
          : <span style={{ color: 'var(--text-3)' }}>Click a component to see what it does in the O-RAN architecture.</span>}
      </div>
    </div>
  );
}

export { Icon, timeAgo, SevTag, StatusTag, NetworkMap, MapLegend, Topology, SEV_KIND, STATUS_COLOR };

