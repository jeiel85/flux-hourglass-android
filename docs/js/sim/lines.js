// Drag-to-draw obstacle lines. Each segment fades out over ~6 s (same decay
// rate as the Android app: alpha -= 0.16/s) and deflects grains and splashes
// with a damped elastic bounce.

const MAX_SEGMENTS = 360;
const FADE_PER_SEC = 0.16;
const ELASTICITY = 0.35;

export class ObstacleLines {
  constructor() {
    this.x1 = new Float32Array(MAX_SEGMENTS);
    this.y1 = new Float32Array(MAX_SEGMENTS);
    this.x2 = new Float32Array(MAX_SEGMENTS);
    this.y2 = new Float32Array(MAX_SEGMENTS);
    this.alpha = new Float32Array(MAX_SEGMENTS);
    this.count = 0;
    // Scratch output for collide(): [x, y, vx, vy]
    this.out = new Float32Array(4);
  }

  add(x1, y1, x2, y2) {
    if (this.count === MAX_SEGMENTS) this.#removeAt(0); // oldest first
    const i = this.count++;
    this.x1[i] = x1;
    this.y1[i] = y1;
    this.x2[i] = x2;
    this.y2[i] = y2;
    this.alpha[i] = 1;
  }

  clear() {
    this.count = 0;
  }

  update(dt) {
    for (let i = 0; i < this.count; ) {
      this.alpha[i] -= FADE_PER_SEC * dt;
      if (this.alpha[i] <= 0) this.#removeAt(i);
      else i++;
    }
  }

  // Keeps insertion order so the fade reads as a trail, not a shuffle.
  #removeAt(i) {
    const n = --this.count;
    this.x1.copyWithin(i, i + 1, n + 1);
    this.y1.copyWithin(i, i + 1, n + 1);
    this.x2.copyWithin(i, i + 1, n + 1);
    this.y2.copyWithin(i, i + 1, n + 1);
    this.alpha.copyWithin(i, i + 1, n + 1);
  }

  // Pushes a particle out of every segment it overlaps — or crossed since its
  // previous position (ox, oy), so fast grains can't tunnel through — and
  // reflects the inbound velocity component. Returns true when the particle
  // was touched; the corrected state is left in this.out.
  collide(px, py, vx, vy, radius, ox = px, oy = py) {
    let hit = false;
    const r2 = radius * radius;
    for (let i = 0; i < this.count; i++) {
      const x1 = this.x1[i];
      const y1 = this.y1[i];
      const abx = this.x2[i] - x1;
      const aby = this.y2[i] - y1;
      const len2 = abx * abx + aby * aby;
      if (len2 < 1e-4) continue;

      // Swept test: did the path (ox,oy)→(px,py) cross the segment?
      const mx = px - ox;
      const my = py - oy;
      const denom = mx * aby - my * abx;
      if (denom > 1e-6 || denom < -1e-6) {
        const qx = x1 - ox;
        const qy = y1 - oy;
        const s = (qx * aby - qy * abx) / denom; // along the path
        const u = (qx * my - qy * mx) / denom; // along the segment
        if (s >= 0 && s <= 1 && u >= 0 && u <= 1) {
          const len = Math.sqrt(len2);
          let nx = -aby / len;
          let ny = abx / len;
          if ((ox - x1) * nx + (oy - y1) * ny < 0) {
            nx = -nx; // face the side the particle came from
            ny = -ny;
          }
          px = ox + mx * s + nx * radius;
          py = oy + my * s + ny * radius;
          const dot = vx * nx + vy * ny;
          if (dot < 0) {
            vx = (vx - 2 * dot * nx) * ELASTICITY;
            vy = (vy - 2 * dot * ny) * ELASTICITY;
          }
          ox = px;
          oy = py;
          hit = true;
          continue;
        }
      }

      let t = ((px - x1) * abx + (py - y1) * aby) / len2;
      t = t < 0 ? 0 : t > 1 ? 1 : t;
      const cx = x1 + t * abx;
      const cy = y1 + t * aby;
      const dx = px - cx;
      const dy = py - cy;
      const d2 = dx * dx + dy * dy;
      if (d2 >= r2 || d2 < 1e-4) continue;
      const d = Math.sqrt(d2);
      const nx = dx / d;
      const ny = dy / d;
      px = cx + nx * radius;
      py = cy + ny * radius;
      const dot = vx * nx + vy * ny;
      if (dot < 0) {
        vx = (vx - 2 * dot * nx) * ELASTICITY;
        vy = (vy - 2 * dot * ny) * ELASTICITY;
      }
      hit = true;
    }
    if (hit) {
      this.out[0] = px;
      this.out[1] = py;
      this.out[2] = vx;
      this.out[3] = vy;
    }
    return hit;
  }

  draw(ctx, width = 3) {
    if (!this.count) return;
    ctx.lineCap = 'round';
    ctx.lineWidth = width;
    for (let i = 0; i < this.count; i++) {
      ctx.strokeStyle = `rgba(255,255,255,${(this.alpha[i] * 0.65).toFixed(3)})`;
      ctx.beginPath();
      ctx.moveTo(this.x1[i], this.y1[i]);
      ctx.lineTo(this.x2[i], this.y2[i]);
      ctx.stroke();
    }
  }
}
