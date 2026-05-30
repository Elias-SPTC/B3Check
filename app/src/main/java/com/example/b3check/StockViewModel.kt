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
    BRAPI("Brapi"),
    FUNDAMENTUS("Fundamentus"),
    HYBRID("Híbrido"),
    MANUAL("Manual"),
    MOCK("Simulação")
}

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ManualAssetDatabase(application)
    private val apiRepo = BrapiAssetRepository("8BPJ5K2iUYL59vbXQdK6Mt")
    private val scraperRepo = FundamentusScraperRepository()
    private val hybridRepo = HybridAssetRepository(apiRepo, scraperRepo)
    private val mockRepo = MockAssetRepository()

    private val _searchSource = MutableStateFlow(SearchSource.HYBRID)
    val searchSource: StateFlow<SearchSource> = _searchSource.asStateFlow()

    private val _uiState = MutableStateFlow<StockUiState>(StockUiState.Idle)
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    fun setSource(source: SearchSource) {
        _searchSource.value = source
    }

    fun analyzeTicker(ticker: String) {
        val t = ticker.trim().uppercase()
        if (t.isBlank()) return

        _uiState.value = StockUiState.Loading

        viewModelScope.launch {
            try {
                Log.d("StockViewModel", "Iniciando análise para $t via ${_searchSource.value}")
                
                var data: AssetData? = null
                
                if (_searchSource.value == SearchSource.MANUAL) {
                    data = try { db.getAsset(t) } catch(e: Exception) { null }
                    if (data == null) {
                        Log.d("StockViewModel", "Ticker $t não encontrado no banco manual, buscando via Híbrido")
                        data = fetchFromRepo(hybridRepo, t)
                    }
                } else {
                    val selectedRepo = when (_searchSource.value) {
                        SearchSource.BRAPI -> apiRepo
                        SearchSource.FUNDAMENTUS -> scraperRepo
                        SearchSource.HYBRID -> hybridRepo
                        SearchSource.MOCK -> mockRepo
                        SearchSource.MANUAL -> hybridRepo // Should not happen due to if above
                    }
                    data = fetchFromRepo(selectedRepo, t)
                }

                if (data != null) {
                    val score = calculateScoreForAsset(data)
                    _uiState.value = StockUiState.Success(data, score, isMockData = _searchSource.value == SearchSource.MOCK)
                } else {
                    handleError(t)
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Erro fatal na análise de $t", e)
                _uiState.value = StockUiState.Error("Ocorreu um erro interno. Tente novamente.")
            }
        }
    }

    private suspend fun fetchFromRepo(repo: AssetRepository, ticker: String): AssetData? {
        return try {
            withTimeout(10000) {
                repo.getAssetData(ticker)
            }
        } catch (e: Exception) {
            Log.e("StockViewModel", "Erro no repositório: ${e.message}")
            null
        }
    }

    private suspend fun handleError(ticker: String) {
        if (_searchSource.value != SearchSource.MOCK) {
            val mockData = mockRepo.getAssetData(ticker)
            if (mockData != null) {
                val score = calculateScoreForAsset(mockData)
                _uiState.value = StockUiState.Success(mockData, score, isMockData = true)
            } else {
                _uiState.value = StockUiState.Error("Não foi possível obter dados para este ticker.")
            }
        } else {
            _uiState.value = StockUiState.Error("Ticker não encontrado no Mock.")
        }
    }

    fun saveManualAsset(data: AssetData) {
        viewModelScope.launch {
            db.saveAsset(data)
            // Atualiza a UI com os novos dados e score recalculado
            val score = calculateScoreForAsset(data)
            _uiState.value = StockUiState.Success(data, score, isMockData = false)
        }
    }

    private fun calculateScoreForAsset(data: AssetData): Double {
        return when (data) {
            is AssetData.Stock -> calculateStockScore(data)
            is AssetData.Fii -> calculateFiiScore(data)
            is AssetData.Etf -> calculateEtfScore(data)
        }
    }

    private fun calculateStockScore(data: AssetData.Stock): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        // DY Médio 5 anos
        if (data.avgDividend5Years >= 0.06) {
            score += 1.0
            p.add("DY Histórico sólido (> 6% nos últimos 5 anos)")
        }

        val grahamPrice = sqrt(22.5 * data.lpa * data.vpa)
        if (data.currentPrice <= grahamPrice) {
            score += 1.0
            p.add("Preço abaixo do valor de Graham")
        } else {
            c.add("Valuation esticado (acima de Graham)")
        }

        if (data.pvp <= 1.5) {
            score += 1.0
            p.add("P/VP atrativo (<= 1.5)")
        } else if (data.pvp > 2.5) {
            c.add("P/VP elevado (Acima de 2.5)")
        }

        if (data.pl in 1.0..15.0) {
            score += 1.0
            p.add("P/L equilibrado (Preço/Lucro entre 1 e 15)")
        } else if (data.pl > 25.0) {
            c.add("P/L alto (Expectativa de crescimento)")
        }

        if (data.roe >= 0.15) {
            score += 1.0
            p.add("Rentabilidade sólida (ROE > 15%)")
        }

        if (data.dividendYield >= 0.06) {
            score += 1.0
            p.add("Dividend Yield atrativo (> 6% a.a.)")
        }

        if (data.netMargin >= 0.12) {
            score += 1.5
            p.add("Boa Margem Líquida")
        }

        if (data.sector == "Bancário") {
            if (data.baselIndex >= 0.14) {
                score += 2.0
                p.add("Basileia robusto (Segurança)")
            }
        } else {
            if (data.debtToEquity <= 0.8) {
                score += 2.0
                p.add("Baixa Dívida/Patrimônio")
            } else if (data.debtToEquity > 1.5) {
                score -= 1.0
                c.add("Alavancagem financeira elevada")
            }
        }

        if (data.paidDividendsLast5Years) p.add("Histórico de dividendos consistente")
        if (data.payout in 0.25..0.75) {
            score += 0.5
            p.add("Payout saudável e sustentável")
        }

        data.pros = p.take(10)
        data.cons = c.take(10)
        return score.coerceIn(0.0, 10.0)
    }

    private fun calculateFiiScore(data: AssetData.Fii): Double {
        var score = 0.0
        val p = mutableListOf<String>()
        val c = mutableListOf<String>()

        // Bonus para Gestão Ativa
        if (data.managementType.contains("Ativa", ignoreCase = true)) {
            score += 0.5
            p.add("Gestão Ativa (Potencial de alpha)")
        }

        // DY Médio 5 anos
        if (data.avgYield5Years >= 0.08) {
            score += 1.0
            p.add("DY Histórico sólido (> 8% nos últimos 5 anos)")
        }

        if (data.pvp in 0.92..1.03) {
            score += 3.0
            p.add("P/VP em zona de equilíbrio (Próximo a 1.0)")
        } else if (data.pvp < 0.92) {
            score += 1.5
            p.add("Ativo com desconto patrimonial")
        } else {
            c.add("Ágio elevado (P/VP > 1.05)")
        }

        if (data.vacancy <= 0.05) {
            score += 1.5
            p.add("Ocupação excelente (Vacância < 5%)")
        } else if (data.vacancy > 0.15) {
            c.add("Risco de vacância elevado")
        }

        if (data.multiProperty && data.multiTenant) {
            score += 1.5
            p.add("Alta diversificação (Multi-imóvel/inquilino)")
        } else {
            c.add("Risco de concentração (Mono-imóvel/inquilino)")
        }

        if (data.weightedLeaseTerm >= 4.0) {
            score += 1.0
            p.add("Contratos de longo prazo (WALT > 4 anos)")
        }

        if (data.yield12m >= 0.09) {
            score += 2.0
            p.add("Dividend Yield atrativo")
        }
        
        if (data.managementFee <= 0.01) {
            score += 1.0
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
}

sealed class StockUiState {
    data object Idle : StockUiState()
    data object Loading : StockUiState()
    data class Success(val data: AssetData, val score: Double, val isMockData: Boolean) : StockUiState()
    data class Error(val message: String) : StockUiState()
}
