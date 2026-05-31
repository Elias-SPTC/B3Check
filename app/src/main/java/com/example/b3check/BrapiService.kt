package com.example.b3check

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BrapiService {
    @GET("api/quote/{ticker}")
    suspend fun getQuote(
        @Path("ticker") ticker: String,
        @Query("token") token: String,
        @Query("modules") modules: String = "financialData,defaultKeyStatistics"
    ): BrapiResponse
}

data class BrapiResponse(
    val results: List<AssetDto>
)

data class AssetDto(
    val symbol: String,
    val regularMarketPrice: Double?,
    val sector: String?,
    val type: String?, // "stock", "fund"
    val longName: String?,
    // Campos fundamentalistas principais
    val priceEarnings: Double?,
    val priceToBook: Double?,
    val dividendYield: Double?,
    // Módulos adicionais
    val financialData: FinancialDataDto?,
    val defaultKeyStatistics: DefaultKeyStatisticsDto?
)

data class FinancialDataDto(
    val returnOnEquity: Double?,
    val targetHighPrice: Double?,
    val targetLowPrice: Double?,
    val targetMeanPrice: Double?,
    val totalCash: Long?,
    val totalDebt: Long?,
    val totalRevenue: Long?,
    val debtToEquity: Double?,
    val profitMargins: Double?,
    val operatingMargins: Double?
)

data class DefaultKeyStatisticsDto(
    val returnOnEquity: Double?,
    val priceToBook: Double?,
    val dividendYield: Double?,
    val forwardEps: Double?,
    val trailingEps: Double?,
    val netIncomeToCommon: Long?,
    val bookValue: Double?,
    val trailingPE: Double?,
    val profitMargins: Double?
)

fun AssetDto.toAssetData(): AssetData {
    val isStock = type == "stock"
    val isFii = type == "fund" || (symbol.endsWith("11") && !isStock)
    val isEtf = type == "etf" || (symbol == "IVVB11" || symbol == "BOVA11" || symbol == "AUVP11")

    // O ROE e DY podem vir de módulos diferentes
    val roeReal = financialData?.returnOnEquity ?: defaultKeyStatistics?.returnOnEquity ?: 0.0
    val dyReal = dividendYield ?: defaultKeyStatistics?.dividendYield ?: 0.0

    return when {
        isEtf -> AssetData.Etf(
            ticker = symbol,
            name = longName ?: symbol,
            currentPrice = regularMarketPrice ?: 0.0,
            sector = sector ?: "ETF",
            adminFee = 0.003,
            trackingError = 0.002,
            avgDailyVolume = 5_000_000.0,
            benchmarkPerformance12m = 0.12,
            aum = 1_000_000_000.0,
            numberOfHoldings = 100,
            isPassive = true
        )
        isFii -> AssetData.Fii(
            ticker = symbol,
            name = longName ?: symbol,
            currentPrice = regularMarketPrice ?: 0.0,
            sector = sector ?: "Imobiliário",
            pvp = priceToBook ?: defaultKeyStatistics?.priceToBook ?: 1.0,
            vacancy = 0.05,
            yield12m = dyReal,
            ffoMargin = 0.8,
            multiProperty = true,
            multiTenant = true,
            capRate = 0.08,
            weightedLeaseTerm = 5.0,
            managementFee = 0.01,
            propertyCount = 5,
            aum = 1_000_000_000.0
        )
        else -> AssetData.Stock(
            ticker = symbol,
            name = longName ?: symbol,
            currentPrice = regularMarketPrice ?: 0.0,
            sector = sector ?: "Ações",
            lpa = defaultKeyStatistics?.trailingEps ?: 0.0,
            vpa = defaultKeyStatistics?.bookValue ?: 0.0,
            avgDividend3Years = dyReal * (regularMarketPrice ?: 0.0),
            paidDividendsLast5Years = true,
            netDebt = (financialData?.totalDebt ?: 0L).toDouble(),
            ebitda = 1.0,
            netMargin = financialData?.profitMargins ?: defaultKeyStatistics?.profitMargins ?: 0.0,
            cagrProfit5Years = 0.08,
            cagrRevenue5Years = 0.06,
            payout = 0.5,
            roe = roeReal,
            pvp = priceToBook ?: defaultKeyStatistics?.priceToBook ?: 0.0,
            pl = defaultKeyStatistics?.trailingPE ?: priceEarnings ?: 0.0,
            dividendYield = dyReal,
            debtToEquity = (financialData?.debtToEquity ?: 0.0) / 100.0
        )
    }
}
