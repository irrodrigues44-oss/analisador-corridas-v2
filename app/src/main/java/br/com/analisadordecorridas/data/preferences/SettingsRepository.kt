package br.com.analisadordecorridas.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "analisador_corridas_prefs"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
        val FUEL_TYPE = stringPreferencesKey("fuel_type")
        val FUEL_PRICE_PER_LITER = doublePreferencesKey("fuel_price_per_liter")
        val FUEL_CONSUMPTION_KM_PER_LITER = doublePreferencesKey("fuel_consumption_km_per_liter")
        val MAX_BAD_PRICE_PER_KM = doublePreferencesKey("max_bad_price_per_km")
        val MAX_MEDIUM_PRICE_PER_KM = doublePreferencesKey("max_medium_price_per_km")
        val MAX_GOOD_PICKUP_KM = doublePreferencesKey("max_good_pickup_km")
        val MAX_MEDIUM_PICKUP_KM = doublePreferencesKey("max_medium_pickup_km")
        val MAX_ALERT_PICKUP_KM = doublePreferencesKey("max_alert_pickup_km")
        val CAPTURE_INTERVAL_MS = longPreferencesKey("capture_interval_ms")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    }

    val settingsFlow: Flow<AnalyzerSettings> = context.dataStore.data.map { prefs ->
        val defaults = AnalyzerSettings()
        AnalyzerSettings(
            vehicleModel = prefs[Keys.VEHICLE_MODEL] ?: defaults.vehicleModel,
            fuelType = prefs[Keys.FUEL_TYPE] ?: defaults.fuelType,
            fuelPricePerLiter = prefs[Keys.FUEL_PRICE_PER_LITER] ?: defaults.fuelPricePerLiter,
            fuelConsumptionKmPerLiter = prefs[Keys.FUEL_CONSUMPTION_KM_PER_LITER] ?: defaults.fuelConsumptionKmPerLiter,
            maxBadPricePerKm = prefs[Keys.MAX_BAD_PRICE_PER_KM] ?: defaults.maxBadPricePerKm,
            maxMediumPricePerKm = prefs[Keys.MAX_MEDIUM_PRICE_PER_KM] ?: defaults.maxMediumPricePerKm,
            maxGoodPickupDistanceKm = prefs[Keys.MAX_GOOD_PICKUP_KM] ?: defaults.maxGoodPickupDistanceKm,
            maxMediumPickupDistanceKm = prefs[Keys.MAX_MEDIUM_PICKUP_KM] ?: defaults.maxMediumPickupDistanceKm,
            maxAlertPickupDistanceKm = prefs[Keys.MAX_ALERT_PICKUP_KM] ?: defaults.maxAlertPickupDistanceKm,
            captureIntervalMs = prefs[Keys.CAPTURE_INTERVAL_MS] ?: defaults.captureIntervalMs,
            overlayOpacity = prefs[Keys.OVERLAY_OPACITY] ?: defaults.overlayOpacity
        )
    }

    suspend fun updateSettings(settings: AnalyzerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VEHICLE_MODEL] = settings.vehicleModel
            prefs[Keys.FUEL_TYPE] = settings.fuelType
            prefs[Keys.FUEL_PRICE_PER_LITER] = settings.fuelPricePerLiter
            prefs[Keys.FUEL_CONSUMPTION_KM_PER_LITER] = settings.fuelConsumptionKmPerLiter
            prefs[Keys.MAX_BAD_PRICE_PER_KM] = settings.maxBadPricePerKm
            prefs[Keys.MAX_MEDIUM_PRICE_PER_KM] = settings.maxMediumPricePerKm
            prefs[Keys.MAX_GOOD_PICKUP_KM] = settings.maxGoodPickupDistanceKm
            prefs[Keys.MAX_MEDIUM_PICKUP_KM] = settings.maxMediumPickupDistanceKm
            prefs[Keys.MAX_ALERT_PICKUP_KM] = settings.maxAlertPickupDistanceKm
            prefs[Keys.CAPTURE_INTERVAL_MS] = settings.captureIntervalMs
            prefs[Keys.OVERLAY_OPACITY] = settings.overlayOpacity
        }
    }

    suspend fun resetToDefaults() {
        updateSettings(AnalyzerSettings())
    }
}
