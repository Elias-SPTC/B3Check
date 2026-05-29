package com.example.b3check

sealed class AssetData {
    abstract val ticker: String
    abstract val currentPrice: Double
    abstract val sector: String
    var pros: List<String> = emptyList()
    var cons: List<String> = emptyList()
    var mockedFields: Set<String> = emptySet()

    data class Stock(
        override val ticker: String,
        override val currentPrice: Double,
        override val sector: String,
        val lpa: Double,
        val vpa: Double,
        val avgDividend3Years: Double,
        val paidDividendsLast5Years: Boolean,
        val netDebt: Double,
        val ebitda: Double,
        val netMargin: Double,
        val cagrProfit5Years: Double,
        val cagrRevenue5Years: Double,
        val payout: Double,
        val roe: Double, // Return on Equity
        val pvp: Double, // P/VP
        val pl: Double,  // P/L (Preço/Lucro)
        val dividendYield: Double,
        val debtToEquity: Double,
        val baselIndex: Double = 0.0,
        val defaultRate: Double = 0.0
    ) : AssetData()

    data class Fii(
        override val ticker: String,
        override val currentPrice: Double,
        override val sector: String,
        val pvp: Double,
        val vacancy: Double,
        val yield12m: Double,
        val ffoMargin: Double,
        val multiProperty: Boolean,
        val multiTenant: Boolean,
        val capRate: Double,
        val weightedLeaseTerm: Double, // WALT em anos
        val managementFee: Double,
        val propertyCount: Int,
        val aum: Double // Patrimônio Líquido
    ) : AssetData()

    data class Etf(
        override val ticker: String,
        override val currentPrice: Double,
        override val sector: String,
        val adminFee: Double,
        val trackingError: Double,
        val avgDailyVolume: Double,
        val benchmarkPerformance12m: Double,
        val aum: Double, // Assets Under Management (Patrimônio Líquido)
        val numberOfHoldings: Int,
        val isPassive: Boolean
    ) : AssetData()
}
