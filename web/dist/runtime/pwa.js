"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.studyFlowPwaCapabilities = studyFlowPwaCapabilities;
exports.notificationPermissionState = notificationPermissionState;
exports.requestStudyFlowNotificationPermission = requestStudyFlowNotificationPermission;
exports.registerStudyFlowServiceWorker = registerStudyFlowServiceWorker;
exports.studyFlowServiceWorkerReady = studyFlowServiceWorkerReady;
exports.showStudyFlowBrowserNotification = showStudyFlowBrowserNotification;
exports.registerStudyFlowPwa = registerStudyFlowPwa;
let registrationPromise = null;
function studyFlowPwaCapabilities(navigatorObject, windowObject) {
    var _a, _b;
    if (navigatorObject === void 0) { navigatorObject = (_a = globalThis.navigator) !== null && _a !== void 0 ? _a : null; }
    if (windowObject === void 0) { windowObject = (_b = globalThis.window) !== null && _b !== void 0 ? _b : null; }
    const nav = navigatorObject;
    return {
        serviceWorker: Boolean(navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker),
        notifications: Boolean(windowObject && "Notification" in windowObject),
        push: Boolean((navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker) && windowObject && "PushManager" in windowObject),
        badge: typeof (nav === null || nav === void 0 ? void 0 : nav.setAppBadge) === "function",
    };
}
function notificationPermissionState(windowObject) {
    var _a;
    if (windowObject === void 0) { windowObject = (_a = globalThis.window) !== null && _a !== void 0 ? _a : null; }
    const api = windowObject === null || windowObject === void 0 ? void 0 : windowObject.Notification;
    if (!api)
        return "unsupported";
    return api.permission;
}
async function requestStudyFlowNotificationPermission(windowObject) {
    var _a;
    if (windowObject === void 0) { windowObject = (_a = globalThis.window) !== null && _a !== void 0 ? _a : null; }
    const api = windowObject === null || windowObject === void 0 ? void 0 : windowObject.Notification;
    if (!api)
        return "unsupported";
    return api.requestPermission();
}
function registerStudyFlowServiceWorker(navigatorObject) {
    var _a;
    if (navigatorObject === void 0) { navigatorObject = (_a = globalThis.navigator) !== null && _a !== void 0 ? _a : null; }
    if (!(navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker))
        return Promise.resolve(null);
    if (!registrationPromise) {
        registrationPromise = navigatorObject.serviceWorker.register("./service-worker.js", { scope: "./" })
            .catch(() => { registrationPromise = null; return null; });
    }
    return registrationPromise;
}
async function studyFlowServiceWorkerReady(navigatorObject) {
    var _a;
    if (navigatorObject === void 0) { navigatorObject = (_a = globalThis.navigator) !== null && _a !== void 0 ? _a : null; }
    if (!(navigatorObject === null || navigatorObject === void 0 ? void 0 : navigatorObject.serviceWorker))
        return null;
    const registered = await registerStudyFlowServiceWorker(navigatorObject);
    if (!registered)
        return null;
    try {
        return await navigatorObject.serviceWorker.ready;
    }
    catch {
        return registered;
    }
}
async function showStudyFlowBrowserNotification(title, options) {
    if (notificationPermissionState() !== "granted")
        return false;
    const registration = await studyFlowServiceWorkerReady();
    if (!registration)
        return false;
    await registration.showNotification(title, options);
    return true;
}
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
    registerStudyFlowServiceWorker(navigatorObject)
        .then((registration) => registration ? syncNetworkStatus() : setStatus("Offline worker registration failed."));
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
