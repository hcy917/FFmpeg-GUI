package com.ffmpeg.gui.ai

import com.ffmpeg.gui.data.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = "{}",
)

@Serializable
data class ToolCall(
    val id: String = "",
    val type: String = "function",
    val function: FunctionCall = FunctionCall(),
)

@Serializable
data class ChatMsg(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class ToolSpec(
    val type: String = "function",
    val function: ToolFunctionSpec,
)

@Serializable
data class ToolFunctionSpec(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val tools: List<ToolSpec>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double = 0.3,
    val stream: Boolean = false,
)

@Serializable
private data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
private data class Choice(
    val message: ChatMsg? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class ApiError(
    val message: String = "",
    val type: String = "",
)

data class AiCompletion(
    val content: String,
    val toolCalls: List<ToolCall>,
    val finishReason: String = "",
)

class OpenAiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        settings: AiSettings,
        messages: List<ChatMsg>,
        tools: List<ToolSpec>,
    ): AiCompletion = withContext(Dispatchers.IO) {
        val base = settings.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IOException("请先在设置中填写 AI 接口地址")
        val endpoint = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val requestBody = ChatRequest(
            model = settings.model.trim().ifBlank { "deepseek-chat" },
            messages = messages,
            tools = tools,
            toolChoice = if (tools.isEmpty()) null else "auto",
            temperature = settings.temperature,
        )
        val body = json.encodeToString(ChatRequest.serializer(), requestBody).toRequestBody(mediaType)
        val builder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
        if (settings.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${settings.apiKey.trim()}")
        }
        val call = http.newCall(builder.build())
        suspendCancellableCoroutine<AiCompletion> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { continuation.resume(parseResponse(it)) }
                    } catch (t: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }
                }
            })
        }
    }

    private fun parseResponse(response: Response): AiCompletion {
        val text = response.body.string()
        if (!response.isSuccessful) {
            val message = runCatching {
                json.decodeFromString<ChatResponse>(text).error?.message
            }.getOrNull()
            throw IOException("AI 请求失败（${response.code}）：${message ?: text.take(600)}")
        }
        val parsed = runCatching { json.decodeFromString<ChatResponse>(text) }
            .getOrElse { throw IOException("无法解析 AI 响应：${text.take(500)}") }
        parsed.error?.let { throw IOException("AI 错误：${it.message}") }
        val choice = parsed.choices.firstOrNull() ?: throw IOException("AI 未返回任何回复")
        return AiCompletion(
            content = choice.message?.content ?: "",
            toolCalls = choice.message?.toolCalls ?: emptyList(),
            finishReason = choice.finishReason ?: "",
        )
    }
}
