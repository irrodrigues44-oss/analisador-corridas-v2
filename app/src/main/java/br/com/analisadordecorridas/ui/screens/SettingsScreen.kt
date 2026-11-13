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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    settings: AnalyzerSettings,
    onSave: (AnalyzerSettings) -> Unit,
    onReset: () -> Unit
) {
    var vehicleModelText by remember { mutableStateOf("") }
    var fuelTypeText by remember { mutableStateOf("") }
    var fuelPriceText by remember { mutableStateOf("") }
    var fuelConsumptionText by remember { mutableStateOf("") }
    var maxBadKmText by remember { mutableStateOf("") }
    var maxMediumKmText by remember { mutableStateOf("") }
    var maxGoodPickupText by remember { mutableStateOf("") }
    var maxMediumPickupText by remember { mutableStateOf("") }
    var maxAlertPickupText by remember { mutableStateOf("") }
    var intervalMs by remember { mutableFloatStateOf(settings.captureIntervalMs.toFloat()) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val ptBr = Locale("pt", "BR")

    LaunchedEffect(settings) {
        vehicleModelText = settings.vehicleModel
        fuelTypeText = settings.fuelType
        fuelPriceText = String.format(ptBr, "%.2f", settings.fuelPricePerLiter)
        fuelConsumptionText = String.format(ptBr, "%.1f", settings.fuelConsumptionKmPerLiter)
        maxBadKmText = String.format(ptBr, "%.2f", settings.maxBadPricePerKm)
        maxMediumKmText = String.format(ptBr, "%.2f", settings.maxMediumPricePerKm)
        maxGoodPickupText = String.format(ptBr, "%.2f", settings.maxGoodPickupDistanceKm)
        maxMediumPickupText = String.format(ptBr, "%.2f", settings.maxMediumPickupDistanceKm)
        maxAlertPickupText = String.format(ptBr, "%.2f", settings.maxAlertPickupDistanceKm)
        intervalMs = settings.captureIntervalMs.toFloat()
    }

    fun parseBrDouble(raw: String): Double? = raw.trim().replace(",", ".").toDoubleOrNull()

    val previewFuelPrice = parseBrDouble(fuelPriceText) ?: settings.fuelPricePerLiter
    val previewConsumption = parseBrDouble(fuelConsumptionText) ?: settings.fuelConsumptionKmPerLiter
    val previewCostPerKm = if (previewConsumption > 0.0) {
        RideCalculator.formatBrl(previewFuelPrice / previewConsumption)
    } else {
        "—"
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
                        text = "Veículo e combustível (local)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Referência inicial: Hyundai HB20 1.6 automático 2017 · Etanol · 7,1 km/l. Você pode ajustar todos os valores abaixo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = vehicleModelText,
                        onValueChange = { vehicleModelText = it },
                        label = { Text("Veículo de referência") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fuelTypeText,
                            onValueChange = { fuelTypeText = it },
                            label = { Text("Combustível") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fuelPriceText,
                            onValueChange = { fuelPriceText = it },
                            label = { Text("Preço/litro (R$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = fuelConsumptionText,
                        onValueChange = { fuelConsumptionText = it },
                        label = { Text("Consumo urbano médio (km/l)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Custo estimado de combustível por km: $previewCostPerKm/km",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Limites de classificação por R$/km (KM total)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ruim: ≤ limite inferior · Média: entre os dois limites · Boa: acima do limite superior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = maxBadKmText,
                            onValueChange = { maxBadKmText = it },
                            label = { Text("Ruim até (R$/km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxMediumKmText,
                            onValueChange = { maxMediumKmText = it },
                            label = { Text("Média até (R$/km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Limites de distância até o passageiro (km)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Boa: até o 1º limite · Média: até o 2º · Média com alerta: até o 3º · Ruim: acima do 3º.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = maxGoodPickupText,
                            onValueChange = { maxGoodPickupText = it },
                            label = { Text("Boa até (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxMediumPickupText,
                            onValueChange = { maxMediumPickupText = it },
                            label = { Text("Média até (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxAlertPickupText,
                            onValueChange = { maxAlertPickupText = it },
                            label = { Text("Alerta até (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Intervalo de leitura da tela: ${intervalMs.roundToLong()} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = intervalMs,
                        onValueChange = { intervalMs = it },
                        valueRange = 800f..3000f,
                        steps = 10
                    )

                    if (feedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = feedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val fuelPrice = parseBrDouble(fuelPriceText)
                                val consumption = parseBrDouble(fuelConsumptionText)
                                val badKm = parseBrDouble(maxBadKmText)
                                val medKm = parseBrDouble(maxMediumKmText)
                                val goodPick = parseBrDouble(maxGoodPickupText)
                                val medPick = parseBrDouble(maxMediumPickupText)
                                val alertPick = parseBrDouble(maxAlertPickupText)

                                if (
                                    fuelPrice == null || consumption == null ||
                                    badKm == null || medKm == null ||
                                    goodPick == null || medPick == null || alertPick == null
                                ) {
                                    feedback = "Informe valores numéricos válidos."
                                    return@Button
                                }
                                if (fuelPrice < 0.0 || consumption <= 0.0) {
                                    feedback = "O preço do combustível não pode ser negativo e o consumo deve ser maior que zero."
                                    return@Button
                                }
                                if (badKm >= medKm) {
                                    feedback = "O limite de R$/km Ruim deve ser menor que o limite de R$/km Média."
                                    return@Button
                                }
                                if (!(goodPick < medPick && medPick < alertPick)) {
                                    feedback = "Os limites de distância devem seguir: Boa < Média < Alerta."
                                    return@Button
                                }

                                onSave(
                                    settings.copy(
                                        vehicleModel = vehicleModelText.trim().ifEmpty { AnalyzerSettings.DEFAULT_VEHICLE_MODEL },
                                        fuelType = fuelTypeText.trim().ifEmpty { AnalyzerSettings.DEFAULT_FUEL_TYPE },
                                        fuelPricePerLiter = fuelPrice,
                                        fuelConsumptionKmPerLiter = consumption,
                                        maxBadPricePerKm = badKm,
                                        maxMediumPricePerKm = medKm,
                                        maxGoodPickupDistanceKm = goodPick,
                                        maxMediumPickupDistanceKm = medPick,
                                        maxAlertPickupDistanceKm = alertPick,
                                        captureIntervalMs = intervalMs.roundToLong()
                                    )
                                )
                                feedback = "Configurações salvas localmente neste celular."
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Salvar ajustes")
                        }
                        OutlinedButton(
                            onClick = {
                                onReset()
                                feedback = "Padrões do HB20 1.6 e limites originais restaurados."
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Padrão")
                        }
                    }
                }
            }
        }
    }
}
