package com.example.b3check

/**
 * Interface que define as operações de persistência de dados.
 */
interface AssetDataSource {
    fun saveAsset(asset: AssetData)
    fun getAsset(ticker: String): AssetData?
    fun deleteAsset(ticker: String)
    fun getAllAssets(): List<AssetData>
    fun exportBackup(): String
    fun importBackup(json: String): Boolean
}
