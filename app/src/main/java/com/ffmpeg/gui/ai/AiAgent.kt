package com.ffmpeg.gui.ai

import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.ChatMessage
import com.ffmpeg.gui.data.ChatTurn
import com.ffmpeg.gui.data.ToolTrace
import com.ffmpeg.gui.data.Workspace

class AiAgent(
    private val client: OpenAiClient,
    private val executor: AiToolExecutor,
    private val workspace: Workspace,
) {
    suspend fun run(
        history: List<ChatMessage>,
        userMessage: String,
        settings: AiSettings,
        onTrace: (ToolTrace) -> Unit = {},
    ): ChatTurn {
        val traces = mutableListOf<ToolTrace>()
        val messages = mutableListOf<ChatMsg>()
        messages += ChatMsg(
            role = "system",
            content = buildString {
                appendLine(settings.systemPrompt)
                appendLine()
                appendLine("Workspace root (absolute): ${workspace.root.absolutePath}")
                appendLine("Outputs directory: ${workspace.outputs.absolutePath}")
                appendLine("Python scripts: ${workspace.pythonDir.absolutePath}")
                appendLine("Imported files: ${workspace.inbox.absolutePath}")
                appendLine("Use workspace_list to inspect files. FFmpeg and Python tools are available.")
                appendLine("When using ffmpeg_run, write outputs under the outputs directory and use absolute paths.")
                appendLine()
                appendLine("Workspace snapshot:")
                workspace.listTree(limit = 80).take(60).forEach { entry ->
                    appendLine(if (entry.isDirectory) "[dir] ${entry.relativePath}" else "[file] ${entry.relativePath}")
                }
            },
        )
        history.filter { it.role == "user" || it.role == "assistant" }
            .takeLast(18)
            .forEach { message ->
                messages += ChatMsg(role = message.role, content = message.content)
            }
        messages += ChatMsg(role = "user", content = userMessage)

        var lastContent = ""
        repeat(settings.maxIterations.coerceIn(1, 12)) { iteration ->
            val completion = client.complete(settings, messages, executor.specs)
            if (completion.content.isNotBlank()) lastContent = completion.content
            if (completion.toolCalls.isEmpty()) {
                return ChatTurn(
                    assistant = completion.content.ifBlank { lastContent.ifBlank { "Done." } },
                    traces = traces,
                )
            }
            messages += ChatMsg(
                role = "assistant",
                content = completion.content.ifBlank { null },
                toolCalls = completion.toolCalls,
            )
            completion.toolCalls.forEach { call ->
                val name = call.function.name
                val args = call.function.arguments.ifBlank { "{}" }
                val result = executor.execute(name, args).take(16_000)
                val trace = ToolTrace(name = name, arguments = args, result = result)
                traces += trace
                onTrace(trace)
                messages += ChatMsg(role = "tool", content = result, toolCallId = call.id.ifBlank { "call_$iteration" })
            }
        }
        return ChatTurn(
            assistant = lastContent.ifBlank { "I reached the tool-iteration limit. Check the tool traces for details." },
            traces = traces,
        )
    }
}
