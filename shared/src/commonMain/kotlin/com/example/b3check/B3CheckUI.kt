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
import kotlin.math.sqrt

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    label = { Text("Recomendados", fontSize = 10.sp, maxLines = 1) }
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Investir", fontSize = 10.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentTab) {
                0 -> StockAnalysisScreen(viewModel)
                1 -> AssetListScreen(viewModel, onAssetClick = { currentTab = 0 }, onExport, onImport)
                2 -> PortfolioBalanceScreen(viewModel)
                3 -> RecommendationsScreen(viewModel)
                4 -> InvestScreen(viewModel)
            }
        }
    }
}

@Composable
fun AssetListScreen(
    viewModel: StockViewModel, 
    onAssetClick: () -> Unit = {},
    onExport: (String, String) -> Unit = { _, _ -> },
    onImport: (onResult: (String) -> Unit) -> Unit = {}
) {
    val assets by viewModel.allAssets.collectAsState()
    var tickerToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showIntegrityReport by rememberSaveable { mutableStateOf(false) }
    var showRecalcSuccess by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllAssets()
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Meus Ativos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row {
                IconButton(onClick = { onImport { viewModel.importBackup(it) } }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = { showIntegrityReport = true }) { Icon(Icons.Default.List, null, tint = Color(0xFFE65100)) }
                IconButton(onClick = { viewModel.recalculateAllScores(); showRecalcSuccess = true }) { Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { onExport(viewModel.exportBackup(), "${viewModel.getCurrentDate()}-B3Check.json") }) { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(assets) { asset ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { viewModel.lookupTicker(asset.ticker); onAssetClick() }) {
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
fun PortfolioBalanceScreen(viewModel: StockViewModel) {
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val allAssets by viewModel.allAssets.collectAsState()
    val portfolio = remember(allAssets) { allAssets.filter { it.isInPortfolio } }
    val totalVal = remember(portfolio) { portfolio.sumOf { it.sharesCount * it.currentPrice } }

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
        allocation.forEach { (asset, ideal) ->
            val curVal = asset.sharesCount * asset.currentPrice
            val curPerc = if (totalVal > 0) (curVal / totalVal) * 100.0 else 0.0
            val curColor = if (curPerc < ideal) Color.Red else MaterialTheme.colorScheme.onSurface

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
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
                                Text(text = "${formatBR(qtyToBuy * asset.currentPrice)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (equilibriumAmount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Para equilíbrio total é necessário:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text("${formatBR(equilibriumAmount)}", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun InvestScreen(viewModel: StockViewModel) {
    val assets by viewModel.allAssets.collectAsState()
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val portfolio = remember(assets) { assets.filter { it.isInPortfolio }.sortedBy { it.ticker } }
    var investAmountStr by rememberSaveable { mutableStateOf("") }
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
                // Algoritmo Ganancioso Unidade-por-Unidade para redistribuição precisa em todos os tipos
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Simulador de Aportes", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = portfolio.isNotEmpty() && selectedPortfolio.size == portfolio.size,
                onCheckedChange = { isChecked -> portfolio.forEach { selectedTickers[it.ticker] = isChecked } },
                modifier = Modifier.size(32.dp).padding(end = 4.dp)
            )
            Text("Ticker", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Cotas", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
            Text("Preço", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
            Text("Montante", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
            Text("Sugestão", modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(portfolio) { index, asset ->
                val qtySuggest = suggestions.first[asset.ticker] ?: 0
                val montanteSug = suggestions.second[asset.ticker] ?: 0.0
                val rowBg = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent
                val montanteAtual = asset.sharesCount * asset.currentPrice
                
                Row(modifier = Modifier.fillMaxWidth().background(rowBg).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedTickers[asset.ticker] ?: false,
                        onCheckedChange = { selectedTickers[asset.ticker] = it },
                        modifier = Modifier.size(32.dp).padding(end = 4.dp)
                    )
                    Text(asset.ticker, modifier = Modifier.weight(1.1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    
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
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

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
                        modifier = Modifier.weight(1.1f),
                        textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Text(
                        text = formatBR(montanteAtual),
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.End,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )

                    Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                        if (qtySuggest > 0) { 
                            Text("${qtySuggest} un", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (qtySuggest >= 100 && asset is AssetData.Stock) {
                                val lots = qtySuggest / 100
                                val rem = qtySuggest % 100
                                Text("${lots} lotes" + (if(rem>0) " + $rem" else ""), fontSize = 10.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            }
                            Text(formatBR(montanteSug), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) 
                        } else Text("-", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                val totalCurrentVal = portfolio.sumOf { it.sharesCount * it.currentPrice }
                val totalSugVal = suggestions.second.values.sum()
                val totalSugQty = suggestions.first.values.sum()
                
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(32.dp).padding(end = 4.dp))
                    Text("TOTAIS", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("", modifier = Modifier.weight(1f))
                    Text("", modifier = Modifier.weight(1.1f))
                    Text(formatBR(totalCurrentVal), modifier = Modifier.weight(1.3f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                        Text("${totalSugQty} un", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF2E7D32))
                        Text(formatBR(totalSugVal), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
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
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Sugerido: R$ ${formatBR(realInvested)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Text("Sobras: R$ ${formatBR(investAmount - realInvested)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun RecommendationsScreen(viewModel: StockViewModel) {
    val recs by viewModel.recommendations.collectAsState()
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var investAmount by rememberSaveable { mutableStateOf("") }
    
    val scored = remember(recs) { recs.map { it to viewModel.calculateScoreForAsset(it) } }
    val sortedScored = remember(scored) {
        scored.sortedWith(compareByDescending<Pair<AssetData, Double>> { it.second }.thenBy { it.first.ticker })
    }
    val totalScore = sortedScored.filter { selected[it.first.ticker] == true }.sumOf { it.second }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Recomendados", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
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
                val rowBg = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent

                Row(
                    modifier = Modifier.fillMaxWidth().background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else rowBg).clickable { selected[asset.ticker] = !isSelected }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSelected, onCheckedChange = { selected[asset.ticker] = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row {
                            Text("Nota: ${formatBR(score)}", fontSize = 11.sp)
                            if (pvp > 0) Text(" • P/VP: ${formatBR(pvp)}", fontSize = 11.sp, color = if(pvp<1.0) Color(0xFF2E7D32) else Color.Gray)
                        }
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
            is StockUiState.Success -> { ScoreHeader(state.data, state.score, viewModel, { viewModel.saveManualAsset(it) }); ManualEditor(state.data, state.score, { viewModel.saveManualAsset(it) }, {}, { viewModel.deleteAsset(it.ticker); tickerInput = "" }) }
            is StockUiState.Error -> if (tickerInput.length >= 4) Row { Button(onClick = { viewModel.addManualAsset(tickerInput, "Ação") }) { Text("Ação") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "FII") }) { Text("FII") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "ETF") }) { Text("ETF") } }
            else -> {}
        }
    }
}

@Composable
fun ScoreHeader(data: AssetData, finalScore: Double, viewModel: StockViewModel, onSave: (AssetData) -> Unit) {
    val motorScore = remember(data) {
        val raw = when(data) {
            is AssetData.Stock -> data.copy(userScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Fii -> data.copy(userScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Etf -> data.copy(userScore = 0.0, userScorePriority = false, userScoreAverage = false)
            is AssetData.Bdr -> data.copy(userScore = 0.0, userScorePriority = false, userScoreAverage = false)
        }
        viewModel.calculateScoreForAsset(raw)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(data.ticker, fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(data.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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
                "Média", 
                (motorScore + data.userScore)/2.0, 
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
        ProsConsSection(data.pros, data.cons)
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
    var helpDialogType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(data.ticker) {
        indicatorStates.clear()
        indicatorStates["uScore"] = if(data.userScore > 0) formatBR(data.userScore) else ""
        if (data is AssetData.Stock) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["lpa"] = formatBR(data.lpa); indicatorStates["vpa"] = formatBR(data.vpa); indicatorStates["roe"] = formatBR(data.roe * 100); indicatorStates["dy"] = formatBR(data.dividendYield * 100); indicatorStates["dy5"] = formatBR(data.dividendYield5Years * 100); indicatorStates["de"] = formatBR(data.debtToEquity); indicatorStates["deEbitda"] = formatBR(data.debtToEbitda); indicatorStates["ml"] = formatBR(data.netMargin * 100); indicatorStates["pl"] = formatBR(data.pl); indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["payout"] = formatBR(data.payout * 100); indicatorStates["basel"] = formatBR(data.baselIndex * 100); indicatorStates["graham"] = formatBR(data.grahamPrice); indicatorStates["bazin"] = formatBR(data.bazinPrice); indicatorStates["cLuc"] = formatBR(data.cagrProfit5Years * 100); indicatorStates["cRec"] = formatBR(data.cagrRevenue5Years * 100); indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0)
        } else if (data is AssetData.Fii) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["vac"] = formatBR(data.vacancy * 100); indicatorStates["y12"] = formatBR(data.yield12m * 100); indicatorStates["y5"] = formatBR(data.avgYield5Years * 100); indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0); indicatorStates["prop"] = data.propertyCount.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0); indicatorStates["mFee"] = formatBR(data.managementFee * 100); indicatorStates["walt"] = formatBR(data.weightedLeaseTerm); indicatorStates["mLev"] = formatBR(data.leverageValue * 100); indicatorStates["mType"] = data.managementType; indicatorStates["tScore"] = data.tenantScore.toString(); indicatorStates["lScore"] = data.leverageScore.toString()
        } else if (data is AssetData.Etf) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["aFee"] = formatBR(data.adminFee * 100); indicatorStates["te"] = formatBR(data.trackingError * 100); indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0); indicatorStates["hold"] = data.numberOfHoldings.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0)
        } else if (data is AssetData.Bdr) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["dy"] = formatBR(data.dividendYield * 100); indicatorStates["par"] = data.parity
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
            EditRow("LPA", indicatorStates["lpa"] ?: "", true) { indicatorStates["lpa"] = it }; EditRow("VPA", indicatorStates["vpa"] ?: "", true) { indicatorStates["vpa"] = it }; EditRow("P/L", indicatorStates["pl"] ?: "", true) { indicatorStates["pl"] = it }; EditRow("P/VP", indicatorStates["pvp"] ?: "", true) { indicatorStates["pvp"] = it }; EditRow("ROE (%)", indicatorStates["roe"] ?: "", true) { indicatorStates["roe"] = it }; EditRow("Margem (%)", indicatorStates["ml"] ?: "", true) { indicatorStates["ml"] = it }
            EditRow("Dív/Patrimônio", indicatorStates["de"] ?: "", true) { indicatorStates["de"] = it }; EditRow("Dív/EBITDA", indicatorStates["deEbitda"] ?: "", true) { indicatorStates["deEbitda"] = it }
            EditRow("CAGR Lucro (%)", indicatorStates["cLuc"] ?: "", true) { indicatorStates["cLuc"] = it }; EditRow("CAGR Rec. (%)", indicatorStates["cRec"] ?: "", true) { indicatorStates["cRec"] = it }; EditRow("Payout (%)", indicatorStates["payout"] ?: "", true) { indicatorStates["payout"] = it }
            if (subSectorState.contains("Bancos")) EditRow("Basileia (%)", indicatorStates["basel"] ?: "", true) { indicatorStates["basel"] = it }
            EditRow("DY 12m (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }; EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true) { indicatorStates["dy5"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }; EditRow("Graham", indicatorStates["graham"] ?: "", true) { indicatorStates["graham"] = it }; EditRow("Bazin", indicatorStates["bazin"] ?: "", true) { indicatorStates["bazin"] = it }
        } else if (data is AssetData.Fii) {
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true) { indicatorStates["pvp"] = it }; EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true) { indicatorStates["y12"] = it }; EditRow("DY Médio 5a (%)", indicatorStates["y5"] ?: "", true) { indicatorStates["y5"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }; EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true) { indicatorStates["vac"] = it }; EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true) { indicatorStates["walt"] = it }; EditRow("Alavancagem (%)", indicatorStates["mLev"] ?: "", true) { indicatorStates["mLev"] = it }; EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true) { indicatorStates["prop"] = it }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Nota Inquilino", fontSize = 12.sp); Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp).padding(start = 4.dp).clickable { helpDialogType = "tenant" }, tint = Color.Gray) }
                Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) { val cur = (indicatorStates["tScore"] ?: "0").toInt(); (0..5).forEach { s -> TextButton(onClick = { indicatorStates["tScore"] = s.toString() }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) { Text(text = s.toString(), fontWeight = if (cur == s) FontWeight.Bold else FontWeight.Normal, color = if (cur == s) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface) } } }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Nota Alavancagem", fontSize = 12.sp); Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp).padding(start = 4.dp).clickable { helpDialogType = "leverage" }, tint = Color.Gray) }
                Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) { val cur = (indicatorStates["lScore"] ?: "0").toInt(); (0..5).forEach { s -> TextButton(onClick = { indicatorStates["lScore"] = s.toString() }, modifier = Modifier.size(32.dp), contentPadding = PaddingValues(0.dp)) { Text(text = s.toString(), fontWeight = if (cur == s) FontWeight.Bold else FontWeight.Normal, color = if (cur == s) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface) } } }
            }
        } else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true) { indicatorStates["aFee"] = it }; EditRow("Tracking Error (%)", indicatorStates["te"] ?: "", true) { indicatorStates["te"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }; EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true) { indicatorStates["aum"] = it }; EditRow("Holdings", indicatorStates["hold"] ?: "", true) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }; EditRow("Paridade", indicatorStates["par"] ?: "") { indicatorStates["par"] = it }
        }
        Row(modifier = Modifier.padding(top = 16.dp)) { Button(onClick = { val updated = when (data) {
            is AssetData.Stock -> data.copy(name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState, isInert = isInertState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), lpa = parseBR(indicatorStates["lpa"] ?: "0"), vpa = parseBR(indicatorStates["vpa"] ?: "0"), roe = parseBR(indicatorStates["roe"] ?: "0")/100, dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, dividendYield5Years = parseBR(indicatorStates["dy5"] ?: "0")/100, netMargin = parseBR(indicatorStates["ml"] ?: "0")/100, debtToEbitda = parseBR(indicatorStates["deEbitda"] ?: "0"), debtToEquity = parseBR(indicatorStates["de"] ?: "0"), pl = parseBR(indicatorStates["pl"] ?: "0"), pvp = parseBR(indicatorStates["pvp"] ?: "0"), payout = parseBR(indicatorStates["payout"] ?: "0")/100, baselIndex = parseBR(indicatorStates["basel"] ?: "0")/100, grahamPrice = parseBR(indicatorStates["graham"] ?: "0"), bazinPrice = parseBR(indicatorStates["bazin"] ?: "0"), cagrProfit5Years = parseBR(indicatorStates["cLuc"] ?: "0")/100, cagrRevenue5Years = parseBR(indicatorStates["cRec"] ?: "0")/100, avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000)
            is AssetData.Fii -> data.copy(name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState, isInert = isInertState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), pvp = parseBR(indicatorStates["pvp"] ?: "0"), vacancy = parseBR(indicatorStates["vac"] ?: "0")/100, yield12m = parseBR(indicatorStates["y12"] ?: "0")/100, avgYield5Years = parseBR(indicatorStates["y5"] ?: "0")/100, propertyCount = (indicatorStates["prop"] ?: "0").toInt(), weightedLeaseTerm = parseBR(indicatorStates["walt"] ?: "0"), leverageValue = parseBR(indicatorStates["mLev"] ?: "0")/100, tenantScore = (indicatorStates["tScore"] ?: "0").toInt(), leverageScore = (indicatorStates["lScore"] ?: "0").toInt(), avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000, aum = parseBR(indicatorStates["aum"] ?: "0")*1_000_000)
            is AssetData.Etf -> data.copy(name = nameState, currentPrice = parseBR(priceState), isInPortfolio = inPortfolioState, isInert = isInertState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), adminFee = parseBR(indicatorStates["aFee"] ?: "0")/100, trackingError = parseBR(indicatorStates["te"] ?: "0")/100, avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000, aum = parseBR(indicatorStates["aum"] ?: "0")*1_000_000, numberOfHoldings = (indicatorStates["hold"] ?: "0").toInt())
            is AssetData.Bdr -> data.copy(name = nameState, currentPrice = parseBR(priceState), isInPortfolio = inPortfolioState, isInert = isInertState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, parity = indicatorStates["par"] ?: "1:1")
            else -> data }; onSave(updated) }, modifier = Modifier.weight(1f)) { Text("Salvar") }
 Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { onDelete(data) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Deletar") } }
    }
}

@Composable
fun EditRow(l: String, v: String, num: Boolean = false, onVal: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(l, modifier = Modifier.weight(1f), fontSize = 12.sp)
        BasicTextField(value = v, onValueChange = onVal, modifier = Modifier.weight(1.8f).height(32.dp), textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), keyboardOptions = if(num) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default, decorationBox = { inner -> Surface(shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))) { Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { inner() } } })
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column {
        DetailsRow("Preço Atual", formatBR(data.currentPrice))
        if (data is AssetData.Stock) {
            DetailsRow("P/L", formatBR(data.pl)); DetailsRow("P/VP", formatBR(data.pvp)); DetailsRow("ROE", formatBR(data.roe * 100) + "%")
            if (data.subSector.contains("Bancos")) DetailsRow("Basileia", formatBR(data.baselIndex * 100) + "%") else DetailsRow("Dív/EBITDA", formatBR(data.debtToEbitda))
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", formatBR(data.pvp)); DetailsRow("DY 12m", formatBR(data.yield12m * 100) + "%"); DetailsRow("Alavancagem", formatBR(data.leverageValue * 100) + "%")
        }
    }
}

@Composable
fun DetailsRow(l: String, v: String, c: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface); Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 12.sp)
    }
}

@Composable
fun ProsConsSection(p: List<String>, c: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (p.isNotEmpty()) { Text("Prós", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 14.sp); p.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) } }
        if (c.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text("Contras", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp); c.forEach { Text("• $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) } }
    }
}
