"use strict";

(() => {
  const PACKAGE_MODULES = Object.freeze({
    react: "./node_modules/react/cjs/react.production.js",
    "react-dom": "./node_modules/react-dom/cjs/react-dom.production.js",
    "react-dom/client": "./node_modules/react-dom/cjs/react-dom-client.production.js",
    scheduler: "./node_modules/scheduler/cjs/scheduler.production.js",
  });
  const sources = new Map();
  const loading = new Set();
  const modules = new Map();
  const processObject = Object.freeze({ env: Object.freeze({ NODE_ENV: "production" }) });

  function resolveModule(specifier, parentUrl) {
    if (Object.prototype.hasOwnProperty.call(PACKAGE_MODULES, specifier)) return new URL(PACKAGE_MODULES[specifier], location.href).href;
    if (!specifier.startsWith(".") && !specifier.startsWith("/")) throw new Error(`Unsupported browser module: ${specifier}`);
    let url = new URL(specifier, parentUrl);
    if (!/\.[a-z0-9]+$/i.test(url.pathname)) url = new URL(`${url.href}.js`);
    return url.href;
  }
  function requires(source) { return [...source.matchAll(/\brequire\(["']([^"']+)["']\)/g)].map((match) => match[1]); }
  async function preload(url) {
    if (sources.has(url) || loading.has(url)) return;
    loading.add(url);
    try {
      const response = await fetch(url, { cache: "no-cache" });
      if (!response.ok) throw new Error(`Failed to load ${new URL(url).pathname}: HTTP ${response.status}`);
      const source = await response.text(); sources.set(url, source);
      await Promise.all(requires(source).map((specifier) => preload(resolveModule(specifier, url))));
    } finally { loading.delete(url); }
  }
  function execute(url) {
    if (modules.has(url)) return modules.get(url).exports;
    const source = sources.get(url); if (source == null) throw new Error(`Module was not preloaded: ${url}`);
    const module = { exports: {} }; modules.set(url, module);
    const localRequire = (specifier) => execute(resolveModule(specifier, url));
    const factory = new Function("module", "exports", "require", "process", "globalThis", `${source}\n//# sourceURL=${url}`);
    factory(module, module.exports, localRequire, processObject, globalThis);
    return module.exports;
  }
  async function boot() {
    const entry = new URL("./runtime/main.js", location.href).href;
    await preload(entry); execute(entry);
  }
  boot().catch((error) => {
    console.error(error);
    const root = document.getElementById("root");
    if (root) root.textContent = `StudyFlow could not start: ${error instanceof Error ? error.message : String(error)}`;
  });
})();
