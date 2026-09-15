"use strict";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: string; platform?: string }>;
}

export interface StudyFlowPwaCapabilities {
  serviceWorker: boolean;
  notifications: boolean;
  push: boolean;
  badge: boolean;
}

export interface PwaRegistrationOptions {
  statusElement?: HTMLElement | null;
  installButton?: HTMLButtonElement | null;
  navigatorObject?: Navigator | null;
  windowObject?: Window | null;
}

export interface PwaRegistrationHandle { supported:boolean; dispose():void; }

let registrationPromise: Promise<ServiceWorkerRegistration | null> | null = null;

export function studyFlowPwaCapabilities(
  navigatorObject: Navigator | null = globalThis.navigator ?? null,
  windowObject: Window | null = globalThis.window ?? null,
): StudyFlowPwaCapabilities {
  const nav = navigatorObject as (Navigator & { setAppBadge?: (value?: number) => Promise<void> }) | null;
  return {
    serviceWorker: Boolean(navigatorObject?.serviceWorker),
    notifications: Boolean(windowObject && "Notification" in windowObject),
    push: Boolean(navigatorObject?.serviceWorker && windowObject && "PushManager" in windowObject),
    badge: typeof nav?.setAppBadge === "function",
  };
}

export function notificationPermissionState(windowObject: Window | null = globalThis.window ?? null): NotificationPermission | "unsupported" {
  const api = (windowObject as (Window & { Notification?: typeof Notification }) | null)?.Notification;
  if (!api) return "unsupported";
  return api.permission;
}

export async function requestStudyFlowNotificationPermission(windowObject: Window | null = globalThis.window ?? null): Promise<NotificationPermission | "unsupported"> {
  const api = (windowObject as (Window & { Notification?: typeof Notification }) | null)?.Notification;
  if (!api) return "unsupported";
  return api.requestPermission();
}

export function registerStudyFlowServiceWorker(
  navigatorObject: Navigator | null = globalThis.navigator ?? null,
): Promise<ServiceWorkerRegistration | null> {
  if (!navigatorObject?.serviceWorker) return Promise.resolve(null);
  if (!registrationPromise) {
    registrationPromise = navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
      .catch(() => { registrationPromise = null; return null; });
  }
  return registrationPromise;
}

export async function studyFlowServiceWorkerReady(
  navigatorObject: Navigator | null = globalThis.navigator ?? null,
): Promise<ServiceWorkerRegistration | null> {
  if (!navigatorObject?.serviceWorker) return null;
  const registered = await registerStudyFlowServiceWorker(navigatorObject);
  if (!registered) return null;
  try { return await navigatorObject.serviceWorker.ready; }
  catch { return registered; }
}

export async function showStudyFlowBrowserNotification(title: string, options: NotificationOptions): Promise<boolean> {
  if (notificationPermissionState() !== "granted") return false;
  const registration = await studyFlowServiceWorkerReady();
  if (!registration) return false;
  await registration.showNotification(title, options);
  return true;
}

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
  registerStudyFlowServiceWorker(navigatorObject)
    .then((registration) => registration ? syncNetworkStatus() : setStatus("Offline worker registration failed."));

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
