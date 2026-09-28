// Renders the Android version-history table from docs/releases/versions.json
// into README.md and the landing page (docs/index.html), between the
// `versions:start` / `versions:end` marker comments.
//
//   node scripts/web/versions.mjs          rewrite both files
//   node scripts/web/versions.mjs --check  exit 1 if either is out of date
//
// tests/web/versions.test.mjs runs the same check in Web CI.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const NOTES_URL = 'https://github.com/jeiel85/flux-hourglass-android/blob/main/docs/releases';

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// Input: nothing. Output: the release list, newest first.
// Why validate here: a typo in the JSON would otherwise surface as a broken
// link or an empty cell on the live landing page, not as a failed check.
export function loadVersions() {
  const list = JSON.parse(readFileSync(join(ROOT, 'docs/releases/versions.json'), 'utf8'));
  for (const v of list) {
    if (!/^\d+\.\d+\.\d+$/.test(v.version)) throw new Error(`bad version: ${JSON.stringify(v)}`);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(v.date)) throw new Error(`bad date: ${JSON.stringify(v)}`);
    if (!v.notes?.trim() || v.notes.includes('|')) throw new Error(`missing notes or a '|' in them: ${JSON.stringify(v)}`);
  }
  return list;
}

// README rows link the notes relatively so they work on forks and in the
// GitHub file view; the landing page is served from Pages, so it links to
// GitHub absolutely. The Markdown table is regenerated whole, header
// included: a comment line inside a GFM table ends it, so the markers have to
// sit outside the table (with blank lines, so they stay separate HTML blocks).
export function renderMarkdown(list) {
  const rows = list.map((v) => `| [${v.version}](docs/releases/v${v.version}.md) | ${v.date} | ${v.notes} |`);
  return ['', '| Version | Date | Notes |', '|---------|------|-------|', ...rows, ''].join('\n');
}

export function renderHtml(list, indent = '            ') {
  return list
    .map(
      (v) =>
        `${indent}<tr><td><a href="${NOTES_URL}/v${v.version}.md">${v.version}</a></td>` +
        `<td>${v.date}</td><td>${escapeHtml(v.notes)}.</td></tr>`,
    )
    .join('\n');
}

// Input: file text, the rendered rows. Output: the text with everything
// between the two marker lines replaced.
// Why markers instead of regenerating the whole table: the header row and the
// surrounding prose stay hand-written and reviewable in place.
export function splice(text, rows, file) {
  const re = /(<!-- versions:start[^>]*-->\n)[\s\S]*?(\n[ \t]*<!-- versions:end -->)/;
  if (!re.test(text)) throw new Error(`${file}: versions:start / versions:end markers not found`);
  return text.replace(re, (_, start, end) => `${start}${rows}${end}`);
}

export const TARGETS = [
  { file: 'README.md', render: renderMarkdown },
  { file: 'docs/index.html', render: renderHtml },
];

// Output: [{ file, current, expected }] for each target.
export function plan(list = loadVersions()) {
  return TARGETS.map(({ file, render }) => {
    const current = readFileSync(join(ROOT, file), 'utf8');
    return { file, current, expected: splice(current, render(list), file) };
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const check = process.argv.includes('--check');
  let stale = 0;
  for (const { file, current, expected } of plan()) {
    if (current === expected) continue;
    stale++;
    if (check) console.error(`${file} is out of date — run: node scripts/web/versions.mjs`);
    else {
      writeFileSync(join(ROOT, file), expected);
      console.log(`updated ${file}`);
    }
  }
  if (check && stale) process.exit(1);
  if (!stale) console.log('version tables are up to date');
}
