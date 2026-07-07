use std::net::TcpStream;
use std::process::{Child, Command};
use std::sync::Mutex;
use std::time::{Duration, Instant};
use tauri::Manager;
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

fn repo_root() -> std::path::PathBuf {
    // Allow explicit override via env var (useful for CI and custom installs)
    if let Ok(root) = std::env::var("BOOKIE_REPO_ROOT") {
        return std::path::PathBuf::from(root);
    }
    // Walk up from the executable until we find gradlew (the repo root marker)
    let mut dir = std::env::current_exe()
        .expect("cannot resolve executable path")
        .parent()
        .expect("executable has no parent directory")
        .to_path_buf();
    for _ in 0..10 {
        if dir.join("gradlew").exists() {
            return dir;
        }
        match dir.parent() {
            Some(p) => dir = p.to_path_buf(),
            None => break,
        }
    }
    std::env::current_dir().expect("cannot resolve current directory")
}

fn start_backend() {
    let root = repo_root();
    let (cmd, args): (&str, &[&str]) = if cfg!(target_os = "windows") {
        ("gradlew.bat", &["bootRun"])
    } else {
        ("./gradlew", &["bootRun"])
    };
    match Command::new(cmd).args(args).current_dir(&root).spawn() {
        Ok(child) => *BACKEND_PROCESS.lock().unwrap() = Some(child),
        Err(e) => eprintln!("Failed to start backend: {e}"),
    }
}

fn stop_backend() {
    if let Some(mut child) = BACKEND_PROCESS.lock().unwrap().take() {
        let _ = child.kill();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let window = app.get_webview_window("main").unwrap();
            let app_handle = app.handle().clone();
            std::thread::spawn(move || {
                if !is_backend_available() {
                    start_backend();
                }
                if wait_for_backend() {
                    window.show().unwrap();
                } else {
                    stop_backend();
                    app_handle
                        .dialog()
                        .message("The backend did not become ready in time.\nCheck the terminal for Gradle startup errors.")
                        .title("Bookie Startup Failed")
                        .blocking_show();
                    app_handle.exit(1);
                }
            });
            Ok(())
        })
        .on_window_event(|_window, event| {
            if let tauri::WindowEvent::Destroyed = event {
                stop_backend();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
