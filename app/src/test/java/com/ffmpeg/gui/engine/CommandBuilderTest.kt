package com.ffmpeg.gui.engine

import com.ffmpeg.gui.data.MediaItem
import com.ffmpeg.gui.data.MediaKind
import com.ffmpeg.gui.data.MergeOptions
import com.ffmpeg.gui.data.SplitMode
import com.ffmpeg.gui.data.SplitOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandBuilderTest {
    private fun item(
        name: String,
        kind: MediaKind = MediaKind.VIDEO,
        duration: Long = 10_000,
        audioCodec: String = "aac",
    ): MediaItem {
        return MediaItem(
            id = name,
            name = name,
            path = "/tmp/workspace/inbox/$name",
            kind = kind,
            durationMs = duration,
            videoCodec = if (kind == MediaKind.VIDEO) "h264" else "",
            audioCodec = audioCodec,
        )
    }

    @Test
    fun convertBuildsPresetCommand() {
        val command = CommandBuilder.convert(item("clip.mov"), File("/tmp/out.mp4"), Presets.byId("mp4_720_h264"))
        assertTrue(command.startsWith("ffmpeg "))
        assertTrue(command.contains("-i \"/tmp/workspace/inbox/clip.mov\""))
        assertTrue(command.contains("libx264"))
        assertTrue(command.contains("/tmp/out.mp4"))
    }

    @Test
    fun mergeNormalisesDurationsAndAudio() {
        val command = CommandBuilder.merge(
            listOf(item("a.mp4"), item("b.wav", MediaKind.AUDIO, 5_000)),
            MergeOptions(width = 1280, height = 720, fps = 30, container = "mp4"),
            File("/tmp/merged.mp4"),
        )
        assertTrue(command.contains("concat=n=2:v=1:a=1"))
        assertTrue(command.contains("scale=1280:720"))
        assertTrue(command.contains("[vout]"))
        assertTrue(command.contains("libx264"))
    }

    @Test
    fun mergeInsertsSilenceForSilentVideo() {
        val command = CommandBuilder.merge(
            listOf(item("silent.mp4", audioCodec = ""), item("music.mp3", MediaKind.AUDIO)),
            MergeOptions(width = 1280, height = 720, fps = 30, container = "mp4"),
            File("/tmp/merged.mp4"),
        )
        assertTrue(command.contains("anullsrc"))
        assertTrue(command.contains("concat=n=2:v=1:a=1"))
    }

    @Test
    fun splitUsesSegmentMuxerAndRequestedFormat() {
        val command = CommandBuilder.split(
            item("long.mp4", duration = 120_000),
            SplitOptions(mode = SplitMode.DURATION, secondsPerPart = 30, outputFormat = "mkv"),
            File("/tmp/split"),
        )
        assertTrue(command.contains("-f segment"))
        assertTrue(command.contains("-segment_time 30"))
        assertTrue(command.contains("part_%03d.mkv"))
        assertTrue(command.contains("libx264"))
    }

    @Test
    fun audioOnlyMergeProducesAudioGraph() {
        val command = CommandBuilder.merge(
            listOf(item("a.mp3", MediaKind.AUDIO, 10_000), item("b.mp3", MediaKind.AUDIO, 10_000)),
            MergeOptions(container = "mp3"),
            File("/tmp/audio.mp3"),
        )
        assertTrue(command.contains("concat=n=2:v=0:a=1"))
        assertTrue(command.contains("libmp3lame"))
    }
}
