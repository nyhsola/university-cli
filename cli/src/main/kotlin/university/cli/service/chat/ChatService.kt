package university.cli.service.chat

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.RawModeScope
import com.github.ajalt.mordant.input.enterRawModeOrNull
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import university.cli.command.CommandDispatcher
import university.cli.command.CommandMessageType
import university.cli.command.CommandResult
import university.cli.command.CommandSuggestion
import university.cli.service.operation.OperationCancellationService
import university.cli.util.TextUtil
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ChatService(
    private val terminal: Terminal = Terminal(),
    private val commandDispatcher: CommandDispatcher = CommandDispatcher(emptyList()),
    private val chatStatusService: ChatStatusService = ChatStatusService(),
    private val chatOutputService: ChatOutputService = ChatOutputService(),
    private val cancellationService: OperationCancellationService = OperationCancellationService(),
) {
    fun start() {
        val rawMode = terminal.enterRawModeOrNull(MouseTracking.Off)
        if (rawMode == null) {
            startLineMode()
            return
        }

        terminal.rawPrint(ENTER_ALTERNATE_SCREEN + ENABLE_ALTERNATE_SCROLL + SHOW_CURSOR + BLINKING_BAR_CURSOR)
        val history = try {
            rawMode.use(::startInteractiveMode)
        } finally {
            terminal.rawPrint(DISABLE_ALTERNATE_SCROLL + DEFAULT_CURSOR + SHOW_CURSOR + LEAVE_ALTERNATE_SCREEN)
        }
        printTranscript(history)
    }

    private fun startLineMode() {
        printHeader()
        printResult(commandDispatcher.dispatch("/help"))
        val outputSubscription = chatOutputService.observe { message ->
            terminal.println(CHAT_PADDING + styleMessage(message.text, message.type))
        }
        try {
            while (true) {
                val input = terminal.prompt("", promptSuffix = "> ") ?: break
                val result = commandDispatcher.dispatch(input)
                printResult(result)
                if (result.shouldExit) break
            }
        } finally {
            outputSubscription.close()
        }
    }

    private fun startInteractiveMode(input: RawModeScope): List<ScreenMessage> {
        val history = commandDispatcher.dispatch("/help").toScreenMessages().toMutableList()
        val buffer = StringBuilder()
        val inputReader = ConsoleInputReader(input)
        var selectedIndex = 0
        var suggestionsDismissed = false
        var running = true
        var scrollOffset = 0
        val commandExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "university-command").apply { isDaemon = true }
        }
        val statusSubscription = chatStatusService.observe(::renderStatusLine)
        val outputSubscription = chatOutputService.observe { message ->
            history += ScreenMessage(message.text, message.type)
            scrollOffset = renderScreen(history, StringBuilder(), emptyList(), 0, 0)
        }

        try {
            while (running) {
                val suggestions = visibleSuggestions(buffer, suggestionsDismissed)
                selectedIndex = selectedIndex.coerceIn(0, (suggestions.size - 1).coerceAtLeast(0))
                scrollOffset = renderScreen(history, buffer, suggestions, selectedIndex, scrollOffset)

                val event = inputReader.read()
                if (event is MouseEvent) {
                    scrollOffset = mouseScrollOffset(scrollOffset, event)
                    continue
                }
                val key = event as? KeyboardEvent ?: continue
                if (key.isCtrlC) {
                    history += ScreenMessage("Goodbye!", CommandMessageType.MUTED)
                    break
                }

                when (key.key) {
                "Enter" -> {
                    val enteredValue = completeInput(buffer.toString(), suggestions.getOrNull(selectedIndex))
                    if (enteredValue.isNotBlank()) {
                        history += ScreenMessage("> $enteredValue", CommandMessageType.INFO, isUser = true)
                        scrollOffset = renderScreen(history, StringBuilder(), emptyList(), 0, 0)

                        val result = executeCommand(inputReader, commandExecutor, enteredValue) { mouseEvent ->
                            scrollOffset = mouseScrollOffset(scrollOffset, mouseEvent)
                            scrollOffset = renderScreen(
                                history,
                                StringBuilder(),
                                emptyList(),
                                0,
                                scrollOffset,
                            )
                        }
                        history += result.toScreenMessages()
                        scrollOffset = 0
                        running = !result.shouldExit
                    }
                    buffer.clear()
                    selectedIndex = 0
                    suggestionsDismissed = false
                }

                "ArrowUp" -> {
                    if (suggestions.isNotEmpty()) {
                        selectedIndex = (selectedIndex - 1 + suggestions.size) % suggestions.size
                    } else {
                        scrollOffset += HISTORY_SCROLL_STEP
                    }
                }

                "ArrowDown" -> {
                    if (suggestions.isNotEmpty()) {
                        selectedIndex = (selectedIndex + 1) % suggestions.size
                    } else {
                        scrollOffset = (scrollOffset - HISTORY_SCROLL_STEP).coerceAtLeast(0)
                    }
                }

                "PageUp", "Page Up" -> scrollOffset += HISTORY_SCROLL_STEP

                "PageDown", "Page Down" -> {
                    scrollOffset = (scrollOffset - HISTORY_SCROLL_STEP).coerceAtLeast(0)
                }

                "Backspace" -> if (buffer.isNotEmpty()) {
                    buffer.deleteCharAt(buffer.lastIndex)
                    selectedIndex = 0
                    suggestionsDismissed = false
                }

                "Escape" -> suggestionsDismissed = true

                else -> if (!key.ctrl && !key.alt && key.key.length == 1) {
                    buffer.append(key.key)
                    scrollOffset = 0
                    selectedIndex = 0
                    suggestionsDismissed = false
                }
                }
            }
        } finally {
            commandExecutor.shutdownNow()
            inputReader.close()
            outputSubscription.close()
            statusSubscription.close()
        }

        return history
    }

    private fun executeCommand(
        inputReader: ConsoleInputReader,
        commandExecutor: ExecutorService,
        command: String,
        onMouseEvent: (MouseEvent) -> Unit,
    ): CommandResult {
        val future = commandExecutor.submit<CommandResult> {
            cancellationService.execute { commandDispatcher.dispatch(command) }
        }

        while (!future.isDone) {
            when (val event = inputReader.poll(COMMAND_INPUT_POLL_MILLIS)) {
                is KeyboardEvent -> if (event.isCtrlC && cancellationService.cancel()) {
                    chatStatusService.set("Cancelling...")
                }

                is MouseEvent -> onMouseEvent(event)
                null -> Unit
            }
        }

        return try {
            future.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun visibleSuggestions(
        buffer: StringBuilder,
        suggestionsDismissed: Boolean,
    ): List<CommandSuggestion> = if (suggestionsDismissed) {
        emptyList()
    } else {
        commandDispatcher.suggestions(buffer.toString())
    }

    private fun mouseScrollOffset(currentOffset: Int, event: MouseEvent): Int = when {
        event.wheelUp -> currentOffset + HISTORY_SCROLL_STEP
        event.wheelDown -> (currentOffset - HISTORY_SCROLL_STEP).coerceAtLeast(0)
        else -> currentOffset
    }

    private fun completeInput(input: String, suggestion: CommandSuggestion?): String {
        if (suggestion == null) return input

        val normalizedInput = input.trimStart()
        val typedCommand = normalizedInput.substringBefore(' ')
        val arguments = normalizedInput.removePrefix(typedCommand)
        return suggestion.name + arguments
    }

    @Synchronized
    private fun renderScreen(
        history: List<ScreenMessage>,
        buffer: StringBuilder,
        suggestions: List<CommandSuggestion>,
        selectedIndex: Int,
        scrollOffset: Int,
    ): Int {
        val width = terminal.size.width.coerceAtLeast(MINIMUM_WIDTH)
        val height = terminal.size.height.coerceAtLeast(MINIMUM_HEIGHT)
        val headerLines = headerLines()
        val suggestionStart = (selectedIndex - MAX_SUGGESTIONS + 1)
            .coerceAtLeast(0)
            .coerceAtMost((suggestions.size - MAX_SUGGESTIONS).coerceAtLeast(0))
        val suggestionLines = suggestions.drop(suggestionStart).take(MAX_SUGGESTIONS)
        val renderedSuggestionLines = suggestionLines.flatMapIndexed { index, suggestion ->
            val suggestionIndex = suggestionStart + index
            val selected = suggestionIndex == selectedIndex
            TextUtil.wrap("${suggestion.name} — ${suggestion.description}", (width - 2).coerceAtLeast(1))
                .mapIndexed { lineIndex, line ->
                    val marker = if (lineIndex == 0 && selected) ">" else " "
                    "$marker $line" to selected
                }
        }
        val fixedLineCount = renderedSuggestionLines.size + COMPOSER_HEIGHT
        val historyHeight = (height - fixedLineCount).coerceAtLeast(0)
        val renderedHistory = history.flatMap { message ->
            val messageWidth = (width - message.padding().length).coerceAtLeast(1)
            wrapLine(message.text, messageWidth).map { line ->
                ScreenMessage(line, message.type, message.isUser)
            }
        }
        val maximumScrollOffset = (renderedHistory.size - historyHeight).coerceAtLeast(0)
        val boundedScrollOffset = scrollOffset.coerceIn(0, maximumScrollOffset)
        val historyEnd = (renderedHistory.size - boundedScrollOffset).coerceAtLeast(0)
        val historyStart = (historyEnd - historyHeight).coerceAtLeast(0)
        val visibleHistory = renderedHistory.subList(historyStart, historyEnd)

        terminal.rawPrint(BEGIN_SYNCHRONIZED_UPDATE)
        try {
            terminal.cursor.move {
                clearScreen()
                setPosition(0, 0)
            }

            val emptyHistoryRows = historyHeight - visibleHistory.size
            repeat(emptyHistoryRows) { row ->
                printHistoryRow(null, row, width, headerLines)
            }
            visibleHistory.forEachIndexed { index, message ->
                printHistoryRow(message, emptyHistoryRows + index, width, headerLines)
            }

            renderedSuggestionLines.forEach { (line, selected) ->
                terminal.println(if (selected) (bold + cyan)(line) else dim(line))
            }

            printComposer(buffer, width, chatStatusService.status)
        } finally {
            terminal.rawPrint(END_SYNCHRONIZED_UPDATE)
        }
        return boundedScrollOffset
    }

    private fun printComposer(buffer: StringBuilder, width: Int, status: String?) {
        val inputPrefix = "> "
        val availableInputWidth = (width - inputPrefix.length).coerceAtLeast(0)
        val visibleInput = buffer.takeLast(availableInputWidth).toString()
        val inputLine = (inputPrefix + visibleInput).padEnd(width)
        val inputStyle = brightWhite on rgb("#303030")

        val statusLine = fitLine(status ?: DEFAULT_STATUS_HINT, width).padEnd(width)

        terminal.println(inputStyle(inputLine))
        terminal.print(dim(statusLine))
        terminal.cursor.move {
            up(1)
            startOfLine()
            right((inputPrefix.length + visibleInput.length).coerceAtMost(width - 1))
        }
    }

    @Synchronized
    private fun renderStatusLine(status: String?) {
        val width = terminal.size.width.coerceAtLeast(1)
        val row = terminal.size.height.coerceAtLeast(1)
        val statusLine = fitLine(status ?: DEFAULT_STATUS_HINT, width).padEnd(width)

        terminal.rawPrint(BEGIN_SYNCHRONIZED_UPDATE + SAVE_CURSOR + "\u001B[${row};1H")
        terminal.print(dim(statusLine))
        terminal.rawPrint(RESTORE_CURSOR + END_SYNCHRONIZED_UPDATE)
    }

    private fun printTranscript(history: List<ScreenMessage>) {
        terminal.println((bold + red)("UNIVERSITY RAG session"))
        history.forEach { message ->
            terminal.println(message.padding() + styleMessage(message.text, message.type))
        }
    }

    private fun printResult(result: CommandResult) {
        result.toScreenMessages().forEach { message ->
            terminal.println(CHAT_PADDING + styleMessage(message.text, message.type))
        }
    }

    private fun CommandResult.toScreenMessages(): List<ScreenMessage> {
        if (lines.isNotEmpty()) {
            return lines.map { line -> ScreenMessage(line.text, line.type) }
        }
        return message?.lineSequence()
            ?.map { line -> ScreenMessage(line, messageType) }
            ?.toList()
            .orEmpty()
    }

    private fun printHeader() {
        val width = terminal.size.width.coerceAtLeast(MINIMUM_WIDTH)
        headerLines().forEach { line ->
            val visibleLine = line.takeLast(width)
            terminal.println(" ".repeat((width - visibleLine.length).coerceAtLeast(0)) + (bold + red)(visibleLine))
        }
    }

    private fun printHistoryRow(
        message: ScreenMessage?,
        row: Int,
        width: Int,
        headerLines: List<String>,
    ) {
        val headerLine = headerLines.getOrNull(row)?.takeLast(width)
        if (headerLine == null) {
            val content = message?.let {
                val padding = it.padding()
                padding + styleMessage(fitLine(it.text, width - padding.length), it.type)
            }.orEmpty()
            terminal.println(content)
            return
        }

        val gapWidth = HEADER_GAP.coerceAtMost((width - headerLine.length).coerceAtLeast(0))
        val historyAreaWidth = (width - headerLine.length - gapWidth).coerceAtLeast(0)
        val padding = message?.padding().orEmpty()
        val historyContentWidth = (historyAreaWidth - padding.length).coerceAtLeast(0)
        val plainHistoryLine = message?.let {
            padding.take(historyAreaWidth) + fitLine(it.text, historyContentWidth)
        }.orEmpty().padEnd(historyAreaWidth)
        val historyLine = message?.let { styleMessage(plainHistoryLine, it.type) } ?: plainHistoryLine
        terminal.print(historyLine)
        terminal.print(" ".repeat(gapWidth))
        terminal.println((bold + red)(headerLine))
    }

    private fun headerLines(): List<String> {
        val artLines = HEADER_ART.lines()
        val title = " UNIVERSITY RAG "
        val innerWidth = maxOf(artLines.maxOf { it.length }, title.length + 1)
        val topBorder = "╭─$title${"─".repeat((innerWidth - title.length - 1).coerceAtLeast(0))}╮"
        val bottomBorder = "╰${"─".repeat(innerWidth)}╯"
        return buildList {
            add(topBorder)
            artLines.forEach { artLine ->
                add("│${artLine.padEnd(innerWidth)}│")
            }
            add(bottomBorder)
        }
    }

    private fun styleMessage(message: String, type: CommandMessageType) = when (type) {
        CommandMessageType.INFO -> message
        CommandMessageType.SUCCESS -> green(message)
        CommandMessageType.WARNING -> yellow(message)
        CommandMessageType.MUTED -> dim(message)
        CommandMessageType.ACCENT -> (bold + cyan)(message)
    }

    private fun fitLine(value: String, width: Int): String = when {
        value.length <= width -> value
        width <= 1 -> value.take(width)
        else -> value.take(width - 1) + "…"
    }

    private fun wrapLine(value: String, width: Int): List<String> =
        if (value.isEmpty()) listOf("") else value.chunked(width.coerceAtLeast(1))

    private data class ScreenMessage(
        val text: String,
        val type: CommandMessageType,
        val isUser: Boolean = false,
    )

    private fun ScreenMessage.padding(): String = if (isUser) "" else CHAT_PADDING

    companion object {
        private const val MAX_SUGGESTIONS = 5
        private const val MINIMUM_WIDTH = 20
        private const val MINIMUM_HEIGHT = 18
        private const val COMPOSER_HEIGHT = 2
        private const val COMMAND_INPUT_POLL_MILLIS = 50L
        private const val HISTORY_SCROLL_STEP = 5
        private const val HEADER_GAP = 2
        private const val CHAT_PADDING = "   "
        private const val DEFAULT_STATUS_HINT = "Type / for commands"
        private const val ENTER_ALTERNATE_SCREEN = "\u001B[?1049h"
        private const val LEAVE_ALTERNATE_SCREEN = "\u001B[?1049l"
        private const val ENABLE_ALTERNATE_SCROLL = "\u001B[?1007h"
        private const val DISABLE_ALTERNATE_SCROLL = "\u001B[?1007l"
        private const val SHOW_CURSOR = "\u001B[?25h"
        private const val BLINKING_BAR_CURSOR = "\u001B[5 q"
        private const val DEFAULT_CURSOR = "\u001B[0 q"
        private const val SAVE_CURSOR = "\u001B[s"
        private const val RESTORE_CURSOR = "\u001B[u"
        private const val BEGIN_SYNCHRONIZED_UPDATE = "\u001B[?2026h"
        private const val END_SYNCHRONIZED_UPDATE = "\u001B[?2026l"
        private val HEADER_ART = """
             __  __     ______     ______     ______
            /\ \/\ \   /\  == \   /\  __ \   /\  ___\
            \ \ \_\ \  \ \  __<   \ \  __ \  \ \ \__ \
             \ \_____\  \ \_\ \_\  \ \_\ \_\  \ \_____\
              \/_____/   \/_/ /_/   \/_/\/_/   \/_____/
        """.trimIndent()
    }
}
