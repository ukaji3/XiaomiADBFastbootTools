import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

object ROMFlasher {

    var directory = XiaomiADBFastbootTools.dir

    private fun setupScript(arg: String): File {
        val script = if (XiaomiADBFastbootTools.win)
            File(directory, "script.bat").apply {
                try {
                    writeText(File(directory, "$arg.bat").readText().replace("fastboot", "${Command.prefix}fastboot"))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                setExecutable(true, false)
            } else
            File(directory, "script.sh").apply {
                try {
                    writeText(File(directory, "$arg.sh").readText().replace("fastboot", "${Command.prefix}fastboot"))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                setExecutable(true, false)
            }
        return script
    }

    suspend fun flash(arg: String?, onOutput: suspend (String) -> Unit = {}, onProgress: suspend (Double) -> Unit = {}) {
        if (arg == null) return
        withContext(Dispatchers.IO) {
            val script = setupScript(arg)
            val n = script.readText().split("fastboot").size - 1
            Scanner(runScript(script, redirectErrorStream = true).inputStream, "UTF-8").useDelimiter("")
                .use { scanner ->
                    val sb = StringBuilder()
                    while (scanner.hasNext()) {
                        val next = scanner.next()
                        sb.append(next)
                        val full = sb.toString()
                        if ("pause" in full) break
                        onOutput(next)
                        onProgress(1.0 * (full.toLowerCase().split("finished.").size - 1) / n)
                    }
                }
            script.delete()
        }
        onOutput("\nDone!")
    }
}
