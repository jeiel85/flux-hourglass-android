// The offline precache list in docs/app/sw.js is kept by hand, and
// cache.addAll() rejects the whole install on a single 404 — offline mode then
// silently stops working. These checks catch a renamed, deleted or forgotten
// file before it ships.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const DOCS = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs');
const APP_DIR = join(DOCS, 'app');

// Input: nothing. Output: the SHELL entries exactly as written in sw.js.
// Why regex instead of importing: sw.js is a classic worker script that calls
// self.addEventListener at load, so it can't be imported into Node.
function readShell() {
  const src = readFileSync(join(APP_DIR, 'sw.js'), 'utf8');
  const block = src.match(/const SHELL = \[([\s\S]*?)\];/);
  assert.ok(block, 'SHELL array not found in sw.js');
  return [...block[1].matchAll(/'([^']+)'/g)].map((m) => m[1]);
}

// Input: a SHELL entry (relative to the worker's scope, docs/app/).
// Output: the file GitHub Pages serves for it. A trailing slash is the
// directory's index.html, as Pages serves it.
function toFile(entry) {
  const path = resolve(APP_DIR, entry);
  return entry.endsWith('/') ? join(path, 'index.html') : path;
}

// Input: an entry JS file. Output: that file and every local module it
// imports, transitively, as absolute paths.
// Why: an ES module that fails to load offline takes the whole app down with
// it, so each file in the import graph has to be precached — not just app.js.
function importGraph(entry) {
  const seen = new Set();
  const visit = (file) => {
    if (seen.has(file)) return;
    seen.add(file);
    const src = readFileSync(file, 'utf8');
    for (const m of src.matchAll(/^\s*(?:import|export)\b[^'"]*?\bfrom\s*['"](\.[^'"]+)['"]|^\s*import\s*['"](\.[^'"]+)['"]/gm)) {
      visit(resolve(dirname(file), m[1] ?? m[2]));
    }
  };
  visit(entry);
  return seen;
}

const shell = readShell();
const shellFiles = new Set(shell.map(toFile));
const rel = (file) => relative(DOCS, file);

test('every SHELL entry exists', () => {
  assert.ok(shell.length > 0);
  const missing = shell.filter((entry) => {
    const file = toFile(entry);
    return !existsSync(file) || !statSync(file).isFile();
  });
  assert.deepEqual(missing, [], `sw.js precaches files that don't exist: ${missing.join(', ')}`);
});

test('SHELL has no duplicates', () => {
  assert.equal(new Set(shell).size, shell.length);
});

test('every module the app imports is precached', () => {
  const missing = [...importGraph(join(DOCS, 'js/app.js'))].filter((f) => !shellFiles.has(f)).map(rel);
  assert.deepEqual(missing, [], `add these to SHELL in docs/app/sw.js (and bump VERSION): ${missing.join(', ')}`);
});

test('every local file the app page links is precached', () => {
  const html = readFileSync(join(APP_DIR, 'index.html'), 'utf8');
  const refs = [...html.matchAll(/\b(?:href|src)="([^"#?]+)"/g)]
    .map((m) => m[1])
    .filter((ref) => !/^[a-z]+:/i.test(ref) && !ref.startsWith('//'));
  assert.ok(refs.length > 0);
  const missing = refs.filter((ref) => !shellFiles.has(resolve(APP_DIR, ref)));
  assert.deepEqual(missing, [], `add these to SHELL in docs/app/sw.js (and bump VERSION): ${missing.join(', ')}`);
});
