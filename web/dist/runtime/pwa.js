"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerStudyFlowPwa = registerStudyFlowPwa;
function registerStudyFlowPwa(_a) {
    var _b, _c;
    var { statusElement = null, installButton = null, navigatorObject = (_b = globalThis.navigator) !== null && _b !== void 0 ? _b : null, windowObject = (_c = globalThis.window) !== null && _c !== void 0 ? _c : null, } = _a === void 0 ? {} : _a;
    let installPrompt = null;
    const setStatus = (message) => { if (statusElement)
        statusElement.textContent = message; };
    const syncNetworkStatus = () => {
        if (!navigatorObject)
            return;
        setStatus(navigatorObject.onLine === false
            ? "Offline. The installed StudyFlow app shell remains available from local cache."
            : "Online. StudyFlow core data remains local; the service worker keeps the app shell offline-ready.");
    };
    const noop = () => undefined;
    if (!(navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker)) {
        setStatus("Service workers are unavailable in this browser context.");
        return { supported: false, dispose: noop };
    }
    navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
        .then(() => syncNetworkStatus())
        .catch((error) => setStatus(`Offline worker registration failed: ${error instanceof Error ? error.message : String(error)}`));
    const onBeforeInstall = ((event) => {
        event.preventDefault();
        installPrompt = event;
        if (installButton)
            installButton.hidden = false;
        setStatus("Installable on this device.");
    });
    const onInstalled = () => {
        installPrompt = null;
        if (installButton)
            installButton.hidden = true;
        setStatus("StudyFlow is installed for offline launch.");
    };
    const onInstallClick = async () => {
        if (!installPrompt)
            return;
        await installPrompt.prompt();
        await installPrompt.userChoice;
        installPrompt = null;
        if (installButton)
            installButton.hidden = true;
        syncNetworkStatus();
    };
    windowObject === null || windowObject === void 0 ? void 0 : windowObject.addEventListener("online", syncNetworkStatus);
    windowObject === null || windowObject === void 0 ? void 0 : windowObject.addEventListener("offline", syncNetworkStatus);
    windowObject === null || windowObject === void 0 ? void 0 : windowObject.addEventListener("beforeinstallprompt", onBeforeInstall);
    windowObject === null || windowObject === void 0 ? void 0 : windowObject.addEventListener("appinstalled", onInstalled);
    installButton === null || installButton === void 0 ? void 0 : installButton.addEventListener("click", onInstallClick);
    syncNetworkStatus();
    return {
        supported: true,
        dispose() {
            windowObject === null || windowObject === void 0 ? void 0 : windowObject.removeEventListener("online", syncNetworkStatus);
            windowObject === null || windowObject === void 0 ? void 0 : windowObject.removeEventListener("offline", syncNetworkStatus);
            windowObject === null || windowObject === void 0 ? void 0 : windowObject.removeEventListener("beforeinstallprompt", onBeforeInstall);
            windowObject === null || windowObject === void 0 ? void 0 : windowObject.removeEventListener("appinstalled", onInstalled);
            installButton === null || installButton === void 0 ? void 0 : installButton.removeEventListener("click", onInstallClick);
            installPrompt = null;
        },
    };
}
