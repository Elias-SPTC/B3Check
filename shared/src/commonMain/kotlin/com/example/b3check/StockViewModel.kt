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
                val isFinance = sub.contains("bancos") || sub.contains("seguradora") || sec.contains("financeiro") || 
                             data.ticker.uppercase().startsWith("ITUB") || data.ticker.uppercase().startsWith("BBAS") || 
                             data.ticker.uppercase().startsWith("SANB") || data.ticker.uppercase().startsWith("BBDC") ||
                             data.ticker.uppercase().startsWith("BBSE") || data.ticker.uppercase().startsWith("PSSA") ||
                             data.ticker.uppercase().startsWith("BPAC")
                
                val isUtility = sec.contains("utilidade") || sec.contains("pública") || sub.contains("elétrica") || sub.contains("saneamento")

                // 1. Rentabilidade (ROE)
                val roeMeta = when {
                    isFinance -> 0.12
                    isUtility || data.ticker.startsWith("VALE") || data.ticker.startsWith("VIVT") -> 0.10
                    else -> 0.14
                }
                
                if (data.roe >= roeMeta) { 
                    base += 2.5
                    prosList.add("Rentabilidade Superior: ROE de ${formatBR(data.roe*100)}%") 
                } else if (data.roe >= 0.08) {
                    base += 1.5
                    prosList.add("Rentabilidade Aceitável: ROE de ${formatBR(data.roe*100)}%")
                } else {
                    base += 0.5
                    consList.add("Baixa Rentabilidade: ROE abaixo de 8%")
                }

                // 2. Solvência e Risco
                if (isFinance) {
                    if (data.baselIndex >= 0.14 || (sub.contains("seguradora") && data.roe > 0.15)) { 
                        base += 2.5
                        prosList.add("Estrutura de Capital Sólida") 
                    } else {
                        base += 1.0
                        consList.add("Alerta de Solvência/Basileia: ${formatBR(data.baselIndex*100)}%")
                    }
                } else {
                    val debtLimit = if (isUtility) 4.5 else 3.0
                    if (data.debtToEbitda <= 0 && data.currentPrice > 0) {
                        base += 2.5
                        prosList.add("Saúde Financeira: Possui Caixa Líquido")
                    } else if (data.debtToEbitda < debtLimit) { 
                        base += 2.5
                        prosList.add("Endividamento Saudável: ${formatBR(data.debtToEbitda)}x EBITDA") 
                    } else if (data.debtToEbitda > debtLimit + 1.5) {
                        consList.add("Alavancagem Crítica: ${formatBR(data.debtToEbitda)}x Dív/EBITDA")
                    } else {
                        base += 1.0
                        consList.add("Dívida Pressionada: ${formatBR(data.debtToEbitda)}x EBITDA")
                    }
                }

                // 3. Eficiência e Crescimento
                val marginLimit = if (data.ticker.startsWith("VIVT")) 0.08 else 0.12
                if (data.netMargin >= marginLimit) {
                    base += 1.0
                    prosList.add("Eficiência: Margem Líquida de ${formatBR(data.netMargin*100)}%")
                } else if (isFinance) { base += 1.0 }
                else {
                    base += 0.5
                    consList.add("Margem Líquida apertada: ${formatBR(data.netMargin*100)}%")
                }

                if (data.cagrProfit5Years >= 0.10) {
                    base += 1.0
                    prosList.add("Forte Crescimento (CAGR 5a): ${formatBR(data.cagrProfit5Years*100)}%")
                } else if (data.cagrProfit5Years >= 0 || data.ticker.startsWith("VALE")) {
                    base += 0.7
                    prosList.add("Lucratividade Resiliente")
                } else {
                    consList.add("Histórico: Lucros em Queda (CAGR Negativo)")
                }

                // 4. Valuation
                val isGrowth = data.cagrProfit5Years > 0.15 && data.roe > 0.20
                if (data.pvp in 0.1..1.8 || (isGrowth && data.pvp < 8.0) || (isFinance && data.roe > 0.20 && data.pvp < 4.0)) { 
                    base += 1.0
                    prosList.add("Valuation adequado ao perfil") 
                } else {
                    consList.add("Valuation Esticado: P/VP de ${formatBR(data.pvp)}")
                }

                if (data.pl in 1.0..18.0 || (isGrowth && data.pl < 35.0)) {
                    base += 1.0
                    prosList.add("Múltiplo P/L Atrativo")
                } else if (data.pl > 25.0) {
                    consList.add("Múltiplo P/L Elevado: ${formatBR(data.pl)}x")
                } else { base += 0.5 }

                // 5. Dividendos
                if (data.dividendYield >= 0.05) { 
                    base += 1.0
                    prosList.add("Excelente Proventos: DY de ${formatBR(data.dividendYield*100)}%") 
                } else if (data.dividendYield >= 0.025) {
                    base += 0.7
                    prosList.add("Distribuição de Dividendos Regular")
                } else {
                    consList.add("Dividend Yield abaixo de 2.5%")
                }
            }
            is AssetData.Fii -> {
                val sub = (data.subSector ?: "").trim().lowercase()
                
                // 1. Valuation (P/VP)
                if (data.pvp in 0.92..1.06) { 
                    base += 4.0
                    prosList.add("Preço Justo: P/VP de ${formatBR(data.pvp)}") 
                } else if (data.pvp < 0.92) {
                    base += 3.5
                    prosList.add("Oportunidade: Ativo com Desconto Patrimonial")
                } else {
                    consList.add("Ágio Elevado: P/VP de ${formatBR(data.pvp)}")
                }

                // 2. Operacional
                val vacLimit = if (sub.contains("shopping") || sub.contains("escritório") || sub.contains("lajes")) 0.15 else 0.08
                if (data.vacancy <= vacLimit) { 
                    base += 2.0
                    prosList.add("Vacância sob controle (${formatBR(data.vacancy*100)}%)") 
                } else if (data.vacancy <= 0.25) {
                    base += 1.0
                    consList.add("Vacância em Observação: ${formatBR(data.vacancy*100)}%")
                } else {
                    consList.add("Vacância Crítica: ${formatBR(data.vacancy*100)}%")
                }
                
                if (data.leverageValue < 0.15) {
                    base += 1.0
                    prosList.add("Baixa Alavancagem Financeira")
                } else {
                    base += 0.5
                    consList.add("Fundo Alavancado: ${formatBR(data.leverageValue*100)}%")
                }

                // 3. Rendimentos
                if (data.yield12m >= 0.10) { 
                    base += 3.0
                    prosList.add("Rendimento Real Forte: DY ${formatBR(data.yield12m*100)}%") 
                } else if (data.yield12m >= 0.07) {
                    base += 1.5
                    prosList.add("Rendimento em linha com o mercado")
                } else {
                    consList.add("Rendimento abaixo do esperado para o setor")
                }
            }
            is AssetData.Etf -> {
                if (data.adminFee <= 0.003) { base += 4.0; prosList.add("Taxa Adm Baixíssima") }
                else if (data.adminFee <= 0.007) { base += 2.0; prosList.add("Taxa Adm Competitiva") }
                else { consList.add("Taxa de Administração Acima da Média") }

                if (data.trackingError <= 0.015) { base += 3.0; prosList.add("Alta Fidelidade ao Índice") }
                else { consList.add("Erro de Aderência (Tracking Error)") }

                if (data.aum >= 100_000_000) { base += 3.0; prosList.add("Fundo com Alta Liquidez/Patrimônio") }
                else if (data.aum > 0) { base += 1.0; consList.add("Patrimônio Reduzido") }
                else { consList.add("Dados de Patrimônio Ausentes") }
            }
            is AssetData.Bdr -> {
                if (data.dividendYield > 0.025) { base += 5.0; prosList.add("BDR Pagador de Proventos") }
                else { base += 3.0; consList.add("Foco em Valorização (Sem Dividendos)") }
                base += if (data.parity == "1:1") 5.0 else 4.0
            }
        }

        val final = when {
            data.userScorePriority && data.userScore > 0 -> data.userScore
            data.userScoreAverage && data.userScore > 0 -> (base + data.userScore) / 2.0
            else -> base
        }

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
                calculateScoreForAsset(it) // Atualiza Prós/Contras/Notas antes de salvar
                db.saveAsset(it) 
            }
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
