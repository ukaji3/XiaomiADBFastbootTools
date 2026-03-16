import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTableCell
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyCode
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.*
import java.net.URL
import java.util.*

class AdbTabController : Initializable, CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    @FXML lateinit var appManagerPane: TabPane
    @FXML lateinit var reinstallerTab: Tab
    @FXML lateinit var disablerTab: Tab
    @FXML lateinit var enablerTab: Tab
    @FXML lateinit var uninstallerTableView: TableView<App>
    @FXML lateinit var reinstallerTableView: TableView<App>
    @FXML lateinit var disablerTableView: TableView<App>
    @FXML lateinit var enablerTableView: TableView<App>
    @FXML private lateinit var uncheckTableColumn: TableColumn<App, Boolean>
    @FXML private lateinit var unappTableColumn: TableColumn<App, String>
    @FXML private lateinit var unpackageTableColumn: TableColumn<App, String>
    @FXML private lateinit var recheckTableColumn: TableColumn<App, Boolean>
    @FXML private lateinit var reappTableColumn: TableColumn<App, String>
    @FXML private lateinit var repackageTableColumn: TableColumn<App, String>
    @FXML private lateinit var discheckTableColumn: TableColumn<App, Boolean>
    @FXML private lateinit var disappTableColumn: TableColumn<App, String>
    @FXML private lateinit var dispackageTableColumn: TableColumn<App, String>
    @FXML private lateinit var encheckTableColumn: TableColumn<App, Boolean>
    @FXML private lateinit var enappTableColumn: TableColumn<App, String>
    @FXML private lateinit var enpackageTableColumn: TableColumn<App, String>
    @FXML lateinit var camera2Pane: TitledPane
    @FXML lateinit var fileExplorerPane: TitledPane
    @FXML lateinit var dpiPane: TitledPane
    @FXML lateinit var resolutionPane: TitledPane
    @FXML private lateinit var dpiTextField: TextField
    @FXML private lateinit var widthTextField: TextField
    @FXML private lateinit var heightTextField: TextField

    lateinit var outputTextArea: TextArea
    lateinit var progressBar: ProgressBar
    lateinit var progressIndicator: ProgressIndicator
    lateinit var onDeviceLost: suspend () -> Unit

    private val displayOutput: suspend (String) -> Unit = { line ->
        withContext(Dispatchers.Main) { outputTextArea.appendText(line) }
    }

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        uncheckTableColumn.cellValueFactory = PropertyValueFactory("selected")
        uncheckTableColumn.setCellFactory { CheckBoxTableCell() }
        unappTableColumn.cellValueFactory = PropertyValueFactory("appname")
        unpackageTableColumn.cellValueFactory = PropertyValueFactory("packagename")
        recheckTableColumn.cellValueFactory = PropertyValueFactory("selected")
        recheckTableColumn.setCellFactory { CheckBoxTableCell() }
        reappTableColumn.cellValueFactory = PropertyValueFactory("appname")
        repackageTableColumn.cellValueFactory = PropertyValueFactory("packagename")
        discheckTableColumn.cellValueFactory = PropertyValueFactory("selected")
        discheckTableColumn.setCellFactory { CheckBoxTableCell() }
        disappTableColumn.cellValueFactory = PropertyValueFactory("appname")
        dispackageTableColumn.cellValueFactory = PropertyValueFactory("packagename")
        encheckTableColumn.cellValueFactory = PropertyValueFactory("selected")
        encheckTableColumn.setCellFactory { CheckBoxTableCell() }
        enappTableColumn.cellValueFactory = PropertyValueFactory("appname")
        enpackageTableColumn.cellValueFactory = PropertyValueFactory("packagename")

        uninstallerTableView.columns.setAll(uncheckTableColumn, unappTableColumn, unpackageTableColumn)
        reinstallerTableView.columns.setAll(recheckTableColumn, reappTableColumn, repackageTableColumn)
        disablerTableView.columns.setAll(discheckTableColumn, disappTableColumn, dispackageTableColumn)
        enablerTableView.columns.setAll(encheckTableColumn, enappTableColumn, enpackageTableColumn)

        listOf(uninstallerTableView, reinstallerTableView, disablerTableView, enablerTableView).forEach { tv ->
            tv.setOnKeyPressed {
                if (it.code == KeyCode.ENTER || it.code == KeyCode.SPACE)
                    tv.selectionModel.selectedItems.forEach { app -> app.selectedProperty().set(!app.selectedProperty().get()) }
            }
        }
    }

    fun setDpiFields(dpi: Int, width: Int, height: Int) {
        dpiTextField.text = if (dpi != -1) dpi.toString() else "ERROR"
        widthTextField.text = if (width != -1) width.toString() else "ERROR"
        heightTextField.text = if (height != -1) height.toString() else "ERROR"
    }

    suspend fun refreshAppTables() {
        val lists = AppManager.getAppLists()
        withContext(Dispatchers.Main) {
            uninstallerTableView.items.setAll(lists.uninstall)
            reinstallerTableView.items.setAll(lists.reinstall)
            disablerTableView.items.setAll(lists.disable)
            enablerTableView.items.setAll(lists.enable)
        }
    }

    private fun runAppOperation(
        tableView: TableView<App>,
        operation: suspend (List<App>, suspend (OperationResult) -> Unit) -> List<OperationResult>
    ) {
        if (isAppSelected(tableView.items))
            launch {
                if (Device.checkADB()) {
                    if (confirm()) {
                        val selected = tableView.items.filter { it.selectedProperty().get() }
                        val n = selected.sumOf { it.packagenameProperty().get().trim().lines().size }
                        var done = 0
                        withContext(Dispatchers.Main) {
                            outputTextArea.text = ""
                            progressBar.progress = 0.0
                            progressIndicator.isVisible = true
                        }
                        operation(selected) { result ->
                            done++
                            withContext(Dispatchers.Main) {
                                outputTextArea.appendText("App: ${result.appName}\nPackage: ${result.packageName}\nResult: ${result.output}\n")
                                progressBar.progress = done.toDouble() / n
                            }
                        }
                        withContext(Dispatchers.Main) {
                            outputTextArea.appendText("Done!")
                            progressBar.progress = 0.0
                            progressIndicator.isVisible = false
                        }
                        refreshAppTables()
                    }
                } else onDeviceLost()
            }
    }

    @FXML private fun uninstallButtonPressed(event: ActionEvent) =
        runAppOperation(uninstallerTableView) { sel, cb -> AppManager.uninstall(sel, cb) }
    @FXML private fun reinstallButtonPressed(event: ActionEvent) =
        runAppOperation(reinstallerTableView) { sel, cb -> AppManager.reinstall(sel, cb) }
    @FXML private fun disableButtonPressed(event: ActionEvent) =
        runAppOperation(disablerTableView) { sel, cb -> AppManager.disable(sel, cb) }
    @FXML private fun enableButtonPressed(event: ActionEvent) =
        runAppOperation(enablerTableView) { sel, cb -> AppManager.enable(sel, cb) }

    private suspend fun checkCamera2() = "1" in Command.exec(mutableListOf("adb", "shell", "getprop", "persist.camera.HAL3.enabled"))
    private suspend fun checkEIS() = "1" in Command.exec(mutableListOf("adb", "shell", "getprop", "persist.camera.eis.enable"))

    private fun toggleRecoveryProp(prop: String, value: String, successMsg: String, failMsg: String, verify: suspend () -> Boolean) {
        launch {
            if (Device.checkRecovery()) {
                Command.exec(mutableListOf("adb", "shell", "setprop", prop, value))
                withContext(Dispatchers.Main) { outputTextArea.text = if (verify()) successMsg else failMsg }
            } else onDeviceLost()
        }
    }

    @FXML private fun enableCamera2ButtonPressed(event: ActionEvent) =
        toggleRecoveryProp("persist.camera.HAL3.enabled", "1", "Camera2 enabled!", "ERROR: Couldn't enable Camera2!") { checkCamera2() }
    @FXML private fun disableCamera2ButtonPressed(event: ActionEvent) =
        toggleRecoveryProp("persist.camera.HAL3.enabled", "0", "Camera2 disabled!", "ERROR: Couldn't disable Camera2!") { !checkCamera2() }
    @FXML private fun enableEISButtonPressed(event: ActionEvent) =
        toggleRecoveryProp("persist.camera.eis.enable", "1", "EIS enabled!", "ERROR: Couldn't enable EIS!") { checkEIS() }
    @FXML private fun disableEISButtonPressed(event: ActionEvent) =
        toggleRecoveryProp("persist.camera.eis.enable", "0", "EIS disabled!", "ERROR: Couldn't disable EIS!") { !checkEIS() }

    @FXML private fun openButtonPressed(event: ActionEvent) {
        launch(Dispatchers.IO) {
            if (Device.checkADB()) {
                val scene = Scene(FXMLLoader(javaClass.classLoader.getResource("FileExplorer.fxml")).load())
                withContext(Dispatchers.Main) {
                    Stage().apply { this.scene = scene; initModality(Modality.APPLICATION_MODAL); title = "File Explorer"; isResizable = false; showAndWait() }
                }
            } else onDeviceLost()
        }
    }

    private fun execWmCommand(vararg args: String) {
        launch {
            if (Device.checkADB()) {
                withContext(Dispatchers.Main) { outputTextArea.text = "" }
                val attempt = Command.execDisplayed(mutableListOf("adb", "shell", "wm", *args), onOutput = displayOutput)
                withContext(Dispatchers.Main) {
                    outputTextArea.text = when {
                        "permission" in attempt -> "ERROR: Please allow USB debugging (Security settings)!"
                        "bad" in attempt -> "ERROR: Invalid value!"
                        attempt.isEmpty() -> "Done!"
                        else -> "ERROR: $attempt"
                    }
                }
            } else onDeviceLost()
        }
    }

    @FXML private fun applyDpiButtonPressed(event: ActionEvent) { if (dpiTextField.text.isNotBlank()) execWmCommand("density", dpiTextField.text.trim()) }
    @FXML private fun resetDpiButtonPressed(event: ActionEvent) = execWmCommand("density", "reset")
    @FXML private fun applyResButtonPressed(event: ActionEvent) {
        if (widthTextField.text.isNotBlank() && heightTextField.text.isNotBlank())
            execWmCommand("size", "${widthTextField.text.trim()}x${heightTextField.text.trim()}")
    }
    @FXML private fun resetResButtonPressed(event: ActionEvent) = execWmCommand("size", "reset")
}
