// FIRE — a campfire that blazes at the start and burns down to glowing
// embers as time runs out. Flames stop at 80 % elapsed; after that only
// sparks and the pulsing charcoal core remain, fading to dark at the end.
// Positions are normalised: x to a phone-shaped stage centred in the canvas,
// y to the canvas height (as in the app).

import { clamp, rng, dpScale } from './util.js';

const TAU = Math.PI * 2;

// Flame colour by life ratio (white core → yellow → orange → deep → smoke).
// Alphas are lower than the app's because the web draws flames additively:
// overlaps build the white-hot core on their own.
const FLAME_STOPS = [
  [0.1, [255, 244, 214], 0.5],
  [0.3, [255, 214, 70], 0.5],
  [0.58, [255, 140, 20], 0.46],
  [0.8, [230, 72, 0], 0.36],
  [Infinity, [90, 30, 10], 0.2],
];
const SPARK_RGB = [255, 249, 196];

export class FireSim {
  constructor({ seed } = {}) {
    this.rand = rng(seed);
    this.w = 0;
    this.h = 0;
    this.sprites = null;
    this.reset();
  }

  resize(w, h) {
    this.w = w;
    this.h = h;
    this.stageW = Math.min(w, h * 0.62);
    this.stageX = (w - this.stageW) / 2;
  }

  reset() {
    this.particles = [];
    this.time = 0;
  }

  update(dt, env) {
    if (!this.w || dt <= 0) return;
    const r = this.rand;
    const prog = 1 - env.remaining;
    const gs = clamp(env.settings.gravity, 0.3, 2.5);
    const intensity = Math.max(1 - prog * 0.95, 0.05);
    this.time += dt;

    // Flames (until 80 % elapsed).
    const flame = prog < 0.8 ? clamp(1 - prog / 0.8, 0, 1) : 0;
    let count = Math.floor(22 * flame * dt * 60);
    if (count < 1 && flame > 0.1) count = 1;
    for (let i = 0; i < count; i++) {
      // Born across the whole bed of logs; the taper below pulls them into
      // a flame shape as they rise.
      const spread = 0.06 + flame * 0.16;
      const x0 = 0.5 + (r() - 0.5) * spread;
      this.particles.push({
        x: x0,
        x0,
        drift: 0,
        y: 0.9 + r() * 0.02,
        vx: (r() - 0.5) * 0.05,
        vy: -(0.2 + flame * 0.4 + r() * 0.22) * gs,
        life: 0,
        max: (0.7 + r() * 1.3) * (0.4 + flame * 0.6),
        size: 1.4 + r() * 2.8 * flame,
        spark: false,
      });
    }

    // Sparks — a steady crackle, busier once the flames are gone.
    const sparkRate = prog < 0.8 ? 1.5 : 4 * clamp(1 - (prog - 0.8) / 0.2, 0, 1);
    const want = sparkRate * dt * 60;
    let sparks = Math.floor(want);
    if (r() < want - sparks) sparks++;
    for (let i = 0; i < sparks; i++) {
      this.particles.push({
        x: 0.5 + (r() - 0.5) * 0.35,
        y: 0.9 + r() * 0.02,
        vx: (r() - 0.5) * 0.12,
        vy: -(0.4 + r() * 0.5) * gs,
        life: 0,
        max: 0.5 + r() * 0.8,
        size: 0.6 + r() * 1.2,
        spark: true,
      });
    }

    const tiltBias = (env.gx || 0) * 0.005 * gs;
    const lift = 0.2 * (1 - prog * 0.8) * gs;
    this.particles = this.particles.filter((p) => {
      p.y += p.vy * dt;
      if (p.spark) {
        p.x += p.vx * dt + tiltBias * dt;
        p.vx += (r() - 0.5) * 0.3 * dt * gs;
        p.vy += 0.08 * dt * gs;
      } else {
        p.vy -= lift * dt;
        // Flicker drift, then taper: a flame narrows to a tip as it rises.
        p.vx += (r() - 0.5) * 0.4 * dt * gs;
        p.drift += p.vx * dt + tiltBias * dt;
        const lr = Math.min(p.life / p.max, 1);
        p.x = 0.5 + (p.x0 - 0.5) * (1 - 0.85 * Math.sqrt(lr)) + p.drift;
      }
      p.life += dt;
      return p.life < p.max && p.y > -0.05 && p.x > -0.05 && p.x < 1.05;
    });

    const cap = prog < 0.8 ? Math.max(Math.floor(350 * intensity), 30) : 20;
    if (this.particles.length > cap) this.particles.splice(0, this.particles.length - cap);
  }

  #spriteFor(rgb) {
    if (!this.sprites) this.sprites = new Map();
    const key = rgb.join(',');
    let s = this.sprites.get(key);
    if (s) return s;
    s = document.createElement('canvas');
    s.width = s.height = 64;
    const g = s.getContext('2d');
    const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    grad.addColorStop(0, `rgba(${key},1)`);
    grad.addColorStop(0.22, `rgba(${key},0.62)`);
    grad.addColorStop(0.55, `rgba(${key},0.18)`);
    grad.addColorStop(1, `rgba(${key},0)`);
    g.fillStyle = grad;
    g.fillRect(0, 0, 64, 64);
    this.sprites.set(key, s);
    return s;
  }

  draw(ctx, env) {
    const { w, h, stageW, stageX } = this;
    if (!w || !h) return;
    const D = dpScale(h);
    const prog = 1 - env.remaining;
    const intensity = Math.max(1 - prog * 0.95, 0.05);
    const cx = stageX + stageW / 2;
    const logY = h * 0.91;
    const logLen = stageW * 0.35;
    const logW = 14 * D;

    // Warm ambient glow; shrinks as the fire dies.
    if (prog < 0.98) {
      const gr = stageW * 0.62 * intensity + 30 * D;
      const glow = ctx.createRadialGradient(cx, logY, 0, cx, logY, gr);
      glow.addColorStop(0, `rgba(255,120,40,${(0.28 * intensity).toFixed(3)})`);
      glow.addColorStop(0.35, `rgba(255,87,34,${(0.12 * intensity).toFixed(3)})`);
      glow.addColorStop(1, 'rgba(255,87,34,0)');
      ctx.fillStyle = glow;
      ctx.fillRect(cx - gr, logY - gr, gr * 2, gr * 2);
    }

    // Crossed logs.
    ctx.lineCap = 'round';
    ctx.lineWidth = logW;
    ctx.strokeStyle = '#211510';
    line(ctx, cx - logLen / 2, logY + 8 * D, cx + logLen / 2, logY - 8 * D);
    line(ctx, cx - logLen / 2, logY - 8 * D, cx + logLen / 2, logY + 8 * D);

    // Glowing charcoal core.
    const ember =
      prog < 0.8 ? 0.3 + prog * 0.5 : clamp(0.8 * (1 - (prog - 0.8) / 0.2), 0, 1);
    if (prog < 0.99 && ember > 0) {
      const p1 = Math.sin(this.time * 3) * 0.15 + 0.85;
      const p2 = Math.cos(this.time * 4.2) * 0.15 + 0.85;
      // Lit edge on the logs so they read against the dark.
      ctx.lineWidth = Math.max(1, 1.5 * D);
      ctx.strokeStyle = `rgba(255,140,60,${(ember * 0.35 * p1).toFixed(3)})`;
      line(ctx, cx - logLen / 2, logY + 8 * D - logW * 0.42, cx + logLen / 2, logY - 8 * D - logW * 0.42);
      line(ctx, cx - logLen / 2, logY - 8 * D - logW * 0.42, cx + logLen / 2, logY + 8 * D - logW * 0.42);

      ctx.lineWidth = logW * 0.35;
      ctx.strokeStyle = `rgba(230,81,0,${(ember * p1).toFixed(3)})`;
      line(ctx, cx - logLen * 0.35, logY + 5 * D, cx + logLen * 0.35, logY - 5 * D);
      ctx.strokeStyle = `rgba(255,61,0,${(ember * p2).toFixed(3)})`;
      line(ctx, cx - logLen * 0.35, logY - 5 * D, cx + logLen * 0.35, logY + 5 * D);

      const cr = 10 * D * (1 - prog * 0.3);
      const core = ctx.createRadialGradient(cx, logY, 0, cx, logY, cr * 2.2);
      const ca = ember * ((p1 + p2) / 2);
      core.addColorStop(0, `rgba(255,213,79,${ca.toFixed(3)})`);
      core.addColorStop(0.45, `rgba(255,213,79,${(ca * 0.8).toFixed(3)})`);
      core.addColorStop(1, 'rgba(255,160,40,0)');
      ctx.fillStyle = core;
      ctx.beginPath();
      ctx.arc(cx, logY, cr * 2.2, 0, TAU);
      ctx.fill();
    }

    // Flames and sparks, additively blended so overlaps burn hotter.
    if (!this.particles.length) return;
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const flamesOn = prog < 0.8 ? 1 : 0;
    const flameScale = clamp(stageW / 420, 0.55, 1.6);
    for (const p of this.particles) {
      const lr = p.life / p.max;
      const x = stageX + p.x * stageW;
      const y = p.y * h;
      let rgb;
      let a;
      let rad;
      if (p.spark) {
        rgb = SPARK_RGB;
        a = clamp(1 - lr, 0, 1);
        rad = p.size * D * 2.2;
      } else {
        // Big, soft and shrinking toward the tip, so the overlapping
        // sprites melt into one tapered flame body.
        const stop = FLAME_STOPS.find((s) => lr < s[0]);
        rgb = stop[1];
        a = (1 - lr * 0.6) * flamesOn * stop[2] * 0.5;
        rad = (9 + p.size * 4.5) * D * (1 - lr * 0.72) * flameScale;
      }
      if (a <= 0.01) continue;
      ctx.globalAlpha = a;
      ctx.drawImage(this.#spriteFor(rgb), x - rad, y - rad, rad * 2, rad * 2);
    }
    ctx.restore();
  }
}

function line(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}
