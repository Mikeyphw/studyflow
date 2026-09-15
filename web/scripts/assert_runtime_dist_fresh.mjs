#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const root = path.resolve(__dirname, '..');
const pairs = [
  ['runtime/indexeddb.js', 'dist/runtime/indexeddb.js'],
  ['runtime/storage.js', 'dist/runtime/storage.js'],
  ['runtime/state.js', 'dist/runtime/state.js'],
  ['runtime/attention.js', 'dist/runtime/attention.js'],
  ['runtime/main.js', 'dist/runtime/main.js'],
  ['runtime/react/App.js', 'dist/runtime/react/App.js'],
  ['runtime/tv_mock_exam.js', 'dist/runtime/tv_mock_exam.js'],
];
function hash(rel) {
  const file = path.join(root, rel);
  if (!fs.existsSync(file)) throw new Error(`missing ${rel}`);
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}
const stale = [];
for (const [runtime, dist] of pairs) {
  if (hash(runtime) !== hash(dist)) stale.push(`${dist} is stale against ${runtime}`);
}
const manifest = path.join(root, 'dist', 'release-manifest.json');
if (!fs.existsSync(manifest)) stale.push('missing dist/release-manifest.json');
else {
  const raw = fs.readFileSync(manifest, 'utf8');
  if (!raw.includes('runtime/indexeddb.js') || !raw.includes('service-worker.js')) {
    stale.push('release manifest omits runtime/service-worker assets');
  }
}
if (stale.length) {
  console.error('WEB_STALE_DIST_RELEASE_INTEGRITY: FAIL');
  for (const item of stale) console.error('-', item);
  process.exit(1);
}
console.log(`WEB_STALE_DIST_RELEASE_INTEGRITY: PASS (${pairs.length} runtime/dist pairs)`);
