package com.example.b3check

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.b3check.ui.theme.B3CheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            B3CheckTheme {
                val container = B3CheckContainer(this)
                
                // Implementação Android dos seletores de arquivo
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        val json = container.dataSource.exportBackup()
                        contentResolver.openOutputStream(it)?.use { s -> 
                            s.write(json.toByteArray())
                            Toast.makeText(this, "Backup OK!", Toast.LENGTH_SHORT).show() 
                        }
                    }
                }

                val openDocumentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> 
                            if (container.dataSource.importBackup(r.readText())) {
                                Toast.makeText(this, "Restaurado!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                MainContainer(
                    dataSource = container.dataSource,
                    onExport = { defaultName -> createDocumentLauncher.launch(defaultName) },
                    onImport = { onResult -> 
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }
        }
    }
}
