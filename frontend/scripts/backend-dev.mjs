#!/usr/bin/env node
// Runs `gradlew bootRun` in backend/. npm runs scripts through cmd.exe on Windows, which can't
// execute `./gradlew`, so pick the platform's wrapper here instead of in package.json.
import { spawn } from 'node:child_process';
import { platform } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend');

// Node refuses to spawn .bat files without a shell on Windows; the absolute path avoids relying on
// cmd searching the current directory, which NoDefaultCurrentDirectoryInExePath disables.
const [command, args, options] =
  platform() === 'win32'
    ? [
        'cmd.exe',
        ['/d', '/s', '/c', `"${join(backendDir, 'gradlew.bat')}" bootRun`],
        { windowsVerbatimArguments: true },
      ]
    : [join(backendDir, 'gradlew'), ['bootRun'], {}];

const child = spawn(command, args, { cwd: backendDir, stdio: 'inherit', ...options });

child.on('exit', (code, signal) => {
  process.exit(signal ? 1 : (code ?? 1));
});
