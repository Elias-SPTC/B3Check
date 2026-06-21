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
                
                // Só salva se NÃO for uma mensagem de erro (404, 429, etc)
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
                    
                    saveManualAsset(updated)
                    _aiStatus.value = "Concluído"
                } else {
                    // Mostra o erro na tela temporariamente mas NÃO salva no banco
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
        // Calcula antes de salvar para garantir que prós/contras entrem no JSON do banco
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
            data.cons = emptyList()
            return data.userScore
        }
        
        var base = 0.0
        val prosList = mutableListOf<String>()
        val consList = mutableListOf<String>()

        when (data) {
            is AssetData.Stock -> {
                val sec = (data.sector ?: "").trim().lowercase()
                val sub = (data.subSector ?: "").trim().lowercase()
                val isBank = sub.contains("banco")
                val isInsurance = sub.contains("seguradora")
                val isHolding = sub.contains("holding")
                val isUtility = sec.contains("utilidade") || sec.contains("pública") || sub.contains("elétrica") || sub.contains("saneamento")

                // 1. Rentabilidade (ROE)
                val roeMeta = when {
                    isBank || isInsurance || isHolding -> 0.12
                    isUtility || data.ticker.startsWith("VALE") || data.ticker.startsWith("VIVT") -> 0.10
                    else -> 0.14
                }
                if (data.roe >= roeMeta) { base += 2.0; prosList.add("ROE Superior: ${formatBR(data.roe*100)}%") }
                else { base += 1.0; consList.add("ROE abaixo da meta ideal") }

                // 2. Solvência
                if (isBank) {
                    if (data.baselIndex >= 0.14) { base += 2.0; prosList.add("Basileia Sólida") }
                    else { base += 0.5; consList.add("Alerta de Basileia: ${formatBR(data.baselIndex*100)}%") }
                } else {
                    val debtLimit = if (isUtility) 4.5 else 3.0
                    var solvPts = 0.0
                    if (data.debtToEbitda < debtLimit) { solvPts += 1.0; prosList.add("Dív/EBITDA Saudável") }
                    else { consList.add("Dívida/EBITDA Elevada: ${formatBR(data.debtToEbitda)}x") }

                    if (data.debtToEquity < 1.0) { solvPts += 1.0; prosList.add("Dív/Patrimônio Sob Controle") }
                    else { consList.add("Dív/Patrimônio Elevada: ${formatBR(data.debtToEquity)}") }
                    
                    base += solvPts.coerceAtMost(2.0)
                }

                // 3. Eficiência e Crescimento
                if (data.netMargin > 0.10 || isBank) { base += 1.0; prosList.add("Boa Eficiência Operacional") }
                else { consList.add("Margem Líquida Estreita") }

                if (data.cagrProfit5Years >= 0.10) { base += 1.0; prosList.add("Forte Crescimento de Lucro") }
                else if (data.cagrProfit5Years >= 0) { base += 0.5; prosList.add("Lucratividade Resiliente") }
                else { consList.add("Histórico: Lucros em Queda") }

                if (data.cagrRevenue5Years >= 0.10) { base += 0.5; prosList.add("Crescimento de Receita Sólido") }
                else if (data.cagrRevenue5Years < 0.05) { consList.add("Crescimento de Receita Baixo/Nulo") }

                // 4. Valuation
                if (data.pvp in 0.1..2.0) { base += 1.0; prosList.add("Preço/VP Atrativo") }
                else { consList.add("Valuation Esticado (P/VP): ${formatBR(data.pvp)}") }

                if (data.pl in 1.0..20.0) { base += 1.0; prosList.add("Múltiplo P/L Saudável") }
                else { base += 0.5; consList.add("Múltiplo P/L Fora da Faixa Ideal: ${formatBR(data.pl)}x") }

                // 5. Dividendos e Payout
                var divPts = 0.0
                if (data.dividendYield >= 0.05) { divPts += 0.5; prosList.add("DY Atual Forte") }
                else { consList.add("DY Atual Abaixo de 5%") }

                if (data.dividendYield5Years >= 0.05) { divPts += 0.5; prosList.add("Excelente Histórico de Proventos") }
                else { consList.add("Falta de Consistência nos Proventos (5a)") }
                base += divPts

                if (data.payout in 0.2..0.9) { base += 0.5; prosList.add("Payout Sustentável: ${formatBR(data.payout*100)}%") }
                else if (data.payout > 0.9) { consList.add("Payout Elevado (Risco p/ Reservas): ${formatBR(data.payout*100)}%") }
                else { consList.add("Payout Baixo ou Retenção Excessiva: ${formatBR(data.payout*100)}%") }
                
                if (data.netEquity >= 1_000_000_000.0) { base += 0.5; prosList.add("Empresa de Grande Porte (Blue Chip)") }
                
                if (data.avgDailyVolume > 0 && data.avgDailyVolume < 1_000_000) consList.add("Baixa Liquidez Diária (Ações)")
            }
            is AssetData.Fii -> {
                // 1. Valuation
                if (data.pvp in 0.92..1.06) { base += 2.0; prosList.add("Preço Justo (P/VP)") }
                else if (data.pvp < 0.92) { base += 1.5; prosList.add("Oportunidade (Desconto)") }
                else { consList.add("Ágio Elevado: P/VP ${formatBR(data.pvp)}") }

                // 2. Rendimentos
                var yldPts = 0.0
                if (data.yield12m >= 0.09) yldPts += 1.5
                if (data.avgYield5Years >= 0.08) yldPts += 1.0
                if (yldPts >= 2.0) prosList.add("Rendimentos Fortes e Consistentes")
                else consList.add("Rendimento ou Consistência abaixo do ideal")
                base += yldPts

                // 3. Qualitativo (Manual)
                base += data.tenantScore * 0.3 // Max 1.5
                base += data.leverageScore * 0.3 // Max 1.5
                if (data.tenantScore >= 4) prosList.add("Excelente Mix de Inquilinos")
                if (data.leverageScore >= 4) prosList.add("Alavancagem Sob Controle")

                // 4. Operacional
                if (data.vacancy < 0.10) { base += 0.5; prosList.add("Baixa Vacância") }
                else { consList.add("Vacância em Alerta: ${formatBR(data.vacancy*100)}%") }

                if (data.propertyCount > 5) { base += 0.5; prosList.add("Portfólio Multi-Propriedade") }
                if (data.managementType.lowercase() == "ativa") { base += 0.5; prosList.add("Gestão Ativa") }
                
                if (data.aum < 300_000_000 && data.aum > 0) consList.add("Fundo de Pequeno Porte (Risco de Liquidez)")
                if (data.avgDailyVolume > 0 && data.avgDailyVolume < 1_000_000) consList.add("Baixa Liquidez Diária")
            }
            is AssetData.Etf -> {
                if (data.adminFee <= 0.005) { base += 4.0; prosList.add("Taxa Adm Baixa") }
                else { consList.add("Taxa Adm Elevada") }
                
                if (data.trackingError <= 0.02) { base += 3.0; prosList.add("Alta Fidelidade ao Índice") }
                
                if (data.numberOfHoldings > 50) { base += 2.0; prosList.add("Alta Diversificação (+50 ativos)") }
                
                if (data.aum >= 100_000_000) { base += 1.0; prosList.add("Alta Liquidez/AUM") }
                else { consList.add("Patrimônio Reduzido") }
                if (data.avgDailyVolume > 0 && data.avgDailyVolume < 1_000_000) consList.add("Baixa Liquidez Diária")
            }
            is AssetData.Bdr -> {
                if (data.dividendYield > 0.02) { base += 5.0; prosList.add("BDR Pagador") }
                if (data.parity == "1:1") { base += 5.0; prosList.add("Paridade Direta") }
                else { base += 3.0; consList.add("Paridade Fracionada") }
            }
        }

        val rawScore = base.coerceIn(0.0, 10.0)
        val final = if (data.userScoreAverage && data.userScore > 0) (rawScore + data.userScore) / 2.0 else rawScore

        data.pros = prosList
        data.cons = consList
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
