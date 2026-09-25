// LED — liquid-level dot matrix. The grid fills bottom-up with elapsed time;
// each column's level is offset by the sideways tilt so the lit surface
// levels itself like a liquid, and the partially lit boundary cells pulse.

import { clamp } from './util.js';

const ROWS = 32;

export class LedSim {
  constructor() {
    this.w = 0;
    this.h = 0;
    this.time = 0;
  }

  resize(w, h) {
    this.w = w;
    this.h = h;
    // Square cells: 16 columns on a phone (as in the app), more when wide.
    this.cols = clamp(Math.round((ROWS * w) / h * 1.08), 6, 96);
  }

  reset() {
    this.time = 0;
  }

  update(dt) {
    this.time += dt;
  }

  draw(ctx, env) {
    const { w, h, cols } = this;
    if (!w || !h) return;
    const cellW = w / cols;
    const cellH = h / ROWS;
    const dot = Math.min(cellW, cellH) * 0.62;
    const rad = dot * 0.2;
    const offX = (cellW - dot) / 2;
    const offY = (cellH - dot) / 2;

    const baseLevel = (1 - env.remaining) * ROWS;
    const tilt = clamp((env.gx || 0) / 9.81, -1, 1);
    const center = (cols - 1) / 2;
    const pulse = 0.85 + 0.15 * Math.sin(this.time * 3);

    const unlit = new Path2D();
    const lit = new Path2D();
    // Partially lit (surface) cells, batched by alpha quantised to 1/32.
    const edges = new Map();
    for (let row = 0; row < ROWS; row++) {
      const y = h - (row + 1) * cellH + offY;
      for (let col = 0; col < cols; col++) {
        const x = col * cellW + offX;
        const level = baseLevel + (tilt * 6 * (col - center)) / center;
        const fill = clamp(level - row, 0, 1);
        if (fill <= 0) roundRect(unlit, x, y, dot, dot, rad);
        else if (fill >= 1) roundRect(lit, x, y, dot, dot, rad);
        else {
          const a = clamp((0.06 + fill * 0.89) * pulse, 0.02, 0.95);
          const key = Math.round(a * 32);
          let path = edges.get(key);
          if (!path) edges.set(key, (path = new Path2D()));
          roundRect(path, x, y, dot, dot, rad);
        }
      }
    }

    ctx.fillStyle = 'rgba(255,255,255,0.06)';
    ctx.fill(unlit);

    ctx.fillStyle = 'rgba(255,255,255,0.95)';
    ctx.fill(lit);

    for (const [key, path] of edges) {
      ctx.fillStyle = `rgba(255,255,255,${(key / 32).toFixed(3)})`;
      ctx.fill(path);
    }
  }
}

function roundRect(path, x, y, w, h, r) {
  if (path.roundRect) path.roundRect(x, y, w, h, r);
  else path.rect(x, y, w, h);
}
