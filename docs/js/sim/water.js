// WATER — a thin stream pours into a pool that fills to 45 % of the screen.
// The pool sloshes on a spring-damper driven by tilt, the stream leans with
// gravity, bubbles rise and pop at the surface, and droplets splash where the
// stream lands (and bounce off drawn lines).

import { clamp, rng, dpScale } from './util.js';

const TAU = Math.PI * 2;
const MAX_BUBBLES = 35;
const MAX_SPLASHES = 60;
const POOL_MAX = 0.45;

export class WaterSim {
  constructor({ seed } = {}) {
    this.rand = rng(seed);
    this.w = 0;
    this.h = 0;
    this.reset();
  }

  resize(w, h) {
    this.w = w;
    this.h = h;
  }

  reset() {
    this.phase = 0;
    this.slosh = 0;
    this.sloshV = 0;
    this.bubbles = [];
    this.splashes = [];
  }

  #pool(env) {
    const D = dpScale(this.h);
    const level = (1 - env.remaining) * POOL_MAX;
    const poolH = this.h * level;
    const lean = (env.gx || 0) * env.settings.gravity * 36 * D;
    return { D, poolH, surfaceY: this.h - poolH, lean, hitX: this.w / 2 + lean };
  }

  surfaceAt(x, surfaceY) {
    return surfaceY + (x / this.w - 0.5) * this.w * this.slosh;
  }

  update(dt, env) {
    const { w, h } = this;
    if (!w || !h || dt <= 0) return;
    const r = this.rand;
    const gs = env.settings.gravity;
    const { D, poolH, surfaceY, hitX } = this.#pool(env);

    this.phase = (this.phase + 3 * dt) % TAU;

    // Spring-damper slosh (k = 35, c = 6 — the app's constants).
    const target = clamp((-(env.gx || 0) * gs) / 9.81, -0.4, 0.4);
    this.sloshV += ((target - this.slosh) * 35 - this.sloshV * 6) * dt;
    this.slosh += this.sloshV * dt;

    // Bubbles rise from the floor, wobble, and pop at the surface.
    if (poolH > 14 * D && this.bubbles.length < MAX_BUBBLES && r() < 0.08 * dt * 60) {
      this.bubbles.push({
        x: r() * w,
        y: h + 4,
        r: (2 + r() * 4) * D,
        speed: (40 + r() * 60) * D,
        wob: 4 + r() * 8,
        wobScale: (3 + r() * 6) * D,
        ph: r() * TAU,
        a: 0.3 + r() * 0.4,
      });
    }
    this.bubbles = this.bubbles.filter((b) => {
      b.y -= b.speed * dt;
      b.ph += b.wob * dt;
      const x = b.x + Math.sin(b.ph) * b.wobScale;
      return b.y - b.r > this.surfaceAt(x, surfaceY) + 2;
    });

    // Splash droplets where the stream lands.
    if (env.remaining > 0 && r() < 0.25 * dt * 60) {
      const n = 1 + ((r() * 2) | 0);
      const y0 = this.surfaceAt(hitX, surfaceY);
      for (let k = 0; k < n && this.splashes.length < MAX_SPLASHES; k++) {
        this.splashes.push({
          x: hitX + (r() - 0.5) * 8 * D,
          y: y0 - 1,
          vx: (r() - 0.5) * 80 * D,
          vy: -(60 + r() * 130) * D,
          a: 0.8 + r() * 0.2,
        });
      }
    }
    const g = 260 * gs * D;
    const lines = env.lines;
    const radius = 2 + 1.5 * env.settings.size;
    this.splashes = this.splashes.filter((s) => {
      const ox = s.x;
      const oy = s.y;
      s.vy += g * dt;
      s.x += s.vx * dt;
      s.y += s.vy * dt;
      s.a -= 1.2 * dt;
      if (lines && lines.count && lines.collide(s.x, s.y, s.vx, s.vy, radius, ox, oy)) {
        [s.x, s.y, s.vx, s.vy] = lines.out;
      }
      const below = s.vy > 0 && s.y > this.surfaceAt(s.x, surfaceY) + 2;
      return s.a > 0 && !below && s.y < h && s.x > -10 && s.x < w + 10;
    });
  }

  draw(ctx, env) {
    const { w, h } = this;
    if (!w || !h) return;
    const { D, poolH, surfaceY, lean, hitX } = this.#pool(env);
    const ph = this.phase;
    const running = env.remaining > 0;
    const hitY = this.surfaceAt(hitX, surfaceY);

    // Stream: parabolic lean toward gravity plus a small ripple.
    if (running) {
      const seg = Math.max(24, Math.ceil(hitY / 4)); // fine enough for a smooth ripple
      const path = new Path2D();
      path.moveTo(w / 2, 0);
      for (let i = 1; i <= seg; i++) {
        const t = i / seg;
        const y = hitY * t;
        const x = w / 2 + Math.sin(y / (11 * D) - ph * 4) * 1.8 * D * (0.3 + 0.7 * t) + lean * t * t;
        path.lineTo(x, y);
      }
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.strokeStyle = 'rgba(0,200,255,0.07)';
      ctx.lineWidth = 16 * D;
      ctx.stroke(path);
      ctx.strokeStyle = 'rgba(0,210,255,0.22)';
      ctx.lineWidth = 6 * D;
      ctx.stroke(path);
      ctx.strokeStyle = 'rgba(255,255,255,0.9)';
      ctx.lineWidth = 2 * D;
      ctx.stroke(path);
    }

    if (poolH > 0.5) {
      const grow = clamp(poolH / (24 * D), 0, 1); // waves swell in as the pool forms
      const seg = 72;
      const back = new Path2D();
      const front = new Path2D();
      back.moveTo(0, h);
      front.moveTo(0, h);
      const rim = new Path2D();
      for (let i = 0; i <= seg; i++) {
        const t = i / seg;
        const x = t * w;
        const slosh = (t - 0.5) * w * this.slosh;
        const fy =
          surfaceY + slosh +
          (Math.sin(t * TAU * 1.5 + ph) * 10 + Math.cos(t * TAU * 2.8 - ph * 1.3) * 5) * D * grow;
        const by =
          surfaceY - 3 * D + slosh +
          (Math.sin(t * TAU * 1.8 - ph + 1.2) * 9 + Math.cos(t * TAU * 2.2 + ph * 0.8) * 4) * D * grow;
        back.lineTo(x, clamp(by, 0, h));
        front.lineTo(x, clamp(fy, 0, h));
        if (i === 0) rim.moveTo(x, clamp(fy, 0, h));
        else rim.lineTo(x, clamp(fy, 0, h));
      }
      back.lineTo(w, h);
      front.lineTo(w, h);
      back.closePath();
      front.closePath();

      ctx.fillStyle = 'rgba(0,92,151,0.27)';
      ctx.fill(back);

      const grad = ctx.createLinearGradient(0, surfaceY - 20 * D, 0, h);
      grad.addColorStop(0, 'rgba(0,201,255,0.46)');
      grad.addColorStop(0.35, 'rgba(0,150,220,0.36)');
      grad.addColorStop(1, 'rgba(0,40,90,0.42)');
      ctx.fillStyle = grad;
      ctx.fill(front);

      ctx.strokeStyle = 'rgba(170,240,255,0.55)';
      ctx.lineWidth = 1.2;
      ctx.stroke(rim);

      // Bubbles.
      ctx.lineWidth = Math.max(1, D);
      for (const b of this.bubbles) {
        const x = b.x + Math.sin(b.ph) * b.wobScale;
        ctx.strokeStyle = `rgba(255,255,255,${b.a.toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(x, b.y, b.r, 0, TAU);
        ctx.stroke();
        ctx.fillStyle = `rgba(255,255,255,${(b.a * 0.5).toFixed(3)})`;
        ctx.beginPath();
        ctx.arc(x - b.r * 0.3, b.y - b.r * 0.3, b.r * 0.4, 0, TAU);
        ctx.fill();
      }
    }

    // Soft glow where the stream meets the water.
    if (running) {
      const gr = 26 * D;
      const glow = ctx.createRadialGradient(hitX, hitY, 0, hitX, hitY, gr);
      glow.addColorStop(0, 'rgba(160,245,255,0.35)');
      glow.addColorStop(1, 'rgba(160,245,255,0)');
      ctx.fillStyle = glow;
      ctx.fillRect(hitX - gr, hitY - gr, gr * 2, gr * 2);
    }

    // Splash droplets.
    const sr = 1.5 * D * env.settings.size;
    for (const s of this.splashes) {
      ctx.fillStyle = `rgba(136,242,255,${clamp(s.a, 0, 1).toFixed(3)})`;
      ctx.beginPath();
      ctx.arc(s.x, s.y, sr, 0, TAU);
      ctx.fill();
    }
  }
}
