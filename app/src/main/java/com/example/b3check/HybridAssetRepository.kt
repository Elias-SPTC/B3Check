package com.example.b3check

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HybridAssetRepository(
    private val apiRepo: AssetRepository,
    private val scraperRepo: AssetRepository
) : AssetRepository {

    override suspend fun getAssetData(ticker: String): AssetData? = coroutineScope {
        // Dispara as duas buscas em paralelo
        val scraperDeferred = async { 
            try { scraperRepo.getAssetData(ticker) } catch(e: Exception) { null }
        }
        val apiDeferred = async { 
            try { apiRepo.getAssetData(ticker) } catch(e: Exception) { null }
        }

        val scraperData = scraperDeferred.await()
        val apiData = apiDeferred.await()

        if (scraperData == null) {
            null
        } else if (apiData != null && apiData.currentPrice > 0) {
            // Só substitui se a API retornar um preço válido (maior que zero)
            when (scraperData) {
                is AssetData.Stock -> scraperData.copy(currentPrice = apiData.currentPrice)
                is AssetData.Fii -> scraperData.copy(currentPrice = apiData.currentPrice)
                is AssetData.Etf -> scraperData.copy(currentPrice = apiData.currentPrice)
                is AssetData.Bdr -> scraperData.copy(currentPrice = apiData.currentPrice)
            }
        } else {
            // Se a API falhou ou retornou preço 0, mantém o dado original do Scraper
            scraperData
        }
    }
}
