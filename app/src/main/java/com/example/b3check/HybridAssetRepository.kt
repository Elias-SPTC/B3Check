package com.example.b3check

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HybridAssetRepository(
    private val apiRepo: AssetRepository,
    private val scraperRepo: AssetRepository
) : AssetRepository {

    override suspend fun getAssetData(ticker: String): AssetData? = coroutineScope {
        // Dispara as duas buscas em paralelo para ganhar velocidade
        val scraperDeferred = async { scraperRepo.getAssetData(ticker) }
        val apiDeferred = async { apiRepo.getAssetData(ticker) }

        val scraperData = scraperDeferred.await() ?: return@coroutineScope null
        val apiData = apiDeferred.await()

        if (apiData != null) {
            // Combina: Preço em tempo real da API com Indicadores completos do Scraper
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
