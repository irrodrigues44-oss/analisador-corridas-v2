package br.com.analisadordecorridas.ui.state

import android.graphics.Rect
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AnalysisStateHolder {
    private val _isCaptureRunning = MutableStateFlow(false)
    val isCaptureRunning: StateFlow<Boolean> = _isCaptureRunning.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    // Oferta real capturada via MediaProjection + ML Kit (nunca misturada com simulação)
    private val _liveResult = MutableStateFlow<RideAnalysisResult?>(null)
    val liveResult: StateFlow<RideAnalysisResult?> = _liveResult.asStateFlow()

    // Resultado exclusivo da tela de simulação
    private val _simulationResult = MutableStateFlow<RideAnalysisResult?>(null)
    val simulationResult: StateFlow<RideAnalysisResult?> = _simulationResult.asStateFlow()

    // Retângulo ocupado pela própria janela flutuante na tela (para exclusão no OCR)
    private val _overlayBoundsInScreen = MutableStateFlow<Rect?>(null)
    val overlayBoundsInScreen: StateFlow<Rect?> = _overlayBoundsInScreen.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pronto para iniciar a análise.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _analyzedOffersCount = MutableStateFlow(0)
    val analyzedOffersCount: StateFlow<Int> = _analyzedOffersCount.asStateFlow()

    fun setCaptureRunning(running: Boolean) {
        _isCaptureRunning.value = running
        // Ao iniciar ou parar a captura real, limpa qualquer oferta antiga ou simulada
        _liveResult.value = null
        _overlayBoundsInScreen.value = null
        if (running) {
            _simulationResult.value = null
            _analyzedOffersCount.value = 0
            _statusMessage.value = "Captura ativa. Aguardando oferta na tela (Uber ou 99)."
        } else {
            _statusMessage.value = "Análise interrompida."
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
        if (!visible) {
            _overlayBoundsInScreen.value = null
        }
    }

    fun updateOverlayBounds(bounds: Rect?) {
        _overlayBoundsInScreen.value = bounds
    }

    fun publishLiveResult(result: RideAnalysisResult) {
        if (result.isSimulation) return
        _liveResult.value = result
        _analyzedOffersCount.update { it + 1 }
        _statusMessage.value = "Oferta detectada (${result.platform.displayName})."
    }

    fun clearLiveResult(reason: String = "Captura ativa. Aguardando oferta na tela (Uber ou 99).") {
        if (_liveResult.value != null) {
            _liveResult.value = null
            _overlayBoundsInScreen.value = null
            if (_isCaptureRunning.value) {
                _statusMessage.value = reason
            }
        }
    }

    fun publishSimulationResult(result: RideAnalysisResult) {
        if (!result.isSimulation) return
        _simulationResult.value = result
        _statusMessage.value = "Simulação analisada (${result.platform.displayName})."
    }

    fun clearSimulationResult() {
        _simulationResult.value = null
    }

    fun setStatusMessage(message: String) {
        _statusMessage.value = message
    }
}
