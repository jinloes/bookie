#!/usr/bin/env node
// Runs `tauri dev` with the IDE config. IDEs launched from the macOS Dock don't inherit the login
// shell's PATH, so cargo and Homebrew's node/npm aren't found. This prepends their usual locations
// to the inherited PATH with the OS path delimiter, which a hardcoded run-configuration PATH can't
// do portably (it clobbered the whole PATH on Windows).
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { homedir, platform } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pathKey = Object.keys(process.env).find((key) => key.toUpperCase() === 'PATH') ?? 'PATH';

const extraDirs = [
  join(process.env.CARGO_HOME ?? join(homedir(), '.cargo'), 'bin'),
  dirname(process.execPath),
  ...(platform() === 'darwin' ? ['/opt/homebrew/bin', '/usr/local/bin'] : []),
].filter(existsSync);

const env = {
  ...process.env,
  [pathKey]: [...extraDirs, process.env[pathKey] ?? ''].filter(Boolean).join(delimiter),
};

const tauriBin = join(
  frontendDir,
  'node_modules',
  '.bin',
  platform() === 'win32' ? 'tauri.cmd' : 'tauri'
);

const child = spawn(
  tauriBin,
  ['dev', '--config', 'src-tauri/tauri.ide.conf.json', ...process.argv.slice(2)],
  { cwd: frontendDir, env, stdio: 'inherit', shell: platform() === 'win32' }
);

child.on('exit', (code, signal) => {
  process.exit(signal ? 1 : (code ?? 1));
});
