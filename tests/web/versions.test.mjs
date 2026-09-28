// The version-history tables in README.md and docs/index.html are generated
// from docs/releases/versions.json; this fails when someone edits one by hand
// or forgets to re-run the generator after a release.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, loadVersions, plan } from '../../scripts/web/versions.mjs';

const list = loadVersions();

test('README and landing version tables match versions.json', () => {
  for (const { file, current, expected } of plan(list)) {
    assert.ok(current === expected, `${file} is out of date — run: node scripts/web/versions.mjs`);
  }
});

test('versions.json is newest first, unique, and every entry has release notes', () => {
  const key = (v) => v.split('.').map(Number);
  for (let i = 1; i < list.length; i++) {
    const [a, b] = [key(list[i - 1].version), key(list[i].version)];
    const cmp = a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
    assert.ok(cmp > 0, `${list[i - 1].version} should come before ${list[i].version}`);
    assert.ok(list[i - 1].date >= list[i].date, `dates out of order at ${list[i].version}`);
  }
  for (const { version } of list) {
    assert.ok(existsSync(join(ROOT, `docs/releases/v${version}.md`)), `docs/releases/v${version}.md missing`);
  }
});
