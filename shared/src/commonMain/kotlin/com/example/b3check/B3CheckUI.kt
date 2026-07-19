package com.example.b3check

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun MainContainer(
    dataSource: AssetDataSource,
    onExport: (json: String, defaultName: String) -> Unit = { _, _ -> },
    onImport: (onResult: (String) -> Unit) -> Unit = {}
) {
    val viewModel: StockViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StockViewModel(dataSource) }
        }
    )

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val filterOptions = listOf("Todos", "Ações", "FII", "ETF", "BDR")
    var selectedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentFilter = filterOptions[selectedFilterIndex]

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Análise", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Ativos", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    label = { Text("Carteira", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Favoritos", fontSize = 10.sp, maxLines = 1) }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Investir", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { currentTab = 5 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("IA Global", fontSize = 10.sp) }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (currentTab in 1..4) {
                ScrollableTabRow(
                    selectedTabIndex = selectedFilterIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    filterOptions.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedFilterIndex == index,
                            onClick = { selectedFilterIndex = index },
                            text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
            
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> StockAnalysisScreen(viewModel)
                    1 -> AssetListScreen(viewModel, onAssetClick = { currentTab = 0 }, onExport, onImport, currentFilter)
                    2 -> PortfolioBalanceScreen(viewModel, currentFilter)
                    3 -> RecommendationsScreen(viewModel, currentFilter)
                    4 -> InvestScreen(viewModel, currentFilter)
                    5 -> GlobalAiScreen(viewModel, onExport)
                }
            }
        }
    }
}

@Composable
fun GlobalAiScreen(viewModel: StockViewModel, onExport: (String, String) -> Unit = { _, _ -> }) {
    var question by remember { mutableStateOf("") }
    val response by viewModel.globalAiResponse.collectAsState()
    val status by viewModel.aiStatus.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Inteligência Global da Carteira", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Faça perguntas analíticas sobre o conjunto total dos seus ativos salvos no banco de dados.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f).height(100.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).padding(8.dp),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                decorationBox = { inner -> if (question.isEmpty()) Text("Sua pergunta...", color = Color.Gray); inner() }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (question.isNotBlank()) viewModel.askAiGlobal(question) },
                enabled = status != "Analisando Carteira..." && question.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (status == "Analisando Carteira...") "..." else "Perguntar")
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(12.dp).verticalScroll(rememberScrollState())) {
            if (response == null) {
                Text("Aguardando pergunta...\nExemplos:\n- Quais ativos têm maior risco de liquidez?\n- Qual setor está com melhor nota média?\n- Quais FIIs de tijolo têm menor vacância?", color = Color.Gray)
            } else {
                Text(response!!, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (response != null) {
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = { 
                    val textToSave = "PERGUNTA:\n$question\n\nRESPOSTA DA IA:\n${response!!}"
                    onExport(textToSave, "Analise-IA-${viewModel.getCurrentDate()}.txt") 
                }) {
                    Text("Gravar TXT", fontSize = 12.sp)
                }
                TextButton(onClick = { viewModel.clearGlobalAi(); question = "" }) {
                    Text("Limpar Análise", color = Color.Red, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun AssetListScreen(
    viewModel: StockViewModel, 
    onAssetClick: () -> Unit = {},
    onExport: (String, String) -> Unit = { _, _ -> },
    onImport: (onResult: (String) -> Unit) -> Unit = {},
    currentFilter: String = "Todos"
) {
    val assets by viewModel.allAssets.collectAsState()
    val filteredAssets = remember(assets, currentFilter) {
        if (currentFilter == "Todos") assets
        else assets.filter { asset ->
            when (currentFilter) {
                "Ações" -> asset is AssetData.Stock
                "FII" -> asset is AssetData.Fii
                "ETF" -> asset is AssetData.Etf
                "BDR" -> asset is AssetData.Bdr
                else -> true
            }
        }
    }
    val status by viewModel.aiStatus.collectAsState()
    var tickerToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showIntegrityReport by rememberSaveable { mutableStateOf(false) }
    var showRecalcSuccess by rememberSaveable { mutableStateOf(false) }
    var showImportSuccess by rememberSaveable { mutableStateOf(false) }
    var showExportSuccess by rememberSaveable { mutableStateOf(false) }
    var importDataPending by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAllAssets()
    }

    if (importDataPending != null) {
        AlertDialog(
            onDismissRequest = { importDataPending = null },
            title = { Text("Importar Dados", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Deseja mesclar os dados (mantendo as versões mais recentes) ou restaurar completamente (substituir tudo pelo backup)?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(onClick = {
                    viewModel.importBackup(importDataPending!!, force = true)
                    importDataPending = null
                    showImportSuccess = true
                }) { Text("Restaurar Tudo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.importBackup(importDataPending!!, force = false)
                    importDataPending = null
                    showImportSuccess = true
                }) { Text("Mesclar") }
            }
        )
    }

    if (tickerToDelete != null) {
        AlertDialog(
            onDismissRequest = { tickerToDelete = null },
            title = { Text("Excluir Ativo", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Tem certeza que deseja excluir o ativo $tickerToDelete?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAsset(tickerToDelete!!)
                    tickerToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { tickerToDelete = null }) { Text("Cancelar", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    if (showIntegrityReport) {
        val assetsWithIssues = assets.map { it to viewModel.getIntegrityWarnings(it) }.filter { it.second.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { showIntegrityReport = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Integridade", color = MaterialTheme.colorScheme.onSurface)
            }},
            text = {
                if (assetsWithIssues.isEmpty()) Text("✅ Tudo OK!", color = MaterialTheme.colorScheme.onSurface)
                else LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(assetsWithIssues) { (asset, warnings) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(asset.ticker, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            warnings.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showIntegrityReport = false }) { Text("Fechar") } }
        )
    }

    if (showRecalcSuccess) {
        AlertDialog(
            onDismissRequest = { showRecalcSuccess = false },
            title = { Text("Processamento", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("✅ Recálculo de todos os ativos concluído com sucesso!", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = { Button(onClick = { showRecalcSuccess = false }) { Text("Fechar") } }
        )
    }

    if (showImportSuccess) {
        AlertDialog(
            onDismissRequest = { showImportSuccess = false },
            title = { Text("Importação", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("✅ Banco de dados importado e mesclado com sucesso!", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = { Button(onClick = { showImportSuccess = false }) { Text("Fechar") } }
        )
    }

    if (showExportSuccess) {
        AlertDialog(
            onDismissRequest = { showExportSuccess = false },
            title = { Text("Exportação", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("✅ Backup gerado com sucesso!", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = { Button(onClick = { showExportSuccess = false }) { Text("Fechar") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Ativos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (status != "Pronto" && status != "Chave Ausente") {
                    Text(status, fontSize = 11.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                }
            }
            Row {
                IconButton(onClick = { onImport { importDataPending = it } }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { viewModel.researchAllMarketScores() }) { Icon(Icons.Default.Public, null, tint = Color(0xFFE65100)) }
                IconButton(onClick = { showIntegrityReport = true }) { Icon(Icons.Default.List, null, tint = Color(0xFFE65100)) }
                IconButton(onClick = { viewModel.recalculateAllScores(); showRecalcSuccess = true }) { Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { onExport(viewModel.exportBackup(), "${viewModel.getCurrentDate()}-B3Check.json"); showExportSuccess = true }) { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(filteredAssets) { asset ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { viewModel.lookupTicker(asset.ticker); onAssetClick() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                val pvp = when(asset) {
                                    is AssetData.Stock -> asset.pvp
                                    is AssetData.Fii -> asset.pvp
                                    else -> 0.0
                                }
                                if (pvp > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("P/VP: ${formatBR(pvp)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(asset.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable { 
                                val updated = when(asset) {
                                    is AssetData.Stock -> asset.copy(isInPortfolio = !asset.isInPortfolio)
                                    is AssetData.Fii -> asset.copy(isInPortfolio = !asset.isInPortfolio)
                                    is AssetData.Etf -> asset.copy(isInPortfolio = !asset.isInPortfolio)
                                    is AssetData.Bdr -> asset.copy(isInPortfolio = !asset.isInPortfolio)
                                }
                                viewModel.saveManualAsset(updated)
                            }) {
                                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(24.dp), tint = if(asset.isInPortfolio) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                if(!asset.isInPortfolio) Icon(Icons.Default.Close, null, modifier = Modifier.size(24.dp), tint = Color.Red)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable { 
                                val updated = when(asset) {
                                    is AssetData.Stock -> asset.copy(isInert = !asset.isInert)
                                    is AssetData.Fii -> asset.copy(isInert = !asset.isInert)
                                    is AssetData.Etf -> asset.copy(isInert = !asset.isInert)
                                    is AssetData.Bdr -> asset.copy(isInert = !asset.isInert)
                                }
                                viewModel.saveManualAsset(updated)
                            }) {
                                Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(24.dp), tint = if(asset.isInert) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                if(!asset.isInert) Icon(Icons.Default.Close, null, modifier = Modifier.size(24.dp), tint = Color.Red)
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { tickerToDelete = asset.ticker }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioBalanceScreen(viewModel: StockViewModel, currentFilter: String = "Todos") {
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val allAssets by viewModel.allAssets.collectAsState()
    val portfolio = remember(allAssets) { allAssets.filter { it.isInPortfolio } }
    val totalVal = remember(portfolio) { portfolio.sumOf { it.sharesCount * it.currentPrice } }

    val filteredAllocation = remember(allocation, currentFilter) {
        if (currentFilter == "Todos") allocation
        else allocation.filter { (asset, _) ->
            when (currentFilter) {
                "Ações" -> asset is AssetData.Stock
                "FII" -> asset is AssetData.Fii
                "ETF" -> asset is AssetData.Etf
                "BDR" -> asset is AssetData.Bdr
                else -> true
            }
        }
    }

    val equilibriumAmount = remember(portfolio, allocation) {
        if (totalVal == 0.0 || allocation.isEmpty()) return@remember 0.0
        val requiredTotals = portfolio.map { a ->
            val idealP = allocation.find { it.first.ticker == a.ticker }?.second ?: 0.0
            if (idealP > 0) (a.sharesCount * a.currentPrice) / (idealP / 100.0) else 0.0
        }
        val targetTotalValue = requiredTotals.maxOrNull() ?: 0.0
        (targetTotalValue - totalVal).coerceAtLeast(0.0)
    }

    val targetTotalPortfolio = totalVal + equilibriumAmount

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Equilíbrio da Carteira", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))
        filteredAllocation.forEach { (asset, ideal) ->
            val curVal = asset.sharesCount * asset.currentPrice
            val curPerc = if (totalVal > 0) (curVal / totalVal) * 100.0 else 0.0
            val curColor = if (curPerc < ideal) Color.Red else MaterialTheme.colorScheme.onSurface

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(asset.ticker, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = AnnotatedString("Atual: ") + AnnotatedString(formatBR(curPerc) + "%", spanStyle = androidx.compose.ui.text.SpanStyle(color = curColor)) + 
                                   AnnotatedString("  |  Ideal: ${formatBR(ideal)}%  |  Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    if (curPerc < ideal && asset.currentPrice > 0) {
                        val targetAssetVal = targetTotalPortfolio * (ideal / 100.0)
                        val gapVal = (targetAssetVal - curVal).coerceAtLeast(0.0)
                        val qtyToBuy = (gapVal / asset.currentPrice).toInt()
                        
                        if (qtyToBuy > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val baseText = "${qtyToBuy} un"
                                val lotInfo = if (qtyToBuy >= 100 && asset is AssetData.Stock) {
                                    val lots = qtyToBuy / 100
                                    val rem = qtyToBuy % 100
                                    " ($lots lot${if(lots>1) "es" else "e"}${if(rem>0) " + $rem" else ""})"
                                } else ""
                                
                                Text(text = baseText + lotInfo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = formatBR(qtyToBuy * asset.currentPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (equilibriumAmount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Para equilíbrio total é necessário:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text(formatBR(equilibriumAmount), fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun InvestScreen(viewModel: StockViewModel, currentFilter: String = "Todos") {
    val assets by viewModel.allAssets.collectAsState()
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val portfolio = remember(assets, currentFilter) { 
        val p = assets.filter { it.isInPortfolio }
        if (currentFilter == "Todos") p.sortedBy { it.ticker }
        else p.filter { asset ->
            when (currentFilter) {
                "Ações" -> asset is AssetData.Stock
                "FII" -> asset is AssetData.Fii
                "ETF" -> asset is AssetData.Etf
                "BDR" -> asset is AssetData.Bdr
                else -> true
            }
        }.sortedBy { it.ticker }
    }
    var investAmountStr by rememberSaveable { mutableStateOf("") }
    var showLots by rememberSaveable { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val editStates = remember { mutableStateMapOf<String, String>() }
    val selectedTickers = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(portfolio) {
        portfolio.forEach { if (!selectedTickers.containsKey(it.ticker)) selectedTickers[it.ticker] = true }
    }

    val selectedPortfolio = remember(portfolio, selectedTickers.toMap()) {
        portfolio.filter { selectedTickers[it.ticker] == true }
    }

    val investAmount = parseBR(investAmountStr)
    val currentTotal = remember(portfolio) { portfolio.sumOf { it.sharesCount * it.currentPrice } }
    val targetTotal = currentTotal + investAmount

    val suggestions = remember(targetTotal, portfolio, selectedPortfolio, allocation) {
        val sharesToBuy = mutableMapOf<String, Int>()
        val investSuggestions = mutableMapOf<String, Double>()
        var remainingCash = investAmount

        if (investAmount > 0 && selectedPortfolio.isNotEmpty()) {
            val activePortfolio = selectedPortfolio.filter { !it.isInert }
            if (activePortfolio.isNotEmpty()) {
                while (true) {
                    val currentTotalSim = portfolio.sumOf { it.sharesCount * it.currentPrice } + 
                        sharesToBuy.entries.sumOf { (ticker, qty) -> 
                            (portfolio.find { it.ticker == ticker }?.currentPrice ?: 0.0) * qty 
                        }
                    
                    val bestAsset = activePortfolio
                        .filter { it.currentPrice <= remainingCash && it.currentPrice > 0 }
                        .maxByOrNull { a ->
                            val currentValSim = (a.sharesCount * a.currentPrice) + ((sharesToBuy[a.ticker] ?: 0) * a.currentPrice)
                            val currentPerc = if (currentTotalSim > 0) (currentValSim / currentTotalSim) * 100.0 else 0.0
                            val idealPerc = allocation.find { it.first.ticker == a.ticker }?.second ?: 0.0
                            idealPerc - currentPerc
                        }

                    if (bestAsset != null) {
                        sharesToBuy[bestAsset.ticker] = (sharesToBuy[bestAsset.ticker] ?: 0) + 1
                        investSuggestions[bestAsset.ticker] = (sharesToBuy[bestAsset.ticker] ?: 0) * bestAsset.currentPrice
                        remainingCash -= bestAsset.currentPrice
                    } else break
                }
            }
        }
        sharesToBuy to investSuggestions
    }

    val lotSuggestions = remember(suggestions, portfolio) {
        val sharesToBuy = mutableMapOf<String, Int>()
        val investSuggestions = mutableMapOf<String, Double>()
        
        suggestions.first.forEach { (ticker, qty) ->
            val asset = portfolio.find { it.ticker == ticker }
            val roundedQty = if (asset is AssetData.Stock) {
                ((qty + 50) / 100) * 100
            } else {
                qty
            }
            
            if (roundedQty > 0) {
                sharesToBuy[ticker] = roundedQty
                investSuggestions[ticker] = roundedQty * (asset?.currentPrice ?: 0.0)
            }
        }
        sharesToBuy to investSuggestions
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isEditMode) "Edição Rápida" else "Simulador de Aportes", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
            IconButton(onClick = { isEditMode = !isEditMode }) {
                Icon(if (isEditMode) Icons.Default.CheckCircle else Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1976D2))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isEditMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ticker", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Cotas", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
                Text("Preço", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(portfolio) { asset ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(asset.ticker, modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        
                        BasicTextField(
                            value = editStates["${asset.ticker}_c"] ?: formatBR(asset.sharesCount, true),
                            onValueChange = { 
                                editStates["${asset.ticker}_c"] = it
                                val n = parseBR(it)
                                val updated = when(asset) {
                                    is AssetData.Stock -> asset.copy(sharesCount = n)
                                    is AssetData.Fii -> asset.copy(sharesCount = n)
                                    is AssetData.Etf -> asset.copy(sharesCount = n)
                                    is AssetData.Bdr -> asset.copy(sharesCount = n)
                                }
                                viewModel.saveManualAsset(updated)
                            },
                            modifier = Modifier.weight(1.2f).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                            textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { inner() } }
                        )

                        Spacer(Modifier.width(8.dp))

                        BasicTextField(
                            value = editStates["${asset.ticker}_p"] ?: formatBR(asset.currentPrice, true),
                            onValueChange = { 
                                editStates["${asset.ticker}_p"] = it
                                val n = parseBR(it)
                                val updated = when(asset) {
                                    is AssetData.Stock -> asset.copy(currentPrice = n)
                                    is AssetData.Fii -> asset.copy(currentPrice = n)
                                    is AssetData.Etf -> asset.copy(currentPrice = n)
                                    is AssetData.Bdr -> asset.copy(currentPrice = n)
                                }
                                viewModel.saveManualAsset(updated)
                            },
                            modifier = Modifier.weight(1.2f).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                            textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { inner() } }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
            Button(onClick = { isEditMode = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Concluir Edição")
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = portfolio.isNotEmpty() && selectedPortfolio.size == portfolio.size,
                    onCheckedChange = { isChecked -> portfolio.forEach { selectedTickers[it.ticker] = isChecked } },
                    modifier = Modifier.size(32.dp).padding(end = 4.dp)
                )
                Text("Ticker", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Cotas", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
                Text("Preço", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
                Text("Montante", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
                
                Row(modifier = Modifier.weight(1.4f).clickable { showLots = !showLots }, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showLots) "Lotes" else "Unidades", fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = if (showLots) Color(0xFF1976D2) else Color(0xFF2E7D32))
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(12.dp).padding(start = 2.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(portfolio) { index, asset ->
                    val qtySuggest = suggestions.first[asset.ticker] ?: 0
                    val montanteSug = suggestions.second[asset.ticker] ?: 0.0
                    val qtyLotSuggest = lotSuggestions.first[asset.ticker] ?: 0
                    val montanteLotSug = lotSuggestions.second[asset.ticker] ?: 0.0

                    val rowBg = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                    val montanteAtual = asset.sharesCount * asset.currentPrice
                    
                    Row(modifier = Modifier.fillMaxWidth().background(rowBg).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedTickers[asset.ticker] ?: false,
                            onCheckedChange = { selectedTickers[asset.ticker] = it },
                            modifier = Modifier.size(32.dp).padding(end = 4.dp)
                        )
                        Text(asset.ticker, modifier = Modifier.weight(1.0f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        
                        Text(
                            text = formatBR(asset.sharesCount),
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.End,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = formatBR(asset.currentPrice),
                            modifier = Modifier.weight(0.9f),
                            textAlign = TextAlign.End,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = formatBR(montanteAtual),
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )

                        Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                            if (!showLots) {
                                if (qtySuggest > 0) { 
                                    Text("${qtySuggest} un", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formatBR(montanteSug), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) 
                                } else Text("-", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            } else {
                                if (qtyLotSuggest > 0) {
                                    val label = if (asset is AssetData.Stock) {
                                        val lots = qtyLotSuggest / 100
                                        "${lots} lot" + (if(lots>1) "es" else "e")
                                    } else "${qtyLotSuggest} un"
                                    
                                    Text(label, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(formatBR(montanteLotSug), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                } else Text("-", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        val totalCurrentVal = portfolio.sumOf { it.sharesCount * it.currentPrice }
        val totalSugVal = suggestions.second.values.sum()
        val totalSugQty = suggestions.first.values.sum()
        val totalLotSugVal = lotSuggestions.second.values.sum()
        val totalLotSugQty = lotSuggestions.first.values.sum()

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(32.dp).padding(end = 4.dp))
                Text("TOTAIS", modifier = Modifier.weight(3.0f), fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                
                Text(
                    text = formatBR(totalCurrentVal), 
                    modifier = Modifier.weight(1.2f), 
                    textAlign = TextAlign.End, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 10.sp, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                    if (!showLots) {
                        Text("${totalSugQty} un", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                        Text(formatBR(totalSugVal), fontWeight = FontWeight.Bold, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text("${totalLotSugQty} un", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF1976D2))
                        Text(formatBR(totalLotSugVal), fontWeight = FontWeight.Bold, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        BasicTextField(
            value = investAmountStr, 
            onValueChange = { investAmountStr = it }, 
            modifier = Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(8.dp),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { inner ->
                if (investAmountStr.isEmpty()) Text("Valor do aporte", color = Color.Gray, fontSize = 14.sp)
                inner()
            }
        )
        if (investAmount > 0) {
            val realInvested = suggestions.second.values.sum()
            val realLotInvested = lotSuggestions.second.values.sum()
            
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Unidades: R$ ${formatBR(realInvested)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Sobras: R$ ${formatBR(investAmount - realInvested)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Lotes: R$ ${formatBR(realLotInvested)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    Text("Sobras: R$ ${formatBR(investAmount - realLotInvested)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun RecommendationsScreen(viewModel: StockViewModel, currentFilter: String = "Todos") {
    val recs by viewModel.recommendations.collectAsState()
    val filteredRecs = remember(recs, currentFilter) {
        if (currentFilter == "Todos") recs
        else recs.filter { asset ->
            when (currentFilter) {
                "Ações" -> asset is AssetData.Stock
                "FII" -> asset is AssetData.Fii
                "ETF" -> asset is AssetData.Etf
                "BDR" -> asset is AssetData.Bdr
                else -> true
            }
        }
    }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var investAmount by rememberSaveable { mutableStateOf("") }
    
    val scored = remember(filteredRecs) { filteredRecs.map { it to viewModel.calculateScoreForAsset(it) } }
    val sortedScored = remember(scored) {
        scored.sortedWith(compareByDescending<Pair<AssetData, Double>> { it.second }.thenBy { it.first.ticker })
    }
    val totalScore = sortedScored.filter { selected[it.first.ticker] == true }.sumOf { it.second }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Favoritos", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(sortedScored) { index, (asset, score) ->
                val qtySuggest = remember(totalScore, investAmount, selected[asset.ticker]) {
                    val amt = parseBR(investAmount)
                    if (amt > 0 && totalScore > 0 && selected[asset.ticker] == true) {
                        (amt * (score / totalScore) / asset.currentPrice).toInt()
                    } else 0
                }

                val pvp = when(asset) { is AssetData.Stock -> asset.pvp; is AssetData.Fii -> asset.pvp; else -> 0.0 }
                val isSelected = selected[asset.ticker] ?: false

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clickable { selected[asset.ticker] = !isSelected },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected, 
                            onCheckedChange = { selected[asset.ticker] = it },
                            modifier = Modifier.size(32.dp).padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                if (pvp > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("P/VP: ${formatBR(pvp)}", fontSize = 11.sp, color = if(pvp<1.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Nota: ${formatBR(score)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (qtySuggest > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${qtySuggest} un", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (qtySuggest >= 100 && asset is AssetData.Stock) {
                                    val lots = qtySuggest / 100
                                    val rem = qtySuggest % 100
                                    Text("${lots} lotes" + (if(rem>0) " + $rem" else ""), fontSize = 11.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        BasicTextField(value = investAmount, onValueChange = { investAmount = it }, modifier = Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(8.dp), textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), decorationBox = { inner -> if (investAmount.isEmpty()) Text("Simular aporte", color = Color.Gray); inner() })
    }
}

@Composable
fun StockAnalysisScreen(viewModel: StockViewModel) {
    var tickerInput by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val allAssets by viewModel.allAssets.collectAsState()

    LaunchedEffect(uiState) { if (uiState is StockUiState.Success) tickerInput = (uiState as StockUiState.Success).data.ticker }
    LaunchedEffect(tickerInput) { if (tickerInput.length >= 4) viewModel.lookupTicker(tickerInput) else if (tickerInput.isEmpty()) viewModel.resetAnalysis() }

    fun navigate(next: Boolean) {
        val cur = (uiState as? StockUiState.Success)?.data?.ticker ?: ""
        val idx = allAssets.indexOfFirst { it.ticker == cur }
        if (idx != -1) tickerInput = allAssets[if (next) (idx + 1) % allAssets.size else (idx - 1 + allAssets.size) % allAssets.size].ticker
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()).pointerInput(allAssets) {
        var totalDrag = 0f
        detectHorizontalDragGestures(onDragEnd = { if (totalDrag > 250) navigate(false) else if (totalDrag < -250) navigate(true); totalDrag = 0f }, onHorizontalDrag = { _, amt -> totalDrag += amt })
    }) {
        BasicTextField(
            value = tickerInput, 
            onValueChange = { tickerInput = it.uppercase() }, 
            modifier = Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(8.dp),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black),
            singleLine = true,
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (tickerInput.isEmpty()) Text("Ticker", color = Color.Gray)
                        inner()
                    }
                    if (tickerInput.isNotEmpty()) IconButton(onClick = { tickerInput = ""; viewModel.resetAnalysis() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null) }
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (val state = uiState) {
            is StockUiState.Success -> {
                val liveData = allAssets.find { it.ticker == state.data.ticker } ?: state.data
                ScoreHeader(liveData, state.score, viewModel, { viewModel.saveManualAsset(it) })
                ManualEditor(liveData, state.score, { viewModel.saveManualAsset(it) }, {}, { viewModel.deleteAsset(it.ticker); tickerInput = "" })
            }
            is StockUiState.Error -> if (tickerInput.length >= 4) Row { Button(onClick = { viewModel.addManualAsset(tickerInput, "Ação") }) { Text("Ação") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "FII") }) { Text("FII") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "ETF") }) { Text("ETF") } }
            else -> {}
        }
    }
}

@Composable
fun ScoreHeader(data: AssetData, finalScore: Double, viewModel: StockViewModel, onSave: (AssetData) -> Unit) {
    val motorScore = remember(data) {
        val raw = when(data) {
            is AssetData.Stock -> data.copy(userScore = 0.0, marketScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Fii -> data.copy(userScore = 0.0, marketScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Etf -> data.copy(userScore = 0.0, marketScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Bdr -> data.copy(userScore = 0.0, marketScore = 0.0, userScorePriority = false, userScoreAverage = false)
        }
        viewModel.calculateScoreForAsset(raw)
    }

    val avgScore = remember(motorScore, data.userScore, data.marketScore) {
        val scores = mutableListOf<Double>()
        scores.add(motorScore)
        if (data.userScore > 0) scores.add(data.userScore)
        if (data.marketScore > 0) scores.add(data.marketScore)
        scores.average()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(data.ticker, fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(data.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.researchMarketScore(data.ticker) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Public, null, modifier = Modifier.size(20.dp), tint = Color(0xFFE65100))
            }
            ScoreIndicator("Motor", motorScore, MaterialTheme.colorScheme.onSurface)
            ScoreIndicator(
                "Manual", 
                data.userScore, 
                MaterialTheme.colorScheme.secondary,
                checked = data.userScorePriority,
                onCheck = { isChecked ->
                    val updated = when(data) {
                        is AssetData.Stock -> data.copy(userScorePriority = isChecked, userScoreAverage = false)
                        is AssetData.Fii -> data.copy(userScorePriority = isChecked, userScoreAverage = false)
                        is AssetData.Etf -> data.copy(userScorePriority = isChecked, userScoreAverage = false)
                        is AssetData.Bdr -> data.copy(userScorePriority = isChecked, userScoreAverage = false)
                    }
                    onSave(updated)
                }
            )
            ScoreIndicator(
                "Mercado",
                data.marketScore,
                Color(0xFFE65100)
            )
            ScoreIndicator(
                "Média", 
                avgScore, 
                Color(0xFF1976D2),
                checked = data.userScoreAverage,
                onCheck = { isChecked ->
                    val updated = when(data) {
                        is AssetData.Stock -> data.copy(userScoreAverage = isChecked, userScorePriority = false)
                        is AssetData.Fii -> data.copy(userScoreAverage = isChecked, userScorePriority = false)
                        is AssetData.Etf -> data.copy(userScoreAverage = isChecked, userScorePriority = false)
                        is AssetData.Bdr -> data.copy(userScoreAverage = isChecked, userScorePriority = false)
                    }
                    onSave(updated)
                }
            )
            ScoreIndicator("FINAL", finalScore, Color(0xFF2E7D32), true)
        }
        AssetDetails(data)
    }
}

@Composable
fun ScoreIndicator(l: String, v: Double, c: Color, main: Boolean = false, checked: Boolean? = null, onCheck: ((Boolean) -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatBR(v), fontSize = if (main) 22.sp else 16.sp, fontWeight = FontWeight.Bold, color = c)
            if (checked != null && onCheck != null) {
                Checkbox(checked = checked, onCheckedChange = onCheck, modifier = Modifier.size(24.dp).padding(start = 2.dp))
            }
        }
    }
}

@Composable
fun ManualEditor(data: AssetData, score: Double, onSave: (AssetData) -> Unit, onAnalyze: (AssetData) -> Unit, onDelete: (AssetData) -> Unit) {
    var nameState by remember(data.ticker) { mutableStateOf(data.name) }
    var priceState by remember(data.ticker) { mutableStateOf(formatBR(data.currentPrice, true)) }
    var sectorState by remember(data.ticker) { mutableStateOf(data.sector) }
    var subSectorState by remember(data.ticker) { mutableStateOf(data.subSector) }
    var inPortfolioState by remember(data.ticker) { mutableStateOf(data.isInPortfolio) }
    var isInertState by remember(data.ticker) { mutableStateOf(data.isInert) }
    val indicatorStates = remember(data.ticker) { mutableStateMapOf<String, String>() }
    val sourceStates = remember(data.ticker) { mutableStateMapOf<String, FieldSource?>() }
    var helpDialogType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(data) {
        indicatorStates.clear()
        sourceStates.clear()
        indicatorStates["uScore"] = formatBR(data.userScore, true)
        
        val fields = if (data is AssetData.Stock) {
            val sub = subSectorState.lowercase()
            val isBank = sub.contains("banco")
            val isInsurance = sub.contains("seguradora")
            val isHolding = sub.contains("holding")
            
            mutableListOf("lpa", "vpa", "roe", "dy", "dy5", "ml", "pl", "pvp", "payout", "vol", "netEquity").apply {
                if (!isBank && !isInsurance && !isHolding) { add("de"); add("deEbitda"); add("cRec") }
                if (!isBank) { add("cLuc") }
                if (isBank) { add("basel") }
                add("graham"); add("bazin")
            }
        } else if (data is AssetData.Fii) {
            val isPapel = sectorState.lowercase().contains("papel")
            mutableListOf("pvp", "y12", "y5", "vol", "aum", "mFee", "mLev", "mType", "lScore").apply {
                if (!isPapel) { add("vac"); add("prop"); add("walt"); add("tScore") }
            }
        } else if (data is AssetData.Etf) {
            listOf("aFee", "te", "vol", "hold", "aum")
        } else if (data is AssetData.Bdr) {
            listOf("dy", "par")
        } else emptyList()

        fields.forEach { f -> sourceStates[f] = data.fieldSources?.get(f) }
        indicatorStates["cotas"] = formatBR(data.sharesCount, true)

        if (data is AssetData.Stock) {
            indicatorStates["lpa"] = formatSmart(data.lpa, true); indicatorStates["vpa"] = formatSmart(data.vpa, true); indicatorStates["roe"] = formatBR(data.roe * 100, true); indicatorStates["dy"] = formatBR(data.dividendYield * 100, true); indicatorStates["dy5"] = formatBR(data.dividendYield5Years * 100, true); indicatorStates["de"] = formatSmart(data.debtToEquity, true); indicatorStates["deEbitda"] = formatSmart(data.debtToEbitda, true); indicatorStates["ml"] = formatBR(data.netMargin * 100, true); indicatorStates["pl"] = formatSmart(data.pl, true); indicatorStates["pvp"] = formatSmart(data.pvp, true); indicatorStates["payout"] = formatBR(data.payout * 100, true); indicatorStates["basel"] = formatBR(data.baselIndex * 100, true); indicatorStates["graham"] = formatSmart(data.grahamPrice, true); indicatorStates["bazin"] = formatSmart(data.bazinPrice, true); indicatorStates["cLuc"] = formatBR(data.cagrProfit5Years * 100, true); indicatorStates["cRec"] = formatBR(data.cagrRevenue5Years * 100, true); indicatorStates["vol"] = formatSmart(data.avgDailyVolume, true); indicatorStates["netEquity"] = formatSmart(data.netEquity, true)
        } else if (data is AssetData.Fii) {
            indicatorStates["pvp"] = formatSmart(data.pvp, true); indicatorStates["vac"] = formatBR(data.vacancy * 100, true); indicatorStates["y12"] = formatBR(data.yield12m * 100, true); indicatorStates["y5"] = formatBR(data.avgYield5Years * 100, true); indicatorStates["vol"] = formatSmart(data.avgDailyVolume, true); indicatorStates["prop"] = if(data.propertyCount == 0) "" else data.propertyCount.toString(); indicatorStates["aum"] = formatSmart(data.aum, true); indicatorStates["mFee"] = formatBR(data.managementFee * 100, true); indicatorStates["walt"] = formatSmart(data.weightedLeaseTerm, true); indicatorStates["mLev"] = formatBR(data.leverageValue * 100, true); indicatorStates["mType"] = data.managementType; indicatorStates["tScore"] = if(data.tenantScore == 0) "" else data.tenantScore.toString(); indicatorStates["lScore"] = if(data.leverageScore == 0) "" else data.leverageScore.toString()
        } else if (data is AssetData.Etf) {
            indicatorStates["aFee"] = formatBR(data.adminFee * 100, true); indicatorStates["te"] = formatBR(data.trackingError * 100, true); indicatorStates["vol"] = formatSmart(data.avgDailyVolume, true); indicatorStates["hold"] = if(data.numberOfHoldings == 0) "" else data.numberOfHoldings.toString(); indicatorStates["aum"] = formatSmart(data.aum, true)
        } else if (data is AssetData.Bdr) {
            indicatorStates["dy"] = formatBR(data.dividendYield * 100, true); indicatorStates["par"] = data.parity
        }
    }

    if (helpDialogType != null) {
        AlertDialog(onDismissRequest = { helpDialogType = null }, title = { Text(if (helpDialogType == "tenant") "Inquilinos" else "Alavancagem", fontWeight = FontWeight.Bold) }, text = { val help = if (helpDialogType == "tenant") "Nota 0: Monoinquilino (100% da renda).\nNota 1-2: Risco Alto (>30% ou <5 loc).\nNota 3: Risco Moderado (20-30%).\nNota 4: Risco Baixo (15-30 loc).\nNota 5: Excelente (<10% base pulverizada)." else "Nota 5: Excelente (0-5% LTV).\nNota 4: Conservador (5-15% LTV).\nNota 3: Moderado (15-25% LTV).\nNota 2: Risco Elevado (>25% LTV).\nNota 1: Muito Elevado (>35% LTV).\nNota 0: Crítico (>40% LTV)."; Text(help, fontSize = 13.sp) }, confirmButton = { TextButton(onClick = { helpDialogType = null }) { Text("OK") } })
    }

    val stockClassification = mapOf("Financeiro" to listOf("Bancos", "Seguradoras", "Holdings", "Serviços Financeiros", "Exploração de Imóveis"), "Utilidade Pública" to listOf("Energia Elétrica", "Água e Saneamento", "Gás"), "Materiais Básicos" to listOf("Mineração", "Siderurgia e Metalurgia", "Papel e Celulose", "Químicos"), "Petróleo e Gás" to listOf("Extração e Refino", "Equipamentos e Serviços", "Biocombustíveis"), "Telecomunicações" to listOf("Telefonia Fixa e Móvel"), "Consumo Cíclico" to listOf("Comércio", "Construção Civil", "Roupas", "Turismo", "Veículos"), "Consumo Não Cíclico e Saúde" to listOf("Alimentos", "Bebidas", "Agropecuária", "Hospitais", "Laboratórios", "Farmácias", "Uso P pessoal e Limpeza"), "Bens Industriais" to listOf("Transporte e Logística", "Máquinas e Equipamentos", "Defesa e Aeroespacial"))
    val fiiClassification = mapOf("Tijolo" to listOf("Lajes Corporativas", "Logística / Industrial", "Shopping Centers", "Hotéis", "Hospitais", "Agências bancárias", "Educacional", "Residencial", "Fiagros"), "Papel" to listOf("Recebíveis Imobiliários", "Fundos de Fundos (FOFs)"), "Híbridos" to listOf("Geral"))
    var showSectorMenu by remember { mutableStateOf(false) }; var showSubSectorMenu by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Carteira", modifier = Modifier.weight(1f)); Switch(checked = inPortfolioState, onCheckedChange = { inPortfolioState = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Ativo Inerte", modifier = Modifier.weight(1f)); Switch(checked = isInertState, onCheckedChange = { isInertState = it }) }
        if (data is AssetData.Stock || data is AssetData.Fii) {
            val currentMap = if (data is AssetData.Stock) stockClassification else fiiClassification
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Setor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box(modifier = Modifier.weight(1.8f)) { OutlinedButton(onClick = { showSectorMenu = true }, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) { Text(if (sectorState.isBlank()) "Selecionar" else sectorState, fontSize = 13.sp) }; DropdownMenu(expanded = showSectorMenu, onDismissRequest = { showSectorMenu = false }) { currentMap.keys.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { sectorState = s; subSectorState = ""; showSectorMenu = false }) } } }
            }
            if (sectorState.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subsetor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Box(modifier = Modifier.weight(1.8f)) { OutlinedButton(onClick = { showSubSectorMenu = true }, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) { Text(if (subSectorState.isBlank()) "Selecionar" else subSectorState, fontSize = 13.sp) }; DropdownMenu(expanded = showSubSectorMenu, onDismissRequest = { showSubSectorMenu = false }) { currentMap[sectorState]?.forEach { ss -> DropdownMenuItem(text = { Text(ss) }, onClick = { subSectorState = ss; showSubSectorMenu = false }) } } }
                }
            }
        }
        EditRow("Nome", nameState) { nameState = it }; EditRow("Preço", priceState, true) { priceState = it }; EditRow("Cotas", indicatorStates["cotas"] ?: "", true) { indicatorStates["cotas"] = it }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Minha Nota", modifier = Modifier.weight(1f), fontSize = 12.sp)
            BasicTextField(value = indicatorStates["uScore"] ?: "", onValueChange = { indicatorStates["uScore"] = it }, modifier = Modifier.weight(1.8f).height(32.dp), textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), decorationBox = { inner -> Surface(shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)) { Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { inner() } } } )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (data is AssetData.Stock) {
            val isBank = subSectorState.lowercase().contains("banco")
            val isInsurance = subSectorState.lowercase().contains("seguradora")
            val isHolding = subSectorState.lowercase().contains("holding")

            EditRow("LPA", indicatorStates["lpa"] ?: "", true, sourceStates["lpa"]) { indicatorStates["lpa"] = it; sourceStates["lpa"] = FieldSource.USER }
            EditRow("VPA", indicatorStates["vpa"] ?: "", true, sourceStates["vpa"]) { indicatorStates["vpa"] = it; sourceStates["vpa"] = FieldSource.USER }
            EditRow("P/L", indicatorStates["pl"] ?: "", true, sourceStates["pl"]) { indicatorStates["pl"] = it; sourceStates["pl"] = FieldSource.USER }
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, sourceStates["pvp"]) { indicatorStates["pvp"] = it; sourceStates["pvp"] = FieldSource.USER }
            EditRow("ROE (%)", indicatorStates["roe"] ?: "", true, sourceStates["roe"]) { indicatorStates["roe"] = it; sourceStates["roe"] = FieldSource.USER }
            EditRow("Margem (%)", indicatorStates["ml"] ?: "", true, sourceStates["ml"]) { indicatorStates["ml"] = it; sourceStates["ml"] = FieldSource.USER }
            
            if (!isBank && !isInsurance && !isHolding) {
                EditRow("Dív. Líq./Patr.", indicatorStates["de"] ?: "", true, sourceStates["de"]) { indicatorStates["de"] = it; sourceStates["de"] = FieldSource.USER }
                EditRow("Dív. Líq./EBITDA", indicatorStates["deEbitda"] ?: "", true, sourceStates["deEbitda"]) { indicatorStates["deEbitda"] = it; sourceStates["deEbitda"] = FieldSource.USER }
                EditRow("CAGR Rec. (%)", indicatorStates["cRec"] ?: "", true, sourceStates["cRec"]) { indicatorStates["cRec"] = it; sourceStates["cRec"] = FieldSource.USER }
            }
            if (!isBank) {
                EditRow("CAGR Lucro (%)", indicatorStates["cLuc"] ?: "", true, sourceStates["cLuc"]) { indicatorStates["cLuc"] = it; sourceStates["cLuc"] = FieldSource.USER }
            }
            EditRow("Payout (%)", indicatorStates["payout"] ?: "", true, sourceStates["payout"]) { indicatorStates["payout"] = it; sourceStates["payout"] = FieldSource.USER }
            if (isBank) {
                EditRow("Basileia (%)", indicatorStates["basel"] ?: "", true, sourceStates["basel"]) { indicatorStates["basel"] = it; sourceStates["basel"] = FieldSource.USER }
            }
            EditRow("DY 12m (%)", indicatorStates["dy"] ?: "", true, sourceStates["dy"]) { indicatorStates["dy"] = it; sourceStates["dy"] = FieldSource.USER }
            EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true, sourceStates["dy5"]) { indicatorStates["dy5"] = it; sourceStates["dy5"] = FieldSource.USER }
            EditRow("Vol. Diário", indicatorStates["vol"] ?: "", true, sourceStates["vol"]) { indicatorStates["vol"] = it; sourceStates["vol"] = FieldSource.USER }
            EditRow("Graham", indicatorStates["graham"] ?: "", true, sourceStates["graham"]) { indicatorStates["graham"] = it; sourceStates["graham"] = FieldSource.USER }
            EditRow("Bazin", indicatorStates["bazin"] ?: "", true, sourceStates["bazin"]) { indicatorStates["bazin"] = it; sourceStates["bazin"] = FieldSource.USER }
            EditRow("Patrimônio", indicatorStates["netEquity"] ?: "", true, sourceStates["netEquity"]) { indicatorStates["netEquity"] = it; sourceStates["netEquity"] = FieldSource.USER }
        } else if (data is AssetData.Fii) {
            val isPapel = sectorState.lowercase().contains("papel")
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, sourceStates["pvp"]) { indicatorStates["pvp"] = it; sourceStates["pvp"] = FieldSource.USER }
            EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true, sourceStates["y12"]) { indicatorStates["y12"] = it; sourceStates["y12"] = FieldSource.USER }
            EditRow("DY Médio 5a (%)", indicatorStates["y5"] ?: "", true, sourceStates["y5"]) { indicatorStates["y5"] = it; sourceStates["y5"] = FieldSource.USER }
            EditRow("Vol. Diário", indicatorStates["vol"] ?: "", true, sourceStates["vol"]) { indicatorStates["vol"] = it; sourceStates["vol"] = FieldSource.USER }
            
            if (!isPapel) {
                EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true, sourceStates["vac"]) { indicatorStates["vac"] = it; sourceStates["vac"] = FieldSource.USER }
                EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true, sourceStates["walt"]) { indicatorStates["walt"] = it; sourceStates["walt"] = FieldSource.USER }
                EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true, sourceStates["prop"]) { indicatorStates["prop"] = it; sourceStates["prop"] = FieldSource.USER }
            }
            
            EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true, sourceStates["mFee"]) { indicatorStates["mFee"] = it; sourceStates["mFee"] = FieldSource.USER }
            EditRow("Patrimônio", indicatorStates["aum"] ?: "", true, sourceStates["aum"]) { indicatorStates["aum"] = it; sourceStates["aum"] = FieldSource.USER }
            
            var showManagementMenu by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Gestão", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box(modifier = Modifier.weight(1.8f)) {
                    OutlinedButton(onClick = { showManagementMenu = true }, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(if (indicatorStates["mType"].isNullOrBlank()) "Selecionar" else indicatorStates["mType"]!!, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showManagementMenu, onDismissRequest = { showManagementMenu = false }) {
                        listOf("Ativa", "Passiva").forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { indicatorStates["mType"] = type; showManagementMenu = false })
                        }
                    }
                }
            }

            if (!isPapel) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Nota Inquilino", fontSize = 12.sp); Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp).padding(start = 4.dp).clickable { helpDialogType = "tenant" }, tint = Color.Gray) }
                    Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) { val cur = (indicatorStates["tScore"] ?: "0").toInt(); (0..5).forEach { s -> TextButton(onClick = { indicatorStates["tScore"] = s.toString(); sourceStates["tScore"] = FieldSource.USER }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) { Text(text = s.toString(), fontWeight = if (cur == s) FontWeight.Bold else FontWeight.Normal, color = if (cur == s) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface) } } }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Nota Alavancagem", fontSize = 12.sp); Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp).padding(start = 4.dp).clickable { helpDialogType = "leverage" }, tint = Color.Gray) }
                Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) { val cur = (indicatorStates["lScore"] ?: "0").toInt(); (0..5).forEach { s -> TextButton(onClick = { indicatorStates["lScore"] = s.toString(); sourceStates["lScore"] = FieldSource.USER }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) { Text(text = s.toString(), fontWeight = if (cur == s) FontWeight.Bold else FontWeight.Normal, color = if (cur == s) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface) } } }
            }
        }
        else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true, sourceStates["aFee"]) { indicatorStates["aFee"] = it; sourceStates["aFee"] = FieldSource.USER }; EditRow("Tracking Error (%)", indicatorStates["te"] ?: "", true, sourceStates["te"]) { indicatorStates["te"] = it; sourceStates["te"] = FieldSource.USER }
            EditRow("Vol. Diário", indicatorStates["vol"] ?: "", true, sourceStates["vol"]) { indicatorStates["vol"] = it; sourceStates["vol"] = FieldSource.USER }; EditRow("Patrimônio", indicatorStates["aum"] ?: "", true, sourceStates["aum"]) { indicatorStates["aum"] = it; sourceStates["aum"] = FieldSource.USER }; EditRow("Holdings", indicatorStates["hold"] ?: "", true, sourceStates["hold"]) { indicatorStates["hold"] = it; sourceStates["hold"] = FieldSource.USER }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true, sourceStates["dy"]) { indicatorStates["dy"] = it; sourceStates["dy"] = FieldSource.USER }; EditRow("Paridade", indicatorStates["par"] ?: "", false, sourceStates["par"]) { indicatorStates["par"] = it; sourceStates["par"] = FieldSource.USER }
        }
        Row(modifier = Modifier.padding(top = 16.dp)) { Button(onClick = { val updated = when (data) {
            is AssetData.Stock -> data.copy(
                name = nameState, 
                currentPrice = parseBR(priceState), 
                sector = sectorState, 
                subSector = subSectorState, 
                isInPortfolio = inPortfolioState, 
                isInert = isInertState, 
                sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), 
                userScore = parseBR(indicatorStates["uScore"] ?: "0"), 
                lpa = parseBR(indicatorStates["lpa"] ?: "0"), 
                vpa = parseBR(indicatorStates["vpa"] ?: "0"), 
                roe = parseBR(indicatorStates["roe"] ?: "0")/100, 
                dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, 
                dividendYield5Years = parseBR(indicatorStates["dy5"] ?: "0")/100, 
                netMargin = parseBR(indicatorStates["ml"] ?: "0")/100, 
                debtToEbitda = parseBR(indicatorStates["deEbitda"] ?: "0"), 
                debtToEquity = parseBR(indicatorStates["de"] ?: "0"), 
                pl = parseBR(indicatorStates["pl"] ?: "0"), 
                pvp = parseBR(indicatorStates["pvp"] ?: "0"), 
                payout = parseBR(indicatorStates["payout"] ?: "0")/100, 
                baselIndex = parseBR(indicatorStates["basel"] ?: "0")/100, 
                grahamPrice = parseBR(indicatorStates["graham"] ?: "0"), 
                bazinPrice = parseBR(indicatorStates["bazin"] ?: "0"), 
                cagrProfit5Years = parseBR(indicatorStates["cLuc"] ?: "0")/100, 
                cagrRevenue5Years = parseBR(indicatorStates["cRec"] ?: "0")/100, 
                avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0"),
                netEquity = parseBR(indicatorStates["netEquity"] ?: "0")
            )
            is AssetData.Fii -> data.copy(
                name = nameState, 
                currentPrice = parseBR(priceState), 
                sector = sectorState, 
                subSector = subSectorState, 
                isInPortfolio = inPortfolioState, 
                isInert = isInertState, 
                sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), 
                userScore = parseBR(indicatorStates["uScore"] ?: "0"), 
                pvp = parseBR(indicatorStates["pvp"] ?: "0"), 
                vacancy = parseBR(indicatorStates["vac"] ?: "0")/100, 
                yield12m = parseBR(indicatorStates["y12"] ?: "0")/100, 
                avgYield5Years = parseBR(indicatorStates["y5"] ?: "0")/100, 
                propertyCount = (indicatorStates["prop"] ?: "0").ifBlank { "0" }.toInt(), 
                weightedLeaseTerm = parseBR(indicatorStates["walt"] ?: "0"), 
                tenantScore = (indicatorStates["tScore"] ?: "0").ifBlank { "0" }.toInt(), 
                leverageScore = (indicatorStates["lScore"] ?: "0").ifBlank { "0" }.toInt(), 
                avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0"), 
                aum = parseBR(indicatorStates["aum"] ?: "0")
            )
            is AssetData.Etf -> data.copy(
                name = nameState, 
                currentPrice = parseBR(priceState), 
                isInPortfolio = inPortfolioState, 
                isInert = isInertState, 
                sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), 
                userScore = parseBR(indicatorStates["uScore"] ?: "0"), 
                adminFee = parseBR(indicatorStates["aFee"] ?: "0")/100, 
                trackingError = parseBR(indicatorStates["te"] ?: "0")/100, 
                avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0"), 
                aum = parseBR(indicatorStates["aum"] ?: "0"), 
                numberOfHoldings = (indicatorStates["hold"] ?: "0").ifBlank { "0" }.toInt()
            )
            is AssetData.Bdr -> data.copy(
                name = nameState, 
                currentPrice = parseBR(priceState), 
                isInPortfolio = inPortfolioState, 
                isInert = isInertState, 
                sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), 
                userScore = parseBR(indicatorStates["uScore"] ?: "0"), 
                dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, 
                parity = indicatorStates["par"] ?: "1:1"
            )
            else -> data 
        }
        updated.fieldSources = sourceStates.filterValues { it != null }.mapValues { it.value!! }
        onSave(updated) 
    }, modifier = Modifier.weight(1f)) { Text("Salvar") }
 Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { onDelete(data) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Deletar") } }
        ProsConsSection(data.pros, data.cons, data.neutros)
    }
}

@Composable
fun EditRow(l: String, v: String, num: Boolean = false, source: FieldSource? = null, onVal: (String) -> Unit) {
    val magnitudeIndicator = remember(v, num) {
        if (!num || v.isEmpty()) null
        else {
            val parsed = parseBR(v)
            if (parsed >= 1_000) {
                formatSmart(parsed)
            } else null
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l, fontSize = 12.sp)
                if (source == FieldSource.AI) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1976D2))
                } else if (source == FieldSource.USER) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
            if (magnitudeIndicator != null) {
                Text(magnitudeIndicator, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
        BasicTextField(
            value = v, 
            onValueChange = onVal, 
            modifier = Modifier.weight(1.8f).height(32.dp), 
            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), 
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            decorationBox = { inner -> 
                Surface(
                    shape = MaterialTheme.shapes.extraSmall, 
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                ) { 
                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { 
                        if (v.isEmpty() && num) Text("0,00", color = Color.Gray.copy(alpha = 0.5f), fontSize = 13.sp)
                        inner() 
                    } 
                } 
            } 
        )
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column {
        DetailsRow("Preço Atual", formatBR(data.currentPrice))
        if (data is AssetData.Stock) {
            DetailsRow("P/L", formatBR(data.pl))
            DetailsRow("P/VP", formatBR(data.pvp))
            DetailsRow("ROE", formatBR(data.roe * 100) + "%")
            if (data.subSector.contains("Bancos")) DetailsRow("Basileia", formatBR(data.baselIndex * 100) + "%") 
            else DetailsRow("Dív. Líq./EBITDA", formatBR(data.debtToEbitda))
            DetailsRow("Vol. Diário", formatSmart(data.avgDailyVolume))
            DetailsRow("Patrimônio", formatSmart(data.netEquity))
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", formatBR(data.pvp))
            DetailsRow("DY 12m", formatBR(data.yield12m * 100) + "%")
            DetailsRow("Vol. Diário", formatSmart(data.avgDailyVolume))
            DetailsRow("Patrimônio", formatSmart(data.aum))
        } else if (data is AssetData.Etf) {
            DetailsRow("Vol. Diário", formatSmart(data.avgDailyVolume))
            DetailsRow("Patrimônio", formatSmart(data.aum))
        }
    }
}

@Composable
fun DetailsRow(l: String, v: String, c: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 12.sp)
    }
}

@Composable
fun ProsConsSection(p: List<String>, c: List<String>, n: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Prós", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 14.sp)
        if (p.isNotEmpty()) p.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) }
        else Text("• Não foram encontrados pontos positivos.", fontSize = 11.sp, color = Color.Gray)

        Spacer(Modifier.height(4.dp))
        Text("Contras", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp)
        if (c.isNotEmpty()) c.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) }
        else Text("• Não foram encontrados pontos negativos.", fontSize = 11.sp, color = Color.Gray)

        Spacer(Modifier.height(4.dp))
        Text("Neutros", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
        if (n.isNotEmpty()) n.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) }
        else Text("• Não foram encontrados pontos neutros.", fontSize = 11.sp, color = Color.Gray)
    }
}
