package br.com.analisadordecorridas.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.analisadordecorridas.AnalisadorApplication
import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import br.com.analisadordecorridas.domain.model.RideOfferInput
import br.com.analisadordecorridas.domain.model.RidePlatform
import br.com.analisadordecorridas.domain.parser.RideOfferParser
import br.com.analisadordecorridas.service.FloatingOverlayService
import br.com.analisadordecorridas.service.ScreenCaptureService
import br.com.analisadordecorridas.ui.state.AnalysisStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionSnapshot(
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as AnalisadorApplication).settingsRepository

    val settings: StateFlow<AnalyzerSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyzerSettings()
    )

    val isCaptureRunning: StateFlow<Boolean> = AnalysisStateHolder.isCaptureRunning
    val isOverlayVisible: StateFlow<Boolean> = AnalysisStateHolder.isOverlayVisible
    val liveResult: StateFlow<RideAnalysisResult?> = AnalysisStateHolder.liveResult
    val simulationResult: StateFlow<RideAnalysisResult?> = AnalysisStateHolder.simulationResult
    val statusMessage: StateFlow<String> = AnalysisStateHolder.statusMessage
    val analyzedOffersCount: StateFlow<Int> = AnalysisStateHolder.analyzedOffersCount

    private val _permissions = MutableStateFlow(readPermissions(application))
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = readPermissions(getApplication())
    }

    private fun readPermissions(context: Context): PermissionSnapshot {
        val overlayGranted = Settings.canDrawOverlays(context)
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionSnapshot(
            hasOverlayPermission = overlayGranted,
            hasNotificationPermission = notificationsGranted
        )
    }

    fun startCaptureService(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        AnalysisStateHolder.clearSimulationResult()
        val serviceIntent = ScreenCaptureService.createStartIntent(context, resultCode, data)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopCaptureService() {
        val context = getApplication<Application>()
        context.startService(ScreenCaptureService.createStopIntent(context))
    }

    fun simulateOffer(
        platform: RidePlatform,
        offeredPriceBrl: Double,
        pickupDistanceKm: Double,
        pickupDurationMinutes: Int,
        tripDistanceKm: Double,
        tripDurationMinutes: Int,
        showOnOverlay: Boolean
    ): RideAnalysisResult? {
        val input = RideOfferInput(
            platform = platform,
            offeredPriceBrl = offeredPriceBrl,
            pickupDistanceKm = pickupDistanceKm,
            pickupDurationMinutes = pickupDurationMinutes,
            tripDistanceKm = tripDistanceKm,
            tripDurationMinutes = tripDurationMinutes
        )
        val result = RideCalculator.analyzeOrNull(
            input = input,
            settings = settings.value,
            isSimulation = true
        ) ?: return null

        AnalysisStateHolder.publishSimulationResult(result)

        if (!isCaptureRunning.value && showOnOverlay && Settings.canDrawOverlays(getApplication())) {
            FloatingOverlayService.show(getApplication())
        }
        return result
    }

    fun simulateFromOcrText(rawOcrText: String, showOnOverlay: Boolean): RideAnalysisResult? {
        val parsed = RideOfferParser.parse(rawOcrText) ?: return null
        val result = RideCalculator.analyzeOrNull(
            input = parsed,
            settings = settings.value,
            isSimulation = true
        ) ?: return null

        AnalysisStateHolder.publishSimulationResult(result)

        if (!isCaptureRunning.value && showOnOverlay && Settings.canDrawOverlays(getApplication())) {
            FloatingOverlayService.show(getApplication())
        }
        return result
    }

    fun hideOverlay() {
        AnalysisStateHolder.clearSimulationResult()
        if (!isCaptureRunning.value) {
            FloatingOverlayService.hide(getApplication())
        }
    }

    fun saveSettings(updated: AnalyzerSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(updated)
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }
}
