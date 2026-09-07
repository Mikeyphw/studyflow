"use strict";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: string; platform?: string }>;
}

export interface PwaRegistrationOptions {
  statusElement?: HTMLElement | null;
  installButton?: HTMLButtonElement | null;
  navigatorObject?: Navigator | null;
  windowObject?: Window | null;
}

export function registerStudyFlowPwa({
  statusElement = null,
  installButton = null,
  navigatorObject = globalThis.navigator ?? null,
  windowObject = globalThis.window ?? null,
}: PwaRegistrationOptions = {}): { supported: boolean } {
  let installPrompt: BeforeInstallPromptEvent | null = null;
  const setStatus = (message: string): void => { if (statusElement) statusElement.textContent = message; };
  const syncNetworkStatus = (): void => {
    if (!navigatorObject) return;
    setStatus(navigatorObject.onLine === false
      ? "Offline. Cached StudyFlow screens remain available."
      : "Online. Offline app shell is ready after service-worker activation.");
  };
  if (!navigatorObject?.serviceWorker) {
    setStatus("Service workers are unavailable in this browser context.");
    return { supported: false };
  }
  navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
    .then(() => syncNetworkStatus())
    .catch((error: unknown) => setStatus(`Offline worker registration failed: ${error instanceof Error ? error.message : String(error)}`));
  windowObject?.addEventListener("online", syncNetworkStatus);
  windowObject?.addEventListener("offline", syncNetworkStatus);
  windowObject?.addEventListener("beforeinstallprompt", ((event: Event) => {
    event.preventDefault();
    installPrompt = event as BeforeInstallPromptEvent;
    if (installButton) installButton.hidden = false;
    setStatus("Installable on this device.");
  }) as EventListener);
  windowObject?.addEventListener("appinstalled", () => {
    installPrompt = null;
    if (installButton) installButton.hidden = true;
    setStatus("StudyFlow is installed for offline launch.");
  });
  installButton?.addEventListener("click", async () => {
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
