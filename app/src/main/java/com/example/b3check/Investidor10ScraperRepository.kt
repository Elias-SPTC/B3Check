package com.example.b3check

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URL

class Investidor10ScraperRepository : AssetRepository {

    override suspend fun getAssetData(ticker: String): AssetData? = withContext(Dispatchers.IO) {
        val t = ticker.lowercase().trim()
        val isFii = t.endsWith("11") 
        val category = if (isFii) "fiis" else "acoes"
        val urlString = "https://investidor10.com.br/$category/$t/"

        var connection: HttpURLConnection? = null
        try {
            Log.d("Scraper", "Iniciando requisição HTTP para: $urlString")
            
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val responseCode = connection.responseCode
            Log.d("Scraper", "Código de resposta: $responseCode")

            if (responseCode != 200) {
                return@withContext null
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html)
            
            Log.d("Scraper", "HTML parseado com sucesso")

            // Verificação básica
            val pageTitle = doc.select("h1").first()?.text() ?: ""
            if (!pageTitle.contains(ticker, ignoreCase = true)) {
                return@withContext null
            }

            // 1. Dados Principais (Top Cards)
            val price = parseDouble(findIndicatorValue(doc, "COTAÇÃO", "PREÇO ATUAL"))
            val dy = parsePercentage(findIndicatorValue(doc, "DY", "DIVIDEND YIELD"))
            val pvp = parseDouble(findIndicatorValue(doc, "P/VP"))
            val pl = parseDouble(findIndicatorValue(doc, "P/L"))
            val roe = parsePercentage(findIndicatorValue(doc, "ROE"))

            // 2. Dados Adicionais
            val netMargin = parsePercentage(findIndicatorValue(doc, "MARGEM LÍQUIDA"))
            val debtToEquity = parseDouble(findIndicatorValue(doc, "DÍVIDA LÍQUIDA / PATRIMÔNIO"))
            val netWorth = parseLargeNumber(findIndicatorValue(doc, "PATRIMÔNIO LÍQUIDO", "PL"))

            if (isFii) {
                val vacancy = parsePercentage(findIndicatorValue(doc, "VACÂNCIA"))
                val propertyCount = parseDouble(findIndicatorValue(doc, "NÚMERO DE IMÓVEIS", "QTD DE IMÓVEIS")).toInt()
                
                AssetData.Fii(
                    ticker = ticker, name = pageTitle, currentPrice = price, sector = "Imobiliário",
                    pvp = pvp, vacancy = vacancy, yield12m = dy, ffoMargin = 0.8,
                    multiProperty = propertyCount > 1, multiTenant = true, capRate = 0.08,
                    weightedLeaseTerm = 5.0, managementFee = 0.01, 
                    propertyCount = if (propertyCount > 0) propertyCount else 1, 
                    aum = netWorth
                ).apply { 
                    val mocked = mutableSetOf<String>()
                    if (price == 0.0) mocked.add("Preço Atual")
                    if (pvp == 0.0) mocked.add("P/VP")
                    if (dy == 0.0) mocked.add("Div. Yield (DY)")
                    if (vacancy == 0.0) mocked.add("Vacância")
                    if (netWorth == 0.0) mocked.add("Patrimônio (PL)")
                    mockedFields = mocked
                }
            } else {
                AssetData.Stock(
                    ticker = ticker, name = pageTitle, currentPrice = price, sector = "Ações",
                    lpa = if (pl > 0) price / pl else 1.0, 
                    vpa = if (pvp > 0) price / pvp else 1.0,
                    avgDividend3Years = dy * price, paidDividendsLast5Years = true,
                    netDebt = 0.0, ebitda = 1.0, netMargin = netMargin,
                    cagrProfit5Years = 0.08, cagrRevenue5Years = 0.08,
                    payout = 0.5, roe = roe, pvp = pvp, pl = pl, dividendYield = dy, 
                    debtToEquity = debtToEquity
                ).apply {
                    val mocked = mutableSetOf<String>()
                    if (price == 0.0) mocked.add("Preço Atual")
                    if (pvp == 0.0) mocked.add("P/VP")
                    if (pl == 0.0) mocked.add("P/L")
                    if (roe == 0.0) mocked.add("ROE")
                    if (dy == 0.0) mocked.add("Div. Yield (DY)")
                    if (netMargin == 0.0) mocked.add("Margem Líquida")
                    mockedFields = mocked
                }
            }
        } catch (e: Exception) {
            Log.e("Scraper", "Erro ao acessar Investidor10: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun findIndicatorValue(doc: Document, vararg labels: String): String? {
        for (label in labels) {
            val cardValue = doc.select("._card:contains($label) ._card-value").first()?.text()
            if (!cardValue.isNullOrBlank()) return cardValue

            val gridValue = doc.select(".cell:has(span:contains($label)) .value, .item:has(span:contains($label)) .value, div:has(span:contains($label)) .value").first()?.text()
            if (!gridValue.isNullOrBlank()) return gridValue
            
            val listValue = doc.select("li:contains($label) .value, tr:contains($label) td:last-child").first()?.text()
            if (!listValue.isNullOrBlank()) return listValue
        }
        return null
    }

    private fun parseDouble(text: String?): Double {
        if (text.isNullOrBlank() || text == "-") return 0.0
        return try {
            text.replace("R$", "")
                .replace(".", "")
                .replace(",", ".")
                .replace(Regex("[^0-9.-]"), "")
                .trim()
                .toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parsePercentage(text: String?): Double {
        if (text.isNullOrBlank() || text == "-") return 0.0
        return try {
            val value = text.replace("%", "")
                .replace(".", "")
                .replace(",", ".")
                .replace(Regex("[^0-9.-]"), "")
                .trim()
                .toDoubleOrNull() ?: 0.0
            value / 100.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parseLargeNumber(text: String?): Double {
        if (text.isNullOrBlank() || text == "-") return 0.0
        val cleanText = text.uppercase().replace("R$", "").trim()
        val multiplier = when {
            cleanText.contains("B") -> 1_000_000_000.0
            cleanText.contains("M") -> 1_000_000.0
            cleanText.contains("K") -> 1_000.0
            else -> 1.0
        }
        return parseDouble(cleanText) * multiplier
    }
}
