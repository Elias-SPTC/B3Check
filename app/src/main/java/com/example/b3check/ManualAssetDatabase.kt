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
                asset?.let {
                    if (it.fieldSources == null) it.fieldSources = emptyMap()
                    when(it) {
                        is AssetData.Stock -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, debtToEbitda = it.debtToEbitda)
                        is AssetData.Fii -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, leverageScore = it.leverageScore, leverageValue = it.leverageValue)
                        is AssetData.Etf -> it.copy(sector = it.sector ?: "ETF", subSector = it.subSector ?: "ETF", sharesCount = it.sharesCount)
                        is AssetData.Bdr -> it.copy(sector = it.sector ?: "BDR", subSector = it.subSector ?: "BDR", sharesCount = it.sharesCount)
                    }
                }
            } else {
                cursor.close()
                null
            }
        } catch (e: Exception) {
            Log.e("ManualDB", "Erro ao ler: ${e.message}")
            null
        }
    }

    fun deleteAsset(ticker: String) {
        try {
            val db = writableDatabase
            db.delete("assets", "ticker = ?", arrayOf(ticker))
        } catch (e: Exception) {
            Log.e("ManualDB", "Erro ao deletar: ${e.message}")
        }
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
                asset?.let { 
                    if (it.fieldSources == null) it.fieldSources = emptyMap()
                    // Garante que campos novos não venham nulos de backups antigos ou desserialização incompleta
                    val safeAsset = when(it) {
                        is AssetData.Stock -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, debtToEbitda = it.debtToEbitda, cagrProfit5Years = it.cagrProfit5Years, cagrRevenue5Years = it.cagrRevenue5Years)
                        is AssetData.Fii -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, leverageScore = it.leverageScore, leverageValue = it.leverageValue, avgDailyVolume = it.avgDailyVolume)
                        is AssetData.Etf -> it.copy(sector = it.sector ?: "ETF", subSector = it.subSector ?: "ETF", sharesCount = it.sharesCount)
                        is AssetData.Bdr -> it.copy(sector = it.sector ?: "BDR", subSector = it.subSector ?: "BDR", sharesCount = it.sharesCount)
                    }
                    list.add(safeAsset)
                }
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("ManualDB", "Erro ao listar: ${e.message}")
        }
        return list
    }

    fun exportBackup(): String {
        return try {
            val list = mutableListOf<Map<String, String>>()
            val db = readableDatabase
            val cursor = db.query("assets", arrayOf("type", "json_data"), null, null, null, null, null)
            while (cursor.moveToNext()) {
                list.add(mapOf("type" to cursor.getString(0), "json" to cursor.getString(1)))
            }
            cursor.close()
            gson.toJson(list)
        } catch (e: Exception) { "" }
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
                    if (type != null && jsonData != null) {
                        val asset = when (type) {
                            "STOCK" -> gson.fromJson(jsonData, AssetData.Stock::class.java)
                            "FII" -> gson.fromJson(jsonData, AssetData.Fii::class.java)
                            "ETF" -> gson.fromJson(jsonData, AssetData.Etf::class.java)
                            "BDR" -> gson.fromJson(jsonData, AssetData.Bdr::class.java)
                            else -> null
                        }
                        asset?.let {
                            // Aplica a lógica de "Ativo Seguro" antes de salvar para evitar nulidades
                            val safeAsset = when(it) {
                                is AssetData.Stock -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, debtToEbitda = it.debtToEbitda, cagrProfit5Years = it.cagrProfit5Years, cagrRevenue5Years = it.cagrRevenue5Years)
                                is AssetData.Fii -> it.copy(sector = it.sector ?: "", subSector = it.subSector ?: "", sharesCount = it.sharesCount, leverageScore = it.leverageScore, leverageValue = it.leverageValue, avgDailyVolume = it.avgDailyVolume)
                                is AssetData.Etf -> it.copy(sector = it.sector ?: "ETF", subSector = it.subSector ?: "ETF", sharesCount = it.sharesCount)
                                is AssetData.Bdr -> it.copy(sector = it.sector ?: "BDR", subSector = it.subSector ?: "BDR", sharesCount = it.sharesCount)
                            }
                            saveAsset(safeAsset) 
                        }
                    }
                }
                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e("ManualDB", "Erro ao importar: ${e.message}")
            false
        }
    }
}
