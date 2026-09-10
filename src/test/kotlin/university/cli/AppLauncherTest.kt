package university.cli

import university.cli.launcher.AppLauncher
import kotlin.test.Test

class AppLauncherTest {
    @Test
    fun `launch completes without errors`() {
        val launcher = AppLauncher()
        launcher.launch(emptyArray())
    }
}
