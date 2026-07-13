import com.example.b3check.AssetData
import com.example.b3check.AssetDataSource
import com.google.gson.Gson
import java.io.File
import javax.swing.JFileChooser

class DesktopDataSource : AssetDataSource {
    private val gson = Gson()
    private val storageFile: File = run {
        val home = System.getProperty("user.home")
        val configDir = File(home, ".b3check")
        if (!configDir.exists()) configDir.mkdirs()
        
        val pathFile = File(configDir, "storage_location.txt")
        
        // 1. Tenta carregar a PASTA salva anteriormente
        if (pathFile.exists()) {
            val savedPath = pathFile.readText().trim()
            if (savedPath.isNotEmpty()) {
                val folder = File(savedPath)
                if (folder.exists() && folder.isDirectory) {
                    return@run File(folder, "assets.json")
                }
            }
        }
        
        // 2. Busca automática no Filen (Caminho padrão)
        val filenFolder = File(home, "Filen/B3Check")
        if (filenFolder.exists() && filenFolder.isDirectory) {
            pathFile.writeText(filenFolder.absolutePath)
            return@run File(filenFolder, "assets.json")
        }

        // 3. Se não encontrou, abre o diálogo de seleção de PASTA
        try {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = "Selecione a PASTA do Filen para o banco do B3Check"
            val result = chooser.showOpenDialog(null)
            
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFolder = chooser.selectedFile
                pathFile.writeText(selectedFolder.absolutePath)
                return@run File(selectedFolder, "assets.json")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback final
        File(configDir, "assets.json")
    }

    init {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (!storageFile.exists()) {
            storageFile.writeText("[]")
        }
    }

    private fun safeAsset(it: AssetData): AssetData {
        // Garantindo integridade após desserialização sem perda de campos var
        if (it.pros == null) it.pros = emptyList()
        if (it.cons == null) it.cons = emptyList()
        if (it.neutros == null) it.neutros = emptyList()
        if (it.fieldSources == null) it.fieldSources = emptyMap()
        if (it.qualitativeInsights == null) it.qualitativeInsights = emptyMap()
        return it
    }

    override fun saveAsset(asset: AssetData) {
        val all = getAllAssets().toMutableList()
        all.removeAll { it.ticker == asset.ticker }
        all.add(safeAsset(asset))
        saveAll(all)
    }

    private fun saveAll(list: List<AssetData>) {
        val backupList = list.map { asset ->
            val type = when (asset) {
                is AssetData.Stock -> "STOCK"
                is AssetData.Fii -> "FII"
                is AssetData.Etf -> "ETF"
                is AssetData.Bdr -> "BDR"
            }
            mapOf("type" to type, "json" to gson.toJson(asset))
        }
        storageFile.writeText(gson.toJson(backupList))
    }

    override fun getAsset(ticker: String): AssetData? {
        return getAllAssets().find { it.ticker == ticker }
    }

    override fun deleteAsset(ticker: String) {
        val all = getAllAssets().toMutableList()
        all.removeAll { it.ticker == ticker }
        saveAll(all)
    }

    override fun getAllAssets(): List<AssetData> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val json = storageFile.readText()
            val typeToken = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val data: List<Map<String, String>> = gson.fromJson(json, typeToken) ?: return emptyList()
            
            data.mapNotNull { item ->
                val type = item["type"]
                val jsonData = item["json"]
                val asset = when (type) {
                    "STOCK" -> gson.fromJson(jsonData, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(jsonData, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(jsonData, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(jsonData, AssetData.Bdr::class.java)
                    else -> null
                }
                asset?.let { safeAsset(it) }
            }.sortedBy { it.ticker }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun exportBackup(): String = storageFile.readText()
    
    override fun getSettings(key: String): String {
        val home = System.getProperty("user.home")
        val settingsFile = File(home, ".b3check/settings_$key.txt")
        return if (settingsFile.exists()) settingsFile.readText() else ""
    }

    override fun saveSettings(key: String, value: String) {
        val home = System.getProperty("user.home")
        val configDir = File(home, ".b3check")
        if (!configDir.exists()) configDir.mkdirs()
        val settingsFile = File(configDir, "settings_$key.txt")
        settingsFile.writeText(value.trim())
    }

    override fun importBackup(json: String, force: Boolean): Boolean {
        return try {
            val typeToken = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val importedData: List<Map<String, String>> = gson.fromJson(json, typeToken)
            
            val importedAssets = importedData.mapNotNull { item ->
                val type = item["type"]
                val jsonData = item["json"]
                val asset = when (type) {
                    "STOCK" -> gson.fromJson(jsonData, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(jsonData, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(jsonData, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(jsonData, AssetData.Bdr::class.java)
                    else -> null
                }
                asset?.let { safeAsset(it) }
            }
            
            if (force) {
                saveAll(importedAssets)
                return true
            }

            val localAssets = getAllAssets().toMutableList()
            var changed = false
            
            importedAssets.forEach { imported ->
                val localIndex = localAssets.indexOfFirst { it.ticker == imported.ticker }
                if (localIndex == -1) {
                    localAssets.add(imported)
                    changed = true
                } else {
                    val local = localAssets[localIndex]
                    // Na importação de backup, somos mais permissivos para garantir a sincronia total
                    if (imported.lastUpdated >= local.lastUpdated) {
                        localAssets[localIndex] = imported
                        changed = true
                    }
                }
            }
            
            if (changed) {
                saveAll(localAssets)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
