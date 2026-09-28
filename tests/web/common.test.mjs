import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readItem, writeItem, store, createToast } from '../../docs/js/common.js';

// A Map-backed stand-in for window.localStorage.
function fakeStorage() {
  const m = new Map();
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => m.set(k, String(v)),
  };
}

// A storage that throws like Safari private mode / blocked site data.
const blockedStorage = {
  getItem() {
    throw new Error('SecurityError');
  },
  setItem() {
    throw new Error('QuotaExceededError');
  },
};

afterEach(() => {
  delete globalThis.localStorage;
});

test('storage helpers survive a missing localStorage (Node)', () => {
  assert.equal(readItem('k'), null);
  assert.equal(writeItem('k', 'v'), false);
  assert.equal(store.get('k', 7), 7);
  assert.equal(store.set('k', 1), false);
});

test('storage helpers survive a throwing localStorage', () => {
  globalThis.localStorage = blockedStorage;
  assert.equal(readItem('k'), null);
  assert.equal(writeItem('k', 'v'), false);
  assert.deepEqual(store.get('k', { a: 1 }), { a: 1 });
});

test('raw strings round-trip unquoted (the landing <head> script reads fh.lang raw)', () => {
  globalThis.localStorage = fakeStorage();
  assert.equal(writeItem('fh.lang', 'ko'), true);
  assert.equal(localStorage.getItem('fh.lang'), 'ko');
  assert.equal(readItem('fh.lang'), 'ko');
});

test('store round-trips JSON and falls back on corrupt values', () => {
  globalThis.localStorage = fakeStorage();
  store.set('fh.last', { h: 0, m: 25, s: 0, mode: 'sand' });
  assert.deepEqual(store.get('fh.last', null), { h: 0, m: 25, s: 0, mode: 'sand' });
  localStorage.setItem('fh.settings', '{"gravity":');
  assert.deepEqual(store.get('fh.settings', {}), {});
});

test('toast shows the latest message and hides after its own delay', async () => {
  const classes = new Set();
  const el = { textContent: '', classList: { add: (c) => classes.add(c), remove: (c) => classes.delete(c) } };
  const show = createToast(el, 30);
  show('first');
  await new Promise((r) => setTimeout(r, 20));
  show('second'); // restarts the timer
  await new Promise((r) => setTimeout(r, 20));
  assert.equal(el.textContent, 'second');
  assert.ok(classes.has('on'), 'still visible: the first timeout was cleared');
  await new Promise((r) => setTimeout(r, 30));
  assert.ok(!classes.has('on'));
});
