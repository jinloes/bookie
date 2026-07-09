use std::net::TcpStream;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_dialog::DialogExt;

const BACKEND_PORT: u16 = 48763;
const HEALTH_TIMEOUT_SECS: u64 = 180;

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

fn start_backend(data_dir: Option<std::path::PathBuf>) {
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
        return;
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
        }
        Err(e) => {
            eprintln!(
                "ERROR: Failed to start backend from {}: {}. Set BOOKIE_BACKEND_ROOT=/path/to/backend",
                root.display(),
                e
            );
        }
    }
}

fn stop_backend() {
    if let Some(mut child) = BACKEND_PROCESS.lock().unwrap().take() {
        let _ = child.kill();
    }
}

fn show_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
    }
}

#[tauri::command]
fn update_tray_tooltip(app: AppHandle, count: u32) {
    if let Some(tray) = app.tray_by_id("main") {
        let tooltip = if count > 0 {
            format!("Bookie — {count} pending item{}", if count == 1 { "" } else { "s" })
        } else {
            "Bookie".to_string()
        };
        let _ = tray.set_tooltip(Some(&tooltip));
    }
}

#[tauri::command]
fn get_backend_url() -> String {
    format!("http://localhost:{}", BACKEND_PORT)
}


#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            None,
        ))
        .invoke_handler(tauri::generate_handler![update_tray_tooltip, get_backend_url])
        .setup(|app| {
            // Resolve the platform-correct app data directory to pass to the backend.
            let data_dir = app.path().app_data_dir().ok();

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
            std::thread::spawn(move || {
                // In debug (dev) builds, beforeDevCommand starts the backend via Gradle.
                // We just wait for it to become available. In release builds, Rust owns
                // the backend process lifecycle.
                #[cfg(not(debug_assertions))]
                if !is_backend_available() {
                    start_backend(data_dir);
                }
                if wait_for_backend() {
                    window.show().unwrap();
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
