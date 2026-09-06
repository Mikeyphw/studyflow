"use strict";

function normalizedDuration(value) {
    return Math.max(5, Math.min(240, Number(value) || 25));
}

export function bindPersistentStudyEvents({ store, showToast }) {
    const form = document.getElementById("quick-add-form");
    form?.addEventListener("submit", (event) => {
        event.preventDefault();
        const data = new FormData(form);
        const title = String(data.get("title") || "").trim();
        if (!title) return;
        const activity = store.addActivity({
            title,
            kind: String(data.get("type") || "study"),
            durationMinutes: normalizedDuration(data.get("duration")),
            origin: "manual",
            queue: "today",
        });
        form.reset();
        const durationInput = form.elements.namedItem("duration");
        if (durationInput instanceof HTMLInputElement) durationInput.value = "25";
        form.closest("dialog")?.close();
        const status = store.getPersistenceStatus();
        showToast(status.durable ? `${activity.title} saved locally.` : `${activity.title} added in memory; local persistence is unavailable.`);
    });

    const reset = document.getElementById("reset-local-state");
    reset?.addEventListener("click", () => {
        const confirmed = globalThis.confirm?.("Reset StudyFlow Local to its sample state on this device?") ?? true;
        if (!confirmed) return;
        store.resetLocalState();
        showToast("Local StudyFlow sample state reset.");
    });
}
