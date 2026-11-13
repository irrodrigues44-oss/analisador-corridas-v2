package br.com.analisadordecorridas.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import br.com.analisadordecorridas.ui.MainViewModel
import br.com.analisadordecorridas.ui.screens.HomeScreen
import br.com.analisadordecorridas.ui.screens.SettingsScreen
import br.com.analisadordecorridas.ui.screens.SimulationScreen

private enum class AppTab(val title: String) {
    HOME("Início"),
    SIMULATION("Simulação"),
    SETTINGS("Metas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisadorApp(
    viewModel: MainViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartCapture: () -> Unit
) {
    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    val permissions by viewModel.permissions.collectAsState()
    val isCaptureRunning by viewModel.isCaptureRunning.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val analyzedOffersCount by viewModel.analyzedOffersCount.collectAsState()
    val liveResult by viewModel.liveResult.collectAsState()
    val simulationResult by viewModel.simulationResult.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analisador de Corridas",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.HOME,
                    onClick = { currentTab = AppTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(AppTab.HOME.title) }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SIMULATION,
                    onClick = { currentTab = AppTab.SIMULATION },
                    icon = { Icon(Icons.Default.Science, contentDescription = null) },
                    label = { Text(AppTab.SIMULATION.title) }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text(AppTab.SETTINGS.title) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    permissions = permissions,
                    isCaptureRunning = isCaptureRunning,
                    statusMessage = statusMessage,
                    analyzedOffersCount = analyzedOffersCount,
                    latestResult = liveResult,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onStartCapture = onStartCapture,
                    onStopCapture = { viewModel.stopCaptureService() },
                    onOpenSimulation = { currentTab = AppTab.SIMULATION }
                )
                AppTab.SIMULATION -> SimulationScreen(
                    hasOverlayPermission = permissions.hasOverlayPermission,
                    latestResult = simulationResult,
                    onRunNumericSimulation = { platform, price, pKm, pMin, tKm, tMin, showOverlay ->
                        viewModel.simulateOffer(platform, price, pKm, pMin, tKm, tMin, showOverlay) != null
                    },
                    onRunOcrSimulation = { rawText, showOverlay ->
                        viewModel.simulateFromOcrText(rawText, showOverlay) != null
                    },
                    onHideOverlay = { viewModel.hideOverlay() }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSave = { viewModel.saveSettings(it) },
                    onReset = { viewModel.resetSettings() }
                )
            }
        }
    }
}
