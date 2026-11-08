# FFmpeg GUI

[![Android CI](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml/badge.svg)](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml)

一个完全本地运行的 Android 媒体工作室：Kotlin + Jetpack Compose + Material 3 Expressive + FFmpegKit Full-GPL + 内置 Python + AI 智能助手。

![FFmpeg](app/src/main/res/drawable-nodpi/ffmpeg_logo.png)

## 功能

- **导入文件与文件夹**：通过 Android 存储访问框架（SAF）选择任意文件或文件夹，自动复制到工作区，FFmpeg、Python 和 AI 都能安全访问。
- **自定义存储位置**：设置 → 存储位置可以在内部存储、应用外部存储和自定义目录之间切换；切换时自动迁移文件、更新媒体库路径，旧目录保留为备份。
- **批量转换**：15 个预设，覆盖 H.264/H.265 MP4、MKV、MOV/ProRes、WebM/VP9+Opus、GIF、MP3、M4A、FLAC、WAV、Opus、JPEG、PNG 以及封装直复制。不同来源、不同格式可以统一输出为同一格式。
- **音视频合并**：混合格式、混合分辨率、纯音频、纯图片都可以。自动统一画布、帧率和 48 kHz 立体声；缺失音轨补静音，图片变成静帧片段，最终通过 FFmpeg concat 滤镜输出。
- **媒体拆分**：按时长或按段数拆分，输出格式可选 MP4、MKV、WebM、MOV、MP3、M4A、WAV、FLAC，支持重新编码或流复制。
- **内置 Python 3.11**：使用 Chaquopy 运行。支持新建、编辑、运行 `.py` 脚本，导入纯 Python 的 `.whl`/`.zip` 库，并通过 `ffmpeg_gui` 模块直接调用 FFmpeg 和文件接口。
- **AI 智能助手**：兼容任何 OpenAI Chat Completions 接口（DeepSeek、OpenAI、OpenRouter、Ollama、自定义）。支持工具调用：列出/读取/写入工作区文件、媒体探测、运行 FFmpeg、执行 Python。
- **FFmpeg 控制台**：直接输入完整命令，实时任务队列显示进度、日志、取消和输出文件分享。
- 中文界面，Material 3 Expressive 大字号、弹性动效、响应式导航，FFmpeg 官方 Logo 启动图标。

## 兼容性

- 最低 Android 8.0（API 26）
- 目标 Android 17（API 37，`targetSdk 37` / `compileSdk 37`）
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86_64`
- 界面全中文；代码与命令保持技术原名。

## 下载与安装

前往 [GitHub Releases](https://github.com/hcy917/FFmpeg-GUI/releases) 下载最新 `FFmpeg-GUI-release.apk`。也可以从 Actions 页面的构建产物下载：

- `FFmpeg-GUI-release`：使用仓库签名密钥签名的 release APK
- `FFmpeg-GUI-debug`：调试 APK

APK 约 82 MB，包含 FFmpeg、Python 运行时和三种 ABI 的原生库。

## 自定义存储位置

打开「设置 → 存储位置」：

- **内部存储**：默认位置，`/data/user/0/com.ffmpeg.gui/files/workspace`，卸载应用后删除。
- **外部存储**：`Android/data/com.ffmpeg.gui/files/FFmpegGUI/workspace`，方便通过 USB 访问，卸载后删除。
- **自定义目录**：可直接输入路径或通过系统文件夹选择器选择。Android 11 及以上写入公共目录需要「所有文件访问权限」（设置页有一键授权按钮）；Android 8–9 需要存储权限；Android 10 受分区存储限制，建议使用外部存储或应用专属目录。

切换时会复制当前工作区到新目录并同步媒体库路径，旧目录不会自动删除，确认新位置正常后可手动清理旧目录。运行中的 FFmpeg、Python 或导入任务会阻止切换。

## 本地构建

环境：JDK 17、Android SDK Platform 37、Build-Tools 37、Gradle 8.13。

```bash
./gradlew :app:assembleDebug
```

如果本机 `~/.gradle` 不可写，可以使用项目内 Gradle Home：

```bash
mkdir -p .gradle-home
GRADLE_USER_HOME="$PWD/.gradle-home" gradle :app:assembleDebug
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`。

## CI 与签名

`.github/workflows/android.yml` 在每次 `main` 推送、PR 和手动触发时运行：单元测试、debug APK、release APK。推送到 `v*` 标签时自动创建 GitHub Release，附带签名 APK 和 SHA-256 校验文件。

签名通过 GitHub Actions Secrets 注入，不进入仓库：

| Secret | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | release keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名（`ffmpeg-gui`） |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

没有 Secrets 时只构建未签名 release，方便 fork 和 PR 验证。

## 架构

```
app/src/main/java/com/ffmpeg/gui/
├── MainActivity.kt / MainViewModel.kt / FFmpegGuiApp
├── data/       工作区、媒体库、设置、数据模型
├── engine/     FFmpeg 引擎、命令构建、预设、任务队列、媒体探测
├── python/     Chaquopy 运行器与 Kotlin↔Python 桥
├── ai/         OpenAI 兼容客户端、工具执行器、智能助手循环
└── ui/         Material 3 Expressive 中文界面
app/src/main/python/         ffmpeg_gui 辅助模块
app/src/main/assets/python_examples/  中文示例脚本
```

所有处理都在本地完成。只有 AI 助手会访问网络，且只访问你配置的接口。API 密钥保存在应用私有偏好中。

## Python 示例

```python
import ffmpeg_gui

print(ffmpeg_gui.list_media())
print(ffmpeg_gui.probe("inbox/example.mp4"))
ffmpeg_gui.run_ffmpeg('-y -i "inbox/example.mp4" -c copy "outputs/copy.mkv"')
```

## AI 工具

| 工具 | 用途 |
| --- | --- |
| `workspace_list` | 列出文件/文件夹 |
| `workspace_read` | 读取文本/JSON |
| `workspace_write` | 创建或覆盖文本文件 |
| `media_library` | 列出媒体库 |
| `media_probe` | FFprobe 媒体信息 |
| `ffmpeg_run` | 执行任意 FFmpeg 参数 |
| `python_run` | 执行工作区 Python 脚本 |
| `python_list` | 列出脚本与库 |

工具在对话中自动执行，工作区是安全边界。生成的文件会在对话结束后自动加入媒体库。

## 已知限制

- 运行时只能安装纯 Python 包；带原生扩展的 wheel 暂不支持。
- AI 需要兼容 Chat Completions 且支持工具调用的接口，不内置模型。
- 合并会重新编码所有源文件，不是流复制拼接。
- 内置 FFmpegKit Full-GPL 6.0-2 是较旧的 4 KB 页对齐构建；绝大多数 Android 8–17 设备可用，但极端 16 KB 页设备可能需要替换为维护版 FFmpeg 8。
- FFmpeg 名称与 Logo 属于 FFmpeg 项目，仅用于标识。

## 许可证

由于捆绑的 FFmpegKit Full-GPL 包含 GPL 组件（x264/x265），应用整体以 **GPL v3** 发布。详见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
