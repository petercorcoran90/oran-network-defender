/* ============================================================
   data.js — O-RAN Network Defender mock state + game store
   ------------------------------------------------------------
   This is the SINGLE source of game data for the prototype.
   It exposes a small observable store API:

       GameStore.getState()            -> current state snapshot
       GameStore.subscribe(fn)         -> fn(state) on change; returns unsub
       GameStore.applyAction(incId, actionId)
       GameStore.acknowledge(incId)
       GameStore.setConfig({difficulty, simSpeed})

   To go live, replace the body of createStore() with calls to
   your REST/WebSocket backend while keeping this same API
   surface — the UI never touches the internals directly.
   ============================================================ */

  const now = () => Date.now();

  // ---- helpers ----
  export function statusOf(h) {
    if (h <= 0) return 'down';
    if (h < 50) return 'crit';
    if (h < 80) return 'warn';
    return 'good';
  }
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

  // ---- network cells (positions are % of map canvas) ----
  const CELLS = [
    { id: 'Cell-01', x: 14, y: 24, health: 92, users: 412 },
    { id: 'Cell-02', x: 34, y: 16, health: 76, users: 690 },
    { id: 'Cell-03', x: 58, y: 22, health: 45, users: 980 },
    { id: 'Cell-04', x: 82, y: 30, health: 88, users: 354 },
    { id: 'Cell-05', x: 40, y: 48, health: 67, users: 720 },
    { id: 'Cell-06', x: 88, y: 58, health: 95, users: 240 },
    { id: 'Cell-07', x: 30, y: 70, health: 33, users: 510 },
    { id: 'Cell-08', x: 62, y: 64, health: 70, users: 605 },
    { id: 'Cell-09', x: 16, y: 84, health: 85, users: 288 },
    { id: 'Cell-10', x: 78, y: 86, health: 95, users: 199 },
  ];
  // adjacency for the map links + traffic rebalancing
  const LINKS = [
    ['Cell-01', 'Cell-02'], ['Cell-02', 'Cell-03'], ['Cell-03', 'Cell-04'],
    ['Cell-01', 'Cell-05'], ['Cell-02', 'Cell-05'], ['Cell-03', 'Cell-08'],
    ['Cell-04', 'Cell-06'], ['Cell-05', 'Cell-07'], ['Cell-05', 'Cell-08'],
    ['Cell-06', 'Cell-08'], ['Cell-07', 'Cell-09'], ['Cell-08', 'Cell-10'],
    ['Cell-09', 'Cell-07'], ['Cell-10', 'Cell-06'],
  ];

  // ---- action catalog ----
  const ACTIONS = {
    rebalance: { id: 'rebalance', name: 'Rebalance Traffic', desc: 'Redistribute users to neighboring cells', points: 50, heal: 28, icon: 'shuffle' },
    restart:   { id: 'restart',   name: 'Restart Cell',      desc: 'Restart the affected radio unit',        points: 40, heal: 42, icon: 'power' },
    power:     { id: 'power',      name: 'Increase Power',    desc: 'Increase transmit power by 10%',         points: 30, heal: 18, icon: 'zap' },
    rollback:  { id: 'rollback',  name: 'Rollback Config',   desc: 'Revert to previous configuration',       points: 35, heal: 25, icon: 'rewind' },
    ignore:    { id: 'ignore',    name: 'Ignore',            desc: 'Dismiss this incident for now',          points: -10, heal: 0, icon: 'x' },
  };

  // ---- incident templates ----
  const TEMPLATES = {
    overload: { type: 'overload', title: 'Cell Overload',          rec: ['rebalance', 'power'],   metrics: { cpu: 92, userLoad: 120, latency: 85, packetLoss: 12 }, desc: 'CPU utilization is above 90% and the number of connected users is very high.' },
    latency:  { type: 'latency',  title: 'High Latency',           rec: ['rebalance', 'power'],   metrics: { cpu: 64, userLoad: 88, latency: 142, packetLoss: 6 }, desc: 'Round-trip latency exceeds the SLA threshold of 50ms for this cell.' },
    packet:   { type: 'packet',   title: 'Packet Loss Detected',   rec: ['restart', 'rollback'],  metrics: { cpu: 58, userLoad: 70, latency: 64, packetLoss: 28 }, desc: 'Uplink packet loss has risen above 20% over the last polling window.' },
    config:   { type: 'config',   title: 'Configuration Mismatch', rec: ['rollback', 'restart'],  metrics: { cpu: 47, userLoad: 60, latency: 58, packetLoss: 9 }, desc: 'Active cell configuration does not match the approved baseline profile.' },
    alarm:    { type: 'alarm',    title: 'Alarm Storm',            rec: ['restart', 'rollback'],  metrics: { cpu: 88, userLoad: 95, latency: 110, packetLoss: 18 }, desc: 'A burst of fault-management alarms has been raised by this node.' },
    coverage: { type: 'coverage', title: 'Coverage Hole',          rec: ['power', 'rebalance'],   metrics: { cpu: 40, userLoad: 52, latency: 96, packetLoss: 14 }, desc: 'RSRP has dropped below threshold across part of this cell sector.' },
  };
  const TEMPLATE_KEYS = Object.keys(TEMPLATES);

  // ---- players / teams (multiplayer-ready) ----
  const PLAYERS = [
    { id: 'p1', name: 'Player1', team: 'Alpha', you: true, score: 720, resolved: 5, online: true },
    { id: 'p2', name: 'Player2', team: 'Alpha', you: false, score: 530, resolved: 3, online: true },
    { id: 'p3', name: 'Nyx',     team: 'Bravo', you: false, score: 1010, resolved: 7, online: true },
    { id: 'p4', name: 'Orion',   team: 'Bravo', you: false, score: 840, resolved: 5, online: false },
    { id: 'p5', name: 'Vega',    team: 'Charlie', you: false, score: 980, resolved: 6, online: true },
    { id: 'p6', name: 'Lyra',    team: 'Delta', you: false, score: 620, resolved: 4, online: false },
  ];
  const TEAMS = [
    { id: 'Bravo',   name: 'Team Bravo',   score: 1850, health: 91, resolved: 12, penalty: -50, you: false },
    { id: 'Alpha',   name: 'Team Alpha',   score: 1250, health: 82, resolved: 8,  penalty: -20, you: true },
    { id: 'Charlie', name: 'Team Charlie', score: 980,  health: 71, resolved: 6,  penalty: -40, you: false },
    { id: 'Delta',   name: 'Team Delta',   score: 620,  health: 56, resolved: 4,  penalty: -60, you: false },
  ];

  // ---- seed incidents (matches the briefing dashboard) ----
  const t = now();
  let incCounter = 1006;
  const seedIncidents = [
    mkSeed('INC-1001', 'overload', 'Cell-03', 'high',   'open',          2),
    mkSeed('INC-1002', 'latency',  'Cell-07', 'high',   'open',          4),
    mkSeed('INC-1003', 'packet',   'Cell-05', 'medium', 'open',          6),
    mkSeed('INC-1004', 'config',   'Cell-02', 'medium', 'investigating', 8),
    mkSeed('INC-1005', 'alarm',    'Cell-03', 'high',   'open',          10),
  ];
  function mkSeed(id, type, cellId, sev, status, minsAgo) {
    const tpl = TEMPLATES[type];
    return {
      id, type, title: tpl.title, cellId, severity: sev, status,
      detectedAt: t - minsAgo * 60000, description: tpl.desc,
      metrics: { ...tpl.metrics }, rec: tpl.rec, resolvedBy: null,
    };
  }

  // ---- activity feed seed ----
  const seedFeed = [
    feed('apply', 'Player1 applied Rebalance Traffic on Cell-03', 50, 2),
    feed('ack', 'Player2 acknowledged incident INC-1002', 10, 4),
    feed('apply', 'Player1 applied Restart Cell on Cell-07', 40, 6),
    feed('apply', 'Player2 applied Increase Power on Cell-05', 30, 8),
    feed('incident', 'Incident INC-1004 occurred on Cell-02', 0, 10),
  ];
  function feed(kind, text, points, minsAgo) {
    return { id: 'f' + Math.random().toString(36).slice(2, 8), kind, text, points, when: now() - minsAgo * 60000 };
  }

  // ============================================================
  function createStore() {
    let state = {
      game: '7f3a1c',
      you: { player: 'Player1', team: 'Alpha' },
      score: 1250,
      cells: CELLS.map((c) => ({ ...c })),
      links: LINKS,
      incidents: seedIncidents,
      activity: seedFeed,
      players: PLAYERS,
      teams: TEAMS,
      config: { difficulty: 'normal', simSpeed: 1 },
      version: 0,
    };
    const subs = new Set();
    function emit() { state = { ...state, version: state.version + 1 }; subs.forEach((f) => f(state)); }

    function cell(id) { return state.cells.find((c) => c.id === id); }

    function logFeed(kind, text, points) {
      state.activity = [{ id: 'f' + Math.random().toString(36).slice(2, 8), kind, text, points, when: now() }, ...state.activity].slice(0, 40);
    }

    // ---- mutations ----
    function applyAction(incId, actionId) {
      const inc = state.incidents.find((i) => i.id === incId);
      if (!inc || inc.status === 'resolved') return;
      const act = ACTIONS[actionId];
      if (!act) return;
      const c = cell(inc.cellId);

      if (actionId === 'ignore') {
        inc.status = 'open';
        state.score = Math.max(0, state.score + act.points);
        logFeed('apply', `${state.you.player} ignored ${inc.id}`, act.points);
        emit();
        return;
      }
      // correct action (in recommended set) -> full points + full heal
      const correct = inc.rec.includes(actionId);
      const gained = correct ? act.points : Math.round(act.points * 0.5);
      if (c) c.health = clamp(c.health + (correct ? act.heal : Math.round(act.heal * 0.5)), 0, 100);
      inc.status = 'resolved';
      inc.resolvedBy = state.you.player;
      state.score += gained;
      const me = state.players.find((p) => p.you);
      if (me) { me.score += gained; me.resolved += 1; }
      const myTeam = state.teams.find((tm) => tm.you);
      if (myTeam) { myTeam.score += gained; myTeam.resolved += 1; }
      logFeed('apply', `${state.you.player} applied ${act.name} on ${inc.cellId}`, gained);
      emit();
    }

    function acknowledge(incId) {
      const inc = state.incidents.find((i) => i.id === incId);
      if (!inc || inc.status !== 'open') return;
      inc.status = 'investigating';
      state.score += 10;
      logFeed('ack', `${state.you.player} acknowledged incident ${inc.id}`, 10);
      emit();
    }

    function setConfig(patch) { state.config = { ...state.config, ...patch }; emit(); }

    // ---- simulation ----
    const sevByDifficulty = {
      easy:   ['low', 'low', 'medium', 'medium', 'high'],
      normal: ['low', 'medium', 'medium', 'high', 'high'],
      hard:   ['medium', 'high', 'high', 'high', 'high'],
    };
    function spawnIncident() {
      const open = state.incidents.filter((i) => i.status !== 'resolved');
      if (open.length >= 12) return;
      // prefer degrading a healthier cell so criticals don't all stack
      const candidates = state.cells.filter((c) => c.health > 25);
      const c = pick(candidates.length ? candidates : state.cells);
      const type = pick(TEMPLATE_KEYS);
      const tpl = TEMPLATES[type];
      const sev = pick(sevByDifficulty[state.config.difficulty] || sevByDifficulty.normal);
      const drop = sev === 'high' ? 22 : sev === 'medium' ? 13 : 7;
      c.health = clamp(c.health - drop, 0, 100);
      const id = 'INC-' + (++incCounter);
      state.incidents = [{
        id, type, title: tpl.title, cellId: c.id, severity: sev, status: 'open',
        detectedAt: now(), description: tpl.desc, metrics: { ...tpl.metrics }, rec: tpl.rec, resolvedBy: null,
      }, ...state.incidents];
      logFeed('incident', `Incident ${id} occurred on ${c.id}`, 0);
      emit();
    }

    let acc = 0;
    setInterval(() => {
      const speed = state.config.simSpeed;
      if (!speed) return;            // paused
      acc += speed;
      // gentle health regen on cells with no open incident
      const busy = new Set(state.incidents.filter((i) => i.status !== 'resolved').map((i) => i.cellId));
      let drifted = false;
      state.cells.forEach((c) => {
        if (!busy.has(c.id) && c.health < 100) { c.health = clamp(c.health + 1, 0, 100); drifted = true; }
      });
      if (acc >= 4) { acc = 0; spawnIncident(); }
      else if (drifted) emit();
    }, 2000);

    return {
      getState: () => state,
      subscribe(fn) { subs.add(fn); return () => subs.delete(fn); },
      applyAction, acknowledge, setConfig,
      ACTIONS, LINKS,
    };
  }

  // ---- selectors (pure, derived) ----
  export const Selectors = {
    networkHealth(s) { return Math.round(s.cells.reduce((a, c) => a + c.health, 0) / s.cells.length); },
    activeIncidents(s) { return s.incidents.filter((i) => i.status !== 'resolved'); },
    cellStatus: statusOf,
    teamRank(s) {
      const sorted = [...s.teams].sort((a, b) => b.score - a.score);
      const me = sorted.findIndex((tm) => tm.you);
      return me + 1;
    },
  };

export const GameStore = createStore();
