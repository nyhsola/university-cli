package university.cli.service.chat

import java.util.concurrent.CopyOnWriteArrayList

class ChatStatusService {
    private val listeners = CopyOnWriteArrayList<(String?) -> Unit>()

    @Volatile
    var status: String? = null
        private set

    fun set(status: String) {
        update(status.takeIf(String::isNotBlank))
    }

    fun clear() {
        update(null)
    }

    internal fun observe(listener: (String?) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    private fun update(status: String?) {
        this.status = status
        listeners.forEach { it(status) }
    }

}
