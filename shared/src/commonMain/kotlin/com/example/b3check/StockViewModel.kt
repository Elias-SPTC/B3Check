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

                // 1. Rentabilidade (ROE) - Max 2.5
                val roeMeta = when {
                    isBank || isInsurance || isHolding -> 0.12
                    isUtility || data.ticker.startsWith("VALE") || data.ticker.startsWith("VIVT") -> 0.10
                    else -> 0.14
                }
                
                if (data.roe >= roeMeta) { 
                    base += 2.5
                    prosList.add("Rentabilidade Superior: ROE de ${formatBR(data.roe*100)}%") 
                } else if (data.roe >= 0.08) {
                    base += 1.5
                    prosList.add("Rentabilidade Aceitável: ROE de ${formatBR(data.roe*100)}%")
                    consList.add("ROE abaixo da meta ideal de ${formatBR(roeMeta*100)}%")
                } else {
                    base += 0.5
                    consList.add("Baixa Rentabilidade: ROE insuficiente (${formatBR(data.roe*100)}%)")
                }

                // 2. Solvência e Risco - Max 2.5
                if (isBank) {
                    if (data.baselIndex >= 0.14) { 
                        base += 2.5
                        prosList.add("Basileia Sólida: ${formatBR(data.baselIndex*100)}%") 
                    } else {
                        base += 1.0
                        consList.add("Alerta de Basileia: ${formatBR(data.baselIndex*100)}% (abaixo de 14%)")
                    }
                } else if (isInsurance) {
                    if (data.roe > 0.15) {
                        base += 2.5
                        prosList.add("Seguradora com forte solvência/ROE")
                    } else {
                        base += 1.5
                        consList.add("Seguradora com rentabilidade/solvência moderada")
                    }
                } else {
                    val debtLimit = if (isUtility) 4.5 else 3.0
                    if (data.debtToEbitda <= 0 && data.currentPrice > 0) {
                        base += 2.5
                        prosList.add("Excelente Saúde: Caixa Líquido")
                    } else if (data.debtToEbitda < debtLimit) { 
                        base += 2.5
                        prosList.add("Endividamento Saudável: ${formatBR(data.debtToEbitda)}x EBITDA") 
                    } else {
                        base += 1.0
                        if (data.debtToEbitda > debtLimit + 1.5) {
                            consList.add("Alavancagem Crítica: ${formatBR(data.debtToEbitda)}x Dív/EBITDA")
                        } else {
                            consList.add("Dívida Pressionada: ${formatBR(data.debtToEbitda)}x EBITDA")
                        }
                    }
                }

                // 3. Eficiência e Crescimento - Max 2.0
                val marginLimit = if (data.ticker.startsWith("VIVT") || isBank) 0.08 else 0.12
                if (data.netMargin >= marginLimit || isBank) {
                    base += 1.0
                    prosList.add("Eficiência: Margem Líquida adequada")
                } else {
                    base += 0.5
                    consList.add("Margem Líquida Estreita: ${formatBR(data.netMargin*100)}%")
                }

                if (data.cagrProfit5Years >= 0.10) {
                    base += 1.0
                    prosList.add("Forte Crescimento de Lucro (CAGR 5a)")
                } else if (data.cagrProfit5Years >= 0 || data.ticker.startsWith("VALE")) {
                    base += 0.7
                    prosList.add("Lucratividade Resiliente")
                    if (data.cagrProfit5Years < 0.05) consList.add("Baixo Crescimento de Lucro")
                } else {
                    consList.add("Histórico Negativo: Lucros em Queda (CAGR)")
                }

                // 4. Valuation - Max 2.0
                val isGrowth = data.cagrProfit5Years > 0.15 && data.roe > 0.20
                if (data.pvp in 0.1..1.8 || (isGrowth && data.pvp < 8.0) || (isBank && data.roe > 0.20 && data.pvp < 4.0)) { 
                    base += 1.0
                    prosList.add("Preço/VP adequado ao perfil") 
                } else {
                    consList.add("Valuation Esticado: P/VP de ${formatBR(data.pvp)}")
                }

                if (data.pl in 1.0..18.0 || (isGrowth && data.pl < 35.0)) {
                    base += 1.0
                    prosList.add("Múltiplo P/L Atrativo")
                } else {
                    base += 0.5
                    if (data.pl > 25.0) consList.add("P/L Elevado: ${formatBR(data.pl)}x")
                    else if (data.pl <= 0) consList.add("P/L Negativo ou Indisponível")
                }

                // 5. Dividendos - Max 1.0
                if (data.dividendYield >= 0.05) { 
                    base += 1.0
                    prosList.add("Proventos Fortes: DY ${formatBR(data.dividendYield*100)}%") 
                } else if (data.dividendYield >= 0.025) {
                    base += 0.7
                    prosList.add("Pagadora Regular de Dividendos")
                } else {
                    consList.add("Dividend Yield Baixo: ${formatBR(data.dividendYield*100)}%")
                }
            }
            is AssetData.Fii -> {
                // 1. Valuation (P/VP) - Max 3.0
                if (data.pvp in 0.92..1.06) { base += 3.0; prosList.add("Preço Justo: P/VP de ${formatBR(data.pvp)}") }
                else if (data.pvp < 0.92) { base += 2.5; prosList.add("Desconto Patrimonial (Oportunidade)") }
                else { consList.add("Ágio Elevado: P/VP de ${formatBR(data.pvp)}") }

                // 2. Rendimentos (DY 12m) - Max 3.0
                if (data.yield12m >= 0.10) { base += 3.0; prosList.add("Rendimento Forte: DY ${formatBR(data.yield12m*100)}%") }
                else if (data.yield12m >= 0.07) { 
                    base += 1.5; prosList.add("Rendimento em linha com mercado") 
                    consList.add("DY Moderado: ${formatBR(data.yield12m*100)}%")
                } else {
                    consList.add("Rendimento Abaixo do Esperado: DY ${formatBR(data.yield12m*100)}%")
                }

                // 3. Qualidade (Manual Inquilino) - Max 2.0
                base += data.tenantScore * 0.4
                if (data.tenantScore >= 4) prosList.add("Qualidade: Portfólio/Inquilinos de Alto Nível")
                else if (data.tenantScore <= 2) consList.add("Risco: Concentração ou Inquilinos de Médio/Baixo Risco")

                // 4. Estrutura (Manual Alavancagem) - Max 2.0
                base += data.leverageScore * 0.4
                if (data.leverageScore >= 4) prosList.add("Estrutura: Baixa Alavancagem/Dívida Segura")
                else if (data.leverageScore <= 2) consList.add("Alerta: Alavancagem que exige monitoramento")
            }
            is AssetData.Etf -> {
                if (data.adminFee <= 0.003) { base += 4.0; prosList.add("Taxa Adm Baixíssima") }
                else if (data.adminFee <= 0.007) { base += 2.0; prosList.add("Taxa Adm Competitiva") }
                else { consList.add("Taxa de Administração Elevada: ${formatBR(data.adminFee*100)}%") }

                if (data.trackingError <= 0.015) { base += 3.0; prosList.add("Alta Fidelidade ao Índice") }
                else { consList.add("Erro de Aderência (Tracking Error) elevado") }

                if (data.aum >= 100_000_000) { base += 3.0; prosList.add("Alta Liquidez/Patrimônio") }
                else { base += 1.0; consList.add("Patrimônio Reduzido (Risco de Liquidez)") }
            }
            is AssetData.Bdr -> {
                if (data.dividendYield > 0.025) { base += 5.0; prosList.add("BDR Pagador de Proventos") }
                else { base += 3.0; consList.add("Sem foco em Dividendos (Crescimento)") }
                if (data.parity == "1:1") { base += 5.0; prosList.add("Paridade Direta (1:1)") }
                else { base += 4.0; consList.add("Paridade Fracionada: ${data.parity}") }
            }
        }

        // Verificações Globais de Dados
        if (data.currentPrice <= 0) consList.add("Dados: Preço de mercado não disponível")
        
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

    fun exportBackup() = db.exportBackup()
    fun importBackup(j: String) { viewModelScope.launch { if (db.importBackup(j)) loadAllAssets() } }

    fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    private fun formatBR(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%,.2f", v)
}
