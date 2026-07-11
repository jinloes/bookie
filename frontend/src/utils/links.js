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
