#!/usr/bin/env node
// Builds the backend as a self-contained native app-image (jpackage, bundled JRE) and copies it
// into src-tauri/backend-runtime/ so `tauri build` can bundle it as a resource. This is what lets
// a distributed release installer start the backend without the user having a JDK or the
// backend/ Gradle project installed — see start_backend_release() in src-tauri/src/lib.rs.
//
// Not wired into tauri.conf.json's beforeBuildCommand: it's slow (invokes Gradle + jpackage) and
// only needed for real release builds, not everyday `npm run build`/local `tauri build` testing.
// Run manually, or as an explicit step in the release CI workflow, before `tauri build`.
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { platform } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const frontendDir = resolve(__dirname, '..');
const backendDir = resolve(frontendDir, '../backend');
const runtimeDest = join(frontendDir, 'src-tauri', 'backend-runtime');

const jpackageVersion = process.env.BOOKIE_JPACKAGE_VERSION || '1.0.0';
const gradlew = platform() === 'win32' ? 'gradlew.bat' : './gradlew';

console.log(`Building backend app-image (jpackage version ${jpackageVersion})...`);
execFileSync(gradlew, ['jpackageImage', `-PjpackageVersion=${jpackageVersion}`], {
  cwd: backendDir,
  stdio: 'inherit',
});

const jpackageOutDir = join(backendDir, 'build', 'jpackage');
const entries = readdirSync(jpackageOutDir);
if (entries.length !== 1) {
  throw new Error(
    `Expected exactly one entry in ${jpackageOutDir}, found: ${entries.join(', ') || '(none)'}`,
  );
}
const producedImage = join(jpackageOutDir, entries[0]);

console.log(`Copying ${producedImage} -> ${runtimeDest}`);
// Clear out any previously-copied app-image content, but keep .gitkeep so the directory itself
// still exists for Tauri's resource-path validation if this script is re-run locally.
for (const entry of existsSync(runtimeDest) ? readdirSync(runtimeDest) : []) {
  if (entry !== '.gitkeep') {
    rmSync(join(runtimeDest, entry), { recursive: true, force: true });
  }
}
mkdirSync(runtimeDest, { recursive: true });
cpSync(producedImage, join(runtimeDest, entries[0]), { recursive: true });

if (!existsSync(join(runtimeDest, entries[0]))) {
  throw new Error('Copy appears to have failed - backend-runtime is missing the app-image.');
}

console.log('backend-runtime prepared successfully.');
