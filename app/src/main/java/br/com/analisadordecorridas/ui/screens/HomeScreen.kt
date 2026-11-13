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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.OfferClassification
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import br.com.analisadordecorridas.ui.PermissionSnapshot

@Composable
fun HomeScreen(
    permissions: PermissionSnapshot,
    isCaptureRunning: Boolean,
    statusMessage: String,
    analyzedOffersCount: Int,
    latestResult: RideAnalysisResult?,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenSimulation: () -> Unit
) {
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
                        text = "Captura automática da tela",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (analyzedOffersCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ofertas analisadas nesta sessão: $analyzedOffersCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { if (isCaptureRunning) onStopCapture() else onStartCapture() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCaptureRunning) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isCaptureRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCaptureRunning) "Parar análise" else "Iniciar análise automática",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenSimulation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Testar na tela de simulação")
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
                        text = "Permissões necessárias",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "O aplicativo solicita somente o essencial para ler as ofertas na tela e mostrar a janela flutuante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Janela flutuante sobre outros apps",
                        description = "Exibe a classificação sobre a Uber e a 99 sem bloquear toques. Em APKs instalados manualmente (Android 13+), habilite \"Permitir configurações restritas\" nos detalhes do app se necessário.",
                        granted = permissions.hasOverlayPermission,
                        actionLabel = "Autorizar",
                        onAction = onRequestOverlayPermission
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    PermissionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notificação do serviço em segundo plano",
                        description = "Mantém a captura ativa no Android 14/15/16 e permite parar a análise pela barra.",
                        granted = permissions.hasNotificationPermission,
                        actionLabel = "Permitir",
                        onAction = onRequestNotificationPermission
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    PermissionRow(
                        icon = Icons.Default.ScreenShare,
                        title = "Captura de tela (MediaProjection)",
                        description = "Solicitada pelo Android a cada início. No Android 14+, selecione \"Tela inteira\" para capturar Uber e 99.",
                        granted = isCaptureRunning,
                        grantedLabel = "Ativa agora",
                        pendingLabel = "Solicitada ao iniciar",
                        actionLabel = "Iniciar",
                        onAction = onStartCapture
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacidade e segurança",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Todo o OCR (Google ML Kit) roda localmente no aparelho.\n" +
                                "• Nenhuma captura de tela é salva na galeria ou memória.\n" +
                                "• Nenhum dado pessoal de passageiro é armazenado.\n" +
                                "• O app nunca aceita nem recusa corridas automaticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String = "Concedida",
    pendingLabel: String = "Pendente",
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (granted) grantedLabel else pendingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) Color(0xFF16A34A) else Color(0xFFD97706),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (!granted) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LatestResultCard(result: RideAnalysisResult) {
    val (statusColor, statusIcon) = when (result.classification) {
        OfferClassification.GOOD -> Color(0xFF16A34A) to Icons.Default.CheckCircle
        OfferClassification.MEDIUM -> Color(0xFFCA8A04) to Icons.Default.Info
        OfferClassification.MEDIUM_WITH_ALERT -> Color(0xFFEA580C) to Icons.Default.Warning
        OfferClassification.BAD -> Color(0xFFDC2626) to Icons.Default.Cancel
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Última análise · ${result.platform.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = result.classification.label,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = result.classification.label,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = result.justification,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("R$/km total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = RideCalculator.formatBrl(result.pricePerKm),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Column {
                    Text("Após combustível", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = RideCalculator.formatBrl(result.afterFuelBrl),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Oferta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = RideCalculator.formatBrl(result.offeredPriceBrl),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "KM total: ${RideCalculator.formatKm(result.totalDistanceKm)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Até pass.: ${RideCalculator.formatKm(result.pickupDistanceKm)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tempo: ${RideCalculator.formatMinutes(result.totalDurationMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Combustível est.: ${RideCalculator.formatBrl(result.estimatedFuelCostBrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "R$/hora: ${RideCalculator.formatBrl(result.pricePerHour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Após combustível considera apenas o gasto estimado de combustível (não inclui manutenção, pneus, depreciação ou seguro).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
