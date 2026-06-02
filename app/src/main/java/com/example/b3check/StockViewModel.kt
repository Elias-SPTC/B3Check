package com.example.b3check

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.sqrt

enum class SearchSource(val label: String) {
    MANUAL("Inteligente/Manual")
}

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ManualAssetDatabase(application)
    private val apiRepo = BrapiAssetRepository("8BPJ5K2iUYL59vbXQdK6Mt")
    private val scraperRepo = FundamentusScraperRepository()
    private val hybridRepo = HybridAssetRepository(apiRepo, scraperRepo)
    private val mockRepo = MockAssetRepository()

    private val _searchSource = MutableStateFlow(SearchSource.MANUAL)
    val searchSource: StateFlow<SearchSource> = _searchSource.asStateFlow()

    private val _uiState = MutableStateFlow<StockUiState>(StockUiState.Idle)
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    fun setSource(source: SearchSource) {
        _searchSource.value = source
    }

    fun analyzeTicker(ticker: String, manualType: String? = null) {
        val t = ticker.trim().uppercase()
        if (t.isBlank()) return

        _uiState.value = StockUiState.Loading

        viewModelScope.launch {
            try {
                Log.d("StockViewModel", "Iniciando análise inteligente para $t")
                
                var data: AssetData? = try { db.getAsset(t) } catch(e: Exception) { null }
                
                // Se não existe OU se campos vitais estão vazios, busca na internet
                val needsFetch = data == null || (data is AssetData.Stock && data.dividendYield5Years <= 0.0)
                
                if (needsFetch) {
                    var internetData = fetchFromRepo(hybridRepo, t)
                    
                    // Fallback para API se o scraper falhar
                    if (internetData == null) {
                        internetData = fetchFromRepo(apiRepo, t)
                    }

                    if (internetData != null) {
                        data = if (data == null) internetData else {
                            // Mescla dados da internet nos campos vazios do objeto do banco
                            if (data is AssetData.Stock && internetData is AssetData.Stock) {
                                val mergedSources = data.fieldSources?.toMutableMap() ?: mutableMapOf()
                                internetData.fieldSources?.forEach { (k, v) ->
                                    if (data.fieldSources?.get(k) == null) mergedSources[k] = v
                                }
                                data.copy(
                                    dividendYield5Years = if (data.dividendYield5Years <= 0.0) internetData.dividendYield5Years else data.dividendYield5Years,
                                    grahamPrice = if (data.grahamPrice <= 0.0) internetData.grahamPrice else data.grahamPrice,
                                    bazinPrice = if (data.bazinPrice <= 0.0) internetData.bazinPrice else data.bazinPrice,
                                    isInPortfolio = data.isInPortfolio
                                ).apply { fieldSources = mergedSources }
                            } else internetData
                        }
                    }
                }

                // Se mesmo assim for nulo, tenta MOCK como último recurso (Simulação)
                if (data == null) {
                    data = fetchFromRepo(mockRepo, t)
                }

                // Fallback final: Objeto vazio com tipo selecionado
                if (data == null && manualType != null) {
                    data = when(manualType) {
                        "FII" -> AssetData.Fii(t, t, 0.0, "FII")
                        "ETF" -> AssetData.Etf(t, t, 0.0, "ETF")
                        "BDR" -> AssetData.Bdr(t, t, 0.0, "BDR")
                        else -> AssetData.Stock(t, t, 0.0, "Ação")
                    }
                }

                if (data != null) {
                    db.saveAsset(data!!) // Persiste
                    val score = calculateScoreForAsset(data!!)
                    _uiState.value = StockUiState.Success(data!!, score)
                    loadAllAssets()
                } else {
                    handleError(ticker)
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Erro fatal na análise de $t", e)
                _uiState.value = StockUiState.Error("Ocorreu um erro interno.")
            }
        }
    }

    private suspend fun fetchFromRepo(repo: AssetRepository, ticker: String): AssetData? {
        return try {
            withTimeout(10000) {
                val result = repo.getAssetData(ticker)
                if (result == null) {
                    Log.d("StockViewModel", "Repositório ${repo.javaClass.simpleName} retornou NULL para $ticker")
                }
                result
            }
        } catch (e: Exception) {
            Log.e("StockViewModel", "Erro no repositório ${repo.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun handleError(ticker: String) {
        _uiState.value = StockUiState.Error("Não foi possível obter dados para $ticker.")
    }

    fun saveManualAsset(data: AssetData) {
        viewModelScope.launch {
            // Marca campos vindo do editor como USER
            val fieldKeys = when(data) {
                is AssetData.Stock -> listOf("name", "currentPrice", "lpa", "vpa", "roe", "dy", "dy5", "de", "ml", "pl", "pvp", "payout", "graham", "bazin", "valSource")
                is AssetData.Fii -> listOf("name", "currentPrice", "pvp", "vac", "y12", "y5", "prop", "aum", "mFee", "walt", "fType", "mType")
                is AssetData.Etf -> listOf("name", "currentPrice", "aFee", "te", "vol", "hold")
                is AssetData.Bdr -> listOf("name", "currentPrice", "dy", "par")
            }
            val newSources = data.fieldSources?.toMutableMap() ?: mutableMapOf()
            fieldKeys.forEach { newSources[it] = FieldSource.USER }
            data.fieldSources = newSources

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

    private fun updateRecommendations(list: List<AssetData>) {
        val recommended = list
            .filter { !it.isInPortfolio }
            .map { it to calculateScoreForAsset(it) }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        _recommendations.value = recommended
    }

    private fun updatePortfolioAllocation(list: List<AssetData>) {
        val portfolio = list.filter { it.isInPortfolio }
        val scoredPortfolio = portfolio.map { it to calculateScoreForAsset(it) }
        val totalScore = scoredPortfolio.sumOf { it.second }
        
        val allocation = if (totalScore > 0) {
            scoredPortfolio.map { (asset, score) ->
                asset to (score / totalScore) * 100.0
            }.sortedByDescending { it.second }
        } else {
            scoredPortfolio.map { it.first to 0.0 }
        }
        _portfolioAllocation.value = allocation
    }

    fun exportBackup(): String = db.exportBackup()
    
    fun importBackup(json: String) {
        viewModelScope.launch {
            if (db.importBackup(json)) {
                loadAllAssets()
            }
        }
    }

    private fun calculateScoreForAsset(data: AssetData): Double {
        return when (data) {
            is AssetData.Stock -> calculateStockScore(data)
            is AssetData.Fii -> calculateFiiScore(data)
            is AssetData.Etf -> calculateEtfScore(data)
            is AssetData.Bdr -> calculateBdrScore(data)
        }
    }

    private fun calculateStockScore(data: AssetData.Stock): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        // --- Critérios de Setor (Perenidade e Estabilidade) ---
        when (data.sector) {
            "Utilidade Pública" -> {
                score += 1.5
                p.add("Setor Perene: Utilidade Pública (Demanda estável)")
            }
            "Consumo Não Cíclico e Saúde" -> {
                score += 1.0
                p.add("Setor Resiliente: Consumo Não Cíclico/Saúde")
            }
            "Financeiro" -> {
                if (data.subSector == "Seguradoras") {
                    score += 0.5
                    p.add("Subsetor Estável: Seguradoras")
                }
            }
            "Consumo Cíclico" -> {
                if (data.debtToEquity > 0.8) {
                    score -= 1.0
                    c.add("Risco Cíclico: Alavancagem alta em setor sensível")
                }
            }
        }

        // --- Indicadores Quantitativos ---
        if (data.dividendYield5Years >= 0.06) {
            score += 1.0
            p.add("DY Histórico sólido (> 6% nos últimos 5 anos)")
        } else if (data.dividendYield5Years > 0) {
            c.add("DY Histórico abaixo de 6%")
        }

        val grahamPrice = sqrt(22.5 * data.lpa * data.vpa)
        if (data.currentPrice <= grahamPrice && grahamPrice > 0) {
            score += 1.0
            p.add("Preço abaixo do valor de Graham")
        } else if (grahamPrice > 0) {
            c.add("Valuation esticado (acima de Graham)")
        }

        if (data.pvp in 0.1..1.5) {
            score += 1.0
            p.add("P/VP atrativo (<= 1.5)")
        } else if (data.pvp > 2.0) {
            c.add("P/VP elevado (Acima de 2.0)")
        } else if (data.pvp <= 0) {
            c.add("P/VP não disponível ou negativo")
        }

        if (data.pl in 1.0..15.0) {
            score += 1.0
            p.add("P/L equilibrado (Preço/Lucro entre 1 e 15)")
        } else if (data.pl > 20.0 || data.pl < 0) {
            c.add("P/L fora da zona ideal (Alto ou Negativo)")
        }

        if (data.roe >= 0.15) {
            score += 1.0
            p.add("Rentabilidade sólida (ROE > 15%)")
        } else {
            c.add("ROE abaixo de 15%")
        }

        if (data.dividendYield >= 0.06) {
            score += 1.0
            p.add("Dividend Yield atrativo (> 6% a.a.)")
        } else if (data.dividendYield < 0.04) {
            c.add("DY atual baixo (Abaixo de 4%)")
        }

        if (data.netMargin >= 0.12) {
            score += 1.0
            p.add("Boa Margem Líquida (> 12%)")
        } else {
            c.add("Margem Líquida abaixo de 12%")
        }

        if (data.subSector == "Bancos") {
            if (data.baselIndex >= 0.14) {
                score += 2.0
                p.add("Basileia robusto (Segurança)")
            } else {
                c.add("Índice de Basileia abaixo de 14%")
            }
        } else {
            if (data.debtToEquity <= 0.8) {
                score += 2.0
                p.add("Baixa Dívida/Patrimônio (Saudável)")
            } else if (data.debtToEquity > 1.2) {
                score -= 1.0
                c.add("Alavancagem financeira elevada (> 1.2)")
            }
        }

        if (data.paidDividendsLast5Years) {
            score += 0.5
            p.add("Histórico de dividendos consistente")
        } else {
            c.add("Não pagou dividendos consistentemente")
        }

        if (data.payout in 0.3..0.8) {
            score += 0.5
            p.add("Payout saudável e sustentável")
        } else if (data.payout > 0.9) {
            c.add("Payout muito elevado (Risco de corte)")
        }

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }

    private fun calculateFiiScore(data: AssetData.Fii): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        val isPaper = data.sector == "Papel" || data.subSector.contains("Recebíveis") || data.subSector.contains("FOFs")
        val isShopping = data.subSector.contains("Shopping")

        // --- Critérios de Setor e Subsetor ---
        when (data.sector) {
            "Tijolo" -> {
                score += 0.5
                p.add("Ativo Real: Fundo de Tijolo")
                when (data.subSector) {
                    "Logística / Industrial" -> {
                        score += 0.5
                        p.add("Subsetor Perene: Logística (Contratos atípicos)")
                    }
                    "Shopping Centers" -> {
                        score += 0.5
                        p.add("Subsetor Resiliente: Shopping Centers")
                    }
                    "Agências bancárias", "Hospitais" -> {
                        score += 1.0
                        p.add("Contratos de Longo Prazo: ${data.subSector}")
                    }
                }
            }
            "Papel" -> {
                score += 0.5
                p.add("Fundo de Papel (Renda Fixa Imobiliária)")
            }
            "Híbridos" -> {
                score += 1.0
                p.add("Diversificação de estratégia (Híbrido)")
            }
        }

        if (data.managementType.contains("Ativa", ignoreCase = true)) {
            score += 0.5
            p.add("Gestão Ativa (Potencial de alpha)")
        }

        if (data.avgYield5Years >= 0.08) {
            score += 1.0
            p.add("DY Histórico sólido (> 8% nos últimos 5 anos)")
        }

        // P/VP é mais crítico em Papel (deve estar próximo a 1.0)
        if (isPaper) {
            if (data.pvp in 0.98..1.02) {
                score += 3.0
                p.add("P/VP ideal para Fundo de Papel")
            } else if (data.pvp > 1.05) {
                c.add("Ágio perigoso para Fundo de Papel")
            }
        } else {
            if (data.pvp in 0.92..1.03) {
                score += 3.0
                p.add("P/VP em zona de equilíbrio")
            } else if (data.pvp < 0.92 && data.pvp > 0) {
                score += 1.5
                p.add("Ativo com desconto patrimonial")
            }
        }

        // Vacância não se aplica a Papel
        if (!isPaper) {
            if (data.vacancy <= 0.05) {
                score += 1.5
                p.add("Ocupação excelente (Vacância < 5%)")
            } else if (data.vacancy > 0.15) {
                score -= 1.0
                c.add("Risco de vacância elevado (> 15%)")
            }
        }

        if (data.multiProperty && data.multiTenant) {
            score += 1.0
            p.add("Alta diversificação (Multi-imóvel/inquilino)")
        }

        // WALT não é métrica padrão de Shoppings
        if (!isShopping && !isPaper) {
            if (data.weightedLeaseTerm >= 4.0) {
                score += 1.0
                p.add("Contratos de longo prazo (WALT > 4 anos)")
            }
        }

        if (data.yield12m >= 0.09) {
            score += 2.0
            p.add("Dividend Yield atrativo (> 9%)")
        }
        
        if (data.managementFee <= 0.01) {
            score += 0.5
            p.add("Taxa de gestão competitiva")
        }

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }

    private fun calculateEtfScore(data: AssetData.Etf): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        if (data.adminFee <= 0.003) {
            score += 4.0
            p.add("Taxa de Administração competitiva")
        } else if (data.adminFee > 0.007) {
            c.add("Taxa de Administração elevada")
        }

        if (data.avgDailyVolume >= 2_000_000) {
            score += 1.5
            p.add("Liquidez robusta para negociação")
        } else {
            c.add("Baixa liquidez diária")
        }

        if (data.trackingError <= 0.005) {
            score += 2.0
            p.add("Alta fidelidade ao índice (Baixo Tracking Error)")
        }

        if (data.numberOfHoldings >= 50) {
            score += 1.0
            p.add("Boa diversificação interna")
        }

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }

    private fun calculateBdrScore(data: AssetData.Bdr): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        if (data.dividendYield >= 0.03) {
            score += 5.0
            p.add("Dividend Yield aceitável para BDR")
        }

        p.add("Exposição ao mercado internacional")

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }
}

sealed class StockUiState {
    data object Idle : StockUiState()
    data object Loading : StockUiState()
    data class Success(val data: AssetData, val score: Double) : StockUiState()
    data class Error(val message: String) : StockUiState()
}
