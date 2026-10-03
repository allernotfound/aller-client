//! What the system is playing, through Windows' media transport controls: the same session the
//! volume flyout and a keyboard's media keys act on. Called from a worker thread of Java's, never
//! the render thread: the calls wait on the system's answer.

use std::panic::catch_unwind;

use jni::{
    objects::JClass,
    sys::{jint, jstring},
    JNIEnv,
};
use windows::Media::Control::{
    GlobalSystemMediaTransportControlsSession as Session, GlobalSystemMediaTransportControlsSessionManager as Manager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
};

/// Milliseconds between 1601, where Windows counts from, and 1970.
const EPOCH: i64 = 11_644_473_600_000;

fn session() -> windows::core::Result<Session> {
    Manager::RequestAsync()?.get()?.GetCurrentSession()
}

/// Title, artist, playing (1 or 0), position, length and when the position was true (milliseconds), then the app.
fn now() -> windows::core::Result<String> {
    let session = session()?;
    let media = session.TryGetMediaPropertiesAsync()?.get()?;
    let playing = session.GetPlaybackInfo()?.PlaybackStatus()? == Status::Playing;
    let timeline = session.GetTimelineProperties()?;
    let position = timeline.Position()?.Duration / 10_000;
    let end = (timeline.EndTime()?.Duration - timeline.StartTime()?.Duration) / 10_000;
    let updated = timeline.LastUpdatedTime()?.UniversalTime / 10_000 - EPOCH;
    let clean = |text: String| text.replace(['\n', '\r'], " ");
    Ok(format!(
        "{}\n{}\n{}\n{}\n{}\n{}\n{}",
        clean(media.Title()?.to_string()),
        clean(media.Artist()?.to_string()),
        playing as i32,
        position,
        end,
        updated,
        clean(session.SourceAppUserModelId()?.to_string())
    ))
}

fn command(what: i32) -> windows::core::Result<()> {
    let session = session()?;
    match what {
        0 => session.TryTogglePlayPauseAsync()?.get()?,
        1 => session.TrySkipNextAsync()?.get()?,
        _ => session.TrySkipPreviousAsync()?.get()?,
    };
    Ok(())
}

/// The lines of `now`, or an empty string while nothing is playing or the system will not say.
#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_MediaNative_now<'l>(env: JNIEnv<'l>, _class: JClass<'l>) -> jstring {
    let text = catch_unwind(|| now().unwrap_or_default()).unwrap_or_default();
    env.new_string(text).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// 0 plays or pauses, 1 skips on, 2 skips back.
#[no_mangle]
pub extern "system" fn Java_dev_aller_platform_MediaNative_command(_env: JNIEnv, _class: JClass, what: jint) {
    let _ = catch_unwind(|| command(what));
}
