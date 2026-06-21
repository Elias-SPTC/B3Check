import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.b3check.MainContainer
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val dataSource = DesktopDataSource()
    // Define dimensões relativas de um celular (Proporção ~9:18)
    val windowState = rememberWindowState(width = 420.dp, height = 840.dp)
    
    Window(
        onCloseRequest = ::exitApplication, 
        title = "B3Check - Gestor Expert",
        state = windowState
    ) {
        MainContainer(
            dataSource = dataSource,
            onExport = { json, defaultName ->
                val fd = FileDialog(Frame(), "Exportar Backup", FileDialog.SAVE)
                fd.file = defaultName
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
