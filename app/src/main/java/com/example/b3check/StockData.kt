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
        val lpa: Double,
        val vpa: Double,
        val avgDividend3Years: Double,
        val avgDividend5Years: Double = 0.0, // Adicionado
        val paidDividendsLast5Years: Boolean,
        val netDebt: Double,
        val ebitda: Double,
        val netMargin: Double,
        val cagrProfit5Years: Double,
        val cagrRevenue5Years: Double,
        val payout: Double,
        val roe: Double,
        val pvp: Double,
        val pl: Double,
        val dividendYield: Double,
        val debtToEquity: Double,
        val baselIndex: Double = 0.0,
        val defaultRate: Double = 0.0
    ) : AssetData()

    data class Fii(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val fundType: String = "Tijolo", // Adicionado (Papel, Tijolo, Híbrido)
        val managementType: String = "Ativa", // Adicionado (Ativa, Passiva)
        val pvp: Double,
        val vacancy: Double,
        val yield12m: Double,
        val avgYield5Years: Double = 0.0, // Adicionado
        val ffoMargin: Double,
        val multiProperty: Boolean,
        val multiTenant: Boolean,
        val capRate: Double,
        val weightedLeaseTerm: Double,
        val managementFee: Double,
        val propertyCount: Int,
        val aum: Double
    ) : AssetData()

    data class Etf(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String,
        val adminFee: Double,
        val trackingError: Double,
        val avgDailyVolume: Double,
        val benchmarkPerformance12m: Double,
        val aum: Double,
        val numberOfHoldings: Int,
        val isPassive: Boolean
    ) : AssetData()
}
