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

export interface PwaRegistrationHandle { supported:boolean; dispose():void; }

export function registerStudyFlowPwa({
  statusElement = null,
  installButton = null,
  navigatorObject = globalThis.navigator ?? null,
  windowObject = globalThis.window ?? null,
}: PwaRegistrationOptions = {}): PwaRegistrationHandle {
  let installPrompt: BeforeInstallPromptEvent | null = null;
  const setStatus = (message: string): void => { if (statusElement) statusElement.textContent = message; };
  const syncNetworkStatus = (): void => {
    if (!navigatorObject) return;
    setStatus(navigatorObject.onLine === false
      ? "Offline. The installed StudyFlow app shell remains available from local cache."
      : "Online. StudyFlow core data remains local; the service worker keeps the app shell offline-ready.");
  };
  const noop=():void=>undefined;
  if (!navigatorObject?.serviceWorker) {
    setStatus("Service workers are unavailable in this browser context.");
    return { supported: false, dispose: noop };
  }
  navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
    .then(() => syncNetworkStatus())
    .catch((error: unknown) => setStatus(`Offline worker registration failed: ${error instanceof Error ? error.message : String(error)}`));

  const onBeforeInstall=((event: Event) => {
    event.preventDefault();
    installPrompt = event as BeforeInstallPromptEvent;
    if (installButton) installButton.hidden = false;
    setStatus("Installable on this device.");
  }) as EventListener;
  const onInstalled=():void=>{
    installPrompt = null;
    if (installButton) installButton.hidden = true;
    setStatus("StudyFlow is installed for offline launch.");
  };
  const onInstallClick=async ():Promise<void>=>{
    if (!installPrompt) return;
    await installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    if (installButton) installButton.hidden = true;
    syncNetworkStatus();
  };

  windowObject?.addEventListener("online", syncNetworkStatus);
  windowObject?.addEventListener("offline", syncNetworkStatus);
  windowObject?.addEventListener("beforeinstallprompt", onBeforeInstall);
  windowObject?.addEventListener("appinstalled", onInstalled);
  installButton?.addEventListener("click", onInstallClick);
  syncNetworkStatus();

  return {
    supported:true,
    dispose(){
      windowObject?.removeEventListener("online",syncNetworkStatus);
      windowObject?.removeEventListener("offline",syncNetworkStatus);
      windowObject?.removeEventListener("beforeinstallprompt",onBeforeInstall);
      windowObject?.removeEventListener("appinstalled",onInstalled);
      installButton?.removeEventListener("click",onInstallClick);
      installPrompt=null;
    },
  };
}
