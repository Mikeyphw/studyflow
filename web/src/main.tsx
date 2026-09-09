import * as React from "react";
import { createRoot } from "react-dom/client";
import { createBrowserPersistence } from "./storage";
import { createStudyStore } from "./state";
import type { BrowserPersistence, StudyStore } from "./types";
import { App } from "./react/App";
import { installTvBrowserAdapter } from "./tv";
import { installStudyFlowWebSflpPeer } from "./sflp_link";

const tvAdapter=installTvBrowserAdapter();
installStudyFlowWebSflpPeer(tvAdapter.resolution.enabled?"web_tv":"web");

async function boot():Promise<void>{const node=document.getElementById("root");if(!node)throw new Error("StudyFlow React root is missing");const persistence=await createBrowserPersistence() as BrowserPersistence;const store=(createStudyStore as unknown as (options:{repository:BrowserPersistence["repository"]})=>StudyStore)({repository:persistence.repository});createRoot(node).render(React.createElement(App,{store,persistence}))}
void boot().catch((error:unknown)=>{const node=document.getElementById("root");const message=error instanceof Error?error.message:String(error);if(node)node.textContent=`StudyFlow could not start: ${message}`;console.error(error)});
