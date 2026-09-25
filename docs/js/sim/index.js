import { SandSim } from './sand.js';
import { LedSim } from './led.js';
import { WaterSim } from './water.js';
import { FireSim } from './fire.js';

// Display order matches the app's object filmstrip.
export const MODES = [
  { id: 'sand', label: 'Sand', draw: true, sound: true },
  { id: 'led', label: 'LED', draw: false, sound: false },
  { id: 'water', label: 'Water', draw: true, sound: true },
  { id: 'fire', label: 'Fire', draw: false, sound: true },
];

export const modeById = (id) => MODES.find((m) => m.id === id);

export function createSim(id, opts = {}) {
  switch (id) {
    case 'led':
      return new LedSim(opts);
    case 'water':
      return new WaterSim(opts);
    case 'fire':
      return new FireSim(opts);
    default:
      return new SandSim(opts);
  }
}

export { SandSim, LedSim, WaterSim, FireSim };
