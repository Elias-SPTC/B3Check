package com.example.b3check

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                    label = { Text("Recomenda", fontSize = 10.sp) }
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
                                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (asset.isInPortfolio) {
                                    Surface(color = Color(0xFFE8F5E9), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.padding(start = 8.dp)) {
                                        Text("CARTEIRA", fontSize = 8.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(asset.name, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.deleteAsset(asset.ticker) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioBalanceScreen(viewModel: StockViewModel = viewModel()) {
    val portfolio by viewModel.portfolioAllocation.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Equilíbrio da Carteira", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1976D2))
        Text("Alocação sugerida baseada na qualidade (Nota)", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (portfolio.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Marque ativos como 'Já possuo na carteira' no editor manual.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
            }
        } else {
            portfolio.forEach { (asset, percent) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(asset.sector, fontSize = 10.sp, color = Color.Gray)
                        }
                        Text(formatBR(percent) + "%", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1976D2))
                    }
                }
            }
        }
    }
}

@Composable
fun InvestScreen(viewModel: StockViewModel = viewModel()) {
    val assets by viewModel.allAssets.collectAsState()
    val portfolio = assets.filter { it.isInPortfolio }
    val allocation by viewModel.portfolioAllocation.collectAsState()
    
    var investAmount by remember { mutableStateOf("") }
    val investSuggestions = remember { mutableStateMapOf<String, Double>() }
    
    // Estados locais para edição sem interferência do reformat automático
    val editStates = remember { mutableStateMapOf<String, String>() }

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
            Text("Aportes", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
        }

        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(portfolio) { asset ->
                val applied = asset.sharesCount * asset.currentPrice
                val recPercent = allocation.find { it.first.ticker == asset.ticker }?.second ?: 0.0
                val suggest = investSuggestions[asset.ticker] ?: 0.0
                
                val cKey = "${asset.ticker}_c"
                val pKey = "${asset.ticker}_p"
                
                // Sincroniza estado inicial se não houver edição ativa
                val cotasText = editStates.getOrPut(cKey) { formatBR(asset.sharesCount, true) }
                val precoText = editStates.getOrPut(pKey) { formatBR(asset.currentPrice, true) }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.ticker, modifier = Modifier.weight(1.2f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    
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
                    Text(formatBR(suggest), modifier = Modifier.weight(1.8f), fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val totalApplied = portfolio.sumOf { it.sharesCount * it.currentPrice }
                val totalSuggest = investSuggestions.values.sum()
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("TOTAL", modifier = Modifier.weight(3.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(formatBR(totalApplied), modifier = Modifier.weight(2.0f).padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
                    Text("", modifier = Modifier.weight(1.0f))
                    Text(formatBR(totalSuggest), modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32), textAlign = TextAlign.End)
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
                val total = parseBR(investAmount)
                if (total > 0) {
                    investSuggestions.clear()
                    allocation.forEach { (asset, percent) ->
                        investSuggestions[asset.ticker] = total * (percent / 100.0)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Top 5 para Pesquisa", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
        Text("Ativos na Watchlist com melhores fundamentos", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (recs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum ativo de pesquisa encontrado.", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(recs.withIndex().toList()) { (index, asset) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}º", fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 12.dp), fontSize = 15.sp)
                            Column {
                                Text(asset.ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(asset.name, fontSize = 10.sp, maxLines = 1)
                                Text("Nota: ${asset.sector}", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
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
    var pendingType by remember { mutableStateOf("") }

    val stockClassification = mapOf(
        "Financeiro" to listOf("Bancos", "Seguradoras", "Serviços Financeiros", "Exploração de Imóveis"),
        "Utilidade Pública" to listOf("Energia Elétrica", "Água e Saneamento", "Gás"),
        "Materiais Básicos" to listOf("Mineração", "Siderurgia e Metalurgia", "Papel e Celulose", "Químicos"),
        "Consumo Cíclico" to listOf("Comércio", "Construção Civil", "Roupas", "Turismo", "Veículos"),
        "Consumo Não Cíclico e Saúde" to listOf("Alimentos", "Bebidas", "Agropecuária", "Hospitais", "Laboratórios", "Farmácias", "Uso Pessoal e Limpeza")
    )

    val fiiClassification = mapOf(
        "Tijolo" to listOf("Lajes Corporativas", "Logística / Industrial", "Shopping Centers", "Hotéis", "Hospitais", "Agências bancárias", "Fiagros"),
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
        
        val updated = when (newType) {
            "FII" -> AssetData.Fii(t, n, p, s, ss, isInPortfolio = inPortfolioState, sharesCount = shares)
            "ETF" -> AssetData.Etf(t, n, p, "ETF", "ETF", isInPortfolio = inPortfolioState, sharesCount = shares)
            "BDR" -> AssetData.Bdr(t, n, p, "BDR", "BDR", isInPortfolio = inPortfolioState, sharesCount = shares)
            else -> AssetData.Stock(t, n, p, s, ss, isInPortfolio = inPortfolioState, sharesCount = shares)
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

    LaunchedEffect(data.ticker) {
        indicatorStates.clear()
        if (data is AssetData.Stock) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["lpa"] = formatBR(data.lpa); indicatorStates["vpa"] = formatBR(data.vpa)
            indicatorStates["roe"] = formatBR(data.roe * 100); indicatorStates["dy"] = formatBR(data.dividendYield * 100)
            indicatorStates["dy5"] = formatBR(data.dividendYield5Years * 100); indicatorStates["de"] = formatBR(data.debtToEquity)
            indicatorStates["ml"] = formatBR(data.netMargin * 100); indicatorStates["pl"] = formatBR(data.pl)
            indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["payout"] = formatBR(data.payout * 100)
            indicatorStates["basel"] = formatBR(data.baselIndex)
            indicatorStates["graham"] = formatBR(data.grahamPrice); indicatorStates["bazin"] = formatBR(data.bazinPrice)
            indicatorStates["valSource"] = data.valuationSource
        } else if (data is AssetData.Fii) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["pvp"] = formatBR(data.pvp); indicatorStates["vac"] = formatBR(data.vacancy * 100)
            indicatorStates["y12"] = formatBR(data.yield12m * 100); indicatorStates["y5"] = formatBR(data.avgYield5Years * 100)
            indicatorStates["prop"] = data.propertyCount.toString(); indicatorStates["aum"] = formatBR(data.aum / 1_000_000.0)
            indicatorStates["mFee"] = formatBR(data.managementFee * 100); indicatorStates["walt"] = formatBR(data.weightedLeaseTerm)
            indicatorStates["mType"] = data.managementType
        } else if (data is AssetData.Etf) {
            indicatorStates["cotas"] = formatBR(data.sharesCount, true)
            indicatorStates["aFee"] = formatBR(data.adminFee * 100); indicatorStates["te"] = formatBR(data.trackingError)
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
        EditRow("Cotas", indicatorStates["cotas"] ?: "", true) { indicatorStates["cotas"] = it }

        if (data is AssetData.Stock) {
            EditRow("LPA", indicatorStates["lpa"] ?: "", true, data.fieldSources?.get("lpa")) { indicatorStates["lpa"] = it }
            EditRow("VPA", indicatorStates["vpa"] ?: "", true, data.fieldSources?.get("vpa")) { indicatorStates["vpa"] = it }
            EditRow("P/L", indicatorStates["pl"] ?: "", true, data.fieldSources?.get("pl")) { indicatorStates["pl"] = it }
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, data.fieldSources?.get("pvp")) { indicatorStates["pvp"] = it }
            EditRow("ROE (%)", indicatorStates["roe"] ?: "", true, data.fieldSources?.get("roe")) { indicatorStates["roe"] = it }
            EditRow("Margem Líq (%)", indicatorStates["ml"] ?: "", true, data.fieldSources?.get("ml")) { indicatorStates["ml"] = it }
            EditRow("Dív/Patrim", indicatorStates["de"] ?: "", true, data.fieldSources?.get("de")) { indicatorStates["de"] = it }
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true, data.fieldSources?.get("dy")) { indicatorStates["dy"] = it }
            EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true, data.fieldSources?.get("dy5")) { indicatorStates["dy5"] = it }
            EditRow("Payout (%)", indicatorStates["payout"] ?: "", true, data.fieldSources?.get("payout")) { indicatorStates["payout"] = it }
            
            if (subSectorState == "Bancos") {
                EditRow("Índ. Basileia", indicatorStates["basel"] ?: "", true, data.fieldSources?.get("basel")) { indicatorStates["basel"] = it }
            }
            Spacer(modifier = Modifier.height(8.dp))
            EditRow("Preço Graham", indicatorStates["graham"] ?: "", true, data.fieldSources?.get("graham")) { indicatorStates["graham"] = it }
            EditRow("Preço Bazin", indicatorStates["bazin"] ?: "", true, data.fieldSources?.get("bazin")) { indicatorStates["bazin"] = it }
        } else if (data is AssetData.Fii) {
            val isPaper = sectorState == "Papel" || subSectorState.contains("Recebíveis") || subSectorState.contains("FOFs")
            val isShopping = subSectorState.contains("Shopping")
            
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, data.fieldSources?.get("pvp")) { indicatorStates["pvp"] = it }
            
            if (!isPaper) {
                EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true, data.fieldSources?.get("vac")) { indicatorStates["vac"] = it }
            }
            
            EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true, data.fieldSources?.get("y12")) { indicatorStates["y12"] = it }
            EditRow("DY 5a (%)", indicatorStates["y5"] ?: "", true, data.fieldSources?.get("y5")) { indicatorStates["y5"] = it }
            
            if (!isPaper) {
                EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true, data.fieldSources?.get("prop")) { indicatorStates["prop"] = it }
            }
            
            EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true, data.fieldSources?.get("aum")) { indicatorStates["aum"] = it }
            EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true, data.fieldSources?.get("mFee")) { indicatorStates["mFee"] = it }
            
            if (!isPaper && !isShopping) {
                EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true, data.fieldSources?.get("walt")) { indicatorStates["walt"] = it }
            }

            EditRow("Gestão", indicatorStates["mType"] ?: "", false, data.fieldSources?.get("mType")) { indicatorStates["mType"] = it }
        } else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true, data.fieldSources?.get("aFee")) { indicatorStates["aFee"] = it }
            EditRow("Tracking Error", indicatorStates["te"] ?: "", true, data.fieldSources?.get("te")) { indicatorStates["te"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true, data.fieldSources?.get("vol")) { indicatorStates["vol"] = it }
            EditRow("Holdings", indicatorStates["hold"] ?: "", true, data.fieldSources?.get("hold")) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true, data.fieldSources?.get("dy")) { indicatorStates["dy"] = it }
            EditRow("Paridade", indicatorStates["par"] ?: "", source = data.fieldSources?.get("par")) { indicatorStates["par"] = it }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Button(onClick = {
                val sharesNum = parseBR(indicatorStates["cotas"] ?: "0")
                val updated = when (data) {
                    is AssetData.Stock -> data.copy(
                        name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState,
                        sharesCount = sharesNum,
                        lpa = parseBR(indicatorStates["lpa"] ?: "0"), vpa = parseBR(indicatorStates["vpa"] ?: "0"),
                        roe = parseBR(indicatorStates["roe"] ?: "0") / 100.0, dividendYield = parseBR(indicatorStates["dy"] ?: "0") / 100.0,
                        dividendYield5Years = parseBR(indicatorStates["dy5"] ?: "0") / 100.0, payout = parseBR(indicatorStates["payout"] ?: "0") / 100.0,
                        netMargin = parseBR(indicatorStates["ml"] ?: "0") / 100.0, debtToEquity = parseBR(indicatorStates["de"] ?: "0"),
                        pl = parseBR(indicatorStates["pl"] ?: "0"), pvp = parseBR(indicatorStates["pvp"] ?: "0"),
                        baselIndex = parseBR(indicatorStates["basel"] ?: "0"),
                        grahamPrice = parseBR(indicatorStates["graham"] ?: "0"), bazinPrice = parseBR(indicatorStates["bazin"] ?: "0")
                    )
                    is AssetData.Fii -> data.copy(
                        name = nameState, currentPrice = parseBR(priceState), sector = sectorState, subSector = subSectorState, isInPortfolio = inPortfolioState,
                        sharesCount = sharesNum,
                        pvp = parseBR(indicatorStates["pvp"] ?: "0"), vacancy = parseBR(indicatorStates["vac"] ?: "0") / 100.0,
                        yield12m = parseBR(indicatorStates["y12"] ?: "0") / 100.0, avgYield5Years = parseBR(indicatorStates["y5"] ?: "0") / 100.0,
                        propertyCount = parseBR(indicatorStates["prop"] ?: "0").toInt(), aum = parseBR(indicatorStates["aum"] ?: "0") * 1_000_000.0,
                        managementFee = parseBR(indicatorStates["mFee"] ?: "0") / 100.0, weightedLeaseTerm = parseBR(indicatorStates["walt"] ?: "0"),
                        fundType = sectorState, managementType = indicatorStates["mType"] ?: ""
                    )
                    is AssetData.Etf -> data.copy(
                        name = nameState, currentPrice = parseBR(priceState), sector = "ETF", subSector = "ETF", isInPortfolio = inPortfolioState,
                        sharesCount = sharesNum,
                        adminFee = parseBR(indicatorStates["aFee"] ?: "0") / 100.0, trackingError = parseBR(indicatorStates["te"] ?: "0"),
                        avgDailyVolume = parseBR(indicatorStates["vol"] ?: "0") * 1_000_000.0, numberOfHoldings = parseBR(indicatorStates["hold"] ?: "0").toInt()
                    )
                    is AssetData.Bdr -> data.copy(
                        name = nameState, currentPrice = parseBR(priceState), sector = "BDR", subSector = "BDR", isInPortfolio = inPortfolioState,
                        sharesCount = sharesNum,
                        dividendYield = parseBR(indicatorStates["dy"] ?: "0") / 100.0, parity = indicatorStates["par"] ?: "1:1"
                    )
                }
                onSave(updated)
            }, modifier = Modifier.weight(1f)) { Text("Salvar") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onDelete(data) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Deletar") }
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
                }
                val color = when(s) {
                    FieldSource.INTERNET -> Color(0xFF1976D2)
                    FieldSource.SIMULATION -> Color(0xFF9C27B0)
                    FieldSource.USER -> Color(0xFF4CAF50)
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
fun ScoreResult(data: AssetData, score: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("${data.ticker} - ${data.name}", fontWeight = FontWeight.Bold)
        Text("Nota: ${formatBR(score)} / 10", style = MaterialTheme.typography.headlineMedium)
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
            DetailsRow("Margem Líq", formatBR(data.netMargin * 100) + "%")
            DetailsRow("Dív/Patrim", formatBR(data.debtToEquity))
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", formatBR(data.pvp))
            DetailsRow("DY 12m", formatBR(data.yield12m * 100) + "%")
            DetailsRow("Vacância", formatBR(data.vacancy * 100) + "%", if (data.vacancy <= 0.05) Color(0xFF2E7D32) else Color.Red)
            DetailsRow("WALT", formatBR(data.weightedLeaseTerm) + " anos")
            DetailsRow("Imóveis", data.propertyCount.toString())
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
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) { Text("Prós", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold); pros.forEach { Text("• $it", fontSize = 11.sp) } }
        Column(modifier = Modifier.weight(1f)) { Text("Contras", color = Color.Red, fontWeight = FontWeight.Bold); cons.forEach { Text("• $it", fontSize = 11.sp) } }
    }
}
