// SAND — port of the Android ParticleSystem.
//
// Grains spawn in a thin stream, fall under the (tilt-aware) gravity vector,
// and deposit into a per-column height field. On landing a grain slides a few
// columns downhill; a slumping pass then relaxes steep slopes, biased by the
// sideways gravity so the heap pours toward whichever edge is lower. A
// baseline catch-up term guarantees the pile tracks elapsed time exactly.

import { clamp, rng, hash01, dpScale, perFrame, REF_PX_HEIGHT } from './util.js';

const BASE_RATE = 640; // grains / second on the Android reference screen

export class SandSim {
  /**
   * @param {object} [o]
   * @param {number} [o.maxFill=1]   Pile height (fraction of canvas) when time runs out.
   * @param {number} [o.spawnX=0.5]  Stream origin, fraction of width.
   * @param {number} [o.spawnY=0]    Stream origin, fraction of height.
   * @param {number} [o.seed]
   */
  constructor({ maxFill = 1, spawnX = 0.5, spawnY = 0, maxParticles = 4000, seed } = {}) {
    this.maxFill = maxFill;
    this.spawnX = spawnX;
    this.spawnY = spawnY;
    this.max = maxParticles;
    this.px = new Float32Array(maxParticles);
    this.py = new Float32Array(maxParticles);
    this.vx = new Float32Array(maxParticles);
    this.vy = new Float32Array(maxParticles);
    this.alpha = new Float32Array(maxParticles);
    this.count = 0;
    this.w = 0;
    this.h = 0;
    this.cols = 0;
    this.heights = new Float32Array(0);
    this.spawnAcc = 0;
    this.rand = rng(seed);
    this.surfaceSeed = 55;
    this.texture = null;
  }

  resize(w, h) {
    if (w === this.w && h === this.h) return;
    // ~180 columns across a phone, like the app. Capped so the slump pass
    // can still spread the heap across a wide desktop window.
    const cols = clamp(Math.round(w / 2), 90, 200);
    const next = new Float32Array(cols);
    // Keep the fill RATIO across resizes/rotations, like the Android app.
    if (this.h > 1 && this.cols) {
      for (let i = 0; i < cols; i++) {
        const src = this.heights[Math.min(this.cols - 1, Math.floor((i * this.cols) / cols))];
        next[i] = clamp((src / this.h) * h, 0, h);
      }
    }
    this.heights = next;
    this.cols = cols;
    this.w = w;
    this.h = h;
    this.colW = w / cols;
    this.count = 0; // in-flight grains were in the old frame's pixels
    this.surfaceSeed = (Math.round(w) * 31 + Math.round(h)) | 0;
  }

  reset() {
    this.count = 0;
    this.heights.fill(0);
    this.spawnAcc = 0;
  }

  /** Mean pile height as a fraction of the canvas height. */
  fillLevel() {
    let sum = 0;
    for (let i = 0; i < this.cols; i++) sum += this.heights[i];
    return this.cols && this.h ? sum / this.cols / this.h : 0;
  }

  /** Sinks the whole pile by a factor — the landing hero uses it to "flip". */
  drain(factor) {
    for (let i = 0; i < this.cols; i++) this.heights[i] *= factor;
  }

  #kill(i) {
    const last = --this.count;
    if (i === last) return;
    this.px[i] = this.px[last];
    this.py[i] = this.py[last];
    this.vx[i] = this.vx[last];
    this.vy[i] = this.vy[last];
    this.alpha[i] = this.alpha[last];
  }

  update(dt, env) {
    const { w, h, cols, heights } = this;
    if (!w || !h || dt <= 0) return;
    const { remaining, totalMs, settings, lines } = env;
    const gx = Number.isFinite(env.gx) ? env.gx : 0;
    const gy = Number.isFinite(env.gy) ? env.gy : 9.81;
    const S = h / REF_PX_HEIGHT; // Android px → css px
    const u = this.colW / 6; // Android columns were 6 px wide; slopes scale with that
    const r = this.rand;
    const { px, py, vx, vy, alpha } = this;

    const ax = gx * 200 * S * settings.gravity;
    const ay = gy * 200 * S * settings.gravity;
    const stage = Math.min(w, h * 0.5);
    const rate = BASE_RATE * clamp(stage / 390, 0.4, 2.2);
    const fillH = h * this.maxFill;
    // Height one grain adds to its column so the pile reaches fillH exactly
    // when time runs out. Divided by density so the density slider changes
    // the look of the stream, not the pace of the timer.
    const increment = clamp(
      (fillH * cols) / (rate * settings.density * Math.max(totalMs, 1000) / 1000),
      0.004,
      60 * S,
    );

    // 1. Spawn (env.emit === false holds the stream, e.g. during an intro).
    if (remaining > 0 && env.emit !== false) {
      this.spawnAcc += rate * (1.5 - remaining) * settings.density * dt;
      let n = this.spawnAcc | 0;
      this.spawnAcc -= n;
      const cx = w * this.spawnX + gx * 4 * S;
      const y0 = h * this.spawnY;
      const jitter = Math.max(stage * 0.008, 1);
      while (n-- > 0 && this.count < this.max) {
        const i = this.count++;
        px[i] = cx + (r() - 0.5) * 2 * jitter;
        py[i] = y0 - (2 + r() * 6) * S;
        vx[i] = (r() - 0.5) * 20 * S;
        vy[i] = (320 + r() * 60) * S;
        alpha[i] = 0.78 + r() * 0.22;
      }
    }

    // 2. Integrate, collide, deposit.
    const dampX = Math.pow(0.995, dt * 60);
    const dampY = Math.pow(0.992, dt * 60);
    const radius = 1.5 + 0.85 * settings.size;
    const hasLines = lines && lines.count > 0;
    // Downhill in the gravity frame: sideways pull tips the "level" line.
    // (The app adds this bias with the opposite sign, sending grains
    // against the tilt; the port slides them with it.)
    const slideBias = gx * 0.55 * u;
    const slideMin = 0.6 * u;
    for (let i = 0; i < this.count; ) {
      const ox = px[i];
      const oy = py[i];
      vx[i] = (vx[i] + ax * dt) * dampX;
      vy[i] = (vy[i] + ay * dt) * dampY;
      px[i] += vx[i] * dt;
      py[i] += vy[i] * dt;

      if (hasLines && lines.collide(px[i], py[i], vx[i], vy[i], radius, ox, oy)) {
        const o = lines.out;
        px[i] = o[0];
        py[i] = o[1];
        vx[i] = o[2];
        vy[i] = o[3];
      }

      // Side walls: under strong tilt grains hit the edge and slide down it
      // instead of vanishing off-screen.
      if (px[i] < 0) {
        px[i] = 0;
        vx[i] = -vx[i] * 0.3;
      } else if (px[i] > w) {
        px[i] = w;
        vx[i] = -vx[i] * 0.3;
      }
      if (py[i] < -h * 0.5) {
        this.#kill(i);
        continue;
      }

      let col = clamp(((px[i] / w) * cols) | 0, 0, cols - 1);
      if (py[i] >= h - heights[col] || py[i] >= h - 1) {
        for (let step = 0; step < 10; step++) {
          const here = heights[col];
          const left = col > 0 ? heights[col - 1] : Infinity;
          const right = col < cols - 1 ? heights[col + 1] : Infinity;
          const scoreL = here - left - slideBias;
          const scoreR = here - right + slideBias;
          if (scoreL > scoreR && scoreL > slideMin && col > 0) col--;
          else if (scoreR > scoreL && scoreR > slideMin && col < cols - 1) col++;
          else break;
        }
        heights[col] = Math.min(h, heights[col] + increment);
        this.#kill(i);
        continue;
      }
      i++;
    }

    // 3. Slumping — the heap reacts to tilt immediately. A column sheds sand
    // to a neighbour once the step between them exceeds the angle of repose
    // measured against the tilted gravity, so the surface settles
    // perpendicular to gravity (within repose) instead of screen-flat. The
    // thresholds may go negative under strong tilt — that is sand flowing
    // "uphill" on screen, i.e. downhill for real. (The app clamps them at
    // zero, which only ever flattens the heap.)
    const biasX = gx * 0.55;
    // More passes when each grain adds a lot of height (short timers), so the
    // heap still relaxes into a mound instead of towering into a spike.
    const basePasses = (Math.abs(gx) > 2 ? 8 : 6) + increment / u;
    const passes = clamp(Math.round(basePasses * dt * 60), 1, 48);
    const thrL = (1 + biasX) * u;
    const thrR = (1 - biasX) * u;
    for (let p = 0; p < passes; p++) {
      for (let i = 0; i < cols; i++) {
        if (i > 0) {
          const dl = heights[i] - heights[i - 1];
          if (dl > thrL) {
            const s = Math.min((dl - thrL) * 0.45, heights[i]);
            heights[i] -= s;
            heights[i - 1] = Math.min(h, heights[i - 1] + s);
          }
        }
        const dr = i < cols - 1 ? heights[i] - heights[i + 1] : -Infinity;
        if (dr > thrR) {
          const s = Math.min((dr - thrR) * 0.45, heights[i]);
          heights[i] -= s;
          heights[i + 1] = Math.min(h, heights[i + 1] + s);
        }
      }
      // Strong tilt: pour sideways aggressively.
      if (biasX < -0.6) {
        for (let i = 1; i < cols; i++) {
          const d = heights[i] - heights[i - 1];
          if (d > 0.05 * u) {
            const s = d * 0.25;
            heights[i] -= s;
            heights[i - 1] = Math.min(h, heights[i - 1] + s);
          }
        }
      } else if (biasX > 0.6) {
        for (let i = cols - 2; i >= 0; i--) {
          const d = heights[i] - heights[i + 1];
          if (d > 0.05 * u) {
            const s = d * 0.25;
            heights[i] -= s;
            heights[i + 1] = Math.min(h, heights[i + 1] + s);
          }
        }
      }
    }

    // 4. Baseline catch-up: the mean height never lags elapsed time. Lifting
    // every column evenly keeps the surface smooth. Also scrubs NaNs.
    let sum = 0;
    for (let i = 0; i < cols; i++) {
      const v = heights[i];
      if (!(v >= 0)) heights[i] = 0;
      else sum += v;
    }
    const gap = (1 - remaining) * fillH - sum / cols;
    if (gap > 0 && remaining < 0.999) {
      const lift = Math.min(gap * perFrame(0.03, dt), h * 0.004 * dt * 60);
      for (let i = 0; i < cols; i++) heights[i] = Math.min(h, heights[i] + lift);
    }
  }

  draw(ctx, env) {
    const { w, h, cols, heights, colW } = this;
    if (!w || !h) return;
    const size = env.settings.size;
    const D = dpScale(h);

    // Falling grains, batched into two alpha buckets.
    const gr = Math.max(0.85 * size * D, 0.6);
    const round = gr * (env.dpr || 1) > 2.2;
    for (let bucket = 0; bucket < 2; bucket++) {
      ctx.beginPath();
      for (let i = 0; i < this.count; i++) {
        if ((this.alpha[i] > 0.89 ? 1 : 0) !== bucket) continue;
        if (round) {
          ctx.moveTo(this.px[i] + gr, this.py[i]);
          ctx.arc(this.px[i], this.py[i], gr, 0, Math.PI * 2);
        } else {
          ctx.rect(this.px[i] - gr, this.py[i] - gr, gr * 2, gr * 2);
        }
      }
      ctx.fillStyle = bucket ? 'rgba(255,255,255,0.97)' : 'rgba(255,255,255,0.82)';
      ctx.fill();
    }

    // The pile: one smooth polygon through the column tops.
    let any = false;
    for (let i = 0; i < cols; i++) if (heights[i] > 0.5) { any = true; break; }
    if (!any) return;

    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(0, h - heights[0]);
    for (let i = 0; i < cols; i++) ctx.lineTo((i + 0.5) * colW, h - heights[i]);
    ctx.lineTo(w, h - heights[cols - 1]);
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fillStyle = '#ecebe7';
    ctx.fill();

    // Fine grain texture so the heap reads as sand, not a flat shape.
    const tex = this.#grainTexture(ctx);
    if (tex) {
      ctx.fillStyle = tex;
      ctx.fill();
    }

    // Crisp bright rim along the surface.
    ctx.beginPath();
    ctx.moveTo(0, h - heights[0]);
    for (let i = 0; i < cols; i++) ctx.lineTo((i + 0.5) * colW, h - heights[i]);
    ctx.lineTo(w, h - heights[cols - 1]);
    ctx.strokeStyle = 'rgba(255,255,255,0.95)';
    ctx.lineWidth = 1.2;
    ctx.stroke();

    // Loose surface grains: one jittered highlight per column (stable noise,
    // so it sparkles only when the surface moves).
    const sg = Math.max(0.6 * size * D, 0.55);
    ctx.beginPath();
    for (let i = 0; i < cols; i++) {
      const hi = heights[i];
      if (hi <= 0.5) continue;
      const x = i * colW + hash01(i, this.surfaceSeed) * colW;
      const y = h - hi - hash01(i + 7919, this.surfaceSeed) * 4 * D;
      if (y < 0) continue;
      ctx.moveTo(x + sg, y);
      ctx.arc(x, y, sg, 0, Math.PI * 2);
    }
    ctx.fillStyle = 'rgba(255,255,255,0.85)';
    ctx.fill();
  }

  #grainTexture(ctx) {
    if (this.texture !== null) return this.texture;
    this.texture = undefined;
    if (typeof document === 'undefined') return undefined;
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    const r = rng(1234);
    for (let i = 0; i < 900; i++) {
      const dark = r() < 0.78;
      g.fillStyle = dark
        ? `rgba(60,55,48,${(0.10 + r() * 0.18).toFixed(3)})`
        : `rgba(255,255,255,${(0.35 + r() * 0.4).toFixed(3)})`;
      const s = r() < 0.85 ? 1 : 2;
      g.fillRect((r() * 128) | 0, (r() * 128) | 0, s, s);
    }
    this.texture = ctx.createPattern(c, 'repeat') || undefined;
    return this.texture;
  }
}
