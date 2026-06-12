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
        viewModelScope.launch {
            db.saveAsset(data)
            loadAllAssets()
            if ((_uiState.value as? StockUiState.Success)?.data?.ticker == data.ticker) {
                _uiState.value = StockUiState.Success(data, calculateScoreForAsset(data))
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
        val activePortfolio = portfolio.filter { !it.isInert } // Ativos que não são inertes

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
        if (data.userScorePriority && data.userScore > 0) return data.userScore
        
        var base = 0.0
        val prosList = mutableListOf<String>()
        val consList = mutableListOf<String>()

        when (data) {
            is AssetData.Stock -> {
                val sec = (data.sector ?: "").trim().lowercase()
                val sub = (data.subSector ?: "").trim().lowercase()
                val isBank = sub.contains("bancos") || sec.contains("financeiro") || data.ticker.uppercase().startsWith("ITUB") || data.ticker.uppercase().startsWith("BBAS") || data.ticker.uppercase().startsWith("SANB") || data.ticker.uppercase().startsWith("BBDC")
                
                // ROE
                val roeMeta = if (isBank) 0.08 else 0.14
                if (data.roe >= roeMeta) { base += 2.5; prosList.add("ROE forte (>=${formatBR(roeMeta*100)}%)") }
                else consList.add("ROE baixo (<${formatBR(roeMeta*100)}%)")

                // Solvência
                if (isBank) {
                    if (data.baselIndex >= 0.14) { base += 2.5; prosList.add("Basileia sólida (>=14%)") }
                    else consList.add("Basileia fraca (<14%)")
                } else {
                    if (data.debtToEbitda < 3.5) { base += 2.5; prosList.add("Dívida/EBITDA controlada (<3.5x)") }
                    else consList.add("Alavancagem alta (>3.5x Dív/EBITDA)")
                }

                // Crescimento (CAGR)
                if (data.cagrProfit5Years > 0.05) { base += 1.5; prosList.add("Crescimento Lucro >5% aa") }
                
                // Valuation (Deep Value)
                if (data.pvp > 0 && data.pvp < 0.75) { base += 3.5; prosList.add("Deep Value (P/VP < 0.75)") }
                else if (data.pvp in 0.75..1.5) { base += 2.0; prosList.add("Preço Justo (P/VP 0.75-1.5)") }
                
                // Dividendos
                if (data.dividendYield >= 0.06) { base += 1.0; prosList.add("DY robusto (>6%)") }
            }
            is AssetData.Fii -> {
                val sub = data.subSector.trim().lowercase()
                
                // P/VP
                if (data.pvp in 0.85..1.05) { base += 4.0; prosList.add("Preço atrativo (P/VP próx 1.0)") }
                else if (data.pvp < 0.85) { base += 2.0; consList.add("Desconto excessivo (P/VP < 0.85)") }
                else consList.add("Ágio elevado (P/VP > 1.05)")

                // Vacância
                val vacLimit = if (sub.contains("shopping") || sub.contains("escritório") || sub.contains("lajes")) 0.12 else 0.08
                if (data.vacancy <= vacLimit) { base += 3.0; prosList.add("Vacância controlada (<${formatBR(vacLimit*100)}%)") }
                else consList.add("Vacância alta (>${formatBR(vacLimit*100)}%)")

                // Yield
                if (data.yield12m >= 0.09) { base += 3.0; prosList.add("Yield real forte (>=9%)") }
            }
            is AssetData.Etf -> {
                if (data.adminFee <= 0.005) { base += 5.0; prosList.add("Taxa adm baixa (<=0.5%)") }
                if (data.trackingError <= 0.02) { base += 5.0; prosList.add("Tracking Error baixo") }
            }
            is AssetData.Bdr -> {
                if (data.dividendYield > 0.02) { base += 5.0; prosList.add("Pagador de dividendos") }
                else base += 3.0
            }
        }

        val final = if (data.userScore > 0 && !data.userScorePriority) (base + data.userScore) / 2.0 else base
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
            list.forEach { db.saveAsset(it) }
            loadAllAssets()
        }
    }

    fun exportBackup() = db.exportBackup()
    fun importBackup(j: String) { viewModelScope.launch { if (db.importBackup(j)) loadAllAssets() } }

    fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    private fun formatScore(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%,.2f", v)
}
