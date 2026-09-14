import assert from "node:assert/strict";
import { createTvRelay, TV_UNIFIED_PLANE_VERSION } from "../scripts/tv_relay.mjs";

assert.equal(TV_UNIFIED_PLANE_VERSION, 1);
const relaySource = await import("node:fs").then(fs => fs.readFileSync(new URL("../scripts/tv_relay.mjs", import.meta.url), "utf8"));
assert.match(relaySource, /validatePlaneEnvelope/);
assert.match(relaySource, /tv-plane-envelope-required/);
assert.match(relaySource, /tv-plane-payload-hash-mismatch/);
assert.doesNotMatch(relaySource, /Bearer /);
assert.doesNotMatch(relaySource, /controllerToken/);
assert.equal(typeof createTvRelay, "function");
console.log("OV21_TV_PLANE_WEB: PASS");
