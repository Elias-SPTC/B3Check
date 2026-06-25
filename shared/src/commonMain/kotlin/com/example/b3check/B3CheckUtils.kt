package com.example.b3check

import java.util.Locale

// Utilitários de formatação brasileiros
fun formatBR(v: Double, emptyIfZero: Boolean = false): String {
    if (emptyIfZero && v == 0.0) return ""
    return String.format(Locale("pt", "BR"), "%,.2f", v)
}

/**
 * Formata números grandes de forma legível (M, B, T) ou mantém precisão se houver quebrados.
 */
fun formatSmart(v: Double, emptyIfZero: Boolean = false): String {
    if (emptyIfZero && v == 0.0) return ""
    val absV = kotlin.math.abs(v)
    val epsilon = 0.0001
    
    // Verifica se o número pode ser simplificado sem perda de precisão significativa
    fun canSimplify(value: Double, divisor: Double): Boolean {
        val simplified = value / divisor
        val roundedSimplified = kotlin.math.round(simplified * 100.0) / 100.0
        // Condição: O número deve ser inteiro e o resultado da simplificação deve ter no máximo 2 casas decimais
        return kotlin.math.abs(simplified - roundedSimplified) < epsilon && 
               kotlin.math.abs(value - kotlin.math.round(value)) < epsilon
    }

    return when {
        absV >= 1_000_000_000_000.0 && canSimplify(absV, 1_000_000_000_000.0) -> String.format(Locale("pt", "BR"), "%.2f T", v / 1_000_000_000_000.0)
        absV >= 1_000_000_000.0 && canSimplify(absV, 1_000_000_000.0) -> String.format(Locale("pt", "BR"), "%.2f B", v / 1_000_000_000.0)
        absV >= 1_000_000.0 && canSimplify(absV, 1_000_000.0) -> String.format(Locale("pt", "BR"), "%.2f M", v / 1_000_000.0)
        absV >= 1_000.0 && canSimplify(absV, 1_000.0) -> String.format(Locale("pt", "BR"), "%.2f K", v / 1_000.0)
        else -> formatBR(v)
    }
}

/**
 * Converte string formatada (ex: 1.234,56 ou 10M) para Double.
 */
fun parseBR(v: String, unitScale: Double = 1.0): Double {
    if (v.isBlank()) return 0.0
    val cleaned = v.uppercase().trim().replace(" ", "")
    
    val multiplier = when {
        cleaned.endsWith("K") -> 1_000.0
        cleaned.endsWith("M") -> 1_000_000.0
        cleaned.endsWith("B") -> 1_000_000_000.0
        cleaned.endsWith("T") -> 1_000_000_000_000.0
        else -> 1.0
    }
    
    val numericPart = if (multiplier != 1.0) cleaned.dropLast(1) else cleaned
    
    return try {
        val num = numericPart.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        num * multiplier
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
