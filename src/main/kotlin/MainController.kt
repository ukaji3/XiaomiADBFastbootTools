import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.*
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URL
import java.util.*
import java.util.zip.ZipFile

class MainController : Initializable, CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    @FXML private lateinit var deviceMenu: Menu
    @FXML private lateinit var appManagerMenu: Menu
    @FXML private lateinit var secondSpaceButton: CheckMenuItem
    @FXML private lateinit var recoveryMenuItem: MenuItem
    @FXML private lateinit var reloadMenuItem: MenuItem
    @FXML private lateinit var infoTextArea: TextArea
    @FXML private lateinit var outputTextArea: TextArea
    @FXML private lateinit var progressBar: ProgressBar
    @FXML private lateinit var progressIndicator: ProgressIndicator
    @FXML private lateinit var tabPane: TabPane

    @FXML private lateinit var adbTabContent: AnchorPane
    @FXML private lateinit var adbTabContentController: AdbTabController
    @FXML private lateinit var fastbootTabContent: AnchorPane
    @FXML private lateinit var fastbootTabContentController: FastbootTabController

    private var adbVersion = ""

    private val displayOutput: suspend (String) -> Unit = { line ->
        withContext(Dispatchers.Main) { outputTextArea.appendText(line) }
    }

    private suspend fun setPanels(mode: Mode?) {
        withContext(Dispatchers.Main) {
            val adb = adbTabContentController
            val fb = fastbootTabContentController
            when (mode) {
                Mode.ADB -> {
                    adb.appManagerPane.isDisable = false
                    appManagerMenu.isDisable = false
                    adb.reinstallerTab.isDisable = !Device.reinstaller
                    adb.disablerTab.isDisable = !Device.disabler
                    adb.enablerTab.isDisable = !Device.disabler
                    adb.camera2Pane.isDisable = true
                    adb.fileExplorerPane.isDisable = false
                    adb.resolutionPane.isDisable = false
                    adb.dpiPane.isDisable = false
                    fb.flasherPane.isDisable = true; fb.wiperPane.isDisable = true; fb.oemPane.isDisable = true
                    deviceMenu.isDisable = false; recoveryMenuItem.isDisable = false; reloadMenuItem.isDisable = false
                }
                Mode.RECOVERY -> {
                    adb.appManagerPane.isDisable = true; appManagerMenu.isDisable = true
                    adb.reinstallerTab.isDisable = true; adb.disablerTab.isDisable = true; adb.enablerTab.isDisable = true
                    adb.camera2Pane.isDisable = false
                    adb.fileExplorerPane.isDisable = true; adb.resolutionPane.isDisable = true; adb.dpiPane.isDisable = true
                    fb.flasherPane.isDisable = true; fb.wiperPane.isDisable = true; fb.oemPane.isDisable = true
                    deviceMenu.isDisable = false; recoveryMenuItem.isDisable = false; reloadMenuItem.isDisable = false
                }
                Mode.FASTBOOT -> {
                    adb.appManagerPane.isDisable = true; appManagerMenu.isDisable = true
                    adb.camera2Pane.isDisable = true; adb.fileExplorerPane.isDisable = true
                    adb.resolutionPane.isDisable = true; adb.dpiPane.isDisable = true
                    fb.flasherPane.isDisable = false; fb.wiperPane.isDisable = false; fb.oemPane.isDisable = false
                    deviceMenu.isDisable = false; recoveryMenuItem.isDisable = true; reloadMenuItem.isDisable = false
                }
                else -> {
                    adb.appManagerPane.isDisable = true; appManagerMenu.isDisable = true
                    adb.camera2Pane.isDisable = true; adb.fileExplorerPane.isDisable = true
                    adb.resolutionPane.isDisable = true; adb.dpiPane.isDisable = true
                    fb.flasherPane.isDisable = true; fb.wiperPane.isDisable = true; fb.oemPane.isDisable = true
                    deviceMenu.isDisable = true; recoveryMenuItem.isDisable = true; reloadMenuItem.isDisable = true
                }
            }
        }
    }

    private suspend fun setUI() {
        setPanels(Device.mode)
        withContext(Dispatchers.Main) {
            when (Device.mode) {
                Mode.ADB -> {
                    tabPane.selectionModel.select(0)
                    infoTextArea.text = "Serial number:\t\t${Device.serial}\nCodename:\t\t${Device.codename}\n"
                    if (Device.bootloader) infoTextArea.appendText("Bootloader:\t\tunlocked\n")
                    if (Device.camera2) infoTextArea.appendText("Camera2:\t\t\tenabled")
                    fastbootTabContentController.codenameTextField.text = Device.codename
                    adbTabContentController.setDpiFields(Device.dpi, Device.width, Device.height)
                }
                Mode.RECOVERY -> {
                    tabPane.selectionModel.select(0)
                    infoTextArea.text = "Serial number:\t\t${Device.serial}\nCodename:\t\t${Device.codename}\n"
                    if (Device.bootloader) infoTextArea.appendText("Bootloader:\t\tunlocked\n")
                    if (Device.camera2) infoTextArea.appendText("Camera2:\t\t\tenabled")
                    fastbootTabContentController.codenameTextField.text = Device.codename
                }
                Mode.FASTBOOT -> {
                    tabPane.selectionModel.select(1)
                    infoTextArea.text = "Serial number:\t\t${Device.serial}\nCodename:\t\t${Device.codename}\nBootloader:\t\t"
                    if (Device.bootloader) infoTextArea.appendText("unlocked") else infoTextArea.appendText("locked")
                    if (Device.anti != -1) infoTextArea.appendText("\nAnti version:\t\t${Device.anti}")
                    fastbootTabContentController.codenameTextField.text = Device.codename
                }
                else -> { infoTextArea.clear() }
            }
        }
    }

    private suspend fun checkDevice() {
        Device.mode = null
        setUI()
        withContext(Dispatchers.Main) { outputTextArea.text = "Looking for devices...\n"; progressIndicator.isVisible = true }
        do {
            Device.readADB()
            Device.readFastboot()
            withContext(Dispatchers.Main) {
                when (Device.mode) {
                    Mode.ADB -> {
                        progressIndicator.isVisible = false
                        outputTextArea.text = "Device connected in ADB mode!\n"
                        if (!Device.reinstaller || !Device.disabler)
                            outputTextArea.appendText("Note:\nThis device isn't fully supported by the App Manager.\nAs a result, some modules have been disabled.\n\n")
                        AppManager.readPotentialApps()
                        adbTabContentController.refreshAppTables()
                    }
                    Mode.RECOVERY -> { progressIndicator.isVisible = false; outputTextArea.text = "Device connected in Recovery mode!\n\n" }
                    Mode.FASTBOOT -> { progressIndicator.isVisible = false; outputTextArea.text = "Device connected in Fastboot mode!\n\n" }
                    Mode.AUTH -> { if ("Unauthorised" !in outputTextArea.text) outputTextArea.text = "Unauthorised device found!\nPlease allow USB debugging on the device!\n\n"; Unit }
                    Mode.ADB_ERROR -> { if ("loaded" !in outputTextArea.text) outputTextArea.text = "ERROR: The device cannot be loaded!\nTry setting the USB configuration to data transfer or launching the application with root/admin privileges!\n\n"; Unit }
                    Mode.FASTBOOT_ERROR -> { if ("loaded" !in outputTextArea.text) outputTextArea.text = "ERROR: The device cannot be loaded!\nTry launching the application with root/admin privileges!\n\n"; Unit }
                    else -> {}
                }
            }
            setUI()
            delay(1000)
        } while (!(Device.mode == Mode.ADB || Device.mode == Mode.FASTBOOT || Device.mode == Mode.RECOVERY))
    }

    override fun initialize(url: URL, rb: ResourceBundle?) {
        Device.cmd = Command
        AppManager.cmd = Command

        adbTabContentController.apply {
            outputTextArea = this@MainController.outputTextArea
            progressBar = this@MainController.progressBar
            progressIndicator = this@MainController.progressIndicator
            onDeviceLost = { checkDevice() }
        }
        fastbootTabContentController.apply {
            outputTextArea = this@MainController.outputTextArea
            progressBar = this@MainController.progressBar
            progressIndicator = this@MainController.progressIndicator
            onDeviceLost = { checkDevice() }
            onSetPanels = { setPanels(it) }
        }

        launch(Dispatchers.IO) {
            try {
                val link = URL("https://api.github.com/repos/Szaki/XiaomiADBFastbootTools/releases/latest").readText()
                    .substringAfter("\"html_url\":\"").substringBefore('"')
                val latest = link.substringAfterLast('/')
                if (latest > XiaomiADBFastbootTools.version)
                    withContext(Dispatchers.Main) {
                        val vb = VBox(); val download = Hyperlink("Download")
                        Alert(AlertType.INFORMATION).apply {
                            initStyle(StageStyle.UTILITY); title = "New version available!"; graphic = ImageView("mitu.png")
                            headerText = "Version $latest is available!"
                            vb.alignment = Pos.CENTER
                            download.onAction = EventHandler {
                                if (XiaomiADBFastbootTools.linux) Runtime.getRuntime().exec(arrayOf("xdg-open", link))
                                else Desktop.getDesktop().browse(URI(link))
                            }
                            download.font = Font(15.0); vb.children.add(download); dialogPane.content = vb; showAndWait()
                        }
                    }
            } catch (ex: Exception) { }
            if (!Command.check()) {
                withContext(Dispatchers.Main) {
                    val hb = HBox(15.0); val label = Label(); val indicator = ProgressIndicator()
                    Alert(AlertType.WARNING).apply {
                        initStyle(StageStyle.UTILITY); title = "Downloading SDK Platform Tools..."
                        headerText = "ERROR: Cannot find ADB/Fastboot!\nDownloading the latest version..."
                        hb.alignment = Pos.CENTER; label.font = Font(15.0); indicator.setPrefSize(35.0, 35.0)
                        hb.children.addAll(indicator, label); dialogPane.content = hb; isResizable = false; show()
                        withContext(Dispatchers.IO) {
                            val file = File(XiaomiADBFastbootTools.dir, "platform-tools.zip")
                            when {
                                XiaomiADBFastbootTools.win -> Downloader("https://dl.google.com/android/repository/platform-tools-latest-windows.zip", file, label).start(this)
                                XiaomiADBFastbootTools.linux -> Downloader("https://dl.google.com/android/repository/platform-tools-latest-linux.zip", file, label).start(this)
                                else -> Downloader("https://dl.google.com/android/repository/platform-tools-latest-darwin.zip", file, label).start(this)
                            }
                            withContext(Dispatchers.Main) { label.text = "Unzipping..." }
                            File(XiaomiADBFastbootTools.dir, "platform-tools").mkdirs()
                            ZipFile(file).use { zip ->
                                zip.stream().forEach { entry ->
                                    if (entry.isDirectory) File(XiaomiADBFastbootTools.dir, entry.name).mkdirs()
                                    else zip.getInputStream(entry).use { input ->
                                        File(XiaomiADBFastbootTools.dir, entry.name).apply { outputStream().use { output -> input.copyTo(output) }; setExecutable(true, false) }
                                    }
                                }
                            }
                            file.delete()
                        }
                        hb.children.remove(indicator); label.text = "Done!"
                    }
                }
                if (!Command.check(true))
                    withContext(Dispatchers.Main) {
                        Alert(AlertType.ERROR).apply { title = "Fatal Error"; headerText = "ERROR: Couldn't run ADB/Fastboot!"; showAndWait() }
                        Platform.exit()
                    }
            }
            adbVersion = try { Command.exec(mutableListOf("adb", "--version")).lines()[1] } catch (e: Exception) { "Unknown" }
            checkDevice()
        }
    }

    @FXML private fun secondSpaceButtonPressed(event: ActionEvent) {
        launch {
            if (Device.checkADB()) {
                AppManager.user = if (secondSpaceButton.isSelected) "10" else "0"
                adbTabContentController.refreshAppTables()
            }
        }
    }

    @FXML private fun addAppsButtonPressed(event: ActionEvent) {
        launch(Dispatchers.IO) {
            if (Device.checkADB()) {
                val scene = javafx.scene.Scene(javafx.fxml.FXMLLoader(javaClass.classLoader.getResource("AppAdder.fxml")).load())
                withContext(Dispatchers.Main) { Stage().apply { initModality(Modality.APPLICATION_MODAL); this.scene = scene; isResizable = false; showAndWait() } }
            } else checkDevice()
        }
    }

    @FXML private fun readPropertiesMenuItemPressed(event: ActionEvent) {
        launch {
            withContext(Dispatchers.Main) { outputTextArea.text = "" }
            when (Device.mode) {
                Mode.ADB, Mode.RECOVERY -> { if (Device.checkADB()) Command.execDisplayed(mutableListOf("adb", "shell", "getprop"), onOutput = displayOutput) else checkDevice() }
                Mode.FASTBOOT -> { if (Device.checkFastboot()) Command.execDisplayed(mutableListOf("fastboot", "getvar", "all"), onOutput = displayOutput) else checkDevice() }
                else -> {}
            }
        }
    }

    @FXML private fun savePropertiesMenuItemPressed(event: ActionEvent) {
        launch {
            when (Device.mode) {
                Mode.ADB, Mode.RECOVERY -> {
                    if (Device.checkADB()) {
                        val props = Command.exec(mutableListOf("adb", "shell", "getprop"))
                        withContext(Dispatchers.Main) {
                            FileChooser().apply {
                                extensionFilters.add(FileChooser.ExtensionFilter("Text File", "*")); title = "Save properties"
                                showSaveDialog((event.target as MenuItem).parentPopup.ownerWindow)?.let { withContext(Dispatchers.IO) { try { it.writeText(props) } catch (ex: Exception) { ex.printStackTrace(); ex.alert() } } }
                            }
                        }
                    } else checkDevice()
                }
                Mode.FASTBOOT -> {
                    if (Device.checkFastboot()) {
                        val props = Command.exec(mutableListOf("fastboot", "getvar", "all"))
                        withContext(Dispatchers.Main) {
                            FileChooser().apply {
                                extensionFilters.add(FileChooser.ExtensionFilter("Text File", "*")); title = "Save properties"
                                showSaveDialog((event.target as MenuItem).parentPopup.ownerWindow)?.let { withContext(Dispatchers.IO) { try { it.writeText(props) } catch (ex: Exception) { ex.printStackTrace(); ex.alert() } } }
                            }
                        }
                    } else checkDevice()
                }
                else -> checkDevice()
            }
        }
    }

    @FXML private fun systemMenuItemPressed(event: ActionEvent) {
        launch {
            when (Device.mode) {
                Mode.ADB, Mode.RECOVERY -> { if (Device.checkADB()) Command.exec(mutableListOf("adb", "reboot")); checkDevice() }
                Mode.FASTBOOT -> { if (Device.checkFastboot()) Command.exec(mutableListOf("fastboot", "reboot")); checkDevice() }
                else -> {}
            }
        }
    }

    @FXML private fun recoveryMenuItemPressed(event: ActionEvent) {
        launch { if (Device.mode == Mode.ADB || Device.mode == Mode.RECOVERY) { if (Device.checkADB()) Command.exec(mutableListOf("adb", "reboot", "recovery")); checkDevice() } }
    }

    @FXML private fun fastbootMenuItemPressed(event: ActionEvent) {
        launch {
            when (Device.mode) {
                Mode.ADB, Mode.RECOVERY -> { if (Device.checkADB()) Command.exec(mutableListOf("adb", "reboot", "bootloader")); checkDevice() }
                Mode.FASTBOOT -> { if (Device.checkFastboot()) Command.exec(mutableListOf("fastboot", "reboot", "bootloader")); checkDevice() }
                else -> {}
            }
        }
    }

    @FXML private fun edlMenuItemPressed(event: ActionEvent) {
        launch {
            when (Device.mode) {
                Mode.ADB, Mode.RECOVERY -> { if (Device.checkADB()) Command.exec(mutableListOf("adb", "reboot", "edl")); checkDevice() }
                Mode.FASTBOOT -> { if (Device.checkFastboot()) Command.exec(mutableListOf("fastboot", "oem", "edl")); checkDevice() }
                else -> {}
            }
        }
    }

    @FXML private fun reloadMenuItemPressed(event: ActionEvent) = launch { checkDevice() }

    @FXML private fun aboutMenuItemPressed(event: ActionEvent) {
        Alert(AlertType.INFORMATION).apply {
            initStyle(StageStyle.UTILITY); title = "About"; graphic = ImageView("icon.png")
            headerText = "Xiaomi ADB/Fastboot Tools\nVersion ${XiaomiADBFastbootTools.version}\nCreated by Szaki\n\n" +
                    "SDK Platform Tools\n$adbVersion"
            val vb = VBox(); vb.alignment = Pos.CENTER
            val discord = Hyperlink("Xiaomi Community on Discord"); val twitter = Hyperlink("Szaki on Twitter"); val github = Hyperlink("Repository on GitHub")
            listOf(discord to "http://discord.szaki.io/", twitter to "http://twitter.szaki.io/", github to "https://github.com/Szaki/XiaomiADBFastbootTools").forEach { (link, url) ->
                link.onAction = EventHandler {
                    if (XiaomiADBFastbootTools.linux) Runtime.getRuntime().exec(arrayOf("xdg-open", url))
                    else Desktop.getDesktop().browse(URI(url))
                }
                link.font = Font(15.0)
            }
            vb.children.addAll(discord, twitter, github); dialogPane.content = vb; isResizable = false; showAndWait()
        }
    }
}
