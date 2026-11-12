package university.cli.service.chat

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.RawModeScope
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class ConsoleInputReader(
    private val input: RawModeScope,
) : AutoCloseable {
    private val events = LinkedBlockingQueue<ReadResult>()

    @Volatile
    private var running = true

    private val readerThread = Thread(::readEvents, "university-console-input")
        .apply {
            isDaemon = true
            start()
        }

    fun read(): InputEvent = events.take().getOrThrow()

    fun poll(timeoutMillis: Long): InputEvent? =
        events.poll(timeoutMillis, TimeUnit.MILLISECONDS)?.getOrThrow()

    override fun close() {
        running = false
        readerThread.interrupt()
    }

    private fun readEvents() {
        while (running) {
            try {
                events.put(ReadResult.Event(input.readEvent()))
            } catch (error: Exception) {
                if (running) events.offer(ReadResult.Failure(error))
                return
            }
        }
    }

    private sealed interface ReadResult {
        fun getOrThrow(): InputEvent

        data class Event(val event: InputEvent) : ReadResult {
            override fun getOrThrow() = event
        }

        data class Failure(val error: Exception) : ReadResult {
            override fun getOrThrow(): InputEvent = throw error
        }
    }
}
