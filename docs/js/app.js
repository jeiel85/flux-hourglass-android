// Hourglass web app: setup → running → paused → finished, mirroring the
// Android TimerViewModel. Time is measured against Date.now() (wall clock),
// so a background tab or a sleeping laptop never makes the timer drift.

import { MODES, modeById, createSim } from './sim/index.js';
import { ObstacleLines } from './sim/lines.js';
import { DEFAULT_SETTINGS, clamp } from './sim/util.js';
import { Tilt } from './tilt.js';
import { Sound } from './audio.js';
import {
  parseShareParams,
  buildShareUrl,
  formatSpaced,
  formatTitle,
  splitSeconds,
  MAX_SECONDS,
} from './time.js';

const $ = (id) => document.getElementById(id);

// Pointer capture keeps a drag alive past the element's edge; it can throw
// for a pointer that is already gone, which must not abort the gesture.
function capture(el, pointerId) {
  try {
    el.setPointerCapture(pointerId);
  } catch {
    /* ignore */
  }
}

const store = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* private mode / storage blocked: settings just won't persist */
    }
  },
};

const KEY_LAST = 'fh.last';
const KEY_SETTINGS = 'fh.settings';
const BASE_TITLE = document.title;
const FINE_POINTER = matchMedia('(hover: hover) and (pointer: fine)').matches;

// ------------------------------------------------------------------ state

const saved = store.get(KEY_LAST, null);
const shared = parseShareParams(location.search);
const pick = { h: 0, m: 1, s: 0 };
if (saved && typeof saved === 'object') {
  pick.h = clamp(saved.h | 0, 0, 99);
  pick.m = clamp(saved.m | 0, 0, 59);
  pick.s = clamp(saved.s | 0, 0, 59);
}
if (shared.seconds) Object.assign(pick, splitSeconds(shared.seconds));
let mode = shared.mode || (saved && modeById(saved.mode) ? saved.mode : 'sand');

const settings = { ...DEFAULT_SETTINGS };
{
  const s = store.get(KEY_SETTINGS, {});
  if (s && typeof s === 'object') {
    settings.gravity = clamp(Number(s.gravity) || 1, 0.3, 2.5);
    settings.size = clamp(Number(s.size) || 1, 0.6, 2.2);
    settings.density = clamp(Number(s.density) || 1, 0.5, 2);
  }
}

const timer = { state: 'setup', totalMs: 0, endAt: 0, remainingMs: 0 };
const tilt = new Tilt();
const sound = new Sound();
const lines = new ObstacleLines();
let soundOn = false;
let sim = null;

// ------------------------------------------------------------ setup view

const modesEl = $('modes');
for (const m of MODES) {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = 'mode';
  b.setAttribute('role', 'radio');
  b.dataset.mode = m.id;
  b.innerHTML = `<span class="sp">${m.label}</span>`;
  b.addEventListener('click', () => setMode(m.id, true));
  modesEl.append(b);
}
modesEl.addEventListener('keydown', (e) => {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) return;
  e.preventDefault();
  const i = MODES.findIndex((m) => m.id === mode);
  const step = e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 1;
  setMode(MODES[(i + step + MODES.length) % MODES.length].id, true);
  modesEl.querySelector('[aria-checked="true"]').focus();
});

function setMode(id, smooth) {
  mode = id;
  for (const b of modesEl.children) {
    const on = b.dataset.mode === id;
    b.setAttribute('aria-checked', String(on));
    b.tabIndex = on ? 0 : -1;
    if (on) {
      // Glide the chosen object to the centre of the strip.
      const left = b.offsetLeft + b.offsetWidth / 2 - modesEl.clientWidth / 2;
      modesEl.scrollTo({ left, behavior: smooth ? 'smooth' : 'auto' });
    }
  }
}

$('presets').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-secs]');
  if (!b) return;
  Object.assign(pick, splitSeconds(Number(b.dataset.secs)));
  renderPicker();
});

const cols = [...document.querySelectorAll('.col')];
function renderPicker() {
  for (const col of cols) {
    const v = pick[col.dataset.unit];
    const num = col.querySelector('.num');
    num.textContent = String(v).padStart(2, '0');
    num.setAttribute('aria-valuenow', String(v));
  }
}
function bump(unit, delta) {
  const col = cols.find((c) => c.dataset.unit === unit);
  pick[unit] = clamp(pick[unit] + delta, 0, Number(col.dataset.max));
  renderPicker();
}

for (const col of cols) {
  const unit = col.dataset.unit;
  const max = Number(col.dataset.max);
  const num = col.querySelector('.num');
  col.querySelector('.up').addEventListener('click', () => bump(unit, 1));
  col.querySelector('.down').addEventListener('click', () => bump(unit, -1));

  num.addEventListener('keydown', (e) => {
    const map = { ArrowUp: 1, ArrowDown: -1, PageUp: 10, PageDown: -10 };
    if (e.key in map) bump(unit, map[e.key]);
    else if (e.key === 'Home') bump(unit, -pick[unit]);
    else if (e.key === 'End') bump(unit, max - pick[unit]);
    else return;
    e.preventDefault();
  });

  let wheelAcc = 0;
  col.addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      wheelAcc += e.deltaMode === 1 ? e.deltaY * 30 : e.deltaY;
      while (Math.abs(wheelAcc) >= 40) {
        bump(unit, wheelAcc < 0 ? 1 : -1);
        wheelAcc -= Math.sign(wheelAcc) * 40;
      }
    },
    { passive: false },
  );

  // Drag up/down on the column, like the app: up = more.
  let drag = null;
  col.addEventListener('pointerdown', (e) => {
    if (e.target.closest('.arrow')) return;
    drag = { id: e.pointerId, y: e.clientY, acc: 0 };
    capture(col, e.pointerId);
  });
  col.addEventListener('pointermove', (e) => {
    if (!drag || e.pointerId !== drag.id) return;
    drag.acc += drag.y - e.clientY;
    drag.y = e.clientY;
    while (Math.abs(drag.acc) >= 18) {
      bump(unit, drag.acc > 0 ? 1 : -1);
      drag.acc -= Math.sign(drag.acc) * 18;
    }
  });
  const end = () => (drag = null);
  col.addEventListener('pointerup', end);
  col.addEventListener('pointercancel', end);
}

$('btn-start').addEventListener('click', start);
$('btn-share').addEventListener('click', share);
$('btn-settings').addEventListener('click', openSettings);

// ----------------------------------------------------------------- canvas

const runEl = $('run');
const canvas = $('stage');
const ctx = canvas.getContext('2d');
const view = { w: 0, h: 0, dpr: 1 };

function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  view.w = rect.width;
  view.h = rect.height;
  view.dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.round(view.w * view.dpr);
  canvas.height = Math.round(view.h * view.dpr);
  sim?.resize(view.w, view.h);
  lines.clear();
  if (timer.state !== 'setup') render();
}
new ResizeObserver(resizeCanvas).observe(canvas);

function env() {
  return {
    remaining: timer.totalMs ? timer.remainingMs / timer.totalMs : 0,
    totalMs: timer.totalMs,
    gx: tilt.gx,
    gy: tilt.gy,
    settings,
    lines: modeById(mode).draw ? lines : null,
    dpr: view.dpr,
  };
}

function render(e = env()) {
  ctx.setTransform(view.dpr, 0, 0, view.dpr, 0, 0);
  ctx.globalCompositeOperation = 'source-over';
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, view.w, view.h);
  if (!sim) return;
  lines.draw(ctx, 3);
  sim.draw(ctx, e);
}

let raf = 0;
let lastT = 0;
function frame(t) {
  raf = requestAnimationFrame(frame);
  const dt = lastT ? Math.min((t - lastT) / 1000, 0.03) : 0;
  lastT = t;
  if (timer.state === 'running') {
    timer.remainingMs = Math.max(0, timer.endAt - Date.now());
    if (timer.remainingMs === 0) {
      finish();
      return;
    }
  }
  tilt.update(dt);
  const e = env();
  lines.update(dt);
  sim.update(dt, e);
  render(e);
  if (revealEl.classList.contains('on')) revealTime.textContent = formatSpaced(timer.remainingMs);
}
function startLoop() {
  cancelAnimationFrame(raf);
  lastT = 0;
  raf = requestAnimationFrame(frame);
}
function stopLoop() {
  cancelAnimationFrame(raf);
  raf = 0;
}

// ------------------------------------------------------ running controls

const revealEl = $('reveal');
const revealTime = $('reveal-time');
function setReveal(on) {
  if (on) revealTime.textContent = formatSpaced(timer.remainingMs);
  revealEl.classList.toggle('on', on);
}

let press = null;
runEl.addEventListener('pointerdown', (e) => {
  if (timer.state !== 'running' || press || e.target.closest('.pill')) return;
  capture(runEl, e.pointerId);
  press = { id: e.pointerId, x: e.clientX, y: e.clientY, travelled: 0, drawing: false };
  setReveal(true);
});
runEl.addEventListener('pointermove', (e) => {
  wake();
  if (!press || e.pointerId !== press.id) return;
  const d = Math.hypot(e.clientX - press.x, e.clientY - press.y);
  press.travelled += d;
  // Past the touch slop a drag becomes drawing (in modes that support it).
  if (!press.drawing && press.travelled > 8 && modeById(mode).draw) {
    press.drawing = true;
    setReveal(false);
  }
  if (press.drawing && d > 4) {
    const r = canvas.getBoundingClientRect();
    lines.add(press.x - r.left, press.y - r.top, e.clientX - r.left, e.clientY - r.top);
    press.x = e.clientX;
    press.y = e.clientY;
  } else if (!press.drawing) {
    press.x = e.clientX;
    press.y = e.clientY;
  }
});
const release = (e) => {
  if (!press || e.pointerId !== press.id) return;
  press = null;
  setReveal(false);
};
runEl.addEventListener('pointerup', release);
runEl.addEventListener('pointercancel', release);
runEl.addEventListener('contextmenu', (e) => e.preventDefault());

$('btn-pause').addEventListener('click', pause);
$('btn-reset').addEventListener('click', reset);
$('btn-sound').addEventListener('click', toggleSound);
$('btn-resume').addEventListener('click', resume);
$('btn-paused-reset').addEventListener('click', reset);
$('btn-again').addEventListener('click', reset);

// Desktop: hide chrome + cursor after a few still seconds.
let idleTimer = 0;
function wake() {
  runEl.classList.remove('idle');
  clearTimeout(idleTimer);
  if (timer.state === 'running') idleTimer = setTimeout(() => runEl.classList.add('idle'), 3500);
}

const hintEl = $('hint');
let hintTimer = 0;
function showHint() {
  const parts = ['Hold to see the time'];
  if (modeById(mode).draw) parts.push('drag to draw');
  if (FINE_POINTER) parts.push('← → to tilt', 'space to pause');
  else parts.push('tilt your phone');
  hintEl.textContent = parts.join(' · ');
  hintEl.classList.add('on');
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => hintEl.classList.remove('on'), 4000);
}

function updateSoundUi() {
  const b = $('btn-sound');
  b.hidden = !modeById(mode).sound || !sound.supported;
  b.setAttribute('aria-pressed', String(soundOn));
  b.firstElementChild.textContent = soundOn ? 'Sound' : 'Mute';
  b.setAttribute('aria-label', soundOn ? 'Sound on — tap to mute' : 'Sound off — tap to play');
}
function toggleSound() {
  soundOn = !soundOn;
  if (soundOn) sound.unlock();
  syncSound();
  updateSoundUi();
}
function syncSound() {
  if (timer.state === 'running' && soundOn && modeById(mode).sound) sound.play(mode);
  else sound.stop();
}

// ------------------------------------------------------------ transitions

function show(id) {
  for (const s of ['setup', 'run', 'paused', 'done']) $(s).hidden = true;
  runEl.classList.toggle('frozen', id === 'paused');
  if (id === 'paused' || id === 'done') $('run').hidden = id === 'done';
  $(id).hidden = false;
}

function totalPickSeconds() {
  return pick.h * 3600 + pick.m * 60 + pick.s;
}

function start() {
  const secs = totalPickSeconds();
  if (secs <= 0) {
    const p = $('picker');
    p.classList.remove('nudge');
    void p.offsetWidth;
    p.classList.add('nudge');
    return;
  }
  store.set(KEY_LAST, { ...pick, mode });
  // Gesture-bound unlocks: audio (for the chime) and iOS motion access.
  sound.unlock();
  tilt.requestPermission();
  if (!FINE_POINTER && !matchMedia('(display-mode: standalone)').matches) {
    document.documentElement.requestFullscreen?.({ navigationUI: 'hide' }).catch(() => {});
  }

  timer.totalMs = secs * 1000;
  timer.remainingMs = timer.totalMs;
  timer.endAt = Date.now() + timer.totalMs;
  timer.state = 'running';

  sim = createSim(mode);
  lines.clear();
  runEl.classList.toggle('can-draw', modeById(mode).draw);
  show('run');
  resizeCanvas();
  sim.resize(view.w, view.h);
  enterRunning();
  showHint();
}

function enterRunning() {
  startLoop();
  scheduleFinish();
  startTicker();
  syncSound();
  updateSoundUi();
  keepAwake(true);
  wake();
}

function pause() {
  if (timer.state !== 'running') return;
  timer.remainingMs = Math.max(0, timer.endAt - Date.now());
  timer.state = 'paused';
  press = null;
  setReveal(false);
  stopLoop();
  clearTimeout(finishTimer);
  stopTicker();
  syncSound();
  keepAwake(false);
  $('paused-time').textContent = formatSpaced(timer.remainingMs);
  const pct = Math.floor((Math.floor(timer.remainingMs / 1000) / (timer.totalMs / 1000)) * 100);
  $('paused-pct').textContent = `${pct} % remaining`;
  document.title = `Paused · ${formatTitle(timer.remainingMs)}`;
  show('paused');
  $('btn-resume').focus();
}

function resume() {
  if (timer.state !== 'paused') return;
  timer.endAt = Date.now() + timer.remainingMs;
  timer.state = 'running';
  show('run');
  enterRunning();
}

function finish() {
  timer.state = 'finished';
  timer.remainingMs = 0;
  stopLoop();
  clearTimeout(finishTimer);
  stopTicker();
  syncSound();
  keepAwake(false);
  press = null;
  setReveal(false);
  sound.chime();
  navigator.vibrate?.([0, 100, 150, 100, 150, 400]);
  document.title = `End · ${BASE_TITLE}`;
  setFavicon(0);
  show('done');
  $('btn-again').focus();
}

function reset() {
  timer.state = 'setup';
  timer.totalMs = timer.remainingMs = 0;
  stopLoop();
  clearTimeout(finishTimer);
  stopTicker();
  syncSound();
  keepAwake(false);
  press = null;
  setReveal(false);
  sim = null;
  lines.clear();
  document.title = BASE_TITLE;
  setFavicon(null);
  if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
  show('setup');
  setMode(mode, false);
  $('btn-start').focus();
}

// A one-shot timeout still fires on time in a background tab (rAF doesn't).
let finishTimer = 0;
function scheduleFinish() {
  clearTimeout(finishTimer);
  const ms = Math.max(0, timer.endAt - Date.now());
  finishTimer = setTimeout(() => {
    if (timer.state !== 'running') return;
    if (Date.now() >= timer.endAt) finish();
    else scheduleFinish();
  }, ms + 30);
}

// Tab title + favicon countdown, once a second.
let ticker = 0;
function startTicker() {
  stopTicker();
  const tick = () => {
    const rem = Math.max(0, timer.endAt - Date.now());
    document.title = `${formatTitle(rem)} · Hourglass`;
    $('sr-time').textContent = `${formatTitle(rem)} left`;
    setFavicon(rem / timer.totalMs);
  };
  tick();
  ticker = setInterval(tick, 1000);
}
function stopTicker() {
  clearInterval(ticker);
}

// -------------------------------------------------------------- favicon

const faviconEl = $('favicon');
const staticFavicon = faviconEl.href;
const iconCanvas = document.createElement('canvas');
iconCanvas.width = iconCanvas.height = 64;
let lastIconKey = '';
function setFavicon(remaining) {
  if (remaining == null) {
    faviconEl.type = 'image/svg+xml';
    faviconEl.href = staticFavicon;
    lastIconKey = '';
    return;
  }
  const key = Math.round(remaining * 40);
  if (key === lastIconKey) return;
  lastIconKey = key;
  const g = iconCanvas.getContext('2d');
  g.clearRect(0, 0, 64, 64);
  g.fillStyle = '#000';
  g.beginPath();
  g.roundRect ? g.roundRect(2, 2, 60, 60, 14) : g.rect(2, 2, 60, 60);
  g.fill();
  // Sand in each bulb (triangles, so fill height ~ sqrt of area share).
  g.fillStyle = '#fff';
  const top = Math.sqrt(clamp(remaining, 0, 1));
  const bot = 1 - Math.sqrt(clamp(1 - remaining, 0, 1));
  if (top > 0.02) {
    const y = 32 - 20 * top;
    const half = 16 * top;
    g.beginPath();
    g.moveTo(32 - half, y);
    g.lineTo(32 + half, y);
    g.lineTo(32, 32);
    g.fill();
  }
  if (bot < 0.98) {
    const y = 32 + 20 * bot;
    const half = 16 * bot;
    g.beginPath();
    g.moveTo(32 - half, y);
    g.lineTo(32 + half, y);
    g.lineTo(48, 52);
    g.lineTo(16, 52);
    g.fill();
  }
  g.strokeStyle = '#fff';
  g.lineWidth = 3;
  g.lineJoin = 'round';
  g.beginPath();
  g.moveTo(14, 11);
  g.lineTo(50, 11);
  g.lineTo(32, 32);
  g.lineTo(50, 53);
  g.lineTo(14, 53);
  g.lineTo(32, 32);
  g.closePath();
  g.stroke();
  faviconEl.type = 'image/png';
  faviconEl.href = iconCanvas.toDataURL('image/png');
}

// ------------------------------------------------------------- wake lock

let wakeLock = null;
let wantAwake = false;
async function keepAwake(on) {
  wantAwake = on;
  if (!('wakeLock' in navigator)) return;
  if (on && !wakeLock) {
    try {
      wakeLock = await navigator.wakeLock.request('screen');
      wakeLock.addEventListener('release', () => (wakeLock = null));
    } catch {
      wakeLock = null; // denied (e.g. battery saver) — the timer still works
    }
  } else if (!on && wakeLock) {
    wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return;
  if (wantAwake) keepAwake(true);
  if (timer.state === 'running' && Date.now() >= timer.endAt) finish();
});

// ----------------------------------------------------------------- share

const toastEl = $('toast');
let toastTimer = 0;
function toast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('on'), 2200);
}

async function share() {
  const secs = totalPickSeconds();
  if (secs <= 0) {
    toast('Set a time first');
    return;
  }
  const url = buildShareUrl(location.href, Math.min(secs, MAX_SECONDS), mode);
  const text = `${formatTitle(secs * 1000)} · ${modeById(mode).label} — a timer you can watch fall.`;
  if (navigator.share && !FINE_POINTER) {
    try {
      await navigator.share({ title: 'Flux Hourglass', text, url });
      return;
    } catch (err) {
      if (err && err.name === 'AbortError') return;
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    toast('Link copied');
  } catch {
    window.prompt('Copy this link', url);
  }
}

// -------------------------------------------------------------- settings

const dialog = $('settings');
const form = dialog.querySelector('form');
function syncSliders() {
  for (const input of form.querySelectorAll('input[type="range"]')) {
    input.value = settings[input.name];
    paintSlider(input);
  }
}
function paintSlider(input) {
  const pct = ((input.value - input.min) / (input.max - input.min)) * 100;
  input.style.setProperty('--pct', `${pct}%`);
  form.querySelector(`output[data-for="${input.name}"]`).textContent = `${Number(input.value).toFixed(1)}x`;
}
form.addEventListener('input', (e) => {
  const input = e.target;
  if (input.type !== 'range') return;
  settings[input.name] = Number(input.value);
  paintSlider(input);
  store.set(KEY_SETTINGS, settings);
});
function openSettings() {
  syncSliders();
  $('level-row').hidden = !tilt.hasSensor;
  if (typeof dialog.showModal === 'function') dialog.showModal();
  else dialog.setAttribute('open', '');
}
dialog.addEventListener('click', (e) => {
  if (e.target === dialog) dialog.close(); // tap on the backdrop
});
$('btn-level').addEventListener('click', () => {
  tilt.calibrate();
  toast('Level set');
});

// ------------------------------------------------------------- keyboard

window.addEventListener('keydown', (e) => {
  if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.altKey || dialog.open) return;
  const k = e.key.toLowerCase();
  if (timer.state === 'setup') {
    if (k === 'enter' && (e.target === document.body || e.target.getAttribute('role') === 'spinbutton')) {
      e.preventDefault();
      start();
    }
    return;
  }
  if (timer.state === 'running') {
    wake();
    if (k === ' ') {
      e.preventDefault();
      pause();
    } else if (k === 'r') reset();
    else if (k === 'm' && modeById(mode).sound) toggleSound();
    else if (k === 'f') toggleFullscreen();
    else if (k === 't' && !e.repeat) setReveal(true);
  } else if (timer.state === 'paused') {
    if (k === ' ') {
      e.preventDefault();
      resume();
    } else if (k === 'r') reset();
  } else if (timer.state === 'finished' && (k === ' ' || k === 'enter')) {
    e.preventDefault();
    reset();
  }
});
window.addEventListener('keyup', (e) => {
  if (e.key.toLowerCase() === 't') setReveal(false);
});

function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
  else document.documentElement.requestFullscreen?.().catch(() => {});
}

// ------------------------------------------------------------------ boot

tilt.start();
renderPicker();
setMode(mode, false);
updateSoundUi();

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
