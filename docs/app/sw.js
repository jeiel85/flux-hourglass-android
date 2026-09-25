// Offline support for the web app. Network-first so a deploy is picked up on
// the next online load; the cache is only the fallback when offline.
// Bump VERSION when the file list changes.

const VERSION = 'fh-web-v1';
const SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  '../css/app.css',
  '../js/app.js',
  '../js/time.js',
  '../js/tilt.js',
  '../js/audio.js',
  '../js/sim/index.js',
  '../js/sim/util.js',
  '../js/sim/lines.js',
  '../js/sim/sand.js',
  '../js/sim/led.js',
  '../js/sim/water.js',
  '../js/sim/fire.js',
  '../assets/favicon.svg',
  '../assets/icon-192.png',
  '../assets/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(VERSION)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
  // Every shared link (?t=…&m=…) is the same page: cache navigations under
  // their query-less URL so the cache doesn't grow one entry per link.
  const key = request.mode === 'navigate' ? new URL(request.url).pathname : request;
  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(VERSION).then((cache) => cache.put(key, copy));
        }
        return response;
      })
      .catch(() =>
        caches
          .match(request, { ignoreSearch: request.mode === 'navigate' })
          .then((hit) => hit || (request.mode === 'navigate' ? caches.match('./') : undefined))
          .then((hit) => hit || Response.error()),
      ),
  );
});
