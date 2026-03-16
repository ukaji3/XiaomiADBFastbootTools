import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.TextArea
import javafx.scene.layout.VBox
import javafx.stage.StageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

enum class Mode {
    ADB, FASTBOOT, AUTH, RECOVERY, ADB_ERROR, FASTBOOT_ERROR
}

fun isAppSelected(list: ObservableList<App>) = list.isNotEmpty() && list.any { it.selectedProperty().get() }

fun String.escape(): String = "'$this'"

fun MutableMap<String, MutableList<String>>.add(key: String, value: String) {
    if (this[key] == null) {
        this[key] = mutableListOf(value)
    } else this[key]!!.add(value)
}

fun startProcess(vararg command: String, redirectErrorStream: Boolean = false): Process =
    ProcessBuilder(*command).directory(XiaomiADBFastbootTools.dir).redirectErrorStream(redirectErrorStream).start()

fun startProcess(command: List<String?>, redirectErrorStream: Boolean = false): Process =
    ProcessBuilder(command).directory(XiaomiADBFastbootTools.dir).redirectErrorStream(redirectErrorStream).start()

fun runScript(file: File, redirectErrorStream: Boolean = false): Process = if (XiaomiADBFastbootTools.win)
    ProcessBuilder("cmd.exe", "/c", file.absolutePath).directory(XiaomiADBFastbootTools.dir)
        .redirectErrorStream(redirectErrorStream).start()
else ProcessBuilder("sh", "-c", file.absolutePath).directory(XiaomiADBFastbootTools.dir)
    .redirectErrorStream(redirectErrorStream).start()

suspend fun Exception.alert() {
    val stringWriter = StringWriter()
    val printWriter = PrintWriter(stringWriter)
    this.printStackTrace(printWriter)
    withContext(Dispatchers.Main) {
        Alert(Alert.AlertType.ERROR).apply {
            initStyle(StageStyle.UTILITY)
            title = "ERROR"
            headerText = "Unexpected exception!"
            val vb = VBox()
            vb.alignment = Pos.CENTER
            val textArea = TextArea(stringWriter.toString()).apply {
                isEditable = false
                isWrapText = true
                maxWidth = Double.MAX_VALUE
                maxHeight = Double.MAX_VALUE
            }
            vb.children.add(textArea)
            dialogPane.content = vb
            isResizable = false
            showAndWait()
        }
        Platform.exit()
    }
}

suspend fun confirm(msg: String = ""): Boolean = withContext(Dispatchers.Main) {
    Alert(Alert.AlertType.CONFIRMATION).run {
        initStyle(StageStyle.UTILITY)
        isResizable = false
        headerText = "${msg.trim()}\nAre you sure you want to proceed?".trim()
        val yes = ButtonType("Yes")
        val no = ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE)
        buttonTypes.setAll(yes, no)
        val result = showAndWait()
        result.get() == yes
    }
}
