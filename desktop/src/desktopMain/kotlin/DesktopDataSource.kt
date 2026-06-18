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
        val asset = when(it) {
            is AssetData.Stock -> it.copy(
                sector = it.sector ?: "", 
                subSector = it.subSector ?: "",
                valuationSource = it.valuationSource ?: "",
                isInPortfolio = it.isInPortfolio,
                isInert = it.isInert,
                userScore = it.userScore,
                userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage
            )
            is AssetData.Fii -> it.copy(
                sector = it.sector ?: "", 
                subSector = it.subSector ?: "",
                managementType = it.managementType ?: "Ativa",
                fundType = it.fundType ?: "Tijolo",
                isInPortfolio = it.isInPortfolio,
                isInert = it.isInert,
                userScore = it.userScore,
                userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage
            )
            is AssetData.Etf -> it.copy(
                sector = it.sector ?: "ETF", 
                subSector = it.subSector ?: "ETF",
                isInPortfolio = it.isInPortfolio,
                isInert = it.isInert,
                userScore = it.userScore,
                userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage
            )
            is AssetData.Bdr -> it.copy(
                sector = it.sector ?: "BDR", 
                subSector = it.subSector ?: "BDR",
                parity = it.parity ?: "1:1",
                isInPortfolio = it.isInPortfolio,
                isInert = it.isInert,
                userScore = it.userScore,
                userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage
            )
        }
        asset.pros = it.pros ?: emptyList()
        asset.cons = it.cons ?: emptyList()
        asset.fieldSources = it.fieldSources ?: emptyMap()
        asset.lastUpdated = it.lastUpdated
        return asset
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
    
    override fun importBackup(json: String): Boolean {
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
            
            val localAssets = getAllAssets().toMutableList()
            var changed = false
            
            importedAssets.forEach { imported ->
                val localIndex = localAssets.indexOfFirst { it.ticker == imported.ticker }
                if (localIndex == -1) {
                    localAssets.add(imported)
                    changed = true
                } else {
                    val local = localAssets[localIndex]
                    // Mesclagem Granular: Só substitui se o importado for mais recente
                    if (imported.lastUpdated > local.lastUpdated) {
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
