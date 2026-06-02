/* ============================================================
   store.js — backend-driven game store.
   Replaces the old mock/simulation. Given a connection
   ({ user, session, playerId }) it fetches the player's own
   network + incidents + the scoreboard from the REST API,
   polls for updates, and submits actions to the server.

   It maps backend shapes onto the shape the screens expect, and
   keeps the same observable API surface the UI already uses:
       getState() / subscribe(fn) / applyAction(incId, actionId)
       acknowledge(incId) / setConfig(patch) / ACTIONS / LINKS
   ============================================================ */

import { Api } from './api.js';

// ---- shared helpers (used by ui.jsx + screens.jsx) ----
export function statusOf(h) {
  if (h <= 0) return 'down';
  if (h < 50) return 'crit';
  if (h < 80) return 'warn';
  return 'good';
}

// backend HealthStatus -> a 0-100 number the map/inspector can render
const HEALTH = { GOOD: 92, WARNING: 58, CRITICAL: 25 };

// backend action_name -> an icon from ui.jsx's glyph set
const ACTION_ICON = {
  REBALANCE_TRAFFIC: 'shuffle',
  RESTART_CELL: 'power',
  ROLLBACK_CONFIG: 'rewind',
  ROLLBACK_SOFTWARE: 'rewind',
  INCREASE_TRANSMIT_POWER: 'zap',
  FILTER_ALARMS: 'filter',
  DISABLE_AUTOMATION: 'x',
  ESCALATE: 'bell',
  IGNORE: 'x',
};

const prettyName = (name) =>
  name.toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

// Stable per-cell pseudo-random in [0,1] (FNV-1a) — lets us scatter towers without them
// jumping around on every poll.
function seededUnit(str, salt) {
  let h = (2166136261 ^ salt) >>> 0;
  for (let i = 0; i < str.length; i += 1) {
    h = Math.imul(h ^ str.charCodeAt(i), 16777619) >>> 0;
  }
  return (h >>> 0) / 4294967295;
}
const clampPct = (v) => Math.max(8, Math.min(92, v));

// Deterministic scatter: place towers at seeded random spots, rejecting positions too close
// to already-placed ones. Looks organic (no grid) and is stable across polls because it's
// driven only by the (sorted) cell names.
function scatterPositions(names, seed) {
  const n = names.length || 1;
  const margin = 14;
  const span = 100 - margin * 2;
  const minDist = Math.max(14, (span / Math.sqrt(n)) * 0.55);
  const placed = [];
  names.forEach((name) => {
    const key = seed + ':' + name; // per-session seed so each match is laid out differently
    let chosen = null;
    for (let attempt = 0; attempt < 40; attempt += 1) {
      const x = margin + seededUnit(key, attempt * 2 + 1) * span;
      const y = margin + seededUnit(key, attempt * 2 + 2) * span;
      if (!chosen) chosen = { x, y };
      if (placed.every((p) => Math.hypot(p.x - x, p.y - y) >= minDist)) {
        chosen = { x, y };
        break;
      }
    }
    placed.push(chosen);
  });
  return placed;
}

// ---- selectors (pure, derived) — unchanged API for the screens ----
export const Selectors = {
  networkHealth(s) {
    if (!s.cells.length) return 100;
    return Math.round(s.cells.reduce((a, c) => a + c.health, 0) / s.cells.length);
  },
  activeIncidents(s) { return s.incidents.filter((i) => i.status === 'open'); },
  cellStatus: statusOf,
  teamRank(s) {
    const sorted = [...s.teams].sort((a, b) => b.score - a.score);
    return sorted.findIndex((tm) => tm.you) + 1;
  },
};

function emptyState(conn) {
  return {
    game: conn.session.sessionCode,
    you: { player: conn.user.username, team: conn.user.username },
    score: 0,
    cells: [], links: [], incidents: [], activity: [],
    players: [], teams: [],
    sessionStatus: conn.session.status,
    endsAt: conn.session.endedAt ? Date.parse(conn.session.endedAt) : null,
    forfeitedBy: conn.session.forfeitedByPlayerId ?? null,
    config: { difficulty: 'normal', simSpeed: 1 },
    version: 0,
  };
}

export function createBackendStore(conn) {
  const sessionId = conn.session.id;
  const playerId = conn.playerId;
  let actions = {};          // backend id -> { id, name, desc, icon }
  let state = emptyState(conn);
  const subs = new Set();
  const notify = () => subs.forEach((f) => f(state));

  function buildState(session, players, cells, incidents, events) {
    const sorted = [...cells].sort((a, b) => a.cellName.localeCompare(b.cellName));
    const nameById = {};
    const positions = scatterPositions(sorted.map((c) => c.cellName), session.sessionCode || String(session.id));
    const uiCells = sorted.map((c, i) => {
      nameById[c.id] = c.cellName;
      return {
        id: c.cellName,
        backendId: c.id,
        health: HEALTH[c.healthStatus] ?? 70,
        users: Math.round(c.userLoad * 10),
        x: Math.round(clampPct(positions[i].x)),
        y: Math.round(clampPct(positions[i].y)),
        signalQuality: c.signalQuality,
        userLoad: c.userLoad,
        latency: c.latency,
        packetLoss: c.packetLoss,
        configStatus: c.configStatus,
      };
    });
    // a simple ring so the map has links to draw
    const links = uiCells.map((c, i) => [c.id, uiCells[(i + 1) % uiCells.length].id]);

    const uiIncidents = incidents.map((inc) => {
      const cell = sorted.find((c) => c.id === inc.cellId);
      return {
        id: inc.id,
        title: inc.incidentType,
        type: inc.incidentType,
        cellId: nameById[inc.cellId] || ('Cell ' + inc.cellId),
        severity: inc.severity.toLowerCase(),
        status: inc.status.toLowerCase(), // open | resolved | failed
        detectedAt: Date.parse(inc.createdAt),
        description: inc.description,
        symptomGroup: inc.symptomGroup || null,
        diagnostics: inc.availableDiagnostics || [], // [{ name, label }] — what to investigate with
        candidates: inc.candidates || [],            // [{ cause, label, action }] — the deduction board
        diagnosticBudget: inc.diagnosticBudget || 0, // how many diagnostics you may run on this incident
        metrics: cell
          ? { signalQuality: Math.round(cell.signalQuality), userLoad: Math.round(cell.userLoad), latency: Math.round(cell.latency), packetLoss: Math.round(cell.packetLoss) }
          : { signalQuality: 0, userLoad: 0, latency: 0, packetLoss: 0 },
        rec: [], // recommended action is the hidden root cause — never sent to the client
        resolvedBy: inc.status === 'OPEN' ? null : 'a player',
      };
    }).sort((a, b) => b.detectedAt - a.detectedAt); // newest first

    const resolvedCount = {};
    events.forEach((e) => { if (e.points > 0) resolvedCount[e.playerId] = (resolvedCount[e.playerId] || 0) + 1; });

    const me = players.find((p) => p.id === playerId);
    const myHealth = uiCells.length ? Math.round(uiCells.reduce((a, c) => a + c.health, 0) / uiCells.length) : 100;

    const uiPlayers = players.map((p) => ({
      id: p.id, name: p.teamName, team: p.teamName, you: p.id === playerId,
      score: p.score, resolved: resolvedCount[p.id] || 0, online: true,
    }));
    // Head-to-head: each player is their own "team" so the existing team views still work.
    const uiTeams = players.map((p) => ({
      id: p.teamName, name: p.teamName, score: p.score,
      health: myHealth, resolved: resolvedCount[p.id] || 0, penalty: 0, you: p.id === playerId,
    }));

    const activity = [...events]
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
      .map((e) => ({ id: 'e' + e.id, kind: e.points > 0 ? 'apply' : 'incident', text: e.reason, points: e.points, when: Date.parse(e.createdAt), playerId: e.playerId }));

    return {
      game: session.sessionCode,
      you: { player: conn.user.username, team: me ? me.teamName : conn.user.username },
      score: me ? me.score : 0,
      cells: uiCells, links, incidents: uiIncidents, activity,
      players: uiPlayers, teams: uiTeams,
      sessionStatus: session.status,
      endsAt: session.endedAt ? Date.parse(session.endedAt) : null,
      forfeitedBy: session.forfeitedByPlayerId ?? null,
      config: state.config,
      version: state.version + 1,
    };
  }

  async function refresh() {
    try {
      const [session, players, cells, incidents, events] = await Promise.all([
        Api.getSession(sessionId),
        Api.getPlayers(sessionId),
        Api.getCells(sessionId, playerId),
        Api.getIncidents(sessionId, playerId),
        Api.getScoreEvents(sessionId),
      ]);
      state = buildState(session, players, cells, incidents, events);
      notify();
    } catch { /* transient — keep last good snapshot */ }
  }

  async function applyAction(incidentId, actionId) {
    let outcome = null;
    try {
      outcome = await Api.submitAction(sessionId, Number(incidentId), playerId, Number(actionId));
    } catch { /* the refresh below reflects the server's truth either way */ }
    await refresh();
    return outcome; // { result: SUCCESS|PARTIAL|FAILED, pointsAwarded, ... } or null
  }

  // Investigation: run a diagnostic (returns its evidence) / fetch evidence gathered so far.
  async function runDiagnostic(incidentId, diagnostic) {
    return Api.runDiagnostic(sessionId, Number(incidentId), playerId, diagnostic);
  }

  async function getDiagnostics(incidentId) {
    return Api.getDiagnostics(sessionId, Number(incidentId), playerId);
  }

  // Diagnostic console: send a command, get emulated output. Errors (budget/guard) are returned
  // as printable output so the terminal can show them.
  async function runConsole(incidentId, command) {
    try {
      return await Api.runConsole(sessionId, Number(incidentId), playerId, command);
    } catch (e) {
      return { command, recognised: false, output: e?.message || 'error' };
    }
  }

  function acknowledge() { /* backend has no acknowledge step — no-op */ }

  // Leave the match — ends the session so the opponent is shown the result too.
  async function leave() {
    try { await Api.leaveSession(sessionId, playerId); } catch { /* leaving anyway */ }
  }

  function setConfig(patch) {
    state = { ...state, config: { ...state.config, ...patch }, version: state.version + 1 };
    notify();
  }

  // load the action catalog once, then begin polling
  Api.getActions().then((list) => {
    list.forEach((a) => { actions[a.id] = { id: a.id, actionName: a.actionName, name: prettyName(a.actionName), desc: a.description, icon: ACTION_ICON[a.actionName] || 'actions' }; });
    notify();
  }).catch(() => {});
  refresh();
  const timer = setInterval(refresh, 2500);

  return {
    getState: () => state,
    subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
    applyAction,
    runDiagnostic,
    getDiagnostics,
    runConsole,
    acknowledge,
    setConfig,
    leave,
    get ACTIONS() { return actions; },
    LINKS: [],
    stop() { clearInterval(timer); },
  };
}
