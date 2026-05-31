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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Restore
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
                2 -> RecommendationsScreen()
            }
        }
    }
}

@Composable
fun AssetListScreen(viewModel: StockViewModel = viewModel(), onAssetClick: () -> Unit = {}) {
    val assets by viewModel.allAssets.collectAsState()
    val context = LocalContext.current

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
            Text("Meus Ativos Salvos", style = MaterialTheme.typography.titleLarge)
            
            Row {
                IconButton(onClick = {
                    createDocumentLauncher.launch("b3check_backup.json")
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
                            Text(asset.ticker, fontWeight = FontWeight.Bold)
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
fun RecommendationsScreen(viewModel: StockViewModel = viewModel()) {
    val recs by viewModel.recommendations.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Top 5 Recomendações", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
        Text("Critérios: Buy & Hold + Dividendos", fontSize = 12.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (recs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Salve ativos para ver recomendações", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(recs.withIndex().toList()) { (index, asset) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}º", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(end = 16.dp))
                            Column {
                                Text(asset.ticker, fontWeight = FontWeight.Bold)
                                Text(asset.name, fontSize = 12.sp)
                                Text("Setor: ${asset.sector}", fontSize = 11.sp, color = Color.Gray)
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
    val searchSource by viewModel.searchSource.collectAsState()
    var selectedAssetType by remember { mutableStateOf("Ação") }
    val assetTypes = listOf("Ação", "FII", "ETF", "BDR")

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScrollableTabRow(
            selectedTabIndex = searchSource.ordinal,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {}
        ) {
            SearchSource.entries.forEach { source ->
                Tab(
                    selected = searchSource == source,
                    onClick = { viewModel.setSource(source) },
                    text = { Text(source.label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchSource == SearchSource.MANUAL) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                assetTypes.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedAssetType == type, 
                            onClick = { 
                                selectedAssetType = type
                                if (uiState is StockUiState.Success && tickerInput.isNotBlank()) {
                                    viewModel.analyzeTicker(tickerInput, type)
                                }
                            }
                        )
                        Text(type, fontSize = 12.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = tickerInput,
            onValueChange = { tickerInput = it.uppercase() },
            label = { Text("Ticker") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.analyzeTicker(tickerInput, selectedAssetType) },
            modifier = Modifier.fillMaxWidth(),
            enabled = tickerInput.isNotBlank()
        ) {
            Text(if (searchSource == SearchSource.MANUAL) "Buscar/Carregar Dados" else "Analisar Ativo")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is StockUiState.Loading -> CircularProgressIndicator()
            is StockUiState.Success -> {
                if (searchSource == SearchSource.MANUAL) {
                    ManualEditor(
                        data = state.data,
                        score = state.score,
                        onSave = { viewModel.saveManualAsset(it) },
                        onAnalyze = { viewModel.saveManualAsset(it) },
                        onDelete = { viewModel.deleteAsset(it.ticker) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
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
    val indicatorStates = remember(data) { mutableStateMapOf<String, String>() }

    fun parse(v: String) = v.replace(",", ".").toDoubleOrNull() ?: 0.0
    fun format(v: Double) = if (v == 0.0) "" else String.format("%.2f", v).replace(".", ",")

    LaunchedEffect(data) {
        if (data is AssetData.Stock) {
            indicatorStates["lpa"] = format(data.lpa); indicatorStates["vpa"] = format(data.vpa)
            indicatorStates["roe"] = format(data.roe * 100); indicatorStates["dy"] = format(data.dividendYield * 100)
            indicatorStates["dy5"] = format(data.dividendYield5Years * 100); indicatorStates["de"] = format(data.debtToEquity)
            indicatorStates["ml"] = format(data.netMargin * 100); indicatorStates["pl"] = format(data.pl)
            indicatorStates["pvp"] = format(data.pvp); indicatorStates["payout"] = format(data.payout * 100)
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
        EditRow("Nome", nameState) { nameState = it }
        EditRow("Preço", priceState, true) { priceState = it }
        EditRow("Segmento", sectorState) { sectorState = it }

        if (data is AssetData.Stock) {
            EditRow("LPA", indicatorStates["lpa"] ?: "", true) { indicatorStates["lpa"] = it }
            EditRow("VPA", indicatorStates["vpa"] ?: "", true) { indicatorStates["vpa"] = it }
            EditRow("ROE (%)", indicatorStates["roe"] ?: "", true) { indicatorStates["roe"] = it }
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }
            EditRow("DY 5a (%)", indicatorStates["dy5"] ?: "", true) { indicatorStates["dy5"] = it }
            EditRow("Payout (%)", indicatorStates["payout"] ?: "", true) { indicatorStates["payout"] = it }
            Spacer(modifier = Modifier.height(8.dp))
            EditRow("Preço Graham", indicatorStates["graham"] ?: "", true) { indicatorStates["graham"] = it }
            EditRow("Preço Bazin", indicatorStates["bazin"] ?: "", true) { indicatorStates["bazin"] = it }
        } else if (data is AssetData.Fii) {
            EditRow("P/VP", indicatorStates["pvp"] ?: "", true) { indicatorStates["pvp"] = it }
            EditRow("Vacância (%)", indicatorStates["vac"] ?: "", true) { indicatorStates["vac"] = it }
            EditRow("DY 12m (%)", indicatorStates["y12"] ?: "", true) { indicatorStates["y12"] = it }
            EditRow("DY 5a (%)", indicatorStates["y5"] ?: "", true) { indicatorStates["y5"] = it }
            EditRow("Qtd Imóveis", indicatorStates["prop"] ?: "", true) { indicatorStates["prop"] = it }
            EditRow("Patrimônio (M)", indicatorStates["aum"] ?: "", true) { indicatorStates["aum"] = it }
            EditRow("Taxa Adm (%)", indicatorStates["mFee"] ?: "", true) { indicatorStates["mFee"] = it }
            EditRow("WALT (anos)", indicatorStates["walt"] ?: "", true) { indicatorStates["walt"] = it }
        } else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", indicatorStates["aFee"] ?: "", true) { indicatorStates["aFee"] = it }
            EditRow("Tracking Error", indicatorStates["te"] ?: "", true) { indicatorStates["te"] = it }
            EditRow("Vol. Diário (M)", indicatorStates["vol"] ?: "", true) { indicatorStates["vol"] = it }
            EditRow("Holdings", indicatorStates["hold"] ?: "", true) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY Atual (%)", indicatorStates["dy"] ?: "", true) { indicatorStates["dy"] = it }
            EditRow("Paridade", indicatorStates["par"] ?: "") { indicatorStates["par"] = it }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Button(onClick = {
                val updated = when (data) {
                    is AssetData.Stock -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState,
                        lpa = parse(indicatorStates["lpa"] ?: "0"), vpa = parse(indicatorStates["vpa"] ?: "0"),
                        roe = parse(indicatorStates["roe"] ?: "0") / 100.0, dividendYield = parse(indicatorStates["dy"] ?: "0") / 100.0,
                        dividendYield5Years = parse(indicatorStates["dy5"] ?: "0") / 100.0, payout = parse(indicatorStates["payout"] ?: "0") / 100.0,
                        grahamPrice = parse(indicatorStates["graham"] ?: "0"), bazinPrice = parse(indicatorStates["bazin"] ?: "0")
                    )
                    is AssetData.Fii -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState,
                        pvp = parse(indicatorStates["pvp"] ?: "0"), vacancy = parse(indicatorStates["vac"] ?: "0") / 100.0,
                        yield12m = parse(indicatorStates["y12"] ?: "0") / 100.0, avgYield5Years = parse(indicatorStates["y5"] ?: "0") / 100.0,
                        propertyCount = parse(indicatorStates["prop"] ?: "0").toInt(), aum = parse(indicatorStates["aum"] ?: "0") * 1_000_000.0,
                        managementFee = parse(indicatorStates["mFee"] ?: "0") / 100.0, weightedLeaseTerm = parse(indicatorStates["walt"] ?: "0")
                    )
                    is AssetData.Etf -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState,
                        adminFee = parse(indicatorStates["aFee"] ?: "0") / 100.0, trackingError = parse(indicatorStates["te"] ?: "0"),
                        avgDailyVolume = parse(indicatorStates["vol"] ?: "0") * 1_000_000.0, numberOfHoldings = parse(indicatorStates["hold"] ?: "0").toInt()
                    )
                    is AssetData.Bdr -> data.copy(
                        name = nameState, currentPrice = parse(priceState), sector = sectorState,
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
fun EditRow(label: String, value: String, isNum: Boolean = false, onValueChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
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
        } else if (data is AssetData.Fii) {
            DetailsRow("P/VP", String.format("%.2f", data.pvp))
            DetailsRow("DY 12m", String.format("%.1f%%", data.yield12m * 100))
            DetailsRow("Vacância", String.format("%.1f%%", data.vacancy * 100), if (data.vacancy <= 0.05) Color(0xFF2E7D32) else Color.Red)
            DetailsRow("Imóveis", data.propertyCount.toString())
        } else if (data is AssetData.Etf) {
            DetailsRow("Taxa Adm", String.format("%.2f%%", data.adminFee * 100), if (data.adminFee <= 0.005) Color(0xFF2E7D32) else Color.Red)
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
