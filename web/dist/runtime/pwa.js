"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerStudyFlowPwa = registerStudyFlowPwa;
function registerStudyFlowPwa({ statusElement = null, installButton = null, navigatorObject = globalThis.navigator ?? null, windowObject = globalThis.window ?? null, } = {}) {
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
    if (!navigatorObject?.serviceWorker) {
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
    windowObject?.addEventListener("online", syncNetworkStatus);
    windowObject?.addEventListener("offline", syncNetworkStatus);
    windowObject?.addEventListener("beforeinstallprompt", onBeforeInstall);
    windowObject?.addEventListener("appinstalled", onInstalled);
    installButton?.addEventListener("click", onInstallClick);
    syncNetworkStatus();
    return {
        supported: true,
        dispose() {
            windowObject?.removeEventListener("online", syncNetworkStatus);
            windowObject?.removeEventListener("offline", syncNetworkStatus);
            windowObject?.removeEventListener("beforeinstallprompt", onBeforeInstall);
            windowObject?.removeEventListener("appinstalled", onInstalled);
            installButton?.removeEventListener("click", onInstallClick);
            installPrompt = null;
        },
    };
}
