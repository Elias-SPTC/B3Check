package com.example.b3check

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fundamentus é conhecido por ser extremamente leve e estável para scraping.
 */
class FundamentusScraperRepository : AssetRepository {

    override suspend fun getAssetData(ticker: String): AssetData? = withContext(Dispatchers.IO) {
        val t = ticker.uppercase().trim()
        val urlString = "https://www.fundamentus.com.br/detalhes.php?papel=$t"
        
        var connection: HttpURLConnection? = null
        try {
            Log.d("Scraper", "Conectando ao Fundamentus: $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode != 200) return@withContext null

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html)

            // No Fundamentus, o nome da empresa geralmente está no título ou em uma célula específica
            val name = doc.select("td:contains(Empresa) + td").first()?.text() ?: t

            // Extração de dados via texto da célula vizinha (Padrão Fundamentus)
            val price = parseDouble(findValueByLabel(doc, "Cotação"))
            val pl = parseDouble(findValueByLabel(doc, "P/L"))
            val pvp = parseDouble(findValueByLabel(doc, "P/VP"))
            val dy = parsePercentage(findValueByLabel(doc, "Div. Yield"))
            val roe = parsePercentage(findValueByLabel(doc, "ROE"))
            val netMargin = parsePercentage(findValueByLabel(doc, "Marg. Líquida"))
            val debtToEquity = parseDouble(findValueByLabel(doc, "Div. Liq. / Patrim."))
            val netWorth = parseLargeNumber(findValueByLabel(doc, "Patrim. Líq"))

            // Detecta se é FII ou Ação pelo conteúdo (Fundamentus tem páginas diferentes)
            val tipo = findValueByLabel(doc, "Tipo") ?: ""
            val isFii = t.endsWith("11") && (tipo.contains("FII", ignoreCase = true) || html.contains("Fundo Imobiliário", ignoreCase = true) || html.contains("Vacância", ignoreCase = true))

            // No Fundamentus não há DY médio de 5 anos fácil, usamos o atual como fallback
            val dy5 = dy

            if (isFii) {
                val vacancy = parsePercentage(findValueByLabel(doc, "Vacância"))
                AssetData.Fii(
                    ticker = t, name = name, currentPrice = price, sector = "FII",
                    pvp = pvp, vacancy = vacancy, yield12m = dy, ffoMargin = 0.8,
                    multiProperty = true, multiTenant = true, capRate = 0.08,
                    weightedLeaseTerm = 5.0, managementFee = 0.01, propertyCount = 5, aum = netWorth
                ).apply {
                    val mocked = mutableSetOf<String>()
                    if (price == 0.0) mocked.add("Preço Atual")
                    if (pvp == 0.0) mocked.add("P/VP")
                    mockedFields = mocked
                }
            } else {
                AssetData.Stock(
                    ticker = t, name = name, currentPrice = price, sector = "Ação",
                    lpa = if (pl > 0) price / pl else 1.0,
                    vpa = if (pvp > 0) price / pvp else 1.0,
                    avgDividend5Years = dy * price, 
                    dividendYield5Years = dy,
                    paidDividendsLast5Years = true,
                    netDebt = 0.0, ebitda = 1.0, netMargin = netMargin,
                    cagrProfit5Years = 0.08, cagrRevenue5Years = 0.08,
                    payout = 0.5, roe = roe, pvp = pvp, pl = pl, dividendYield = dy,
                    debtToEquity = debtToEquity
                ).apply {
                    val mocked = mutableSetOf<String>()
                    if (price == 0.0) mocked.add("Preço Atual")
                    if (roe == 0.0) mocked.add("ROE")
                    mockedFields = mocked
                }
            }
        } catch (e: Exception) {
            Log.e("Scraper", "Erro Fundamentus: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun findValueByLabel(doc: org.jsoup.nodes.Document, label: String): String? {
        // O Fundamentus usa <td> com a label seguida de <td> com o valor
        return doc.select("td:contains($label) + td").first()?.text()
    }

    private fun parseDouble(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        return text.replace(".", "").replace(",", ".").replace(Regex("[^0-9.-]"), "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun parsePercentage(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val value = text.replace("%", "").replace(".", "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
        return value / 100.0
    }

    private fun parseLargeNumber(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        return parseDouble(text)
    }
}
