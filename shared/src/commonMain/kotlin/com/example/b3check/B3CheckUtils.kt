package com.example.b3check

import java.util.Locale

// Utilitários de formatação brasileiros
fun formatBR(v: Double, emptyIfZero: Boolean = false): String {
    if (emptyIfZero && v == 0.0) return ""
    return String.format(Locale("pt", "BR"), "%,.2f", v)
}

fun parseBR(v: String): Double {
    if (v.isBlank()) return 0.0
    return try {
        v.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    } catch (e: Exception) { 0.0 }
}

fun getValByKey(stock: AssetData.Stock, key: String): Double {
    return when(key) {
        "lpa" -> stock.lpa
        "vpa" -> stock.vpa
        "pl" -> stock.pl
        "pvp" -> stock.pvp
        "roe" -> stock.roe
        "ml" -> stock.netMargin
        "de" -> stock.debtToEquity
        "deEbitda" -> stock.debtToEbitda
        "dy" -> stock.dividendYield
        "dy5" -> stock.dividendYield5Years
        "payout" -> stock.payout
        "basel" -> stock.baselIndex
        "graham" -> stock.grahamPrice
        "bazin" -> stock.bazinPrice
        else -> 0.0
    }
}

fun getValByKeyFii(fii: AssetData.Fii, key: String): Double {
    return when(key) {
        "pvp" -> fii.pvp
        "vac" -> fii.vacancy
        "y12" -> fii.yield12m
        "y5" -> fii.avgYield5Years
        "vol" -> fii.avgDailyVolume
        "prop" -> fii.propertyCount.toDouble()
        "aum" -> fii.aum
        "mFee" -> fii.managementFee
        "walt" -> fii.weightedLeaseTerm
        "mLev" -> fii.leverageValue
        else -> 0.0
    }
}
