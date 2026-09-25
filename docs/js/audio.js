// Procedural soundscapes (Web Audio port of ProceduralAudioPlayer.kt) and the
// three-note completion chime. No audio files: each bed is synthesised once
// into a seamless looping buffer.
//
// The app filters white noise with a one-pole low-pass at 22 050 Hz. Here the
// coefficient is converted so the cutoff frequency is the same at whatever
// rate the AudioContext runs (usually 44.1 or 48 kHz).

const APP_RATE = 22050;
const MASTER = 2; // the app's levels are phone-speaker quiet; lift a little

const RECIPES = {
  // Soft falling grains: gentle low-pass noise + rare faint ticks.
  sand: { a: 0.09, amp: 6000, pops: 0.0002, popAmp: 4000, popTail: 8, seconds: 8 },
  // Campfire: low rumble + crackle pops.
  fire: { a: 0.08, amp: 8000, pops: 0.0005, popAmp: 13000, popTail: 28, seconds: 8 },
  // Rolling water: slower low-pass noise under a ~1 Hz swell.
  water: { a: 0.05, amp: 12000, swell: 0.0003, seconds: 0 },
};

function synth(ctx, recipe) {
  const fs = ctx.sampleRate;
  const a = 1 - Math.pow(1 - recipe.a, APP_RATE / fs);
  let seconds = recipe.seconds;
  let swellStep = 0;
  if (recipe.swell) {
    // Whole number of swell periods so the loop point is seamless.
    const period = (2 * Math.PI) / (recipe.swell * APP_RATE);
    seconds = period * Math.round(8 / period);
    swellStep = (recipe.swell * APP_RATE) / fs;
  }
  const n = Math.round(seconds * fs);
  const fade = Math.round(fs * 0.08);
  const raw = new Float32Array(n + fade);
  const popChance = recipe.pops ? (recipe.pops * APP_RATE) / fs : 0;
  let y = 0;
  let phase = 0;
  let tail = 0;
  let tailAmp = 0;
  for (let i = 0; i < raw.length; i++) {
    y += a * (Math.random() * 2 - 1 - y);
    let v = y * recipe.amp;
    if (recipe.swell) {
      phase += swellStep;
      v *= Math.sin(phase) * 0.4 + 0.6;
    }
    if (popChance && Math.random() < popChance) {
      tailAmp = Math.random() * recipe.popAmp * 2 - recipe.popAmp;
      tail = recipe.popTail;
    }
    if (tail > 0) {
      v += tailAmp * (Math.random() * 0.6 + 0.4);
      tailAmp *= 0.72;
      tail--;
    }
    raw[i] = (v / 32768) * MASTER;
  }
  // Cross-fade the overrun into the head so the loop has no seam.
  const buf = ctx.createBuffer(1, n, fs);
  const out = buf.getChannelData(0);
  out.set(raw.subarray(0, n));
  for (let i = 0; i < fade; i++) {
    const t = i / fade;
    out[i] = raw[i] * t + raw[n + i] * (1 - t);
  }
  return buf;
}

export class Sound {
  constructor() {
    this.ctx = null;
    this.buffers = new Map();
    this.current = null;
    this.mode = null;
  }

  get supported() {
    return typeof window !== 'undefined' && !!(window.AudioContext || window.webkitAudioContext);
  }

  /** Create/resume the context. Call from a user gesture (START, SOUND). */
  unlock() {
    if (!this.supported) return null;
    if (!this.ctx) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      this.ctx = new Ctx();
    }
    if (this.ctx.state === 'suspended') this.ctx.resume().catch(() => {});
    return this.ctx;
  }

  play(mode) {
    if (!RECIPES[mode]) {
      this.stop();
      return;
    }
    if (this.current && this.mode === mode) return;
    const ctx = this.unlock();
    if (!ctx) return;
    this.stop();
    let buf = this.buffers.get(mode);
    if (!buf) {
      buf = synth(ctx, RECIPES[mode]);
      this.buffers.set(mode, buf);
    }
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.loop = true;
    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0, ctx.currentTime);
    gain.gain.linearRampToValueAtTime(1, ctx.currentTime + 0.6);
    src.connect(gain).connect(ctx.destination);
    src.start();
    this.current = { src, gain };
    this.mode = mode;
  }

  stop() {
    if (!this.current || !this.ctx) {
      this.current = null;
      this.mode = null;
      return;
    }
    const { src, gain } = this.current;
    const t = this.ctx.currentTime;
    gain.gain.cancelScheduledValues(t);
    gain.gain.setValueAtTime(gain.gain.value, t);
    gain.gain.linearRampToValueAtTime(0, t + 0.35);
    try {
      src.stop(t + 0.4);
    } catch {
      /* already stopped */
    }
    this.current = null;
    this.mode = null;
  }

  /** Two short notes and a longer, higher one — the app's finish tone. */
  chime() {
    const ctx = this.unlock();
    if (!ctx) return;
    const t0 = ctx.currentTime + 0.05;
    note(ctx, t0, 0.22, 880);
    note(ctx, t0 + 0.32, 0.22, 880);
    note(ctx, t0 + 0.64, 0.42, 1318.5);
  }
}

function note(ctx, t, dur, freq) {
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(0.22, t + 0.012);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.3);
  gain.connect(ctx.destination);
  for (const [mult, level] of [[1, 1], [2, 0.18], [3, 0.06]]) {
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq * mult;
    g.gain.value = level;
    osc.connect(g).connect(gain);
    osc.start(t);
    osc.stop(t + dur + 0.35);
  }
}
