import React, { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Api, ApiError } from './api.js';

/* ============================================================
   Lobby.jsx — real backend connect flow (the first wired slice).
   identify -> choose (create or join) -> room (live match lobby).
   Talks only to the REST API; does not touch the mock GameStore.
   ============================================================ */

const inputStyle = {
  width: '100%', padding: '10px 12px', background: 'var(--inset)',
  border: '1px solid var(--hair)', borderRadius: 'var(--r)', color: 'var(--text)',
  fontFamily: 'inherit', fontSize: 13, outline: 'none',
};
const labelStyle = {
  fontSize: 10.5, letterSpacing: '.08em', textTransform: 'uppercase',
  color: 'var(--text-3)', marginBottom: 6, display: 'block',
};

function Field({ label, children }) {
  return <div style={{ marginBottom: 14 }}><label style={labelStyle}>{label}</label>{children}</div>;
}

Field.propTypes = {
  label: PropTypes.string.isRequired,
  children: PropTypes.node.isRequired,
};

function StatusTag({ status }) {
  const cls = status === 'ACTIVE' ? 'good' : status === 'WAITING' ? 'warn' : 'muted';
  return <span className={'tag ' + cls}>{status}</span>;
}

StatusTag.propTypes = {
  status: PropTypes.string.isRequired,
};

export default function Lobby({ onEnter }) {
  const [step, setStep] = useState('identify');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  // identity
  const [username, setUsername] = useState('');
  const [user, setUser] = useState(null);            // { id, username }

  // create form
  const [matchName, setMatchName] = useState('');
  const [minutes, setMinutes] = useState(5);
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [joinCode, setJoinCode] = useState('');

  // join
  const [sessions, setSessions] = useState([]);

  // high scores
  const [scores, setScores] = useState([]);

  // room
  const [session, setSession] = useState(null);      // GameSession
  const [playerId, setPlayerId] = useState(null);
  const [players, setPlayers] = useState([]);
  const [countdown, setCountdown] = useState(null);   // 3..2..1 once both ready

  const myPlayer = players.find((p) => p.id === playerId);
  const myReady = !!(myPlayer && myPlayer.ready);

  const run = useCallback(async (fn) => {
    setBusy(true); setError(null);
    try { return await fn(); }
    catch (e) { setError(e instanceof ApiError ? e.message : 'Network error — is the backend running on :8080?'); throw e; }
    finally { setBusy(false); }
  }, []);

  // --- identify ---
  async function identify() {
    if (!username.trim()) return;
    await run(async () => {
      const u = await Api.login(username.trim());
      setUser(u);
      setMatchName(`${u.username}'s match`);
      setStep('choose');
    }).catch(() => {});
  }

  async function loadScores() {
    await run(async () => {
      setScores(await Api.getHighScores());
      setStep('scores');
    }).catch(() => {});
  }

  // --- solo training: activates immediately at your tier's difficulty, no opponent ---
  async function startTraining() {
    await run(async () => {
      const tr = await Api.startTraining(user.id, Number(minutes) * 60);
      onEnter({ user, session: tr.session, playerId: tr.playerId });
    }).catch(() => {});
  }

  // --- create + auto-join as the creator ---
  async function createMatch() {
    await run(async () => {
      const s = await Api.createSession(matchName.trim() || `${user.username}'s match`, user.id, Number(minutes) * 60, difficulty);
      const me = await Api.joinSession(s.id, user.id, user.username);
      setSession(s); setPlayerId(me.id); setStep('room');
    }).catch(() => {});
  }

  // --- load joinable sessions ---
  async function loadSessions() {
    await run(async () => {
      const all = await Api.listSessions();
      setSessions(all.filter((s) => s.status === 'WAITING'));
      setStep('join');
    }).catch(() => {});
  }

  async function joinMatch(s) {
    await run(async () => {
      const me = await Api.joinSession(s.id, user.id, user.username);
      setSession(s); setPlayerId(me.id); setStep('room');
    }).catch(() => {});
  }

  async function joinByCode() {
    if (!joinCode.trim()) return;
    await run(async () => {
      const s = await Api.getSessionByCode(joinCode.trim());
      const me = await Api.joinSession(s.id, user.id, user.username);
      setSession(s); setPlayerId(me.id); setStep('room');
    }).catch(() => {});
  }

  // --- poll the room while waiting ---
  useEffect(() => {
    if (step !== 'room' || !session) return undefined;
    let alive = true;
    const tick = async () => {
      try {
        const [s, ps] = await Promise.all([Api.getSession(session.id), Api.getPlayers(session.id)]);
        if (!alive) return;
        setSession(s); setPlayers(ps);
      } catch { /* keep last good snapshot */ }
    };
    tick();
    const h = setInterval(tick, 2000);
    return () => { alive = false; clearInterval(h); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, session?.id]);

  async function readyUp() {
    await run(() => Api.ready(session.id, playerId)).catch(() => {});
  }

  // Once the backend flips the match to ACTIVE (both players ready), run a 3-2-1 countdown
  // and then drop both players into the game console.
  useEffect(() => {
    if (step !== 'room' || !session || session.status !== 'ACTIVE') return undefined;
    if (countdown === null) { setCountdown(3); return undefined; }
    if (countdown <= 0) { onEnter({ user, session, playerId }); return undefined; }
    const h = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(h);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, session?.status, countdown]);

  function leave() {
    setSession(null); setPlayers([]); setPlayerId(null); setCountdown(null); setError(null); setStep('choose');
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, position: 'relative', zIndex: 1 }}>
      <div className="panel" style={{ width: step === 'scores' ? 'min(640px, 96vw)' : 'min(460px, 94vw)' }}>
        <div className="panel-head">
          <h2>O-RAN Network Defender</h2>
          <span className="corner">{step === 'room' ? 'MATCH LOBBY' : step === 'scores' ? 'TOP SCORERS' : 'CONNECT'}</span>
        </div>
        <div className="panel-pad">

          {step === 'identify' && (
            <>
              <p style={{ color: 'var(--text-3)', marginTop: 0, fontSize: 12.5 }}>Enter a callsign to register and connect to the game server.</p>
              <Field label="Callsign">
                <input style={inputStyle} value={username} autoFocus
                  onChange={(e) => setUsername(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && identify()}
                  placeholder="e.g. alice" />
              </Field>
              <button className="btn primary" disabled={busy || !username.trim()} onClick={identify}>
                {busy ? 'Connecting…' : 'Continue'}
              </button>
              <button className="btn ghost" disabled={busy} onClick={loadScores} style={{ width: '100%', justifyContent: 'center', marginTop: 10 }}>
                🏆 Top scorers
              </button>
            </>
          )}

          {step === 'scores' && (
            <>
              <p style={{ color: 'var(--text-2)', marginTop: 0, fontSize: 12.5 }}>Top matches by winning score.</p>
              {scores.length === 0 ? <div className="empty">No completed matches yet.</div> : (
                <table>
                  <thead><tr><th>Player</th><th>Score</th><th>Difficulty</th><th>Length</th><th>Beat</th></tr></thead>
                  <tbody>
                    {scores.map((s, i) => (
                      <tr key={i}>
                        <td style={{ fontWeight: 600 }}>{s.winnerName}</td>
                        <td className="mono">{s.winnerScore.toLocaleString()}</td>
                        <td className="mono" style={{ textTransform: 'capitalize' }}>{s.difficulty.toLowerCase()}</td>
                        <td className="mono">{Math.round(s.durationSeconds / 60)}m</td>
                        <td>{s.loserName}{s.forfeit && <span className="tag muted" style={{ marginLeft: 6 }}>forfeit</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <button className="btn ghost" onClick={() => setStep('identify')} style={{ width: '100%', justifyContent: 'center', marginTop: 14 }}>Back</button>
            </>
          )}

          {step === 'choose' && (
            <>
              <p style={{ color: 'var(--text-2)', marginTop: 0, fontSize: 12.5 }}>
                Signed in as <b style={{ color: 'var(--accent)' }}>{user.username}</b>. Start a new match or join one.
              </p>
              <div className="panel" style={{ background: 'var(--inset)', padding: 'var(--pad)', marginBottom: 14 }}>
                <Field label="Match name">
                  <input style={inputStyle} value={matchName} onChange={(e) => setMatchName(e.target.value)} />
                </Field>
                <Field label={`Match length · ${minutes} min`}>
                  <input type="range" min={1} max={15} step={1} value={minutes}
                    onChange={(e) => setMinutes(Number(e.target.value))}
                    style={{ width: '100%', accentColor: 'var(--accent)', cursor: 'pointer' }} />
                </Field>
                <Field label="Difficulty">
                  <div className="seg" style={{ width: '100%' }}>
                    {[['EASY', 'Easy · 3'], ['MEDIUM', 'Medium · 6'], ['HARD', 'Hard · 9']].map(([v, l]) => (
                      <button key={v} type="button" className={difficulty === v ? 'on' : ''} style={{ flex: 1 }} onClick={() => setDifficulty(v)}>{l}</button>
                    ))}
                  </div>
                </Field>
                <button className="btn primary" disabled={busy} onClick={createMatch}>{busy ? 'Working…' : 'Create match'}</button>
              </div>
              <div className="panel" style={{ background: 'var(--inset)', padding: 'var(--pad)', marginBottom: 14 }}>
                <Field label="Join with a match code">
                  <input style={inputStyle} value={joinCode} maxLength={6}
                    onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                    onKeyDown={(e) => e.key === 'Enter' && joinByCode()}
                    placeholder="e.g. L5Y9FS" />
                </Field>
                <button className="btn" disabled={busy || !joinCode.trim()} onClick={joinByCode}>Join match</button>
              </div>
              <button className="btn ghost" disabled={busy} onClick={loadSessions} style={{ width: '100%', justifyContent: 'center' }}>Or browse open matches</button>

              <div className="panel" style={{ background: 'var(--inset)', padding: 'var(--pad)', marginTop: 14 }}>
                <p style={{ color: 'var(--text-3)', fontSize: 12, margin: '0 0 10px' }}>
                  Solo training · no opponent · difficulty is set by your tier and rises as you learn.
                </p>
                <button className="btn" disabled={busy} onClick={startTraining} style={{ width: '100%', justifyContent: 'center' }}>
                  {busy ? 'Working…' : 'Start training'}
                </button>
              </div>
            </>
          )}

          {step === 'join' && (
            <>
              <p style={{ color: 'var(--text-2)', marginTop: 0, fontSize: 12.5 }}>Open matches waiting for a second player:</p>
              {sessions.length === 0 && <div className="empty">No open matches. Go back and create one.</div>}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {sessions.map((s) => (
                  <div key={s.id} className="inc-mini" onClick={() => !busy && joinMatch(s)}>
                    <div className="inc-hdr">
                      <span className="t">{s.name}</span>
                      <span className="chip"><b>{s.sessionCode}</b></span>
                    </div>
                  </div>
                ))}
              </div>
              <button className="btn ghost" onClick={() => setStep('choose')} style={{ width: '100%', justifyContent: 'center', marginTop: 14 }}>Back</button>
            </>
          )}

          {step === 'room' && session && (
            <>
              <div style={{ textAlign: 'center', marginBottom: 16 }}>
                <div style={{ fontSize: 10.5, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--text-3)' }}>Session code</div>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 34, fontWeight: 700, color: 'var(--accent)', letterSpacing: '.06em' }}>{session.sessionCode}</div>
                <div style={{ marginTop: 6 }}><StatusTag status={session.status} /></div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                {[0, 1].map((i) => {
                  const p = players[i];
                  return (
                    <div key={i} className="inc-mini" style={{ borderLeftColor: p ? (p.ready ? 'var(--good)' : 'var(--warn)') : 'var(--hair)' }}>
                      <div className="inc-hdr">
                        <span className="t">{p ? p.teamName : 'Waiting for player…'}{p && p.id === playerId ? ' (you)' : ''}</span>
                        {p
                          ? <span className={'tag ' + (p.ready ? 'good' : 'muted')}>{p.ready ? 'READY' : 'NOT READY'}</span>
                          : <span className="chip">—</span>}
                      </div>
                    </div>
                  );
                })}
              </div>

              {countdown !== null && session.status === 'ACTIVE' ? (
                <div style={{ textAlign: 'center', marginBottom: 4 }}>
                  <div style={{ fontFamily: 'var(--font-head)', fontSize: 44, fontWeight: 700, color: 'var(--accent)' }}>{countdown > 0 ? countdown : 'GO'}</div>
                  <div style={{ color: 'var(--text-3)', fontSize: 12 }}>Match starting…</div>
                </div>
              ) : (
                <>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="btn primary" style={{ flex: 1, justifyContent: 'center' }}
                      disabled={busy || myReady || players.length < 2}
                      onClick={readyUp}>
                      {myReady ? 'Ready ✓' : players.length < 2 ? 'Waiting for opponent…' : 'Ready up'}
                    </button>
                    <button className="btn ghost" onClick={leave}>Leave</button>
                  </div>
                  <p style={{ textAlign: 'center', color: 'var(--text-3)', fontSize: 12, marginTop: 10 }}>
                    The match starts when both players are ready.
                  </p>
                </>
              )}
            </>
          )}

          {error && <div className="tag crit" style={{ marginTop: 14, display: 'block', padding: '8px 10px' }}>{error}</div>}
        </div>
      </div>
    </div>
  );
}

Lobby.propTypes = {
  onEnter: PropTypes.func.isRequired,
};
