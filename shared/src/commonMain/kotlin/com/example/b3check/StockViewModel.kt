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

    fun calculateScoreForAsset(data: AssetData): Double {
        if (data.userScorePriority && data.userScore > 0) {
            data.pros = listOf("Prioridade Manual Ativa")
            data.cons = emptyList(); data.neutros = emptyList()
            return data.userScore
        }
        
        val indicatorResults = mutableListOf<Triple<String, Double, Double>>() // Label, Weight, Multiplier (1.0, 0.0, -1.0)
        val sources = data.fieldSources ?: emptyMap()
        fun isFilled(key: String, value: Double): Boolean = sources.containsKey(key) || value != 0.0

        when (data) {
            is AssetData.Stock -> {
                val sec = data.sector.trim().lowercase()
                val sub = data.subSector.trim().lowercase()
                val isBank = sub.contains("banco")
                val isInsurance = sub.contains("seguradora")
                val isHolding = sub.contains("holding")
                val isFinancial = isBank || isInsurance || isHolding
                val isUtility = sec.contains("utilidade") || sec.contains("pública") || sub.contains("elétrica")
                
                if (isFilled("roe", data.roe)) {
                    val w = if (isFinancial) 3.0 else 2.5
                    val meta = if (isFinancial) 0.15 else if (isUtility) 0.10 else 0.14
                    val m = if (data.roe >= meta) 1.0 else if (data.roe < meta * 0.6) -1.0 else 0.0
                    indicatorResults.add(Triple("ROE: ${formatBR(data.roe*100)}%", w, m))
                }
                if (isBank && isFilled("basel", data.baselIndex)) {
                    val m = if (data.baselIndex >= 0.14) 1.0 else if (data.baselIndex < 0.11) -1.0 else 0.0
                    indicatorResults.add(Triple("Basileia", 2.0, m))
                } else if (!isFinancial) {
                    val limit = if (isUtility) 4.5 else 3.0
                    if (isFilled("deEbitda", data.debtToEbitda)) {
                        val m = if (data.debtToEbitda < limit) 1.0 else if (data.debtToEbitda > limit * 1.5) -1.0 else 0.0
                        indicatorResults.add(Triple("Dívida/EBITDA", 1.0, m))
                    }
                    if (isFilled("de", data.debtToEquity)) {
                        val m = if (data.debtToEquity < 1.0) 1.0 else if (data.debtToEquity > 2.0) -1.0 else 0.0
                        indicatorResults.add(Triple("Dívida/Patrimônio", 1.0, m))
                    }
                }
                if (isBank || isFilled("ml", data.netMargin)) {
                    val m = if (data.netMargin > 0.12 || isBank) 1.0 else if (data.netMargin < 0.05) -1.0 else 0.0
                    indicatorResults.add(Triple("Margem Líquida", 1.0, m))
                }
                if (isFilled("cLuc", data.cagrProfit5Years)) {
                    val m = if (data.cagrProfit5Years >= 0.08) 1.0 else if (data.cagrProfit5Years < 0) -1.0 else 0.0
                    indicatorResults.add(Triple("CAGR Lucro (5a)", 1.0, m))
                }
                if (!isBank && isFilled("cRec", data.cagrRevenue5Years)) {
                    val m = if (data.cagrRevenue5Years >= 0.08) 1.0 else if (data.cagrRevenue5Years < 0) -1.0 else 0.0
                    indicatorResults.add(Triple("CAGR Receita (5a)", 0.5, m))
                }
                if (isFilled("pvp", data.pvp)) {
                    val pvpLimit = if (isBank) 1.8 else 2.0
                    val m = if (data.pvp in 0.1..pvpLimit) 1.0 else if (data.pvp > pvpLimit + 1.0) -1.0 else 0.0
                    indicatorResults.add(Triple("P/VP (Valuation)", 1.0, m))
                }
                if (isFilled("pl", data.pl)) {
                    val m = if (data.pl in 1.0..20.0) 1.0 else if (data.pl > 30.0 || data.pl < 0) -1.0 else 0.0
                    indicatorResults.add(Triple("P/L (Valuation)", 1.0, m))
                }
                if (isFilled("dy", data.dividendYield)) {
                    val m = if (data.dividendYield >= 0.05) 1.0 else if (data.dividendYield < 0.02) -1.0 else 0.0
                    indicatorResults.add(Triple("Dividend Yield", 1.0, m))
                }
                if (isFilled("payout", data.payout)) {
                    val m = if (data.payout in 0.2..0.9) 1.0 else if (data.payout > 1.0) -1.0 else 0.0
                    indicatorResults.add(Triple("Payout", 0.5, m))
                }
                if (isFilled("netEquity", data.netEquity)) {
                    val m = if (data.netEquity >= 1_000_000_000.0) 1.0 else 0.0
                    indicatorResults.add(Triple("Blue Chip (+1Bi)", 0.5, m))
                }
                if (isFilled("vol", data.avgDailyVolume)) {
                    val m = if (data.avgDailyVolume >= 1_000_000) 1.0 else if (data.avgDailyVolume < 100_000) -1.0 else 0.0
                    indicatorResults.add(Triple("Liquidez Diária", 0.5, m))
                }
            }
            is AssetData.Fii -> {
                val isPapel = data.sector.lowercase().contains("papel")
                if (isFilled("pvp", data.pvp)) {
                    val limit = if (isPapel) 1.03 else 1.08
                    val m = if (data.pvp <= limit) 1.0 else if (data.pvp > limit + 0.15) -1.0 else 0.0
                    indicatorResults.add(Triple("P/VP", 2.5, m))
                }
                if (isFilled("y12", data.yield12m)) {
                    val m = if (data.yield12m >= 0.09) 1.0 else if (data.yield12m < 0.06) -1.0 else 0.0
                    indicatorResults.add(Triple("Yield 12m", 2.5, m))
                }
                if (isFilled("y5", data.avgYield5Years)) {
                    val m = if (data.avgYield5Years >= 0.08) 1.0 else if (data.avgYield5Years < 0.05) -1.0 else 0.0
                    indicatorResults.add(Triple("Consistência Yield", 1.0, m))
                }
                if (sources.containsKey("tScore") || data.tenantScore > 0) {
                    val m = if (data.tenantScore >= 4) 1.0 else if (data.tenantScore <= 1) -1.0 else 0.0
                    indicatorResults.add(Triple("Inquilinos", 1.5, m))
                }
                if (sources.containsKey("lScore") || data.leverageScore > 0) {
                    val m = if (data.leverageScore >= 4) 1.0 else if (data.leverageScore <= 1) -1.0 else 0.0
                    indicatorResults.add(Triple("Alavancagem", 1.5, m))
                }
                if (!isPapel) {
                    if (isFilled("vac", data.vacancy)) { val m = if (data.vacancy < 0.10) 1.0 else if (data.vacancy > 0.25) -1.0 else 0.0; indicatorResults.add(Triple("Vacância", 0.5, m)) }
                    if (isFilled("prop", data.propertyCount.toDouble())) { val m = if (data.propertyCount > 5) 1.0 else if (data.propertyCount == 1) -1.0 else 0.0; indicatorResults.add(Triple("Qtd Imóveis", 0.5, m)) }
                    if (isFilled("walt", data.weightedLeaseTerm)) { val m = if (data.weightedLeaseTerm >= 4.0) 1.0 else if (data.weightedLeaseTerm < 2.0) -1.0 else 0.0; indicatorResults.add(Triple("WALT", 1.0, m)) }
                }
                if (sources.containsKey("mType")) { val m = if (data.managementType.lowercase() == "ativa") 1.0 else 0.0; indicatorResults.add(Triple("Gestão Ativa", 0.5, m)) }
                if (isFilled("mFee", data.managementFee)) { val m = if (data.managementFee <= 0.01) 1.0 else if (data.managementFee > 0.015) -1.0 else 0.0; indicatorResults.add(Triple("Taxa Adm", 0.5, m)) }
                if (isFilled("aum", data.aum)) { val m = if (data.aum >= 300_000_000) 1.0 else if (data.aum < 100_000_000) -1.0 else 0.0; indicatorResults.add(Triple("Patrimônio (PL)", 1.0, m)) }
            }
            is AssetData.Etf -> {
                if (isFilled("aFee", data.adminFee)) { val m = if (data.adminFee <= 0.005) 1.0 else if (data.adminFee > 0.01) -1.0 else 0.0; indicatorResults.add(Triple("Taxa Adm", 6.0, m)) }
                if (isFilled("hold", data.numberOfHoldings.toDouble())) { val m = if (data.numberOfHoldings > 50) 1.0 else if (data.numberOfHoldings < 20) -1.0 else 0.0; indicatorResults.add(Triple("Diversificação", 4.0, m)) }
            }
            is AssetData.Bdr -> {
                if (data is AssetData.Bdr) {
                    if (isFilled("dy", data.dividendYield)) { val m = if (data.dividendYield > 0.02) 1.0 else -1.0; indicatorResults.add(Triple("Dividendos", 5.0, m)) }
                    val p = if(data.parity=="1:1") 1.0 else 0.0; indicatorResults.add(Triple("Paridade", 5.0, p))
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
        val final = if (data.userScoreAverage && data.userScore > 0) (rawScore + data.userScore) / 2.0 else rawScore

        data.pros = prosList; data.cons = consList; data.neutros = neutrosList
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
