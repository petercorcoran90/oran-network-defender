import React from 'react';
import PropTypes from 'prop-types';
import { Icon } from './ui.jsx';

/* ============================================================
   Tutorial.jsx — a self-contained, scripted walk-through that
   teaches every fault: how the console works (help / man / run),
   how to read each diagnostic's output, and which remediation
   resolves it. Nothing here touches the backend — the incident
   and console are faked so each step is deterministic and you
   can never get stuck (every prompt has a "Show me" button).

   The commands and console output mirror the real engine
   (ConsoleRenderer + DiagnosticType + ActionType) so what you
   learn here transfers straight into a real match.
   ============================================================ */

// --- shared console output (kept in sync with the backend ConsoleRenderer) ---
const HELP = {
  congestion: "Commands for this incident (run 'man <command>' for its arguments):\n  kubectl logs\nUtility: help · man <command> · clear",
  degradation: "Commands for this incident (run 'man <command>' for its arguments):\n  traceroute\n  netconf get-config\n  kubectl rollout history\n  pm-query\nUtility: help · man <command> · clear",
  alarms: "Commands for this incident (run 'man <command>' for its arguments):\n  fmcli list-alarms\nUtility: help · man <command> · clear",
};

// rogue automation: an anomaly (lots of changes) vs idle (rules it out)
const INSPECT_BUSY = 'traffic-steering-xapp\n10:31 applied handover policy (batch 326)\n10:31 applied handover policy (batch 327)\n10:32 applied handover policy (batch 328)   <- 328 changes in 5 min';
const INSPECT_IDLE = 'traffic-steering-xapp\n10:05 reconcile: no action needed\nidle since 10:05 (no recent changes)';
const ALARMS_STORM = '40 active alarms on cell-07\nCRITICAL  LinkDown          (root)\nMAJOR     HighBER           -> correlates to LinkDown\ncorrelation score 0.92  (a real fault is being masked)';
const ALARMS_QUIET = '1 active alarm on cell-07\nWARNING   ThresholdCrossed  (flapping x31, auto-cleared)\nKPIs nominal · correlation score 0.04';

// Each lesson: an incident, then a script of steps. A step with `expect` makes the
// player type a command (tokens that must all appear); a step without it is narration.
const LESSONS = [
  {
    group: 'Congestion', title: 'Cell overload',
    blurb: 'User load and latency are climbing on a cell. Congestion looks the same whether a cell is genuinely overloaded or an automation loop is hammering it — so investigate before you act.',
    metrics: [{ k: 'User Load', v: '92%' }, { k: 'Latency', v: '160 ms' }, { k: 'Packet Loss', v: '1%' }],
    steps: [
      { say: "Start by listing the checks that apply here. Type help.", expect: ['help'], output: HELP.congestion },
      { say: "Only one check applies: kubectl logs. You don't know its arguments yet — ask the manual. Type man kubectl logs.", expect: ['man', 'kubectl', 'logs'], output: 'kubectl logs deploy/traffic-steering\n  Investigates: Rogue automation.' },
      { say: "Now run it. Type kubectl logs deploy/traffic-steering.", expect: ['kubectl', 'logs', 'deploy/traffic-steering'], output: INSPECT_IDLE },
      { say: "The automation is idle — no recent changes. That rules OUT rogue automation. With the only other cause in this group eliminated, it's a genuine Cell overload. The fix is to rebalance traffic. Type rrmctl rebalance --cell o-ru-07.", expect: ['rrmctl', 'rebalance', '--cell'], output: '✓ Rebalance Traffic applied — load shifted to neighbours, incident resolved.' },
      { say: "Resolved. Lesson: when the automation check comes back idle, congestion is a real overload → Rebalance Traffic.", last: true },
    ],
  },
  {
    group: 'Congestion', title: 'Rogue automation',
    blurb: 'Same congestion symptoms as before — but this time something keeps making things worse on its own.',
    metrics: [{ k: 'User Load', v: '80%' }, { k: 'Latency', v: '120 ms' }, { k: 'Config', v: 'DRIFT' }],
    steps: [
      { say: "Same group, same check. Run the investigation: type kubectl logs deploy/traffic-steering. (Type help or man kubectl logs first if you want a refresher.)", expect: ['kubectl', 'logs', 'deploy/traffic-steering'], output: INSPECT_BUSY },
      { say: "328 policy changes in 5 minutes — the automation xApp is in a loop. That CONFIRMS rogue automation. Stop it: type ricctl xapp disable traffic-steering.", expect: ['ricctl', 'xapp', 'disable'], output: '✓ Disable Automation applied — the rogue xApp is stopped, incident resolved.' },
      { say: "Resolved. Lesson: a flood of recent automation changes → Disable Automation, not Rebalance.", last: true },
    ],
  },
  {
    group: 'Service degradation', title: 'Transport link fault',
    blurb: 'A cell is dropping packets with rising latency. Four different faults present as "service degradation" — you get two checks here, so choose well.',
    metrics: [{ k: 'Packet Loss', v: '22%' }, { k: 'Latency', v: '140 ms' }, { k: 'Signal', v: 'OK' }],
    steps: [
      { say: "Heavy packet loss points at the fronthaul link. Check the path: type man traceroute to learn the command.", expect: ['man', 'traceroute'], output: 'traceroute o-ru\n  Investigates: Transport link fault.' },
      { say: "Run it against the radio unit. Type traceroute o-ru.", expect: ['traceroute', 'o-ru'], output: 'traceroute to o-ru-07 (10.42.7.7), 30 hops max\n 1  o-du-gw        0.2 ms\n 2  fronthaul-sw1  0.4 ms   14% loss\n 3  o-ru-07        2.8 ms   57% loss' },
      { say: "57% loss on the last hop to the radio unit — the transport link is failing. This isn't something you fix from here. Escalate it: type ticket open --team transport --priority p1.", expect: ['ticket', 'open', '--team', '--priority'], output: '✓ Escalate applied — raised to the transport team, incident resolved.' },
      { say: "Resolved. Lesson: loss concentrated on the fronthaul hop → Escalate to transport (don't restart the cell).", last: true },
    ],
  },
  {
    group: 'Service degradation', title: 'Neighbour config change',
    blurb: 'Packet loss crept up shortly after a neighbouring cell was reconfigured.',
    metrics: [{ k: 'Packet Loss', v: '14%' }, { k: 'Latency', v: '70 ms' }, { k: 'Config', v: 'CHANGED' }],
    steps: [
      { say: "The config flag is CHANGED — compare running config against baseline. Type man netconf get-config.", expect: ['man', 'netconf', 'get-config'], output: 'netconf get-config o-du\n  Investigates: Recent neighbour config change.' },
      { say: "Run it. Type netconf get-config o-du.", expect: ['netconf', 'get-config', 'o-du'], output: '--- baseline\n+++ running\n@@ cell-07 @@\n- pci: 211\n+ pci: 207\n  neighbor-list changed 18 min ago by cm-batch-job' },
      { say: "A batch job changed the PCI 18 minutes ago — that CONFIRMS a neighbour config change. Roll it back: type netconf edit-config --rollback.", expect: ['netconf', 'edit-config', '--rollback'], output: '✓ Rollback Config applied — previous known-good config restored, incident resolved.' },
      { say: "Resolved. Lesson: a recent config diff → Rollback Config.", last: true },
    ],
  },
  {
    group: 'Service degradation', title: 'Software-upgrade fault',
    blurb: 'Packet loss appeared right after a software upgrade window.',
    metrics: [{ k: 'Packet Loss', v: '9%' }, { k: 'Latency', v: '60 ms' }, { k: 'Config', v: 'DRIFT' }],
    steps: [
      { say: "Suspect the recent upgrade — check the rollout history. Type man kubectl rollout history.", expect: ['man', 'kubectl', 'rollout', 'history'], output: 'kubectl rollout history deploy/o-du\n  Investigates: Software-upgrade fault.' },
      { say: "Run it. Type kubectl rollout history deploy/o-du.", expect: ['kubectl', 'rollout', 'history', 'deploy/o-du'], output: 'deploy/o-du\nREVISION  CHANGE-CAUSE\n4         o-du-l1:4.0.0 (22 min ago)\n3         o-du-l1:3.9.4\n! pods CrashLoopBackOff x7 since revision 4' },
      { say: "Revision 4 is crash-looping since the upgrade 22 minutes ago — that CONFIRMS a software-upgrade fault. Revert it: type kubectl rollout undo deploy/o-du.", expect: ['kubectl', 'rollout', 'undo', 'deploy/o-du'], output: '✓ Rollback Software applied — reverted to the previous build, incident resolved.' },
      { say: "Resolved. Lesson: a bad revision in the rollout history → Rollback Software (not Rollback Config).", last: true },
    ],
  },
  {
    group: 'Service degradation', title: 'Neighbour interference',
    blurb: 'Signal quality dropped while load stayed normal — likely radio interference from a neighbour.',
    metrics: [{ k: 'Signal', v: '55%' }, { k: 'User Load', v: '40%' }, { k: 'Latency', v: '45 ms' }],
    steps: [
      { say: "Low signal with normal load smells like radio interference — scan the air. Type man pm-query.", expect: ['man', 'pm-query'], output: 'pm-query cell --counters sinr,rsrp,pci\n  Investigates: Neighbour interference.' },
      { say: "Run the scan. Type pm-query cell --counters sinr,rsrp,pci.", expect: ['pm-query', '--counters'], output: 'cell-07 counters:\n  rsrp: -78 dBm   sinr: 2.1 dB (low)\n  neighbour PCI 207 on the same frequency -> PCI collision' },
      { say: "Low SINR and a same-frequency PCI collision — that CONFIRMS neighbour interference. Push through it: type rrmctl set-power --cell o-ru-07 --delta +3.", expect: ['rrmctl', 'set-power', '--cell', '--delta'], output: '✓ Increase Transmit Power applied — link margin restored, incident resolved.' },
      { say: "Resolved. Lesson: a PCI collision / low SINR → Increase Transmit Power.", last: true },
    ],
  },
  {
    group: 'Alarms', title: 'Alarm storm',
    blurb: 'A burst of alarms is firing on a cell. The flood can hide the one fault that actually matters — correlate them before reacting.',
    metrics: [{ k: 'Alarm Count', v: '40' }, { k: 'Signal', v: 'WARN' }, { k: 'Load', v: 'normal' }],
    steps: [
      { say: "Cut through the noise — correlate the alarms. Type man fmcli list-alarms.", expect: ['man', 'fmcli', 'list-alarms'], output: 'fmcli list-alarms\n  Investigates: Alarm storm masking a real fault.' },
      { say: "Run it. Type fmcli list-alarms.", expect: ['fmcli', 'list-alarms'], output: ALARMS_STORM },
      { say: "Correlation 0.92 — the alarms all trace back to one root fault that's being masked. That CONFIRMS an alarm storm. Suppress the correlated noise: type fmcli suppress --correlated.", expect: ['fmcli', 'suppress', '--correlated'], output: '✓ Filter Alarms applied — correlated noise suppressed so the real fault is visible, incident resolved.' },
      { say: "Resolved. Lesson: a high correlation score → Filter Alarms.", last: true },
    ],
  },
  {
    group: 'Alarms', title: 'False alarm',
    blurb: 'An alert fired, but the cell looks healthy. Sometimes the right move is to do nothing — but only after you check.',
    metrics: [{ k: 'Alarm Count', v: '1' }, { k: 'Signal', v: 'GOOD' }, { k: 'KPIs', v: 'nominal' }],
    steps: [
      { say: "Don't assume — correlate it. Type fmcli list-alarms. (man fmcli list-alarms if you need the refresher.)", expect: ['fmcli', 'list-alarms'], output: ALARMS_QUIET },
      { say: "A single flapping alarm that auto-cleared, correlation 0.04, KPIs nominal — that rules OUT a real storm. With nothing else in this group, it's a false alarm. Acting would only cost you. Acknowledge it and move on: type fmcli ack --no-action.", expect: ['fmcli', 'ack', '--no-action'], output: '✓ Ignore applied — acknowledged with no remediation, incident closed.' },
      { say: "Done. Lesson: low correlation + healthy KPIs → Ignore. The same action that's a trap on a real incident is the correct one here — context decides.", last: true },
    ],
  },
];

const norm = (s) => (s || '').trim().toLowerCase().replaceAll(/\s+/g, ' ');

export default function Tutorial({ onExit }) {
  const [lessonIdx, setLessonIdx] = React.useState(0);
  const [stepIdx, setStepIdx] = React.useState(0);
  const [lines, setLines] = React.useState([]);
  const [cmd, setCmd] = React.useState('');
  const [shake, setShake] = React.useState(false);
  const lineSeq = React.useRef(0);
  const termRef = React.useRef(null);

  const lesson = LESSONS[lessonIdx];
  const step = lesson.steps[stepIdx];
  const isLast = lessonIdx === LESSONS.length - 1;

  React.useEffect(() => {
    if (termRef.current) termRef.current.scrollTop = termRef.current.scrollHeight;
  }, [lines]);

  function pushLine(line) {
    setLines((l) => [...l, { id: lineSeq.current++, ...line }]);
  }

  function advance() {
    setStepIdx((i) => i + 1);
  }

  function nextLesson() {
    if (isLast) { onExit(); return; }
    setLessonIdx((i) => i + 1);
    setStepIdx(0);
    setLines([]);
    setCmd('');
  }

  // Accept the command if every required token is present (forgiving on spacing/order).
  function accept(typed) {
    pushLine({ t: '$ ' + typed, you: true });
    if (step.output) pushLine({ t: step.output });
    setCmd('');
    advance();
  }

  function onSubmit() {
    const typed = cmd.trim();
    if (!typed) return;
    const n = norm(typed);
    const ok = step.expect.every((tok) => n.includes(tok.toLowerCase()));
    if (ok) {
      accept(typed);
    } else {
      pushLine({ t: '$ ' + typed, you: true });
      pushLine({ t: "not quite — re-read the step, or press “Show me”." });
      setCmd('');
      setShake(true);
      globalThis.setTimeout(() => setShake(false), 400);
    }
  }

  function showMe() {
    // Reveal the canonical command for this step (the first plausible full command from its tokens).
    accept(step.expect.join(' '));
  }

  const progress = Math.round(((lessonIdx + (stepIdx / lesson.steps.length)) / LESSONS.length) * 100);

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, position: 'relative', zIndex: 1 }}>
      <div className="panel" style={{ width: 'min(640px, 96vw)' }}>
        <div className="panel-head">
          <h2>Tutorial · lesson {lessonIdx + 1} of {LESSONS.length}</h2>
          <button className="btn ghost" onClick={onExit}>Exit</button>
        </div>
        <div style={{ height: 4, background: 'var(--inset)' }}>
          <div style={{ height: '100%', width: progress + '%', background: 'var(--accent)', transition: 'width .3s' }} />
        </div>

        <div className="panel-pad">
          {/* faux incident */}
          <div style={{ marginBottom: 14 }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: 18, fontWeight: 600 }}>{lesson.title}</div>
            <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
              <span className="tag warn">{lesson.group}</span>
            </div>
            <div style={{ color: 'var(--text-3)', fontSize: 12.5, lineHeight: 1.6, marginTop: 8 }}>{lesson.blurb}</div>
            <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 8 }}>
              {lesson.metrics.map((m) => (
                <span key={m.k} style={{ fontSize: 11.5, color: 'var(--text-3)' }}>{m.k}: <b style={{ color: 'var(--text-2)' }}>{m.v}</b></span>
              ))}
            </div>
          </div>

          {/* console */}
          <div ref={termRef} className={shake ? 'shake' : ''}
            style={{ background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', padding: '10px 12px', fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-2)', minHeight: 90, maxHeight: 220, overflowY: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
            {lines.length === 0 && <div style={{ color: 'var(--text-3)' }}>Diagnostic console — follow the steps below.</div>}
            {lines.map((ln) => (
              <div key={ln.id} style={{ color: ln.you ? 'var(--accent)' : 'var(--text-2)' }}>{ln.t}</div>
            ))}
          </div>

          {/* teaching prompt */}
          <div style={{ color: 'var(--text)', fontSize: 13, lineHeight: 1.6, margin: '14px 0' }}>{step.say}</div>

          {step.expect ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--inset)', border: '1px solid var(--hair)', borderRadius: 'var(--r)', padding: '7px 11px' }}>
                <span style={{ color: 'var(--text-3)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>$</span>
                <input value={cmd} autoFocus aria-label="tutorial console"
                  onChange={(e) => setCmd(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') onSubmit(); }}
                  placeholder="type the command and press Enter"
                  style={{ flex: 1, background: 'none', border: 'none', color: 'var(--text)', outline: 'none', fontFamily: 'var(--font-mono)', fontSize: 12 }} />
              </div>
              <button className="btn ghost" onClick={showMe} style={{ marginTop: 10 }}>
                <Icon name="search" size={13} /> Show me
              </button>
            </>
          ) : (
            <button className="btn primary" onClick={nextLesson} style={{ width: '100%', justifyContent: 'center' }}>
              {isLast ? 'Finish the tutorial' : 'Next lesson →'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

Tutorial.propTypes = {
  onExit: PropTypes.func.isRequired,
};
