import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseDuration,
  formatShort,
  formatSpaced,
  formatTitle,
  parseShareParams,
  buildShareUrl,
  splitSeconds,
  MAX_SECONDS,
} from '../../docs/js/time.js';

test('parseDuration accepts unit, clock and bare-minute forms', () => {
  assert.equal(parseDuration('25m'), 1500);
  assert.equal(parseDuration('1h30m'), 5400);
  assert.equal(parseDuration('90s'), 90);
  assert.equal(parseDuration('1h 5m 3s'), 3903);
  assert.equal(parseDuration('1:30'), 90);
  assert.equal(parseDuration('1:30:00'), 5400);
  assert.equal(parseDuration('25'), 1500);
  assert.equal(parseDuration('1.5'), 90);
  assert.equal(parseDuration(' 3M '), 180);
});

test('parseDuration rejects junk, zero and out-of-range clock parts', () => {
  for (const bad of [null, undefined, '', '0', '0m', 'abc', '25min', '1:75', '5m-', 'm5', '1::2']) {
    assert.equal(parseDuration(bad), null, `expected null for ${JSON.stringify(bad)}`);
  }
});

test('parseDuration caps at the picker maximum', () => {
  assert.equal(parseDuration('200h'), MAX_SECONDS);
  assert.equal(MAX_SECONDS, 99 * 3600 + 59 * 60 + 59);
});

test('formatShort round-trips through parseDuration', () => {
  for (const secs of [1, 59, 60, 90, 1500, 3600, 3903, 5400, MAX_SECONDS]) {
    assert.equal(parseDuration(formatShort(secs)), secs);
  }
  assert.equal(formatShort(1500), '25m');
  assert.equal(formatShort(5400), '1h30m');
  assert.equal(formatShort(0), '0s');
});

test('clock formats round remaining time up', () => {
  assert.equal(formatSpaced(59_001), '00 : 01 : 00');
  assert.equal(formatSpaced(0), '00 : 00 : 00');
  assert.equal(formatSpaced(3_723_000), '01 : 02 : 03');
  assert.equal(formatTitle(65_000), '1:05');
  assert.equal(formatTitle(3_723_000), '1:02:03');
  assert.equal(formatTitle(1), '0:01');
});

test('splitSeconds', () => {
  assert.deepEqual(splitSeconds(3903), { h: 1, m: 5, s: 3 });
  assert.deepEqual(splitSeconds(-5), { h: 0, m: 0, s: 0 });
});

test('share params: valid values load, unknown ones are dropped', () => {
  assert.deepEqual(parseShareParams('?t=25m&m=fire'), { seconds: 1500, mode: 'fire' });
  assert.deepEqual(parseShareParams('?t=10m&m=LED'), { seconds: 600, mode: 'led' });
  assert.deepEqual(parseShareParams('?t=lots&m=lava'), { seconds: null, mode: null });
  assert.deepEqual(parseShareParams(''), { seconds: null, mode: null });
});

test('buildShareUrl replaces any existing query and hash', () => {
  const url = buildShareUrl('https://example.com/app/?t=1m&m=sand#x', 1500, 'water');
  assert.equal(url, 'https://example.com/app/?t=25m&m=water');
  assert.deepEqual(parseShareParams(new URL(url).search), { seconds: 1500, mode: 'water' });
});
