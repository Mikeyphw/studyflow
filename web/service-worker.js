"use strict";

const CACHE_VERSION = "studyflow-local-w13-v1";
const APP_SHELL = [
    "./",
    "./index.html",
    "./styles.css",
    "./app.js",
    "./manifest.webmanifest",
    "./src/domain.js",
    "./src/storage.js",
    "./src/indexeddb.js",
    "./src/state.js",
    "./src/render.js",
    "./src/events.js",
    "./src/portable.js",
    "./src/pwa.js",
    "./icons/icon-192.png",
    "./icons/icon-512.png",
    "./icons/maskable-512.png"
];

self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(CACHE_VERSION).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
    event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key.startsWith("studyflow-local-") && key !== CACHE_VERSION).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET") return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    if (request.mode === "navigate") {
        event.respondWith(fetch(request).then((response) => {
            const copy = response.clone();
            caches.open(CACHE_VERSION).then((cache) => cache.put("./index.html", copy));
            return response;
        }).catch(() => caches.match("./index.html")));
        return;
    }
    event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then((response) => {
        if (!response || response.status !== 200 || response.type === "opaque") return response;
        const copy = response.clone();
        caches.open(CACHE_VERSION).then((cache) => cache.put(request, copy));
        return response;
    })));
});
