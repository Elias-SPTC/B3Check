package com.example.b3check

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson

class ManualAssetDatabase(context: Context) : SQLiteOpenHelper(context, "manual_assets.db", null, 3) {

    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE assets (
                ticker TEXT PRIMARY KEY,
                type TEXT,
                json_data TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS assets")
        onCreate(db)
    }

    fun saveAsset(data: AssetData) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("ticker", data.ticker)
                put("type", when(data) {
                    is AssetData.Stock -> "STOCK"
                    is AssetData.Fii -> "FII"
                    is AssetData.Etf -> "ETF"
                    is AssetData.Bdr -> "BDR"
                })
                put("json_data", gson.toJson(data))
            }
            db.insertWithOnConflict("assets", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("ManualDB", "Erro ao salvar: ${e.message}")
        }
    }

    private fun safeAsset(it: AssetData): AssetData {
        val sources = it.fieldSources ?: emptyMap()
        val asset = when(it) {
            is AssetData.Stock -> it.copy(
                sector = it.sector ?: "", subSector = it.subSector ?: "",
                debtToEbitda = it.debtToEbitda, cagrProfit5Years = it.cagrProfit5Years,
                baselIndex = it.baselIndex, dividendYield5Years = it.dividendYield5Years,
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                avgDailyVolume = it.avgDailyVolume, cagrRevenue5Years = it.cagrRevenue5Years,
                grahamPrice = it.grahamPrice, bazinPrice = it.bazinPrice,
                debtToEquity = it.debtToEquity, netMargin = it.netMargin, payout = it.payout
            )
            is AssetData.Fii -> it.copy(
                sector = it.sector ?: "", subSector = it.subSector ?: "",
                leverageValue = it.leverageValue, avgDailyVolume = it.avgDailyVolume,
                vacancy = it.vacancy, propertyCount = it.propertyCount,
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                aum = it.aum, managementFee = it.managementFee, weightedLeaseTerm = it.weightedLeaseTerm,
                tenantScore = it.tenantScore, leverageScore = it.leverageScore
            )
            is AssetData.Etf -> it.copy(
                sector = it.sector ?: "ETF", subSector = it.subSector ?: "ETF",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                adminFee = it.adminFee, trackingError = it.trackingError,
                avgDailyVolume = it.avgDailyVolume, aum = it.aum, numberOfHoldings = it.numberOfHoldings
            )
            is AssetData.Bdr -> it.copy(
                sector = it.sector ?: "BDR", subSector = it.subSector ?: "BDR",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                dividendYield = it.dividendYield, parity = it.parity ?: "1:1"
            )
        }
        asset.fieldSources = sources
        asset.pros = it.pros
        asset.cons = it.cons
        return asset
    }

    fun getAsset(ticker: String): AssetData? {
        return try {
            val db = readableDatabase
            val cursor = db.query("assets", arrayOf("type", "json_data"), "ticker = ?", arrayOf(ticker), null, null, null)
            if (cursor.moveToFirst()) {
                val type = cursor.getString(0)
                val json = cursor.getString(1)
                cursor.close()
                val asset = when (type) {
                    "STOCK" -> gson.fromJson(json, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(json, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(json, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(json, AssetData.Bdr::class.java)
                    else -> null
                }
                asset?.let { safeAsset(it) }
            } else { cursor.close(); null }
        } catch (e: Exception) { null }
    }

    fun deleteAsset(ticker: String) {
        writableDatabase.delete("assets", "ticker = ?", arrayOf(ticker))
    }

    fun getAllAssets(): List<AssetData> {
        val list = mutableListOf<AssetData>()
        try {
            val db = readableDatabase
            val cursor = db.query("assets", arrayOf("type", "json_data"), null, null, null, null, "ticker ASC")
            while (cursor.moveToNext()) {
                val type = cursor.getString(0)
                val json = cursor.getString(1)
                val asset = when (type) {
                    "STOCK" -> gson.fromJson(json, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(json, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(json, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(json, AssetData.Bdr::class.java)
                    else -> null
                }
                asset?.let { list.add(safeAsset(it)) }
            }
            cursor.close()
        } catch (e: Exception) {}
        return list
    }

    fun exportBackup(): String {
        val list = mutableListOf<Map<String, String>>()
        val cursor = readableDatabase.query("assets", arrayOf("type", "json_data"), null, null, null, null, null)
        while (cursor.moveToNext()) {
            list.add(mapOf("type" to cursor.getString(0), "json" to cursor.getString(1)))
        }
        cursor.close()
        return gson.toJson(list)
    }

    fun importBackup(json: String): Boolean {
        return try {
            val typeToken = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val data: List<Map<String, String>> = gson.fromJson(json, typeToken)
            val db = writableDatabase
            db.beginTransaction()
            try {
                data.forEach { item ->
                    val type = item["type"]
                    val jsonData = item["json"]
                    val asset = when (type) {
                        "STOCK" -> gson.fromJson(jsonData, AssetData.Stock::class.java)
                        "FII" -> gson.fromJson(jsonData, AssetData.Fii::class.java)
                        "ETF" -> gson.fromJson(jsonData, AssetData.Etf::class.java)
                        "BDR" -> gson.fromJson(jsonData, AssetData.Bdr::class.java)
                        else -> null
                    }
                    asset?.let { saveAsset(safeAsset(it)) }
                }
                db.setTransactionSuccessful(); true
            } finally { db.endTransaction() }
        } catch (e: Exception) { false }
    }
}
