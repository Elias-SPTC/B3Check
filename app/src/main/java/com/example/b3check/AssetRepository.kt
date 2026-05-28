package com.example.b3check

import kotlinx.coroutines.delay

/**
 * Interface que define como o app obtém dados de ativos.
 * Facilita a troca de Mock para uma API real futuramente.
 */
interface AssetRepository {
    suspend fun getAssetData(ticker: String): AssetData?
}

class MockAssetRepository : AssetRepository {

    // Simula uma tabela interna de tipos de ativos
    private val knownEtfs = setOf("IVVB11", "BOVA11", "AUVP11", "SMALL11", "HASH11", "ECOO11", "MATB11")

    override suspend fun getAssetData(ticker: String): AssetData? {
        val t = ticker.uppercase().trim()
        
        // Simula latência de rede (500ms)
        delay(500)

        return when {
            // Lógica de Classificação Ações
            t.matches(Regex("^[A-Z]{4}[3-6]$")) -> getMockStock(t)
            
            // Lógica de Classificação ETF
            knownEtfs.contains(t) -> getMockEtf(t)
            
            // Lógica de Classificação FII
            t.matches(Regex("^[A-Z]{4}11$")) -> getMockFii(t)
            
            else -> null
        }
    }

    private fun getMockStock(t: String) = AssetData.Stock(
        ticker = t, currentPrice = 35.50, sector = "Consumo",
        lpa = 2.8, vpa = 15.0, avgDividend3Years = 1.8,
        paidDividendsLast5Years = true, netDebt = 500.0, ebitda = 1200.0,
        netMargin = 0.14, cagrProfit5Years = 0.12, cagrRevenue5Years = 0.09,
        payout = 0.6, roe = 0.22, pvp = 1.2, pl = 12.0, dividendYield = 0.05, debtToEquity = 0.4
    )

    private fun getMockFii(t: String) = AssetData.Fii(
        ticker = t, currentPrice = 110.0, sector = "Papel",
        pvp = 0.99, vacancy = 0.0, yield12m = 0.11, ffoMargin = 0.9,
        multiProperty = false, multiTenant = true, capRate = 0.12,
        weightedLeaseTerm = 3.0, managementFee = 0.008, propertyCount = 12, aum = 2_000_000_000.0
    )

    private fun getMockEtf(t: String) = AssetData.Etf(
        ticker = t, currentPrice = 210.0, sector = "Internacional",
        adminFee = 0.0025, trackingError = 0.003, avgDailyVolume = 8_000_000.0,
        benchmarkPerformance12m = 0.18, aum = 1_500_000_000.0,
        numberOfHoldings = 500, isPassive = true
    )
}
