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

                var exportContent by remember { mutableStateOf("") }
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("*/*")
                ) { uri ->
                    uri?.let {
                        try {
                            contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(exportContent.toByteArray())
                                stream.flush()
                                Toast.makeText(this, "Arquivo salvo com sucesso!", Toast.LENGTH_SHORT).show()
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
                            contentResolver.openInputStream(it)?.use { inputStream ->
                                val json = inputStream.bufferedReader().use { r -> r.readText() }
                                if (json.isNotBlank()) {
                                    importCallback?.invoke(json)
                                } else {
                                    Toast.makeText(this, "O arquivo selecionado está vazio", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Erro ao ler: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                MainContainer(
                    dataSource = container.dataSource,
                    onExport = { content, defaultName -> 
                        exportContent = content
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
