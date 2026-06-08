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
        val p = list.filter { it.isInPortfolio }
        val scored = p.map { it to calculateScoreForAsset(it) }
        val total = scored.sumOf { it.second }
        _portfolioAllocation.value = scored.map { (a, s) -> a to if (total > 0) (s / total) * 100.0 else 0.0 }
            .sortedByDescending { it.second }
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
                if (isElite) { calculatedScore += 1.0; p.add("Setor Elite: Alta previsibilidade e resiliência") }
                else { c.add("Setor Cíclico: Exposto a oscilações econômicas") }

                // 2. RENTABILIDADE (2.0 pts)
                val roeMeta = if (isElite) 0.08 else 0.13
                if (data.roe >= roeMeta + 0.07) { calculatedScore += 2.0; p.add("ROE Superior: ${formatScore(data.roe*100)}% (Eficiência Máxima)") }
                else if (data.roe >= roeMeta) { calculatedScore += 1.2; p.add("Rentabilidade Saudável para o perfil") }
                else if (data.roe < 0) { calculatedScore -= 2.0; c.add("Operação Crítica: Empresa com prejuízo") }
                else { c.add("ROE de ${formatScore(data.roe*100)}% está abaixo do ideal") }

                // 3. SEGURANÇA (2.5 pts)
                if (sub.contains("bancos")) {
                    if (data.baselIndex >= 0.14) { calculatedScore += 2.5; p.add("Basileia Robusto (>14%): Alta proteção de capital") }
                    else if (data.baselIndex >= 0.11) { calculatedScore += 1.5; p.add("Índice de Basileia adequado") }
                    else { calculatedScore -= 1.0; c.add("Alerta de Capital: Basileia de ${formatScore(data.baselIndex*100)}% está baixo") }
                } else {
                    val dLimit = if (isElite) 3.5 else 2.5
                    if (data.debtToEbitda <= 0 && data.currentPrice > 0) { calculatedScore += 2.5; p.add("Fortaleza: Empresa possui Caixa Líquido") }
                    else if (data.debtToEbitda <= dLimit) { calculatedScore += 2.5; p.add("Saúde Financeira: Dívida sob controle (${formatScore(data.debtToEbitda)}x)") }
                    else if (data.debtToEbitda > 4.5) { calculatedScore -= 2.0; c.add("Alavancagem Perigosa: Dív/EBITDA acima de 4.5x") }
                    else { c.add("Endividamento de ${formatScore(data.debtToEbitda)}x acima do ideal") }
                }

                // 4. EFICIÊNCIA OPERACIONAL (1.0 pt)
                if (data.netMargin >= 0.10 || (sub.contains("bancos") && data.roe >= 0.12)) { calculatedScore += 1.0; p.add("Operação Saudável e Lucrativa") }
                else { c.add("Margem Estreita ou Eficiência abaixo da meta") }

                // 5. DIVIDENDOS (1.5 pts)
                val dyMeta = if (isElite) 0.055 else 0.045
                if (data.dividendYield >= dyMeta) { calculatedScore += 1.5; p.add("Yield Atual Atrativo: ${formatScore(data.dividendYield*100)}%") }
                else if (data.dividendYield > 0) { calculatedScore += 0.7; p.add("Pagadora regular de proventos") }
                else { c.add("Renda passiva atual baixa ou inexistente") }

                // 6. VALUATION (2.0 pts)
                val graham = if (data.lpa > 0 && data.vpa > 0) sqrt(22.5 * data.lpa * data.vpa) else 0.0
                val bazin = if (data.dividendYield5Years > 0 && data.currentPrice > 0) (data.dividendYield5Years * data.currentPrice) / 0.06 else 0.0
                if (data.currentPrice > 0 && (data.currentPrice <= graham || (bazin > 0 && data.currentPrice <= bazin))) {
                    calculatedScore += 1.5; p.add("Margem de Segurança: Preço abaixo do Valor Justo (Graham/Bazin)")
                } else { c.add("Valuation: Preço atual acima das referências de desconto") }
                if (data.pvp <= 0.8 && data.pvp > 0) { calculatedScore += 0.5; p.add("Oportunidade: Desconto patrimonial expressivo") }
            }
            is AssetData.Fii -> {
                val sub = data.subSector.trim().lowercase()
                val isPrime = sub.contains("shopping") || sub.contains("escritório") || sub.contains("logística")
                val isPaper = data.sector.lowercase().contains("papel") || sub.contains("recebíveis") || sub.contains("fofs")
                
                // 1. VALUATION P/VP (3.0 pts)
                if (data.pvp <= 0.82 && data.pvp > 0) { calculatedScore += 3.0; p.add("Oportunidade Rara: Desconto patrimonial de ${formatScore((1-data.pvp)*100)}%") }
                else if (data.pvp in 0.88..1.03) { calculatedScore += 2.5; p.add("P/VP Ideal: Ativo no valor de face ou com desconto") }
                else { c.add("P/VP de ${formatScore(data.pvp)}x fora da zona de conforto") }

                // 2. RENDIMENTO (2.0 pts)
                val yieldMin = if (isPrime) 0.075 else 0.09
                if (data.yield12m >= yieldMin) { calculatedScore += 2.0; p.add("Rendimento Sólido: DY de ${formatScore(data.yield12m*100)}%") }
                else { c.add("DY Anual de ${formatScore(data.yield12m*100)}% está abaixo do benchmark") }

                // 3. QUALIDADE E ESCALA (2.5 pts)
                if (!isPaper) {
                    val vacMax = if (isPrime) 0.16 else 0.08
                    if (data.vacancy <= vacMax) { calculatedScore += 1.5; p.add("Vacância Controlada para a categoria") }
                    else { calculatedScore -= 1.0; c.add("Risco de Vacância: ${formatScore(data.vacancy*100)}% desocupado") }
                    if (data.propertyCount >= 5) { calculatedScore += 1.0; p.add("Portfólio com Diversificação Física") }
                    else { c.add("Alta Concentração: Poucos imóveis no fundo") }
                } else { calculatedScore += 2.5; p.add("Segurança de Recebíveis: Renda indexada e protegida") }

                // 4. SEGURANÇA E LIQUIDEZ (2.5 pts)
                if (data.tenantScore >= 4) { calculatedScore += 1.0; p.add("Qualidade de Inquilinos/Crédito Nota 4+") }
                else { c.add("Risco de Crédito/Inquilinos elevado ou incerto") }
                if (data.avgDailyVolume >= 600_000) { calculatedScore += 1.0; p.add("Liquidez robusta para negociação") }
                else { c.add("Baixa Liquidez Diária: Dificuldade de saída") }
                if (data.leverageScore >= 4) { calculatedScore += 0.5; p.add("Baixa Alavancagem: Perfil conservador") }
                else if (data.leverageScore <= 1 && data.leverageScore > 0) { calculatedScore -= 2.0; c.add("Alavancagem Perigosa") }
            }
            is AssetData.Etf -> {
                // Inteligência para ETFs (10.0 pts)
                if (data.adminFee <= 0.003 && data.adminFee > 0) { calculatedScore += 2.5; p.add("Baixo Custo: Taxa de Adm de ${formatScore(data.adminFee*100)}%") }
                else { c.add("Taxa de Adm de ${formatScore(data.adminFee*100)}% acima do ideal para ETFs passivos") }
                
                if (data.avgDailyVolume >= 1_000_000) { calculatedScore += 2.5; p.add("Alta Liquidez: Volume diário robusto") }
                else { c.add("Liquidez de negociação reduzida") }
                
                if (data.numberOfHoldings >= 50) { calculatedScore += 2.5; p.add("Excelente Diversificação: ${data.numberOfHoldings} ativos") }
                else { c.add("Fundo Concentrado: Apenas ${data.numberOfHoldings} ativos") }
                
                if (data.trackingError <= 0.005 && data.trackingError > 0) { calculatedScore += 2.5; p.add("Alta Fidelidade: Baixo erro de aderência ao índice") }
                else { c.add("Aderência Insuficiente: Tracking Error elevado") }
            }
            is AssetData.Bdr -> {
                // Inteligência para BDRs (10.0 pts)
                calculatedScore += 4.0; p.add("Exposição Internacional: Diversificação em moeda forte")
                if (data.dividendYield >= 0.03) { calculatedScore += 3.0; p.add("Renda em Dólar: DY de ${formatScore(data.dividendYield*100)}%") }
                else { c.add("Baixa Renda por Dividendos") }
                calculatedScore += 3.0; p.add("Paridade: ${data.parity}")
            }
            else -> calculatedScore = 5.0
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
            if (abs(calcPL - data.pl) / data.pl > 0.20) w.add("P/L informado diverge do Preço/LPA")
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
