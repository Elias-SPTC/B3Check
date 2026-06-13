package com.example.b3check

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.b3check.ui.theme.B3CheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            B3CheckTheme {
                val container = remember { B3CheckContainer(this) }
                
                // Armazena temporariamente o callback de importação para sincronizar com o motor
                var importCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

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
                            val json = r.readText()
                            importCallback?.invoke(json) // Aciona o importBackup do StockViewModel
                            Toast.makeText(this, "Restaurado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                MainContainer(
                    dataSource = container.dataSource,
                    onExport = { _, defaultName -> 
                        createDocumentLauncher.launch(defaultName) 
                    },
                    onImport = { onResult -> 
                        importCallback = onResult
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }
        }
    }
}
