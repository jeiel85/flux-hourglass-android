// Duration parsing/formatting and share-link helpers. Pure — covered by
// tests/web/time.test.mjs.

export const MAX_SECONDS = 99 * 3600 + 59 * 60 + 59;
export const MODE_IDS = ['sand', 'led', 'water', 'fire'];

const pad = (n) => String(n).padStart(2, '0');

export function splitSeconds(total) {
  const t = Math.max(0, Math.floor(total));
  return { h: Math.floor(t / 3600), m: Math.floor((t % 3600) / 60), s: t % 60 };
}

/**
 * Parses a human duration into whole seconds, or null when it isn't one.
 * Accepts "25m", "1h30m", "90s", "1h 5m 3s", "1:30" (m:ss), "1:30:00"
 * (h:mm:ss) and a bare number, which means minutes ("25" → 25 min).
 * Results are capped to the picker's 99:59:59.
 */
export function parseDuration(input) {
  if (input == null) return null;
  const str = String(input).trim().toLowerCase();
  if (!str) return null;
  let secs = null;
  if (/^\d+(\.\d+)?$/.test(str)) {
    secs = Math.round(parseFloat(str) * 60);
  } else if (/^\d+(:\d{1,2}){1,2}$/.test(str)) {
    const parts = str.split(':').map(Number);
    if (parts.slice(1).some((p) => p > 59)) return null;
    secs = parts.length === 2 ? parts[0] * 60 + parts[1] : parts[0] * 3600 + parts[1] * 60 + parts[2];
  } else {
    const re = /(\d+(?:\.\d+)?)\s*(h|m|s)/g;
    let consumed = '';
    let total = 0;
    let match;
    while ((match = re.exec(str))) {
      consumed += match[0];
      const v = parseFloat(match[1]);
      total += match[2] === 'h' ? v * 3600 : match[2] === 'm' ? v * 60 : v;
    }
    if (!consumed || consumed.replace(/\s/g, '') !== str.replace(/\s/g, '')) return null;
    secs = Math.round(total);
  }
  if (!Number.isFinite(secs) || secs <= 0) return null;
  return Math.min(secs, MAX_SECONDS);
}

/** Compact form used in share links: 1500 → "25m", 5400 → "1h30m". */
export function formatShort(total) {
  const { h, m, s } = splitSeconds(total);
  return `${h ? `${h}h` : ''}${m ? `${m}m` : ''}${s ? `${s}s` : ''}` || '0s';
}

/** "HH : MM : SS" — the app's reveal/paused format. */
export function formatSpaced(ms) {
  const { h, m, s } = splitSeconds(Math.ceil(ms / 1000));
  return `${pad(h)} : ${pad(m)} : ${pad(s)}`;
}

/** Tab-title countdown: "4:05", "1:02:09". Rounds up so 0:00 only shows at the end. */
export function formatTitle(ms) {
  const { h, m, s } = splitSeconds(Math.ceil(ms / 1000));
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

/** Reads ?t=…&m=… from a query string. Unknown or invalid values are dropped. */
export function parseShareParams(search) {
  const q = new URLSearchParams(search);
  const seconds = parseDuration(q.get('t'));
  const m = (q.get('m') || '').toLowerCase();
  return { seconds, mode: MODE_IDS.includes(m) ? m : null };
}

export function buildShareUrl(base, seconds, mode) {
  const url = new URL(base);
  url.search = '';
  url.hash = '';
  url.searchParams.set('t', formatShort(seconds));
  url.searchParams.set('m', mode);
  return url.toString();
}
