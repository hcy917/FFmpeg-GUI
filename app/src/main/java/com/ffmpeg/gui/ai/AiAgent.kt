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
                appendLine("请始终使用简体中文回答用户。")
                appendLine()
                appendLine("工作区根目录（绝对路径）：${workspace.root.absolutePath}")
                appendLine("输出目录：${workspace.outputs.absolutePath}")
                appendLine("Python 脚本目录：${workspace.pythonDir.absolutePath}")
                appendLine("导入文件目录：${workspace.inbox.absolutePath}")
                appendLine("使用 workspace_list 查看文件；FFmpeg 与 Python 工具均可调用。")
                appendLine("调用 ffmpeg_run 时，把输出写入 outputs 目录，并使用绝对路径。")
                appendLine()
                appendLine("工作区快照：")
                workspace.listTree(limit = 80).take(60).forEach { entry ->
                    appendLine(if (entry.isDirectory) "[目录] ${entry.relativePath}" else "[文件] ${entry.relativePath}")
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
                    assistant = completion.content.ifBlank { lastContent.ifBlank { "完成。" } },
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
            assistant = lastContent.ifBlank { "已达到工具迭代上限，请查看工具调用记录了解详情。" },
            traces = traces,
        )
    }
}
