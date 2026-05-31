package com.example.b3check

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("B3Check") }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        StockAnalysisScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun StockAnalysisScreen(
    viewModel: StockViewModel = viewModel()
) {
    var tickerInput by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val searchSource by viewModel.searchSource.collectAsState()
    var selectedAssetType by remember { mutableStateOf("Ação") }
    val assetTypes = listOf("Ação", "FII", "ETF", "BDR")

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
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
                val isSelected = searchSource == source
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.setSource(source) },
                    text = { 
                        Text(
                            source.label, 
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ) 
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchSource == SearchSource.MANUAL) {
            Text("Tipo de Ativo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                assetTypes.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedAssetType == type,
                            onClick = { selectedAssetType = type }
                        )
                        Text(type, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = tickerInput,
            onValueChange = { tickerInput = it.uppercase() },
            label = { Text("Ticker") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { 
                if (searchSource == SearchSource.MANUAL) {
                    viewModel.analyzeTicker(tickerInput, selectedAssetType)
                } else {
                    viewModel.analyzeTicker(tickerInput)
                }
            },
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
                    ScoreResult(state.data, state.score)
                } else {
                    ScoreResult(state.data, state.score)
                }
            }
            is StockUiState.Error -> {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }
            StockUiState.Idle -> {
                Text("Digite um ticker para começar", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ManualEditor(
    data: AssetData,
    score: Double,
    onSave: (AssetData) -> Unit,
    onAnalyze: (AssetData) -> Unit,
    onDelete: (AssetData) -> Unit
) {
    var nameState by remember(data) { mutableStateOf(data.name) }
    var priceState by remember(data) { mutableStateOf(data.currentPrice.toString()) }
    var sectorState by remember(data) { mutableStateOf(if (data.sector == "FII" || data.sector == "Ação") "" else data.sector) }
    
    // IMPORTANTE: O indicatorStates precisa ser resetado quando o 'data' muda (ex: após busca na internet)
    val indicatorStates = remember(data) { mutableStateMapOf<String, String>() }

    fun parse(v: String) = v.replace(",", ".").toDoubleOrNull() ?: 0.0
    fun format(v: Double) = if (v == 0.0) "" else String.format("%.2f", v).replace(".", ",")

    // Inicializa ou atualiza os estados quando o dado muda
    LaunchedEffect(data) {
        if (data is AssetData.Stock) {
            indicatorStates["lpa"] = format(data.lpa)
            indicatorStates["vpa"] = format(data.vpa)
            indicatorStates["roe"] = format(data.roe * 100)
            indicatorStates["dy"] = format(data.dividendYield * 100)
            indicatorStates["dy5"] = format(data.dividendYield5Years * 100)
            indicatorStates["de"] = format(data.debtToEquity)
            indicatorStates["ml"] = format(data.netMargin * 100)
            indicatorStates["pl"] = format(data.pl)
            indicatorStates["pvp"] = format(data.pvp)
            indicatorStates["payout"] = format(data.payout * 100)
            indicatorStates["basel"] = format(data.baselIndex)
        } else if (data is AssetData.Fii) {
            indicatorStates["fType"] = data.fundType
            indicatorStates["mType"] = data.managementType
            indicatorStates["dy12"] = format(data.yield12m * 100)
            indicatorStates["dy5"] = format(data.avgYield5Years * 100)
            indicatorStates["pvp"] = format(data.pvp)
            indicatorStates["vac"] = format(data.vacancy * 100)
            indicatorStates["aum"] = format(data.aum / 1_000_000.0)
            indicatorStates["prop"] = data.propertyCount.toString()
            indicatorStates["mFee"] = format(data.managementFee * 100)
            indicatorStates["walt"] = format(data.weightedLeaseTerm)
        }
    }

    fun getInd(key: String, def: String) = indicatorStates.get(key) ?: def

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Edição Manual", style = MaterialTheme.typography.titleMedium, color = Color(0xFF673AB7))
        
        if (score > 0) {
            Text("Nota Atual: ${String.format("%.1f", score)}", fontWeight = FontWeight.Bold, color = if(score >= 6) Color(0xFF2E7D32) else Color.Red)
        }

        Spacer(modifier = Modifier.height(8.dp))

        EditRow("Nome", nameState) { nameState = it }
        EditRow("Preço", priceState, true) { priceState = it }
        EditRow("Segmento", sectorState) { sectorState = it }

        if (data is AssetData.Stock) {
            EditRow("LPA", getInd("lpa", format(data.lpa)), true) { indicatorStates["lpa"] = it }
            EditRow("VPA", getInd("vpa", format(data.vpa)), true) { indicatorStates["vpa"] = it }
            EditRow("ROE (%)", getInd("roe", format(data.roe * 100)), true) { indicatorStates["roe"] = it }
            EditRow("DY Atual (%)", getInd("dy", format(data.dividendYield * 100)), true) { indicatorStates["dy"] = it }
            EditRow("DY 5a (%)", getInd("dy5", format(data.dividendYield5Years * 100)), true) { 
                indicatorStates["dy5"] = it 
            }
            EditRow("Dív/Patrim", getInd("de", format(data.debtToEquity)), true) { indicatorStates["de"] = it }
            EditRow("Margem Líq (%)", getInd("ml", format(data.netMargin * 100)), true) { indicatorStates["ml"] = it }
            EditRow("P/L", getInd("pl", format(data.pl)), true) { indicatorStates["pl"] = it }
            EditRow("P/VP", getInd("pvp", format(data.pvp)), true) { indicatorStates["pvp"] = it }
            EditRow("Payout (%)", getInd("payout", format(data.payout * 100)), true) { indicatorStates["payout"] = it }
            if (data.sector == "Bancário") {
                EditRow("Índ. Basileia", getInd("basel", data.baselIndex.toString()), true) { indicatorStates["basel"] = it }
            }
        } else if (data is AssetData.Fii) {
            EditRow("Tipo Fundo", getInd("fType", data.fundType)) { indicatorStates["fType"] = it }
            EditRow("Tipo Gestão", getInd("mType", data.managementType)) { indicatorStates["mType"] = it }
            EditRow("DY 12m (%)", getInd("dy12", (data.yield12m * 100).toString()), true) { indicatorStates["dy12"] = it }
            EditRow("DY 5a (%)", getInd("dy5", (data.avgYield5Years * 100).toString()), true) { indicatorStates["dy5"] = it }
            EditRow("P/VP", getInd("pvp", data.pvp.toString()), true) { indicatorStates["pvp"] = it }
            EditRow("Vacância (%)", getInd("vac", (data.vacancy * 100).toString()), true) { indicatorStates["vac"] = it }
            EditRow("Patrim (M)", getInd("aum", (data.aum / 1_000_000.0).toString()), true) { indicatorStates["aum"] = it }
            EditRow("Qtd Imóveis", getInd("prop", data.propertyCount.toString()), true) { indicatorStates["prop"] = it }
            EditRow("Taxa Adm (%)", getInd("mFee", (data.managementFee * 100).toString()), true) { indicatorStates["mFee"] = it }
            EditRow("WALT (anos)", getInd("walt", data.weightedLeaseTerm.toString()), true) { indicatorStates["walt"] = it }
        } else if (data is AssetData.Etf) {
            EditRow("Taxa Adm (%)", getInd("aFee", (data.adminFee * 100).toString()), true) { indicatorStates["aFee"] = it }
            EditRow("Tracking Error", getInd("te", data.trackingError.toString()), true) { indicatorStates["te"] = it }
            EditRow("Volume Diário", getInd("vol", data.avgDailyVolume.toString()), true) { indicatorStates["vol"] = it }
            EditRow("Holdings", getInd("hold", data.numberOfHoldings.toString()), true) { indicatorStates["hold"] = it }
        } else if (data is AssetData.Bdr) {
            EditRow("DY (%)", getInd("dy", (data.dividendYield * 100).toString()), true) { indicatorStates["dy"] = it }
            EditRow("Paridade", getInd("par", data.parity)) { indicatorStates["par"] = it }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val updated = when (data) {
                        is AssetData.Stock -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            lpa = parse(getInd("lpa", "0")),
                            vpa = parse(getInd("vpa", "0")),
                            roe = parse(getInd("roe", "0")) / 100.0,
                            dividendYield = parse(getInd("dy", "0")) / 100.0,
                            dividendYield5Years = parse(getInd("dy5", "0")) / 100.0,
                            debtToEquity = parse(getInd("de", "0")),
                            netMargin = parse(getInd("ml", "0")) / 100.0,
                            pl = parse(getInd("pl", "0")),
                            pvp = parse(getInd("pvp", "0")),
                            payout = parse(getInd("payout", "0")) / 100.0,
                            baselIndex = parse(getInd("basel", "0"))
                        )
                        is AssetData.Fii -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            fundType = getInd("fType", ""),
                            managementType = getInd("mType", ""),
                            yield12m = parse(getInd("dy12", "0")) / 100.0,
                            avgYield5Years = parse(getInd("dy5", "0")) / 100.0,
                            pvp = parse(getInd("pvp", "0")),
                            vacancy = parse(getInd("vac", "0")) / 100.0,
                            aum = parse(getInd("aum", "0")) * 1_000_000.0,
                            propertyCount = parse(getInd("prop", "0")).toInt(),
                            managementFee = parse(getInd("mFee", "0")) / 100.0,
                            weightedLeaseTerm = parse(getInd("walt", "0"))
                        )
                        is AssetData.Etf -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            adminFee = parse(getInd("aFee", "0")) / 100.0,
                            trackingError = parse(getInd("te", "0")),
                            avgDailyVolume = parse(getInd("vol", "0")),
                            numberOfHoldings = parse(getInd("hold", "0")).toInt()
                        )
                        is AssetData.Bdr -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            dividendYield = parse(getInd("dy", "0")) / 100.0,
                            parity = getInd("par", "1:1")
                        )
                    }
                    onSave(updated)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Salvar") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val updated = when (data) {
                        is AssetData.Stock -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            lpa = parse(getInd("lpa", "0")),
                            vpa = parse(getInd("vpa", "0")),
                            roe = parse(getInd("roe", "0")) / 100.0,
                            dividendYield = parse(getInd("dy", "0")) / 100.0,
                            dividendYield5Years = parse(getInd("dy5", "0")) / 100.0,
                            debtToEquity = parse(getInd("de", "0")),
                            netMargin = parse(getInd("ml", "0")) / 100.0,
                            pl = parse(getInd("pl", "0")),
                            pvp = parse(getInd("pvp", "0")),
                            payout = parse(getInd("payout", "0")) / 100.0,
                            baselIndex = parse(getInd("basel", "0"))
                        )
                        is AssetData.Fii -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            fundType = getInd("fType", ""),
                            managementType = getInd("mType", ""),
                            yield12m = parse(getInd("dy12", "0")) / 100.0,
                            avgYield5Years = parse(getInd("dy5", "0")) / 100.0,
                            pvp = parse(getInd("pvp", "0")),
                            vacancy = parse(getInd("vac", "0")) / 100.0,
                            aum = parse(getInd("aum", "0")) * 1_000_000.0,
                            propertyCount = parse(getInd("prop", "0")).toInt(),
                            managementFee = parse(getInd("mFee", "0")) / 100.0,
                            weightedLeaseTerm = parse(getInd("walt", "0"))
                        )
                        is AssetData.Etf -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            adminFee = parse(getInd("aFee", "0")) / 100.0,
                            trackingError = parse(getInd("te", "0")),
                            avgDailyVolume = parse(getInd("vol", "0")),
                            numberOfHoldings = parse(getInd("hold", "0")).toInt()
                        )
                        is AssetData.Bdr -> data.copy(
                            name = nameState,
                            currentPrice = parse(priceState),
                            sector = sectorState,
                            dividendYield = parse(getInd("dy", "0")) / 100.0,
                            parity = getInd("par", "1:1")
                        )
                    }
                    onAnalyze(updated)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Analisar") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val cleaned = when (data) {
                    is AssetData.Stock -> data.copy(
                        dividendYield5Years = if (data.dividendYield5Years == 0.0 && data.dividendYield > 0.0) data.dividendYield else data.dividendYield5Years
                    )
                    else -> data
                }
                onSave(cleaned)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF673AB7))
        ) {
            Text("Corrigir Dados (Fallback)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onDelete(data) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Text("Deletar do Banco Manual")
        }
    }
}

@Composable
fun EditRow(label: String, value: String, isNum: Boolean = false, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1.8f).height(32.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFFFFD54F)), // Amarelo claro (Amber 300)
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if(isNum) KeyboardType.Decimal else KeyboardType.Text),
            decorationBox = { innerTextField ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
fun ScoreResult(data: AssetData, score: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("${data.ticker} - ${data.name}", fontWeight = FontWeight.Bold)
        Text("Nota: ${String.format("%.1f", score)} / 10", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        AssetDetails(data)
        Spacer(modifier = Modifier.height(16.dp))
        ProsConsSection(data.pros, data.cons)
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column {
        DetailsRow("Preço Atual", "R$ ${String.format("%.2f", data.currentPrice)}")
        
        if (data is AssetData.Stock) {
            val grahamPrice = if (data.lpa > 0 && data.vpa > 0) {
                sqrt(22.5 * data.lpa * data.vpa)
            } else 0.0
            
            val bazinPrice: Double
            val bazinMethod: String
            
            if (data.dividendYield5Years > 0) {
                bazinPrice = (data.dividendYield5Years * data.currentPrice) / 0.06
                bazinMethod = "(Base: Yield 5a %)"
            } else if (data.lpa > 0 && data.payout > 0) {
                bazinPrice = (data.lpa * data.payout) / 0.06
                bazinMethod = "(Base: LPA/Payout)"
            } else if (data.dividendYield > 0) {
                bazinPrice = (data.dividendYield * data.currentPrice) / 0.06
                bazinMethod = "(Base: DY Atual)"
            } else {
                bazinPrice = 0.0
                bazinMethod = ""
            }

            if (grahamPrice > 0) {
                DetailsRow("Preço Justo (Graham)", "R$ ${String.format("%.2f", grahamPrice)}", 
                    valueColor = if (data.currentPrice <= grahamPrice) Color(0xFF2E7D32) else Color.Red)
            }
            if (bazinPrice > 0) {
                DetailsRow("Preço Teto (Bazin) $bazinMethod", "R$ ${String.format("%.2f", bazinPrice)}",
                    valueColor = if (data.currentPrice <= bazinPrice) Color(0xFF2E7D32) else Color.Red)
            }

            Spacer(modifier = Modifier.height(8.dp))
            DetailsRow("P/VP", String.format("%.2f", data.pvp))
            DetailsRow("P/L", String.format("%.2f", data.pl))
            DetailsRow("ROE", String.format("%.1f%%", data.roe * 100))
        } else if (data is AssetData.Fii) {
            DetailsRow("Tipo Fundo", data.fundType)
            DetailsRow("Gestão", data.managementType)
            DetailsRow("P/VP", String.format("%.2f", data.pvp))
            DetailsRow("DY 12m", String.format("%.1f%%", data.yield12m * 100))
        } else if (data is AssetData.Etf) {
            DetailsRow("Taxa Adm", String.format("%.2f%%", data.adminFee * 100))
            DetailsRow("Holdings", data.numberOfHoldings.toString())
        } else if (data is AssetData.Bdr) {
            DetailsRow("Paridade", data.parity)
            DetailsRow("DY", String.format("%.1f%%", data.dividendYield * 100))
        }
    }
}

@Composable
fun DetailsRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun ProsConsSection(pros: List<String>, cons: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Prós", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            pros.forEach { Text("• $it", fontSize = 11.sp) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Contras", color = Color.Red, fontWeight = FontWeight.Bold)
            cons.forEach { Text("• $it", fontSize = 11.sp) }
        }
    }
}
