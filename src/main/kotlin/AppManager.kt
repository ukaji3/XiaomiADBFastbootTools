import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppLists(
    val uninstall: List<App>,
    val reinstall: List<App>,
    val disable: List<App>,
    val enable: List<App>
)

data class OperationResult(val appName: String, val packageName: String, val output: String)

object AppManager {

    lateinit var cmd: CommandRunner
    var user = "0"
    var showAllApps = false
    val customApps = File(XiaomiADBFastbootTools.dir, "apps.yml")
    private val potentialApps = mutableMapOf<String, String>()

    init {
        if (!customApps.exists())
            customApps.createNewFile()
    }

    suspend fun readPotentialApps() {
        potentialApps.clear()
        potentialApps["android.autoinstalls.config.Xiaomi.${Device.codename}"] = "PAI"
        this::class.java.classLoader.getResource("apps.yml")?.readText()?.trim()?.lines()?.forEach { line ->
            val app = line.split(':')
            potentialApps[app[0].trim()] = app[1].trim()
        }
        customApps.forEachLine { line ->
            val app = line.split(':')
            if (app.size == 1) {
                potentialApps[app[0].trim()] = app[0].trim()
            } else {
                potentialApps[app[0].trim()] = app[1].trim()
            }
        }
    }

    suspend fun getAppLists(): AppLists {
        val uninstallApps = mutableMapOf<String, MutableList<String>>()
        val reinstallApps = mutableMapOf<String, MutableList<String>>()
        val disableApps = mutableMapOf<String, MutableList<String>>()
        val enableApps = mutableMapOf<String, MutableList<String>>()
        val deviceApps = mutableMapOf<String, String>()
        cmd.exec(listOf("adb", "shell", "pm", "list", "packages", "-u", "--user", user)).trim().lines()
            .forEach { deviceApps[it.substringAfter(':').trim()] = "uninstalled" }
        cmd.exec(listOf("adb", "shell", "pm", "list", "packages", "-d", "--user", user)).trim().lines()
            .forEach { deviceApps[it.substringAfter(':').trim()] = "disabled" }
        cmd.exec(listOf("adb", "shell", "pm", "list", "packages", "-e", "--user", user)).trim().lines()
            .forEach { deviceApps[it.substringAfter(':').trim()] = "enabled" }
        val apps = if (showAllApps)
            deviceApps.keys.associateWith { potentialApps[it] ?: it }
        else potentialApps
        apps.forEach { (pkg, name) ->
            when (deviceApps[pkg]) {
                "disabled" -> {
                    uninstallApps.add(name, pkg)
                    enableApps.add(name, pkg)
                }
                "enabled" -> {
                    uninstallApps.add(name, pkg)
                    disableApps.add(name, pkg)
                }
                "uninstalled" -> reinstallApps.add(name, pkg)
            }
        }
        return AppLists(
            uninstall = uninstallApps.toSortedMap().map { App(it.key, it.value) },
            reinstall = reinstallApps.toSortedMap().map { App(it.key, it.value) },
            disable = disableApps.toSortedMap().map { App(it.key, it.value) },
            enable = enableApps.toSortedMap().map { App(it.key, it.value) }
        )
    }

    private suspend fun executeForEach(
        selected: List<App>,
        buildCommand: (String) -> List<String>,
        checkSuccess: (String) -> Boolean,
        onResult: suspend (OperationResult) -> Unit = {}
    ): List<OperationResult> {
        val results = mutableListOf<OperationResult>()
        selected.forEach { app ->
            app.packagenameProperty().get().trim().lines().forEach { pkg ->
                val output = cmd.exec(buildCommand(pkg.trim()))
                val success = if (checkSuccess(output)) "Success\n" else "Failure\n"
                val result = OperationResult(app.appnameProperty().get(), pkg.trim(), success)
                results.add(result)
                onResult(result)
            }
        }
        return results
    }

    suspend fun uninstall(selected: List<App>, onResult: suspend (OperationResult) -> Unit = {}) =
        executeForEach(selected,
            { pkg -> listOf("adb", "shell", "pm", "uninstall", "--user", user, pkg) },
            { "Success" in it },
            onResult
        )

    suspend fun reinstall(selected: List<App>, onResult: suspend (OperationResult) -> Unit = {}) =
        executeForEach(selected,
            { pkg -> listOf("adb", "shell", "cmd", "package", "install-existing", "--user", user, pkg) },
            { "installed for user" in it },
            onResult
        )

    suspend fun disable(selected: List<App>, onResult: suspend (OperationResult) -> Unit = {}) =
        executeForEach(selected,
            { pkg -> listOf("adb", "shell", "pm", "disable-user", "--user", user, pkg) },
            { "disabled-user" in it },
            onResult
        )

    suspend fun enable(selected: List<App>, onResult: suspend (OperationResult) -> Unit = {}) =
        executeForEach(selected,
            { pkg -> listOf("adb", "shell", "pm", "enable", "--user", user, pkg) },
            { "enabled" in it },
            onResult
        )
}
