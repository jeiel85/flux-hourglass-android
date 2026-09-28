// Helpers shared by the app, the landing page and tilt.js. Kept DOM-free at
// import time so the Node test suite can load it too.

// Input: key. Output: the stored string, or null when absent or unreadable.
// Why: localStorage throws (not just returns null) in private mode, with site
// data blocked, or where it doesn't exist at all (Node). Every caller treats
// "can't read" the same as "never saved", so that is decided once, here.
export function readItem(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

// Input: key, string value. Output: whether it was saved.
// Why: a blocked write must never break the UI; the setting simply applies
// for this session only.
export function writeItem(key, value) {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}

// JSON on top of readItem/writeItem for structured settings.
// get(key, fallback): fallback when missing, unreadable or not valid JSON —
// a hand-edited or truncated value must not stop the app from starting.
export const store = {
  get(key, fallback) {
    const raw = readItem(key);
    if (raw == null) return fallback;
    try {
      return JSON.parse(raw);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    return writeItem(key, JSON.stringify(value));
  },
};

// Input: the toast element, how long a message stays up (ms).
// Output: show(msg).
// Why: a new message restarts the timer instead of stacking, so a quick second
// copy doesn't get hidden by the first one's timeout.
export function createToast(el, ms = 2200) {
  let timer = 0;
  return function show(msg) {
    el.textContent = msg;
    el.classList.add('on');
    clearTimeout(timer);
    timer = setTimeout(() => el.classList.remove('on'), ms);
  };
}
