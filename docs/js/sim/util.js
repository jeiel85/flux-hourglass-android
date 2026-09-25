// Shared helpers for the canvas visualizers. Pure functions only — these
// modules are also imported by the Node test suite, so nothing here may touch
// the DOM at import time.

export const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);

// Small, fast seeded PRNG (mulberry32). Seeded so the landing-page loops and
// the OG image render the same way every time.
export function rng(seed = (Math.random() * 2 ** 32) >>> 0) {
  let a = seed >>> 0;
  return function next() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Stable per-index noise in [0, 1) — used where a pattern must not shimmer
// from frame to frame (e.g. the grain jitter on the sand surface).
export function hash01(i, seed = 0) {
  let x = (i * 374761393 + seed * 668265263) >>> 0;
  x = Math.imul(x ^ (x >>> 13), 1274126177);
  return ((x ^ (x >>> 16)) >>> 0) / 4294967296;
}

// The Android app was tuned on a Galaxy S24 (1080×2340 px, 2.8125 density,
// ~832 dp tall). The web sims express every pixel constant relative to the
// canvas height so the motion reads the same on a phone, a desktop window or
// a small landing-page tile.
export const REF_PX_HEIGHT = 2340;
export const REF_DP_HEIGHT = 832;

// dp → css px, scaled with the canvas height but bounded so tiny tiles and 4K
// windows stay legible.
export const dpScale = (h) => clamp(h / REF_DP_HEIGHT, 0.7, 1.6);

// Frame-rate independent exponential smoothing: the share of the remaining
// gap closed in `dt` seconds, given the per-frame factor tuned at 60 fps.
export const perFrame = (factorAt60, dt) => 1 - Math.pow(1 - factorAt60, dt * 60);

export const DEFAULT_SETTINGS = Object.freeze({ gravity: 1, size: 1, density: 1 });
