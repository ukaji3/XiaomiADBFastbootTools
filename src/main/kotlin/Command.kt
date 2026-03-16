import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Command : CommandRunner {

    var prefix = ""

    private suspend fun setup(pref: String) {
        prefix = pref
        withContext(Dispatchers.IO) {
            startProcess("${prefix}adb", "--version")
            startProcess("${prefix}fastboot", "--version")
            startProcess("${prefix}adb", "start-server")
        }
    }

    override suspend fun check(printErr: Boolean): Boolean {
        try {
            setup("")
        } catch (e: Exception) {
            try {
                if (XiaomiADBFastbootTools.win)
                    setup("${XiaomiADBFastbootTools.dir.absolutePath}\\platform-tools\\")
                else setup("${XiaomiADBFastbootTools.dir.absolutePath}/platform-tools/")
            } catch (ex: Exception) {
                if (printErr)
                    ex.printStackTrace()
                return false
            }
        }
        return true
    }

    override suspend fun exec(vararg args: List<String>, redirectErrorStream: Boolean): String {
        val sb = StringBuilder()
        args.forEach {
            val cmd = it.toMutableList(); cmd[0] = prefix + cmd[0]
            withContext(Dispatchers.IO) {
                startProcess(cmd, redirectErrorStream).inputReader().use { reader ->
                    reader.forEachLine { line -> sb.append(line + '\n') }
                }
            }
        }
        return sb.toString()
    }

    suspend fun execDisplayed(
        vararg args: List<String>,
        redirectErrorStream: Boolean = true,
        onOutput: suspend (String) -> Unit = {}
    ): String {
        val sb = StringBuilder()
        args.forEach {
            val cmd = it.toMutableList(); cmd[0] = prefix + cmd[0]
            withContext(Dispatchers.IO) {
                startProcess(cmd, redirectErrorStream).inputReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val next = line + '\n'
                        sb.append(next)
                        onOutput(next)
                    }
                }
            }
        }
        return sb.toString()
    }

    suspend fun execWithImage(
        vararg args: List<String>,
        image: File,
        onOutput: suspend (String) -> Unit = {}
    ) {
        args.forEach {
            val cmd = it.toMutableList(); cmd[0] = prefix + cmd[0]
            withContext(Dispatchers.IO) {
                startProcess(cmd + image.absolutePath, true).inputReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null)
                        onOutput(line + '\n')
                }
            }
        }
    }
}
