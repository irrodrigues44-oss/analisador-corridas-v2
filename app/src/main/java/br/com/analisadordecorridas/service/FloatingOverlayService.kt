package br.com.analisadordecorridas.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.analisadordecorridas.AnalisadorApplication
import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.model.OfferClassification
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import br.com.analisadordecorridas.ui.state.AnalysisStateHolder
import br.com.analisadordecorridas.ui.theme.AnalisadorDeCorridasTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FloatingOverlayService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var visibilityCollectorJob: Job? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeOverlay()
                stopSelf()
            }
            ACTION_SHOW -> {
                if (Settings.canDrawOverlays(this)) {
                    showOverlay()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun readStatusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 72
    }

    private fun publishOverlayScreenBounds(view: ComposeView) {
        if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) {
            AnalysisStateHolder.updateOverlayBounds(null)
            return
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        AnalysisStateHolder.updateOverlayBounds(
            Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )
        )
    }

    private fun showOverlay() {
        if (overlayView != null) return

        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        // FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE garantem que nenhum toque seja interceptado,
        // deixando livres os botões verdadeiros de aceitar/recusar da Uber e da 99.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = readStatusBarHeightPx() + 16
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val app = application as AnalisadorApplication
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                publishOverlayScreenBounds(this)
            }

            setContent {
                val isCaptureRunning by AnalysisStateHolder.isCaptureRunning.collectAsState()
                val liveResult by AnalysisStateHolder.liveResult.collectAsState()
                val simulationResult by AnalysisStateHolder.simulationResult.collectAsState()
                val settings by app.settingsRepository.settingsFlow.collectAsState(
                    initial = AnalyzerSettings()
                )

                // Durante a captura real, nunca exibe resultados simulados nem ofertas antigas removidas.
                val displayedResult = if (isCaptureRunning) liveResult else simulationResult

                if (displayedResult != null) {
                    key(
                        displayedResult.timestampMillis,
                        displayedResult.platform,
                        displayedResult.offeredPriceBrl
                    ) {
                        AnalisadorDeCorridasTheme(darkTheme = true) {
                            SimplifiedFloatingOverlayCard(
                                result = displayedResult,
                                opacity = settings.overlayOpacity
                            )
                        }
                    }
                }
            }
        }

        composeView.visibility = View.GONE
        overlayView = composeView
        windowManager?.addView(composeView, params)
        AnalysisStateHolder.setOverlayVisible(true)

        visibilityCollectorJob?.cancel()
        visibilityCollectorJob = lifecycleScope.launch {
            combine(
                AnalysisStateHolder.isCaptureRunning,
                AnalysisStateHolder.liveResult,
                AnalysisStateHolder.simulationResult
            ) { isRunning, live, sim ->
                if (isRunning) live else sim
            }.collectLatest { displayed ->
                val view = overlayView ?: return@collectLatest
                if (displayed == null) {
                    if (view.visibility != View.GONE) {
                        view.visibility = View.GONE
                    }
                    AnalysisStateHolder.updateOverlayBounds(null)
                } else {
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                    }
                    view.post {
                        if (overlayView === view && view.visibility == View.VISIBLE) {
                            publishOverlayScreenBounds(view)
                        }
                    }
                }
            }
        }
    }

    private fun removeOverlay() {
        visibilityCollectorJob?.cancel()
        visibilityCollectorJob = null
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        AnalysisStateHolder.setOverlayVisible(false)
        AnalysisStateHolder.updateOverlayBounds(null)
    }

    override fun onDestroy() {
        removeOverlay()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "br.com.analisadordecorridas.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "br.com.analisadordecorridas.action.HIDE_OVERLAY"

        fun show(context: Context) {
            context.startService(
                Intent(context, FloatingOverlayService::class.java).apply { action = ACTION_SHOW }
            )
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, FloatingOverlayService::class.java).apply { action = ACTION_HIDE }
            )
        }
    }
}

private data class OverlayPalette(
    val background: Color,
    val content: Color
)

private fun overlayPalette(classification: OfferClassification): OverlayPalette {
    return when (classification) {
        OfferClassification.GOOD -> OverlayPalette(
            background = Color(0xFF15803D), // Verde
            content = Color.White
        )
        OfferClassification.MEDIUM -> OverlayPalette(
            background = Color(0xFFFACC15), // Amarelo
            content = Color(0xFF0F172A)
        )
        OfferClassification.MEDIUM_WITH_ALERT -> OverlayPalette(
            background = Color(0xFFF97316), // Laranja
            content = Color(0xFF0F172A)
        )
        OfferClassification.BAD -> OverlayPalette(
            background = Color(0xFFDC2626), // Vermelho
            content = Color.White
        )
    }
}

@Composable
private fun SimplifiedFloatingOverlayCard(
    result: RideAnalysisResult,
    opacity: Float
) {
    val palette = overlayPalette(result.classification)

    Surface(
        modifier = Modifier
            .width(264.dp)
            .alpha(opacity.coerceIn(0.85f, 1f)),
        shape = RoundedCornerShape(16.dp),
        color = palette.background,
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.platform.displayName,
                    color = palette.content,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = RideCalculator.formatBrl(result.offeredPriceBrl),
                    color = palette.content,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.classification == OfferClassification.MEDIUM_WITH_ALERT) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Atenção",
                        tint = palette.content,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = result.classification.label,
                    color = palette.content,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${RideCalculator.formatBrl(result.pricePerKm)}/km",
                    color = palette.content,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Até pass.: ${RideCalculator.formatKm(result.pickupDistanceKm)}",
                    color = palette.content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
