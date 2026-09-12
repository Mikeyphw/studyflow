"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const React = __importStar(require("react"));
const client_1 = require("react-dom/client");
const storage_1 = require("./storage");
const state_1 = require("./state");
const App_1 = require("./react/App");
const tv_1 = require("./tv");
const sflp_link_1 = require("./sflp_link");
const attention_1 = require("./attention");
const tv_attention_1 = require("./tv_attention");
const tvAdapter = (0, tv_1.installTvBrowserAdapter)();
const sflpPeer = (0, sflp_link_1.installStudyFlowWebSflpPeer)(tvAdapter.resolution.enabled ? "web_tv" : "web");
async function boot() { const node = document.getElementById("root"); if (!node)
    throw new Error("StudyFlow React root is missing"); const persistence = await (0, storage_1.createBrowserPersistence)(); const store = state_1.createStudyStore({ repository: persistence.repository }); const attentionRuntime = sflpPeer ? new attention_1.StudyFlowWebAttentionRuntime(store, sflpPeer, { autoStart: true }) : null; const attentionBridge = attentionRuntime ? (0, attention_1.installStudyFlowWebAttentionBridge)(attentionRuntime) : null; const tvAttentionRuntime = tvAdapter.resolution.enabled && attentionRuntime ? new tv_attention_1.StudyFlowWebTvAttentionRuntime(attentionRuntime) : null; const tvAttentionBridge = tvAttentionRuntime ? (0, tv_attention_1.installStudyFlowWebTvAttentionBridge)(tvAttentionRuntime) : null; addEventListener("pagehide", () => { tvAttentionBridge === null || tvAttentionBridge === void 0 ? void 0 : tvAttentionBridge(); attentionBridge === null || attentionBridge === void 0 ? void 0 : attentionBridge.dispose(); attentionRuntime === null || attentionRuntime === void 0 ? void 0 : attentionRuntime.stop(); }, { once: true }); (0, client_1.createRoot)(node).render(React.createElement(App_1.App, { store, persistence, attentionRuntime })); }
void boot().catch((error) => { const node = document.getElementById("root"); const message = error instanceof Error ? error.message : String(error); if (node)
    node.textContent = `StudyFlow could not start: ${message}`; console.error(error); });
