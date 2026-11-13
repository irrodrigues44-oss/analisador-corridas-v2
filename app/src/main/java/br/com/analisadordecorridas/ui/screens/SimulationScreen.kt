package br.com.analisadordecorridas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import br.com.analisadordecorridas.domain.model.RidePlatform

@Composable
fun SimulationScreen(
    hasOverlayPermission: Boolean,
    latestResult: RideAnalysisResult?,
    onRunNumericSimulation: (
        platform: RidePlatform,
        price: Double,
        pickupKm: Double,
        pickupMin: Int,
        tripKm: Double,
        tripMin: Int,
        showOverlay: Boolean
    ) -> Boolean,
    onRunOcrSimulation: (rawText: String, showOverlay: Boolean) -> Boolean,
    onHideOverlay: () -> Unit
) {
    var selectedPlatform by remember { mutableStateOf(RidePlatform.UBER) }
    var priceText by remember { mutableStateOf("9,06") }
    var pickupKmText by remember { mutableStateOf("1,3") }
    var pickupMinText by remember { mutableStateOf("4") }
    var tripKmText by remember { mutableStateOf("3,1") }
    var tripMinText by remember { mutableStateOf("8") }
    var showOverlay by remember { mutableStateOf(hasOverlayPermission) }
    var formError by remember { mutableStateOf<String?>(null) }

    var ocrText by remember {
        mutableStateOf(
            "UberX\nR$ 9,06\n4 min (1,3 km) de distância\n8 min (3,1 km) de viagem"
        )
    }
    var ocrError by remember { mutableStateOf<String?>(null) }

    fun parseBrDouble(raw: String): Double? = raw.trim().replace(",", ".").toDoubleOrNull()

    fun runPreset(
        platform: RidePlatform,
        price: String,
        pKm: String,
        pMin: String,
        tKm: String,
        tMin: String
    ) {
        selectedPlatform = platform
        priceText = price
        pickupKmText = pKm
        pickupMinText = pMin
        tripKmText = tKm
        tripMinText = tMin
        val ok = onRunNumericSimulation(
            platform,
            parseBrDouble(price) ?: 0.0,
            parseBrDouble(pKm) ?: 0.0,
            pMin.toIntOrNull() ?: 0,
            parseBrDouble(tKm) ?: 0.0,
            tMin.toIntOrNull() ?: 0,
            showOverlay
        )
        formError = if (ok) null else "Dados inválidos ou divisores iguais a zero."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Simulador de ofertas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Teste os cálculos financeiros, a classificação combinada e a janela flutuante sem abrir a Uber ou a 99.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Cenários de validação", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            runPreset(RidePlatform.UBER, "9,06", "1,3", "4", "3,1", "8")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Teste obrigatório (R$ 9,06 · 4,4 km · BOA)", maxLines = 1)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runPreset(RidePlatform.NINETY_NINE, "18,00", "2,5", "7", "5,5", "13")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Média (2,5 km)")
                        }
                        OutlinedButton(
                            onClick = {
                                runPreset(RidePlatform.UBER, "18,00", "3,5", "9", "4,5", "11")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Alerta (3,5 km)")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runPreset(RidePlatform.NINETY_NINE, "22,00", "4,2", "11", "4,8", "12")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ruim (> 4 km)")
                        }
                        OutlinedButton(
                            onClick = {
                                runPreset(RidePlatform.UBER, "7,00", "1,0", "3", "4,0", "12")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ruim (R$ 1,40/km)")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPlatform == RidePlatform.UBER,
                            onClick = { selectedPlatform = RidePlatform.UBER },
                            label = { Text("Uber") }
                        )
                        FilterChip(
                            selected = selectedPlatform == RidePlatform.NINETY_NINE,
                            onClick = { selectedPlatform = RidePlatform.NINETY_NINE },
                            label = { Text("99") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Valor oferecido (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pickupKmText,
                            onValueChange = { pickupKmText = it },
                            label = { Text("Até passageiro (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pickupMinText,
                            onValueChange = { pickupMinText = it },
                            label = { Text("Até passageiro (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tripKmText,
                            onValueChange = { tripKmText = it },
                            label = { Text("Corrida (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tripMinText,
                            onValueChange = { tripMinText = it },
                            label = { Text("Corrida (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exibir na janela flutuante", style = MaterialTheme.typography.bodyMedium)
                            if (!hasOverlayPermission) {
                                Text(
                                    text = "Permissão de sobreposição necessária para janela externa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = showOverlay && hasOverlayPermission,
                            onCheckedChange = { showOverlay = it },
                            enabled = hasOverlayPermission
                        )
                    }

                    if (formError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(formError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val price = parseBrDouble(priceText)
                                val pKm = parseBrDouble(pickupKmText)
                                val pMin = pickupMinText.trim().toIntOrNull()
                                val tKm = parseBrDouble(tripKmText)
                                val tMin = tripMinText.trim().toIntOrNull()

                                if (price == null || pKm == null || pMin == null || tKm == null || tMin == null) {
                                    formError = "Preencha todos os valores numéricos corretamente."
                                } else {
                                    val ok = onRunNumericSimulation(selectedPlatform, price, pKm, pMin, tKm, tMin, showOverlay)
                                    formError = if (ok) null else "Não é possível calcular com KM total, tempo total ou consumo iguais a zero."
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Analisar oferta simulada")
                        }
                        if (hasOverlayPermission) {
                            OutlinedButton(onClick = onHideOverlay, shape = RoundedCornerShape(12.dp)) {
                                Text("Ocultar")
                            }
                        }
                    }
                }
            }
        }

        if (latestResult != null) {
            item {
                LatestResultCard(result = latestResult)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Simular leitura OCR (texto bruto)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Valide o parser local colando um texto semelhante ao exibido pelo Google ML Kit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ocrText,
                        onValueChange = { ocrText = it },
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Texto simulado da tela") }
                    )
                    if (ocrError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(ocrError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                ocrText = "99Pop\nR$ 14,80\n3 min · 1,1 km\n11 min · 4,9 km"
                                ocrError = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exemplo 99")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                val ok = onRunOcrSimulation(ocrText, showOverlay)
                                ocrError = if (ok) null else "Não foi possível identificar valor e dois trechos válidos (km e min) no texto."
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Processar texto")
                        }
                    }
                }
            }
        }
    }
}
