package university.cli

import org.koin.dsl.koinApplication
import university.cli.di.appModule
import university.cli.launcher.AppLauncher
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val application = koinApplication {
        modules(appModule)
    }

    val exitCode = try {
        application.koin.get<AppLauncher>().launch(args)
    } finally {
        application.close()
    }

    if (exitCode != 0) exitProcess(exitCode)
}
