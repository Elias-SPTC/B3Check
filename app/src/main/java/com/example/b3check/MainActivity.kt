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

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
            Text("Analisar Ativo")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is StockUiState.Loading -> CircularProgressIndicator()
            is StockUiState.Success -> {
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
        DetailsRow("Preço Atual", "R$ ${String.format("%.2f", data.currentPrice)}")
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Indicadores Específicos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        when (data) {
            is AssetData.Stock -> {
                DetailsRow("P/VP", String.format("%.2f", data.pvp))
                DetailsRow("P/L", String.format("%.2f", data.pl))
                DetailsRow("ROE", String.format("%.2f%%", data.roe * 100))
                DetailsRow("Div. Yield (DY)", String.format("%.2f%%", data.dividendYield * 100))
                DetailsRow("Margem Líquida", String.format("%.2f%%", data.netMargin * 100))
                DetailsRow("Dívida/Patrimônio", String.format("%.2f", data.debtToEquity))
            }
            is AssetData.Fii -> {
                DetailsRow("P/VP", String.format("%.2f", data.pvp))
                DetailsRow("Div. Yield (DY)", String.format("%.2f%%", data.yield12m * 100))
                DetailsRow("Patrimônio (PL)", "R$ ${String.format("%.2f", data.aum / 1_000_000)}M")
                DetailsRow("Nº de Imóveis", "${data.propertyCount}")
                DetailsRow("Vacância", String.format("%.2f%%", data.vacancy * 100))
                DetailsRow("WALT (Contratos)", String.format("%.2f anos", data.weightedLeaseTerm))
            }
            is AssetData.Etf -> {
                DetailsRow("Taxa Adm", String.format("%.2f%%", data.adminFee * 100))
                DetailsRow("Patrimônio (AUM)", "R$ ${String.format("%.2f", data.aum / 1_000_000)}M")
                DetailsRow("Vol. Diário", "R$ ${String.format("%.2f", data.avgDailyVolume / 1_000_000)}M")
                DetailsRow("Holdings", "${data.numberOfHoldings} ativos")
                DetailsRow("Tracking Error", String.format("%.2f%%", data.trackingError * 100))
            }
        }
    }
}

@Composable
fun DetailsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun FiiResultPreview() {
    B3CheckTheme {
        ScoreResult(
            data = AssetData.Fii(
                ticker = "HGLG11",
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
