package com.example.b3check

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.b3check.ui.theme.B3CheckTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

// Utilitários de formatação brasileiros
fun formatBR(v: Double, emptyIfZero: Boolean = false): String {
    if (emptyIfZero && v == 0.0) return ""
    return String.format(Locale("pt", "BR"), "%,.2f", v)
}

fun parseBR(v: String): Double {
    if (v.isBlank()) return 0.0
    return try {
        v.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    } catch (e: Exception) { 0.0 }
}

fun getValByKey(stock: AssetData.Stock, key: String): Double {
    return when(key) {
        "lpa" -> stock.lpa
        "vpa" -> stock.vpa
        "pl" -> stock.pl
        "pvp" -> stock.pvp
        "roe" -> stock.roe
        "ml" -> stock.netMargin
        "de" -> stock.debtToEquity
        "deEbitda" -> stock.debtToEbitda
        "dy" -> stock.dividendYield
        "dy5" -> stock.dividendYield5Years
        "payout" -> stock.payout
        "basel" -> stock.baselIndex
        "graham" -> stock.grahamPrice
        "bazin" -> stock.bazinPrice
        else -> 0.0
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            B3CheckTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
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
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
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
                    label = { Text("Recomendadas", fontSize = 10.sp, maxLines = 1) }
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
                0 -> StockAnalysisScreen()
                1 -> AssetListScreen(onAssetClick = { currentTab = 0 })
                2 -> PortfolioBalanceScreen()
                3 -> RecommendationsScreen()
                4 -> InvestScreen()
            }
        }
    }
}

@Composable
fun AssetListScreen(viewModel: StockViewModel = viewModel(), onAssetClick: () -> Unit = {}) {
    val assets by viewModel.allAssets.collectAsState()
    val context = LocalContext.current
    var tickerToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showIntegrityReport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllAssets()
    }

    if (tickerToDelete != null) {
        AlertDialog(
            onDismissRequest = { tickerToDelete = null },
            title = { Text("Excluir Ativo") },
            text = { Text("Tem certeza que deseja excluir o ativo $tickerToDelete?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAsset(tickerToDelete!!)
                    tickerToDelete = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { tickerToDelete = null }) { Text("Cancelar") }
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
                Text("Integridade")
            }},
            text = {
                if (assetsWithIssues.isEmpty()) Text("✅ Tudo OK!")
                else LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(assetsWithIssues) { (asset, warnings) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(asset.ticker, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            warnings.forEach { Text("• $it", fontSize = 11.sp) }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showIntegrityReport = false }) { Text("Fechar") } }
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val json = viewModel.exportBackup()
            context.contentResolver.openOutputStream(it)?.use { s -> s.write(json.toByteArray()); Toast.makeText(context, "Backup OK!", Toast.LENGTH_SHORT).show() }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> viewModel.importBackup(r.readText()); Toast.makeText(context, "Restaurado!", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Meus Ativos", style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = { showIntegrityReport = true }) { Icon(Icons.Default.List, null, tint = Color(0xFFE65100)) }
                IconButton(onClick = { viewModel.recalculateAllScores() }) { Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { createDocumentLauncher.launch("B3Check-Backup.json") }) { Icon(Icons.Default.Share, null) }
                IconButton(onClick = { openDocumentLauncher.launch(arrayOf("application/json", "*/*")) }) { Icon(Icons.Default.Restore, null) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(assets) { asset ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { viewModel.lookupTicker(asset.ticker); onAssetClick() }) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.ticker, fontWeight = FontWeight.Bold)
                            Text("Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}", fontSize = 12.sp)
                        }
                        IconButton(onClick = { tickerToDelete = asset.ticker }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioBalanceScreen(viewModel: StockViewModel = viewModel()) {
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val allAssets by viewModel.allAssets.collectAsState()
    val portfolio = remember(allAssets) { allAssets.filter { it.isInPortfolio } }
    val totalVal = remember(portfolio) { portfolio.sumOf { it.sharesCount * it.currentPrice } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Equilíbrio da Carteira", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))
        allocation.forEach { (asset, ideal) ->
            val curVal = asset.sharesCount * asset.currentPrice
            val curPerc = if (totalVal > 0) (curVal / totalVal) * 100.0 else 0.0
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.ticker, fontWeight = FontWeight.Bold)
                    Text("Atual: ${formatBR(curPerc)}%  Ideal: ${formatBR(ideal)}%  Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun InvestScreen(viewModel: StockViewModel = viewModel()) {
    val assets by viewModel.allAssets.collectAsState()
    val allocation by viewModel.portfolioAllocation.collectAsState()
    val portfolio = remember(assets) { assets.filter { it.isInPortfolio } }
    var investAmount by rememberSaveable { mutableStateOf("") }
    val editStates = remember { mutableStateMapOf<String, String>() }

    val currentTotal = remember(portfolio) { portfolio.sumOf { it.sharesCount * it.currentPrice } }
    val totalAporte = parseBR(investAmount)
    val targetTotal = currentTotal + totalAporte

    val suggestions = remember(targetTotal, portfolio, allocation) {
        val sharesToBuy = mutableMapOf<String, Int>()
        val investSuggestions = mutableMapOf<String, Double>()
        
        if (totalAporte > 0) {
            val gaps = portfolio.map { a -> 
                val idealP = allocation.find { it.first.ticker == a.ticker }?.second ?: 0.0
                val targetVal = targetTotal * (idealP / 100.0)
                a.ticker to (targetVal - (a.sharesCount * a.currentPrice)).coerceAtLeast(0.0)
            }
            val totalGap = gaps.sumOf { it.second }
            var remaining = totalAporte

            portfolio.forEach { a ->
                val money = if (totalGap > 0) totalAporte * (gaps.find { it.first == a.ticker }?.second ?: 0.0) / totalGap else 0.0
                val qty = (money / a.currentPrice).toInt()
                if (qty > 0) { 
                    sharesToBuy[a.ticker] = qty
                    investSuggestions[a.ticker] = qty * a.currentPrice
                    remaining -= qty * a.currentPrice 
                }
            }

            while (remaining > 0) {
                val best = portfolio.filter { it.currentPrice <= remaining }.maxByOrNull { allocation.find { p -> p.first.ticker == it.ticker }?.second ?: 0.0 } ?: break
                sharesToBuy[best.ticker] = (sharesToBuy[best.ticker] ?: 0) + 1
                investSuggestions[best.ticker] = (sharesToBuy[best.ticker] ?: 0) * best.currentPrice
                remaining -= best.currentPrice
            }
        }
        sharesToBuy to investSuggestions
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Investir", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Ticker", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Cotas", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
            Text("Preço", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
            Text("Montante", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
            Text("Sugestão", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
        }
        
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(portfolio) { index, asset ->
                val qtySuggest = suggestions.first[asset.ticker] ?: 0
                val valSuggest = suggestions.second[asset.ticker] ?: 0.0
                val rowBg = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent
                val montante = asset.sharesCount * asset.currentPrice
                
                Row(modifier = Modifier.fillMaxWidth().background(rowBg).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.ticker, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    
                    // Coluna Cotas (Editável)
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
                        modifier = Modifier.weight(1.2f),
                        textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Coluna Preço (Editável)
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
                        modifier = Modifier.weight(1.2f),
                        textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    // Coluna Montante
                    Text(
                        text = "R$ ${formatBR(montante)}",
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.End,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    // Coluna Sugestão
                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                        if (qtySuggest > 0) { 
                            Text("${qtySuggest} un", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("R$ ${formatBR(valSuggest)}", fontSize = 9.sp, color = Color.Gray) 
                        } else Text("-", color = Color.Gray)
                    }
                }
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val totalCurrentVal = portfolio.sumOf { it.sharesCount * it.currentPrice }
                val totalSugVal = suggestions.second.values.sum()
                val totalSugQty = suggestions.first.values.sum()
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAIS", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("", modifier = Modifier.weight(1.2f))
                    Text("", modifier = Modifier.weight(1.2f))
                    Text("R$ ${formatBR(totalCurrentVal)}", modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                        Text("${totalSugQty} un", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF2E7D32))
                        Text("R$ ${formatBR(totalSugVal)}", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = investAmount, 
            onValueChange = { investAmount = it }, 
            label = { Text("Novo aporte (R$)") }, 
            modifier = Modifier.fillMaxWidth(), 
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun RecommendationsScreen(viewModel: StockViewModel = viewModel()) {
    val recs by viewModel.recommendations.collectAsState()
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var investAmount by rememberSaveable { mutableStateOf("") }
    
    val scored = remember(recs) { recs.map { it to viewModel.calculateScoreForAsset(it) } }
    val selScored = scored.filter { selected[it.first.ticker] == true }
    val totalScore = selScored.sumOf { it.second }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Recomendadas", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
        Text("Simule o aporte proporcional à nota dos ativos selecionados", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(scored) { index, (asset, score) ->
                val qtySuggest = remember(totalScore, investAmount, selected[asset.ticker]) {
                    val amt = parseBR(investAmount)
                    if (amt > 0 && totalScore > 0 && selected[asset.ticker] == true) {
                        (amt * (score / totalScore) / asset.currentPrice).toInt()
                    } else 0
                }

                val pvp = when(asset) {
                    is AssetData.Stock -> asset.pvp
                    is AssetData.Fii -> asset.pvp
                    else -> 0.0
                }

                val rowBg = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent
                val isSelected = selected[asset.ticker] ?: false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else rowBg)
                        .clickable { selected[asset.ticker] = !isSelected }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSelected, onCheckedChange = { selected[asset.ticker] = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nota: ${formatBR(score)}", fontSize = 11.sp)
                            if (pvp > 0) {
                                Text(" • P/VP: ${formatBR(pvp)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (qtySuggest > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${qtySuggest} un", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("R$ ${formatBR(qtySuggest * asset.currentPrice)}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = investAmount, 
            onValueChange = { investAmount = it }, 
            label = { Text("Simular aporte (R$)") }, 
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
fun StockAnalysisScreen(viewModel: StockViewModel = viewModel()) {
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
        OutlinedTextField(value = tickerInput, onValueChange = { tickerInput = it.uppercase() }, label = { Text("Ticker") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { if (tickerInput.isNotEmpty()) IconButton(onClick = { tickerInput = ""; viewModel.resetAnalysis() }) { Icon(Icons.Default.Delete, null) } })
        Spacer(modifier = Modifier.height(16.dp))
        when (val state = uiState) {
            is StockUiState.Success -> { ScoreHeader(state.data, state.score); ManualEditor(state.data, state.score, { viewModel.saveManualAsset(it) }, {}, { viewModel.deleteAsset(it.ticker); tickerInput = "" }) }
            is StockUiState.Error -> if (tickerInput.length >= 4) Row { Button(onClick = { viewModel.addManualAsset(tickerInput, "Ação") }) { Text("Ação") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "FII") }) { Text("FII") }; Button(onClick = { viewModel.addManualAsset(tickerInput, "ETF") }) { Text("ETF") } }
            else -> {}
        }
    }
}

@Composable
fun ScoreHeader(data: AssetData, finalScore: Double, viewModel: StockViewModel = viewModel()) {
    val motorScore = remember(data) {
        val raw = when(data) {
            is AssetData.Stock -> data.copy(userScore = 0.0, userScorePriority = false)
            is AssetData.Fii -> data.copy(userScore = 0.0, userScorePriority = false)
            is AssetData.Etf -> data.copy(userScore = 0.0, userScorePriority = false)
            is AssetData.Bdr -> data.copy(userScore = 0.0, userScorePriority = false)
        }
        viewModel.calculateScoreForAsset(raw)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(data.ticker, fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(data.name, fontSize = 14.sp, color = Color.Gray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreIndicator("Motor", motorScore, Color.Gray)
            ScoreIndicator("Manual", data.userScore, MaterialTheme.colorScheme.secondary)
            ScoreIndicator("Média", (motorScore + data.userScore)/2.0, Color(0xFF1976D2))
            ScoreIndicator("FINAL", finalScore, Color(0xFF2E7D32), true)
        }
        AssetDetails(data)
        ProsConsSection(data.pros, data.cons)
    }
}

@Composable
fun ScoreIndicator(l: String, v: Double, c: Color, main: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.copy(alpha = 0.8f))
        Text(formatBR(v), fontSize = if (main) 22.sp else 16.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
fun ManualEditor(data: AssetData, score: Double, onSave: (AssetData) -> Unit, onAnalyze: (AssetData) -> Unit, onDelete: (AssetData) -> Unit) {
    var nameState by remember(data.ticker) { mutableStateOf(data.name) }
    var priceState by remember(data.ticker) { mutableStateOf(formatBR(data.currentPrice, true)) }
    var sectorState by remember(data.ticker) { mutableStateOf(data.sector) }
    var subSectorState by remember(data.ticker) { mutableStateOf(data.subSector) }
    var inPortfolioState by remember(data.ticker) { mutableStateOf(data.isInPortfolio) }
    val indicatorStates = remember(data.ticker) { mutableStateMapOf<String, String>() }

    LaunchedEffect(data.ticker) {
        indicatorStates.clear()
        indicatorStates["uScore"] = if(data.userScore > 0) formatBR(data.userScore) else ""
        indicatorStates["uPrior"] = if(data.userScorePriority) "Sim" else "Não"
        if (data is AssetData.Stock) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["lpa"] = formatBR(data.lpa); indicatorStates["vpa"] = formatBR(data.vpa)
            indicatorStates["roe"] = formatBR(data.roe * 100); indicatorStates["dy"] = formatBR(data.dividendYield * 100)
            indicatorStates["dy5"] = formatBR(data.dividendYield5Years * 100); indicatorStates["de"] = formatBR(data.debtToEquity)
            indicatorStates["deEbitda"] = formatBR(data.debtToEbitda); indicatorStates["ml"] = formatBR(data.netMargin * 100)
            indicatorStates["pl"] = formatBR(data.pl); indicatorStates["pvp"] = formatBR(data.pvp)
            indicatorStates["payout"] = formatBR(data.payout * 100); indicatorStates["basel"] = formatBR(data.baselIndex * 100)
            indicatorStates["graham"] = formatBR(data.grahamPrice); indicatorStates["bazin"] = formatBR(data.bazinPrice)
            indicatorStates["cLuc"] = formatBR(data.cagrProfit5Years * 100); indicatorStates["cRec"] = formatBR(data.cagrRevenue5Years * 100)
            indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0)
        } else if (data is AssetData.Fii) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["vac"] = formatBR(data.vacancy * 100)
            indicatorStates["y12"] = formatBR(data.yield12m * 100); indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0)
            indicatorStates["prop"] = data.propertyCount.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0)
            indicatorStates["mFee"] = formatBR(data.managementFee * 100); indicatorStates["walt"] = formatBR(data.weightedLeaseTerm)
            indicatorStates["tScore"] = data.tenantScore.toString(); indicatorStates["lScore"] = data.leverageScore.toString()
            indicatorStates["mLev"] = formatBR(data.leverageValue * 100)
        } else if (data is AssetData.Etf) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["aFee"] = formatBR(data.adminFee * 100); indicatorStates["te"] = formatBR(data.trackingError * 100)
            indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0); indicatorStates["hold"] = data.numberOfHoldings.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0)
        } else if (data is AssetData.Bdr) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true); indicatorStates["dy"] = formatBR(data.dividendYield * 100); indicatorStates["par"] = data.parity
        }
    }

    val stockClassification = mapOf(
        "Financeiro" to listOf("Bancos", "Seguradoras", "Serviços Financeiros", "Exploração de Imóveis"),
        "Utilidade Pública" to listOf("Energia Elétrica", "Água e Saneamento", "Gás"),
        "Materiais Básicos" to listOf("Mineração", "Siderurgia e Metalurgia", "Papel e Celulose", "Químicos"),
        "Petróleo e Gás" to listOf("Extração e Refino", "Equipamentos e Serviços", "Biocombustíveis"),
        "Telecomunicações" to listOf("Telefonia Fixa e Móvel"),
        "Consumo Cíclico" to listOf("Comércio", "Construção Civil", "Roupas", "Turismo", "Veículos"),
        "Consumo Não Cíclico e Saúde" to listOf("Alimentos", "Bebidas", "Agropecuária", "Hospitais", "Laboratórios", "Farmácias", "Uso Pessoal e Limpeza"),
        "Bens Industriais" to listOf("Transporte e Logística", "Máquinas e Equipamentos", "Defesa e Aeroespacial")
    )
    val fiiClassification = mapOf(
        "Tijolo" to listOf("Lajes Corporativas", "Logística / Industrial", "Shopping Centers", "Hotéis", "Hospitais", "Agências bancárias", "Educacional", "Residencial", "Fiagros"),
        "Papel" to listOf("Recebíveis Imobiliários", "Fundos de Fundos (FOFs)"),
        "Híbridos" to listOf("Geral")
    )
    var showSectorMenu by remember { mutableStateOf(false) }
    var showSubSectorMenu by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Carteira", modifier = Modifier.weight(1f)); Switch(checked = inPortfolioState, onCheckedChange = { inPortfolioState = it }) }
        
        if (data is AssetData.Stock || data is AssetData.Fii) {
            val currentMap = if (data is AssetData.Stock) stockClassification else fiiClassification
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Setor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box(modifier = Modifier.weight(1.8f)) {
                    OutlinedButton(
                        onClick = { showSectorMenu = true },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(if (sectorState.isBlank()) "Selecionar" else sectorState, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showSectorMenu, onDismissRequest = { showSectorMenu = false }) {
                        currentMap.keys.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { sectorState = s; subSectorState = ""; showSectorMenu = false }) }
                    }
                }
            }

            if (sectorState.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subsetor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Box(modifier = Modifier.weight(1.8f)) {
                        OutlinedButton(
                            onClick = { showSubSectorMenu = true },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(if (subSectorState.isBlank()) "Selecionar" else subSectorState, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = showSubSectorMenu, onDismissRequest = { showSubSectorMenu = false }) {
                            currentMap[sectorState]?.forEach { ss -> DropdownMenuItem(text = { Text(ss) }, onClick = { subSectorState = ss; showSubSectorMenu = false }) }
                        }
                    }
                }
            }
        }

        EditRow("Nome", nameState) { nameState = it }
        EditRow("Preço", priceState, true) { priceState = it }
        EditRow("Cotas", indicatorStates["cotas"] ?: "", true) { indicatorStates["cotas"] = it }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Minha Nota", modifier = Modifier.weight(1f), fontSize = 12.sp)
            BasicTextField(
                value = indicatorStates["uScore"] ?: "", 
                onValueChange = { indicatorStates["uScore"] = it }, 
                modifier = Modifier.weight(0.8f).height(32.dp), 
                textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), 
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                decorationBox = { inner -> Surface(shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)) { Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { inner() } } }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = indicatorStates["uPrior"] == "Sim", onCheckedChange = { indicatorStates["uPrior"] = if(it) "Sim" else "Não" }); Text("Prioridade", fontSize = 11.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (data is AssetData.Stock) {
            EditRow("LPA", indicatorStates["lpa"] ?: "", true) { indicatorStates["lpa"] = it }; EditRow("VPA", indicatorStates["vpa"] ?: "", true) { indicatorStates["vpa"] = it }
            EditRow("P/L", indicatorStates["pl"] ?: "", true) { indicatorStates["pl"] = it }; EditRow("P/VP", indicatorStates["pvp"] ?: "", true) { indicatorStates["pvp"] = it }
            EditRow("ROE (%)", indicatorStates["roe"] ?: "", true) { indicatorStates["roe"] = it }; EditRow("Margem Líq (%)", indicatorStates["ml"] ?: "", true) { indicatorStates["ml"] = it }
            EditRow("Dív/Patrim", indicatorStates["de"] ?: "", true) { indicatorStates["de"] = it }; EditRow("Dív/EBITDA", indicatorStates["deEbitda"] ?: "", true) { indicatorStates["deEbitda"] = it }
            
            if (subSectorState.trim().lowercase().contains("bancos")) {
                EditRow("Basileia (%)", indicatorStates["basel"] ?: "", true) { indicatorStates["basel"] = it }
            }

            EditRow("CAGR Lucro (%)", indicatorStates["cLuc"] ?: "", true) { indicatorStates["cLuc"] = it }
            EditRow("CAGR Rec. (%)", indicatorStates["cRec"] ?: "", true) { indicatorStates["cRec"] = it }
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }
            EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true) { indicatorStates["dy5"] = it }
            EditRow("Payout (%)", indicatorStates["payout"] ?: "", true) { indicatorStates["payout"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }; EditRow("Preço Graham", indicatorStates["graham"] ?: "", true) { indicatorStates["graham"] = it }
            EditRow("Preço Bazin", indicatorStates["bazin"] ?: "", true) { indicatorStates["bazin"] = it }
        }
else if (data is AssetData.Fii) {
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true) { indicatorStates["pvp"] = it }; EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true) { indicatorStates["y12"] = it }
            EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true) { indicatorStates["vac"] = it }; EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true) { indicatorStates["walt"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }; EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true) { indicatorStates["aum"] = it }
            EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true) { indicatorStates["prop"] = it }; EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true) { indicatorStates["mFee"] = it }
            EditRow("Alavancagem (%)", indicatorStates["mLev"] ?: "", true) { indicatorStates["mLev"] = it }
        } else if (data is AssetData.Etf) {
            EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true) { indicatorStates["aum"] = it }; EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true) { indicatorStates["aFee"] = it }
            EditRow("Tracking Error (%)", indicatorStates["te"] ?: "", true) { indicatorStates["te"] = it }; EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }
            EditRow("Holdings", indicatorStates["hold"] ?: "", true) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }; EditRow("Paridade", indicatorStates["par"] ?: "") { indicatorStates["par"] = it }
        }

        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = {
                val updated = when (data) {
                    is AssetData.Stock -> data.copy(name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), userScorePriority = indicatorStates["uPrior"] == "Sim", lpa = parseBR(indicatorStates["lpa"] ?: "0"), vpa = parseBR(indicatorStates["vpa"] ?: "0"), roe = parseBR(indicatorStates["roe"] ?: "0")/100, dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, dividendYield5Years = parseBR(indicatorStates["dy5"] ?: "0")/100, payout = parseBR(indicatorStates["payout"] ?: "0")/100, cagrProfit5Years = parseBR(indicatorStates["cLuc"] ?: "0")/100, cagrRevenue5Years = parseBR(indicatorStates["cRec"] ?: "0")/100, netMargin = parseBR(indicatorStates["ml"] ?: "0")/100, debtToEquity = parseBR(indicatorStates["de"] ?: "0"), debtToEbitda = parseBR(indicatorStates["deEbitda"] ?: "0"), pl = parseBR(indicatorStates["pl"] ?: "0"), pvp = parseBR(indicatorStates["pvp"] ?: "0"), baselIndex = parseBR(indicatorStates["basel"] ?: "0")/100, grahamPrice = parseBR(indicatorStates["graham"] ?: "0"), bazinPrice = parseBR(indicatorStates["bazin"] ?: "0"), avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000)
                    is AssetData.Fii -> data.copy(name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), userScorePriority = indicatorStates["uPrior"] == "Sim", pvp = parseBR(indicatorStates["pvp"] ?: "0"), vacancy = parseBR(indicatorStates["vac"] ?: "0")/100, yield12m = parseBR(indicatorStates["y12"] ?: "0")/100, propertyCount = (indicatorStates["prop"] ?: "0").toInt(), avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000, aum = parseBR(indicatorStates["aum"] ?: "0")*1_000_000, managementFee = parseBR(indicatorStates["mFee"] ?: "0")/100, weightedLeaseTerm = parseBR(indicatorStates["walt"] ?: "0"), leverageValue = parseBR(indicatorStates["mLev"] ?: "0")/100)
                    is AssetData.Etf -> data.copy(name = nameState, currentPrice = parseBR(priceState), isInPortfolio = inPortfolioState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), userScorePriority = indicatorStates["uPrior"] == "Sim", adminFee = parseBR(indicatorStates["aFee"] ?: "0")/100, trackingError = parseBR(indicatorStates["te"] ?: "0")/100, avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0")*1_000_000, aum = parseBR(indicatorStates["aum"] ?: "0")*1_000_000, numberOfHoldings = (indicatorStates["hold"] ?: "0").toInt())
                    is AssetData.Bdr -> data.copy(name = nameState, currentPrice = parseBR(priceState), isInPortfolio = inPortfolioState, sharesCount = parseBR(indicatorStates["cotas"] ?: "0"), userScore = parseBR(indicatorStates["uScore"] ?: "0"), userScorePriority = indicatorStates["uPrior"] == "Sim", dividendYield = parseBR(indicatorStates["dy"] ?: "0")/100, parity = indicatorStates["par"] ?: "1:1")
                }
                onSave(updated)
            }, modifier = Modifier.weight(1f)) { Text("Salvar") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onDelete(data) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Deletar") }
        }
    }
}

@Composable
fun EditRow(l: String, v: String, num: Boolean = false, onVal: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(l, modifier = Modifier.weight(1f), fontSize = 12.sp)
        BasicTextField(
            value = v,
            onValueChange = onVal,
            modifier = Modifier.weight(1.8f).height(32.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = if(num) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            singleLine = true,
            decorationBox = { inner ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
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
        DetailsRow("Preço", "R$ ${formatBR(data.currentPrice)}")
        if (data is AssetData.Stock) {
            DetailsRow("P/VP", formatBR(data.pvp)); DetailsRow("ROE", formatBR(data.roe*100)+"%")
            DetailsRow("Dív/EBITDA", formatBR(data.debtToEbitda))
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", formatBR(data.pvp)); DetailsRow("Vacância", formatBR(data.vacancy*100)+"%")
        }
    }
}

@Composable
fun DetailsRow(l: String, v: String, c: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, color = Color.Gray, fontSize = 12.sp); Text(v, fontWeight = FontWeight.Bold, color = c, fontSize = 12.sp)
    }
}

@Composable
fun ProsConsSection(p: List<String>, c: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (p.isNotEmpty()) { Text("Prós", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 14.sp); p.forEach { Text("• $it", fontSize = 11.sp) } }
        if (c.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text("Contras", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp); c.forEach { Text("• $it", fontSize = 11.sp) } }
    }
}
