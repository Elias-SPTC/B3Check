package com.example.b3check

class HybridAssetRepository(
    private val apiRepo: AssetRepository,
    private val scraperRepo: AssetRepository
) : AssetRepository {

    override suspend fun getAssetData(ticker: String): AssetData? {
        val scraperData = scraperRepo.getAssetData(ticker) ?: return null
        val apiData = apiRepo.getAssetData(ticker)

        return if (apiData != null) {
            // Combina: Preço da API (mais rápido) com Indicadores do Scraper (mais completos)
            when (scraperData) {
                is AssetData.Stock -> scraperData.copy(currentPrice = apiData.currentPrice)
                is AssetData.Fii -> scraperData.copy(currentPrice = apiData.currentPrice)
                is AssetData.Etf -> scraperData.copy(currentPrice = apiData.currentPrice)
            }
        } else {
            scraperData
        }
    }
}
