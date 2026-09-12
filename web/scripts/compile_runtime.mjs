import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(new URL("..", import.meta.url).pathname);
const tracked = join(root, "runtime");
const manifestPath = join(root, "runtime-parity-manifest.json");
const temp = mkdtempSync(join(tmpdir(), "studyflow-ts-"));

function files(dir, base = dir, accept = (path) => path.endsWith(".js")) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) out.push(...files(path, base, accept));
    else if (accept(path)) out.push(relative(base, path));
  }
  return out.sort();
}

function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function verifyGeneratedRuntime(generatedRoot) {
  const generated = files(generatedRoot);
  const committed = files(tracked);
  if (JSON.stringify(generated) !== JSON.stringify(committed)) {
    console.error("Tracked runtime file list differs from TypeScript output", { generated, committed });
    return false;
  }
  for (const rel of generated) {
    if (!readFileSync(join(generatedRoot, rel)).equals(readFileSync(join(tracked, rel)))) {
      console.error(`Tracked runtime is stale: ${rel}`);
      return false;
    }
  }
  console.log(`TypeScript runtime parity OK (${generated.length} files)`);
  return true;
}

function verifySealedManifest(reason) {
  if (!existsSync(manifestPath)) {
    console.error(`TypeScript compiler unavailable (${reason}) and sealed runtime parity manifest is missing.`);
    return false;
  }
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const sourceRoot = join(root, "src");
  const sourceFiles = files(sourceRoot, sourceRoot, (path) => path.endsWith(".ts") || path.endsWith(".tsx"));
  const runtimeFiles = files(tracked);
  const expectedSourceFiles = Object.keys(manifest.sources || {}).sort();
  const expectedRuntimeFiles = Object.keys(manifest.runtime || {}).sort();
  if (JSON.stringify(sourceFiles) !== JSON.stringify(expectedSourceFiles)) {
    console.error("Sealed TypeScript source file list differs from the current tree.");
    return false;
  }
  if (JSON.stringify(runtimeFiles) !== JSON.stringify(expectedRuntimeFiles)) {
    console.error("Sealed runtime file list differs from the current tree.");
    return false;
  }
  if (manifest.tsconfigSha256 !== sha256(join(root, "tsconfig.json"))) {
    console.error("tsconfig.json differs from the sealed runtime parity manifest.");
    return false;
  }
  if (manifest.packageSha256 !== sha256(join(root, "package.json"))) {
    console.error("package.json differs from the sealed runtime parity manifest.");
    return false;
  }
  for (const rel of sourceFiles) {
    if (manifest.sources[rel] !== sha256(join(sourceRoot, rel))) {
      console.error(`TypeScript source differs from sealed runtime parity manifest: ${rel}`);
      return false;
    }
  }
  for (const rel of runtimeFiles) {
    if (manifest.runtime[rel] !== sha256(join(tracked, rel))) {
      console.error(`Committed runtime differs from sealed runtime parity manifest: ${rel}`);
      return false;
    }
  }
  console.log(
    `TypeScript runtime parity OK (${runtimeFiles.length} files; sealed manifest fallback: ${reason})`,
  );
  return true;
}

function compilerCandidates() {
  const candidates = [];
  if (process.env.STUDYFLOW_TSC) {
    candidates.push({ label: "STUDYFLOW_TSC", command: process.env.STUDYFLOW_TSC, prefix: [] });
  }
  const localTsc = join(root, "node_modules", "typescript", "bin", "tsc");
  if (existsSync(localTsc)) {
    candidates.push({ label: "local typescript", command: process.execPath, prefix: [localTsc] });
  }
  candidates.push({ label: "PATH tsc", command: "tsc", prefix: [] });
  return candidates;
}

try {
  let environmentFailure = "compiler executable unavailable";
  let attemptedCompiler = false;
  for (const candidate of compilerCandidates()) {
    const result = spawnSync(
      candidate.command,
      [...candidate.prefix, "-p", join(root, "tsconfig.json"), "--outDir", temp, "--pretty", "false"],
      { cwd: root, encoding: "utf8" },
    );
    if (result.error?.code === "ENOENT") {
      environmentFailure = `${candidate.label} not found`;
      continue;
    }
    attemptedCompiler = true;
    if (result.status === 0) {
      process.exitCode = verifyGeneratedRuntime(temp) ? 0 : 1;
      break;
    }
    const diagnostic = `${result.stderr || ""}${result.stdout || ""}`.trim();
    if (diagnostic) {
      process.stderr.write(`${diagnostic}\n`);
      process.exitCode = result.status ?? 1;
      break;
    }
    environmentFailure = result.signal
      ? `${candidate.label} terminated by ${result.signal}`
      : result.error
        ? `${candidate.label} failed: ${result.error.message}`
        : `${candidate.label} exited without diagnostics`;
    process.exitCode = verifySealedManifest(environmentFailure) ? 0 : 1;
    break;
  }
  if (!attemptedCompiler && process.exitCode === undefined) {
    process.exitCode = verifySealedManifest(environmentFailure) ? 0 : 1;
  }
} finally {
  rmSync(temp, { recursive: true, force: true });
}
