# 3D Network Map — integration notes

The flat SVG map has been replaced with a **Three.js** cell-tower scene.
Same component contract, so nothing else in the app changes.

## 1. Install the one new dependency

```bash
npm install three
```

That's the only npm install. The map uses three post-processing add-ons for the
bloom glow (`three/addons/postprocessing/...`) — those ship **inside** the
`three` package, so there's nothing else to install. The orbit camera is
hand-rolled (no OrbitControls).

## 2. Files

| File | Change |
|------|--------|
| `src/NetworkMap3D.jsx` | **NEW** — the Three.js map component (`export function NetworkMap`) |
| `src/ui.jsx` | now `import { NetworkMap } from './NetworkMap3D.jsx'` and re-exports it; old 2D `NetworkMap` removed |
| `src/styles.css` | added `.map3d` / `.map3d-label` / `.map3d-hint` rules |

`screens.jsx`, `store.js`, `App.jsx`, etc. are untouched — they still import
`NetworkMap` from `ui.jsx` exactly as before.

## 3. Props (unchanged)

```jsx
<NetworkMap
  cells={state.cells}     // [{ id, health (0–100), x (0–100), y (0–100), users, … }]
  links={state.links}     // [[cellIdA, cellIdB], …]
  selectedId={sel}
  onSelect={(id) => …}
  height={520}
/>
```

`cell.x` / `cell.y` (the 0–100 grid your store already produces) place each
tower on the plane; `cell.health` drives the colour (green ≥80, amber 50–79,
red <50, grey 0) and the critical pulse. No data-shape changes needed.

## 4. Interaction

- **Drag** to orbit, **scroll** to zoom, **click a tower** (or its label) to
  select → fires `onSelect(cellId)`, same as the old map.
- Auto-rotates gently until you grab it.
- Each tower is a 3-leg **lattice truss** with antenna panels + a beacon; it
  shows a `Cell-NN + health%` label and a coverage ring.
- **Bloom** makes the beacons/panels glow; criticals pulse and bloom brightest.
- **Signal pings** continuously stream along the links (green normally, amber/red
  when an endpoint is degraded/critical) to convey live traffic.

## 5. Tuning knobs (top of `NetworkMap3D.jsx`, in the effect)

- Bloom: `new UnrealBloomPass(…, 0.95 /*strength*/, 0.55 /*radius*/, 0.82 /*threshold*/)`
- Ping rate / cap: `spawnAcc > 0.45` and `pings.length < 16`
- Tower spacing: `const SPREAD = 22`
- Auto-rotate speed: `orbit.theta += dt * 0.1`
