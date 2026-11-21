package com.ffmpeg.gui.ai

import com.ffmpeg.gui.data.AiSettings
import com.ffmpeg.gui.data.ChatMessage
import com.ffmpeg.gui.data.ChatTurn
import com.ffmpeg.gui.data.ToolTrace
import com.ffmpeg.gui.data.Workspace
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

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
        onStatus: (String) -> Unit = {},
    ): ChatTurn {
        val traces = mutableListOf<ToolTrace>()
        val messages = mutableListOf<ChatMsg>()
        messages += ChatMsg(role = "system", content = systemPrompt(settings))
        history.filter { it.role == "user" || it.role == "assistant" }
            .takeLast(24)
            .forEach { message ->
                messages += ChatMsg(role = message.role, content = message.content)
            }
        messages += ChatMsg(role = "user", content = userMessage)

        var lastContent = ""
        val maxRounds = settings.maxIterations.coerceIn(1, 24)
        repeat(maxRounds) { iteration ->
            coroutineContext.ensureActive()
            onStatus(if (iteration == 0) "正在思考…" else "正在整理结果…")
            val completion = client.complete(settings, messages, executor.specs)
            if (completion.content.isNotBlank()) lastContent = completion.content
            if (completion.toolCalls.isEmpty()) {
                return ChatTurn(
                    assistant = completion.content.ifBlank { lastContent.ifBlank { "完成。" } },
                    traces = traces,
                )
            }
            val toolCalls = completion.toolCalls.mapIndexed { index, call ->
                if (call.id.isBlank()) call.copy(id = "call_${iteration}_$index") else call
            }
            messages += ChatMsg(
                role = "assistant",
                content = completion.content.ifBlank { null },
                toolCalls = toolCalls,
            )
            toolCalls.forEachIndexed { index, call ->
                coroutineContext.ensureActive()
                val name = call.function.name
                val args = call.function.arguments.ifBlank { "{}" }
                onStatus(statusFor(name))
                val result = withTimeoutOrNull(30 * 60 * 1000L) {
                    executor.execute(name, args)
                } ?: "错误：工具执行超过 30 分钟，已中止等待。"
                val clipped = result.take(18_000)
                val trace = ToolTrace(name = name, arguments = args, result = clipped)
                traces += trace
                onTrace(trace)
                messages += ChatMsg(
                    role = "tool",
                    content = clipped,
                    toolCallId = call.id.ifBlank { "call_${iteration}_$index" },
                )
            }
        }
        return ChatTurn(
            assistant = lastContent.ifBlank { "已达到 ${maxRounds} 轮工具迭代上限。请查看工具调用记录，或把任务拆小后继续。" },
            traces = traces,
        )
    }

    private fun systemPrompt(settings: AiSettings): String = buildString {
        appendLine(settings.systemPrompt)
        appendLine()
        appendLine("## 运行环境")
        appendLine("- 应用：FFmpeg GUI，内置 FFmpeg 6.0（GPL full build）与 Python 3.11。")
        appendLine("- 工作区绝对路径：${workspace.root.absolutePath}")
        appendLine("- 导入目录：${workspace.inbox.absolutePath}")
        appendLine("- 输出目录：${workspace.outputs.absolutePath}")
        appendLine("- AI Python 脚本专用目录：${workspace.aiScriptsDir.absolutePath}")
        appendLine("- Python 库目录：${workspace.libsDir.absolutePath}")
        appendLine("- 相对路径按工作区解析；FFmpeg 命令中的路径必须写成绝对路径。")
        appendLine()
        appendLine("## 工作规则")
        appendLine("1. 用户要求转换、合并、拆分、抽取、压制、批量处理时，必须实际调用工具完成，不能只给命令建议。")
        appendLine("2. 操作前先用 fs_list / media_probe 查看真实文件和媒体参数，禁止猜测文件名。")
        appendLine("3. 输出统一写入 outputs 目录，用 -y 覆盖，并在最后报告每个输出文件的绝对路径和大小。")
        appendLine("4. 多个文件先探测，再逐个处理；超过 10 个文件优先写一个 Python 批处理脚本并保存到 AI 脚本目录，然后运行。")
        appendLine("5. 合并不同分辨率/帧率/音轨时，先说明将如何统一参数；纯拼接优先 concat demuxer，编码不同再重编码。")
        appendLine("6. 拆分必须尊重用户指定的输出格式和分段方式；需要精确切分时重新编码并使用 -force_key_frames。")
        appendLine("7. 生成的 Python 脚本必须通过 python_save_script 保存到专用目录，再通过 python_run 运行。")
        appendLine("8. 删除、覆盖用户文件前先说明；不要删除工作区根目录。")
        appendLine("9. 任务很长时分段执行，报告进度；工具报错时读取日志定位原因并尝试可行替代方案。")
        appendLine("10. 回答使用简体中文，简洁说明做了什么、结果在哪里、下一步可做什么。")
        appendLine()
        appendLine("## 可用工具")
        appendLine(executor.specs.joinToString("\n") { "- ${it.function.name}：${it.function.description}" })
        appendLine()
        appendLine("## 工作区快照")
        val tree = workspace.listTree(limit = 70)
        if (tree.isEmpty()) {
            appendLine("（空）")
        } else {
            tree.take(60).forEach { entry ->
                appendLine(if (entry.isDirectory) "[目录] ${entry.relativePath}" else "[文件] ${entry.relativePath}（${entry.sizeBytes} 字节）")
            }
        }
    }

    private fun statusFor(name: String): String = when (name) {
        "ffmpeg_run" -> "正在执行 FFmpeg…"
        "ffmpeg_capabilities" -> "正在查询 FFmpeg 能力…"
        "python_run" -> "正在运行 Python…"
        "python_save_script" -> "正在保存 Python 脚本…"
        "download_file" -> "正在下载文件…"
        "media_probe" -> "正在探测媒体…"
        "fs_list", "fs_read" -> "正在读取文件…"
        "fs_write", "fs_copy", "fs_move", "fs_delete", "fs_mkdir" -> "正在整理文件…"
        else -> "正在调用 ${name}…"
    }
}
