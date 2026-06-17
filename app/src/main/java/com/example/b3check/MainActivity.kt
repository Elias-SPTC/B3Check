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
                var importCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

                val createDocumentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        try {
                            val json = container.dataSource.exportBackup()
                            contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(json.toByteArray())
                                stream.flush()
                                Toast.makeText(this, "Backup criado!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Erro ao gravar arquivo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val openDocumentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        try {
                            contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                                val json = r.readText()
                                importCallback?.invoke(json)
                                Toast.makeText(this, "Banco de dados restaurado!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Erro ao ler arquivo", Toast.LENGTH_SHORT).show()
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
