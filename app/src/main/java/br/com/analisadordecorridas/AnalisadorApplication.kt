package br.com.analisadordecorridas

import android.app.Application
import br.com.analisadordecorridas.data.preferences.SettingsRepository
import br.com.analisadordecorridas.ocr.MlKitTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnalisadorApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var ocrRecognizer: MlKitTextRecognizer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        ocrRecognizer = MlKitTextRecognizer()
        applicationScope.launch {
            ocrRecognizer.warmUp()
        }
    }
}
