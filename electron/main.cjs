const { app, BrowserWindow, dialog } = require("electron");
const { spawn } = require("node:child_process");
const http = require("node:http");
const path = require("node:path");

const APP_URL = process.env.BOOKIE_APP_URL || "http://localhost:8080";
const HEALTH_TIMEOUT_MS = 180000;
const HEALTH_INTERVAL_MS = 1000;

let backendProcess = null;
let backendSpawnedByDesktop = false;
let mainWindow = null;

function isBackendAvailable(url) {
  return new Promise((resolve) => {
    const request = http.get(url, (response) => {
      response.resume();
      resolve(response.statusCode >= 200 && response.statusCode < 500);
    });

    request.on("error", () => resolve(false));
    request.setTimeout(1500, () => {
      request.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend(url, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await isBackendAvailable(url)) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, HEALTH_INTERVAL_MS));
  }
  return false;
}

function startBackend() {
  const repoRoot = path.resolve(__dirname, "..");
  const gradleExecutable = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const args = ["bootRun"];

  backendProcess = spawn(gradleExecutable, args, {
    cwd: repoRoot,
    env: {
      ...process.env,
      LM_STUDIO_PARALLELISM: process.env.LM_STUDIO_PARALLELISM || "1",
    },
    stdio: "inherit",
    shell: process.platform === "win32",
  });

  backendSpawnedByDesktop = true;
}

function stopBackend() {
  if (!backendProcess || !backendSpawnedByDesktop) {
    return;
  }

  if (!backendProcess.killed) {
    backendProcess.kill("SIGTERM");
  }

  backendProcess = null;
  backendSpawnedByDesktop = false;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 700,
    title: "Bookie",
    icon: path.join(__dirname, "icon.png"),
    autoHideMenuBar: true,
    backgroundColor: "#1a1b1e",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.loadFile(path.join(__dirname, "loading.html"));
}

app.whenReady().then(async () => {
  createWindow();

  const backendAlreadyRunning = await isBackendAvailable(APP_URL);
  if (!backendAlreadyRunning) {
    startBackend();
  }

  const backendReady = await waitForBackend(APP_URL, HEALTH_TIMEOUT_MS);
  if (!backendReady) {
    dialog.showErrorBox(
      "Bookie Startup Failed",
      "The local backend did not become ready in time. Check the terminal logs for Gradle and LM Studio startup errors."
    );
    stopBackend();
    app.quit();
    return;
  }

  mainWindow.loadURL(APP_URL);
});

app.on("before-quit", () => {
  stopBackend();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});
