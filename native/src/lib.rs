//! The native half of Aller's web browser: system webviews (WebView2, through wry), each in a
//! borderless window owned by the game window and laid over it. The Java side is `dev.aller.platform.WebNative`.
//!
//! Everything here runs on the thread that owns the game window. WebView2 delivers its callbacks
//! through that thread's message loop, which GLFW already pumps once a frame. Callbacks never call
//! into Java: they queue a line of text that Java collects with `poll`.

#![cfg(windows)]

mod media;

use std::{
    cell::{Cell, RefCell},
    collections::HashMap,
    num::NonZeroIsize,
    panic::{catch_unwind, AssertUnwindSafe},
    rc::Rc,
    sync::Mutex,
};

use jni::{
    objects::{JClass, JIntArray, JObject, JString},
    sys::{jboolean, jbyteArray, jint, jlong, jobjectArray},
    JNIEnv,
};
use raw_window_handle::{HandleError, HasWindowHandle, RawWindowHandle, Win32WindowHandle, WindowHandle};
use webview2_com::{Microsoft::Web::WebView2::Win32::*, *};
use windows::{
    core::{Interface, BOOL, HSTRING, PCWSTR},
    Win32::{
        Foundation::{HGLOBAL, HWND, LPARAM, LRESULT, POINT, WPARAM},
        Graphics::Gdi::ClientToScreen,
        System::Com::{
            CoInitializeEx, IStream, StructuredStorage::CreateStreamOnHGlobal, COINIT_APARTMENTTHREADED, STATFLAG_NONAME, STATSTG,
            STREAM_SEEK_SET,
        },
        UI::{
            Input::KeyboardAndMouse::{GetKeyState, SetFocus, VK_CONTROL, VK_MENU, VK_SHIFT},
            WindowsAndMessaging::{
                CreateWindowExW, DefWindowProcW, DestroyWindow, GetForegroundWindow, GetWindow, RegisterClassExW, SetWindowPos, ShowWindow,
                GW_OWNER, SWP_NOACTIVATE, SWP_NOZORDER, SW_HIDE, SW_SHOWNOACTIVATE, WNDCLASSEXW, WS_CLIPCHILDREN, WS_EX_TOOLWINDOW, WS_POPUP,
            },
        },
    },
};
use wry::{
    PageLoadEvent, Theme, WebView, WebViewBuilder, WebViewBuilderExtWindows, WebViewExtWindows,
};

/// Lines waiting for Java: `id \t kind \t payload`. A mutex because wry answers new-window requests on another thread.
static EVENTS: Mutex<Vec<String>> = Mutex::new(Vec::new());
/// Pictures waiting for Java, by webview and kind.
static BLOBS: Mutex<Vec<(i32, i32, Vec<u8>)>> = Mutex::new(Vec::new());

type Res<T> = std::result::Result<T, String>;

const BLOB_CAPTURE: i32 = 0;
const BLOB_FAVICON: i32 = 1;

const ENV_NONE: i32 = 0;
const ENV_PENDING: i32 = 1;
const ENV_READY: i32 = 2;
const ENV_FAILED: i32 = -1;

thread_local! {
    static ENV: RefCell<Option<ICoreWebView2Environment>> = const { RefCell::new(None) };
    static ENV_STATE: Cell<i32> = const { Cell::new(ENV_NONE) };
    static VIEWS: RefCell<HashMap<i32, Rc<Page>>> = RefCell::new(HashMap::new());
    static HOSTS_TO_DESTROY: RefCell<Vec<isize>> = const { RefCell::new(Vec::new()) };
    static NEXT_ID: Cell<i32> = const { Cell::new(1) };
    static PARENT: Cell<isize> = const { Cell::new(0) };
    /// Key combinations Aller keeps for itself while a page has the keyboard: virtual key, with GLFW's modifier bits above it.
    static KEYS: RefCell<Vec<u32>> = const { RefCell::new(Vec::new()) };
}

fn emit(id: i32, kind: &str, payload: &str) {
    if let Ok(mut events) = EVENTS.lock() {
        // Tabs and line breaks would break the framing; neither belongs in a URL or a title.
        let clean: String = payload.chars().map(|c| if c == '\t' || c == '\n' || c == '\r' { ' ' } else { c }).collect();
        events.push(format!("{id}\t{kind}\t{clean}"));
    }
}

fn put_blob(id: i32, kind: i32, bytes: Vec<u8>) {
    if let Ok(mut blobs) = BLOBS.lock() {
        blobs.retain(|(i, k, _)| !(*i == id && *k == kind));
        blobs.push((id, kind, bytes));
    }
}

/// A webview and the window it lives in.
///
/// The window is a borderless popup owned by the game window and laid over it, not a child of it:
/// a fullscreen game presents straight to the display, which hides its own child windows but not
/// a separate window above it.
struct Page {
    web: WebView,
    host: HWND,
}

impl Drop for Page {
    fn drop(&mut self) {
        // This runs before the webview field is dropped, so the window is only hidden here and
        // destroyed by `sweep` once the webview inside it has closed.
        unsafe {
            let _ = ShowWindow(self.host, SW_HIDE);
        }
        let host = self.host.0 as isize;
        HOSTS_TO_DESTROY.with(|h| h.borrow_mut().push(host));
    }
}

/// Destroys the windows of pages dropped since the last call.
fn sweep() {
    let hosts: Vec<isize> = HOSTS_TO_DESTROY.with(|h| h.borrow_mut().drain(..).collect());
    for host in hosts {
        unsafe {
            let _ = DestroyWindow(HWND(host as _));
        }
    }
}

unsafe extern "system" fn host_proc(hwnd: HWND, msg: u32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    DefWindowProcW(hwnd, msg, wparam, lparam)
}

fn create_host(owner: isize, width: i32, height: i32) -> windows::core::Result<HWND> {
    let name = windows::core::w!("ALLER_WEBVIEW_HOST");
    unsafe {
        let class = WNDCLASSEXW {
            cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
            lpfnWndProc: Some(host_proc),
            lpszClassName: name,
            ..Default::default()
        };
        // Fails harmlessly once the class exists.
        RegisterClassExW(&class);
        // A tool window: no taskbar button and no entry of its own when switching programs.
        CreateWindowExW(
            WS_EX_TOOLWINDOW,
            name,
            PCWSTR::null(),
            WS_POPUP | WS_CLIPCHILDREN,
            -32000,
            -32000,
            width.max(64),
            height.max(64),
            Some(HWND(owner as _)),
            None,
            None,
            None,
        )
    }
}

fn view(id: i32) -> Option<Rc<Page>> {
    VIEWS.with(|v| v.borrow().get(&id).cloned())
}

struct Parent(isize);

impl HasWindowHandle for Parent {
    fn window_handle(&self) -> std::result::Result<WindowHandle<'_>, HandleError> {
        let handle = Win32WindowHandle::new(NonZeroIsize::new(self.0).ok_or(HandleError::Unavailable)?);
        Ok(unsafe { WindowHandle::borrow_raw(RawWindowHandle::Win32(handle)) })
    }
}

/// Starts the browser process for a data folder. It finishes later, on the message loop.
fn start(dir: String, args: String) -> windows::core::Result<()> {
    if ENV_STATE.get() == ENV_PENDING || ENV_STATE.get() == ENV_READY {
        return Ok(());
    }
    ENV_STATE.set(ENV_PENDING);
    unsafe {
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
        let options = CoreWebView2EnvironmentOptions::default();
        options.set_additional_browser_arguments(args);
        // "Custom" crash reporting means crash dumps are not sent to Microsoft.
        options.set_is_custom_crash_reporting_enabled(true);
        // Never sign pages in with the Windows account.
        options.set_allow_single_sign_on_using_os_primary_account(false);
        options.set_are_browser_extensions_enabled(false);
        options.set_enable_tracking_prevention(true);
        CreateCoreWebView2EnvironmentWithOptions(
            PCWSTR::null(),
            &HSTRING::from(dir),
            &ICoreWebView2EnvironmentOptions::from(options),
            &CreateCoreWebView2EnvironmentCompletedHandler::create(Box::new(move |result, environment| {
                match (result, environment) {
                    (Ok(()), Some(environment)) => {
                        ENV.with(|e| e.replace(Some(environment)));
                        ENV_STATE.set(ENV_READY);
                    }
                    (result, _) => {
                        ENV_STATE.set(ENV_FAILED);
                        emit(0, "error", &format!("WebView2 could not start: {result:?}"));
                    }
                }
                Ok(())
            })),
        )
    }
}

fn read_stream(stream: &IStream) -> windows::core::Result<Vec<u8>> {
    unsafe {
        let mut stat = STATSTG::default();
        stream.Stat(&mut stat, STATFLAG_NONAME)?;
        stream.Seek(0, STREAM_SEEK_SET, None)?;
        let mut bytes = vec![0u8; stat.cbSize as usize];
        let mut done = 0usize;
        while done < bytes.len() {
            let mut read = 0u32;
            stream.Read(bytes[done..].as_mut_ptr() as *mut _, (bytes.len() - done) as u32, Some(&mut read)).ok()?;
            if read == 0 {
                break;
            }
            done += read as usize;
        }
        bytes.truncate(done);
        Ok(bytes)
    }
}

fn history(id: i32, webview: &ICoreWebView2) {
    let (mut back, mut forward) = (BOOL::default(), BOOL::default());
    unsafe {
        let _ = webview.CanGoBack(&mut back);
        let _ = webview.CanGoForward(&mut forward);
    }
    emit(id, "nav", &format!("{}{}", back.as_bool() as u8, forward.as_bool() as u8));
}

fn create(parent: isize, url: String, script: String, width: i32, height: i32, private: bool) -> Res<i32> {
    let environment = ENV.with(|e| e.borrow().clone()).ok_or("WebView2 has not started")?;
    PARENT.set(parent);
    let id = NEXT_ID.get();
    NEXT_ID.set(id + 1);
    let host = create_host(parent, width, height).map_err(|e| e.to_string())?;

    let builder = WebViewBuilder::new()
        .with_environment(environment)
        .with_url(url)
        .with_visible(false)
        // A private tab uses WebView2's InPrivate profile: nothing it stores outlives its last tab.
        .with_incognito(private)
        .with_focused(false)
        .with_clipboard(false)
        .with_devtools(false)
        .with_theme(Theme::Dark)
        .with_background_color((16, 14, 24, 255))
        .with_initialization_script_for_main_only(script, true)
        .with_ipc_handler(move |request| {
            if request.body() == "aller:esc" {
                emit(id, "esc", "");
            }
        })
        .with_navigation_handler(move |_| {
            emit(id, "load", "1");
            true
        })
        .with_on_page_load_handler(move |event, _| {
            if matches!(event, PageLoadEvent::Finished) {
                emit(id, "load", "0");
            }
        })
        .with_document_title_changed_handler(move |title| emit(id, "title", &title))
        .with_new_window_req_handler(move |url, _| {
            emit(id, "open", &url);
            wry::NewWindowResponse::Deny
        })
        .with_download_started_handler(move |url, _| {
            emit(id, "download", &url);
            false
        });

    // Fills the host window and follows its size.
    let web = match builder.build(&Parent(host.0 as isize)) {
        Ok(web) => web,
        Err(e) => {
            unsafe {
                let _ = DestroyWindow(host);
            }
            return Err(e.to_string());
        }
    };
    let page = Page { web, host };
    unsafe { attach(id, &page.web) }.map_err(|e| e.to_string())?;
    VIEWS.with(|v| v.borrow_mut().insert(id, Rc::new(page)));
    Ok(id)
}

/// The parts of a browser tab wry has no builder method for.
unsafe fn attach(id: i32, view: &WebView) -> windows::core::Result<()> {
    let webview = view.webview();
    let controller = view.controller();
    let mut token = 0i64;

    let settings = webview.Settings()?;
    settings.SetIsStatusBarEnabled(false)?;
    // No SmartScreen: it sends every address visited to Microsoft.
    if let Ok(settings) = settings.cast::<ICoreWebView2Settings8>() {
        settings.SetIsReputationCheckingRequired(false)?;
    }
    if let Ok(settings) = settings.cast::<ICoreWebView2Settings4>() {
        settings.SetIsPasswordAutosaveEnabled(false)?;
    }

    webview.add_SourceChanged(
        &SourceChangedEventHandler::create(Box::new(move |webview, _| {
            if let Some(webview) = webview {
                let mut uri = windows::core::PWSTR::null();
                webview.Source(&mut uri)?;
                emit(id, "url", &take_pwstr(uri));
            }
            Ok(())
        })),
        &mut token,
    )?;
    webview.add_HistoryChanged(
        &HistoryChangedEventHandler::create(Box::new(move |webview, _| {
            if let Some(webview) = webview {
                history(id, &webview);
            }
            Ok(())
        })),
        &mut token,
    )?;
    webview.add_ContainsFullScreenElementChanged(
        &ContainsFullScreenElementChangedEventHandler::create(Box::new(move |webview, _| {
            if let Some(webview) = webview {
                let mut full = BOOL::default();
                webview.ContainsFullScreenElement(&mut full)?;
                emit(id, "full", if full.as_bool() { "1" } else { "0" });
            }
            Ok(())
        })),
        &mut token,
    )?;
    webview.add_WindowCloseRequested(
        &WindowCloseRequestedEventHandler::create(Box::new(move |_, _| {
            emit(id, "close", "");
            Ok(())
        })),
        &mut token,
    )?;
    webview.add_ProcessFailed(
        &ProcessFailedEventHandler::create(Box::new(move |_, _| {
            emit(id, "crash", "");
            Ok(())
        })),
        &mut token,
    )?;
    // Sites get nothing sensitive: no camera, microphone, location or notifications. Reading the
    // clipboard is allowed only when the player asked for it (a paste button).
    webview.add_PermissionRequested(
        &PermissionRequestedEventHandler::create(Box::new(move |_, args| {
            let Some(args) = args else { return Ok(()) };
            let mut kind = COREWEBVIEW2_PERMISSION_KIND::default();
            args.PermissionKind(&mut kind)?;
            let mut asked = BOOL::default();
            args.IsUserInitiated(&mut asked)?;
            let allow = kind == COREWEBVIEW2_PERMISSION_KIND_CLIPBOARD_READ && asked.as_bool();
            args.SetState(if allow { COREWEBVIEW2_PERMISSION_STATE_ALLOW } else { COREWEBVIEW2_PERMISSION_STATE_DENY })?;
            Ok(())
        })),
        &mut token,
    )?;
    if let Ok(webview) = webview.cast::<ICoreWebView2_8>() {
        webview.add_IsDocumentPlayingAudioChanged(
            &IsDocumentPlayingAudioChangedEventHandler::create(Box::new(move |webview, _| {
                if let Some(webview) = webview.and_then(|w| w.cast::<ICoreWebView2_8>().ok()) {
                    let mut playing = BOOL::default();
                    webview.IsDocumentPlayingAudio(&mut playing)?;
                    emit(id, "audio", if playing.as_bool() { "1" } else { "0" });
                }
                Ok(())
            })),
            &mut token,
        )?;
    }
    if let Ok(webview) = webview.cast::<ICoreWebView2_15>() {
        webview.add_FaviconChanged(
            &FaviconChangedEventHandler::create(Box::new(move |webview, _| {
                let Some(webview) = webview.and_then(|w| w.cast::<ICoreWebView2_15>().ok()) else { return Ok(()) };
                webview.GetFavicon(
                    COREWEBVIEW2_FAVICON_IMAGE_FORMAT_PNG,
                    &GetFaviconCompletedHandler::create(Box::new(move |result, stream| {
                        if let (Ok(()), Some(stream)) = (result, stream) {
                            if let Ok(bytes) = read_stream(&stream) {
                                if !bytes.is_empty() {
                                    put_blob(id, BLOB_FAVICON, bytes);
                                    emit(id, "favicon", "");
                                }
                            }
                        }
                        Ok(())
                    })),
                )
            })),
            &mut token,
        )?;
    }

    controller.add_AcceleratorKeyPressed(
        &AcceleratorKeyPressedEventHandler::create(Box::new(move |_, args| {
            let Some(args) = args else { return Ok(()) };
            let mut kind = COREWEBVIEW2_KEY_EVENT_KIND::default();
            args.KeyEventKind(&mut kind)?;
            if kind != COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN && kind != COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN {
                return Ok(());
            }
            let mut key = 0u32;
            args.VirtualKey(&mut key)?;
            let down = |vk: u16| GetKeyState(vk as i32) < 0;
            let mods = down(VK_SHIFT.0) as u32 | (down(VK_CONTROL.0) as u32) << 1 | (down(VK_MENU.0) as u32) << 2;
            let packed = key | mods << 16;
            if KEYS.with(|k| k.borrow().contains(&packed)) {
                args.SetHandled(true)?;
                let mut status = COREWEBVIEW2_PHYSICAL_KEY_STATUS::default();
                args.PhysicalKeyStatus(&mut status)?;
                if !status.WasKeyDown.as_bool() {
                    emit(id, "key", &packed.to_string());
                }
            }
            Ok(())
        })),
        &mut token,
    )?;
    controller.add_GotFocus(
        &FocusChangedEventHandler::create(Box::new(move |_, _| {
            emit(id, "focus", "1");
            Ok(())
        })),
        &mut token,
    )?;
    controller.add_LostFocus(
        &FocusChangedEventHandler::create(Box::new(move |_, _| {
            emit(id, "focus", "0");
            Ok(())
        })),
        &mut token,
    )?;
    Ok(())
}

/// A still of the page as it is drawn now, as a PNG. It arrives later as a `capture` event.
unsafe fn capture(id: i32, view: &WebView) -> windows::core::Result<()> {
    let stream = CreateStreamOnHGlobal(HGLOBAL::default(), true)?;
    let filled = stream.clone();
    view.webview().CapturePreview(
        COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_PNG,
        &stream,
        &CapturePreviewCompletedHandler::create(Box::new(move |result| {
            match result.and_then(|_| read_stream(&filled)) {
                Ok(bytes) if !bytes.is_empty() => {
                    put_blob(id, BLOB_CAPTURE, bytes);
                    emit(id, "capture", "1");
                }
                _ => emit(id, "capture", "0"),
            }
            Ok(())
        })),
    )
}

fn action(id: i32, what: i32) -> Res<()> {
    if what == 5 {
        let parent = PARENT.get();
        if parent != 0 {
            // Returns the window that had focus before; none having had it is not a failure.
            let _ = unsafe { SetFocus(Some(HWND(parent as _))) };
        }
        return Ok(());
    }
    let page = view(id).ok_or("no such webview")?;
    let view = &page.web;
    let webview = view.webview();
    let done = unsafe {
        match what {
            0 => webview.GoBack(),
            1 => webview.GoForward(),
            2 => webview.Reload(),
            3 => webview.Stop(),
            // Refused while the game window is not the active one; nothing to report.
            4 => {
                let _ = SetFocus(Some(page.host));
                let _ = view.focus();
                return Ok(());
            }
            6 => webview.cast::<ICoreWebView2_3>().and_then(|w| w.TrySuspend(&TrySuspendCompletedHandler::create(Box::new(|_, _| Ok(()))))),
            7 => webview.cast::<ICoreWebView2_3>().and_then(|w| w.Resume()),
            8 => capture(id, view),
            9 | 10 => webview.cast::<ICoreWebView2_8>().and_then(|w| w.SetIsMuted(what == 9)),
            11 => return view.clear_all_browsing_data().map_err(|e| e.to_string()),
            _ => Ok(()),
        }
    };
    done.map_err(|e| e.to_string())
}

// ---- JNI -----------------------------------------------------------------------------------------

fn text(env: &mut JNIEnv, value: &JString) -> String {
    env.get_string(value).map(Into::into).unwrap_or_default()
}

/// Runs a call, turning a failure or a panic into an `error` event instead of taking the game down.
fn guard<T>(fallback: T, id: i32, call: impl FnOnce() -> Res<T>) -> T {
    match catch_unwind(AssertUnwindSafe(call)) {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => {
            emit(id, "error", &message);
            fallback
        }
        Err(_) => {
            emit(id, "error", "the native webview code panicked");
            fallback
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_start<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, dir: JString<'l>, args: JString<'l>) {
    let (dir, args) = (text(&mut env, &dir), text(&mut env, &args));
    guard((), 0, || {
        start(dir, args).map_err(|e| {
            ENV_STATE.set(ENV_FAILED);
            e.to_string()
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_state(_env: JNIEnv, _class: JClass) -> jint {
    ENV_STATE.get()
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_create<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    parent: jlong,
    url: JString<'l>,
    script: JString<'l>,
    width: jint,
    height: jint,
    private: jboolean,
) -> jint {
    let (url, script) = (text(&mut env, &url), text(&mut env, &script));
    guard(-1, 0, || create(parent as isize, url, script, width, height, private != 0))
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_destroy(_env: JNIEnv, _class: JClass, id: jint) {
    guard((), id, || {
        // Dropped outside the borrow: closing a webview can run callbacks.
        let view = VIEWS.with(|v| v.borrow_mut().remove(&id));
        drop(view);
        sweep();
        if let Ok(mut blobs) = BLOBS.lock() {
            blobs.retain(|(i, _, _)| *i != id);
        }
        Ok(())
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_place(_env: JNIEnv, _class: JClass, id: jint, x: jint, y: jint, w: jint, h: jint) {
    guard((), id, || {
        let page = view(id).ok_or("no such webview")?;
        // Given inside the game window; the page's own window is placed on the screen.
        let mut corner = POINT { x, y };
        unsafe {
            let _ = ClientToScreen(HWND(PARENT.get() as _), &mut corner);
            SetWindowPos(page.host, None, corner.x, corner.y, w.max(1), h.max(1), SWP_NOACTIVATE | SWP_NOZORDER).map_err(|e| e.to_string())
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_show(_env: JNIEnv, _class: JClass, id: jint, visible: jboolean) {
    guard((), id, || {
        let page = view(id).ok_or("no such webview")?;
        unsafe {
            let _ = ShowWindow(page.host, if visible != 0 { SW_SHOWNOACTIVATE } else { SW_HIDE });
        }
        page.web.set_visible(visible != 0).map_err(|e| e.to_string())
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_navigate<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, id: jint, url: JString<'l>) {
    let url = text(&mut env, &url);
    guard((), id, || view(id).ok_or("no such webview")?.web.load_url(&url).map_err(|e| e.to_string()))
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_eval<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, id: jint, script: JString<'l>) {
    let script = text(&mut env, &script);
    guard((), id, || view(id).ok_or("no such webview")?.web.evaluate_script(&script).map_err(|e| e.to_string()))
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_action(_env: JNIEnv, _class: JClass, id: jint, what: jint) {
    guard((), id, || action(id, what))
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_keys<'l>(env: JNIEnv<'l>, _class: JClass<'l>, keys: JIntArray<'l>) {
    let length = env.get_array_length(&keys).unwrap_or(0) as usize;
    let mut values = vec![0i32; length];
    if env.get_int_array_region(&keys, 0, &mut values).is_ok() {
        KEYS.with(|k| *k.borrow_mut() = values.iter().map(|v| *v as u32).collect());
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_poll<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>) -> jobjectArray {
    let lines = match EVENTS.lock() {
        Ok(mut events) if !events.is_empty() => std::mem::take(&mut *events),
        _ => return std::ptr::null_mut(),
    };
    let Ok(array) = env.new_object_array(lines.len() as i32, "java/lang/String", JObject::null()) else {
        return std::ptr::null_mut();
    };
    for (i, line) in lines.iter().enumerate() {
        if let Ok(value) = env.new_string(line) {
            let _ = env.set_object_array_element(&array, i as i32, &value);
            let _ = env.delete_local_ref(value);
        }
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_blob<'l>(env: JNIEnv<'l>, _class: JClass<'l>, id: jint, kind: jint) -> jbyteArray {
    let bytes = match BLOBS.lock() {
        Ok(mut blobs) => blobs.iter().position(|(i, k, _)| *i == id && *k == kind).map(|at| blobs.swap_remove(at).2),
        Err(_) => None,
    };
    match bytes.and_then(|b| env.byte_array_from_slice(&b).ok()) {
        Some(array) => array.into_raw(),
        None => std::ptr::null_mut(),
    }
}

/// Whether the given top-level window is the one the user is working in (a page inside it counts).
#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_foreground(_env: JNIEnv, _class: JClass, window: jlong) -> jboolean {
    let front = unsafe { GetForegroundWindow() };
    // A page's window in front counts: it belongs to the game window.
    let owner = unsafe { GetWindow(front, GW_OWNER) }.map(|o| o.0 as isize).unwrap_or(0);
    (front.0 as isize == window as isize || owner == window as isize) as jboolean
}

#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_WebNative_shutdown(_env: JNIEnv, _class: JClass) {
    guard((), 0, || {
        let views: Vec<_> = VIEWS.with(|v| v.borrow_mut().drain().collect());
        drop(views);
        sweep();
        ENV.with(|e| e.replace(None));
        ENV_STATE.set(ENV_NONE);
        Ok(())
    })
}
