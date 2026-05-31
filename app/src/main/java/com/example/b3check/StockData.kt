package com.example.b3check

sealed class AssetData {
    abstract val ticker: String
    abstract val name: String
    abstract val currentPrice: Double
    abstract val sector: String
    var pros: List<String> = emptyList()
    var cons: List<String> = emptyList()
    var mockedFields: Set<String> = emptySet()

    data class Stock(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val lpa: Double = 0.0,
        val vpa: Double = 0.0,
        val dividendYield5Years: Double = 0.0,
        val paidDividendsLast5Years: Boolean = true,
        val netDebt: Double = 0.0,
        val ebitda: Double = 0.0,
        val netMargin: Double = 0.0,
        val cagrProfit5Years: Double = 0.0,
        val cagrRevenue5Years: Double = 0.0,
        val payout: Double = 0.0,
        val roe: Double = 0.0,
        val pvp: Double = 0.0,
        val pl: Double = 0.0,
        val dividendYield: Double = 0.0,
        val debtToEquity: Double = 0.0,
        val baselIndex: Double = 0.0,
        val defaultRate: Double = 0.0,
        val grahamPrice: Double = 0.0,
        val bazinPrice: Double = 0.0,
        val valuationSource: String = ""
    ) : AssetData()

    data class Fii(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val fundType: String = "Tijolo",
        val managementType: String = "Ativa",
        val pvp: Double = 0.0,
        val vacancy: Double = 0.0,
        val yield12m: Double = 0.0,
        val avgYield5Years: Double = 0.0,
        val ffoMargin: Double = 0.0,
        val multiProperty: Boolean = true,
        val multiTenant: Boolean = true,
        val capRate: Double = 0.0,
        val weightedLeaseTerm: Double = 0.0,
        val managementFee: Double = 0.0,
        val propertyCount: Int = 1,
        val aum: Double = 0.0
    ) : AssetData()

    data class Etf(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val adminFee: Double = 0.0,
        val trackingError: Double = 0.0,
        val avgDailyVolume: Double = 0.0,
        val benchmarkPerformance12m: Double = 0.0,
        val aum: Double = 0.0,
        val numberOfHoldings: Int = 1,
        val isPassive: Boolean = true
    ) : AssetData()

    data class Bdr(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val dividendYield: Double = 0.0,
        val parity: String = "1:1"
    ) : AssetData()
}
