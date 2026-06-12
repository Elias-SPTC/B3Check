import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.b3check.MainContainer
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val dataSource = DesktopDataSource()
    
    Window(onCloseRequest = ::exitApplication, title = "B3Check - Gestor Expert") {
        MainContainer(
            dataSource = dataSource,
            onExport = { json ->
                val fd = FileDialog(Frame(), "Exportar Backup", FileDialog.SAVE)
                fd.file = "B3Check-Backup.json"
                fd.isVisible = true
                val file = fd.file
                val dir = fd.directory
                if (file != null && dir != null) {
                    File(dir, file).writeText(json)
                }
            },
            onImport = { onResult ->
                val fd = FileDialog(Frame(), "Importar Backup", FileDialog.LOAD)
                fd.isVisible = true
                val file = fd.file
                val dir = fd.directory
                if (file != null && dir != null) {
                    val json = File(dir, file).readText()
                    onResult(json)
                }
            }
        )
    }
}
