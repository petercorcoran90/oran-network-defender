import React from 'react';
import PropTypes from 'prop-types';
import { Selectors } from './store.js';

/* ============================================================
   GameOver.jsx — end-of-match summary / winner screen.
   Shown when the session reaches ENDED. Winner is decided by
   the server-authoritative scores; ties are a draw.
   ============================================================ */

// The headline banner + colour for each outcome (kept as a flat lookup rather than a
// nested ternary so the four cases read top-to-bottom).
function outcomeBanner(solo, draw, youWon) {
  if (solo) return { text: 'TRAINING COMPLETE', color: 'var(--accent)' };
  if (draw) return { text: 'DRAW', color: 'var(--warn)' };
  if (youWon) return { text: 'YOU WIN', color: 'var(--good)' };
  return { text: 'YOU LOSE', color: 'var(--crit)' };
}

export default function GameOver({ state, onExit }) {
  const players = [...state.players].sort((a, b) => b.score - a.score);
  const me = state.players.find((p) => p.you);
  const forfeitedBy = state.forfeitedBy;
  const forfeited = forfeitedBy != null;
  const solo = state.mode === 'TRAINING' || players.length <= 1;

  let draw;
  let winnerId;
  let youWon;
  if (solo) {
    // Training: no opponent — it's about your score + how far you've progressed.
    draw = false; winnerId = null; youWon = false;
  } else if (forfeited) {
    // Ragequit: whoever didn't leave wins, regardless of score.
    draw = false;
    const w = state.players.find((p) => p.id !== forfeitedBy);
    winnerId = w ? w.id : null;
    youWon = !!(me && me.id !== forfeitedBy);
  } else {
    draw = players.length > 1 && players[0].score === players[1].score;
    winnerId = draw ? null : players[0].id;
    youWon = !!(me && winnerId === me.id);
  }

  const { text: banner, color: bannerColor } = outcomeBanner(solo, draw, youWon);

  const myEvents = (state.activity || []).filter((a) => me && a.playerId === me.id);
  const best = myEvents.length ? myEvents.reduce((a, b) => (b.points > a.points ? b : a)) : null;
  const worst = myEvents.length ? myEvents.reduce((a, b) => (b.points < a.points ? b : a)) : null;
  const health = Selectors.networkHealth(state);

  const decision = (label, e, color) => (
    <div className="kv">
      <span className="k">{label}</span>
      <span className="v" style={{ textAlign: 'right' }}>
        {e ? <>{e.text} <span className="mono" style={{ color }}>{e.points > 0 ? '+' : ''}{e.points}</span></> : '—'}
      </span>
    </div>
  );

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, position: 'relative', zIndex: 1 }}>
      <div className="panel" style={{ width: 'min(520px, 94vw)' }}>
        <div className="panel-head">
          <h2>Match Over</h2>
          <span className="corner">GAME {state.game}</span>
        </div>
        <div className="panel-pad">
          <div style={{ textAlign: 'center', margin: '6px 0 18px' }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: solo ? 26 : 40, fontWeight: 700, color: bannerColor, letterSpacing: '.04em' }}>{banner}</div>
            {forfeited && (
              <div style={{ color: 'var(--text-3)', fontSize: 12, marginTop: 4 }}>
                {youWon ? 'Your opponent forfeited the match.' : 'You forfeited the match.'}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 18 }}>
            {players.map((p, i) => {
              const winner = p.id === winnerId;
              return (
                <div key={p.id} className="inc-mini"
                  style={{ borderLeftColor: winner ? 'var(--good)' : 'var(--hair)', background: p.you ? 'var(--accent-soft)' : 'var(--inset)' }}>
                  <div className="inc-hdr">
                    <span className="t">
                      <span className="rank" style={{ marginRight: 8, color: winner ? 'var(--accent)' : 'var(--text-3)' }}>#{i + 1}</span>
                      {p.name}{p.you && <span style={{ color: 'var(--accent)' }}> (you)</span>}
                      {winner && <span className="tag good" style={{ marginLeft: 8 }}>WINNER</span>}
                    </span>
                    <span className="chip" style={{ color: 'var(--accent)' }}><b>{p.score.toLocaleString()}</b> PTS</span>
                  </div>
                  <div className="m" style={{ display: 'flex', justifyContent: 'space-between', marginTop: 5, fontSize: 10.5, color: 'var(--text-3)' }}>
                    <span>{p.resolved} resolved</span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="panel" style={{ background: 'var(--inset)', padding: 'var(--pad)', marginBottom: 16 }}>
            <div className="kv"><span className="k">Your final network health</span><span className="v mono">{health}%</span></div>
            {solo && <div className="kv"><span className="k">Tier reached</span><span className="v mono">{state.tier}</span></div>}
            {decision('Best decision', best, 'var(--good)')}
            {decision('Worst decision', worst, 'var(--crit)')}
          </div>

          <button className="btn primary" style={{ width: '100%', justifyContent: 'center' }} onClick={onExit}>
            Return to lobby
          </button>
        </div>
      </div>
    </div>
  );
}

GameOver.propTypes = {
  state: PropTypes.object.isRequired,
  onExit: PropTypes.func.isRequired,
};
