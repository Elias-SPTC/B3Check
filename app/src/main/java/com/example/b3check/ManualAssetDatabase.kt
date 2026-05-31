package com.example.b3check

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson

class ManualAssetDatabase(context: Context) : SQLiteOpenHelper(context, "manual_assets.db", null, 2) { // Versão aumentada para 2

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
        // Se a versão mudar, limpamos para evitar conflitos de campos nulos
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
                when (type) {
                    "STOCK" -> gson.fromJson(json, AssetData.Stock::class.java)
                    "FII" -> gson.fromJson(json, AssetData.Fii::class.java)
                    "ETF" -> gson.fromJson(json, AssetData.Etf::class.java)
                    "BDR" -> gson.fromJson(json, AssetData.Bdr::class.java)
                    else -> null
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
}
