use std::net::TcpStream;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_notification::NotificationExt;

const BACKEND_PORT: u16 = 48763;
const HEALTH_TIMEOUT_SECS: u64 = 180;
// Once the backend is up, how often the supervisor thread polls to notice an unexpected exit.
const SUPERVISION_POLL_SECS: u64 = 5;
// Give up auto-restarting after this many consecutive failures so we don't loop forever on a
// backend that's permanently broken (e.g. corrupt DB, missing JDK).
const MAX_BACKEND_RESTART_ATTEMPTS: u32 = 3;

static BACKEND_PROCESS: Mutex<Option<Child>> = Mutex::new(None);

fn is_backend_available() -> bool {
    TcpStream::connect(format!("127.0.0.1:{BACKEND_PORT}"))
        .map(|_| true)
        .unwrap_or(false)
}

fn wait_for_backend() -> bool {
    let deadline = Instant::now() + Duration::from_secs(HEALTH_TIMEOUT_SECS);
    while Instant::now() < deadline {
        if is_backend_available() {
            return true;
        }
        std::thread::sleep(Duration::from_secs(1));
    }
    false
}

fn backend_root() -> std::path::PathBuf {
    // Allow explicit override via env var (useful for CI and custom installs)
    if let Ok(root) = std::env::var("BOOKIE_BACKEND_ROOT") {
        return std::path::PathBuf::from(root);
    }
    // Backward-compatible override (previous variable name before backend/ split)
    if let Ok(root) = std::env::var("BOOKIE_REPO_ROOT") {
        return std::path::PathBuf::from(root);
    }
    // Walk up from the executable and look for either:
    // - backend/gradlew (current layout)
    // - gradlew (legacy layout)
    let mut dir = std::env::current_exe()
        .expect("cannot resolve executable path")
        .parent()
        .expect("executable has no parent directory")
        .to_path_buf();

    // Walk up to the filesystem root looking for gradlew
    loop {
        let nested_backend = dir.join("backend");
        if nested_backend.join("gradlew").exists() {
            return nested_backend;
        }
        if dir.join("gradlew").exists() {
            return dir;
        }
        match dir.parent() {
            Some(p) => dir = p.to_path_buf(),
            None => break, // Reached filesystem root
        }
    }

    // Fallback: return current directory; error will be caught when trying to spawn backend
    std::env::current_dir().expect("cannot resolve current directory")
}

// Locates the jpackage-produced native backend launcher bundled as a Tauri resource (see
// backend/build.gradle's `jpackageImage` task and frontend/scripts/prepare-backend-runtime.mjs,
// which normalizes the per-OS jpackage app-image layout into a fixed `backend-runtime/` folder
// before `tauri build` bundles it). Only used in release builds — a distributed app bundle
// doesn't include the backend/ Gradle project or a JDK, so `gradlew bootRun` (the dev-mode path
// below) isn't viable there.
#[cfg(not(debug_assertions))]
fn release_backend_binary(app: &AppHandle) -> Option<std::path::PathBuf> {
    let resource_dir = app.path().resource_dir().ok()?;
    let base = resource_dir.join("backend-runtime");
    let candidate = if cfg!(target_os = "macos") {
        base.join("bookie-backend.app/Contents/MacOS/bookie-backend")
    } else if cfg!(target_os = "windows") {
        base.join("bookie-backend/bookie-backend.exe")
    } else {
        base.join("bookie-backend/bin/bookie-backend")
    };
    if candidate.exists() {
        Some(candidate)
    } else {
        None
    }
}

fn start_backend(app: &AppHandle, data_dir: Option<std::path::PathBuf>) -> bool {
    #[cfg(debug_assertions)]
    {
        let _ = app;
        start_backend_dev(data_dir)
    }
    #[cfg(not(debug_assertions))]
    {
        start_backend_release(app, data_dir)
    }
}

// Dev-mode fallback: runs the backend straight out of the Gradle project via `gradlew bootRun`.
// Not used in release builds (see start_backend_release), but kept available so a locally built
// release binary can still be pointed at a source checkout via BOOKIE_BACKEND_ROOT for testing.
fn start_backend_dev(data_dir: Option<std::path::PathBuf>) -> bool {
    let root = backend_root();
    eprintln!("Starting backend from: {}", root.display());

    let gradlew_path = if cfg!(target_os = "windows") {
        root.join("gradlew.bat")
    } else {
        root.join("gradlew")
    };

    if !gradlew_path.exists() {
        eprintln!(
            "ERROR: gradlew not found at {}. Set BOOKIE_BACKEND_ROOT environment variable.",
            gradlew_path.display()
        );
        return false;
    }

    let (cmd, args): (&str, &[&str]) = if cfg!(target_os = "windows") {
        ("gradlew.bat", &["bootRun"])
    } else {
        ("./gradlew", &["bootRun"])
    };

    let mut command = Command::new(cmd);
    command.args(args).current_dir(&root);
    if let Some(dir) = data_dir {
        command.env("BOOKIE_DATA_DIR", dir);
    }

    match command.spawn() {
        Ok(child) => {
            eprintln!("Backend started successfully");
            *BACKEND_PROCESS.lock().unwrap() = Some(child);
            true
        }
        Err(e) => {
            eprintln!(
                "ERROR: Failed to start backend from {}: {}. Set BOOKIE_BACKEND_ROOT=/path/to/backend",
                root.display(),
                e
            );
            false
        }
    }
}

// Release-mode startup: launches the jpackage-produced native binary bundled as a Tauri
// resource. This ships its own JRE, so no system Java or Gradle project is required on the
// user's machine.
#[cfg(not(debug_assertions))]
fn start_backend_release(app: &AppHandle, data_dir: Option<std::path::PathBuf>) -> bool {
    let Some(binary) = release_backend_binary(app) else {
        eprintln!(
            "ERROR: bundled backend-runtime not found in app resources. \
             Run `node scripts/prepare-backend-runtime.mjs` before `tauri build`."
        );
        return false;
    };
    eprintln!("Starting backend from: {}", binary.display());

    let mut command = Command::new(&binary);
    if let Some(dir) = data_dir {
        command.env("BOOKIE_DATA_DIR", dir);
    }

    match command.spawn() {
        Ok(child) => {
            eprintln!("Backend started successfully");
            *BACKEND_PROCESS.lock().unwrap() = Some(child);
            true
        }
        Err(e) => {
            eprintln!("ERROR: Failed to start bundled backend at {}: {}", binary.display(), e);
            false
        }
    }
}

fn stop_backend() {
    if let Some(mut child) = BACKEND_PROCESS.lock().unwrap().take() {
        let _ = child.kill();
    }
}

// Only meaningful in release builds where Rust owns the backend process (in dev, Gradle is
// started by `beforeDevCommand` and we never hold a Child handle for it). Polls for the backend
// process exiting unexpectedly — e.g. it crashes after startup — and attempts a bounded number
// of automatic restarts with a notification, falling back to a blocking error dialog once
// restarts are exhausted so the user isn't left staring at a dead app with no explanation.
#[cfg(not(debug_assertions))]
fn supervise_backend(app_handle: AppHandle, data_dir: Option<std::path::PathBuf>) {
    let mut restart_attempts: u32 = 0;
    loop {
        std::thread::sleep(Duration::from_secs(SUPERVISION_POLL_SECS));

        let exited = {
            let mut guard = BACKEND_PROCESS.lock().unwrap();
            match guard.as_mut() {
                Some(child) => matches!(child.try_wait(), Ok(Some(_))),
                None => false,
            }
        };

        if !exited {
            restart_attempts = 0; // backend has been healthy since the last restart; reset backoff
            continue;
        }

        eprintln!("Backend process exited unexpectedly");
        if restart_attempts >= MAX_BACKEND_RESTART_ATTEMPTS {
            app_handle
                .dialog()
                .message(
                    "Bookie's backend stopped unexpectedly and could not be restarted \
                     automatically after several attempts.\n\
                     Please restart Bookie or check the logs.",
                )
                .title("Bookie Backend Crashed")
                .blocking_show();
            // Keep polling rather than exiting the app outright — the user may fix the
            // underlying issue and relaunch manually, or want to keep the window open to
            // read error state already loaded in the UI.
            continue;
        }

        restart_attempts += 1;
        let _ = app_handle
            .notification()
            .builder()
            .title("Bookie")
            .body(format!(
                "Backend stopped — attempting restart ({restart_attempts}/{MAX_BACKEND_RESTART_ATTEMPTS})"
            ))
            .show();

        if start_backend(&app_handle, data_dir.clone()) && wait_for_backend() {
            eprintln!("Backend restarted successfully");
        } else {
            eprintln!("Backend restart attempt {restart_attempts} failed");
        }
    }
}

fn show_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
    }
}

fn format_tray_tooltip(count: u32) -> String {
    if count > 0 {
        format!("Bookie — {count} pending item{}", if count == 1 { "" } else { "s" })
    } else {
        "Bookie".to_string()
    }
}

#[tauri::command]
fn update_tray_tooltip(app: AppHandle, count: u32) {
    if let Some(tray) = app.tray_by_id("main") {
        let tooltip = format_tray_tooltip(count);
        let _ = tray.set_tooltip(Some(&tooltip));
    }
}

#[tauri::command]
fn get_backend_url() -> String {
    format!("http://localhost:{}", BACKEND_PORT)
}


#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default();

    // Must be the first plugin registered. When a second instance is launched (e.g. via
    // autostart re-triggering, or the user double-clicking the app icon again), this callback
    // runs in the *original* process instead of a new process starting — which would otherwise
    // race the first instance for BACKEND_PORT and spawn a duplicate backend.
    #[cfg(desktop)]
    {
        builder = builder.plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            show_main_window(app);
        }));
    }

    builder
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            None,
        ))
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_store::Builder::default().build())
        .invoke_handler(tauri::generate_handler![update_tray_tooltip, get_backend_url])
        .setup(|app| {
            // Resolve the platform-correct app data directory to pass to the backend.
            let data_dir = app.path().app_data_dir().ok();
            let supervisor_data_dir = data_dir.clone();

            // Build system tray
            let quit = MenuItem::with_id(app, "quit", "Quit Bookie", true, None::<&str>)?;
            let open = MenuItem::with_id(app, "open", "Open Bookie", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &quit])?;
            TrayIconBuilder::with_id("main")
                .tooltip("Bookie")
                .icon(app.default_window_icon().unwrap().clone())
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_main_window(app),
                    "quit" => {
                        stop_backend();
                        app.exit(0);
                    }
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        show_main_window(tray.app_handle());
                    }
                })
                .build(app)?;

            let window = app.get_webview_window("main").unwrap();
            let app_handle = app.handle().clone();
            let supervisor_app_handle = app.handle().clone();
            std::thread::spawn(move || {
                // In debug (dev) builds, beforeDevCommand starts the backend via Gradle.
                // We just wait for it to become available. In release builds, Rust owns
                // the backend process lifecycle.
                #[cfg(not(debug_assertions))]
                if !is_backend_available() {
                    start_backend(&app_handle, data_dir);
                }
                if wait_for_backend() {
                    window.show().unwrap();
                    // Only watch for crashes in release builds, where BACKEND_PROCESS is
                    // actually populated with a child we spawned (and can restart).
                    #[cfg(not(debug_assertions))]
                    std::thread::spawn(move || {
                        supervise_backend(supervisor_app_handle, supervisor_data_dir);
                    });
                } else {
                    stop_backend();
                    app_handle
                        .dialog()
                        .message(
                            "The backend did not become ready in time.\n\
                             Check the terminal for Gradle startup errors.",
                        )
                        .title("Bookie Startup Failed")
                        .blocking_show();
                    app_handle.exit(1);
                }
            });
            Ok(())
        })
        .on_window_event(|window, event| match event {
            // Hide to tray instead of closing the window — backend keeps running.
            tauri::WindowEvent::CloseRequested { api, .. } => {
                api.prevent_close();
                let _ = window.hide();
            }
            // Window is actually being destroyed (app exit) — stop the backend.
            tauri::WindowEvent::Destroyed => {
                stop_backend();
            }
            _ => {}
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Mutex as StdMutex;

    // std::env::set_var affects the whole process, so tests that touch BOOKIE_BACKEND_ROOT /
    // BOOKIE_REPO_ROOT must not run concurrently with each other (cargo test runs tests in
    // parallel threads by default within one binary).
    static ENV_TEST_GUARD: StdMutex<()> = StdMutex::new(());

    // Regression test for the startup crash reported in production: registering
    // tauri_plugin_updater requires *some* `plugins.updater` block in tauri.conf.json — if it's
    // missing (or explicitly `null`), Tauri passes `null` to the plugin's config deserializer and
    // the whole app panics at startup with `PluginInitialization("updater", "invalid type: null,
    // expected struct Config")`. This test parses the real shipped config file and deserializes
    // the `plugins.updater` section using the plugin's own `Config` type, so any future edit that
    // removes/breaks this block fails at test time instead of at app launch.
    #[test]
    fn tauri_conf_updater_plugin_config_deserializes() {
        let raw = include_str!("../tauri.conf.json");
        let conf: serde_json::Value =
            serde_json::from_str(raw).expect("tauri.conf.json must be valid JSON");

        let updater_value = conf
            .get("plugins")
            .and_then(|plugins| plugins.get("updater"))
            .cloned()
            .expect(
                "tauri.conf.json must have a plugins.updater block — the updater plugin is \
                 registered in run() and crashes at startup on a missing/null config",
            );

        serde_json::from_value::<tauri_plugin_updater::Config>(updater_value).expect(
            "plugins.updater in tauri.conf.json must deserialize into tauri_plugin_updater::Config",
        );
    }

    #[test]
    fn tauri_conf_parses_as_valid_json() {
        let raw = include_str!("../tauri.conf.json");
        serde_json::from_str::<serde_json::Value>(raw).expect("tauri.conf.json must be valid JSON");
    }

    #[test]
    fn format_tray_tooltip_no_pending_items() {
        assert_eq!(format_tray_tooltip(0), "Bookie");
    }

    #[test]
    fn format_tray_tooltip_singular() {
        assert_eq!(format_tray_tooltip(1), "Bookie — 1 pending item");
    }

    #[test]
    fn format_tray_tooltip_plural() {
        assert_eq!(format_tray_tooltip(5), "Bookie — 5 pending items");
    }

    #[test]
    fn get_backend_url_points_at_expected_port() {
        assert_eq!(get_backend_url(), format!("http://localhost:{BACKEND_PORT}"));
    }

    #[test]
    fn backend_root_respects_explicit_override() {
        let _guard = ENV_TEST_GUARD.lock().unwrap();
        std::env::remove_var("BOOKIE_REPO_ROOT");
        std::env::set_var("BOOKIE_BACKEND_ROOT", "/tmp/custom-backend-root");
        let root = backend_root();
        std::env::remove_var("BOOKIE_BACKEND_ROOT");
        assert_eq!(root, std::path::PathBuf::from("/tmp/custom-backend-root"));
    }

    #[test]
    fn backend_root_respects_legacy_override() {
        let _guard = ENV_TEST_GUARD.lock().unwrap();
        std::env::remove_var("BOOKIE_BACKEND_ROOT");
        std::env::set_var("BOOKIE_REPO_ROOT", "/tmp/legacy-repo-root");
        let root = backend_root();
        std::env::remove_var("BOOKIE_REPO_ROOT");
        assert_eq!(root, std::path::PathBuf::from("/tmp/legacy-repo-root"));
    }
}
