// Gravity for the visualizers, in m/s² and screen coordinates:
//   gx > 0 pulls toward the right edge, gy > 0 toward the bottom edge.
//
// Phones feed it from DeviceMotion (accelerationIncludingGravity), rotated
// into the current screen orientation. Everything else — or anyone who wants
// to play — can lean it with the ← / → keys.

const G = 9.81;
const OFFSET_KEY = 'fh.tiltOffset';

// iOS reports accelerationIncludingGravity with the opposite sign to Android.
const IS_IOS =
  typeof navigator !== 'undefined' &&
  (/iP(hone|ad|od)/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));

function screenAngle() {
  const a = screen.orientation?.angle ?? window.orientation ?? 0;
  return ((a % 360) + 360) % 360;
}

export class Tilt {
  constructor({ keyboard = true } = {}) {
    this.gx = 0;
    this.gy = G;
    this.sensorX = 0;
    this.sensorY = G;
    this.hasSensor = false;
    this.keyboard = keyboard;
    this.keys = { left: false, right: false };
    this.manual = 0;
    this.offsetX = 0;
    try {
      this.offsetX = parseFloat(localStorage.getItem(OFFSET_KEY)) || 0;
    } catch {
      /* storage blocked: keep 0 */
    }
    this.onMotion = this.onMotion.bind(this);
    this.onKey = this.onKey.bind(this);
  }

  /** iOS 13+ gates motion events behind a permission prompt. */
  get needsPermission() {
    return typeof DeviceMotionEvent !== 'undefined' && typeof DeviceMotionEvent.requestPermission === 'function';
  }

  /** Must be called from a user gesture. Resolves to whether motion is allowed. */
  async requestPermission() {
    if (!this.needsPermission) return true;
    try {
      return (await DeviceMotionEvent.requestPermission()) === 'granted';
    } catch {
      return false;
    }
  }

  start() {
    window.addEventListener('devicemotion', this.onMotion);
    if (this.keyboard) {
      window.addEventListener('keydown', this.onKey);
      window.addEventListener('keyup', this.onKey);
    }
  }

  stop() {
    window.removeEventListener('devicemotion', this.onMotion);
    window.removeEventListener('keydown', this.onKey);
    window.removeEventListener('keyup', this.onKey);
    this.keys.left = this.keys.right = false;
  }

  onMotion(e) {
    const a = e.accelerationIncludingGravity;
    if (!a || a.x == null || a.y == null) return;
    const sign = IS_IOS ? -1 : 1;
    // Device frame (Android convention): pull toward right = -x, toward bottom = +y.
    const dx = -a.x * sign;
    const dy = a.y * sign;
    const rad = (screenAngle() * Math.PI) / 180;
    const c = Math.cos(rad);
    const s = Math.sin(rad);
    const sx = dx * c + dy * s;
    const sy = -dx * s + dy * c;
    this.hasSensor = true;
    this.sensorX += 0.2 * (sx - this.sensorX);
    this.sensorY += 0.2 * (sy - this.sensorY);
  }

  onKey(e) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    const t = e.target;
    if (t && (t.isContentEditable || /^(INPUT|SELECT|TEXTAREA)$/.test(t.tagName) || t.getAttribute?.('role') === 'spinbutton' || t.getAttribute?.('role') === 'radio')) return;
    this.keys[e.key === 'ArrowLeft' ? 'left' : 'right'] = e.type === 'keydown';
  }

  /** Advance smoothing; call once per frame. */
  update(dt) {
    const target = (this.keys.right ? 6 : 0) - (this.keys.left ? 6 : 0);
    this.manual += (target - this.manual) * (1 - Math.pow(0.002, dt));
    if (this.hasSensor) {
      // Lying flat, "down" is ambiguous: blend toward screen-down so the sand
      // keeps falling, while small tilts still lean the stream.
      const planar = Math.hypot(this.sensorX, this.sensorY);
      const flat = Math.max(0, Math.min(1, 1 - planar / G));
      this.gx = this.sensorX - this.offsetX + this.manual;
      this.gy = this.sensorY * (1 - flat) + G * flat;
    } else {
      this.gx = this.manual;
      this.gy = G;
    }
  }

  /** Treat the current sideways lean as level (for phones on a tilted stand). */
  calibrate() {
    this.offsetX = this.sensorX;
    try {
      localStorage.setItem(OFFSET_KEY, String(this.offsetX));
    } catch {
      /* not persisted; still applies this session */
    }
  }
}
