package com.ffmpeg.gui.engine

import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.MediaKind
import com.ffmpeg.gui.data.MergeOptions
import com.ffmpeg.gui.data.SplitMode
import com.ffmpeg.gui.data.SplitOptions
import kotlin.math.max
import kotlin.math.min

data class Preset(
    val id: String,
    val title: String,
    val subtitle: String,
    val extension: String,
    val category: PresetCategory,
    val isAudioOnly: Boolean = false,
    val videoArgs: List<String> = emptyList(),
    val audioArgs: List<String> = emptyList(),
    val filterArgs: List<String> = emptyList(),
    val extraArgs: List<String> = emptyList(),
    val badge: String = extension.uppercase(),
)

enum class PresetCategory(val label: String) {
    VIDEO("视频"),
    AUDIO("音频"),
    IMAGE("图像"),
    UTILITY("封装"),
}

object Presets {
    val all: List<Preset> = listOf(
        Preset(
            id = "mp4_1080_h264",
            title = "MP4 · 1080p H.264",
            subtitle = "通用播放，AAC 音频",
            extension = "mp4",
            category = PresetCategory.VIDEO,
            badge = "1080p",
            videoArgs = listOf("-c:v", "libx264", "-preset", "medium", "-crf", "22", "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.1"),
            audioArgs = listOf("-c:a", "aac", "-b:a", "192k", "-ac", "2"),
            filterArgs = listOf("-vf", "scale=1920:-2"),
            extraArgs = listOf("-movflags", "+faststart"),
        ),
        Preset(
            id = "mp4_720_h264",
            title = "MP4 · 720p H.264",
            subtitle = "更小的分享体积",
            extension = "mp4",
            category = PresetCategory.VIDEO,
            badge = "720p",
            videoArgs = listOf("-c:v", "libx264", "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "3.1"),
            audioArgs = listOf("-c:a", "aac", "-b:a", "160k", "-ac", "2"),
            filterArgs = listOf("-vf", "scale=1280:-2"),
            extraArgs = listOf("-movflags", "+faststart"),
        ),
        Preset(
            id = "mp4_1080_hevc",
            title = "MP4 · 1080p HEVC",
            subtitle = "H.265 / x265，高效率归档",
            extension = "mp4",
            category = PresetCategory.VIDEO,
            badge = "HEVC",
            videoArgs = listOf("-c:v", "libx265", "-preset", "medium", "-crf", "26", "-pix_fmt", "yuv420p", "-tag:v", "hvc1"),
            audioArgs = listOf("-c:a", "aac", "-b:a", "192k", "-ac", "2"),
            filterArgs = listOf("-vf", "scale=1920:-2"),
            extraArgs = listOf("-movflags", "+faststart"),
        ),
        Preset(
            id = "mkv_1080_h264",
            title = "MKV · 1080p H.264",
            subtitle = "灵活的 Matroska 容器",
            extension = "mkv",
            category = PresetCategory.VIDEO,
            badge = "MKV",
            videoArgs = listOf("-c:v", "libx264", "-preset", "medium", "-crf", "22", "-pix_fmt", "yuv420p"),
            audioArgs = listOf("-c:a", "aac", "-b:a", "192k"),
            filterArgs = listOf("-vf", "scale=1920:-2"),
        ),
        Preset(
            id = "webm_vp9",
            title = "WebM · VP9 + Opus",
            subtitle = "Web 开放编码",
            extension = "webm",
            category = PresetCategory.VIDEO,
            badge = "VP9",
            videoArgs = listOf("-c:v", "libvpx-vp9", "-crf", "32", "-b:v", "0", "-row-mt", "1"),
            audioArgs = listOf("-c:a", "libopus", "-b:a", "128k"),
            filterArgs = listOf("-vf", "scale=1920:-2"),
        ),
        Preset(
            id = "mov_prores",
            title = "MOV · ProRes 422",
            subtitle = "剪辑友好的中间格式",
            extension = "mov",
            category = PresetCategory.VIDEO,
            badge = "ProRes",
            videoArgs = listOf("-c:v", "prores_ks", "-profile:v", "3", "-pix_fmt", "yuv422p10le"),
            audioArgs = listOf("-c:a", "pcm_s16le"),
        ),
        Preset(
            id = "gif_640",
            title = "GIF · 15 fps 640 px",
            subtitle = "调色板优化的动画",
            extension = "gif",
            category = PresetCategory.IMAGE,
            badge = "GIF",
            filterArgs = listOf("-vf", "fps=15,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"),
            extraArgs = listOf("-loop", "0"),
        ),
        Preset(
            id = "mp3_320",
            title = "MP3 · 320 kbps",
            subtitle = "高品质有损音频",
            extension = "mp3",
            category = PresetCategory.AUDIO,
            isAudioOnly = true,
            badge = "MP3",
            audioArgs = listOf("-c:a", "libmp3lame", "-b:a", "320k"),
        ),
        Preset(
            id = "m4a_aac",
            title = "M4A · AAC 256 kbps",
            subtitle = "高效的 Apple 友好音频",
            extension = "m4a",
            category = PresetCategory.AUDIO,
            isAudioOnly = true,
            badge = "AAC",
            audioArgs = listOf("-c:a", "aac", "-b:a", "256k"),
            extraArgs = listOf("-movflags", "+faststart"),
        ),
        Preset(
            id = "flac_lossless",
            title = "FLAC · 无损",
            subtitle = "无损归档音频",
            extension = "flac",
            category = PresetCategory.AUDIO,
            isAudioOnly = true,
            badge = "FLAC",
            audioArgs = listOf("-c:a", "flac", "-compression_level", "8"),
        ),
        Preset(
            id = "wav_pcm",
            title = "WAV · PCM 16 位",
            subtitle = "录音棚级未压缩音频",
            extension = "wav",
            category = PresetCategory.AUDIO,
            isAudioOnly = true,
            badge = "WAV",
            audioArgs = listOf("-c:a", "pcm_s16le"),
        ),
        Preset(
            id = "opus_audio",
            title = "Opus · 160 kbps",
            subtitle = "现代低码率音频",
            extension = "opus",
            category = PresetCategory.AUDIO,
            isAudioOnly = true,
            badge = "OPUS",
            audioArgs = listOf("-c:a", "libopus", "-b:a", "160k"),
        ),
        Preset(
            id = "jpg_max",
            title = "JPEG · 高质量",
            subtitle = "静帧，最长边 2560 px",
            extension = "jpg",
            category = PresetCategory.IMAGE,
            badge = "JPG",
            filterArgs = listOf("-vf", "scale=2560:-2"),
            extraArgs = listOf("-frames:v", "1", "-q:v", "2"),
        ),
        Preset(
            id = "png_image",
            title = "PNG · 无损",
            subtitle = "带透明通道的静帧",
            extension = "png",
            category = PresetCategory.IMAGE,
            badge = "PNG",
            filterArgs = listOf("-vf", "scale=2560:-2"),
            extraArgs = listOf("-frames:v", "1"),
        ),
        Preset(
            id = "remux_mp4",
            title = "封装 · MP4 直接复制",
            subtitle = "不重新编码，速度最快",
            extension = "mp4",
            category = PresetCategory.UTILITY,
            badge = "COPY",
            videoArgs = listOf("-c", "copy"),
            extraArgs = listOf("-movflags", "+faststart"),
        ),
    )

    val video = all.filter { it.category == PresetCategory.VIDEO }
    val audio = all.filter { it.category == PresetCategory.AUDIO }
    val image = all.filter { it.category == PresetCategory.IMAGE }
    val utility = all.filter { it.category == PresetCategory.UTILITY }

    fun byId(id: String): Preset = all.firstOrNull { it.id == id } ?: all.first()
}

object CommandBuilder {
    fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun filterArgs(args: List<String>): List<String> {
        val out = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            if (args[index] == "-vf" && index + 1 < args.size) {
                out += "-vf"
                out += quote(args[index + 1])
                index += 2
            } else {
                out += args[index]
                index++
            }
        }
        return out
    }

    fun convert(input: MediaItem, outputFile: java.io.File, preset: Preset): String {
        val args = mutableListOf("ffmpeg", "-hide_banner", "-y", "-i", quote(input.path))
        if (preset.isAudioOnly) {
            args += "-vn"
        }
        args += filterArgs(preset.filterArgs)
        args += preset.videoArgs
        args += preset.audioArgs
        args += preset.extraArgs
        args += listOf("-map_metadata", "0", quote(outputFile.absolutePath))
        return args.joinToString(" ")
    }

    fun merge(inputs: List<MediaItem>, options: MergeOptions, outputFile: java.io.File): String {
        require(inputs.isNotEmpty()) { "Select at least two clips to merge" }
        val isVideoJob = inputs.any { it.kind == MediaKind.VIDEO || it.kind == MediaKind.IMAGE }
        val width = options.width.coerceIn(240, 3840)
        val height = options.height.coerceIn(240, 2160)
        val fps = options.fps.coerceIn(1, 60)
        val args = mutableListOf("ffmpeg", "-hide_banner", "-y")

        if (isVideoJob) {
            inputs.forEach { item ->
                if (item.kind == MediaKind.IMAGE) {
                    val duration = max(1.0, (item.durationMs / 1000.0).takeIf { it > 0 } ?: 3.0)
                    args += listOf("-loop", "1", "-framerate", fps.toString(), "-t", fmt(duration), "-i", quote(item.path))
                } else {
                    args += listOf("-i", quote(item.path))
                }
            }
            val filters = mutableListOf<String>()
            inputs.forEachIndexed { index, item ->
                val duration = max(0.5, if (item.durationMs > 0) item.durationMs / 1000.0 else 3.0)
                val hasVideo = item.kind == MediaKind.VIDEO || item.kind == MediaKind.IMAGE
                if (hasVideo) {
                    filters += "[$index:v:0]scale=$width:$height:force_original_aspect_ratio=decrease," +
                        "pad=$width:$height:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=$fps,format=yuv420p," +
                        "trim=duration=${fmt(duration)},setpts=PTS-STARTPTS[v$index]"
                } else {
                    filters += "color=c=black:s=${width}x$height:r=$fps:d=${fmt(duration)},format=yuv420p,setsar=1[v$index]"
                }
                if (item.audioCodec.isNotBlank() || item.kind == MediaKind.AUDIO) {
                    filters += "[$index:a:0]aresample=48000,aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo," +
                        "atrim=0:${fmt(duration)},asetpts=PTS-STARTPTS[a$index]"
                } else {
                    filters += "anullsrc=r=48000:cl=stereo,atrim=0:${fmt(duration)},asetpts=PTS-STARTPTS[a$index]"
                }
            }
            val concatInputs = inputs.indices.joinToString("") { "[v$it][a$it]" }
            filters += "${concatInputs}concat=n=${inputs.size}:v=1:a=1[vout][aout]"
            args += listOf("-filter_complex", quote(filters.joinToString(";")))
            args += listOf("-map", "[vout]", "-map", "[aout]")
            args += codecArgsForContainer(options.container, options.videoBitrate, options.audioBitrate, video = true)
        } else {
            inputs.forEach { args += listOf("-i", quote(it.path)) }
            val filters = mutableListOf<String>()
            inputs.forEachIndexed { index, item ->
                val duration = max(0.5, item.durationMs / 1000.0)
                filters += "[$index:a:0]aresample=48000,aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo," +
                    "atrim=0:${fmt(duration)},asetpts=PTS-STARTPTS[a$index]"
            }
            filters += inputs.indices.joinToString("") { "[a$it]" } + "concat=n=${inputs.size}:v=0:a=1[aout]"
            args += listOf("-filter_complex", quote(filters.joinToString(";")))
            args += listOf("-map", "[aout]")
            args += codecArgsForContainer(options.container, options.videoBitrate, options.audioBitrate, video = false)
        }
        args += quote(outputFile.absolutePath)
        return args.joinToString(" ")
    }

    fun split(input: MediaItem, options: SplitOptions, outputDir: java.io.File): String {
        val durationSec = max(0.5, input.durationMs / 1000.0)
        val segmentSeconds = when (options.mode) {
            SplitMode.DURATION -> options.secondsPerPart.coerceAtLeast(1)
            SplitMode.PARTS -> max(1, kotlin.math.ceil(durationSec / options.parts.coerceAtLeast(1)).toInt())
        }
        val ext = options.outputFormat.lowercase().ifBlank { "mp4" }
        val target = java.io.File(outputDir, "part_%03d.$ext")
        val args = mutableListOf("ffmpeg", "-hide_banner", "-y", "-i", quote(input.path))
        val codecs = codecArgsForContainer(ext, "6M", "192k", video = input.kind != MediaKind.AUDIO)
        if (options.reencode) {
            if (input.kind == MediaKind.AUDIO) {
                args += codecArgsForContainer(ext, "6M", "192k", video = false, audioOnly = true)
                args += "-vn"
            } else {
                args += codecs
                args += filterArgs(listOf("-vf", "scale=1920:-2"))
            }
        } else {
            args += listOf("-c", "copy", "-map", "0")
        }
        if (options.reencode && input.kind != MediaKind.AUDIO) {
            args += listOf("-force_key_frames", "expr:gte(t,n_forced*$segmentSeconds)")
        }
        args += listOf(
            "-f", "segment",
            "-segment_time", segmentSeconds.toString(),
            "-reset_timestamps", "1",
            "-avoid_negative_ts", "make_zero",
        )
        if (options.reencode) args += listOf("-segment_format", segmentFormatFor(ext))
        args += quote(target.absolutePath)
        return args.joinToString(" ")
    }

    fun codecArgsForContainer(container: String, videoBitrate: String, audioBitrate: String, video: Boolean, audioOnly: Boolean = false): List<String> {
        val ext = container.lowercase().substringAfterLast('.').ifBlank { "mp4" }
        if (!video || audioOnly) {
            return when (ext) {
                "mp3" -> listOf("-c:a", "libmp3lame", "-b:a", "320k")
                "wav" -> listOf("-c:a", "pcm_s16le")
                "flac" -> listOf("-c:a", "flac")
                "opus", "ogg" -> listOf("-c:a", "libopus", "-b:a", audioBitrate)
                "m4a", "aac" -> listOf("-c:a", "aac", "-b:a", audioBitrate, "-movflags", "+faststart")
                else -> listOf("-c:a", "aac", "-b:a", audioBitrate)
            }
        }
        return when (ext) {
            "webm" -> listOf(
                "-c:v", "libvpx-vp9", "-b:v", videoBitrate, "-crf", "32", "-row-mt", "1",
                "-c:a", "libopus", "-b:a", audioBitrate,
            )
            "mov" -> listOf(
                "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", audioBitrate,
            )
            "mkv" -> listOf(
                "-c:v", "libx264", "-preset", "medium", "-crf", "22", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", audioBitrate,
            )
            else -> listOf(
                "-c:v", "libx264", "-preset", "medium", "-crf", "22", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", audioBitrate, "-movflags", "+faststart",
            )
        }
    }

    fun segmentFormatFor(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4a", "mov", "3gp" -> "mp4"
        "mkv" -> "matroska"
        "webm" -> "webm"
        "mp3" -> "mp3"
        "wav" -> "wav"
        "flac" -> "flac"
        "ogg", "opus" -> "ogg"
        "ts" -> "mpegts"
        else -> ext
    }

    fun mergeOutputName(inputs: List<MediaItem>, container: String): String {
        val base = inputs.firstOrNull()?.name?.substringBeforeLast('.') ?: "merged"
        return "${base}_merged.${container.lowercase()}"
    }

    fun splitOutputName(input: MediaItem): String = "${input.name.substringBeforeLast('.')}_split"

    fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)
}
