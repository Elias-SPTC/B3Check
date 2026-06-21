package com.example.b3check

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson

class ManualAssetDatabase(context: Context) : SQLiteOpenHelper(context, "assets.db", null, 7), AssetDataSource {
    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE assets (
                ticker TEXT PRIMARY KEY,
                type TEXT,
                json TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE TABLE settings (s_key TEXT PRIMARY KEY, s_value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 6) {
            db.execSQL("DROP TABLE IF EXISTS assets")
            onCreate(db)
        }
        if (old < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS settings (s_key TEXT PRIMARY KEY, s_value TEXT)")
        }
    }

    override fun getSettings(key: String): String {
        val db = readableDatabase
        val cursor = try {
            db.query("settings", arrayOf("s_value"), "s_key=?", arrayOf(key), null, null, null)
        } catch (e: Exception) { null }
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else ""
        } ?: ""
    }

    override fun saveSettings(key: String, value: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("s_key", key)
            put("s_value", value)
        }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun safeAsset(it: AssetData): AssetData {
        val sources = it.fieldSources ?: emptyMap()
        val lastUpd = it.lastUpdated
        val insights = it.qualitativeInsights ?: emptyMap()
        val asset = when(it) {
            is AssetData.Stock -> it.copy(
                sector = it.sector ?: "", subSector = it.subSector ?: "",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage,
                isInert = it.isInert,
                netEquity = it.netEquity
            )
            is AssetData.Fii -> it.copy(
                sector = it.sector ?: "", subSector = it.subSector ?: "",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage,
                isInert = it.isInert
            )
            is AssetData.Etf -> it.copy(
                sector = it.sector ?: "ETF", subSector = it.subSector ?: "ETF",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage,
                isInert = it.isInert
            )
            is AssetData.Bdr -> it.copy(
                sector = it.sector ?: "BDR", subSector = it.subSector ?: "BDR",
                userScore = it.userScore, userScorePriority = it.userScorePriority,
                userScoreAverage = it.userScoreAverage,
                isInert = it.isInert
            )
        }
        asset.fieldSources = sources
        asset.lastUpdated = lastUpd
        asset.qualitativeInsights = insights
        return asset
    }

    override fun saveAsset(asset: AssetData) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("ticker", asset.ticker)
            put("type", when(asset) {
                is AssetData.Stock -> "STOCK"
                is AssetData.Fii -> "FII"
                is AssetData.Etf -> "ETF"
                is AssetData.Bdr -> "BDR"
            })
            put("json", gson.toJson(asset))
        }
        db.insertWithOnConflict("assets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun getAsset(ticker: String): AssetData? {
        val db = readableDatabase
        val cursor = db.query("assets", null, "ticker=?", arrayOf(ticker), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) {
                val type = it.getString(it.getColumnIndexOrThrow("type"))
                val json = it.getString(it.getColumnIndexOrThrow("json"))
                val raw = when(type) {
                    "STOCK" -> gson.fromJson(json, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(json, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(json, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(json, AssetData.Bdr::class.java)
                    else -> null
                }
                raw?.let { safeAsset(it) }
            } else null
        }
    }

    override fun deleteAsset(ticker: String) {
        writableDatabase.delete("assets", "ticker=?", arrayOf(ticker))
    }

    override fun getAllAssets(): List<AssetData> {
        val list = mutableListOf<AssetData>()
        val db = readableDatabase
        val cursor = db.query("assets", null, null, null, null, null, "ticker ASC")
        cursor.use {
            while (it.moveToNext()) {
                val type = it.getString(it.getColumnIndexOrThrow("type"))
                val json = it.getString(it.getColumnIndexOrThrow("json"))
                val raw = when(type) {
                    "STOCK" -> gson.fromJson(json, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(json, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(json, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(json, AssetData.Bdr::class.java)
                    else -> null
                }
                raw?.let { list.add(safeAsset(it)) }
            }
        }
        return list
    }

    override fun exportBackup(): String {
        val all = getAllAssets()
        val backupList = all.map { asset ->
            val type = when (asset) {
                is AssetData.Stock -> "STOCK"
                is AssetData.Fii -> "FII"
                is AssetData.Etf -> "ETF"
                is AssetData.Bdr -> "BDR"
            }
            mapOf("type" to type, "json" to gson.toJson(asset))
        }
        return gson.toJson(backupList)
    }

    override fun importBackup(json: String): Boolean {
        return try {
            val typeToken = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val data: List<Map<String, String>> = gson.fromJson(json, typeToken)
            val db = writableDatabase
            db.beginTransaction()
            try {
                data.forEach { item ->
                    val type = item["type"]
                    val jsonData = item["json"]
                    val imported = when (type) {
                        "STOCK" -> gson.fromJson(jsonData, AssetData.Stock::class.java)
                        "FII" -> gson.fromJson(jsonData, AssetData.Fii::class.java)
                        "ETF" -> gson.fromJson(jsonData, AssetData.Etf::class.java)
                        "BDR" -> gson.fromJson(jsonData, AssetData.Bdr::class.java)
                        else -> null
                    }
                    
                    if (imported != null) {
                        val local = getAsset(imported.ticker)
                        if (local == null || imported.lastUpdated > local.lastUpdated) {
                            saveAsset(imported)
                        }
                    }
                }
                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            false
        }
    }
}
