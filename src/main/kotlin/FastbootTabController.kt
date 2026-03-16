import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.control.*
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URL
import java.util.*

class FastbootTabController : Initializable, CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    @FXML lateinit var flasherPane: TitledPane
    @FXML lateinit var wiperPane: TitledPane
    @FXML lateinit var oemPane: TitledPane
    @FXML lateinit var downloaderPane: TitledPane
    @FXML private lateinit var partitionComboBox: ComboBox<String>
    @FXML private lateinit var scriptComboBox: ComboBox<String>
    @FXML private lateinit var autobootCheckBox: CheckBox
    @FXML private lateinit var imageLabel: Label
    @FXML private lateinit var romLabel: Label
    @FXML lateinit var codenameTextField: TextField
    @FXML private lateinit var branchComboBox: ComboBox<String>
    @FXML private lateinit var versionLabel: Label
    @FXML private lateinit var downloadProgress: Label

    lateinit var outputTextArea: TextArea
    lateinit var progressBar: ProgressBar
    lateinit var progressIndicator: ProgressIndicator
    lateinit var onDeviceLost: suspend () -> Unit
    lateinit var onSetPanels: suspend (Mode?) -> Unit

    private var image: File? = null
    private var romDirectory: File? = null

    private val displayOutput: suspend (String) -> Unit = { line ->
        withContext(Dispatchers.Main) { outputTextArea.appendText(line) }
    }

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        partitionComboBox.items.addAll("boot", "cust", "modem", "persist", "recovery", "system")
        scriptComboBox.items.addAll("Clean install", "Clean install and lock", "Update")
        branchComboBox.items.addAll("China Stable", "EEA Stable", "Global Stable", "India Stable", "Indonesia Stable", "Russia Stable")
    }

    private suspend fun execFastbootDisplayed(vararg args: MutableList<String>) {
        withContext(Dispatchers.Main) { outputTextArea.text = "" }
        Command.execDisplayed(*args, onOutput = displayOutput)
    }

    @FXML private fun antirbButtonPressed(event: ActionEvent) {
        launch {
            if (Device.checkFastboot()) {
                File("dummy.img").apply {
                    writeBytes(ByteArray(8192))
                    withContext(Dispatchers.Main) {
                        if ("FAILED" in Command.exec(mutableListOf("fastboot", "oem", "ignore", "anti"))) {
                            if ("FAILED" in Command.exec(mutableListOf("fastboot", "flash", "antirbpass", "dummy.img")))
                                outputTextArea.text = "Couldn't disable anti-rollback safeguard!"
                            else outputTextArea.text = "Anti-rollback safeguard disabled!"
                        } else outputTextArea.text = "Anti-rollback safeguard disabled!"
                    }
                    delete()
                }
            } else onDeviceLost()
        }
    }

    @FXML private fun browseimageButtonPressed(event: ActionEvent) {
        FileChooser().apply {
            extensionFilters.add(FileChooser.ExtensionFilter("Image File", "*.*"))
            title = "Select an image"
            image = showOpenDialog((event.source as Node).scene.window)
            imageLabel.text = image?.name
        }
    }

    @FXML private fun flashimageButtonPressed(event: ActionEvent) {
        image?.let {
            partitionComboBox.value?.let { pcb ->
                if (it.absolutePath.isNotBlank() && pcb.isNotBlank())
                    launch {
                        if (Device.checkFastboot()) {
                            if (confirm()) {
                                withContext(Dispatchers.Main) { outputTextArea.text = ""; progressIndicator.isVisible = true }
                                if (autobootCheckBox.isSelected && pcb.trim() == "recovery")
                                    Command.execWithImage(mutableListOf("fastboot", "flash", pcb.trim()), mutableListOf("fastboot", "boot"), image = it, onOutput = displayOutput)
                                else Command.execWithImage(mutableListOf("fastboot", "flash", pcb.trim()), image = it, onOutput = displayOutput)
                                withContext(Dispatchers.Main) { progressIndicator.isVisible = false }
                            }
                        } else onDeviceLost()
                    }
            }
        }
    }

    @FXML private fun browseromButtonPressed(event: ActionEvent) {
        DirectoryChooser().apply {
            title = "Select the root directory of a Fastboot ROM"
            romLabel.text = "-"
            romDirectory = showDialog((event.source as Node).scene.window)?.let { dir ->
                when {
                    ' ' in dir.absolutePath -> { outputTextArea.text = "ERROR: Space found in the pathname!"; null }
                    "images" in dir.list()!! -> {
                        romLabel.text = dir.name; outputTextArea.text = "Fastboot ROM found!"
                        dir.listFiles()?.forEach { if (!it.isDirectory) it.setExecutable(true, false) }; dir
                    }
                    else -> { outputTextArea.text = "ERROR: Fastboot ROM not found!"; null }
                }
            }
        }
    }

    @FXML private fun flashromButtonPressed(event: ActionEvent) {
        romDirectory?.let { dir ->
            ROMFlasher.directory = dir
            scriptComboBox.value?.let { scb ->
                launch {
                    if (Device.checkFastboot()) {
                        if (confirm()) {
                            onSetPanels(null)
                            withContext(Dispatchers.Main) { progressBar.progress = 0.0; progressIndicator.isVisible = true; outputTextArea.text = "" }
                            val scriptName = when (scb) {
                                "Clean install" -> "flash_all"; "Clean install and lock" -> "flash_all_lock"
                                "Update" -> dir.list()?.find { "flash_all_except" in it }?.substringBefore('.'); else -> null
                            }
                            ROMFlasher.flash(scriptName, onOutput = displayOutput, onProgress = { withContext(Dispatchers.Main) { progressBar.progress = it } })
                            withContext(Dispatchers.Main) { progressBar.progress = 0.0; progressIndicator.isVisible = false }
                        }
                    } else onDeviceLost()
                }
            }
        }
    }

    @FXML private fun bootButtonPressed(event: ActionEvent) {
        image?.let {
            if (it.absolutePath.isNotBlank())
                launch {
                    if (Device.checkFastboot()) {
                        withContext(Dispatchers.Main) { outputTextArea.text = ""; progressIndicator.isVisible = true }
                        Command.execWithImage(mutableListOf("fastboot", "boot"), image = it, onOutput = displayOutput)
                        withContext(Dispatchers.Main) { progressIndicator.isVisible = false }
                    } else onDeviceLost()
                }
        }
    }

    @FXML private fun cacheButtonPressed(event: ActionEvent) {
        launch { if (Device.checkFastboot()) execFastbootDisplayed(mutableListOf("fastboot", "erase", "cache")) else onDeviceLost() }
    }
    @FXML private fun dataButtonPressed(event: ActionEvent) {
        launch { if (Device.checkFastboot()) { if (confirm("All your data will be gone.")) execFastbootDisplayed(mutableListOf("fastboot", "erase", "userdata")) } else onDeviceLost() }
    }
    @FXML private fun cachedataButtonPressed(event: ActionEvent) {
        launch { if (Device.checkFastboot()) { if (confirm("All your data will be gone.")) execFastbootDisplayed(mutableListOf("fastboot", "erase", "cache"), mutableListOf("fastboot", "erase", "userdata")) } else onDeviceLost() }
    }
    @FXML private fun lockButtonPressed(event: ActionEvent) {
        launch { if (Device.checkFastboot()) { if (confirm("Your partitions must be intact in order to successfully lock the bootloader.")) if (confirm("All your data will be gone.")) execFastbootDisplayed(mutableListOf("fastboot", "oem", "lock")) } else onDeviceLost() }
    }
    @FXML private fun unlockButtonPressed(event: ActionEvent) {
        launch { if (Device.checkFastboot()) { if (confirm("All your data will be gone.")) execFastbootDisplayed(mutableListOf("fastboot", "oem", "unlock")) } else onDeviceLost() }
    }

    @FXML private fun getlinkButtonPressed(event: ActionEvent) {
        branchComboBox.value?.let {
            if (codenameTextField.text.isNotBlank()) {
                launch {
                    withContext(Dispatchers.Main) { outputTextArea.appendText("\nLooking for $it...\n"); progressIndicator.isVisible = true }
                    val link = RomLinkResolver.getLink(it, codenameTextField.text.trim())
                    withContext(Dispatchers.Main) {
                        if (link != null && "bigota" in link) {
                            versionLabel.text = link.substringAfter(".com/").substringBefore('/')
                            progressIndicator.isVisible = false
                            outputTextArea.appendText("$link\nLink copied to clipboard!\n")
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
                        } else { versionLabel.text = "-"; progressIndicator.isVisible = false; outputTextArea.appendText("Link not found!\n") }
                    }
                }
            }
        }
    }

    @FXML private fun downloadromButtonPressed(event: ActionEvent) {
        branchComboBox.value?.let { branch ->
            if (codenameTextField.text.isNotBlank()) {
                DirectoryChooser().apply {
                    title = "Select the download location of the Fastboot ROM"
                    showDialog((event.source as Node).scene.window)?.let {
                        outputTextArea.appendText("Looking for $branch...\n"); progressIndicator.isVisible = true
                        launch {
                            val link = RomLinkResolver.getLink(branch, codenameTextField.text.trim())
                            if (link != null && "bigota" in link) {
                                withContext(Dispatchers.Main) {
                                    versionLabel.text = link.substringAfter(".com/").substringBefore('/'); outputTextArea.appendText("Starting download...\n"); downloaderPane.isDisable = true
                                }
                                Downloader(link, File(it, link.substringAfterLast('/')), downloadProgress).start(this)
                                withContext(Dispatchers.Main) { progressIndicator.isVisible = false; outputTextArea.appendText("Download complete!\n\n"); downloadProgress.text = "100.0%"; downloaderPane.isDisable = false }
                            } else withContext(Dispatchers.Main) { versionLabel.text = "-"; progressIndicator.isVisible = false; outputTextArea.appendText("Link not found!\n\n") }
                        }
                    }
                }
            }
        }
    }
}
