package com.example.b3check

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ManualAssetDatabase(application)

    private val _uiState = MutableStateFlow<StockUiState>(StockUiState.Idle)
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    private val _allAssets = MutableStateFlow<List<AssetData>>(emptyList())
    val allAssets: StateFlow<List<AssetData>> = _allAssets.asStateFlow()

    private val _recommendations = MutableStateFlow<List<AssetData>>(emptyList())
    val recommendations: StateFlow<List<AssetData>> = _recommendations.asStateFlow()

    private val _portfolioAllocation = MutableStateFlow<List<Pair<AssetData, Double>>>(emptyList())
    val portfolioAllocation: StateFlow<List<Pair<AssetData, Double>>> = _portfolioAllocation.asStateFlow()

    init {
        loadAllAssets()
    }

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
        val t = ticker.trim().uppercase()
        if (t.isBlank()) return
        
        viewModelScope.launch {
            val data = createSkeleton(t, type)
            _uiState.value = StockUiState.Success(data, calculateScoreForAsset(data))
        }
    }

    private fun createSkeleton(t: String, type: String): AssetData {
        return when(type) {
            "FII" -> AssetData.Fii(t, t, 0.0)
            "ETF" -> AssetData.Etf(t, t, 0.0)
            "BDR" -> AssetData.Bdr(t, t, 0.0)
            else -> AssetData.Stock(t, t, 0.0)
        }
    }

    fun saveManualAsset(data: AssetData) {
        viewModelScope.launch {
            db.saveAsset(data)
            val score = calculateScoreForAsset(data)
            _uiState.value = StockUiState.Success(data, score)
            loadAllAssets()
        }
    }

    fun deleteAsset(ticker: String) {
        viewModelScope.launch {
            db.deleteAsset(ticker)
            _uiState.value = StockUiState.Idle
            loadAllAssets()
        }
    }

    private fun updateRecommendations(list: List<AssetData>) {
        _recommendations.value = list.filter { !it.isInPortfolio }
            .map { it to calculateScoreForAsset(it) }
            .sortedByDescending { it.second }.map { it.first }
    }

    private fun updatePortfolioAllocation(list: List<AssetData>) {
        val p = list.filter { it.isInPortfolio }
        val scored = p.map { it to calculateScoreForAsset(it) }
        val total = scored.sumOf { it.second }
        _portfolioAllocation.value = scored.map { (a, s) -> a to if (total > 0) (s / total) * 100.0 else 0.0 }
            .sortedByDescending { it.second }
    }

    fun calculateScoreForAsset(data: AssetData): Double {
        // Cálculo puramente local baseado nos indicadores preenchidos
        var score = 0.0
        when (data) {
            is AssetData.Stock -> {
                if (data.roe >= 0.15) score += 2.0
                if (data.dividendYield >= 0.06) score += 2.0
                if (data.debtToEbitda in 0.1..2.5) score += 2.0
            }
            is AssetData.Fii -> {
                if (data.pvp in 0.9..1.05) score += 3.0
                if (data.yield12m >= 0.09) score += 2.0
            }
            else -> score = 5.0
        }
        return score.coerceIn(0.0, 10.0)
    }

    fun getIntegrityWarnings(data: AssetData) = emptyList<String>()
    fun recalculateAllScores() { loadAllAssets() }
    fun exportBackup() = db.exportBackup()
    fun importBackup(j: String) { viewModelScope.launch { if (db.importBackup(j)) loadAllAssets() } }
}

sealed class StockUiState {
    data object Idle : StockUiState()
    data object Loading : StockUiState()
    data class Success(val data: AssetData, val score: Double) : StockUiState()
    data class Error(val message: String) : StockUiState()
}
