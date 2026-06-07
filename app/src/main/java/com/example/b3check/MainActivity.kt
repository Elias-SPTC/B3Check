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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

fun getValByKeyFii(fii: AssetData.Fii, key: String): Double {
    return when(key) {
        "pvp" -> fii.pvp
        "vac" -> fii.vacancy
        "y12" -> fii.yield12m
        "y5" -> fii.avgYield5Years
        "vol" -> fii.avgDailyVolume
        "prop" -> fii.propertyCount.toDouble()
        "aum" -> fii.aum
        "mFee" -> fii.managementFee
        "walt" -> fii.weightedLeaseTerm
        "mLev" -> fii.leverageValue
        else -> 0.0
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
    var currentTab by remember { mutableIntStateOf(0) }

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
    var tickerToDelete by remember { mutableStateOf<String?>(null) }
    var showIntegrityReport by remember { mutableStateOf(false) }

    if (tickerToDelete != null) {
        // ... (existing Delete AlertDialog)
    }

    if (showIntegrityReport) {
        val assetsWithIssues = assets.map { it to viewModel.getIntegrityWarnings(it) }.filter { it.second.isNotEmpty() }
        
        AlertDialog(
            onDismissRequest = { showIntegrityReport = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Relatório de Integridade")
            }},
            text = {
                if (assetsWithIssues.isEmpty()) {
                    Text("✅ Nenhum problema detectado em sua base de dados!")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(assetsWithIssues) { (asset, warnings) ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                warnings.forEach { msg ->
                                    Text("• $msg", fontSize = 11.sp, color = Color.DarkGray)
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showIntegrityReport = false }) { Text("Fechar") } }
        )
    }

    val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val defaultBackupName = "$currentDate-B3Check.json"

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val json = viewModel.exportBackup()
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(json.toByteArray())
                Toast.makeText(context, "Backup salvo com sucesso!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                val json = reader.readText()
                viewModel.importBackup(json)
                Toast.makeText(context, "Backup restaurado com sucesso!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Meus Ativos", style = MaterialTheme.typography.titleLarge)
            
            Row {
                IconButton(onClick = { showIntegrityReport = true }) { 
                    Icon(Icons.Default.List, contentDescription = "Scan de Integridade", tint = Color(0xFFE65100)) 
                }

                IconButton(onClick = {
                    viewModel.recalculateAllScores()
                    Toast.makeText(context, "Todas as notas foram atualizadas!", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Science, contentDescription = "Recalcular Tudo", tint = MaterialTheme.colorScheme.primary) }

                IconButton(onClick = {
                    createDocumentLauncher.launch(defaultBackupName)
                }) { Icon(Icons.Default.Share, contentDescription = "Salvar em Arquivo") }
                
                IconButton(onClick = {
                    openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Icon(Icons.Default.Restore, contentDescription = "Restaurar de Arquivo") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(assets) { asset ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable {
                        viewModel.analyzeTicker(asset.ticker)
                        onAssetClick()
                    }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                if (asset.isInPortfolio) {
                                    Surface(color = Color(0xFFE8F5E9), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.padding(start = 8.dp)) {
                                        Text("CARTEIRA", fontSize = 9.sp, color = Color(0xFF1B5E20), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(asset.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, fontWeight = FontWeight.Normal)
                        }
                        IconButton(onClick = { tickerToDelete = asset.ticker }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioBalanceScreen(viewModel: StockViewModel = viewModel()) {
    val portfolioAllocation by viewModel.portfolioAllocation.collectAsState()
    val allAssets by viewModel.allAssets.collectAsState()
    
    // Otimização: Filtra e calcula o total apenas quando a lista de ativos muda
    val portfolioAssets = remember(allAssets) { allAssets.filter { it.isInPortfolio } }
    val totalCurrentValue = remember(portfolioAssets) { portfolioAssets.sumOf { it.sharesCount * it.currentPrice } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Equilíbrio da Carteira", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Text("Comparativo entre alocação atual e ideal (por Nota)", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (portfolioAllocation.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Marque ativos como 'Já possuo na carteira' no editor manual.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
            }
        } else {
            portfolioAllocation.forEach { (asset, idealPercent) ->
                val currentVal = asset.sharesCount * asset.currentPrice
                val currentPercent = if (totalCurrentValue > 0) (currentVal / totalCurrentValue) * 100.0 else 0.0

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Nota: ${formatBR(viewModel.calculateScoreForAsset(asset))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(asset.sector, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ideal: " + formatBR(idealPercent) + "%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1976D2))
                            Text("Atual: " + formatBR(currentPercent) + "%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1976D2))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvestScreen(viewModel: StockViewModel = viewModel()) {
    val assets by viewModel.allAssets.collectAsState()
    val allocation by viewModel.portfolioAllocation.collectAsState()
    
    // Otimização: Cache de portfólio para evitar filtros repetitivos no desenho da tela
    val portfolio = remember(assets) { assets.filter { it.isInPortfolio } }
    
    var investAmount by remember { mutableStateOf("") }
    val investSuggestions = remember { mutableStateMapOf<String, Double>() }
    val sharesToBuy = remember { mutableStateMapOf<String, Int>() }
    val editStates = remember { mutableStateMapOf<String, String>() }

    // Sincroniza estados de edição apenas quando o portfólio muda, sem travar a UI
    LaunchedEffect(portfolio) {
        portfolio.forEach { asset ->
            val cKey = "${asset.ticker}_c"
            val pKey = "${asset.ticker}_p"
            if (!editStates.containsKey(cKey)) editStates[cKey] = formatBR(asset.sharesCount, true)
            if (!editStates.containsKey(pKey)) editStates[pKey] = formatBR(asset.currentPrice, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Investir", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(16.dp))

        // Cabeçalho da Tabela
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ticker", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Cotas", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Preço", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Aplicado", modifier = Modifier.weight(2.0f).padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("%", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
            Text("Sugestão", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(portfolio) { index, asset ->
                val applied = asset.sharesCount * asset.currentPrice
                val recPercent = remember(allocation, asset.ticker) { 
                    allocation.find { it.first.ticker == asset.ticker }?.second ?: 0.0 
                }
                val valSuggest = investSuggestions[asset.ticker] ?: 0.0
                val qtySuggest = sharesToBuy[asset.ticker] ?: 0
                
                val cKey = "${asset.ticker}_c"
                val pKey = "${asset.ticker}_p"
                
                val cotasText = editStates[cKey] ?: formatBR(asset.sharesCount, true)
                val precoText = editStates[pKey] ?: formatBR(asset.currentPrice, true)

                val rowBg = if (index % 2 != 0) {
                    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                } else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(vertical = 3.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(asset.ticker, modifier = Modifier.weight(1.2f).padding(start = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    
                    // Cotas Editável
                    BasicTextField(
                        value = cotasText,
                        onValueChange = { newVal ->
                            editStates[cKey] = newVal
                            val num = parseBR(newVal)
                            val updated = when(asset) {
                                is AssetData.Stock -> asset.copy(sharesCount = num)
                                is AssetData.Fii -> asset.copy(sharesCount = num)
                                is AssetData.Etf -> asset.copy(sharesCount = num)
                                is AssetData.Bdr -> asset.copy(sharesCount = num)
                            }
                            viewModel.saveManualAsset(updated)
                        },
                        modifier = Modifier.weight(1.5f).padding(horizontal = 2.dp),
                        textStyle = TextStyle(fontSize = 13.sp, color = if(isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF1A237E), fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { inner -> Box(modifier = Modifier.padding(vertical = 2.dp)) { inner() } }
                    )

                    // Preço Editável
                    BasicTextField(
                        value = precoText,
                        onValueChange = { newVal ->
                            editStates[pKey] = newVal
                            val num = parseBR(newVal)
                            val updated = when(asset) {
                                is AssetData.Stock -> asset.copy(currentPrice = num)
                                is AssetData.Fii -> asset.copy(currentPrice = num)
                                is AssetData.Etf -> asset.copy(currentPrice = num)
                                is AssetData.Bdr -> asset.copy(currentPrice = num)
                            }
                            viewModel.saveManualAsset(updated)
                        },
                        modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp),
                        textStyle = TextStyle(fontSize = 13.sp, color = if(isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF1A237E), fontWeight = FontWeight.Medium, textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        decorationBox = { inner -> Box(modifier = Modifier.padding(vertical = 2.dp)) { inner() } }
                    )

                    Text(formatBR(applied), modifier = Modifier.weight(2.0f).padding(end = 8.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                    Text(formatBR(recPercent) + "%", modifier = Modifier.weight(1.0f), fontSize = 11.sp, textAlign = TextAlign.End)
                    
                    Column(modifier = Modifier.weight(1.8f).padding(end = 4.dp), horizontalAlignment = Alignment.End) {
                        if (qtySuggest > 0) {
                            Text("${qtySuggest} un", fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("R$ " + formatBR(valSuggest), fontSize = 10.sp, color = Color.Gray)
                        } else {
                            Text("-", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }
            // ... (rest of LazyColumn)


            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val totalApplied = portfolio.sumOf { it.sharesCount * it.currentPrice }
                val totalSuggest = investSuggestions.values.sum()
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("TOTAL", modifier = Modifier.weight(3.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(formatBR(totalApplied), modifier = Modifier.weight(2.0f).padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
                    Text("", modifier = Modifier.weight(1.0f))
                    Text("R$ " + formatBR(totalSuggest), modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32), textAlign = TextAlign.End)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = investAmount,
                onValueChange = { investAmount = it },
                label = { Text("Novo aporte", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val totalAporteInput = parseBR(investAmount)
                if (totalAporteInput > 0) {
                    val currentTotalValue = portfolio.sumOf { it.sharesCount * it.currentPrice }
                    val targetTotalValue = currentTotalValue + totalAporteInput
                    
                    // 1. Calcula Gaps Teóricos
                    val assetWeights = portfolio.map { asset ->
                        val idealPercent = allocation.find { it.first.ticker == asset.ticker }?.second ?: 0.0
                        asset to idealPercent
                    }
                    
                    val initialGaps = assetWeights.map { (asset, idealPercent) ->
                        val targetVal = targetTotalValue * (idealPercent / 100.0)
                        val currentVal = asset.sharesCount * asset.currentPrice
                        asset.ticker to (targetVal - currentVal).coerceAtLeast(0.0)
                    }
                    
                    val totalGap = initialGaps.sumOf { it.second }
                    val tempShares = mutableMapOf<String, Int>()
                    var remainingMoney = totalAporteInput
                    
                    // 2. Alocação Inicial de Cotas Inteiras
                    assetWeights.forEach { (asset, idealPercent) ->
                        val moneyForAsset = if (totalGap > 0) {
                            totalAporteInput * (initialGaps.find { it.first == asset.ticker }?.second ?: 0.0) / totalGap
                        } else {
                            totalAporteInput * (idealPercent / 100.0)
                        }
                        
                        val qty = (moneyForAsset / asset.currentPrice).toInt()
                        if (qty > 0 && asset.currentPrice > 0) {
                            tempShares[asset.ticker] = qty
                            remainingMoney -= qty * asset.currentPrice
                        } else {
                            tempShares[asset.ticker] = 0
                        }
                    }
                    
                    // 3. Distribuição do Saldo Remanescente (Loop de Troco)
                    // Prioriza ativos onde o "troco" consegue comprar mais 1 cota, 
                    // respeitando o ativo que estiver mais longe do alvo (maior gap/peso)
                    while (remainingMoney > 0) {
                        val canBuyMore = assetWeights
                            .filter { it.first.currentPrice > 0 && it.first.currentPrice <= remainingMoney }
                            .sortedByDescending { it.second } // Prioriza pela nota/peso ideal
                        
                        if (canBuyMore.isEmpty()) break
                        
                        val bestToBuy = canBuyMore.first().first
                        tempShares[bestToBuy.ticker] = (tempShares[bestToBuy.ticker] ?: 0) + 1
                        remainingMoney -= bestToBuy.currentPrice
                    }

                    // Atualiza estados
                    investSuggestions.clear()
                    sharesToBuy.clear()
                    tempShares.forEach { (ticker, qty) ->
                        val price = portfolio.find { it.ticker == ticker }?.currentPrice ?: 0.0
                        sharesToBuy[ticker] = qty
                        investSuggestions[ticker] = qty * price
                    }
                }
            }) {
                Text("Investir")
            }
        }
    }
}

@Composable
fun RecommendationsScreen(viewModel: StockViewModel = viewModel()) {
    val recs by viewModel.recommendations.collectAsState()
    val selectedTickers = remember { mutableStateMapOf<String, Boolean>() }
    var investAmount by remember { mutableStateOf("") }

    // Pre-calcula notas para evitar recálculos excessivos
    val scoredAssets = remember(recs) {
        recs.map { it to viewModel.calculateScoreForAsset(it) }
    }

    val selectedWithScores = scoredAssets.filter { selectedTickers[it.first.ticker] == true }
    val totalScoreSelected = selectedWithScores.sumOf { it.second }
    val totalAporteInput = parseBR(investAmount)

    // Lógica de Alocação de Cotas Reais
    val suggestions = remember(totalAporteInput, selectedTickers.size, totalScoreSelected) {
        val tempShares = mutableMapOf<String, Int>()
        val tempValues = mutableMapOf<String, Double>()
        
        if (totalAporteInput > 0 && totalScoreSelected > 0) {
            var remaining = totalAporteInput
            
            // 1. Alocação inicial proporcional à nota
            selectedWithScores.forEach { (asset, score) ->
                val moneyForAsset = totalAporteInput * (score / totalScoreSelected)
                val qty = (moneyForAsset / asset.currentPrice).toInt()
                if (qty > 0 && asset.currentPrice > 0) {
                    tempShares[asset.ticker] = qty
                    remaining -= qty * asset.currentPrice
                } else {
                    tempShares[asset.ticker] = 0
                }
            }
            
            // 2. Loop de Troco (Distribui sobra nos ativos mais baratos/prioritários que cabem)
            while (remaining > 0) {
                val canBuyMore = selectedWithScores
                    .filter { it.first.currentPrice > 0 && it.first.currentPrice <= remaining }
                    .sortedByDescending { it.second } // Prioriza nota
                
                if (canBuyMore.isEmpty()) break
                
                val best = canBuyMore.first().first
                tempShares[best.ticker] = (tempShares[best.ticker] ?: 0) + 1
                remaining -= best.currentPrice
            }
            
            // Calcula valores totais finais
            tempShares.forEach { (ticker, qty) ->
                val price = selectedWithScores.find { it.first.ticker == ticker }?.first?.currentPrice ?: 0.0
                tempValues[ticker] = qty * price
            }
        }
        tempShares to tempValues
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Recomendadas", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
        Text("Selecione ativos para simular aporte proporcional à Nota", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (recs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum ativo de pesquisa encontrado.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(scoredAssets) { (asset, score) ->
                    val isSelected = selectedTickers[asset.ticker] ?: false
                    val idealPercent = if (totalScoreSelected > 0 && isSelected) (score / totalScoreSelected) * 100.0 else 0.0
                    val qtySuggest = suggestions.first[asset.ticker] ?: 0
                    val valSuggest = suggestions.second[asset.ticker] ?: 0.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { selectedTickers[asset.ticker] = !isSelected },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { selectedTickers[asset.ticker] = it }
                            )
                            
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(asset.name, fontSize = 11.sp, maxLines = 1, color = Color.Gray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Nota: ${formatBR(score)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    val pvp = when(asset) {
                                        is AssetData.Stock -> asset.pvp
                                        is AssetData.Fii -> asset.pvp
                                        else -> 0.0
                                    }
                                    if (pvp > 0) {
                                        Text(" • P/VP: ${formatBR(pvp)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (isSelected) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Ideal: ${formatBR(idealPercent)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                    if (qtySuggest > 0) {
                                        Text("${qtySuggest} un", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                                        Text("R$ ${formatBR(valSuggest)}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Campo de Novo Aporte
            OutlinedTextField(
                value = investAmount,
                onValueChange = { investAmount = it },
                label = { Text("Simular novo aporte nos selecionados", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("R$ ", fontSize = 14.sp) }
            )
        }
    }
}


@Composable
fun StockAnalysisScreen(viewModel: StockViewModel = viewModel()) {
    var tickerInput by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    var selectedAssetType by remember { mutableStateOf("Ação") }

    // Sincroniza o tipo real do dado carregado
    LaunchedEffect(uiState) {
        if (uiState is StockUiState.Success) {
            val data = (uiState as StockUiState.Success).data
            selectedAssetType = when (data) {
                is AssetData.Stock -> "Ação"
                is AssetData.Fii -> "FII"
                is AssetData.Etf -> "ETF"
                is AssetData.Bdr -> "BDR"
            }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = tickerInput,
            onValueChange = { tickerInput = it.uppercase() },
            label = { Text("Ticker (Ex: BBAS3, HGLG11)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.analyzeTicker(tickerInput, selectedAssetType) },
            modifier = Modifier.fillMaxWidth(),
            enabled = tickerInput.isNotBlank()
        ) {
            Text("Analisar Ativo")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is StockUiState.Loading -> CircularProgressIndicator()
            is StockUiState.Success -> {
                ManualEditor(
                    data = state.data,
                    score = state.score,
                    onSave = { viewModel.saveManualAsset(it) },
                    onAnalyze = { viewModel.saveManualAsset(it) },
                    onDelete = { viewModel.deleteAsset(it.ticker) }
                )
                Spacer(modifier = Modifier.height(24.dp))
                ScoreResult(state.data, state.score)
            }
            is StockUiState.Error -> Text(text = state.message, color = MaterialTheme.colorScheme.error)
            StockUiState.Idle -> Text("Digite um ticker para começar", color = Color.Gray)
        }
    }
}

@Composable
fun ManualEditor(data: AssetData, score: Double, onSave: (AssetData) -> Unit, onAnalyze: (AssetData) -> Unit, onDelete: (AssetData) -> Unit) {
    var nameState by remember(data.ticker) { mutableStateOf(data.name) }
    var priceState by remember(data.ticker) { mutableStateOf(formatBR(data.currentPrice, true)) }
    var sectorState by remember(data.ticker) { mutableStateOf(data.sector ?: "") }
    var subSectorState by remember(data.ticker) { mutableStateOf(data.subSector ?: "") }
    var inPortfolioState by remember(data.ticker) { mutableStateOf(data.isInPortfolio) }
    val indicatorStates = remember(data.ticker) { mutableStateMapOf<String, String>() }
    
    var showTypeMenu by remember { mutableStateOf(false) }
    var showSectorMenu by remember { mutableStateOf(false) }
    var showSubSectorMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTenantInfo by remember { mutableStateOf(false) }
    var showLeverageInfo by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf("") }


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

    val assetTypes = listOf("Ação", "FII", "ETF", "BDR")
    val currentTypeLabel = when (data) {
        is AssetData.Stock -> "Ação"
        is AssetData.Fii -> "FII"
        is AssetData.Etf -> "ETF"
        is AssetData.Bdr -> "BDR"
    }

    fun changeType(newType: String) {
        val t = data.ticker
        val n = nameState
        val p = parseBR(priceState)
        val s = sectorState
        val ss = subSectorState
        val shares = parseBR(indicatorStates["cotas"] ?: "0")
        val deEbitda = parseBR(indicatorStates["deEbitda"] ?: "0")
        val levScore = (indicatorStates["lScore"] ?: "0").toInt()
        
        val updated = when (newType) {
            "FII" -> AssetData.Fii(t, n, p, s, ss, isInPortfolio = inPortfolioState, sharesCount = shares, leverageScore = levScore)
            "ETF" -> AssetData.Etf(t, n, p, "ETF", "ETF", isInPortfolio = inPortfolioState, sharesCount = shares)
            "BDR" -> AssetData.Bdr(t, n, p, "BDR", "BDR", isInPortfolio = inPortfolioState, sharesCount = shares)
            else -> AssetData.Stock(t, n, p, s, ss, isInPortfolio = inPortfolioState, sharesCount = shares, debtToEbitda = deEbitda)
        }
        onSave(updated)
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Alterar Tipo de Ativo") },
            text = { Text("Tem certeza que deseja alterar o tipo do ativo? Isso resetará os campos específicos deste tipo.") },
            confirmButton = {
                Button(onClick = {
                    changeType(pendingType)
                    showConfirmDialog = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir Ativo") },
            text = { Text("Tem certeza que deseja excluir este ativo?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(data)
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTenantInfo) {
        val fType = sectorState
        val title = when {
            fType == "Papel" || subSectorState.contains("Recebíveis") -> "Critérios de Devedores (Papel)"
            subSectorState.contains("FOFs") -> "Critérios de Carteira (FoF)"
            else -> "Critérios de Inquilinos (Tijolo)"
        }
        
        AlertDialog(
            onDismissRequest = { showTenantInfo = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (fType == "Papel" || subSectorState.contains("Recebíveis")) {
                        Text("Nota 1: Concentração Crítica (Risco Altíssimo)", fontWeight = FontWeight.Bold)
                        Text("1-3 Devedores ou maior emissor > 40% do PL.\n", fontSize = 12.sp)
                        Text("Nota 2: Baixa Diversificação (Risco Alto)", fontWeight = FontWeight.Bold)
                        Text("4 a 9 Devedores ou maior entre 25% a 40%.\n", fontSize = 12.sp)
                        Text("Nota 3: Diversificação Moderada (Risco Médio)", fontWeight = FontWeight.Bold)
                        Text("10-20 Devedores e nenhum > 15% do PL.\n", fontSize = 12.sp)
                        Text("Nota 4: Boa Diversificação (Risco Baixo)", fontWeight = FontWeight.Bold)
                        Text("21-40 Devedores e maior < 10% do PL.\n", fontSize = 12.sp)
                        Text("Nota 5: Excelente (Pulverizado) (Risco Mínimo)", fontWeight = FontWeight.Bold)
                        Text("Mais de 40 Devedores e nenhum > 5% do PL.\n", fontSize = 12.sp)
                    } else if (subSectorState.contains("FOFs")) {
                        Text("Nota 1: Carteira Restrita (Risco Altíssimo)", fontWeight = FontWeight.Bold)
                        Text("Concentrado em poucos fundos ou em uma única gestora.\n", fontSize = 12.sp)
                        Text("Nota 2: Baixa Diversificação (Risco Alto)", fontWeight = FontWeight.Bold)
                        Text("5 a 14 fundos ou maior concentração > 25%.\n", fontSize = 12.sp)
                        Text("Nota 3: Carteira Diversificada (Risco Médio)", fontWeight = FontWeight.Bold)
                        Text("Mais de 15 fundos de pelo menos 5 gestoras diferentes.\n", fontSize = 12.sp)
                        Text("Nota 4: Boa Carteira (Risco Baixo)", fontWeight = FontWeight.Bold)
                        Text("Mais de 20 fundos de 10+ gestoras e nenhum > 10%.\n", fontSize = 12.sp)
                        Text("Nota 5: Carteira Robusta (Risco Mínimo)", fontWeight = FontWeight.Bold)
                        Text("Mais de 25 fundos, alta liquidez e gestoras independentes.\n", fontSize = 12.sp)
                    } else {
                        Text("Nota 1: Monoinquilino (Risco Altíssimo)", fontWeight = FontWeight.Bold)
                        Text("1 único inquilino ou maior inquilino > 50% da receita.\n", fontSize = 12.sp)
                        Text("Nota 2: Baixa Diversificação (Risco Alto)", fontWeight = FontWeight.Bold)
                        Text("2 a 5 inquilinos ou principal entre 30% a 50%.\n", fontSize = 12.sp)
                        Text("Nota 3: Diversificação Moderada (Risco Médio)", fontWeight = FontWeight.Bold)
                        Text("6 a 15 inquilinos e nenhum > 20% da receita.\n", fontSize = 12.sp)
                        Text("Nota 4: Boa Diversificação (Risco Baixo)", fontWeight = FontWeight.Bold)
                        Text("16 a 30 inquilinos e maior inquilino < 10%.\n", fontSize = 12.sp)
                        Text("Nota 5: Excelente (Pulverizado) (Risco Mínimo)", fontWeight = FontWeight.Bold)
                        Text("Mais de 30 inquilinos e nenhum > 5% da receita.\n", fontSize = 12.sp)
                    }
                    Text("Nota 0: Desativa este parâmetro da análise.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = { Button(onClick = { showTenantInfo = false }) { Text("Entendi") } }
        )
    }

    if (showLeverageInfo) {
        AlertDialog(
            onDismissRequest = { showLeverageInfo = false },
            title = { Text("Critérios de Alavancagem") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Cálculo: (Ativo Total - PL - Caixa) / (Ativo Total - Caixa)\n", fontSize = 12.sp, color = Color(0xFF1976D2))
                    Text("Nota 1: Alavancagem Crítica (Risco Altíssimo)", fontWeight = FontWeight.Bold)
                    Text("> 40% do valor total dos ativos.\n", fontSize = 12.sp)
                    Text("Nota 2: Alavancagem Alta (Risco Elevado)", fontWeight = FontWeight.Bold)
                    Text("Entre 25% e 40% do valor dos ativos.\n", fontSize = 12.sp)
                    Text("Nota 3: Alavancagem Moderada (Risco Médio)", fontWeight = FontWeight.Bold)
                    Text("Entre 15% e 25% (Padrão de mercado).\n", fontSize = 12.sp)
                    Text("Nota 4: Alavancagem Baixa (Risco Baixo)", fontWeight = FontWeight.Bold)
                    Text("Entre 5% e 15%. Equilíbrio saudável.\n", fontSize = 12.sp)
                    Text("Nota 5: Alavancagem Mínima (Risco Mínimo)", fontWeight = FontWeight.Bold)
                    Text("< 5% (Fundo conservador ou com muito caixa).\n", fontSize = 12.sp)
                    Text("Nota 0: Desativa o critério manual e usa o automático se disponível.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = { Button(onClick = { showLeverageInfo = false }) { Text("Entendi") } }
        )
    }

    LaunchedEffect(data.ticker) {
        indicatorStates.clear()
        if (data is AssetData.Stock) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["lpa"] = formatBR(data.lpa); indicatorStates["vpa"] = formatBR(data.vpa)
            indicatorStates["roe"] = formatBR(data.roe * 100); indicatorStates["dy"] = formatBR(data.dividendYield * 100)
            indicatorStates["dy5"] = formatBR(data.dividendYield5Years * 100); indicatorStates["de"] = formatBR(data.debtToEquity)
            indicatorStates["deEbitda"] = formatBR(data.debtToEbitda)
            indicatorStates["ml"] = formatBR(data.netMargin * 100); indicatorStates["pl"] = formatBR(data.pl)
            indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["payout"] = formatBR(data.payout * 100)
            indicatorStates["basel"] = formatBR(data.baselIndex * 100)
            indicatorStates["graham"] = formatBR(data.grahamPrice); indicatorStates["bazin"] = formatBR(data.bazinPrice)
            indicatorStates["valSource"] = data.valuationSource
            indicatorStates["cLuc"] = formatBR(data.cagrProfit5Years * 100)
            indicatorStates["cRec"] = formatBR(data.cagrRevenue5Years * 100)
        } else if (data is AssetData.Fii) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["vac"] = formatBR(data.vacancy * 100)
            indicatorStates["y12"] = formatBR(data.yield12m * 100); indicatorStates["y5"] = formatBR(data.avgYield5Years * 100)
            indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0)
            indicatorStates["prop"] = data.propertyCount.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0)
            indicatorStates["mFee"] = formatBR(data.managementFee * 100); indicatorStates["walt"] = formatBR(data.weightedLeaseTerm)
            indicatorStates["mLev"] = if (data.fieldSources?.get("lev") == FieldSource.INTERNET || data.leverageValue > 0) formatBR(data.leverageValue * 100) else ""
            indicatorStates["mType"] = data.managementType
            indicatorStates["multiT"] = if (data.multiTenant) "Sim" else "Não"
            indicatorStates["tScore"] = data.tenantScore.toString()
            indicatorStates["lScore"] = data.leverageScore.toString()
        } else if (data is AssetData.Etf) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["aFee"] = formatBR(data.adminFee * 100)
            indicatorStates["te"] = formatBR(data.trackingError * 100)
            indicatorStates["vol"] = formatBR(data.avgDailyVolume / 1_000_000.0); indicatorStates["hold"] = data.numberOfHoldings.toString()
        } else if (data is AssetData.Bdr) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["dy"] = formatBR(data.dividendYield * 100); indicatorStates["par"] = data.parity
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Edição Manual", style = MaterialTheme.typography.titleMedium, color = Color(0xFF673AB7))
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Já possuo na carteira", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = inPortfolioState, onCheckedChange = { inPortfolioState = it })
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tipo de Ativo", modifier = Modifier.weight(1f), fontSize = 12.sp)
            Box(modifier = Modifier.weight(1.8f)) {
                OutlinedButton(
                    onClick = { showTypeMenu = true },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(currentTypeLabel, fontSize = 13.sp)
                }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    assetTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                showTypeMenu = false
                                if (type != currentTypeLabel) {
                                    pendingType = type
                                    showConfirmDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }

        if (data is AssetData.Stock || data is AssetData.Fii) {
            val currentClassification = if (data is AssetData.Stock) stockClassification else fiiClassification
            
            // Menu de Setor
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Setor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box(modifier = Modifier.weight(1.8f)) {
                    OutlinedButton(
                        onClick = { showSectorMenu = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(if (sectorState.isNullOrBlank()) "Selecionar" else sectorState, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showSectorMenu, onDismissRequest = { showSectorMenu = false }) {
                        currentClassification.keys.forEach { sector ->
                            DropdownMenuItem(
                                text = { Text(sector) },
                                onClick = {
                                    sectorState = sector
                                    subSectorState = "" // Reseta subsetor ao mudar setor
                                    showSectorMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Menu de Subsetor
            if (!sectorState.isNullOrBlank()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subsetor", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Box(modifier = Modifier.weight(1.8f)) {
                        OutlinedButton(
                            onClick = { showSubSectorMenu = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(if (subSectorState.isNullOrBlank()) "Selecionar" else subSectorState, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = showSubSectorMenu, onDismissRequest = { showSubSectorMenu = false }) {
                            currentClassification[sectorState]?.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        subSectorState = sub
                                        showSubSectorMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        EditRow("Nome", nameState, source = data.fieldSources?.get("name")) { nameState = it }
        EditRow("Preço", priceState, true, source = data.fieldSources?.get("currentPrice")) { priceState = it }
        EditRow("Cotas", indicatorStates["cotas"] ?: "", true, source = data.fieldSources?.get("cotas")) { indicatorStates["cotas"] = it }

        if (data is AssetData.Stock) {
            EditRow("LPA", indicatorStates["lpa"] ?: "", true, data.fieldSources?.get("lpa")) { indicatorStates["lpa"] = it }
            EditRow("VPA", indicatorStates["vpa"] ?: "", true, data.fieldSources?.get("vpa")) { indicatorStates["vpa"] = it }
            EditRow("P/L", indicatorStates["pl"] ?: "", true, data.fieldSources?.get("pl")) { indicatorStates["pl"] = it }
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, data.fieldSources?.get("pvp")) { indicatorStates["pvp"] = it }
            EditRow("ROE (%)", indicatorStates["roe"] ?: "", true, data.fieldSources?.get("roe")) { indicatorStates["roe"] = it }
            
            if (subSectorState != "Bancos") {
                EditRow("Margem Líq (%)", indicatorStates["ml"] ?: "", true, data.fieldSources?.get("ml")) { indicatorStates["ml"] = it }
                EditRow("Dív/Patrim", indicatorStates["de"] ?: "", true, data.fieldSources?.get("de")) { indicatorStates["de"] = it }
                EditRow("Dív/EBITDA", indicatorStates["deEbitda"] ?: "", true, data.fieldSources?.get("deEbitda")) { indicatorStates["deEbitda"] = it }
            }

            EditRow("CAGR Lucro (%)", indicatorStates["cLuc"] ?: "", true, source = data.fieldSources?.get("cLuc")) { indicatorStates["cLuc"] = it }
            EditRow("CAGR Rec. (%)", indicatorStates["cRec"] ?: "", true, source = data.fieldSources?.get("cRec")) { indicatorStates["cRec"] = it }

            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true, data.fieldSources?.get("dy")) { indicatorStates["dy"] = it }
            EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true, data.fieldSources?.get("dy5")) { indicatorStates["dy5"] = it }
            EditRow("Payout (%)", indicatorStates["payout"] ?: "", true, data.fieldSources?.get("payout")) { indicatorStates["payout"] = it }
            
            if (subSectorState == "Bancos") {
                EditRow("Índ. Basileia (%)", indicatorStates["basel"] ?: "", true, data.fieldSources?.get("basel")) { indicatorStates["basel"] = it }
            }
            Spacer(modifier = Modifier.height(8.dp))
            EditRow("Preço Graham", indicatorStates["graham"] ?: "", true, data.fieldSources?.get("graham")) { indicatorStates["graham"] = it }
            EditRow("Preço Bazin", indicatorStates["bazin"] ?: "", true, data.fieldSources?.get("bazin")) { indicatorStates["bazin"] = it }
        } else if (data is AssetData.Fii) {
            val isPaper = sectorState == "Papel" || subSectorState.contains("Recebíveis")
            val isFoF = subSectorState.contains("FOFs")
            val isShopping = subSectorState.contains("Shopping")
            
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, data.fieldSources?.get("pvp")) { indicatorStates["pvp"] = it }
            
            if (!isPaper && !isFoF) {
                EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true, data.fieldSources?.get("vac")) { indicatorStates["vac"] = it }
            }
            
            EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true, data.fieldSources?.get("y12")) { indicatorStates["y12"] = it }
            EditRow("DY 5a (%)", indicatorStates["y5"] ?: "", true, data.fieldSources?.get("y5")) { indicatorStates["y5"] = it }
            
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true, source = data.fieldSources?.get("vol")) { indicatorStates["vol"] = it }
            
            if (!isPaper && !isFoF) {
                EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true, data.fieldSources?.get("prop")) { indicatorStates["prop"] = it }
            }
                
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    val label = when {
                        isPaper -> "Nota Devedores"
                        isFoF -> "Nota Carteira"
                        else -> "Nota Inquilino"
                    }
                    Text(label, fontSize = 12.sp)
                    IconButton(onClick = { showTenantInfo = true }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(14.dp))
                    }
                }
                Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val current = (indicatorStates["tScore"] ?: "0").toInt()
                    (0..5).forEach { score ->
                        TextButton(
                            onClick = { indicatorStates["tScore"] = score.toString() },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = score.toString(),
                                fontWeight = if (current == score) FontWeight.Bold else FontWeight.Normal,
                                color = if (current == score) Color(0xFF1976D2) else Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (!isPaper && !isFoF) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Multi-Inquilino", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Row(modifier = Modifier.weight(1.8f)) {
                        val current = indicatorStates["multiT"] ?: "Sim"
                        TextButton(onClick = { indicatorStates["multiT"] = "Sim" }) { Text("Sim", color = if(current=="Sim") Color(0xFF2E7D32) else Color.Gray) }
                        TextButton(onClick = { indicatorStates["multiT"] = "Não" }) { Text("Não", color = if(current=="Não") Color.Red else Color.Gray) }
                    }
                }
            }
            
            EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true, data.fieldSources?.get("aum")) { indicatorStates["aum"] = it }
            EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true, data.fieldSources?.get("mFee")) { indicatorStates["mFee"] = it }
            
            EditRow("Alavancagem (%)", indicatorStates["mLev"] ?: "", true, data.fieldSources?.get("lev")) { indicatorStates["mLev"] = it }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nota Alavancagem", fontSize = 12.sp)
                    IconButton(onClick = { showLeverageInfo = true }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(14.dp))
                    }
                }
                Row(modifier = Modifier.weight(1.8f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val current = (indicatorStates["lScore"] ?: "0").toInt()
                    (0..5).forEach { score ->
                        TextButton(
                            onClick = { indicatorStates["lScore"] = score.toString() },
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = score.toString(),
                                fontWeight = if (current == score) FontWeight.Bold else FontWeight.Normal,
                                color = if (current == score) Color(0xFFD32F2F) else Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            
            if (!isPaper && !isShopping && !isFoF) {
                EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true, data.fieldSources?.get("walt")) { indicatorStates["walt"] = it }
            }

            EditRow("Gestão", indicatorStates["mType"] ?: "", false, data.fieldSources?.get("mType")) { indicatorStates["mType"] = it }
        } else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true, data.fieldSources?.get("aFee")) { indicatorStates["aFee"] = it }
            EditRow("Tracking Error (%)", indicatorStates["te"] ?: "", true, data.fieldSources?.get("te")) { indicatorStates["te"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true, data.fieldSources?.get("vol")) { indicatorStates["vol"] = it }
            EditRow("Holdings", indicatorStates["hold"] ?: "", true, data.fieldSources?.get("hold")) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true, data.fieldSources?.get("dy")) { indicatorStates["dy"] = it }
            EditRow("Paridade", indicatorStates["par"] ?: "", source = data.fieldSources?.get("par")) { indicatorStates["par"] = it }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Button(onClick = {
                val sharesNum = parseBR(indicatorStates["cotas"] ?: "0")
                
                // Nova lógica: Identifica o que foi alterado para travar como USER
                val newSources = data.fieldSources?.toMutableMap() ?: mutableMapOf()
                
                // Campos base
                if (nameState != data.name) newSources["name"] = FieldSource.USER
                if (parseBR(priceState) != data.currentPrice) newSources["currentPrice"] = FieldSource.USER
                if (sectorState != data.sector) newSources["sector"] = FieldSource.USER
                if (subSectorState != data.subSector) newSources["subSector"] = FieldSource.USER
                if (sharesNum != data.sharesCount) newSources["cotas"] = FieldSource.USER

                val updated = when (data) {
                    is AssetData.Stock -> {
                        val stock = data.copy(
                            name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState,
                            sharesCount = sharesNum,
                            lpa = parseBR(indicatorStates["lpa"] ?: "0"), vpa = parseBR(indicatorStates["vpa"] ?: "0"),
                            roe = parseBR(indicatorStates["roe"] ?: "0") / 100.0, dividendYield = parseBR(indicatorStates["dy"] ?: "0") / 100.0,
                            dividendYield5Years = parseBR(indicatorStates["dy5"] ?: "0") / 100.0, payout = parseBR(indicatorStates["payout"] ?: "0") / 100.0,
                            paidDividendsLast5Years = indicatorStates["paidDiv"] == "Sim",
                            cagrProfit5Years = parseBR(indicatorStates["cLuc"] ?: "0") / 100.0,
                            cagrRevenue5Years = parseBR(indicatorStates["cRec"] ?: "0") / 100.0,
                            netMargin = parseBR(indicatorStates["ml"] ?: "0") / 100.0, 
                            debtToEquity = parseBR(indicatorStates["de"] ?: "0"),
                            debtToEbitda = parseBR(indicatorStates["deEbitda"] ?: "0"),
                            pl = parseBR(indicatorStates["pl"] ?: "0"), pvp = parseBR(indicatorStates["pvp"] ?: "0"),
                            baselIndex = parseBR(indicatorStates["basel"] ?: "0") / 100.0,
                            grahamPrice = parseBR(indicatorStates["graham"] ?: "0"), bazinPrice = parseBR(indicatorStates["bazin"] ?: "0")
                        )
                        // Marca indicadores editados
                        listOf("lpa", "vpa", "pl", "pvp", "roe", "ml", "de", "deEbitda", "dy", "dy5", "payout", "basel", "graham", "bazin", "cLuc", "cRec").forEach { key ->
                            if (indicatorStates.containsKey(key)) newSources[key] = FieldSource.USER
                        }
                        stock
                    }
                    is AssetData.Fii -> {
                        val fii = data.copy(
                            name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState,
                            sharesCount = sharesNum,
                            pvp = parseBR(indicatorStates["pvp"] ?: "0"), vacancy = parseBR(indicatorStates["vac"] ?: "0") / 100.0,
                            yield12m = parseBR(indicatorStates["y12"] ?: "0") / 100.0, avgYield5Years = parseBR(indicatorStates["y5"] ?: "0") / 100.0,
                            propertyCount = parseBR(indicatorStates["prop"] ?: "0").toInt(), 
                            tenantScore = (indicatorStates["tScore"] ?: "0").toInt(),
                            leverageScore = (indicatorStates["lScore"] ?: "0").toInt(),
                            leverageValue = parseBR(indicatorStates["mLev"] ?: "0") / 100.0,
                            avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0") * 1_000_000.0,
                            aum = parseBR(indicatorStates["aum"] ?: "0") * 1_000_000.0,
                            managementFee = parseBR(indicatorStates["mFee"] ?: "0") / 100.0, weightedLeaseTerm = parseBR(indicatorStates["walt"] ?: "0"),
                            fundType = sectorState, managementType = indicatorStates["mType"] ?: ""
                        )
                        listOf("pvp", "vac", "y12", "y5", "vol", "prop", "aum", "mFee", "walt", "mLev").forEach { key ->
                             if (indicatorStates.containsKey(key)) newSources[key] = FieldSource.USER
                        }
                        fii
                    }
                    is AssetData.Etf -> {
                        val etf = data.copy(
                            name = nameState, currentPrice = parseBR(priceState), sector = "ETF", subSector = "ETF", isInPortfolio = inPortfolioState,
                            sharesCount = sharesNum,
                            adminFee = parseBR(indicatorStates["aFee"] ?: "0") / 100.0, 
                            trackingError = parseBR(indicatorStates["te"] ?: "0") / 100.0,
                            avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0") * 1_000_000.0, numberOfHoldings = parseBR(indicatorStates["hold"] ?: "0").toInt()
                        )
                        listOf("aFee", "te", "vol", "hold").forEach { key ->
                            if (indicatorStates.containsKey(key)) newSources[key] = FieldSource.USER
                        }
                        etf
                    }
                    is AssetData.Bdr -> {
                        val bdr = data.copy(
                            name = nameState, currentPrice = parseBR(priceState), sector = "BDR", subSector = "BDR", isInPortfolio = inPortfolioState,
                            sharesCount = sharesNum,
                            dividendYield = parseBR(indicatorStates["dy"] ?: "0") / 100.0, parity = indicatorStates["par"] ?: "1:1"
                        )
                        listOf("dy", "par").forEach { key ->
                            if (indicatorStates.containsKey(key)) newSources[key] = FieldSource.USER
                        }
                        bdr
                    }
                }
                updated.fieldSources = newSources
                onSave(updated)
            }, modifier = Modifier.weight(1f)) { Text("Salvar") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Deletar") }
        }
    }
}

@Composable
fun EditRow(label: String, value: String, isNum: Boolean = false, source: FieldSource? = null, onValueChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp)
            val s = source // Evita problemas de smart cast
            if (s != null) {
                val icon = when(s) {
                    FieldSource.INTERNET -> Icons.Default.Language
                    FieldSource.SIMULATION -> Icons.Default.Science
                    FieldSource.USER -> Icons.Default.Edit
                    FieldSource.DIVERGENT -> Icons.Default.Warning
                }
                val color = when(s) {
                    FieldSource.INTERNET -> Color(0xFF1976D2)
                    FieldSource.SIMULATION -> Color(0xFF9C27B0)
                    FieldSource.USER -> Color(0xFF4CAF50)
                    FieldSource.DIVERGENT -> Color(0xFFE65100)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = s.name,
                    modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    tint = color.copy(alpha = 0.6f)
                )
            }
        }
        BasicTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.weight(1.8f).height(32.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF1A237E)),
            keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Decimal else KeyboardType.Text),
            decorationBox = { inner -> Surface(shape = MaterialTheme.shapes.extraSmall, border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) { Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) { inner() } } }
        )
    }
}

@Composable
fun ScoreResult(data: AssetData, score: Double, viewModel: StockViewModel = viewModel()) {
    val integrityWarnings = remember(data) { viewModel.getIntegrityWarnings(data) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("${data.ticker} - ${data.name}", fontWeight = FontWeight.Bold)
        Text("Nota: ${formatBR(score)} / 10", style = MaterialTheme.typography.headlineMedium)
        
        if (integrityWarnings.isNotEmpty()) {
            Surface(
                color = Color(0xFFFFF3E0),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Text(" Avisos de Integridade", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    integrityWarnings.forEach { msg ->
                        Text("• $msg", fontSize = 11.sp, color = Color(0xFF5D4037), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        AssetDetails(data)
        ProsConsSection(data.pros, data.cons)
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column {
        DetailsRow("Preço Atual", "R$ ${formatBR(data.currentPrice)}")
        if (data is AssetData.Stock) {
            val grahamPrice = if (data.grahamPrice > 0) data.grahamPrice else if (data.lpa > 0 && data.vpa > 0) sqrt(22.5 * data.lpa * data.vpa) else 0.0
            val bazinPrice = if (data.bazinPrice > 0) data.bazinPrice else if (data.dividendYield5Years > 0) (data.dividendYield5Years * data.currentPrice) / 0.06 else 0.0
            if (grahamPrice > 0) DetailsRow("Graham", "R$ ${formatBR(grahamPrice)}", if (data.currentPrice <= grahamPrice) Color(0xFF2E7D32) else Color.Red)
            if (bazinPrice > 0) DetailsRow("Bazin", "R$ ${formatBR(bazinPrice)}", if (data.currentPrice <= bazinPrice) Color(0xFF2E7D32) else Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
            DetailsRow("P/L", formatBR(data.pl))
            DetailsRow("P/VP", formatBR(data.pvp))
            DetailsRow("ROE", formatBR(data.roe * 100) + "%")
            
            if (data.subSector != "Bancos") {
                DetailsRow("Margem Líq", formatBR(data.netMargin * 100) + "%")
                DetailsRow("Dív/Patrim", formatBR(data.debtToEquity))
                DetailsRow("Dív/EBITDA", formatBR(data.debtToEbitda))
            } else {
                DetailsRow("Índ. Basileia", formatBR(data.baselIndex * 100) + "%")
            }
        } else if (data is AssetData.Fii) {
            val isPaper = data.sector == "Papel" || data.subSector.contains("Recebíveis")
            val isFoF = data.subSector.contains("FOFs")
            val isShopping = data.subSector.contains("Shopping")

            DetailsRow("P/VP", formatBR(data.pvp))
            DetailsRow("DY 12m", formatBR(data.yield12m * 100) + "%")
            
            if (data.leverageScore > 0) {
                val levLabel = when(data.leverageScore) {
                    5 -> "Mínima"
                    4 -> "Baixa"
                    3 -> "Moderada"
                    2 -> "Alta"
                    else -> "Crítica"
                }
                DetailsRow("Alavancagem", levLabel, if (data.leverageScore <= 2) Color.Red else Color.Unspecified)
            } else if (data.leverageValue > 0) {
                DetailsRow("Alavancagem (Auto)", formatBR(data.leverageValue * 100) + "%", if (data.leverageValue > 0.3) Color.Red else Color.Unspecified)
            }
            
            if (!isPaper && !isFoF) {
                DetailsRow("Vacância", formatBR(data.vacancy * 100) + "%", if (data.vacancy <= 0.05) Color(0xFF2E7D32) else Color.Red)
            }
            if (!isPaper && !isFoF && !isShopping) {
                DetailsRow("WALT", formatBR(data.weightedLeaseTerm) + " anos")
            }
            if (!isPaper && !isFoF) {
                DetailsRow("Imóveis", data.propertyCount.toString())
            }
        } else if (data is AssetData.Etf) {
            DetailsRow("Taxa Adm", formatBR(data.adminFee * 100) + "%", if (data.adminFee <= 0.005) Color(0xFF2E7D32) else Color.Red)
            DetailsRow("Vol. Diário", "M R$ ${formatBR(data.avgDailyVolume / 1_000_000.0)}")
            DetailsRow("Holdings", data.numberOfHoldings.toString())
        } else if (data is AssetData.Bdr) {
            DetailsRow("DY Atual", formatBR(data.dividendYield * 100) + "%")
            DetailsRow("Paridade", data.parity)
        }
    }
}

@Composable
fun DetailsRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray); Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun ProsConsSection(pros: List<String>, cons: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (pros.isNotEmpty()) {
            Text("Prós", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            pros.forEach { Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp)) }
        }
        if (cons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Contras", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            cons.forEach { Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp)) }
        }
    }
}
