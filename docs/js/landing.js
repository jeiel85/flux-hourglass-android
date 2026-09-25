// Landing page: the live hero hourglass, the four material previews, the
// language toggle and the copy-link board.

import { SandSim, createSim } from './sim/index.js';
import { ObstacleLines } from './sim/lines.js';
import { DEFAULT_SETTINGS } from './sim/util.js';
import { Tilt } from './tilt.js';
import { formatSpaced, formatTitle } from './time.js';

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const root = document.documentElement;

const STRINGS = {
  en: { copied: 'Link copied', copyPrompt: 'Copy this link' },
  ko: { copied: '링크를 복사했어요', copyPrompt: '이 링크를 복사하세요' },
};
const t = (key) => (STRINGS[root.lang] || STRINGS.en)[key];

// ------------------------------------------------------------- language

document.getElementById('lang-toggle').addEventListener('click', () => {
  const next = root.lang === 'ko' ? 'en' : 'ko';
  root.lang = next;
  placeStream(); // copy re-wraps, which can move the word
  try {
    localStorage.setItem('fh.lang', next);
  } catch {
    /* not persisted */
  }
});

// ---------------------------------------------------------------- tilt

const tilt = new Tilt();
tilt.start();
const tiltBtn = document.getElementById('tilt-btn');
// Only offer the button where motion is gated (iOS) and nothing has arrived
// yet; Android delivers motion events without asking.
if (tilt.needsPermission) {
  setTimeout(() => {
    if (!tilt.hasSensor) tiltBtn.hidden = false;
  }, 1200);
  tiltBtn.addEventListener('click', async () => {
    if (await tilt.requestPermission()) tiltBtn.hidden = true;
  });
}

// ------------------------------------------------------------ surfaces

// A canvas + simulation pair that renders only while on screen.
class Surface {
  constructor(canvas, sim, { lines = null } = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.sim = sim;
    this.lines = lines;
    this.w = 0;
    this.h = 0;
    this.dpr = 1;
    this.visible = false;
    new ResizeObserver(() => this.resize()).observe(canvas);
    visibility.observe(canvas);
    canvas.__surface = this;
  }

  resize() {
    const r = this.canvas.getBoundingClientRect();
    if (!r.width || !r.height) return;
    this.w = r.width;
    this.h = r.height;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.canvas.width = Math.round(this.w * this.dpr);
    this.canvas.height = Math.round(this.h * this.dpr);
    this.sim.resize(this.w, this.h);
    this.lines?.clear();
    this.onResize?.();
  }

  frame(dt, env) {
    if (!this.w) return;
    env.dpr = this.dpr;
    env.lines = this.lines;
    this.lines?.update(dt);
    this.sim.update(dt, env);
    const { ctx } = this;
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = 1;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, this.w, this.h);
    this.lines?.draw(this.ctx, 3);
    this.sim.draw(ctx, env);
  }
}

const visibility = new IntersectionObserver(
  (entries) => {
    for (const e of entries) e.target.__surface.visible = e.isIntersecting;
  },
  { rootMargin: '80px' },
);

// --------------------------------------------------------------- hero

const hero = document.getElementById('hero');
const word = document.getElementById('word');
const clockEl = document.getElementById('hero-clock');
const heroSim = new SandSim({ maxFill: 0.22, seed: 7 });
const heroLines = new ObstacleLines();
const heroSurface = new Surface(document.getElementById('hero-canvas'), heroSim, { lines: heroLines });

// The stream leaks from just under the word's letters.
function placeStream() {
  const hr = hero.getBoundingClientRect();
  const wr = word.getBoundingClientRect();
  if (!hr.height) return;
  const fontPx = parseFloat(getComputedStyle(word).fontSize) || 0;
  heroSim.spawnY = Math.max(0, (wr.bottom - hr.top - fontPx * 0.1) / hr.height);
  heroSim.spawnX = 0.5;
}
heroSurface.onResize = placeStream;
document.fonts?.ready.then(placeStream);
// The word can move without the canvas resizing (copy re-wrapping above it).
new ResizeObserver(placeStream).observe(document.querySelector('.hero-copy'));

const introEnds = performance.now() + (reduceMotion ? 0 : 1300);
let lastMinuteLeft = 1;
let draining = 0;
let lastClock = '';

function heroEnv(dt, now) {
  const msInMinute = Date.now() % 60000;
  const remaining = 1 - msInMinute / 60000;
  // New minute: let the old pile sink away instead of snapping to empty.
  if (remaining > lastMinuteLeft + 0.5) draining = 1.6;
  lastMinuteLeft = remaining;
  if (draining > 0) {
    draining -= dt;
    heroSim.drain(Math.exp(-dt * 3.2));
  }
  const clock = formatTitle(60000 - msInMinute);
  if (clock !== lastClock) {
    clockEl.textContent = clock;
    lastClock = clock;
  }
  return {
    remaining,
    totalMs: 60000,
    gx: tilt.gx,
    gy: tilt.gy,
    settings: DEFAULT_SETTINGS,
    emit: now >= introEnds,
  };
}

// Drag across the stream to draw walls (sideways drags on touch, since
// vertical swipes scroll the page).
let heroPress = null;
hero.addEventListener('pointerdown', (e) => {
  if (e.button > 0 || e.target.closest('a, button')) return;
  heroPress = { id: e.pointerId, x: e.clientX, y: e.clientY };
});
hero.addEventListener('pointermove', (e) => {
  if (!heroPress || e.pointerId !== heroPress.id) return;
  const d = Math.hypot(e.clientX - heroPress.x, e.clientY - heroPress.y);
  if (d < 5) return;
  const r = hero.getBoundingClientRect();
  heroLines.add(heroPress.x - r.left, heroPress.y - r.top, e.clientX - r.left, e.clientY - r.top);
  heroPress.x = e.clientX;
  heroPress.y = e.clientY;
});
const heroRelease = (e) => {
  if (heroPress && e.pointerId === heroPress.id) heroPress = null;
};
hero.addEventListener('pointerup', heroRelease);
hero.addEventListener('pointercancel', heroRelease);

// -------------------------------------------------------------- cards

const cards = [...document.querySelectorAll('.card')].map((card, i) => {
  const mode = card.dataset.mode;
  const screen = card.querySelector('.screen');
  const sim = createSim(mode, { seed: 101 + i });
  const surface = new Surface(screen.querySelector('canvas'), sim);
  const state = {
    card,
    mode,
    screen,
    surface,
    sim,
    cycle: Number(card.dataset.cycle) * 1000,
    t: (i * 0.23 % 1) * Number(card.dataset.cycle) * 1000, // staggered start
    revealEl: screen.querySelector('.card-reveal b'),
  };

  // Press and hold to reveal the time left — the app's gesture.
  const press = (on) => screen.classList.toggle('pressed', on);
  screen.addEventListener('pointerdown', () => press(true));
  for (const ev of ['pointerup', 'pointercancel', 'pointerleave']) {
    screen.addEventListener(ev, () => press(false));
  }
  screen.addEventListener('contextmenu', (e) => e.preventDefault());
  return state;
});

const HOLD_END = 1400; // linger on the finished state before looping

function cardEnv(c, dt) {
  c.t += dt * 1000;
  if (c.t > c.cycle + HOLD_END) {
    c.t = 0;
    c.sim.reset();
  }
  const remainingMs = Math.max(0, c.cycle - c.t);
  if (c.screen.classList.contains('pressed')) c.revealEl.textContent = formatSpaced(remainingMs);
  return {
    remaining: remainingMs / c.cycle,
    totalMs: c.cycle,
    gx: tilt.gx,
    gy: tilt.gy,
    settings: DEFAULT_SETTINGS,
  };
}

// ---------------------------------------------------------------- loop

let lastT = 0;
function loop(now) {
  requestAnimationFrame(loop);
  const dt = lastT ? Math.min((now - lastT) / 1000, 0.03) : 0;
  lastT = now;
  tilt.update(dt);
  if (heroSurface.visible) heroSurface.frame(dt, heroEnv(dt, now));
  for (const c of cards) {
    if (c.surface.visible) c.surface.frame(dt, cardEnv(c, dt));
  }
}
requestAnimationFrame(loop);
document.addEventListener('visibilitychange', () => {
  lastT = 0;
});

// ---------------------------------------------------------- copy links

const toastEl = document.getElementById('toast');
let toastTimer = 0;
function toast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('on'), 2000);
}

document.getElementById('board').addEventListener('click', async (e) => {
  const btn = e.target.closest('.row-copy');
  if (!btn) return;
  const row = btn.closest('.row');
  const url = new URL(`app/?t=${row.dataset.t}&m=${row.dataset.m}`, location.href).href;
  try {
    await navigator.clipboard.writeText(url);
    toast(t('copied'));
  } catch {
    window.prompt(t('copyPrompt'), url);
  }
});

// ------------------------------------------------------- scroll reveal

if (!reduceMotion && 'IntersectionObserver' in window) {
  const targets = document.querySelectorAll(
    '.section-head, .cards, .verb, .board, .ledger, .get-card, .section .note',
  );
  const io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (!e.isIntersecting) continue;
        e.target.classList.add('in');
        io.unobserve(e.target);
      }
    },
    { rootMargin: '0px 0px -8% 0px' },
  );
  for (const el of targets) {
    el.classList.add('rise');
    io.observe(el);
  }
}
