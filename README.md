# FFmpeg GUI

[![Android CI](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml/badge.svg)](https://github.com/hcy917/FFmpeg-GUI/actions/workflows/android.yml)

一个以 AI Agent 为核心的 Android 媒体工作台：Kotlin + Jetpack Compose + Material 3 Expressive + FFmpegKit Full-GPL + 内置 Python 3.11。用自然语言描述任务，Agent 会自己查看文件、探测媒体、组合 FFmpeg 命令、编写并运行 Python 脚本，再把结果交给你检查。

![FFmpeg](app/src/main/res/drawable-nodpi/ffmpeg_logo.png)

## 功能

- **Agent 对话**：兼容任何 OpenAI Chat Completions 接口。Agent 在工具循环中自主完成任务，最多 24 轮；每次工具调用都会在对话中展示参数和结果，可随时停止。
- **文件读写**：列出、读取、写入、创建目录、复制、移动、删除文件。工作区内的相对路径直接使用，拥有「所有文件访问权限」后也可以操作任意可访问的存储路径。
- **FFmpeg 全能力**：媒体探测、能力查询、执行任意 FFmpeg 参数。转换、批量统一格式、音视频合并、按段拆分、抽帧、生成 GIF、提取字幕等都可以由 Agent 组合完成。
- **Python 批处理**：Agent 可生成、保存、修改和运行 Python 脚本，也可安装纯 Python 的 `.whl` / `.zip` 库。脚本通过 `ffmpeg_gui` 模块直接调用 FFmpeg。
- **AI 脚本库**：所有由 AI 生成或手动导入的脚本集中保存在 `ai_scripts` 目录，和普通工作区文件分开，不会混在一起。
- **两种导入方式**：聊天页可导入单个文件或整个文件夹；也支持从系统分享面板把任意文件发送给 App，导入后自动复制到工作区。
- **中文 Material 3 Expressive 界面**：三栏底部导航（AI / 已保存脚本 / 设置），大字号、弹性动画、动态取色，全中文提示。

## Agent 工作方式

1. 你输入任务，例如「把 inbox 里所有视频转成 720p MP4，输出到 outputs/720p」。
2. Agent 收到系统提示、工作区快照和工具列表。
3. 模型选择工具；App 在本地执行，把结果回传给模型。
4. 模型继续调用工具，直到给出最终答复。
5. 生成的文件留在工作区，可在「已保存脚本」中复用脚本，或通过文件管理器访问固定目录。

Agent 的工具：

| 工具 | 用途 |
| --- | --- |
| `fs_list` / `fs_read` / `fs_write` | 浏览目录、读取文本、写入文本或源码 |
| `fs_copy` / `fs_move` / `fs_delete` / `fs_mkdir` | 复制、移动、删除、创建目录 |
| `download_file` | 从 HTTP(S) 下载字体、字幕、测试素材或库文件 |
| `workspace_info` | 查看工作区路径、权限、媒体库与脚本概况 |
| `media_library` / `media_probe` | 查看媒体库、读取时长/分辨率/编码信息 |
| `ffmpeg_run` / `ffmpeg_capabilities` | 执行 FFmpeg、查询可用编码器与封装格式 |
| `python_save_script` / `python_run` | 保存并执行 Python 脚本 |
| `python_list` / `python_install_library` | 列出脚本与库、安装纯 Python 库 |

## 快速上手

1. 打开「设置 → AI 模型与接口」，选择预设或填写接口地址、模型名和 API Key。
2. 点「测试连接」确认可用，再保存。
3. 在「设置 → 工作区与权限」查看固定工作区路径；如需让 AI 访问工作区之外的文件，点「授予所有文件访问权限」。
4. 回到「AI」，直接描述任务，或先导入文件/文件夹作为附件。
5. Agent 生成的 Python 脚本会自动出现在「已保存脚本」，可以编辑、运行、导出或再次交给 AI 修改。

## 兼容性

- 最低 Android 8.0（API 26）
- 目标 Android 17（API 37，`targetSdk 37` / `compileSdk 37`）
- ABI：`arm64-v8a`、`armeabi-v7a`、`x86_64`
- 界面全中文；命令、编码器和文件扩展名保持技术原名
- 内置 FFmpegKit Full-GPL 6.0-2，包含 x264、x265、libvpx、Opus、LAME 等组件

## 存储位置

工作区固定在应用的外部专属目录，无需选择存储位置：

```text
/storage/emulated/0/Android/data/com.ffmpeg.gui/files/FFmpegGUI/
├── inbox/        导入的文件与文件夹
├── outputs/      FFmpeg 与 Python 的输出
├── ai_scripts/   AI 生成和手动导入的 Python 脚本
├── media/        其他媒体文件
└── library/      已安装的 Python 库
```

调试版使用独立包名 `com.ffmpeg.gui.debug`，对应目录是 `.../com.ffmpeg.gui.debug/files/FFmpegGUI/`，因此正式版和调试版可以同时安装、互不干扰。

Android 11 及以上如需读写上述目录之外的公共文件，请在设置中授予「所有文件访问权限」。Android 8–9 使用传统存储权限。Android 10 受分区存储限制，请通过聊天页的「导入文件/文件夹」用系统选择器添加公共文件；Agent 仍可处理工作区中的全部文件。

## 下载与安装

- [正式发布](https://github.com/hcy917/FFmpeg-GUI/releases)：`FFmpeg-GUI-release.apk`，包名 `com.ffmpeg.gui`。
- [Nightly 预发布](https://github.com/hcy917/FFmpeg-GUI/releases/tag/nightly)：每次 `main` 分支 CI 成功后自动更新，包含 release 与 debug 两个 APK。
- GitHub Actions 页面也可下载单次构建产物。

两个 APK 使用**同一签名密钥**，可以同时安装；debug 包名带 `.debug` 后缀，版本名带 `-debug` 后缀，应用名显示为「FFmpeg GUI Debug」。

APK 约 80–105 MB，包含 FFmpeg、Python 运行时和三种 ABI 的原生库。

## 本地构建

环境：JDK 17、Android SDK Platform 37、Build-Tools 37、Gradle 8.13、Python 3.11（Chaquopy 构建脚本使用）。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

如果本机 `~/.gradle` 不可写，可以使用项目内 Gradle Home：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

本地没有 release keystore 时，debug 与 release 都会回退到 `app/debug.keystore`，方便自测；CI 注入正式密钥后两者统一使用 release 密钥。

## CI、签名与 Nightly

`.github/workflows/android.yml` 在每次 `main` 推送、PR 和手动触发时运行：

1. 单元测试。
2. 构建 debug 与 release APK。
3. 用 `aapt` 验证包名分别为 `com.ffmpeg.gui.debug` 与 `com.ffmpeg.gui`。
4. 用 `apksigner` 验证两个 APK 的证书 SHA-256 完全一致。
5. 上传两个 APK 与 `SHA256SUMS.txt`。
6. `main` 成功构建后，自动更新 `nightly` 标签的预发布版本；推送 `v*` 标签时创建正式 Release。

签名通过 GitHub Actions Secrets 注入，不进入仓库：

| Secret | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | release keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

没有 Secrets 时，工作流仍会构建并使用本地 debug 密钥签名，但不会用于正式发布。

## Python

内置 Python 3.11，可在「已保存脚本」中新建、编辑和运行脚本。示例：

```python
import ffmpeg_gui

print(ffmpeg_gui.list_media())
print(ffmpeg_gui.probe("inbox/example.mp4"))
ffmpeg_gui.run_ffmpeg('-y -i "inbox/example.mp4" -c copy "outputs/copy.mkv"')
```

Agent 生成的脚本默认使用 UTF-8 保存在 `ai_scripts/`。带原生扩展的 Python wheel 暂不支持，只能安装纯 Python 包。

## 安全说明

- 所有媒体处理都在本机完成；只有 AI 对话会访问你配置的接口。
- API Key 保存在应用私有偏好中，且已关闭 Android 自动备份，避免随备份外泄。
- Agent 的删除、覆盖操作会真实修改文件。重要文件请先备份，并在对话中明确目标路径。
- 授予「所有文件访问权限」后，AI 工具可以读写设备上的其他文件。只在信任所配置模型的前提下开启。

## 已知限制

- AI 需要兼容 Chat Completions 且支持工具调用的接口，App 不内置模型。
- Agent 的上下文与工具结果有截断（单次工具结果约 18 KB），超大目录或超长日志需要分步处理。
- 合并、转码等长任务会持续占用前台；请保持应用在前台运行。
- 内置 FFmpegKit Full-GPL 6.0-2 是较旧的 4 KB 页对齐构建；绝大多数 Android 8–17 设备可用，极端 16 KB 页设备可能需要替换为维护版 FFmpeg 8。
- FFmpeg 名称与 Logo 属于 FFmpeg 项目，仅用于标识。

## 许可证

由于捆绑的 FFmpegKit Full-GPL 包含 GPL 组件（x264/x265），应用整体以 **GPL v3** 发布。详见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
