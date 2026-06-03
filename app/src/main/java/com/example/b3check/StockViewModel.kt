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
                val needsFetch = data == null || 
                                (data is AssetData.Stock && data.dividendYield5Years <= 0.0) ||
                                (data is AssetData.Fii && data.leverageValue <= 0.0)
                
                if (needsFetch) {
                    var internetData = fetchFromRepo(hybridRepo, t)
                    
                    if (internetData == null) {
                        internetData = fetchFromRepo(apiRepo, t)
                    }

                    // SÓ prossegue se os dados da internet forem ÚTEIS (Preço > 0)
                    if (internetData != null && internetData.currentPrice > 0) {
                        data = if (data == null) internetData else {
                            // MESCLAGEM ULTRA-SEGURA: Preserva o status da carteira e edições manuais
                            when {
                                data is AssetData.Stock && internetData is AssetData.Stock -> {
                                    val mergedSources = data.fieldSources?.toMutableMap() ?: mutableMapOf()
                                    internetData.fieldSources?.forEach { (k, v) ->
                                        if (data.fieldSources?.get(k) == null) mergedSources[k] = v
                                    }
                                    data.copy(
                                        name = if (data.name == data.ticker) internetData.name else data.name,
                                        currentPrice = internetData.currentPrice,
                                        dividendYield5Years = if (data.dividendYield5Years <= 0.0) internetData.dividendYield5Years else data.dividendYield5Years,
                                        grahamPrice = if (data.grahamPrice <= 0.0) internetData.grahamPrice else data.grahamPrice,
                                        bazinPrice = if (data.bazinPrice <= 0.0) internetData.bazinPrice else data.bazinPrice,
                                        sector = if (data.sector.isBlank() || data.sector == "Ação") internetData.sector else data.sector,
                                        subSector = if (data.subSector.isBlank()) internetData.subSector else data.subSector
                                    ).apply { fieldSources = mergedSources }
                                }
                                data is AssetData.Fii && internetData is AssetData.Fii -> {
                                    val mergedSources = data.fieldSources?.toMutableMap() ?: mutableMapOf()
                                    internetData.fieldSources?.forEach { (k, v) ->
                                        if (data.fieldSources?.get(k) == null) mergedSources[k] = v
                                    }
                                    data.copy(
                                        name = if (data.name == data.ticker) internetData.name else data.name,
                                        currentPrice = internetData.currentPrice,
                                        pvp = if (data.pvp <= 0.0) internetData.pvp else data.pvp,
                                        yield12m = if (data.yield12m <= 0.0) internetData.yield12m else data.yield12m,
                                        leverageValue = if (data.leverageValue <= 0.0) internetData.leverageValue else data.leverageValue,
                                        aum = if (data.aum <= 0.0) internetData.aum else data.aum,
                                        sector = if (data.sector.isBlank() || data.sector == "FII") internetData.sector else data.sector,
                                        subSector = if (data.subSector.isBlank()) internetData.subSector else data.subSector
                                    ).apply { fieldSources = mergedSources }
                                }
                                else -> data // Tipos diferentes? Confia no banco local.
                            }
                        }
                    } else if (data == null) {
                        // Se for um ativo novo e a internet falhou/veio zerada, força erro para não criar lixo
                        _uiState.value = StockUiState.Error("Dados não encontrados para o ticker $t")
                        return@launch
                    }
                }

                // Se for um ativo novo e nada foi encontrado, tenta MOCK como último recurso (Simulação)
                if (data == null) {
                    data = fetchFromRepo(mockRepo, t)
                }

                // Fallback final: Objeto vazio se o usuário selecionou o tipo mas a internet falhou
                if (data == null && manualType != null) {
                    data = when(manualType) {
                        "FII" -> AssetData.Fii(t, t, 0.0, "FII", "", leverageScore = 0, tenantScore = 0)
                        "ETF" -> AssetData.Etf(t, t, 0.0, "ETF", "ETF")
                        "BDR" -> AssetData.Bdr(t, t, 0.0, "BDR", "BDR")
                        else -> AssetData.Stock(t, t, 0.0, "Ação", "", debtToEbitda = 0.0)
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

    fun calculateScoreForAsset(data: AssetData): Double {
        // Se for um FII e a nota de alavancagem for zero, tenta deduzir a nota do valor bruto da internet
        if (data is AssetData.Fii && data.leverageScore == 0 && data.leverageValue > 0) {
            val deducedScore = when {
                data.leverageValue < 0.05 -> 5
                data.leverageValue < 0.15 -> 4
                data.leverageValue < 0.25 -> 3
                data.leverageValue < 0.40 -> 2
                else -> 1
            }
            // Temporariamente ajusta o score para o cálculo preencher Prós/Contras corretamente
            val updatedData = data.copy(leverageScore = deducedScore)
            val score = calculateFiiScore(updatedData)
            data.pros = updatedData.pros
            data.cons = updatedData.cons
            return score
        }
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
            "Utilidade Pública", "Telecomunicações" -> {
                score += 1.5
                p.add("Setor Perene: Alta previsibilidade e demanda estável")
            }
            "Consumo Não Cíclico e Saúde" -> {
                score += 1.0
                p.add("Setor Resiliente: Consumo Não Cíclico/Saúde")
            }
            "Petróleo e Gás" -> {
                score += 0.5
                p.add("Setor Estratégico: Petróleo, Gás e Biocombustíveis")
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
        } else if (data.dividendYield5Years >= 0.04) {
            score += 0.5
            p.add("DY Histórico consistente (entre 4% e 6%)")
        } else if (data.dividendYield5Years > 0) {
            c.add("DY Histórico baixo (abaixo de 4%)")
        } else {
            c.add("Não possui histórico de dividendos")
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

        if (data.subSector != "Bancos") {
            if (data.netMargin >= 0.12) {
                score += 1.0
                p.add("Boa Margem Líquida (> 12%)")
            } else if (data.netMargin > 0) {
                c.add("Margem Líquida abaixo de 12%")
            }
        }

        if (data.subSector == "Bancos") {
            if (data.baselIndex >= 0.14) {
                score += 2.0
                p.add("Basileia robusto (Segurança)")
            } else if (data.baselIndex > 0) {
                c.add("Índice de Basileia abaixo de 14%")
            }
        } else {
            // Alavancagem Industrial/Comercial
            if (data.debtToEquity <= 0.8 && data.debtToEbitda <= 2.0) {
                score += 2.0
                p.add("Baixa Alavancagem (Saudável)")
            }
            
            if (data.debtToEbitda > 3.5) {
                score -= 2.0
                c.add("Alavancagem Perigosa: Dívida/EBITDA > 3.5x")
            } else if (data.debtToEbitda > 2.5) {
                score -= 1.0
                c.add("Alavancagem Elevada: Dívida/EBITDA > 2.5x")
            }

            if (data.debtToEquity > 1.2) {
                score -= 1.0
                c.add("Dívida/Patrimônio elevada (> 1.2)")
            }
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

        val subSector = data.subSector ?: ""
        val isPaper = data.sector == "Papel" || subSector.contains("Recebíveis") || subSector.contains("FOFs")
        val isShopping = subSector.contains("Shopping")

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
            } else if (data.pvp < 0.95 && data.pvp > 0) {
                c.add("Desconto excessivo (Pode indicar risco no crédito)")
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

        if (data.propertyCount >= 10) {
            score += 0.7
            p.add("Excelente portfólio físico (10+ imóveis)")
        } else if (data.propertyCount >= 5) {
            score += 0.3
            p.add("Bom portfólio físico (5-9 imóveis)")
        } else if (data.sector == "Tijolo") {
            c.add("Risco de concentração física (< 5 imóveis)")
        }

        // --- Diversificação de Inquilinos (Novo Sistema) ---
        val tenantLabel = when {
            isPaper -> "Nota Devedores"
            subSector.contains("FOFs") -> "Nota Carteira"
            else -> "Nota Inquilino"
        }
        
        when (data.tenantScore) {
            1 -> {
                c.add("$tenantLabel 1: Concentração Crítica (Risco Altíssimo)")
            }
            2 -> {
                score += 0.4
                c.add("$tenantLabel 2: Baixa Diversificação (Risco Alto)")
            }
            3 -> {
                score += 0.8
                p.add("$tenantLabel 3: Diversificação Moderada (Risco Médio)")
            }
            4 -> {
                score += 1.2
                p.add("$tenantLabel 4: Boa Diversificação (Risco Baixo)")
            }
            5 -> {
                score += 2.0
                p.add("$tenantLabel 5: Excelente/Pulverizado (Risco Mínimo)")
            }
        }

        // WALT não é métrica padrão de Shoppings
        if (!isShopping && !isPaper) {
            if (data.weightedLeaseTerm >= 4.0) {
                score += 1.0
                p.add("Contratos de longo prazo (WALT > 4 anos)")
            } else if (data.weightedLeaseTerm > 0) {
                c.add("WALT baixo (Contratos vencendo em breve)")
            }
        }

        if (data.yield12m >= 0.09) {
            score += 2.0
            p.add("Dividend Yield atrativo (> 9%)")
        } else if (data.yield12m > 0) {
            c.add("Dividend Yield abaixo do ideal (< 9%)")
        }

        // --- Risco de Alavancagem em FII (Novo Sistema 1-5) ---
        when (data.leverageScore) {
            1 -> {
                score -= 2.0
                c.add("Alavancagem Crítica (> 40% do Ativo): Risco Altíssimo")
            }
            2 -> {
                score -= 1.0
                c.add("Alavancagem Alta (25-40% do Ativo): Risco Elevado")
            }
            3 -> {
                // Neutro
                p.add("Alavancagem Moderada (15-25%): Risco Médio")
            }
            4 -> {
                score += 0.5
                p.add("Alavancagem Baixa (5-15%): Saudável")
            }
            5 -> {
                score += 1.0
                p.add("Alavancagem Mínima (< 5%): Excelente Solidez")
            }
        }
        
        if (data.managementFee <= 0.01 && data.managementFee > 0) {
            score += 0.5
            p.add("Taxa de gestão competitiva")
        } else if (data.managementFee > 0.015) {
            c.add("Taxa de gestão elevada")
        }

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }

    private fun calculateEtfScore(data: AssetData.Etf): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        if (data.adminFee <= 0.003 && data.adminFee > 0) {
            score += 4.0
            p.add("Taxa de Administração competitiva")
        } else if (data.adminFee > 0.007) {
            c.add("Taxa de Administração elevada")
        }

        if (data.avgDailyVolume >= 2_000_000) {
            score += 1.5
            p.add("Liquidez robusta para negociação")
        } else if (data.avgDailyVolume > 0) {
            c.add("Baixa liquidez diária")
        }

        if (data.trackingError <= 0.005 && data.trackingError > 0) {
            score += 2.0
            p.add("Alta fidelidade ao índice (Baixo Tracking Error)")
        } else if (data.trackingError > 0.015) {
            c.add("Tracking Error elevado (Fidelidade baixa)")
        }

        if (data.numberOfHoldings >= 50) {
            score += 1.0
            p.add("Boa diversificação interna")
        } else if (data.numberOfHoldings > 0) {
            c.add("Carteira de ativos restrita")
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
            p.add("Dividend Yield atrativo para BDR")
        } else if (data.dividendYield > 0) {
            c.add("Dividend Yield baixo")
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
