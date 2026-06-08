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
        val isEndsWith11 = t.endsWith("11")
        
        // Se termina em 11, tenta primeiro Ações (Units), depois FIIs
        if (isEndsWith11) {
            val stockData = fetchInternal(t, "acoes")
            if (stockData != null) return@withContext stockData
            
            val fiiData = fetchInternal(t, "fiis")
            if (fiiData != null) return@withContext fiiData
        } else {
            // Se não termina em 11, é muito provavelmente uma Ação normal (3, 4, 5, 6)
            val stockData = fetchInternal(t, "acoes")
            if (stockData != null) return@withContext stockData
        }
        
        // Fallback final para ETFs
        fetchInternal(t, "etfs")
    }

    private fun fetchInternal(t: String, category: String): AssetData? {
        val urlString = "https://investidor10.com.br/$category/$t/"
        val isFii = category == "fiis"
        val isEtf = category == "etfs"
        val ticker = t.uppercase()

        var connection: HttpURLConnection? = null
        try {
            Log.d("Scraper", "Iniciando requisição HTTP para ($category): $urlString")
            
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            
            // Cabeçalhos realistas para evitar bloqueios (WAF/Cloudflare)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8")

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.d("Scraper", "Investidor10 ($category/$t) retornou status $responseCode")
                return null
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html)
            
            // Tenta pegar o nome real ou usa o ticker como fallback
            val pageTitle = doc.select("h1").first()?.text()?.replace(ticker, "")?.replace("-", "")?.trim() 
            val finalName = if (pageTitle.isNullOrBlank()) ticker else pageTitle

            fun findInGrid(label: String): String? {
                // Tenta seletores variados para os cards de indicadores
                val selectors = listOf(
                    "div:has(span:contains($label)) .value",
                    "div:has(span:contains($label)) ._card-value",
                    "div:has(span:contains($label)) strong",
                    "span:contains($label) + span",
                    ".desc:contains($label) + .value"
                )
                for (sel in selectors) {
                    val v = doc.select(sel).first()?.text()
                    if (!v.isNullOrBlank()) return v
                }
                return null
            }

            val price = parseDouble(findInGrid("COTAÇÃO") ?: findInGrid("PREÇO ATUAL") ?: doc.select(".value").first()?.text())
            if (price <= 0.0) {
                Log.d("Scraper", "Preço não encontrado para $t em $category")
                return null
            }

            val dy = parsePercentage(findInGrid("DY") ?: findInGrid("DIVIDEND YIELD"))
            val pvp = parseDouble(findInGrid("P/VP"))
            val pl = parseDouble(findInGrid("P/L"))
            val roe = parsePercentage(findInGrid("ROE"))
            val netWorth = parseLargeNumber(findInGrid("PATRIMÔNIO LÍQUIDO") ?: findIndicatorValue(doc, "PATRIMÔNIO LÍQUIDO"))

            if (isFii) {
                val vacancy = parsePercentage(findInGrid("VACÂNCIA") ?: findIndicatorValue(doc, "VACÂNCIA"))
                val propertyCount = parseDouble(findInGrid("NÚMERO DE IMÓVEIS") ?: findInGrid("QTD DE IMÓVEIS") ?: findIndicatorValue(doc, "NÚMERO DE IMÓVEIS", "QTD DE IMÓVEIS")).toInt()
                
                val totalAssetsStr = findInGrid("ATIVOS") ?: findIndicatorValue(doc, "ATIVOS", "ATIVO TOTAL", "TOTAL DE ATIVOS")
                val cashStr = findInGrid("DISPONIBILIDADES") ?: findInGrid("CAIXA") ?: findIndicatorValue(doc, "DISPONIBILIDADES", "CAIXA", "SALDO EM CAIXA", "DISPONÍVEL")
                val totalAssets = parseLargeNumber(totalAssetsStr)
                val cash = parseLargeNumber(cashStr)
                
                val calculatedLeverage = if (totalAssets > 0 && (totalAssets - cash) > 0) {
                    (totalAssets - netWorth - cash) / (totalAssets - cash)
                } else -1.0

                return AssetData.Fii(
                    ticker = ticker, name = finalName, currentPrice = price, sector = "FII",
                    pvp = pvp, vacancy = vacancy, yield12m = dy, 
                    propertyCount = if (propertyCount > 0) propertyCount else 1, 
                    leverageValue = if (calculatedLeverage >= 0) calculatedLeverage else 0.0,
                    aum = netWorth
                ).apply { 
                    val mergedSources = mutableMapOf(
                        "name" to FieldSource.INTERNET, "currentPrice" to FieldSource.INTERNET,
                        "pvp" to FieldSource.INTERNET, "y12" to FieldSource.INTERNET,
                        "vac" to FieldSource.INTERNET, "prop" to FieldSource.INTERNET,
                        "aum" to FieldSource.INTERNET
                    )
                    if (calculatedLeverage >= 0) mergedSources["lev"] = FieldSource.INTERNET
                    fieldSources = mergedSources
                }
            } else if (isEtf) {
                return AssetData.Etf(
                    ticker = ticker, name = finalName, currentPrice = price,
                    adminFee = parsePercentage(findInGrid("TAXA DE ADMINISTRAÇÃO")),
                    avgDailyVolume = parseLargeNumber(findInGrid("LIQUIDEZ DIÁRIA"))
                ).apply {
                    fieldSources = mapOf("name" to FieldSource.INTERNET, "currentPrice" to FieldSource.INTERNET)
                }
            } else {
                val netMargin = parsePercentage(findInGrid("MARGEM LÍQUIDA"))
                val debtToEquity = parseDouble(findInGrid("DÍVIDA LÍQUIDA / PATRIMÔNIO"))
                val dy5 = parsePercentage(findInGrid("DY MÉDIO (5 ANOS)") ?: findInGrid("DY MÉDIO 5 ANOS"))
                val graham = parseDouble(findInGrid("VALOR JUSTO (GRAHAM)"))
                val bazin = parseDouble(findInGrid("PREÇO TETO (BAZIN)"))

                return AssetData.Stock(
                    ticker = ticker, name = finalName, currentPrice = price, sector = "Ação",
                    lpa = if (pl > 0) price / pl else 1.0, 
                    vpa = if (pvp > 0) price / pvp else 1.0,
                    dividendYield5Years = if (dy5 > 0) dy5 else dy,
                    netMargin = netMargin, roe = roe, pvp = pvp, pl = pl, dividendYield = dy, 
                    debtToEquity = debtToEquity,
                    grahamPrice = graham, bazinPrice = bazin,
                    valuationSource = "Investidor10"
                ).apply {
                    fieldSources = mapOf(
                        "name" to FieldSource.INTERNET, "currentPrice" to FieldSource.INTERNET,
                        "lpa" to FieldSource.INTERNET, "vpa" to FieldSource.INTERNET,
                        "dy" to FieldSource.INTERNET, "dy5" to FieldSource.INTERNET,
                        "ml" to FieldSource.INTERNET, "roe" to FieldSource.INTERNET,
                        "pl" to FieldSource.INTERNET, "pvp" to FieldSource.INTERNET,
                        "de" to FieldSource.INTERNET, "graham" to FieldSource.INTERNET, 
                        "bazin" to FieldSource.INTERNET
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Scraper", "Erro em fetchInternal ($category/$t): ${e.message}")
            return null
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
        }
        return null
    }

    private fun parseDouble(text: String?): Double {
        if (text.isNullOrBlank() || text == "-") return 0.0
        return try {
            text.replace("R$", "").replace(".", "").replace(",", ".").replace(Regex("[^0-9.-]"), "").trim().toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun parsePercentage(text: String?): Double {
        if (text.isNullOrBlank() || text == "-") return 0.0
        return try {
            val value = text.replace("%", "").replace(".", "").replace(",", ".").replace(Regex("[^0-9.-]"), "").trim().toDoubleOrNull() ?: 0.0
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
