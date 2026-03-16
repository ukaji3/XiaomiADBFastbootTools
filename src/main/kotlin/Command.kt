import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

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

    override suspend fun exec(vararg args: MutableList<String>, redirectErrorStream: Boolean): String {
        val sb = StringBuilder()
        args.forEach {
            it[0] = prefix + it[0]
            withContext(Dispatchers.IO) {
                Scanner(startProcess(it, redirectErrorStream).inputStream, "UTF-8").useDelimiter("").use { scanner ->
                    while (scanner.hasNextLine())
                        sb.append(scanner.nextLine() + '\n')
                }
            }
        }
        return sb.toString()
    }

    suspend fun execDisplayed(
        vararg args: MutableList<String>,
        redirectErrorStream: Boolean = true,
        onOutput: suspend (String) -> Unit = {}
    ): String {
        val sb = StringBuilder()
        args.forEach {
            it[0] = prefix + it[0]
            withContext(Dispatchers.IO) {
                Scanner(startProcess(it, redirectErrorStream).inputStream, "UTF-8").useDelimiter("").use { scanner ->
                    while (scanner.hasNextLine()) {
                        val next = scanner.nextLine() + '\n'
                        sb.append(next)
                        onOutput(next)
                    }
                }
            }
        }
        return sb.toString()
    }

    suspend fun execWithImage(
        vararg args: MutableList<String>,
        image: File,
        onOutput: suspend (String) -> Unit = {}
    ) {
        args.forEach {
            it[0] = prefix + it[0]
            withContext(Dispatchers.IO) {
                Scanner(startProcess(it + image.absolutePath, true).inputStream, "UTF-8").useDelimiter("")
                    .use { scanner ->
                        while (scanner.hasNextLine())
                            onOutput(scanner.nextLine() + '\n')
                    }
            }
        }
    }
}
