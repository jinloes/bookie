import { isTauri } from '@tauri-apps/api/core';

/**
 * Opens an external URL (e.g. a OneDrive receipt link) in the user's default system browser.
 *
 * `target="_blank"` anchors are not reliably handled by every OS webview under Tauri — some
 * platforms swallow the navigation instead of handing it to the system browser. The
 * `plugin-shell` `open()` command is the documented, cross-platform-reliable way to do this
 * from a Tauri app, so we use it when running under Tauri and fall back to `window.open` for
 * the plain-browser dev/build target.
 */
export async function openExternalUrl(url) {
  if (!url) {
    return;
  }
  if (isTauri()) {
    const { open } = await import('@tauri-apps/plugin-shell');
    await open(url);
  } else {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}

/**
 * Starts the Outlook OAuth flow. Under Tauri, opens `/api/outlook/connect?client=desktop` in the
 * system browser against the resolved backend origin so the Tauri webview stays loaded and the
 * callback renders a standalone status page instead of redirecting to Vite (:5173). In plain
 * browser mode, navigates directly to `/api/outlook/connect`.
 *
 * @param {(path: string) => Promise<string>} resolveApiUrl
 * @param {{ replace?: boolean }} [options]
 */
export async function openOutlookConnect(resolveApiUrl, { replace = false } = {}) {
  if (isTauri()) {
    const url = await resolveApiUrl('/api/outlook/connect?client=desktop');
    await openExternalUrl(url);
    return;
  }
  if (replace) {
    window.location.replace('/api/outlook/connect');
  } else {
    window.location.assign('/api/outlook/connect');
  }
}
