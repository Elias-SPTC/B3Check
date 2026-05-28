package com.example.b3check

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BrapiAssetRepository(private val token: String) : AssetRepository {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://brapi.dev/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(BrapiService::class.java)

    override suspend fun getAssetData(ticker: String): AssetData? {
        return try {
            val response = service.getQuote(ticker, token)
            response.results.firstOrNull()?.toAssetData()
        } catch (e: Exception) {
            Log.e("BrapiRepo", "Error fetching ticker $ticker", e)
            null
        }
    }
}
