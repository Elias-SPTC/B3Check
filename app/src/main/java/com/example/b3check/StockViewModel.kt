package com.example.b3check

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

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

    fun resetAnalysis() { _uiState.value = StockUiState.Idle }

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
            "FII" -> AssetData.Fii(t, t, 0.0, "FII", "Tijolo")
            "ETF" -> AssetData.Etf(t, t, 0.0, "ETF", "ETF")
            "BDR" -> AssetData.Bdr(t, t, 0.0, "BDR", "BDR")
            else -> AssetData.Stock(t, t, 0.0, "Ação", "Geral")
        }
    }

    fun saveManualAsset(data: AssetData) {
        viewModelScope.launch {
            db.saveAsset(data)
            _uiState.value = StockUiState.Success(data, calculateScoreForAsset(data))
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
        val portfolio = list.filter { it.isInPortfolio }
        val activePortfolio = portfolio.filter { !it.isInert } // Ativos que não são inertes
        
        val scoredActive = activePortfolio.map { it to calculateScoreForAsset(it) }
        val totalActiveScore = scoredActive.sumOf { it.second }
        
        _portfolioAllocation.value = portfolio.map { asset ->
            if (asset.isInert) {
                asset to 0.0 // Ativos inertes têm peso ideal 0%
            } else {
                val score = scoredActive.find { it.first.ticker == asset.ticker }?.second ?: 0.0
                asset to if (totalActiveScore > 0) (score / totalActiveScore) * 100.0 else 0.0
            }
        }.sortedByDescending { it.second }
    }

    fun calculateScoreForAsset(data: AssetData): Double {
        var calculatedScore = 0.0
        val p = mutableListOf<String>(); val c = mutableListOf<String>()

        when (data) {
            is AssetData.Stock -> {
                val sec = data.sector.trim().lowercase()
                val sub = data.subSector.trim().lowercase()
                val isElite = sec.contains("utilidade") || sec.contains("telecom") || sub.contains("bancos") || sub.contains("seguradora")

                // 1. SETOR (1.0 pt)
                if (isElite) { calculatedScore += 1.0; p.add("Setor defensivo e perene") }
                else if (sec.contains("materiais") || sec.contains("saúde")) { calculatedScore += 0.5; p.add("Setor Resiliente") }
                else { c.add("Setor cíclico ou exposto a variações econômicas") }

                // 2. RENTABILIDADE - ROE (2.0 pts)
                val roeMin = if (isElite) 0.10 else 0.14
                if (data.roe >= roeMin + 0.05) { calculatedScore += 2.0; p.add("ROE Superior: ${formatScore(data.roe*100)}%") }
                else if (data.roe >= roeMin) { calculatedScore += 1.2; p.add("Rentabilidade sólida para o perfil") }
                else if (data.roe < 0) { calculatedScore -= 2.0; c.add("Operação com prejuízo") }
                else { c.add("ROE abaixo da meta do setor") }

                // 3. SEGURANÇA FINANCEIRA (2.5 pts)
                if (sub.contains("bancos")) {
                    if (data.baselIndex >= 0.14) { calculatedScore += 2.5; p.add("Basileia Robusto (>14%)") }
                    else { calculatedScore -= 1.0; c.add("Índice de Basileia abaixo do ideal") }
                } else {
                    val dLimit = if (isElite) 3.5 else 2.5
                    if (data.debtToEbitda <= 0 && data.currentPrice > 0) { calculatedScore += 2.5; p.add("Fortaleza: Empresa possui Caixa Líquido") }
                    else if (data.debtToEbitda <= dLimit) { calculatedScore += 2.5; p.add("Dívida sob controle (${formatScore(data.debtToEbitda)}x)") }
                    else if (data.debtToEbitda > 4.5) { calculatedScore -= 2.0; c.add("Alavancagem Perigosa (>4.5x)") }
                    
                    if (data.debtToEquity <= 0.8 && data.debtToEquity > 0) { calculatedScore += 0.5; p.add("Relação Dív/Patrimônio saudável") }
                    if (data.avgDailyVolume >= 1_000_000) { calculatedScore += 0.5; p.add("Alta Liquidez Diária") }
                }

                // 4. EFICIÊNCIA E CRESCIMENTO (1.5 pts)
                if (data.netMargin >= 0.12) { calculatedScore += 1.0; p.add("Alta Eficiência: Margem Líquida > 12%") }
                if (data.pl in 1.0..15.0) { calculatedScore += 0.5; p.add("P/L em patamar atrativo") }

                // 5. CRESCIMENTO (1.5 pts)
                if (data.cagrProfit5Years >= 0.10) { calculatedScore += 1.0; p.add("Crescimento de Lucro Sólido") }
                if (data.cagrRevenue5Years >= 0.10) { calculatedScore += 0.5; p.add("Crescimento de Receita Sólido") }
                else if (data.cagrProfit5Years < 0) { c.add("Histórico de lucro em queda") }

                // 6. DIVIDENDOS (1.5 pts)
                val dyMin = if (isElite) 0.055 else 0.045
                if (data.dividendYield >= dyMin) { calculatedScore += 1.0; p.add("Yield Atual atrativo: ${formatScore(data.dividendYield*100)}%") }
                if (data.dividendYield5Years >= 0.05) { calculatedScore += 0.5; p.add("Histórico sólido de dividendos") }
                if (data.payout > 0.95) { c.add("Payout Elevado: Risco de corte") }

                // 7. VALUATION (2.0 pts)
                val graham = if (data.grahamPrice > 0) data.grahamPrice else if (data.lpa > 0 && data.vpa > 0) sqrt(22.5 * data.lpa * data.vpa) else 0.0
                val bazin = if (data.bazinPrice > 0) data.bazinPrice else if (data.dividendYield5Years > 0 && data.currentPrice > 0) (data.dividendYield5Years * data.currentPrice) / 0.06 else 0.0
                
                if (data.currentPrice > 0 && (data.currentPrice <= graham || (bazin > 0 && data.currentPrice <= bazin))) {
                    calculatedScore += 1.5; p.add("Margem de Segurança (Graham/Bazin)")
                }
                if (data.pvp <= 1.2 && data.pvp > 0) { calculatedScore += 0.5; p.add("P/VP atrativo") }
                else if (data.pvp > 3.0) { c.add("Valuation Esticado (P/VP > 3)") }
            }
            is AssetData.Fii -> {
                val sub = data.subSector.trim().lowercase()
                val isPrime = sub.contains("shopping") || sub.contains("escritório") || sub.contains("logística")
                val isPaper = data.sector.lowercase().contains("papel") || sub.contains("recebíveis") || sub.contains("fofs")
                
                // 1. VALUATION P/VP (3.0 pts)
                if (data.pvp <= 0.82 && data.pvp > 0) { calculatedScore += 3.0; p.add("Oportunidade Rara: Desconto brutal") }
                else if (data.pvp in 0.88..1.03) { calculatedScore += 2.5; p.add("P/VP em zona ideal") }
                else { c.add("Preço fora da zona de conforto patrimonial") }

                // 2. RENDIMENTO (2.0 pts)
                val yieldMin = if (isPrime) 0.075 else 0.09
                if (data.yield12m >= yieldMin) { calculatedScore += 2.0; p.add("Rendimento sólido: DY de ${formatScore(data.yield12m*100)}%") }
                else { c.add("DY Anual abaixo do benchmark") }

                // 3. QUALIDADE E ESCALA (2.5 pts)
                if (!isPaper) {
                    val vacMax = if (isPrime) 0.16 else 0.08
                    if (data.vacancy <= vacMax) { calculatedScore += 1.5; p.add("Vacância controlada") }
                    else { calculatedScore -= 1.0; c.add("Risco de Vacância: ${formatScore(data.vacancy*100)}% desocupado") }
                    if (data.propertyCount >= 5) { calculatedScore += 1.0; p.add("Diversificação física adequada") }
                } else { calculatedScore += 2.5; p.add("Fundo de papel: Renda indexada") }

                // 4. SEGURANÇA E LIQUIDEZ (2.5 pts)
                if (data.tenantScore >= 4) { calculatedScore += 1.0; p.add("Alta qualidade de inquilinos") }
                if (data.avgDailyVolume >= 800_000) { calculatedScore += 1.0; p.add("Alta liquidez diária") }
                if (data.leverageScore >= 4) { calculatedScore += 0.5; p.add("Baixa Alavancagem") }
                else if (data.leverageScore <= 1 && data.leverageScore > 0) { calculatedScore -= 2.0; c.add("Alavancagem Perigosa") }
            }
            is AssetData.Etf -> {
                if (data.adminFee <= 0.003 && data.adminFee > 0) { calculatedScore += 3.0; p.add("Baixo custo de adm") }
                if (data.avgDailyVolume >= 2_000_000) { calculatedScore += 3.0; p.add("Alta liquidez de negociação") }
                if (data.numberOfHoldings >= 50) { calculatedScore += 2.0; p.add("Diversificação robusta") }
                if (data.trackingError <= 0.005 && data.trackingError > 0) { calculatedScore += 2.0; p.add("Baixo erro de aderência") }
            }
            is AssetData.Bdr -> {
                calculatedScore += 5.0; p.add("Exposição Internacional")
                if (data.dividendYield >= 0.03) { calculatedScore += 3.0; p.add("Dividend Yield em dólar") }
                calculatedScore += 2.0; p.add("Paridade estável")
            }
        }
        data.pros = p; data.cons = c
        val finalCalculated = calculatedScore.coerceIn(0.0, 10.0)

        return when {
            data.userScore > 0 && data.userScorePriority -> data.userScore
            data.userScore > 0 && !data.userScorePriority -> (data.userScore + finalCalculated) / 2.0
            else -> finalCalculated
        }
    }

    fun getIntegrityWarnings(data: AssetData): List<String> {
        val w = mutableListOf<String>()
        if (data is AssetData.Stock && data.currentPrice > 0 && data.lpa > 0 && data.pl > 0) {
            val calcPL = data.currentPrice / data.lpa
            if (abs(calcPL - data.pl) / data.pl > 0.20) w.add("P/L diverge do Preço/LPA")
        }
        return w
    }

    fun recalculateAllScores() {
        viewModelScope.launch {
            db.getAllAssets().forEach { asset ->
                calculateScoreForAsset(asset)
                db.saveAsset(asset)
            }
            loadAllAssets()
        }
    }

    fun exportBackup() = db.exportBackup()
    fun importBackup(j: String) { viewModelScope.launch { if (db.importBackup(j)) loadAllAssets() } }

    private fun formatScore(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%,.2f", v)
}

sealed class StockUiState {
    data object Idle : StockUiState()
    data object Loading : StockUiState()
    data class Success(val data: AssetData, val score: Double) : StockUiState()
    data class Error(val message: String) : StockUiState()
}
