# FFmpeg GUI

[![Android CI](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml/badge.svg)](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml)

A local-first Android media studio built with Kotlin, Jetpack Compose, Material 3 Expressive, FFmpegKit Full-GPL, and embedded Python.

![FFmpeg](app/src/main/res/drawable-nodpi/ffmpeg_logo.png)

## What it does

- **Import files and folders** through the Android Storage Access Framework. Imports are copied into an app-private workspace so FFmpeg, Python, and the AI copilot can access them safely.
- **Batch convert** different formats to one target using 15 presets: H.264/H.265 MP4, MKV, MOV/ProRes, WebM/VP9+Opus, GIF, MP3, M4A, FLAC, WAV, Opus, JPEG, PNG, and remux.
- **Merge audio and video** of mixed formats. Sources are scaled/padded to a shared canvas, retimed, converted to 48 kHz stereo, and joined through the FFmpeg concat filter. Audio-only and image-only inputs are handled.
- **Split** by duration or part count with a chosen output container (MP4, MKV, WebM, MOV, MP3, M4A, WAV, FLAC), with re-encode or stream-copy modes.
- **Python 3.11** embedded with Chaquopy. Create/edit/run `.py` scripts, import pure-Python `.whl`/`.zip` libraries, and call FFmpeg/files from Python through the `ffmpeg_gui` helper module.
- **AI copilot** for any OpenAI-compatible endpoint (DeepSeek, OpenAI, OpenRouter, Ollama, custom). It has tool calling for workspace listing, reading/writing files, probing media, running FFmpeg, and executing Python.
- **FFmpeg console** for direct commands, plus a live task queue with logs, progress, cancellation, and shareable outputs.
- FFmpeg logo launcher icon and in-app branding.

## Compatibility

- `minSdk 26` (Android 8.0)
- `targetSdk 37` / `compileSdk 37` (Android 17 platform)
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Material 3 (`androidx.compose.material3:1.4.0`) with an expressive custom theme, large typography, spring motion, and responsive navigation rail/bottom bar.

## Build

Requirements: JDK 17, Android SDK with platform 37 and build-tools 37, Gradle 8.13.

```bash
./gradlew :app:assembleDebug
```

For reproducible local builds without a writable `~/.gradle`, use a project-local Gradle home:

```bash
mkdir -p .gradle-home
GRADLE_USER_HOME="$PWD/.gradle-home" \
  gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### CI builds and signed releases

Every push to `main` runs [Android CI](.github/workflows/android.yml): unit tests, debug APK, and a release APK signed with the repository's keystore secrets. Download the APKs from the workflow run's **Artifacts** section. Tagging `v*` publishes a GitHub Release with the signed release APK and SHA-256 checksum.

Required repository secrets:

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the release PKCS#12/JKS keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (`ffmpeg-gui`) |
| `ANDROID_KEY_PASSWORD` | Key password |

Without these secrets the release APK is built unsigned, so forks and pull requests still pass CI.

### FFmpeg dependency

`app/libs/ffmpeg-kit-full-gpl.aar` is FFmpegKit Full-GPL 6.0-2. It contains FFmpeg 6.0 with x264, x265, libvpx, libopus, LAME, and many native codecs. FFmpegKit has been retired upstream; this artifact was obtained from an Appodeal Maven mirror. The project is therefore GPL v3. See `THIRD_PARTY_NOTICES.md`.

## Architecture

```
app/src/main/java/com/ffmpeg/gui/
├── MainActivity.kt, MainViewModel.kt, FFmpegGuiApp
├── data/       workspace, media library, settings, models
├── engine/     FFmpeg engine, command builders, presets, task queue, probing
├── python/     Chaquopy runner and the Kotlin↔Python bridge
├── ai/         OpenAI-compatible client, tool executor, agent loop
└── ui/         Material 3 Expressive screens and shared components
app/src/main/python/       ffmpeg_gui helper module + runner
app/src/main/assets/python_examples/   example scripts
```

All processing is local. Only the AI copilot makes network calls, and only to the endpoint you configure. API keys are stored in app-private preferences.

## Python helper

```python
import ffmpeg_gui

print(ffmpeg_gui.list_media())
print(ffmpeg_gui.probe("inbox/example.mp4"))
ffmpeg_gui.run_ffmpeg('-y -i "inbox/example.mp4" -c copy "outputs/copy.mkv"')
```

## AI tools

| Tool | Purpose |
| --- | --- |
| `workspace_list` | List files/folders |
| `workspace_read` | Read text/JSON |
| `workspace_write` | Create/replace text files |
| `media_library` | List library items |
| `media_probe` | FFprobe metadata |
| `ffmpeg_run` | Execute arbitrary FFmpeg arguments |
| `python_run` | Execute a workspace Python script |
| `python_list` | List scripts and libraries |

Tools run automatically while the copilot turn is active. The workspace is the security boundary; generated outputs are added to the library afterward.

## Notes and limitations

- Python packages with native extensions cannot be installed at runtime. Pure-Python wheels/zips work.
- The AI copilot requires an OpenAI-compatible chat completions API with tool calling. No bundled model.
- Merging re-encodes all sources; it is not a stream-copy concat.
- FFmpegKit's legacy AAR is not 16 KB-page aligned on all Android 17 devices. Modern arm64 devices work; a maintained FFmpeg 8 fork can be substituted in `app/build.gradle.kts`.
- FFmpeg is a trademark of the FFmpeg project. The bundled logo is used for identification.

## License

Application code: GPL v3, because the bundled FFmpegKit Full-GPL build includes GPL components (x264/x265). FFmpegKit and FFmpeg retain their respective licenses. Python is provided by Chaquopy.
