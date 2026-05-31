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
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Cabeçalhos extremamente realistas para evitar o 403 do Cloudflare/WAF
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
            connection.setRequestProperty("Cache-Control", "max-age=0")
            connection.setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"")
            connection.setRequestProperty("Sec-Ch-Ua-Mobile", "?0")
            connection.setRequestProperty("Sec-Ch-Ua-Platform", "\"Windows\"")
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "none")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

            val responseCode = connection.responseCode
            Log.d("Scraper", "Investidor10 ($ticker) Response: $responseCode")

            if (responseCode == 403) {
                Log.e("Scraper", "BLOQUEIO 403 no Investidor10. O site detectou o robô.")
                return@withContext null
            }
            if (responseCode != 200) return@withContext null

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html)
            
            val pageTitle = doc.select("h1").first()?.text() ?: ticker

            // Seletor mais robusto para a grade de indicadores do Investidor 10
            fun findInGrid(label: String): String? {
                // Tenta encontrar em divs que contenham o texto da label e um valor associado
                val elements = doc.select("div:has(span:contains($label))")
                for (element in elements) {
                    val value = element.select(".value, ._card-value, strong").first()?.text()
                    if (!value.isNullOrBlank()) return value
                }
                // Tenta seletor genérico de labels
                return doc.select("span:contains($label) + span, span:contains($label) + div").first()?.text()
            }

            // 1. Dados Principais
            val price = parseDouble(findInGrid("COTAÇÃO") ?: findInGrid("PREÇO ATUAL"))
            val dy = parsePercentage(findInGrid("DY") ?: findInGrid("DIVIDEND YIELD"))
            val pvp = parseDouble(findInGrid("P/VP"))
            val pl = parseDouble(findInGrid("P/L"))
            val roe = parsePercentage(findInGrid("ROE"))

            // 2. Dados Adicionais e Médias
            val netMargin = parsePercentage(findInGrid("MARGEM LÍQUIDA"))
            val debtToEquity = parseDouble(findInGrid("DÍVIDA LÍQUIDA / PATRIMÔNIO"))
            val netWorth = parseLargeNumber(findInGrid("PATRIMÔNIO LÍQUIDO"))
            
            val dy5Str = findInGrid("DY MÉDIO (5 ANOS)") ?: findInGrid("DY MÉDIO 5 ANOS")
            val dy5 = parsePercentage(dy5Str)

            val graham = parseDouble(findInGrid("VALOR JUSTO (GRAHAM)"))
            val bazin = parseDouble(findInGrid("PREÇO TETO (BAZIN)"))
            
            Log.d("Scraper", "Investidor10 Parsed -> P:$price, DY:$dy, DY5:$dy5, G:$graham, B:$bazin")

            if (isFii) {
                val vacancy = parsePercentage(findInGrid("VACÂNCIA") ?: findIndicatorValue(doc, "VACÂNCIA"))
                val propertyCount = parseDouble(findInGrid("NÚMERO DE IMÓVEIS") ?: findInGrid("QTD DE IMÓVEIS") ?: findIndicatorValue(doc, "NÚMERO DE IMÓVEIS", "QTD DE IMÓVEIS")).toInt()
                
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
                    mockedFields = mocked
                }
            } else {
                AssetData.Stock(
                    ticker = ticker, name = pageTitle, currentPrice = price, sector = "Ações",
                    lpa = if (pl > 0) price / pl else 1.0, 
                    vpa = if (pvp > 0) price / pvp else 1.0,
                    dividendYield5Years = if (dy5 > 0) dy5 else dy,
                    paidDividendsLast5Years = true,
                    netDebt = 0.0, ebitda = 1.0, netMargin = netMargin,
                    cagrProfit5Years = 0.08, cagrRevenue5Years = 0.08,
                    payout = 0.5, roe = roe, pvp = pvp, pl = pl, dividendYield = dy, 
                    debtToEquity = debtToEquity,
                    grahamPrice = graham,
                    bazinPrice = bazin,
                    valuationSource = "Investidor10"
                ).apply {
                    val mocked = mutableSetOf<String>()
                    if (price == 0.0) mocked.add("Preço Atual")
                    if (roe == 0.0) mocked.add("ROE")
                    if (dy == 0.0) mocked.add("Div. Yield (DY)")
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
