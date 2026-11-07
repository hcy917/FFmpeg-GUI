# 第三方声明

## FFmpeg / FFmpegKit

本应用捆绑 FFmpegKit Full-GPL 6.0-2（`app/libs/ffmpeg-kit-full-gpl.aar`），这是 Arthenica 已停止维护的产品，包含 FFmpeg 6.0 以及 x264、x265、libvpx、libopus、LAME 等 GPL 组件。

- FFmpeg：https://ffmpeg.org/ — LGPL 2.1+，启用 GPL 组件时适用 GPL。
- FFmpegKit：https://github.com/arthenica/ffmpeg-kit — LGPL 3.0；Full-GPL 包适用 GPL v3。
- 由于本应用分发 GPL 构建，整个应用以 GPL v3 发布。
- FFmpeg 名称与 Logo 归 FFmpeg 项目所有，仅用于标识本应用所使用的引擎，不代表官方认可。

## Chaquopy / Python

Python 由 Chaquopy 17.2.0（https://chaquo.com/chaquopy/）嵌入，工具链为 MIT 许可。CPython 采用 Python Software Foundation License。用户自行导入的 Python 包遵循其各自许可证。

## Jetpack Compose / AndroidX

Jetpack Compose、Material 3、AndroidX 与 Kotlin 均采用 Apache License 2.0。

## OkHttp 与 kotlinx.serialization

OkHttp 采用 Apache 2.0；kotlinx.serialization 采用 Apache 2.0。
