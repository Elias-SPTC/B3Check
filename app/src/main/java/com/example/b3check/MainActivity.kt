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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.b3check.ui.theme.B3CheckTheme
import kotlin.math.sqrt

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
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentTab) {
                0 -> StockAnalysisScreen()
                1 -> AssetListScreen(onAssetClick = { currentTab = 0 })
                2 -> PortfolioBalanceScreen()
                3 -> RecommendationsScreen()
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        viewModel.analyzeTicker(asset.ticker)
                        onAssetClick()
                    }
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.ticker, fontWeight = FontWeight.Bold)
                                if (asset.isInPortfolio) {
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text("CARTEIRA", fontSize = 9.sp, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                            Text(asset.name, fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { viewModel.deleteAsset(asset.ticker) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color.Red)
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
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(asset.ticker, fontWeight = FontWeight.Bold)
                            Text(asset.sector, fontSize = 10.sp, color = Color.Gray)
                        }
                        Text("${String.format("%.1f", percent)}%", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1976D2))
                    }
                }
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
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}º", fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text(asset.ticker, fontWeight = FontWeight.Bold)
                                Text(asset.name, fontSize = 11.sp)
                                Text("Nota: ${asset.sector}", fontSize = 10.sp, color = Color.Gray) // Exemplo simples
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
    var nameState by remember(data) { mutableStateOf(data.name) }
    var priceState by remember(data) { mutableStateOf(data.currentPrice.toString()) }
    var sectorState by remember(data) { mutableStateOf(if (data.sector == "FII" || data.sector == "Ação") "" else data.sector) }
    var inPortfolioState by remember(data) { mutableStateOf(data.isInPortfolio) }
    val indicatorStates = remember(data) { mutableStateMapOf<String, String>() }
    
        var showTypeMenu by remember { mutableStateOf(false) }
        var showSectorMenu by remember { mutableStateOf(false) }
        var showFiiTypeMenu by remember { mutableStateOf(false) }
        var showConfirmDialog by remember { mutableStateOf(false) }
        var pendingType by remember { mutableStateOf("") }

        val sectors = listOf(
            "Bancário", "Energia Elétrica", "Saneamento", "Seguros", 
            "Petróleo e Gás", "Mineração e Siderurgia", "Varejo", 
            "Tecnologia", "Saúde", "Construção", "Agronegócio", 
            "Transportes", "Holdings", "Outros"
        )

        val fiiTypes = listOf(
            "Tijolo (Geral)", "Lajes Corporativas", "Logística", 
            "Shoppings", "Papel (Recebíveis)", "Híbrido", 
            "Fundo de Fundos (FoF)", "Fiagro", "Outros"
        )

        val assetTypes = listOf("Ação", "FII", "ETF", "BDR")
    val currentTypeLabel = when (data) {
        is AssetData.Stock -> "Ação"
        is AssetData.Fii -> "FII"
        is AssetData.Etf -> "ETF"
        is AssetData.Bdr -> "BDR"
    }

    fun parse(v: String) = v.replace(",", ".").toDoubleOrNull() ?: 0.0
    fun format(v: Double) = if (v == 0.0) "" else String.format("%.2f", v).replace(".", ",")

    fun changeType(newType: String) {
        val t = data.ticker
        val n = nameState
        val p = parse(priceState)
        val updated = when (newType) {
            "FII" -> AssetData.Fii(t, n, p, "FII", isInPortfolio = inPortfolioState)
            "ETF" -> AssetData.Etf(t, n, p, "ETF", isInPortfolio = inPortfolioState)
            "BDR" -> AssetData.Bdr(t, n, p, "BDR", isInPortfolio = inPortfolioState)
            else -> AssetData.Stock(t, n, p, "Ação", isInPortfolio = inPortfolioState)
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

    LaunchedEffect(data) {
        if (data is AssetData.Stock) {
            indicatorStates["lpa"] = format(data.lpa); indicatorStates["vpa"] = format(data.vpa)
            indicatorStates["roe"] = format(data.roe * 100); indicatorStates["dy"] = format(data.dividendYield * 100)
            indicatorStates["dy5"] = format(data.dividendYield5Years * 100); indicatorStates["de"] = format(data.debtToEquity)
            indicatorStates["ml"] = format(data.netMargin * 100); indicatorStates["pl"] = format(data.pl)
            indicatorStates["pvp"] = format(data.pvp); indicatorStates["payout"] = format(data.payout * 100)
            indicatorStates["basel"] = format(data.baselIndex)
            indicatorStates["graham"] = format(data.grahamPrice); indicatorStates["bazin"] = format(data.bazinPrice)
            indicatorStates["valSource"] = data.valuationSource
        } else if (data is AssetData.Fii) {
            indicatorStates["pvp"] = format(data.pvp); indicatorStates["vac"] = format(data.vacancy * 100)
            indicatorStates["y12"] = format(data.yield12m * 100); indicatorStates["y5"] = format(data.avgYield5Years * 100)
            indicatorStates["prop"] = data.propertyCount.toString(); indicatorStates["aum"] = format(data.aum / 1_000_000.0)
            indicatorStates["mFee"] = format(data.managementFee * 100); indicatorStates["walt"] = format(data.weightedLeaseTerm)
            indicatorStates["fType"] = data.fundType; indicatorStates["mType"] = data.managementType
        } else if (data is AssetData.Etf) {
            indicatorStates["aFee"] = format(data.adminFee * 100); indicatorStates["te"] = format(data.trackingError)
            indicatorStates["vol"] = format(data.avgDailyVolume / 1_000_000.0); indicatorStates["hold"] = data.numberOfHoldings.toString()
        } else if (data is AssetData.Bdr) {
            indicatorStates["dy"] = format(data.dividendYield * 100); indicatorStates["par"] = data.parity
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

        EditRow("Nome", nameState, source = data.fieldSources?.get("name")) { nameState = it }
        EditRow("Preço", priceState, true, source = data.fieldSources?.get("currentPrice")) { priceState = it }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Segmento", modifier = Modifier.weight(1f), fontSize = 12.sp)
            Box(modifier = Modifier.weight(1.8f)) {
                OutlinedButton(
                    onClick = { showSectorMenu = true },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(if (sectorState.isBlank()) "Selecionar" else sectorState, fontSize = 13.sp)
                }
                DropdownMenu(expanded = showSectorMenu, onDismissRequest = { showSectorMenu = false }) {
                    sectors.forEach { sector ->
                        DropdownMenuItem(
                            text = { Text(sector) },
                            onClick = {
                                sectorState = sector
                                showSectorMenu = false
                            }
                        )
                    }
                }
            }
        }

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
            
            if (sectorState == "Bancário") {
                EditRow("Índ. Basileia", indicatorStates["basel"] ?: "", true, data.fieldSources?.get("basel")) { indicatorStates["basel"] = it }
            }
            Spacer(modifier = Modifier.height(8.dp))
            EditRow("Preço Graham", indicatorStates["graham"] ?: "", true, data.fieldSources?.get("graham")) { indicatorStates["graham"] = it }
            EditRow("Preço Bazin", indicatorStates["bazin"] ?: "", true, data.fieldSources?.get("bazin")) { indicatorStates["bazin"] = it }
        } else if (data is AssetData.Fii) {
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true, data.fieldSources?.get("pvp")) { indicatorStates["pvp"] = it }
            EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true, data.fieldSources?.get("vac")) { indicatorStates["vac"] = it }
            EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true, data.fieldSources?.get("y12")) { indicatorStates["y12"] = it }
            EditRow("DY 5a (%)", indicatorStates["y5"] ?: "", true, data.fieldSources?.get("y5")) { indicatorStates["y5"] = it }
            EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true, data.fieldSources?.get("prop")) { indicatorStates["prop"] = it }
            EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true, data.fieldSources?.get("aum")) { indicatorStates["aum"] = it }
            EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true, data.fieldSources?.get("mFee")) { indicatorStates["mFee"] = it }
            EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true, data.fieldSources?.get("walt")) { indicatorStates["walt"] = it }
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tipo Fundo", modifier = Modifier.weight(1f), fontSize = 12.sp)
                Box(modifier = Modifier.weight(1.8f)) {
                    OutlinedButton(
                        onClick = { showFiiTypeMenu = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        val fType = indicatorStates["fType"] ?: ""
                        Text(if (fType.isBlank()) "Selecionar" else fType, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showFiiTypeMenu, onDismissRequest = { showFiiTypeMenu = false }) {
                        fiiTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    indicatorStates["fType"] = type
                                    showFiiTypeMenu = false
                                }
                            )
                        }
                    }
                }
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
                val updated = when (data) {
                    is AssetData.Stock -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState, isInPortfolio = inPortfolioState,
                        lpa = parse(indicatorStates["lpa"] ?: "0"), vpa = parse(indicatorStates["vpa"] ?: "0"),
                        roe = parse(indicatorStates["roe"] ?: "0") / 100.0, dividendYield = parse(indicatorStates["dy"] ?: "0") / 100.0,
                        dividendYield5Years = parse(indicatorStates["dy5"] ?: "0") / 100.0, payout = parse(indicatorStates["payout"] ?: "0") / 100.0,
                        netMargin = parse(indicatorStates["ml"] ?: "0") / 100.0, debtToEquity = parse(indicatorStates["de"] ?: "0"),
                        pl = parse(indicatorStates["pl"] ?: "0"), pvp = parse(indicatorStates["pvp"] ?: "0"),
                        baselIndex = parse(indicatorStates["basel"] ?: "0"),
                        grahamPrice = parse(indicatorStates["graham"] ?: "0"), bazinPrice = parse(indicatorStates["bazin"] ?: "0")
                    )
                    is AssetData.Fii -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState, isInPortfolio = inPortfolioState,
                        pvp = parse(indicatorStates["pvp"] ?: "0"), vacancy = parse(indicatorStates["vac"] ?: "0") / 100.0,
                        yield12m = parse(indicatorStates["y12"] ?: "0") / 100.0, avgYield5Years = parse(indicatorStates["y5"] ?: "0") / 100.0,
                        propertyCount = parse(indicatorStates["prop"] ?: "0").toInt(), aum = parse(indicatorStates["aum"] ?: "0") * 1_000_000.0,
                        managementFee = parse(indicatorStates["mFee"] ?: "0") / 100.0, weightedLeaseTerm = parse(indicatorStates["walt"] ?: "0"),
                        fundType = indicatorStates["fType"] ?: "", managementType = indicatorStates["mType"] ?: ""
                    )
                    is AssetData.Etf -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState, isInPortfolio = inPortfolioState,
                        adminFee = parse(indicatorStates["aFee"] ?: "0") / 100.0, trackingError = parse(indicatorStates["te"] ?: "0"),
                        avgDailyVolume = parse(indicatorStates["vol"] ?: "0") * 1_000_000.0, numberOfHoldings = parse(indicatorStates["hold"] ?: "0").toInt()
                    )
                    is AssetData.Bdr -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState, isInPortfolio = inPortfolioState,
                        dividendYield = parse(indicatorStates["dy"] ?: "0") / 100.0, parity = indicatorStates["par"] ?: "1:1"
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text("Nota: ${String.format("%.1f", score)} / 10", style = MaterialTheme.typography.headlineMedium)
        AssetDetails(data)
        ProsConsSection(data.pros, data.cons)
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column {
        DetailsRow("Preço Atual", "R$ ${String.format("%.2f", data.currentPrice)}")
        if (data is AssetData.Stock) {
            val grahamPrice = if (data.grahamPrice > 0) data.grahamPrice else if (data.lpa > 0 && data.vpa > 0) sqrt(22.5 * data.lpa * data.vpa) else 0.0
            val bazinPrice = if (data.bazinPrice > 0) data.bazinPrice else if (data.dividendYield5Years > 0) (data.dividendYield5Years * data.currentPrice) / 0.06 else 0.0
            if (grahamPrice > 0) DetailsRow("Graham", "R$ ${String.format("%.2f", grahamPrice)}", if (data.currentPrice <= grahamPrice) Color(0xFF2E7D32) else Color.Red)
            if (bazinPrice > 0) DetailsRow("Bazin", "R$ ${String.format("%.2f", bazinPrice)}", if (data.currentPrice <= bazinPrice) Color(0xFF2E7D32) else Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
            DetailsRow("P/L", String.format("%.2f", data.pl))
            DetailsRow("P/VP", String.format("%.2f", data.pvp))
            DetailsRow("ROE", String.format("%.1f%%", data.roe * 100))
            DetailsRow("Margem Líq", String.format("%.1f%%", data.netMargin * 100))
            DetailsRow("Dív/Patrim", String.format("%.2f", data.debtToEquity))
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", String.format("%.2f", data.pvp))
            DetailsRow("DY 12m", String.format("%.1f%%", data.yield12m * 100))
            DetailsRow("Vacância", String.format("%.1f%%", data.vacancy * 100), if (data.vacancy <= 0.05) Color(0xFF2E7D32) else Color.Red)
            DetailsRow("WALT", String.format("%.1f anos", data.weightedLeaseTerm))
            DetailsRow("Imóveis", data.propertyCount.toString())
        } else if (data is AssetData.Etf) {
            DetailsRow("Taxa Adm", String.format("%.2f%%", data.adminFee * 100), if (data.adminFee <= 0.005) Color(0xFF2E7D32) else Color.Red)
            DetailsRow("Vol. Diário", "M R$ ${String.format("%.1f", data.avgDailyVolume / 1_000_000.0)}")
            DetailsRow("Holdings", data.numberOfHoldings.toString())
        } else if (data is AssetData.Bdr) {
            DetailsRow("DY Atual", String.format("%.1f%%", data.dividendYield * 100))
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
