"use strict";

export function registerStudyFlowPwa({ statusElement = null, installButton = null, navigatorObject = globalThis.navigator, windowObject = globalThis.window } = {}) {
    let installPrompt = null;
    const setStatus = (message) => { if (statusElement) statusElement.textContent = message; };
    const syncNetworkStatus = () => {
        if (!navigatorObject) return;
        setStatus(navigatorObject.onLine === false ? "Offline. Cached StudyFlow screens remain available." : "Online. Offline app shell is ready after service-worker activation.");
    };
    if (!navigatorObject?.serviceWorker) {
        setStatus("Service workers are unavailable in this browser context.");
        return { supported: false };
    }
    navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
        .then(() => syncNetworkStatus())
        .catch((error) => setStatus(`Offline worker registration failed: ${error instanceof Error ? error.message : String(error)}`));
    windowObject?.addEventListener?.("online", syncNetworkStatus);
    windowObject?.addEventListener?.("offline", syncNetworkStatus);
    windowObject?.addEventListener?.("beforeinstallprompt", (event) => {
        event.preventDefault();
        installPrompt = event;
        if (installButton) installButton.hidden = false;
        setStatus("Installable on this device.");
    });
    windowObject?.addEventListener?.("appinstalled", () => {
        installPrompt = null;
        if (installButton) installButton.hidden = true;
        setStatus("StudyFlow is installed for offline launch.");
    });
    installButton?.addEventListener?.("click", async () => {
        if (!installPrompt) return;
        await installPrompt.prompt();
        await installPrompt.userChoice;
        installPrompt = null;
        installButton.hidden = true;
        syncNetworkStatus();
    });
    syncNetworkStatus();
    return { supported: true };
}
