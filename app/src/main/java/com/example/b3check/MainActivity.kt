package com.example.b3check

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.b3check.ui.theme.B3CheckTheme

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
                            title = { Text("B3Check - Análise Inteligente") }
                        )
                    }
                ) { innerPadding ->
                    StockAnalysisScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun StockAnalysisScreen(
    modifier: Modifier = Modifier,
    viewModel: StockViewModel = viewModel()
) {
    var tickerInput by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val searchSource by viewModel.searchSource.collectAsState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Menu de Seleção de Fonte de Dados com Cores
        ScrollableTabRow(
            selectedTabIndex = searchSource.ordinal,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            containerColor = Color.Transparent,
            divider = {}
        ) {
            SearchSource.entries.forEach { source ->
                val isSelected = searchSource == source
                val color = when(source) {
                    SearchSource.BRAPI -> Color(0xFF2196F3)
                    SearchSource.FUNDAMENTUS -> Color(0xFF4CAF50)
                    SearchSource.HYBRID -> Color(0xFFFF9800)
                    SearchSource.MANUAL -> Color(0xFF673AB7)
                    SearchSource.MOCK -> Color(0xFF9E9E9E)
                }
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.setSource(source) },
                    text = { 
                        Text(
                            source.label, 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ) 
                    }
                )
            }
        }

        OutlinedTextField(
            value = tickerInput,
            onValueChange = { tickerInput = it.uppercase() },
            label = { Text("Ticker (Ex: BBAS3, HGLG11, IVVB11)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.analyzeTicker(tickerInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = tickerInput.isNotBlank()
        ) {
            Text(if (searchSource == SearchSource.MANUAL) "Buscar/Carregar Ativo" else "Analisar Ativo")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is StockUiState.Loading -> CircularProgressIndicator()
            is StockUiState.Success -> {
                if (searchSource == SearchSource.MANUAL) {
                    ManualEditor(
                        data = state.data,
                        score = state.score,
                        onSave = { updatedData -> viewModel.saveManualAsset(updatedData) },
                        onAnalyze = { viewModel.saveManualAsset(it) }
                    )
                } else {
                    if (state.isMockData) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = "Aviso: Acesso à API Brapi limitado. Exibindo análise baseada em dados históricos/simulados.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
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
    onAnalyze: (AssetData) -> Unit
) {
    var editedData by remember(data) { mutableStateOf(data) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Modo Edição Manual", style = MaterialTheme.typography.titleLarge, color = Color(0xFF673AB7))
        Text("Os dados abaixo foram carregados. Você pode alterá-los antes de salvar ou analisar.", style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Se já tivermos calculado um score (maior que 0), mostramos o resultado no topo
        if (score > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Resultado da Análise Manual", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Nota: ${String.format("%.1f", score)} / 10",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (score >= 6.0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }

        // Campos comuns
        EditRow("Nome", editedData.name) { newValue ->
            editedData = when(val d = editedData) {
                is AssetData.Stock -> d.copy(name = newValue)
                is AssetData.Fii -> d.copy(name = newValue)
                is AssetData.Etf -> d.copy(name = newValue)
            }
        }
        EditRow("Preço Atual", editedData.currentPrice.toString(), isNumber = true) { newValue ->
            val v = newValue.toDoubleOrNull() ?: 0.0
            editedData = when(val d = editedData) {
                is AssetData.Stock -> d.copy(currentPrice = v)
                is AssetData.Fii -> d.copy(currentPrice = v)
                is AssetData.Etf -> d.copy(currentPrice = v)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Indicadores específicos (${if (editedData is AssetData.Stock) "Ação" else if (editedData is AssetData.Fii) "FII" else "ETF"})", fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        when (val d = editedData) {
            is AssetData.Stock -> {
                EditRow("LPA", d.lpa.toString(), true) { editedData = d.copy(lpa = it.toDoubleOrNull() ?: 0.0) }
                EditRow("VPA", d.vpa.toString(), true) { editedData = d.copy(vpa = it.toDoubleOrNull() ?: 0.0) }
                EditRow("ROE (%)", (d.roe * 100).toString(), true) { editedData = d.copy(roe = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("P/VP", d.pvp.toString(), true) { editedData = d.copy(pvp = it.toDoubleOrNull() ?: 0.0) }
                EditRow("P/L", d.pl.toString(), true) { editedData = d.copy(pl = it.toDoubleOrNull() ?: 0.0) }
                EditRow("DY (%)", (d.dividendYield * 100).toString(), true) { editedData = d.copy(dividendYield = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("Margem Líq (%)", (d.netMargin * 100).toString(), true) { editedData = d.copy(netMargin = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("Dívida/Patrimônio", d.debtToEquity.toString(), true) { editedData = d.copy(debtToEquity = it.toDoubleOrNull() ?: 0.0) }
            }
            is AssetData.Fii -> {
                EditRow("P/VP", d.pvp.toString(), true) { editedData = d.copy(pvp = it.toDoubleOrNull() ?: 0.0) }
                EditRow("DY 12m (%)", (d.yield12m * 100).toString(), true) { editedData = d.copy(yield12m = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("Vacância (%)", (d.vacancy * 100).toString(), true) { editedData = d.copy(vacancy = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("WALT (anos)", d.weightedLeaseTerm.toString(), true) { editedData = d.copy(weightedLeaseTerm = it.toDoubleOrNull() ?: 0.0) }
                EditRow("Nº Imóveis", d.propertyCount.toString(), true) { editedData = d.copy(propertyCount = it.toIntOrNull() ?: 0) }
            }
            is AssetData.Etf -> {
                EditRow("Taxa Adm (%)", (d.adminFee * 100).toString(), true) { editedData = d.copy(adminFee = (it.toDoubleOrNull() ?: 0.0) / 100.0) }
                EditRow("Vol. Diário", d.avgDailyVolume.toString(), true) { editedData = d.copy(avgDailyVolume = it.toDoubleOrNull() ?: 0.0) }
                EditRow("Holdings", d.numberOfHoldings.toString(), true) { editedData = d.copy(numberOfHoldings = it.toIntOrNull() ?: 0) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSave(editedData) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Salvar")
            }
            Button(
                onClick = { onAnalyze(editedData) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Analisar")
            }
        }

        if (score > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            ProsConsSection(data.pros, data.cons)
        }
    }
}

@Composable
fun EditRow(label: String, value: String, isNumber: Boolean = false, onValueChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1.5f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumber) KeyboardType.Decimal else KeyboardType.Text
            )
        )
    }
}

@Composable
fun ScoreResult(data: AssetData, score: Double) {
    val (verdict, color) = when {
        score >= 8.5 -> "Excelente - Ativo com indicadores sólidos." to Color(0xFF2E7D32)
        score >= 6.0 -> "Interessante - Bom ativo, mas exige atenção." to Color(0xFFEF6C00)
        else -> "Risco Alto - Indicadores abaixo da média recomendada." to Color(0xFFC62828)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${data.ticker} - ${data.name}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Nota Final: ${String.format("%.1f", score)} / 10",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { (score / 10f).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Veredito:", fontWeight = FontWeight.Bold, color = color)
                Text(text = verdict, style = MaterialTheme.typography.bodyLarge, color = color)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Detalhes da Análise",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        AssetDetails(data)

        Spacer(modifier = Modifier.height(24.dp))

        ProsConsSection(data.pros, data.cons)
    }
}

@Composable
fun ProsConsSection(pros: List<String>, cons: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pontos de Atenção",
            style = MaterialTheme.typography.titleLarge
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Pontos Positivos
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Positivos",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                pros.forEach { point ->
                    Text(
                        text = "• $point",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (pros.isEmpty()) Text("Nenhum destaque detectado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            // Pontos Negativos
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "Negativos",
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                cons.forEach { point ->
                    Text(
                        text = "• $point",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (cons.isEmpty()) Text("Nenhum risco relevante detectado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AssetDetails(data: AssetData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailsRow("Tipo de Ativo", when(data) {
            is AssetData.Stock -> "Ação"
            is AssetData.Fii -> "FII"
            is AssetData.Etf -> "ETF"
        })
        DetailsRow("Setor / Índice", data.sector)
        DetailsRow(
            label = "Preço Atual",
            value = "R$ ${String.format("%.2f", data.currentPrice)}",
            isMocked = data.mockedFields.contains("Preço Atual")
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Indicadores Específicos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        when (data) {
            is AssetData.Stock -> {
                DetailsRow("P/VP", String.format("%.2f", data.pvp), data.mockedFields.contains("P/VP"))
                DetailsRow("P/L", String.format("%.2f", data.pl), data.mockedFields.contains("P/L"))
                DetailsRow("ROE", String.format("%.2f%%", data.roe * 100), data.mockedFields.contains("ROE"))
                DetailsRow("Div. Yield (DY)", String.format("%.2f%%", data.dividendYield * 100), data.mockedFields.contains("Div. Yield (DY)"))
                DetailsRow("Margem Líquida", String.format("%.2f%%", data.netMargin * 100), data.mockedFields.contains("Margem Líquida"))
                DetailsRow("Dívida/Patrimônio", String.format("%.2f", data.debtToEquity), data.mockedFields.contains("Dívida/Patrimônio"))
            }
            is AssetData.Fii -> {
                DetailsRow("P/VP", String.format("%.2f", data.pvp), data.mockedFields.contains("P/VP"))
                DetailsRow("Div. Yield (DY)", String.format("%.2f%%", data.yield12m * 100), data.mockedFields.contains("Div. Yield (DY)"))
                DetailsRow("Patrimônio (PL)", "R$ ${String.format("%.2f", data.aum / 1_000_000)}M", data.mockedFields.contains("Patrimônio (PL)"))
                DetailsRow("Nº de Imóveis", "${data.propertyCount}")
                DetailsRow("Vacância", String.format("%.2f%%", data.vacancy * 100), data.mockedFields.contains("Vacância"))
                DetailsRow("WALT (Contratos)", String.format("%.2f anos", data.weightedLeaseTerm), data.mockedFields.contains("WALT (Contratos)"))
            }
            is AssetData.Etf -> {
                DetailsRow("Taxa Adm", String.format("%.2f%%", data.adminFee * 100), data.mockedFields.contains("Taxa Adm"))
                DetailsRow("Patrimônio (AUM)", "R$ ${String.format("%.2f", data.aum / 1_000_000)}M", data.mockedFields.contains("Patrimônio (AUM)"))
                DetailsRow("Vol. Diário", "R$ ${String.format("%.2f", data.avgDailyVolume / 1_000_000)}M")
                DetailsRow("Holdings", "${data.numberOfHoldings} ativos", data.mockedFields.contains("Holdings"))
                DetailsRow("Tracking Error", String.format("%.2f%%", data.trackingError * 100))
            }
        }
    }
}

@Composable
fun DetailsRow(label: String, value: String, isMocked: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMocked) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "Simulado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = value, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FiiResultPreview() {
    B3CheckTheme {
        ScoreResult(
            data = AssetData.Fii(
                ticker = "HGLG11",
                name = "CSHG Logística",
                currentPrice = 160.0,
                sector = "Logística",
                pvp = 1.02,
                vacancy = 0.03,
                yield12m = 0.09,
                ffoMargin = 0.85,
                multiProperty = true,
                multiTenant = true,
                capRate = 0.08,
                weightedLeaseTerm = 5.0,
                managementFee = 0.01,
                propertyCount = 10,
                aum = 1_500_000_000.0
            ),
            score = 9.0
        )
    }
}
