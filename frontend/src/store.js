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
    const n = sorted.length || 1;
    const cols = Math.min(3, n);
    const rows = Math.max(1, Math.ceil(n / cols));
    const nameById = {};
    const uiCells = sorted.map((c, i) => {
      nameById[c.id] = c.cellName;
      return {
        id: c.cellName,
        backendId: c.id,
        health: HEALTH[c.healthStatus] ?? 70,
        users: Math.round(c.userLoad * 10),
        x: ((i % cols) + 1) / (cols + 1) * 100,
        y: (Math.floor(i / cols) + 1) / (rows + 1) * 100,
        signalQuality: c.signalQuality,
        userLoad: c.userLoad,
        latency: c.latency,
        packetLoss: c.packetLoss,
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
        status: inc.status === 'OPEN' ? 'open' : 'resolved',
        detectedAt: Date.parse(inc.createdAt),
        description: inc.description,
        metrics: cell
          ? { signalQuality: Math.round(cell.signalQuality), userLoad: Math.round(cell.userLoad), latency: Math.round(cell.latency), packetLoss: Math.round(cell.packetLoss) }
          : { signalQuality: 0, userLoad: 0, latency: 0, packetLoss: 0 },
        rec: [], // recommended action is the hidden root cause — never sent to the client
        resolvedBy: inc.status === 'OPEN' ? null : 'a player',
      };
    });

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
      .map((e) => ({ id: 'e' + e.id, kind: e.points > 0 ? 'apply' : 'incident', text: e.reason, points: e.points, when: Date.parse(e.createdAt) }));

    return {
      game: session.sessionCode,
      you: { player: conn.user.username, team: me ? me.teamName : conn.user.username },
      score: me ? me.score : 0,
      cells: uiCells, links, incidents: uiIncidents, activity,
      players: uiPlayers, teams: uiTeams,
      sessionStatus: session.status,
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
    try {
      await Api.submitAction(sessionId, Number(incidentId), playerId, Number(actionId));
    } catch { /* the refresh below reflects the server's truth either way */ }
    await refresh();
  }

  function acknowledge() { /* backend has no acknowledge step — no-op */ }

  function setConfig(patch) {
    state = { ...state, config: { ...state.config, ...patch }, version: state.version + 1 };
    notify();
  }

  // load the action catalog once, then begin polling
  Api.getActions().then((list) => {
    list.forEach((a) => { actions[a.id] = { id: a.id, name: prettyName(a.actionName), desc: a.description, icon: ACTION_ICON[a.actionName] || 'actions' }; });
    notify();
  }).catch(() => {});
  refresh();
  const timer = setInterval(refresh, 2500);

  return {
    getState: () => state,
    subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
    applyAction,
    acknowledge,
    setConfig,
    get ACTIONS() { return actions; },
    LINKS: [],
    stop() { clearInterval(timer); },
  };
}
