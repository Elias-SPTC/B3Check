package com.example.b3check

import kotlinx.coroutines.delay

interface AssetRepository {
    suspend fun getAssetData(ticker: String): AssetData?
}

class MockAssetRepository : AssetRepository {

    private val knownEtfs = setOf("IVVB11", "BOVA11", "AUVP11", "SMALL11", "HASH11", "ECOO11", "MATB11")

    override suspend fun getAssetData(ticker: String): AssetData? {
        val t = ticker.uppercase().trim()
        delay(500)

        return when {
            // Ações: Tickers com 4 letras + sufixo 3, 4, 5, 6
            t.matches(Regex("^[A-Z]{4}[3-6]$")) -> AssetData.Stock(
                ticker = t, currentPrice = 35.50, sector = "Consumo",
                lpa = 2.8, vpa = 15.0, avgDividend3Years = 1.8,
                paidDividendsLast5Years = true, netDebt = 500.0, ebitda = 1200.0,
                netMargin = 0.14, cagrProfit5Years = 0.12, cagrRevenue5Years = 0.09,
                payout = 0.6, roe = 0.24, pvp = 1.15, pl = 12.0, dividendYield = 0.05, debtToEquity = 0.4
            )
            // ETFs
            knownEtfs.contains(t) -> AssetData.Etf(
                ticker = t, currentPrice = 210.0, sector = "Internacional",
                adminFee = 0.0025, trackingError = 0.003, avgDailyVolume = 8_000_000.0,
                benchmarkPerformance12m = 0.18, aum = 1_500_000_000.0,
                numberOfHoldings = 500, isPassive = true
            )
            // FIIs
            t.matches(Regex("^[A-Z]{4}11$")) -> AssetData.Fii(
                ticker = t, currentPrice = 110.0, sector = "Papel",
                pvp = 0.99, vacancy = 0.02, yield12m = 0.11, ffoMargin = 0.9,
                multiProperty = true, multiTenant = true, capRate = 0.12,
                weightedLeaseTerm = 4.5, managementFee = 0.008, propertyCount = 12, aum = 2_000_000_000.0
            )
            else -> null
        }
    }
}
