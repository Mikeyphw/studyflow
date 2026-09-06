import { bindPersistentStudyEvents } from "./src/events.js";
import { renderStudyState } from "./src/render.js";
import { createStudyStore } from "./src/state.js";
import { createBrowserRepository } from "./src/storage.js";

(() => {
    "use strict";

    const SCREEN_IDS = ["today", "plan", "practice", "progress"];
    const DEFAULT_TABS = {
        plan: "plan-overview",
        practice: "practice-reviews",
        progress: "progress-summary",
    };
    const FOCUS_DEFAULT_MS = 25 * 60 * 1000;

    const uiState = {
        screen: "today",
        tabs: { ...DEFAULT_TABS },
        scheduleFilter: "all",
        focus: {
            remainingMs: FOCUS_DEFAULT_MS,
            endsAt: null,
            running: false,
        },
    };

    let focusInterval = null;
    let toastTimeout = null;

    function byId(id) {
        return document.getElementById(id);
    }

    function screenForTarget(targetId) {
        if (SCREEN_IDS.includes(targetId)) return targetId;
        if (targetId.startsWith("plan-")) return "plan";
        if (targetId.startsWith("practice-")) return "practice";
        if (targetId.startsWith("progress-")) return "progress";
        return "today";
    }

    function activateTab(group, targetId) {
        const panel = byId(targetId);
        if (!panel || panel.dataset.tabPanel !== group) return;

        document.querySelectorAll(`[data-tab-panel="${group}"]`).forEach((candidate) => {
            candidate.hidden = candidate.id !== targetId;
        });
        document.querySelectorAll(`[data-tab-nav="${group}"] [data-tab]`).forEach((link) => {
            if (link.dataset.tab === targetId) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
        uiState.tabs[group] = targetId;
    }

    function showScreen(screenId, targetId = null) {
        const nextScreen = SCREEN_IDS.includes(screenId) ? screenId : "today";
        document.querySelectorAll("[data-screen]").forEach((screen) => {
            screen.hidden = screen.dataset.screen !== nextScreen;
        });
        document.querySelectorAll("[data-screen-link]").forEach((link) => {
            if (link.dataset.screenLink === nextScreen) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
        uiState.screen = nextScreen;

        if (DEFAULT_TABS[nextScreen]) {
            const requested = targetId && byId(targetId)?.dataset.tabPanel === nextScreen
                ? targetId
                : uiState.tabs[nextScreen] || DEFAULT_TABS[nextScreen];
            activateTab(nextScreen, requested);
        }
    }

    function routeFromHash({ replace = false } = {}) {
        const targetId = window.location.hash.slice(1) || "today";
        const screenId = screenForTarget(targetId);
        showScreen(screenId, targetId);
        if (!window.location.hash && replace) {
            history.replaceState(null, "", "#today");
        }
    }

    function openDialog(id) {
        const dialog = byId(id);
        if (!(dialog instanceof HTMLDialogElement)) return;
        if (!dialog.open) dialog.showModal();
        const firstInput = dialog.querySelector("input, select, button");
        firstInput?.focus();
    }

    function closeDialog(button) {
        const dialog = button.closest("dialog");
        if (dialog instanceof HTMLDialogElement) dialog.close();
    }

    function showToast(message) {
        const region = byId("toast-region");
        if (!region) return;
        window.clearTimeout(toastTimeout);
        region.replaceChildren();
        const toast = document.createElement("div");
        toast.className = "toast";
        toast.textContent = message;
        region.append(toast);
        toastTimeout = window.setTimeout(() => region.replaceChildren(), 2600);
    }

    function applyScheduleFilter(filter) {
        const normalized = ["all", "study", "questions", "review"].includes(filter) ? filter : "all";
        uiState.scheduleFilter = normalized;
        document.querySelectorAll("[data-schedule-filter]").forEach((button) => {
            button.setAttribute("aria-pressed", String(button.dataset.scheduleFilter === normalized));
        });
        document.querySelectorAll("[data-schedule-kind]").forEach((row) => {
            row.hidden = normalized !== "all" && row.dataset.scheduleKind !== normalized;
        });
    }

    function formatDuration(milliseconds) {
        const totalSeconds = Math.max(0, Math.ceil(milliseconds / 1000));
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
    }

    function currentRemainingMs() {
        if (!uiState.focus.running || uiState.focus.endsAt === null) {
            return uiState.focus.remainingMs;
        }
        return Math.max(0, uiState.focus.endsAt - Date.now());
    }

    function renderFocus() {
        const remaining = currentRemainingMs();
        const output = byId("focus-timer");
        if (output) output.textContent = formatDuration(remaining);

        const start = byId("focus-start");
        const pause = byId("focus-pause");
        if (start) {
            start.textContent = uiState.focus.running ? "Running" : remaining < FOCUS_DEFAULT_MS ? "Resume" : "Start";
            start.disabled = uiState.focus.running;
        }
        if (pause) pause.disabled = !uiState.focus.running;

        if (uiState.focus.running && remaining <= 0) {
            uiState.focus.running = false;
            uiState.focus.endsAt = null;
            uiState.focus.remainingMs = 0;
            stopFocusTicker();
            showToast("Focus block complete. W06 will turn completion into durable evidence.");
        }
    }

    function startFocusTicker() {
        stopFocusTicker();
        focusInterval = window.setInterval(renderFocus, 250);
    }

    function stopFocusTicker() {
        if (focusInterval !== null) {
            window.clearInterval(focusInterval);
            focusInterval = null;
        }
    }

    function startFocus() {
        const remaining = currentRemainingMs();
        if (remaining <= 0) uiState.focus.remainingMs = FOCUS_DEFAULT_MS;
        uiState.focus.endsAt = Date.now() + uiState.focus.remainingMs;
        uiState.focus.running = true;
        startFocusTicker();
        renderFocus();
    }

    function pauseFocus() {
        uiState.focus.remainingMs = currentRemainingMs();
        uiState.focus.endsAt = null;
        uiState.focus.running = false;
        stopFocusTicker();
        renderFocus();
    }

    function resetFocus() {
        uiState.focus.remainingMs = FOCUS_DEFAULT_MS;
        uiState.focus.endsAt = null;
        uiState.focus.running = false;
        stopFocusTicker();
        renderFocus();
    }

    function checkPracticeAnswer(event) {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const answer = String(data.get("answer") || "");
        const feedback = byId("practice-feedback");
        if (!feedback) return;
        if (!answer) {
            feedback.dataset.state = "incorrect";
            feedback.textContent = "Choose an answer first.";
            return;
        }
        const correct = answer === "main";
        feedback.dataset.state = correct ? "correct" : "incorrect";
        feedback.textContent = correct
            ? "Correct — <main> represents the document's primary content."
            : "Not quite. The correct answer is <main>.";
    }

    function bindNavigation() {
        document.querySelectorAll("[data-screen-link]").forEach((link) => {
            link.addEventListener("click", () => {
                showScreen(link.dataset.screenLink || "today");
            });
        });

        document.querySelectorAll("[data-tab-nav] [data-tab]").forEach((link) => {
            link.addEventListener("click", () => {
                const nav = link.closest("[data-tab-nav]");
                const group = nav?.dataset.tabNav;
                if (group && link.dataset.tab) {
                    showScreen(group, link.dataset.tab);
                }
            });
        });

        document.querySelectorAll("[data-route-target]").forEach((link) => {
            link.addEventListener("click", () => {
                const target = link.dataset.routeTarget;
                if (target) showScreen(screenForTarget(target), target);
            });
        });

        window.addEventListener("hashchange", () => routeFromHash());
    }

    function bindDialogs() {
        document.querySelectorAll("[data-open-dialog]").forEach((button) => {
            button.addEventListener("click", () => openDialog(button.dataset.openDialog || ""));
        });
        document.querySelectorAll("[data-close-dialog]").forEach((button) => {
            button.addEventListener("click", () => closeDialog(button));
        });
    }

    function bindSchedule() {
        document.querySelectorAll("[data-schedule-filter]").forEach((button) => {
            button.addEventListener("click", () => applyScheduleFilter(button.dataset.scheduleFilter || "all"));
        });
    }

    function bindFocus() {
        byId("focus-start")?.addEventListener("click", startFocus);
        byId("focus-pause")?.addEventListener("click", pauseFocus);
        byId("focus-reset")?.addEventListener("click", resetFocus);
    }

    function init() {
        const store = createStudyStore({ repository: createBrowserRepository() });
        const render = () => renderStudyState(store.getState(), store.getPersistenceStatus());

        bindNavigation();
        bindDialogs();
        bindSchedule();
        bindFocus();
        bindPersistentStudyEvents({ store, showToast });
        store.subscribe(render);
        byId("practice-question-form")?.addEventListener("submit", checkPracticeAnswer);
        routeFromHash({ replace: true });
        Object.entries(DEFAULT_TABS).forEach(([group, target]) => activateTab(group, uiState.tabs[group] || target));
        applyScheduleFilter("all");
        renderFocus();
        render();
    }

    document.addEventListener("DOMContentLoaded", init);
})();
