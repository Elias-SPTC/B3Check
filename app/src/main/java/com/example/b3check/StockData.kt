package com.example.b3check

enum class FieldSource { INTERNET, SIMULATION, USER }

sealed class AssetData {
    abstract val ticker: String
    abstract val name: String
    abstract val currentPrice: Double
    abstract val sector: String
    abstract val subSector: String
    abstract val isInPortfolio: Boolean
    abstract val sharesCount: Double
    var pros: List<String> = emptyList()
    var cons: List<String> = emptyList()
    var mockedFields: Set<String> = emptySet() // Para compatibilidade se necessário
    var fieldSources: Map<String, FieldSource>? = emptyMap()

    data class Stock(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String = "",
        override val subSector: String = "",
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
        val debtToEbitda: Double = 0.0, // Novo parâmetro de alavancagem industrial
        val baselIndex: Double = 0.0,
        val defaultRate: Double = 0.0,
        val grahamPrice: Double = 0.0,
        val bazinPrice: Double = 0.0,
        val valuationSource: String = "",
        override val isInPortfolio: Boolean = false,
        override val sharesCount: Double = 0.0
    ) : AssetData()

    data class Fii(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String = "",
        override val subSector: String = "",
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
        val tenantScore: Int = 0, // 1-5, 0 desativa
        val leverageScore: Int = 0, // 1-5, 0 desativa
        val leverageValue: Double = 0.0, 
        val avgDailyVolume: Double = 0.0, // Novo: Liquidez para FIIs
        val aum: Double = 0.0,
        override val isInPortfolio: Boolean = false,
        override val sharesCount: Double = 0.0
    ) : AssetData()

    data class Etf(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String = "ETF",
        override val subSector: String = "ETF",
        val adminFee: Double = 0.0,
        val trackingError: Double = 0.0,
        val avgDailyVolume: Double = 0.0,
        val benchmarkPerformance12m: Double = 0.0,
        val aum: Double = 0.0,
        val numberOfHoldings: Int = 1,
        val isPassive: Boolean = true,
        override val isInPortfolio: Boolean = false,
        override val sharesCount: Double = 0.0
    ) : AssetData()

    data class Bdr(
        override val ticker: String,
        override val name: String,
        override val currentPrice: Double,
        override val sector: String = "BDR",
        override val subSector: String = "BDR",
        val dividendYield: Double = 0.0,
        val parity: String = "1:1",
        override val isInPortfolio: Boolean = false,
        override val sharesCount: Double = 0.0
    ) : AssetData()
}
