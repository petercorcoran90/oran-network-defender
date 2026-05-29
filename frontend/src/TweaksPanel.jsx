import React, { useState, useEffect, useRef, useCallback } from 'react';

/* ============================================================
   TweaksPanel.jsx — standalone (host-independent) tweak panel.
   Persists to localStorage; opens via floating button OR a
   window message { type: '__activate_edit_mode' } so the
   in-app "Open Tweaks" button keeps working.
   ============================================================ */
const LS_KEY = 'oran-tweaks';

export function useTweaks(defaults) {
  const [values, setValues] = useState(() => {
    try { return { ...defaults, ...JSON.parse(localStorage.getItem(LS_KEY) || '{}') }; }
    catch { return defaults; }
  });
  const setTweak = useCallback((keyOrEdits, val) => {
    const edits = typeof keyOrEdits === 'object' && keyOrEdits !== null ? keyOrEdits : { [keyOrEdits]: val };
    setValues((prev) => {
      const next = { ...prev, ...edits };
      try { localStorage.setItem(LS_KEY, JSON.stringify(next)); } catch {}
      return next;
    });
  }, []);
  return [values, setTweak];
}

const PANEL_CSS = `
.twk-fab{position:fixed;right:18px;bottom:18px;z-index:9998;width:46px;height:46px;border-radius:50%;
  background:var(--accent);color:#1a1306;border:none;cursor:pointer;display:grid;place-items:center;
  box-shadow:0 6px 22px -6px rgba(0,0,0,.7)}
.twk-panel{position:fixed;right:18px;bottom:18px;z-index:9999;width:278px;background:#0d0f12;
  border:1px solid rgba(150,170,160,.22);border-radius:8px;box-shadow:0 20px 50px -16px rgba(0,0,0,.8);
  font-family:var(--font-mono);color:var(--text);overflow:hidden}
.twk-hd{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;
  border-bottom:1px solid rgba(150,170,160,.13);font-family:var(--font-head);font-weight:600;
  text-transform:uppercase;letter-spacing:.05em;font-size:13px}
.twk-x{background:none;border:none;color:var(--text-3);cursor:pointer;font-size:15px}
.twk-body{padding:6px 14px 14px;max-height:70vh;overflow:auto}
.twk-sec{font-size:10px;letter-spacing:.12em;text-transform:uppercase;color:var(--text-3);margin:14px 0 8px}
.twk-row{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:9px 0}
.twk-row label{font-size:11.5px;color:var(--text-2)}
.twk-seg{display:flex;background:#0a0c0e;border:1px solid rgba(150,170,160,.13);border-radius:4px;padding:2px}
.twk-seg button{flex:1;background:none;border:none;color:var(--text-2);font-size:10.5px;padding:5px 7px;
  border-radius:2px;cursor:pointer;font-family:inherit;text-transform:uppercase}
.twk-seg button.on{background:color-mix(in oklab,var(--accent) 18%,transparent);color:var(--accent)}
.twk-chips{display:flex;gap:7px}
.twk-chips button{width:24px;height:24px;border-radius:5px;border:2px solid transparent;cursor:pointer;padding:0}
.twk-chips button.on{border-color:var(--text)}
.twk-slider{width:140px;accent-color:var(--accent)}
.twk-val{font-size:11px;color:var(--accent);min-width:34px;text-align:right}
`;

export function TweaksPanel({ title = 'Tweaks', children }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const onMsg = (e) => {
      const t = e?.data?.type;
      if (t === '__activate_edit_mode') setOpen(true);
      else if (t === '__deactivate_edit_mode') setOpen(false);
    };
    window.addEventListener('message', onMsg);
    return () => window.removeEventListener('message', onMsg);
  }, []);
  return (
    <>
      <style>{PANEL_CSS}</style>
      {!open && (
        <button className="twk-fab" title="Tweaks" onClick={() => setOpen(true)}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="3" /><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
          </svg>
        </button>
      )}
      {open && (
        <div className="twk-panel">
          <div className="twk-hd"><span>{title}</span><button className="twk-x" onClick={() => setOpen(false)}>✕</button></div>
          <div className="twk-body">{children}</div>
        </div>
      )}
    </>
  );
}

export function TweakSection({ label }) { return <div className="twk-sec">{label}</div>; }

export function TweakColor({ label, value, options, onChange }) {
  return (
    <div className="twk-row">
      <label>{label}</label>
      <div className="twk-chips">
        {options.map((c) => (
          <button key={c} className={value === c ? 'on' : ''} style={{ background: c }} onClick={() => onChange(c)} aria-label={c} />
        ))}
      </div>
    </div>
  );
}

export function TweakRadio({ label, value, options, onChange }) {
  const opts = options.map((o) => (typeof o === 'object' ? o : { label: o, value: o }));
  return (
    <div className="twk-row" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
      <label style={{ marginBottom: 6 }}>{label}</label>
      <div className="twk-seg">
        {opts.map((o) => (
          <button key={o.value} className={value === o.value ? 'on' : ''} onClick={() => onChange(o.value)}>{o.label}</button>
        ))}
      </div>
    </div>
  );
}

export function TweakSlider({ label, value, min = 0, max = 100, step = 1, unit = '', onChange }) {
  return (
    <div className="twk-row">
      <label>{label}</label>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <input className="twk-slider" type="range" min={min} max={max} step={step} value={value} onChange={(e) => onChange(Number(e.target.value))} />
        <span className="twk-val">{value}{unit}</span>
      </div>
    </div>
  );
}
