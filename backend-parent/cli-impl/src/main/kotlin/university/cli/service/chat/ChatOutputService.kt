package university.cli.service.chat

import university.cli.command.CommandMessageType
import java.util.concurrent.CopyOnWriteArrayList

class ChatOutputService {
    private val listeners = CopyOnWriteArrayList<(ChatOutputMessage) -> Unit>()

    fun write(text: String, type: CommandMessageType = CommandMessageType.INFO) {
        if (text.isBlank()) return
        val message = ChatOutputMessage(text, type)
        listeners.forEach { listener -> listener(message) }
    }

    internal fun observe(listener: (ChatOutputMessage) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}

data class ChatOutputMessage(
    val text: String,
    val type: CommandMessageType,
)
