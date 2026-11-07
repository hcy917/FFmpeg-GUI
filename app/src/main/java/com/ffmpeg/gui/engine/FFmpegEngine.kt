package com.ffmpeg.gui.engine

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

data class FFmpegResult(
    val success: Boolean,
    val cancelled: Boolean,
    val returnCode: Int,
    val output: String,
    val failStackTrace: String = "",
    val elapsedMs: Long = 0,
)

object FfmpegGate {
    private val semaphore = Semaphore(1, true)

    fun acquireBlocking() = semaphore.acquire()
    fun release() = semaphore.release()
}

class FFmpegEngine {
    private val activeSession = AtomicLong(-1L)

    suspend fun run(
        rawCommand: String,
        onLog: (String) -> Unit = {},
        onProgress: (timeSeconds: Double, frames: Int, speed: Double) -> Unit = { _, _, _ -> },
    ): FFmpegResult = withContext(Dispatchers.IO) {
        FfmpegGate.acquireBlocking()
        try {
            val command = normalize(rawCommand)
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val sessionRef = AtomicLong(-1L)
                val start = System.currentTimeMillis()
                lateinit var session: FFmpegSession
                session = FFmpegKit.executeAsync(
                    command,
                    { completed ->
                        activeSession.set(-1L)
                        if (finished.compareAndSet(false, true)) {
                            val output = runCatching { completed.allLogsAsString }.getOrDefault("")
                            continuation.resume(
                                FFmpegResult(
                                    success = ReturnCode.isSuccess(completed.returnCode),
                                    cancelled = ReturnCode.isCancel(completed.returnCode),
                                    returnCode = completed.returnCode?.value ?: -1,
                                    output = output,
                                    failStackTrace = completed.failStackTrace ?: "",
                                    elapsedMs = System.currentTimeMillis() - start,
                                )
                            )
                        }
                    },
                    { log -> onLog(log.message ?: "") },
                    { stats ->
                        onProgress(stats.time, stats.videoFrameNumber, stats.speed.toDouble())
                    },
                )
                sessionRef.set(session.sessionId)
                activeSession.set(session.sessionId)
                continuation.invokeOnCancellation {
                    runCatching { FFmpegKit.cancel(session.sessionId) }
                    activeSession.set(-1L)
                    if (finished.compareAndSet(false, true)) {
                        // The coroutine is already cancelled; the callback result is ignored.
                    }
                }
            }
        } finally {
            FfmpegGate.release()
        }
    }

    fun cancelCurrent(): Boolean {
        val id = activeSession.get()
        return if (id > 0) {
            runCatching { FFmpegKit.cancel(id) }.isSuccess
        } else false
    }

    fun cancelAll() {
        runCatching { FFmpegKit.cancel() }
        activeSession.set(-1L)
    }

    /** Blocking execution used by the Python bridge and AI tools. */
    fun runBlocking(rawCommand: String, onLog: (String) -> Unit = {}): String {
        FfmpegGate.acquireBlocking()
        return try {
            val session = FFmpegKit.execute(normalize(rawCommand))
            val output = session.allLogsAsString ?: ""
            output.lineSequence().forEach(onLog)
            output
        } finally {
            FfmpegGate.release()
        }
    }

    fun normalize(command: String): String {
        var value = command.trim()
        if (value.startsWith("ffmpeg ")) value = value.removePrefix("ffmpeg ").trimStart()
        else if (value == "ffmpeg") value = ""
        return value
    }

    fun version(): String = runCatching { FFmpegKitConfig.getFFmpegVersion() ?: "unknown" }.getOrDefault("unknown")
    fun buildDate(): String = runCatching { FFmpegKitConfig.getBuildDate() ?: "unknown" }.getOrDefault("unknown")
    fun packageVersion(): String = runCatching { FFmpegKitConfig.getVersion() ?: "unknown" }.getOrDefault("unknown")

    fun parseVersionSummary(): String {
        return "FFmpeg ${version()} · kit ${packageVersion()}"
    }
}
