package com.example.b3check

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class StockViewModel(private val db: AssetDataSource) : ViewModel() {

    private val _uiState = MutableStateFlow<StockUiState>(StockUiState.Idle)
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    private val _allAssets = MutableStateFlow<List<AssetData>>(emptyList())
    val allAssets: StateFlow<List<AssetData>> = _allAssets.asStateFlow()

    private val _recommendations = MutableStateFlow<List<AssetData>>(emptyList())
    val recommendations: StateFlow<List<AssetData>> = _recommendations.asStateFlow()

    private val _portfolioAllocation = MutableStateFlow<List<Pair<AssetData, Double>>>(emptyList())
    val portfolioAllocation: StateFlow<List<Pair<AssetData, Double>>> = _portfolioAllocation.asStateFlow()

    private val _globalAiResponse = MutableStateFlow<String?>(null)
    val globalAiResponse: StateFlow<String?> = _globalAiResponse.asStateFlow()

    init { loadAllAssets() }

    fun loadAllAssets() {
        viewModelScope.launch {
            val list = db.getAllAssets()
            _allAssets.value = list
            updateRecommendations(list)
            updatePortfolioAllocation(list)
        }
    }

    fun lookupTicker(ticker: String) {
        val t = ticker.trim().uppercase()
        if (t.isBlank()) return
        viewModelScope.launch {
            val data = db.getAsset(t)
            if (data != null) {
                _uiState.value = StockUiState.Success(data, calculateScoreForAsset(data))
            } else {
                _uiState.value = StockUiState.Error("Ativo não encontrado.")
            }
        }
    }

    fun resetAnalysis() {
        _uiState.value = StockUiState.Idle
    }

    private val ai = getAiService()
    var geminiApiKey: String
        get() = db.getSettings("gemini_key")
        set(value) = db.saveSettings("gemini_key", value.filter { !it.isWhitespace() })

    private val _aiStatus = MutableStateFlow("Pronto")
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    fun askAi(ticker: String, question: String) {
        if (geminiApiKey.isBlank()) {
            _aiStatus.value = "Chave Ausente"
            return
        }
        _aiStatus.value = "Consultando..."
        viewModelScope.launch {
            try {
                val asset = db.getAsset(ticker) ?: return@launch
                val response = ai.ask(ticker, question, geminiApiKey)
                
                if (!response.startsWith("Erro:")) {
                    val sources = asset.fieldSources?.toMutableMap() ?: mutableMapOf()
                    val updated = when(asset) {
                        is AssetData.Stock -> asset.copy()
                        is AssetData.Fii -> asset.copy()
                        is AssetData.Etf -> {
                            val data = parseAiResponse(response)
                            var current = asset
                            if (data.containsKey("aFee")) { current = current.copy(adminFee = data["aFee"]!!); sources["aFee"] = FieldSource.AI }
                            if (data.containsKey("te")) { current = current.copy(trackingError = data["te"]!!); sources["te"] = FieldSource.AI }
                            if (data.containsKey("vol")) { current = current.copy(avgDailyVolume = data["vol"]!!); sources["vol"] = FieldSource.AI }
                            if (data.containsKey("aum")) { current = current.copy(aum = data["aum"]!!); sources["aum"] = FieldSource.AI }
                            if (data.containsKey("hold")) { current = current.copy(numberOfHoldings = data["hold"]!!.toInt()); sources["hold"] = FieldSource.AI }
                            current
                        }
                        is AssetData.Bdr -> asset.copy()
                    }
                    
                    updated.qualitativeInsights = asset.qualitativeInsights + (question to response)
                    updated.fieldSources = sources
                    updated.pros = asset.pros
                    updated.cons = asset.cons
                    updated.neutros = asset.neutros
                    
                    saveManualAsset(updated)
                    _aiStatus.value = "Concluído"
                } else {
                    _uiState.value = StockUiState.Success(asset.apply { 
                        qualitativeInsights = asset.qualitativeInsights + (question to response)
                    }, calculateScoreForAsset(asset))
                    _aiStatus.value = "Erro na IA"
                }
            } catch (e: Exception) {
                _aiStatus.value = "Erro na IA"
            }
        }
    }

    fun askAiGlobal(question: String) {
        if (geminiApiKey.isBlank()) {
            _aiStatus.value = "Chave Ausente"
            return
        }
        _aiStatus.value = "Analisando Carteira..."
        viewModelScope.launch {
            try {
                val context = generatePortfolioContext()
                val prompt = "Você é um analista expert em investimentos. Abaixo está o snapshot da minha carteira com tickers, setores, contribuições para a nota técnica e volumes de liquidez. Baseado nesses dados locais e no seu conhecimento de mercado, responda: $question\n\nCONTEXTO DA CARTEIRA:\n$context"
                val response = ai.ask("GLOBAL", prompt, geminiApiKey)
                _globalAiResponse.value = response
                _aiStatus.value = "Pronto"
            } catch (e: Exception) {
                _globalAiResponse.value = "Erro ao processar análise global: ${e.message}"
                _aiStatus.value = "Erro na IA"
            }
        }
    }

    private fun generatePortfolioContext(): String {
        return allAssets.value.joinToString("\n") { asset ->
            val score = calculateScoreForAsset(asset)
            val info = when(asset) {
                is AssetData.Stock -> "Setor: ${asset.sector}, Patrimônio: ${formatSmart(asset.netEquity)}, Vol: ${formatSmart(asset.avgDailyVolume)}"
                is AssetData.Fii -> "Setor: ${asset.sector}, Patrimônio: ${formatSmart(asset.aum)}, Vol: ${formatSmart(asset.avgDailyVolume)}"
                is AssetData.Etf -> "Patrimônio: ${formatSmart(asset.aum)}, Vol: ${formatSmart(asset.avgDailyVolume)}"
                is AssetData.Bdr -> "Cotação: ${formatSmart(asset.currentPrice)}"
            }
            "- ${asset.ticker} (${asset.name}): Score Motor ${formatBR(score)}. $info"
        }
    }

    fun clearGlobalAi() {
        _globalAiResponse.value = null
        _aiStatus.value = "Pronto"
    }

    private fun parseAiResponse(response: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        val keys = listOf("aFee", "te", "vol", "aum", "hold")
        keys.forEach { key ->
            val pattern = "\"$key\"\\s*:\\s*([0-9.]+)"
            val regex = Regex(pattern)
            val match = regex.find(response)
            match?.groupValues?.get(1)?.toDoubleOrNull()?.let { map[key] = it }
        }
        return map
    }

    fun bulkAskAiEtfs() {
        if (geminiApiKey.isBlank()) return
        val etfs = allAssets.value.filterIsInstance<AssetData.Etf>()
        etfs.forEach { etf ->
            val prompt = "Forneça Taxa de administração (aFee), tracking error (te), volume diário (vol), patrimônio (aum) e holdings (hold). Responda APENAS um JSON plano com essas chaves e valores numéricos."
            askAi(etf.ticker, prompt)
        }
    }

    fun addManualAsset(ticker: String, type: String) {
        val asset = createSkeleton(ticker, type)
        saveManualAsset(asset)
        lookupTicker(ticker)
    }

    private fun createSkeleton(ticker: String, type: String): AssetData {
        return when(type) {
            "FII" -> AssetData.Fii(ticker, ticker, 0.0)
            "ETF" -> AssetData.Etf(ticker, ticker, 0.0)
            "BDR" -> AssetData.Bdr(ticker, ticker, 0.0)
            else -> AssetData.Stock(ticker, ticker, 0.0)
        }
    }

    fun saveManualAsset(data: AssetData) {
        data.lastUpdated = System.currentTimeMillis()
        val score = calculateScoreForAsset(data)
        viewModelScope.launch {
            db.saveAsset(data)
            loadAllAssets()
            if ((_uiState.value as? StockUiState.Success)?.data?.ticker == data.ticker) {
                _uiState.value = StockUiState.Success(data, score)
            }
        }
    }

    fun deleteAsset(ticker: String) {
        viewModelScope.launch {
            db.deleteAsset(ticker)
            loadAllAssets()
            if ((_uiState.value as? StockUiState.Success)?.data?.ticker == ticker) {
                _uiState.value = StockUiState.Idle
            }
        }
    }

    private fun updateRecommendations(list: List<AssetData>) {
        _recommendations.value = list.filter { !it.isInPortfolio }
            .map { it to calculateScoreForAsset(it) }
            .sortedWith(compareByDescending<Pair<AssetData, Double>> { it.second }.thenBy { it.first.ticker })
            .map { it.first }
    }

    private fun updatePortfolioAllocation(list: List<AssetData>) {
        val portfolio = list.filter { it.isInPortfolio }
        val activePortfolio = portfolio.filter { !it.isInert }

        val scoredActive = activePortfolio.map { it to calculateScoreForAsset(it) }
        val totalActiveScore = scoredActive.sumOf { it.second }

        _portfolioAllocation.value = portfolio.map { asset ->
            val idealPerc = if (asset.isInert) {
                0.0 
            } else if (totalActiveScore > 0) {
                val score = scoredActive.find { it.first.ticker == asset.ticker }?.second ?: 0.0
                (score / totalActiveScore) * 100.0
            } else 0.0
            asset to idealPerc
        }.sortedWith(compareByDescending<Pair<AssetData, Double>> { it.second }.thenBy { it.first.ticker })
    }

    fun calculateScoreForAsset(assetData: AssetData): Double {
        if (assetData.userScorePriority && assetData.userScore > 0) {
            assetData.pros = listOf("Prioridade Manual Ativa")
            assetData.cons = emptyList(); assetData.neutros = emptyList()
            return assetData.userScore
        }
        
        val indicatorResults = mutableListOf<Triple<String, Double, Double>>() // Label, Weight, Multiplier (1.0, 0.0, -1.0)
        val sources = assetData.fieldSources ?: emptyMap()
        fun isFilled(key: String, value: Double): Boolean = sources.containsKey(key) || value != 0.0

        when (assetData) {
            is AssetData.Stock -> {
                val sec = assetData.sector.trim().lowercase()
                val sub = assetData.subSector.trim().lowercase()
                val isBank = sub.contains("banco")
                val isFinancial = isBank || sub.contains("seguradora") || sub.contains("holding")
                val isUtility = sec.contains("utilidade") || sec.contains("pública") || sub.contains("elétrica")
                
                if (isFilled("roe", assetData.roe)) {
                    val w = if (isFinancial) 3.0 else 2.5
                    val meta = if (isFinancial) 0.15 else if (isUtility) 0.10 else 0.14
                    val m = if (assetData.roe >= meta) 1.0 else if (assetData.roe < meta * 0.6) -1.0 else 0.0
                    indicatorResults.add(Triple("ROE: ${formatBR(assetData.roe*100)}%", w, m))
                }
                if (isBank && isFilled("basel", assetData.baselIndex)) {
                    val m = if (assetData.baselIndex >= 0.14) 1.0 else if (assetData.baselIndex < 0.11) -1.0 else 0.0
                    indicatorResults.add(Triple("Basileia", 2.0, m))
                } else if (!isFinancial) {
                    val limit = if (isUtility) 4.5 else 3.0
                    if (isFilled("deEbitda", assetData.debtToEbitda)) {
                        val m = if (assetData.debtToEbitda < limit) 1.0 else if (assetData.debtToEbitda > limit * 1.5) -1.0 else 0.0
                        indicatorResults.add(Triple("Endividamento (EBITDA)", 1.0, m))
                    }
                }
                if (isBank || isFilled("ml", assetData.netMargin)) {
                    val m = if (assetData.netMargin > 0.12 || isBank) 1.0 else if (assetData.netMargin < 0.05) -1.0 else 0.0
                    indicatorResults.add(Triple("Margem Líquida", 1.0, m))
                }
                if (isFilled("pvp", assetData.pvp)) {
                    val pvpLimit = if (isBank) 1.8 else 2.0
                    val m = if (assetData.pvp in 0.1..pvpLimit) 1.0 else if (assetData.pvp > pvpLimit + 1.0) -1.0 else 0.0
                    indicatorResults.add(Triple("P/VP (Valuation)", 1.0, m))
                }
                if (isFilled("dy", assetData.dividendYield)) {
                    val m = if (assetData.dividendYield >= 0.05) 1.0 else if (assetData.dividendYield < 0.02) -1.0 else 0.0
                    indicatorResults.add(Triple("Dividend Yield", 1.0, m))
                }
                if (isFilled("netEquity", assetData.netEquity)) {
                    val m = if (assetData.netEquity >= 1_000_000_000.0) 1.0 else 0.0
                    indicatorResults.add(Triple("Porte (Patrimônio)", 0.5, m))
                }
            }
            is AssetData.Fii -> {
                val isPapel = assetData.sector.lowercase().contains("papel")
                if (isFilled("pvp", assetData.pvp)) {
                    val limit = if (isPapel) 1.03 else 1.08
                    val m = if (assetData.pvp <= limit) 1.0 else if (assetData.pvp > limit + 0.15) -1.0 else 0.0
                    indicatorResults.add(Triple("P/VP", 2.5, m))
                }
                if (isFilled("y12", assetData.yield12m)) {
                    val m = if (assetData.yield12m >= 0.09) 1.0 else if (assetData.yield12m < 0.06) -1.0 else 0.0
                    indicatorResults.add(Triple("Yield 12m", 2.5, m))
                }
                if (!isPapel && isFilled("vac", assetData.vacancy)) {
                    val m = if (assetData.vacancy < 0.07) 1.0 else if (assetData.vacancy > 0.20) -1.0 else 0.0
                    indicatorResults.add(Triple("Vacância", 2.0, m))
                }
                if (sources.containsKey("tScore") || assetData.tenantScore > 0) {
                    val m = if (assetData.tenantScore >= 4) 1.0 else if (assetData.tenantScore <= 1) -1.0 else 0.0
                    indicatorResults.add(Triple("Qualidade Inquilinos", 1.5, m))
                }
                if (sources.containsKey("lScore") || assetData.leverageScore > 0) {
                    val m = if (assetData.leverageScore >= 4) 1.0 else if (assetData.leverageScore <= 1) -1.0 else 0.0
                    indicatorResults.add(Triple("Nível Alavancagem", 1.5, m))
                }
            }
            is AssetData.Etf -> {
                if (isFilled("aFee", assetData.adminFee)) {
                    val m = if (assetData.adminFee <= 0.004) 1.0 else if (assetData.adminFee > 0.01) -1.0 else 0.0
                    indicatorResults.add(Triple("Taxa Adm", 6.0, m))
                }
                if (isFilled("hold", assetData.numberOfHoldings.toDouble())) {
                    val m = if (assetData.numberOfHoldings > 50) 1.0 else 0.0
                    indicatorResults.add(Triple("Diversificação", 4.0, m))
                }
            }
            is AssetData.Bdr -> {
                if (assetData is AssetData.Bdr) {
                    if (isFilled("dy", assetData.dividendYield)) { val m = if (assetData.dividendYield > 0.02) 1.0 else -1.0; indicatorResults.add(Triple("Dividendos", 5.0, m)) }
                    val p = if(assetData.parity=="1:1") 1.0 else 0.0; indicatorResults.add(Triple("Paridade", 5.0, p))
                }
            }
        }

        val totalWeight = indicatorResults.sumOf { it.second }
        val scoreMultiplier = if (totalWeight > 0) 5.0 / totalWeight else 0.0

        val prosList = mutableListOf<String>(); val consList = mutableListOf<String>(); val neutrosList = mutableListOf<String>()
        var netImpactAccumulator = 0.0

        indicatorResults.forEach { (label, weight, multiplier) ->
            val impact = weight * multiplier * scoreMultiplier
            netImpactAccumulator += impact
            val formatted = "${if(impact > 0) "+" else if(impact < 0) "" else "+"}${formatBR(impact)}: $label"
            when {
                multiplier > 0 -> prosList.add(formatted)
                multiplier < 0 -> consList.add(formatted)
                else -> neutrosList.add(formatted)
            }
        }

        val rawScore = (5.0 + netImpactAccumulator).coerceIn(0.0, 10.0)
        val final = if (assetData.userScoreAverage && assetData.userScore > 0) (rawScore + assetData.userScore) / 2.0 else rawScore

        assetData.pros = prosList; assetData.cons = consList; assetData.neutros = neutrosList
        return final.coerceIn(0.0, 10.0)
    }

    fun getIntegrityWarnings(asset: AssetData): List<String> {
        val warnings = mutableListOf<String>()
        if (asset.currentPrice <= 0) warnings.add("Preço zero ou negativo")
        if (asset is AssetData.Stock) {
            if (asset.pvp <= 0) warnings.add("P/VP inválido")
            if (asset.roe <= 0) warnings.add("ROE zero ou negativo")
        }
        return warnings
    }

    fun recalculateAllScores() {
        viewModelScope.launch {
            val list = db.getAllAssets()
            list.forEach { 
                calculateScoreForAsset(it)
                db.saveAsset(it) 
            }
            loadAllAssets()
        }
    }

    fun exportBackup(): String {
        val json = db.exportBackup()
        return json.ifBlank { "{}" }
    }
    fun importBackup(j: String) { viewModelScope.launch { if (db.importBackup(j)) loadAllAssets() } }

    fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    private fun formatBR(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%,.2f", v)
}
