import com.example.b3check.AssetData
import com.example.b3check.AssetDataSource
import com.google.gson.Gson
import java.io.File

class DesktopDataSource : AssetDataSource {
    private val gson = Gson()
    private val storageFile = File(System.getProperty("user.home"), ".b3check/assets.json")

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
        // Garante que campos nulos de versões antigas sejam tratados como strings vazias ou valores padrão
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
            val data: List<Map<String, String>> = gson.fromJson(json, typeToken)
            
            // Processa cada item para garantir que os dados "sujos" sejam limpos antes de salvar
            val cleanedList = data.mapNotNull { item ->
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
            
            saveAll(cleanedList)
            true
        } catch (e: Exception) {
            false
        }
    }
}
