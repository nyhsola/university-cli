package university.cli.service.operation

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

class OperationCancellationService {
    private val activeThread = AtomicReference<Thread?>()

    fun <T> execute(action: () -> T): T {
        val thread = Thread.currentThread()
        check(activeThread.compareAndSet(null, thread)) { "Another operation is already running" }
        return try {
            action()
        } finally {
            activeThread.compareAndSet(thread, null)
            Thread.interrupted()
        }
    }

    fun cancel(): Boolean {
        val thread = activeThread.get() ?: return false
        thread.interrupt()
        return true
    }

    fun ensureActive() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Operation was cancelled")
        }
    }
}
