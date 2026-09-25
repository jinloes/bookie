# Integrations

[← Architecture index](../../ARCHITECTURE.md)

## Integration Boundaries

- Only the `intake` and `datalifecycle` modules may use `integrations` (see
  [`module-boundaries.md`](module-boundaries.md)). They depend on typed ports in
  `com.bookie.integrations`, not Microsoft Graph, MSAL, Copilot SDK, Spring AI, PDFBox, or Commons
  CSV types. Provider implementations and request
  configuration stay under the corresponding integration package. `ModuleBoundaryTest` rejects
  provider-SDK dependencies outside that boundary and cycles between integration slices.
- Microsoft Graph failures use one typed taxonomy: authentication/authorization failures require
  reconnect, rate limits and server failures are retryable, and missing, conflicting, malformed, or
  otherwise ambiguous results require terminal/manual review. Other ports expose provider-neutral
  exceptions or result types. Adapters do not own retries or workflow state.
- Every Outlook message list, pagination, get, move, and ID-translation request asks Graph for
  immutable IDs. Message identities retain both the existing REST ID and the immutable ID. Existing
  API responses and financial `sourceId` values continue to use the REST ID; no financial record
  is rewritten. Inbox items store the immutable ID in `immutable_source_id`; the intake worker translates
  existing IDs in batches and leaves missing or ambiguous results as visible manual-review jobs.
  OAuth requests include delegated `User.Read`, which Microsoft Graph requires for
  `/me/translateExchangeIds`, in addition to mail and file access.
- Outlook moves first inspect the current parent folder. Being at the destination is success, and a
  missing source triggers only a bounded destination scan before the adapter returns a typed
  outcome. ID translation is batched and rejects partial, duplicate, erroneous, or ambiguous
  responses.
- Receipt-file writes use root-confined deterministic paths and SHA-256 comparisons. Repeating the
  same content reports already-present; different content at the same path reports a conflict and
  is never overwritten. Durable receipt moves require a matching artifact identity and persisted
  checksum before OneDrive can be mutated; missing evidence becomes manual review.

## Tauri Plugins

Installed: `dialog`, `notification`, `window-state`, `autostart`, `single-instance`, `shell`, `updater`, `process`, `store`. Permissions are declared in `frontend/src-tauri/capabilities/default.json`.

- **`single-instance`** — must be the first plugin registered (see `lib.rs`); focuses the existing window instead of letting a second launch spawn a duplicate backend process on the same port.
- **`shell`** — used via `frontend/src/utils/links.js` (`openExternalUrl`) to reliably open external links (e.g. "Open in OneDrive") in the system browser instead of relying on undocumented webview `target="_blank"` handling.
- **`store`** — used via `frontend/src/utils/persistentStore.js` to mirror `useSessionState` values (page filters) to an on-disk `settings.json` store, so they survive a full app restart, not just navigation within one run. Best-effort: no-ops outside Tauri.
- **`updater` / `process`** — wired into the "Updates" section of Settings (`check()` / `downloadAndInstall()` / `relaunch()`). `tauri.conf.json` ships with a placeholder `plugins.updater` config (`pubkey: ""`, `endpoints: []`) — this is required for the plugin to initialize at all (an empty/missing config fails to deserialize and the app won't start), but it means **auto-updates are not functional out of the box**: `check()` will simply fail gracefully and Settings shows "Update check is not available for this build." To enable real updates before shipping a release:
  1. Generate a signing keypair: `npm run tauri signer generate -- -w ~/.tauri/bookie.key` (from `frontend/`).
  2. Add a `plugins.updater` block to `tauri.conf.json` with `endpoints` (e.g. a `latest.json` published alongside GitHub releases) and the generated public key.
  3. Set `TAURI_SIGNING_PRIVATE_KEY` (and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` if used) in the build environment, and add `"bundle": { "createUpdaterArtifacts": true }` so `npm run build:tauri` produces signed update bundles.
  4. Publish `latest.json` + the signed artifacts to wherever `endpoints` points.
  Until this is done, "Check for Updates" in Settings will show "Update check is not available for this build" rather than crashing.

Rust unit tests (`frontend/src-tauri/src/lib.rs`, `#[cfg(test)] mod tests`, run via `cargo test --lib` from `frontend/src-tauri/`) guard against startup regressions, notably `tauri_conf_updater_plugin_config_deserializes`, which parses the real `tauri.conf.json` and deserializes `plugins.updater` using `tauri_plugin_updater::Config` — this fails at test time if the required placeholder block is ever removed, instead of only failing when the packaged app is launched.
