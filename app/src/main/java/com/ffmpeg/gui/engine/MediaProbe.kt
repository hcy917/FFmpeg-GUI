package com.ffmpeg.gui.engine

import com.arthenica.ffmpegkit.FFprobeKit
import com.ffmpeg.gui.data.ProbeInfo
import com.ffmpeg.gui.data.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class RawProbe(
    val format: RawFormat? = null,
    val streams: List<RawStream> = emptyList(),
)

@Serializable
private data class RawFormat(
    val filename: String = "",
    @SerialName("format_name") val formatName: String = "",
    @SerialName("format_long_name") val formatLong: String = "",
    val duration: String = "",
    val size: String = "",
    @SerialName("bit_rate") val bitRate: String = "",
)

@Serializable
private data class RawStream(
    val index: Int = 0,
    @SerialName("codec_type") val type: String = "",
    @SerialName("codec_name") val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("sample_rate") val sampleRate: String = "",
    val channels: Int = 0,
    val duration: String = "",
)

object MediaProbe {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun probe(file: File): ProbeInfo = withContext(Dispatchers.IO) {
        runCatching {
            val command = "-v error -print_format json -show_format -show_streams ${escape(file.absolutePath)}"
            val session = FFprobeKit.execute(command)
            val rawText = session.output ?: ""
            val start = rawText.indexOf('{')
            val end = rawText.lastIndexOf('}')
            val raw = if (start >= 0 && end > start) {
                json.decodeFromString<RawProbe>(rawText.substring(start, end + 1))
            } else null
            val duration = raw?.format?.duration?.toDoubleOrNull() ?: raw?.streams?.firstOrNull()?.duration?.toDoubleOrNull() ?: 0.0
            val streams = raw?.streams?.map { stream ->
                TrackInfo(
                    index = stream.index,
                    type = stream.type,
                    codec = stream.codec,
                    width = stream.width,
                    height = stream.height,
                    sampleRate = stream.sampleRate,
                    channels = if (stream.channels > 0) stream.channels.toString() else "",
                    durationMs = (stream.duration.toDoubleOrNull() ?: 0.0).times(1000).toLong(),
                )
            } ?: emptyList()
            ProbeInfo(
                format = raw?.format?.formatName ?: "",
                formatLong = raw?.format?.formatLong ?: "",
                durationMs = (duration * 1000).toLong(),
                sizeBytes = raw?.format?.size?.toLongOrNull() ?: file.length(),
                bitrate = raw?.format?.bitRate?.toLongOrNull() ?: 0L,
                streams = streams,
            )
        }.getOrElse {
            ProbeInfo(format = file.extension, sizeBytes = file.length())
        }
    }

    private fun escape(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""
}
