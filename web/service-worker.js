"use strict";

const CACHE_VERSION = "__STUDYFLOW_CACHE_VERSION__";
const APP_SHELL = [
  "./", "./index.html", "./styles.css", "./app.js", "./manifest.webmanifest",
  "./runtime/main.js", "./runtime/domain.js", "./runtime/content_import.js",
  "./runtime/question_import.js", "./runtime/mode_import.js", "./runtime/intelligence.js", "./runtime/refinement.js", "./runtime/retention_viewer.js", "./runtime/parity_intelligence.js", "./runtime/promise_cleanup.js", "./runtime/product.js", "./runtime/exam_foundation.js", "./runtime/task_workflows.js", "./runtime/indexeddb.js", "./runtime/storage.js",
  "./runtime/state.js", "./runtime/modes.js", "./runtime/schedule.js", "./runtime/portable.js", "./runtime/encrypted_portable.js", "./runtime/pwa.js", "./runtime/hardening.js", "./runtime/release.js", "./runtime/tv.js", "./runtime/sflp_link.js", "./runtime/tv_history.js", "./runtime/tv_library.js", "./runtime/tv_control.js", "./runtime/tv_session.js", "./runtime/tv_question_set.js", "./runtime/tv_mock_exam.js", "./runtime/tv_review_notebook.js", "./runtime/tv_progress.js", "./runtime/tv_schedule.js", "./runtime/tv_semantic_parity.js", "./runtime/review_semantics.js", "./runtime/semantic_golden.js", "./runtime/types.js", "./runtime/react/App.js",
  "./node_modules/react/cjs/react.production.js",
  "./node_modules/react-dom/cjs/react-dom.production.js",
  "./node_modules/react-dom/cjs/react-dom-client.production.js",
  "./node_modules/scheduler/cjs/scheduler.production.js",
  "./icons/icon-192.png", "./icons/icon-512.png", "./icons/maskable-512.png"
];
const appShellUrls = new Set(APP_SHELL.map((path) => new URL(path, self.location.href).href));

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
