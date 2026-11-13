package br.com.analisadordecorridas.domain.model

enum class RidePlatform(val displayName: String) {
    UBER("Uber"),
    NINETY_NINE("99"),
    UNKNOWN("Desconhecida")
}

enum class OfferClassification(val label: String) {
    GOOD("BOA"),
    MEDIUM("MÉDIA"),
    MEDIUM_WITH_ALERT("MÉDIA COM ALERTA"),
    BAD("RUIM")
}

enum class KmClassification {
    GOOD,
    MEDIUM,
    BAD
}

enum class PickupDistanceClassification {
    GOOD,
    MEDIUM,
    MEDIUM_WITH_ALERT,
    BAD
}

data class RideOfferInput(
    val platform: RidePlatform,
    val offeredPriceBrl: Double,
    val pickupDistanceKm: Double,
    val pickupDurationMinutes: Int,
    val tripDistanceKm: Double,
    val tripDurationMinutes: Int,
    val signature: String = ""
)

data class RideAnalysisResult(
    val platform: RidePlatform,
    val offeredPriceBrl: Double,
    val pickupDistanceKm: Double,
    val pickupDurationMinutes: Int,
    val tripDistanceKm: Double,
    val tripDurationMinutes: Int,
    val totalDistanceKm: Double,
    val totalDurationMinutes: Int,
    val pricePerKm: Double,
    val pricePerHour: Double,
    val fuelCostPerKm: Double,
    val estimatedFuelCostBrl: Double,
    val afterFuelBrl: Double,
    val kmClassification: KmClassification,
    val pickupDistanceClassification: PickupDistanceClassification,
    val classification: OfferClassification,
    val justification: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isSimulation: Boolean = false
)

data class AnalyzerSettings(
    val vehicleModel: String = DEFAULT_VEHICLE_MODEL,
    val fuelType: String = DEFAULT_FUEL_TYPE,
    val fuelPricePerLiter: Double = DEFAULT_FUEL_PRICE_PER_LITER,
    val fuelConsumptionKmPerLiter: Double = DEFAULT_FUEL_CONSUMPTION_KM_PER_LITER,
    val maxBadPricePerKm: Double = DEFAULT_MAX_BAD_PRICE_PER_KM,
    val maxMediumPricePerKm: Double = DEFAULT_MAX_MEDIUM_PRICE_PER_KM,
    val maxGoodPickupDistanceKm: Double = DEFAULT_MAX_GOOD_PICKUP_KM,
    val maxMediumPickupDistanceKm: Double = DEFAULT_MAX_MEDIUM_PICKUP_KM,
    val maxAlertPickupDistanceKm: Double = DEFAULT_MAX_ALERT_PICKUP_KM,
    val captureIntervalMs: Long = DEFAULT_CAPTURE_INTERVAL_MS,
    val overlayOpacity: Float = 0.95f
) {
    companion object {
        const val DEFAULT_VEHICLE_MODEL = "Hyundai HB20 1.6 automático 2017"
        const val DEFAULT_FUEL_TYPE = "Etanol"
        const val DEFAULT_FUEL_PRICE_PER_LITER = 3.99
        const val DEFAULT_FUEL_CONSUMPTION_KM_PER_LITER = 7.1
        const val DEFAULT_MAX_BAD_PRICE_PER_KM = 1.40
        const val DEFAULT_MAX_MEDIUM_PRICE_PER_KM = 1.80
        const val DEFAULT_MAX_GOOD_PICKUP_KM = 2.00
        const val DEFAULT_MAX_MEDIUM_PICKUP_KM = 3.00
        const val DEFAULT_MAX_ALERT_PICKUP_KM = 4.00
        const val DEFAULT_CAPTURE_INTERVAL_MS = 800L
    }
}
