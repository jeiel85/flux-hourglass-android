// Headless checks of the visualizer physics (update() only — drawing needs a
// canvas and is verified in the browser).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SandSim, LedSim, WaterSim, FireSim } from '../../docs/js/sim/index.js';
import { ObstacleLines } from '../../docs/js/sim/lines.js';
import { DEFAULT_SETTINGS } from '../../docs/js/sim/util.js';

const DT = 1 / 60;

// Runs a sim through a whole timer, calling probe(remaining) along the way.
function runTimer(sim, totalMs, { gx = 0, gy = 9.81, lines = null, settings = DEFAULT_SETTINGS, probe, stopAt = 0 } = {}) {
  const steps = Math.round(totalMs / 1000 / DT);
  for (let i = 1; i <= steps; i++) {
    const remaining = Math.max(0, 1 - (i * DT * 1000) / totalMs);
    if (remaining < stopAt) break;
    sim.update(DT, { remaining, totalMs, gx, gy, settings, lines });
    probe?.(remaining);
  }
}

test('sand pile tracks elapsed time and fills the screen at the end', () => {
  const sim = new SandSim({ seed: 1 });
  sim.resize(390, 844);
  let atHalf = null;
  runTimer(sim, 60_000, {
    probe: (rem) => {
      if (atHalf === null && rem <= 0.5) atHalf = sim.fillLevel();
    },
  });
  assert.ok(atHalf > 0.4 && atHalf < 0.6, `half-way fill ${atHalf}`);
  assert.ok(sim.fillLevel() > 0.97, `final fill ${sim.fillLevel()}`);
  assert.ok(sim.heights.every(Number.isFinite));
});

test('maxFill caps the pile (landing hero)', () => {
  const sim = new SandSim({ seed: 2, maxFill: 0.22 });
  sim.resize(1200, 800);
  runTimer(sim, 30_000);
  const fill = sim.fillLevel();
  assert.ok(fill > 0.2 && fill < 0.26, `fill ${fill}`);
});

test('density changes the stream, not the pace of the timer', () => {
  const fills = [0.5, 2].map((density) => {
    const sim = new SandSim({ seed: 3 });
    sim.resize(390, 844);
    let atHalf = 0;
    runTimer(sim, 20_000, {
      settings: { ...DEFAULT_SETTINGS, density },
      probe: (rem) => {
        if (!atHalf && rem <= 0.5) atHalf = sim.fillLevel();
      },
    });
    return atHalf;
  });
  assert.ok(Math.abs(fills[0] - fills[1]) < 0.12, `half-way fills ${fills}`);
});

test('tilting pours the heap toward the low side', () => {
  // Measured half-way: at the end the pile fills the whole screen, so its
  // centre is 0.5 whatever the tilt.
  const centre = (gx) => {
    const sim = new SandSim({ seed: 4 });
    sim.resize(390, 844);
    runTimer(sim, 20_000, { gx, stopAt: 0.5 });
    let mass = 0;
    let moment = 0;
    sim.heights.forEach((h, i) => {
      mass += h;
      moment += h * i;
    });
    return moment / mass / sim.cols;
  };
  assert.ok(centre(6) > centre(0) + 0.03, 'tilt right shifts the pile right');
  assert.ok(centre(-6) < centre(0) - 0.03, 'tilt left shifts the pile left');
});

test('a sand sim survives NaN gravity and resizes without losing its fill ratio', () => {
  const sim = new SandSim({ seed: 5 });
  sim.resize(390, 844);
  runTimer(sim, 10_000, { gx: NaN, gy: NaN });
  const before = sim.fillLevel();
  sim.resize(844, 390); // rotate
  assert.ok(Math.abs(sim.fillLevel() - before) < 0.05);
  assert.ok(sim.heights.every(Number.isFinite));
});

test('fast particles cannot tunnel through a drawn line', () => {
  const lines = new ObstacleLines();
  lines.add(0, 50, 100, 50);
  // Moving 20 px in one step, straight through the line.
  assert.ok(lines.collide(50, 60, 0, 3000, 2, 50, 40));
  const [x, y, vx, vy] = lines.out;
  assert.equal(x, 50);
  assert.ok(y < 50 && y >= 47.9, `stopped above the line, y=${y}`);
  assert.ok(vy <= 0, 'bounced upward');
  assert.equal(vx, 0);
  // A particle that doesn't reach the line is left alone.
  assert.equal(lines.collide(50, 30, 0, 100, 2, 50, 20), false);
});

test('drawn lines fade out after about six seconds', () => {
  const lines = new ObstacleLines();
  lines.add(0, 0, 10, 10);
  lines.update(5);
  assert.equal(lines.count, 1);
  lines.update(2);
  assert.equal(lines.count, 0);
});

test('water, fire and LED run a whole timer without blowing their budgets', () => {
  const water = new WaterSim({ seed: 6 });
  const fire = new FireSim({ seed: 7 });
  const led = new LedSim();
  for (const sim of [water, fire, led]) sim.resize(390, 844);
  let maxFire = 0;
  const env = (remaining) => ({ remaining, totalMs: 30_000, gx: 2, gy: 9.81, settings: DEFAULT_SETTINGS, lines: null });
  for (let i = 1; i <= 30 * 60; i++) {
    const remaining = Math.max(0, 1 - i / (30 * 60));
    water.update(DT, env(remaining));
    fire.update(DT, env(remaining));
    led.update(DT, env(remaining));
    maxFire = Math.max(maxFire, fire.particles.length);
  }
  assert.ok(maxFire <= 350, `fire particles ${maxFire}`);
  assert.ok(fire.particles.length <= 20, 'only embers remain at the end');
  assert.ok(water.bubbles.length <= 35 && water.splashes.length <= 60);
  assert.ok(Number.isFinite(water.slosh) && Math.abs(water.slosh) < 0.6);
});
