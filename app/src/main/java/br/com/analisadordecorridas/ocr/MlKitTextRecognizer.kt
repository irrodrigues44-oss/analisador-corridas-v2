package br.com.analisadordecorridas.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val warmUpMutex = Mutex()
    private val isWarmedUp = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    /**
     * Inicializa antecipadamente o detector e reconhecedor nativo do ML Kit com um
     * bitmap mínimo em memória, eliminando o atraso de cold start na primeira oferta.
     */
    suspend fun warmUp() {
        if (isWarmedUp.get() || isClosed.get()) return
        warmUpMutex.withLock {
            if (isWarmedUp.get() || isClosed.get()) return
            withContext(Dispatchers.Default) {
                val dummy = Bitmap.createBitmap(192, 64, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(dummy)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint().apply {
                        color = Color.BLACK
                        textSize = 24f
                        isAntiAlias = true
                    }
                    canvas.drawText("Uber R$ 10,00 4 min", 8f, 40f, paint)
                    processBitmap(dummy)
                    isWarmedUp.set(true)
                } catch (_: Exception) {
                    // Ignora se o serviço foi encerrado durante o aquecimento
                } finally {
                    if (!dummy.isRecycled) {
                        dummy.recycle()
                    }
                }
            }
        }
    }

    /**
     * Executa OCR localmente via Google ML Kit em um Bitmap temporário em memória.
     * Filtra no nível de linha (Text.Line) e só descarta linhas efetivamente cobertas
     * por [excludeRect], preservando blocos vizinhos com dados válidos da oferta.
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        excludeRect: Rect? = null
    ): String {
        if (isClosed.get()) return ""
        val visionText = processBitmap(bitmap)
        isWarmedUp.set(true)

        return visionText.textBlocks
            .flatMap { block -> block.lines }
            .filter { line -> !isLineCoveredByExcludeRect(line.boundingBox, excludeRect) }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .joinToString(separator = "\n") { it.text }
    }

    private suspend fun processBitmap(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) {
                        cont.resume(visionText)
                    }
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) {
                        cont.resumeWithException(error)
                    }
                }
        }

    private fun isLineCoveredByExcludeRect(lineBox: Rect?, excludeRect: Rect?): Boolean {
        if (excludeRect == null || lineBox == null) return false
        if (!Rect.intersects(lineBox, excludeRect)) return false
        if (excludeRect.contains(lineBox.centerX(), lineBox.centerY())) return true

        val intersectLeft = maxOf(lineBox.left, excludeRect.left)
        val intersectTop = maxOf(lineBox.top, excludeRect.top)
        val intersectRight = minOf(lineBox.right, excludeRect.right)
        val intersectBottom = minOf(lineBox.bottom, excludeRect.bottom)
        if (intersectRight <= intersectLeft || intersectBottom <= intersectTop) return false

        val intersectionArea = (intersectRight - intersectLeft).toLong() *
            (intersectBottom - intersectTop).toLong()
        val lineArea = lineBox.width().coerceAtLeast(1).toLong() *
            lineBox.height().coerceAtLeast(1).toLong()

        return intersectionArea * 2L >= lineArea
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                recognizer.close()
            } catch (_: Exception) {
            }
        }
    }
}
