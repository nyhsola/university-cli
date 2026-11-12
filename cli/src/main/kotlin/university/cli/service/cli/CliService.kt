package university.cli.service.cli

import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import university.cli.command.CommandDispatcher
import university.cli.command.CommandMessageType
import university.cli.command.CommandResult
import university.cli.service.chat.ChatOutputService
import university.cli.service.chat.ChatStatusService

class CliService(
    private val commandDispatcher: CommandDispatcher = CommandDispatcher(emptyList()),
    private val terminal: Terminal = Terminal(),
    private val chatStatusService: ChatStatusService = ChatStatusService(),
    private val chatOutputService: ChatOutputService = ChatOutputService(),
) {
    private var statusLineWidth = 0

    private companion object {
        const val HELP_COMMAND = "/help"
    }

    fun execute(arguments: Array<String>): Int {
        val commandName = normalizeCommand(arguments.firstOrNull())
        val commandArguments = if (arguments.isEmpty()) emptyList() else arguments.drop(1)
        val statusSubscription = chatStatusService.observe(::renderStatus)
        val outputSubscription = chatOutputService.observe { message ->
            clearStatusLine()
            terminal.println(style(message.text, message.type))
        }

        return try {
            val result = commandDispatcher.dispatch(commandName, commandArguments)
            clearStatusLine()
            printResult(result)
            if (result.isFailure()) 1 else 0
        } finally {
            clearStatusLine()
            outputSubscription.close()
            statusSubscription.close()
        }
    }

    private fun normalizeCommand(argument: String?): String = when (argument) {
        null, "-h", "--help" -> HELP_COMMAND
        else -> "/${argument.trim().trimStart('/').lowercase()}"
    }

    private fun printResult(result: CommandResult) {
        if (result.lines.isNotEmpty()) {
            result.lines.forEach { terminal.println(style(it.text, it.type)) }
            return
        }
        result.message?.lineSequence()?.forEach { terminal.println(style(it, result.messageType)) }
    }

    @Synchronized
    private fun renderStatus(status: String?) {
        clearStatusLine()
        if (status.isNullOrBlank()) return

        terminal.rawPrint(dim(status))
        statusLineWidth = status.length
    }

    @Synchronized
    private fun clearStatusLine() {
        if (statusLineWidth == 0) return

        terminal.rawPrint("\r${" ".repeat(statusLineWidth)}\r")
        statusLineWidth = 0
    }

    private fun style(text: String, type: CommandMessageType): String = when (type) {
        CommandMessageType.INFO -> text
        CommandMessageType.SUCCESS -> green(text)
        CommandMessageType.WARNING -> yellow(text)
        CommandMessageType.MUTED -> dim(text)
        CommandMessageType.ACCENT -> (bold + cyan)(text)
    }

    private fun CommandResult.isFailure(): Boolean =
        messageType == CommandMessageType.WARNING || lines.any { it.type == CommandMessageType.WARNING }
}
