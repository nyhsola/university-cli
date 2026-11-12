package university.cli.launcher

import university.cli.service.chat.ChatService
import university.cli.service.cli.CliService

class AppLauncher(
    private val chatService: ChatService = ChatService(),
    private val cliService: CliService = CliService(),
) {
    private companion object {
        const val INTERACTIVE_FLAG = "--interactive"
    }

    fun launch(args: Array<String>): Int = if (args.contentEquals(arrayOf(INTERACTIVE_FLAG))) {
        chatService.start()
        0
    } else {
        cliService.execute(args)
    }
}
