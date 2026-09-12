"use strict";

const CACHE_VERSION = "__STUDYFLOW_CACHE_VERSION__";
const APP_SHELL = [
  "./", "./index.html", "./styles.css", "./app.js", "./manifest.webmanifest",
  "./runtime/main.js", "./runtime/domain.js", "./runtime/content_import.js",
  "./runtime/question_import.js", "./runtime/mode_import.js", "./runtime/intelligence.js", "./runtime/refinement.js", "./runtime/retention_viewer.js", "./runtime/parity_intelligence.js", "./runtime/promise_cleanup.js", "./runtime/product.js", "./runtime/exam_foundation.js", "./runtime/task_workflows.js", "./runtime/indexeddb.js", "./runtime/storage.js",
  "./runtime/state.js", "./runtime/modes.js", "./runtime/schedule.js", "./runtime/portable.js", "./runtime/encrypted_portable.js", "./runtime/pwa.js", "./runtime/attention.js", "./runtime/compact_attention.js", "./runtime/calendar_attention.js", "./runtime/hardening.js", "./runtime/release.js", "./runtime/tv.js", "./runtime/tv_attention.js", "./runtime/presence.js", "./runtime/sflp_link.js", "./runtime/tv_history.js", "./runtime/tv_library.js", "./runtime/tv_control.js", "./runtime/tv_session.js", "./runtime/tv_question_set.js", "./runtime/tv_mock_exam.js", "./runtime/tv_review_notebook.js", "./runtime/tv_progress.js", "./runtime/tv_schedule.js", "./runtime/tv_semantic_parity.js", "./runtime/review_semantics.js", "./runtime/semantic_golden.js", "./runtime/types.js", "./runtime/react/App.js",
  "./node_modules/react/cjs/react.production.js",
  "./node_modules/react-dom/cjs/react-dom.production.js",
  "./node_modules/react-dom/cjs/react-dom-client.production.js",
  "./node_modules/scheduler/cjs/scheduler.production.js",
  "./icons/icon-192.png", "./icons/icon-512.png", "./icons/maskable-512.png"
];
const appShellUrls = new Set(APP_SHELL.map((path) => new URL(path, self.location.href).href));
const ATTENTION_DB_NAME = "studyflow-attention-sw-v1";
const ATTENTION_STORE_NAME = "pending";

function openAttentionDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(ATTENTION_DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(ATTENTION_STORE_NAME)) db.createObjectStore(ATTENTION_STORE_NAME, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("StudyFlow attention IndexedDB open failed"));
  });
}

async function persistAttentionEvent(event) {
  try {
    const db = await openAttentionDb();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(ATTENTION_STORE_NAME, "readwrite");
      tx.objectStore(ATTENTION_STORE_NAME).put({ ...event, id: event.id || `${event.kind}-${Date.now()}-${Math.random().toString(16).slice(2)}` });
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error("StudyFlow attention event write failed"));
    });
    db.close();
  } catch { /* Push notification still remains useful when durable event storage is unavailable. */ }
}

async function drainAttentionEvents() {
  try {
    const db = await openAttentionDb();
    const events = await new Promise((resolve, reject) => {
      const tx = db.transaction(ATTENTION_STORE_NAME, "readonly");
      const request = tx.objectStore(ATTENTION_STORE_NAME).getAll();
      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error || new Error("StudyFlow attention event read failed"));
    });
    await new Promise((resolve, reject) => {
      const tx = db.transaction(ATTENTION_STORE_NAME, "readwrite");
      tx.objectStore(ATTENTION_STORE_NAME).clear();
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error("StudyFlow attention event clear failed"));
    });
    db.close();
    return events;
  } catch { return []; }
}

async function broadcastAttentionEvent(event) {
  const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  for (const client of windows) client.postMessage({ type: "studyflow:attention-event", event });
}

function normalizePushPayload(data) {
  let payload = null;
  try { payload = data?.json?.() ?? null; } catch { /* fall through */ }
  if (!payload) {
    try { payload = JSON.parse(data?.text?.() || "null"); } catch { payload = null; }
  }
  const occurrence = payload && typeof payload === "object" ? payload.occurrence : null;
  if (!occurrence || typeof occurrence.occurrenceId !== "string" || typeof occurrence.category !== "string") return null;
  return {
    occurrence,
    title: typeof payload.title === "string" ? payload.title : "StudyFlow",
    body: typeof payload.body === "string" ? payload.body : "StudyFlow needs your attention.",
    routeHash: typeof payload.routeHash === "string" ? payload.routeHash : "#today",
  };
}

function notificationActions(occurrence) {
  const actions = Array.isArray(occurrence?.actions) ? occurrence.actions : [];
  return actions.slice(0, 2).map(({ kind }) => ({
    action: kind,
    title: kind === "review_now" ? "Review" : kind === "start_now" ? "Start" : kind === "snooze" ? "Snooze" : kind === "dismiss" ? "Dismiss" : "Open",
  }));
}

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_VERSION).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key.startsWith("studyflow-local-") && key !== CACHE_VERSION).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (event) => {
  const request=event.request;
  if(request.method!=="GET")return;
  const url=new URL(request.url);
  if(url.origin!==self.location.origin)return;

  if(request.mode === "navigate") {
    event.respondWith(caches.match("./index.html").then((cached)=>cached||fetch(request)));
    return;
  }
  if(!appShellUrls.has(url.href))return;
  event.respondWith(caches.match(request).then((cached)=>cached||fetch(request).then((response)=>{
    if(!response||!response.ok||response.type==="opaque")return response;
    const copy=response.clone();
    void caches.open(CACHE_VERSION).then((cache)=>cache.put(request,copy));
    return response;
  })));
});

self.addEventListener("push", (event) => {
  const payload = normalizePushPayload(event.data);
  if (!payload) return;
  const attentionEvent = {
    kind: "push-delivered",
    occurrence: payload.occurrence,
    title: payload.title,
    body: payload.body,
    routeHash: payload.routeHash,
    occurredAtEpochMillis: Date.now(),
  };
  event.waitUntil((async () => {
    await persistAttentionEvent(attentionEvent);
    await broadcastAttentionEvent(attentionEvent);
    await self.registration.showNotification(payload.title, {
      body: payload.body,
      tag: payload.occurrence.occurrenceId,
      renotify: false,
      icon: "./icons/icon-192.png",
      badge: "./icons/icon-192.png",
      data: { occurrence: payload.occurrence, occurrenceId: payload.occurrence.occurrenceId, routeHash: payload.routeHash },
      actions: notificationActions(payload.occurrence),
    });
  })());
});

self.addEventListener("notificationclick", (event) => {
  const data = event.notification?.data || {};
  const occurrence = data.occurrence;
  const routeHash = typeof data.routeHash === "string" ? data.routeHash : "#today";
  const action = event.action || "open";
  event.notification.close();
  const attentionEvent = { kind: "notification-click", occurrence, routeHash, action, occurredAtEpochMillis: Date.now() };
  event.waitUntil((async () => {
    await persistAttentionEvent(attentionEvent);
    const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const targetUrl = new URL(routeHash, self.location.href).href;
    let focused = false;
    for (const client of windows) {
      client.postMessage({ type: "studyflow:attention-event", event: attentionEvent });
      if (!focused && "focus" in client) {
        try {
          if ("navigate" in client && client.url !== targetUrl) await client.navigate(targetUrl);
          await client.focus();
          focused = true;
        } catch { /* try another client/openWindow */ }
      }
    }
    if (!focused && self.clients.openWindow) await self.clients.openWindow(targetUrl);
  })());
});

self.addEventListener("notificationclose", (event) => {
  const data = event.notification?.data || {};
  const occurrence = data.occurrence;
  if (!occurrence) return;
  const attentionEvent = { kind: "notification-close", occurrence, routeHash: data.routeHash || "#today", occurredAtEpochMillis: Date.now() };
  event.waitUntil(Promise.all([persistAttentionEvent(attentionEvent), broadcastAttentionEvent(attentionEvent)]));
});

self.addEventListener("message", (event) => {
  if (event.data?.type !== "studyflow:attention-drain") return;
  event.waitUntil((async () => {
    const events = await drainAttentionEvents();
    const target = event.source;
    for (const pending of events) target?.postMessage?.({ type: "studyflow:attention-event", event: pending });
  })());
});
