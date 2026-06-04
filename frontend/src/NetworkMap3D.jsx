import React from 'react';
import * as THREE from 'three';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { statusOf } from './store.js';

/* ============================================================
   NetworkMap3D.jsx — Three.js cell-tower map.
   Drop-in replacement for the old 2D <NetworkMap>; identical props:
     cells: [{ id, health, x, y, users, ... }]   (x,y are 0–100)
     links: [[cellIdA, cellIdB], …]
     selectedId, onSelect(id), height
   Lattice-truss towers stand on a dark grid plane, tinted by health
   status (green / amber / red / grey), pulse + bloom when critical,
   carry a floating "name + health%" label, and stream "signal pings"
   along the links. Orbit by dragging; zoom with the wheel; click to
   select.
   ============================================================ */

const STATUS_HEX = { good: 0x36e07f, warn: 0xf5a524, crit: 0xff4d4d, down: 0x6b7a70 };
const UP = new THREE.Vector3(0, 1, 0);

// soft radial sprite used for the beacon glow + signal pings
function makeGlowTexture() {
  const s = 128, cv = document.createElement('canvas');
  cv.width = cv.height = s;
  const ctx = cv.getContext('2d');
  const g = ctx.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  g.addColorStop(0, 'rgba(255,255,255,1)');
  g.addColorStop(0.25, 'rgba(255,255,255,0.55)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, s, s);
  return new THREE.CanvasTexture(cv);
}

// vertical gradient background (kept opaque so bloom composites cleanly)
function makeBg(top, mid, bot) {
  const cv = document.createElement('canvas');
  cv.width = 2; cv.height = 256;
  const ctx = cv.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, 0, 256);
  g.addColorStop(0, top);
  g.addColorStop(0.55, mid);
  g.addColorStop(1, bot);
  ctx.fillStyle = g; ctx.fillRect(0, 0, 2, 256);
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

// Day / night palettes for the 3D scene — driven by the CSS theme (data-theme on <html>).
const MAP_THEMES = {
  dark:  { bg: ['#141c22', '#0b0f13', '#08090b'], fog: 0x0a0c0e, ground: 0x14301f,
           gridA: 0x2c7a52, gridB: 0x163e2a, hemiSky: 0xbfe6d0, hemiGround: 0x0a0c0e,
           hemiInt: 0.8, keyInt: 0.65, rimInt: 0.22, steel: 0x9aa6ad, strut: 0x23282d,
           bloomStrength: 0.95, bloomThreshold: 0.82 },
  light: { bg: ['#cfe0d6', '#dde8e0', '#eef1ee'], fog: 0xe6ece8, ground: 0x86b394,
           gridA: 0x5fa078, gridB: 0xaecdbb, hemiSky: 0xffffff, hemiGround: 0xcfe0d6,
           hemiInt: 1.15, keyInt: 0.95, rimInt: 0.12, steel: 0x808a91, strut: 0x6b737b,
           bloomStrength: 0.35, bloomThreshold: 1.0 },
};
const mapTheme = () => MAP_THEMES[document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'];

// a thin cylinder strut between two points (for the lattice)
function strut(p1, p2, r, mat) {
  const dir = new THREE.Vector3().subVectors(p2, p1);
  const len = dir.length();
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, len, 5), mat);
  m.position.copy(p1).addScaledVector(dir, 0.5);
  m.quaternion.setFromUnitVectors(UP, dir.clone().normalize());
  return m;
}

// a 3-leg tapered lattice mast — returns { group, topY, topPts }
function buildLattice(mat) {
  const group = new THREE.Group();
  const legs = 3, hMast = 2.6, rb = 0.42, rt = 0.1, levels = 4;
  const pts = [];
  for (let k = 0; k <= levels; k++) {
    const y = (k / levels) * hMast;
    const r = rb + (rt - rb) * (k / levels);
    const ring = [];
    for (let i = 0; i < legs; i++) {
      const a = (i / legs) * Math.PI * 2 + Math.PI / 6;
      ring.push(new THREE.Vector3(Math.cos(a) * r, y, Math.sin(a) * r));
    }
    pts.push(ring);
  }
  for (let i = 0; i < legs; i++) for (let k = 0; k < levels; k++) group.add(strut(pts[k][i], pts[k + 1][i], 0.035, mat));   // legs
  for (let k = 0; k <= levels; k++) for (let i = 0; i < legs; i++) group.add(strut(pts[k][i], pts[k][(i + 1) % legs], 0.027, mat)); // rings
  for (let k = 0; k < levels; k++) for (let i = 0; i < legs; i++) group.add(strut(pts[k][i], pts[k + 1][(i + 1) % legs], 0.02, mat)); // diagonals
  return { group, topY: hMast, topPts: pts[levels] };
}

export function NetworkMap({ cells, links, selectedId, onSelect, height = 460 }) {
  const mountRef = React.useRef(null);
  const apiRef = React.useRef(null);
  const cellsRef = React.useRef(cells);
  const linksRef = React.useRef(links);
  const selRef = React.useRef(selectedId);
  const onSelRef = React.useRef(onSelect);
  cellsRef.current = cells;
  linksRef.current = links;
  selRef.current = selectedId;
  onSelRef.current = onSelect;

  React.useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return undefined;
    let W = mount.clientWidth || 600;
    let H = mount.clientHeight || height;

    let pal = mapTheme();
    const scene = new THREE.Scene();
    scene.background = makeBg(...pal.bg);
    scene.fog = new THREE.Fog(pal.fog, 26, 58);

    const camera = new THREE.PerspectiveCamera(45, W / H, 0.1, 200);
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
    renderer.setSize(W, H);
    renderer.domElement.style.cssText = 'display:block;width:100%;height:100%;cursor:grab;touch-action:none';
    mount.appendChild(renderer.domElement);

    // bloom composer
    const composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    const bloom = new UnrealBloomPass(new THREE.Vector2(W, H), pal.bloomStrength, 0.55, pal.bloomThreshold);
    composer.addPass(bloom);

    const labelLayer = document.createElement('div');
    labelLayer.style.cssText = 'position:absolute;inset:0;pointer-events:none;overflow:hidden';
    mount.appendChild(labelLayer);

    // ---- lighting ----
    const hemi = new THREE.HemisphereLight(pal.hemiSky, pal.hemiGround, pal.hemiInt);
    scene.add(hemi);
    const keyLight = new THREE.DirectionalLight(0xffffff, pal.keyInt);
    keyLight.position.set(8, 16, 10);
    scene.add(keyLight);
    const rim = new THREE.DirectionalLight(0x7fbfff, pal.rimInt);
    rim.position.set(-10, 6, -8);
    scene.add(rim);

    // ---- ground: a flat gridded pad where the towers stand, with gentle green hills beyond it ----
    const groundGeo = new THREE.PlaneGeometry(90, 90, 64, 64);
    const gpos = groundGeo.attributes.position;
    for (let i = 0; i < gpos.count; i++) {
      const x = gpos.getX(i);
      const y = gpos.getY(i); // plane-local Y maps to world Z after the -90° rotation
      // ramp stays 0 under the towers (r < 28) so nothing floats, then rises toward the edges
      const ramp = Math.max(0, (Math.hypot(x, y) - 28) / 17);
      const h = ramp * (Math.sin(x * 0.18) * Math.cos(y * 0.16) * 1.6 + Math.sin(x * 0.07 + y * 0.05) * 0.9);
      gpos.setZ(i, h); // plane-local Z becomes world height
    }
    groundGeo.computeVertexNormals();
    const ground = new THREE.Mesh(
      groundGeo,
      new THREE.MeshStandardMaterial({ color: pal.ground, roughness: 1, metalness: 0 }) // earth
    );
    ground.rotation.x = -Math.PI / 2;
    scene.add(ground);
    let grid = new THREE.GridHelper(48, 24, pal.gridA, pal.gridB); // only over the flat tower pad
    grid.position.y = 0.02;
    scene.add(grid);

    const SPREAD = 22;
    const toWorld = (c) => new THREE.Vector3((c.x - 50) / 100 * SPREAD, 0, (c.y - 50) / 100 * SPREAD);

    const glowTex = makeGlowTexture();
    const towers = new Map();
    const pickMeshes = [];
    let linkLines = null;
    const steel = new THREE.MeshStandardMaterial({ color: pal.steel, roughness: 0.45, metalness: 0.7 });
    const dark = new THREE.MeshStandardMaterial({ color: pal.strut, roughness: 0.7, metalness: 0.3 });

    // Re-colour the whole scene when the UI theme flips (data-theme on <html>) — towers share
    // the steel/strut materials, so updating those recolours every tower at once.
    function applyMapTheme() {
      pal = mapTheme();
      const oldBg = scene.background;
      scene.background = makeBg(...pal.bg);
      if (oldBg && oldBg.dispose) oldBg.dispose();
      scene.fog.color.set(pal.fog);
      ground.material.color.set(pal.ground);
      scene.remove(grid); grid.geometry.dispose(); grid.material.dispose();
      grid = new THREE.GridHelper(48, 24, pal.gridA, pal.gridB); grid.position.y = 0.02; scene.add(grid);
      hemi.color.set(pal.hemiSky); hemi.groundColor.set(pal.hemiGround); hemi.intensity = pal.hemiInt;
      keyLight.intensity = pal.keyInt; rim.intensity = pal.rimInt;
      steel.color.set(pal.steel); dark.color.set(pal.strut);
      bloom.strength = pal.bloomStrength; bloom.threshold = pal.bloomThreshold;
    }
    const themeObserver = new MutationObserver(applyMapTheme);
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    // ---- build one tower ----
    function buildTower(cell) {
      const grp = new THREE.Group();
      grp.position.copy(toWorld(cell));

      const statMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 1, roughness: 0.4 });
      const beaconMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 2, roughness: 0.3 });
      const ringMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.18, side: THREE.DoubleSide, depthWrite: false });

      // foundation pad
      const pad = new THREE.Mesh(new THREE.CylinderGeometry(0.62, 0.78, 0.2, 12), dark);
      pad.position.y = 0.1;
      grp.add(pad);

      // lattice-truss mast
      const lat = buildLattice(steel);
      lat.group.position.y = 0.2;
      grp.add(lat.group);
      const topY = 0.2 + lat.topY;

      // small platform disc on top
      const plat = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.06, 6), dark);
      plat.position.y = topY;
      grp.add(plat);

      // 3 antenna panels around the top (status colour)
      const panels = [];
      for (let i = 0; i < 3; i++) {
        const p = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.46, 0.06), statMat);
        const a = (i / 3) * Math.PI * 2;
        p.position.set(Math.cos(a) * 0.24, topY + 0.02, Math.sin(a) * 0.24);
        p.lookAt(p.position.x * 4, topY, p.position.z * 4);
        grp.add(p);
        panels.push(p);
      }

      // beacon + glow
      const beacon = new THREE.Mesh(new THREE.SphereGeometry(0.15, 16, 16), beaconMat);
      beacon.position.y = topY + 0.4;
      grp.add(beacon);
      const glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, color: 0xffffff, transparent: true, opacity: 0.9, depthWrite: false, blending: THREE.AdditiveBlending }));
      glow.scale.set(1.5, 1.5, 1);
      glow.position.y = topY + 0.4;
      grp.add(glow);

      // coverage ring + selection ring
      const ring = new THREE.Mesh(new THREE.RingGeometry(1.25, 1.5, 48), ringMat);
      ring.rotation.x = -Math.PI / 2; ring.position.y = 0.04;
      grp.add(ring);
      const selRing = new THREE.Mesh(new THREE.RingGeometry(1.62, 1.8, 48), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false }));
      selRing.rotation.x = -Math.PI / 2; selRing.position.y = 0.05;
      grp.add(selRing);

      // invisible pick cylinder
      const pick = new THREE.Mesh(new THREE.CylinderGeometry(1.05, 1.05, 3.4, 8), new THREE.MeshBasicMaterial({ visible: false }));
      pick.position.y = 1.6; pick.userData.cellId = cell.id;
      grp.add(pick);
      pickMeshes.push(pick);

      scene.add(grp);

      const el = document.createElement('div');
      el.className = 'map3d-label';
      el.innerHTML = '<span class="nm"></span><span class="pct"></span>';
      el.addEventListener('pointerdown', (e) => { e.stopPropagation(); onSelRef.current && onSelRef.current(cell.id); });
      labelLayer.appendChild(el);

      return { grp, panels, statMat, beaconMat, glow, ringMat, selRing, el, top: new THREE.Vector3(), beaconY: topY + 0.4 };
    }

    function clearTowers() {
      towers.forEach((t) => { scene.remove(t.grp); t.el.remove(); });
      towers.clear();
      pickMeshes.length = 0;
      if (linkLines) { scene.remove(linkLines); linkLines.geometry.dispose(); linkLines = null; }
    }

    function buildTowers(list) {
      clearTowers();
      list.forEach((c) => towers.set(c.id, buildTower(c)));
      const segs = [];
      (linksRef.current || []).forEach(([a, b]) => {
        const ta = towers.get(a), tb = towers.get(b);
        if (!ta || !tb) return;
        segs.push(ta.grp.position.x, 0.06, ta.grp.position.z, tb.grp.position.x, 0.06, tb.grp.position.z);
      });
      if (segs.length) {
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.Float32BufferAttribute(segs, 3));
        linkLines = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: 0x2f6b4a, transparent: true, opacity: 0.5 }));
        scene.add(linkLines);
      }
    }
    buildTowers(cellsRef.current);

    // ---- signal pings travelling along links ----
    const pings = [];
    let spawnAcc = 0;
    function statusWorse(a, b) {
      const order = { good: 0, warn: 1, crit: 2, down: 1 };
      return (order[a] ?? 0) >= (order[b] ?? 0) ? a : b;
    }
    function spawnPing() {
      const list = linksRef.current || [];
      if (!list.length) return;
      const [a, b] = list[Math.floor(Math.random() * list.length)];
      const ta = towers.get(a), tb = towers.get(b);
      if (!ta || !tb) return;
      const ca = cellsRef.current.find((c) => c.id === a);
      const cb = cellsRef.current.find((c) => c.id === b);
      const st = statusWorse(ca ? statusOf(ca.health) : 'down', cb ? statusOf(cb.health) : 'down');
      const hex = st === 'crit' ? 0xff4d4d : st === 'warn' ? 0xf5a524 : 0x4fe39a;
      const flip = Math.random() < 0.5;
      const from = flip ? ta : tb, to = flip ? tb : ta;
      const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, color: hex, transparent: true, opacity: 0.95, depthWrite: false, blending: THREE.AdditiveBlending }));
      sp.scale.set(0.5, 0.5, 1);
      scene.add(sp);
      pings.push({ sp, fx: from.grp.position.x, fz: from.grp.position.z, tx: to.grp.position.x, tz: to.grp.position.z, t: 0, dur: 1.1 + Math.random() * 0.9 });
    }

    // ---- manual orbit camera ----
    const orbit = { target: new THREE.Vector3(0, 1.4, 0), radius: 26, theta: Math.PI * 0.25, phi: Math.PI * 0.30, autoRotate: true };
    let dragging = false, moved = 0, lx = 0, ly = 0;
    const dom = renderer.domElement;
    const onDown = (e) => { dragging = true; moved = 0; lx = e.clientX; ly = e.clientY; orbit.autoRotate = false; dom.style.cursor = 'grabbing'; dom.setPointerCapture?.(e.pointerId); };
    const onMove = (e) => {
      if (!dragging) return;
      const dx = e.clientX - lx, dy = e.clientY - ly;
      lx = e.clientX; ly = e.clientY; moved += Math.abs(dx) + Math.abs(dy);
      orbit.theta -= dx * 0.006;
      orbit.phi = Math.max(0.12, Math.min(Math.PI / 2 - 0.05, orbit.phi - dy * 0.005));
    };
    const onUp = (e) => {
      dom.style.cursor = 'grab';
      if (dragging && moved < 6) {
        const r = dom.getBoundingClientRect();
        const ndc = new THREE.Vector2(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
        const rc = new THREE.Raycaster();
        rc.setFromCamera(ndc, camera);
        const hit = rc.intersectObjects(pickMeshes, false)[0];
        if (hit) onSelRef.current && onSelRef.current(hit.object.userData.cellId);
      }
      dragging = false;
    };
    const onWheel = (e) => { e.preventDefault(); orbit.radius = Math.max(13, Math.min(44, orbit.radius * (1 + e.deltaY * 0.0011))); };
    dom.addEventListener('pointerdown', onDown);
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    dom.addEventListener('wheel', onWheel, { passive: false });

    // ---- animation loop ----
    const clock = new THREE.Clock();
    let raf = 0;
    function frame() {
      raf = requestAnimationFrame(frame);
      const dt = Math.min(0.05, clock.getDelta());
      const tNow = clock.elapsedTime;
      if (orbit.autoRotate) orbit.theta += dt * 0.1;

      camera.position.set(
        orbit.target.x + orbit.radius * Math.sin(orbit.phi) * Math.sin(orbit.theta),
        orbit.target.y + orbit.radius * Math.cos(orbit.phi),
        orbit.target.z + orbit.radius * Math.sin(orbit.phi) * Math.cos(orbit.theta)
      );
      camera.lookAt(orbit.target);

      towers.forEach((t, id) => {
        const cell = cellsRef.current.find((c) => c.id === id);
        const st = cell ? statusOf(cell.health) : 'down';
        const hex = STATUS_HEX[st] || STATUS_HEX.down;
        const selected = selRef.current === id;
        const pulse = st === 'crit' ? 0.5 + 0.5 * Math.sin(tNow * 6) : 1;

        t.statMat.color.setHex(hex); t.statMat.emissive.setHex(hex); t.statMat.emissiveIntensity = 0.8 + (st === 'crit' ? pulse * 0.9 : 0.2);
        t.beaconMat.color.setHex(hex); t.beaconMat.emissive.setHex(hex); t.beaconMat.emissiveIntensity = 1.4 + pulse * 1.6;
        t.glow.material.color.setHex(hex);
        t.glow.material.opacity = (st === 'crit' ? 0.5 + pulse * 0.5 : 0.85);
        const gs = 1.4 + (st === 'crit' ? pulse * 0.6 : 0);
        t.glow.scale.set(gs, gs, 1);
        t.ringMat.color.setHex(hex);
        t.ringMat.opacity = (st === 'crit' ? 0.18 + pulse * 0.25 : 0.16);

        t.selRing.material.opacity += ((selected ? 0.9 : 0) - t.selRing.material.opacity) * Math.min(1, dt * 10);
        const targetScale = selected ? 1.07 : 1;
        t.grp.scale.x += (targetScale - t.grp.scale.x) * Math.min(1, dt * 10);
        t.grp.scale.z = t.grp.scale.y = t.grp.scale.x;

        t.top.set(t.grp.position.x, 3.6, t.grp.position.z).project(camera);
        const visible = t.top.z < 1;
        const sx = (t.top.x * 0.5 + 0.5) * W;
        const sy = (-t.top.y * 0.5 + 0.5) * H;
        t.el.style.display = visible ? 'flex' : 'none';
        t.el.style.transform = `translate(${sx}px,${sy}px) translate(-50%,-130%)`;
        t.el.classList.toggle('sel', selected);
        const css = st === 'good' ? 'var(--good)' : st === 'warn' ? 'var(--warn)' : st === 'crit' ? 'var(--crit)' : 'var(--text-3)';
        const nm = t.el.querySelector('.nm'), pc = t.el.querySelector('.pct');
        if (nm.textContent !== id) nm.textContent = id;
        const pctTxt = (cell ? cell.health : 0) + '%';
        if (pc.textContent !== pctTxt) pc.textContent = pctTxt;
        pc.style.color = css;
      });

      // pings
      spawnAcc += dt;
      if (spawnAcc > 0.45 && pings.length < 16) { spawnAcc = 0; spawnPing(); }
      for (let i = pings.length - 1; i >= 0; i--) {
        const p = pings[i];
        p.t += dt / p.dur;
        if (p.t >= 1) { scene.remove(p.sp); p.sp.material.dispose(); pings.splice(i, 1); continue; }
        const x = p.fx + (p.tx - p.fx) * p.t;
        const z = p.fz + (p.tz - p.fz) * p.t;
        const y = 0.12; // ride along the link line on the ground — no arc/hop between towers
        p.sp.position.set(x, y, z);
        p.sp.material.opacity = 0.95 * Math.sin(Math.PI * p.t);
      }

      composer.render();
    }
    frame();

    // ---- resize ----
    const ro = new ResizeObserver(() => {
      W = mount.clientWidth || W; H = mount.clientHeight || H;
      camera.aspect = W / H; camera.updateProjectionMatrix();
      renderer.setSize(W, H); composer.setSize(W, H); bloom.setSize(W, H);
    });
    ro.observe(mount);

    apiRef.current = { buildTowers, idsKey: cellsRef.current.map((c) => c.id).join(',') };

    // ---- cleanup ----
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      themeObserver.disconnect();
      dom.removeEventListener('pointerdown', onDown);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      dom.removeEventListener('wheel', onWheel);
      pings.forEach((p) => { scene.remove(p.sp); p.sp.material.dispose(); });
      clearTowers();
      glowTex.dispose();
      composer.dispose();
      renderer.dispose();
      mount.removeChild(renderer.domElement);
      mount.removeChild(labelLayer);
      apiRef.current = null;
    };
  }, []);

  React.useEffect(() => {
    const api = apiRef.current;
    if (!api) return;
    const key = cells.map((c) => c.id).join(',');
    if (key !== api.idsKey) { api.idsKey = key; api.buildTowers(cells); }
  }, [cells, links]);

  return (
    <div className="map3d" ref={mountRef} style={{ position: 'relative', width: '100%', height }}>
      <div className="map3d-hint">drag to orbit · scroll to zoom · click a tower</div>
      {(!cells || cells.length === 0) && (
        <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center',
          pointerEvents: 'none', color: 'var(--text-3)', fontFamily: 'var(--font-mono)', fontSize: 13 }}>
          Linking to the network…
        </div>
      )}
    </div>
  );
}
