package com.example.b3check

import android.content.Context

/**
 * Container de dependências (Service Locator simples).
 * No futuro, cada plataforma (Android/Desktop) terá sua própria implementação
 * deste container, injetando os componentes específicos.
 */
class B3CheckContainer(context: Context) {
    // No Android usamos o SQLite. No Desktop usaremos outra implementação.
    val dataSource: AssetDataSource = ManualAssetDatabase(context)
}
