package br.com.analisadordecorridas.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import br.com.analisadordecorridas.AnalisadorApplication
import br.com.analisadordecorridas.MainActivity
import br.com.analisadordecorridas.R
import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.parser.RideOfferParser
import br.com.analisadordecorridas.ocr.MlKitTextRecognizer
import br.com.analisadordecorridas.ui.state.AnalysisStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class ScreenCaptureService : LifecycleService() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var screenWidthPx = 1080
    private var screenHeightPx = 2400
    private var captureWidthPx = 702
    private var captureHeightPx = 1560

    private val ocrRecognizer: MlKitTextRecognizer
        get() = (application as AnalisadorApplication).ocrRecognizer

    private val isCaptureActive = AtomicBoolean(false)
    private val isProcessingFrame = AtomicBoolean(false)
    private val captureEpoch = AtomicLong(0L)

    private val pendingImageLock = Any()
    private var pendingImage: Image? = null

    private val offerStateLock = Any()
    private var lastOfferSignature: String? = null
    private var consecutiveMisses = 0

    @Volatile
    private var lastCaptureElapsedMs = 0L

    @Volatile
    private var isContentVisible = true

    @Volatile
    private var currentSettings = AnalyzerSettings()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val maskPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val processPendingFrameRunnable = Runnable {
        processNextAvailableOrPendingFrame()
    }

    private val staticClearVerificationRunnable = Runnable {
        val epoch = captureEpoch.get()
        if (!isCaptureActive.get()) return@Runnable
        synchronized(offerStateLock) {
            if (epoch == captureEpoch.get() && lastOfferSignature != null && consecutiveMisses > 0) {
                lastOfferSignature = null
                consecutiveMisses = 0
                AnalysisStateHolder.clearLiveResult()
            }
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCaptureAndSelf()
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            isContentVisible = isVisible
            if (!isVisible) {
                captureEpoch.incrementAndGet()
                cancelStaticClearTimer()
                clearPendingImage()
                synchronized(offerStateLock) {
                    lastOfferSignature = null
                    consecutiveMisses = 0
                }
                AnalysisStateHolder.clearLiveResult(
                    "Conteúdo capturado oculto. Aguardando retorno da Uber ou 99."
                )
            } else {
                // Ao voltar a ficar visível, processa imediatamente o próximo frame sem aguardar cooldown
                lastCaptureElapsedMs = 0L
                schedulePendingFrameCheck(40L)
            }
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            screenWidthPx = width
            screenHeightPx = height
            val (newCaptureW, newCaptureH) = computeCaptureSize(width, height)
            if (newCaptureW != captureWidthPx || newCaptureH != captureHeightPx) {
                recreateVirtualDisplaySurface(newCaptureW, newCaptureH)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as AnalisadorApplication
        lifecycleScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { settings ->
                currentSettings = settings
            }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            ocrRecognizer.warmUp()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopCaptureAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    AnalysisStateHolder.setStatusMessage("Autorização de captura de tela não concedida.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundWithProjectionType()
                startProjection(resultCode, resultData)
                startOverlayService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithProjectionType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun readDisplayDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().coerceAtLeast(720) to bounds.height().coerceAtLeast(1280)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels.coerceAtLeast(720) to metrics.heightPixels.coerceAtLeast(1280)
        }
    }

    private fun computeCaptureSize(width: Int, height: Int): Pair<Int, Int> {
        val safeW = width.coerceAtLeast(360)
        val safeH = height.coerceAtLeast(640)
        val minTargetW = 540.coerceAtMost(safeW)
        val maxTargetW = 900.coerceAtMost(safeW)
        val targetW = (safeW * 0.65f).roundToInt().coerceIn(minTargetW, maxTargetW)
        val targetH = ((targetW.toFloat() / safeW.toFloat()) * safeH).roundToInt().coerceAtLeast(1)
        return targetW to targetH
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        cleanupProjection()

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            AnalysisStateHolder.setStatusMessage("Não foi possível iniciar o MediaProjection.")
            stopCaptureAndSelf()
            return
        }

        // Limpa qualquer oferta ou simulação anterior antes de iniciar nova sessão real
        AnalysisStateHolder.setCaptureRunning(true)

        captureEpoch.incrementAndGet()
        isCaptureActive.set(true)
        isProcessingFrame.set(false)
        lastCaptureElapsedMs = 0L
        isContentVisible = true

        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        val (width, height) = readDisplayDimensions()
        screenWidthPx = width
        screenHeightPx = height
        val (capW, capH) = computeCaptureSize(width, height)
        captureWidthPx = capW
        captureHeightPx = capH

        val thread = HandlerThread("AnalisadorCaptureThread").apply { start() }
        captureThread = thread
        captureHandler = Handler(thread.looper)

        val reader = createImageReader(captureWidthPx, captureHeightPx)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "AnalisadorCorridasDisplay",
            captureWidthPx,
            captureHeightPx,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )
    }

    private fun createImageReader(width: Int, height: Int): ImageReader {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 4)
        reader.setOnImageAvailableListener({ imgReader ->
            if (!isCaptureActive.get() || !isContentVisible) {
                drainReader(imgReader)
                clearPendingImage()
                return@setOnImageAvailableListener
            }

            val latest = try {
                imgReader.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            val intervalMs = effectiveIntervalMs()
            val elapsed = SystemClock.elapsedRealtime() - lastCaptureElapsedMs

            if (isProcessingFrame.get() || elapsed < intervalMs) {
                // Retém apenas o frame mais recente e garante nova tentativa ao fim do cooldown/OCR
                replacePendingImage(latest)
                val delayMs = if (isProcessingFrame.get()) {
                    BUSY_RETRY_DELAY_MS
                } else {
                    (intervalMs - elapsed).coerceAtLeast(MIN_RETRY_DELAY_MS)
                }
                schedulePendingFrameCheck(delayMs)
                return@setOnImageAvailableListener
            }

            if (!isProcessingFrame.compareAndSet(false, true)) {
                replacePendingImage(latest)
                schedulePendingFrameCheck(BUSY_RETRY_DELAY_MS)
                return@setOnImageAvailableListener
            }

            clearPendingImage()
            dispatchFrameForAnalysis(latest, width, height)
        }, captureHandler)
        return reader
    }

    private fun processNextAvailableOrPendingFrame() {
        if (!isCaptureActive.get() || !isContentVisible) {
            clearPendingImage()
            return
        }
        if (isProcessingFrame.get()) {
            schedulePendingFrameCheck(BUSY_RETRY_DELAY_MS)
            return
        }

        val intervalMs = effectiveIntervalMs()
        val elapsed = SystemClock.elapsedRealtime() - lastCaptureElapsedMs
        if (elapsed < intervalMs) {
            schedulePendingFrameCheck((intervalMs - elapsed).coerceAtLeast(MIN_RETRY_DELAY_MS))
            return
        }

        val readerLatest = try {
            imageReader?.acquireLatestImage()
        } catch (_: Exception) {
            null
        }

        val frameToProcess = if (readerLatest != null) {
            clearPendingImage()
            readerLatest
        } else {
            takePendingImage()
        } ?: return

        if (!isProcessingFrame.compareAndSet(false, true)) {
            replacePendingImage(frameToProcess)
            schedulePendingFrameCheck(BUSY_RETRY_DELAY_MS)
            return
        }

        dispatchFrameForAnalysis(frameToProcess, captureWidthPx, captureHeightPx)
    }

    private fun dispatchFrameForAnalysis(image: Image, width: Int, height: Int) {
        val epoch = captureEpoch.get()
        lastCaptureElapsedMs = SystemClock.elapsedRealtime()

        // Converte na HandlerThread de captura e libera imediatamente o buffer do ImageReader
        val tempBitmap = try {
            imageToBitmap(image, width, height)
        } catch (_: Exception) {
            null
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
            }
        }

        if (tempBitmap == null || !isCaptureActive.get()) {
            tempBitmap?.recycle()
            isProcessingFrame.set(false)
            if (isCaptureActive.get() && hasPendingImage()) {
                schedulePendingFrameCheck(MIN_RETRY_DELAY_MS)
            }
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!isCaptureActive.get() || epoch != captureEpoch.get()) return@launch

                ocrRecognizer.warmUp()

                val excludeRect = computeScaledOverlayExclusionRect(
                    captureW = tempBitmap.width,
                    captureH = tempBitmap.height
                )
                if (excludeRect != null) {
                    Canvas(tempBitmap).drawRect(excludeRect, maskPaint)
                }

                val extractedText = ocrRecognizer.recognizeText(tempBitmap, excludeRect)
                if (!isCaptureActive.get() || epoch != captureEpoch.get()) return@launch

                val parsedOffer = RideOfferParser.parse(extractedText)
                val analysis = parsedOffer?.let {
                    RideCalculator.analyzeOrNull(
                        input = it,
                        settings = currentSettings,
                        isSimulation = false
                    )
                }

                if (parsedOffer != null && analysis != null) {
                    cancelStaticClearTimer()
                    val shouldPublish = synchronized(offerStateLock) {
                        if (epoch != captureEpoch.get()) {
                            false
                        } else {
                            consecutiveMisses = 0
                            val changed = parsedOffer.signature != lastOfferSignature
                            if (changed) {
                                lastOfferSignature = parsedOffer.signature
                            }
                            changed
                        }
                    }
                    if (shouldPublish) {
                        AnalysisStateHolder.publishLiveResult(analysis)
                    }
                } else {
                    var shouldClearNow = false
                    var shouldScheduleStaticCheck = false
                    synchronized(offerStateLock) {
                        if (epoch == captureEpoch.get() && lastOfferSignature != null) {
                            consecutiveMisses++
                            if (consecutiveMisses >= MISSES_TO_CLEAR_OFFER) {
                                lastOfferSignature = null
                                consecutiveMisses = 0
                                shouldClearNow = true
                            } else {
                                shouldScheduleStaticCheck = true
                            }
                        }
                    }
                    if (shouldClearNow) {
                        cancelStaticClearTimer()
                        AnalysisStateHolder.clearLiveResult()
                    } else if (shouldScheduleStaticCheck) {
                        scheduleStaticClearVerification()
                    }
                }
            } catch (_: Exception) {
                // Ignora quadros transitórios com falha de leitura sem interromper a captura
            } finally {
                if (!tempBitmap.isRecycled) {
                    tempBitmap.recycle()
                }
                isProcessingFrame.set(false)
                if (isCaptureActive.get() && hasPendingImage()) {
                    val remaining = (effectiveIntervalMs() -
                        (SystemClock.elapsedRealtime() - lastCaptureElapsedMs))
                        .coerceAtLeast(MIN_RETRY_DELAY_MS)
                    schedulePendingFrameCheck(remaining)
                }
            }
        }
    }

    private fun recreateVirtualDisplaySurface(newWidth: Int, newHeight: Int) {
        val display = virtualDisplay ?: return
        val oldReader = imageReader

        captureHandler?.removeCallbacks(processPendingFrameRunnable)
        clearPendingImage()

        captureWidthPx = newWidth
        captureHeightPx = newHeight

        val newReader = createImageReader(newWidth, newHeight)
        imageReader = newReader
        display.resize(newWidth, newHeight, resources.displayMetrics.densityDpi)
        display.surface = newReader.surface

        try {
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
        } catch (_: Exception) {
        }
    }

    private fun effectiveIntervalMs(): Long {
        return currentSettings.captureIntervalMs.coerceIn(500L, 3000L)
    }

    private fun schedulePendingFrameCheck(delayMs: Long) {
        val handler = captureHandler ?: return
        handler.removeCallbacks(processPendingFrameRunnable)
        handler.postDelayed(processPendingFrameRunnable, delayMs)
    }

    private fun scheduleStaticClearVerification() {
        mainHandler.removeCallbacks(staticClearVerificationRunnable)
        mainHandler.postDelayed(staticClearVerificationRunnable, STATIC_CLEAR_CONFIRM_DELAY_MS)
    }

    private fun cancelStaticClearTimer() {
        mainHandler.removeCallbacks(staticClearVerificationRunnable)
    }

    private fun replacePendingImage(newImage: Image) {
        synchronized(pendingImageLock) {
            if (pendingImage !== newImage) {
                try {
                    pendingImage?.close()
                } catch (_: Exception) {
                }
                pendingImage = newImage
            }
        }
    }

    private fun takePendingImage(): Image? {
        synchronized(pendingImageLock) {
            val img = pendingImage
            pendingImage = null
            return img
        }
    }

    private fun clearPendingImage() {
        synchronized(pendingImageLock) {
            try {
                pendingImage?.close()
            } catch (_: Exception) {
            }
            pendingImage = null
        }
    }

    private fun hasPendingImage(): Boolean {
        synchronized(pendingImageLock) {
            return pendingImage != null
        }
    }

    private fun drainReader(reader: ImageReader) {
        try {
            reader.acquireLatestImage()?.close()
        } catch (_: Exception) {
        }
    }

    private fun computeScaledOverlayExclusionRect(captureW: Int, captureH: Int): Rect? {
        if (!AnalysisStateHolder.isOverlayVisible.value || AnalysisStateHolder.liveResult.value == null) {
            return null
        }
        val screenRect = AnalysisStateHolder.overlayBoundsInScreen.value ?: return null
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return null

        val scaleX = captureW.toFloat() / screenWidthPx.toFloat()
        val scaleY = captureH.toFloat() / screenHeightPx.toFloat()
        val paddingPx = 2

        val left = ((screenRect.left * scaleX).roundToInt() - paddingPx).coerceIn(0, captureW)
        val top = ((screenRect.top * scaleY).roundToInt() - paddingPx).coerceIn(0, captureH)
        val right = ((screenRect.right * scaleX).roundToInt() + paddingPx).coerceIn(0, captureW)
        val bottom = ((screenRect.bottom * scaleY).roundToInt() + paddingPx).coerceIn(0, captureH)

        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null

        val rowPadding = (rowStride - pixelStride * width).coerceAtLeast(0)
        val paddedWidth = width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return if (paddedWidth > width) {
            Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                paddedBitmap.recycle()
            }
        } else {
            paddedBitmap
        }
    }

    private fun startOverlayService() {
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW
        }
        startService(overlayIntent)
    }

    private fun stopOverlayService() {
        val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_HIDE
        }
        startService(overlayIntent)
    }

    private fun stopCaptureAndSelf() {
        cleanupProjection()
        stopOverlayService()
        AnalysisStateHolder.setCaptureRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupProjection() {
        isCaptureActive.set(false)
        captureEpoch.incrementAndGet()
        cancelStaticClearTimer()
        captureHandler?.removeCallbacks(processPendingFrameRunnable)
        clearPendingImage()

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        synchronized(offerStateLock) {
            lastOfferSignature = null
            consecutiveMisses = 0
        }
        isProcessingFrame.set(false)
        isContentVisible = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_stop_analysis), stopIntent)
            .build()
    }

    override fun onDestroy() {
        cleanupProjection()
        AnalysisStateHolder.setCaptureRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val ACTION_START = "br.com.analisadordecorridas.action.START_CAPTURE"
        const val ACTION_STOP = "br.com.analisadordecorridas.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val MISSES_TO_CLEAR_OFFER = 2
        private const val BUSY_RETRY_DELAY_MS = 120L
        private const val MIN_RETRY_DELAY_MS = 40L
        private const val STATIC_CLEAR_CONFIRM_DELAY_MS = 700L
        private const val CHANNEL_ID = "analisador_captura_canal"
        private const val NOTIFICATION_ID = 1001

        fun createStartIntent(context: Context, resultCode: Int, data: Intent): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
