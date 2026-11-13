package br.com.analisadordecorridas

import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.model.OfferClassification
import br.com.analisadordecorridas.domain.model.RideOfferInput
import br.com.analisadordecorridas.domain.model.RidePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {

    private val defaultSettings = AnalyzerSettings() // HB20 1.6: R$ 3,99/l, 7,1 km/l

    @Test
    fun mandatoryOfferTest_returnsExactExpectedMetrics() {
        val input = RideOfferInput(
            platform = RidePlatform.UBER,
            offeredPriceBrl = 9.06,
            pickupDistanceKm = 1.3,
            pickupDurationMinutes = 4,
            tripDistanceKm = 3.1,
            tripDurationMinutes = 8
        )

        val result = RideCalculator.analyzeOrNull(input, defaultSettings)
        assertNotNull(result)
        result!!

        assertEquals(4.4, result.totalDistanceKm, 0.001)
        assertEquals(12, result.totalDurationMinutes)
        assertEquals(2.06, result.pricePerKm, 0.001)
        assertEquals(45.30, result.pricePerHour, 0.001)
        assertEquals(2.47, result.estimatedFuelCostBrl, 0.001)
        assertEquals(6.59, result.afterFuelBrl, 0.001)
        assertEquals(OfferClassification.GOOD, result.classification)
    }

    @Test
    fun exactPricePerKmBeforeRounding_isUsedNear140And180() {
        // 7,02 / 5,0 = 1,404 -> exato > 1,40 (MÉDIA), embora arredondado exiba R$ 1,40
        val exact1404 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 7.02, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.40, exact1404.pricePerKm, 0.001)
        assertEquals(OfferClassification.MEDIUM, exact1404.classification)

        // 7,00 / 5,0 = 1,400 -> exato <= 1,40 (RUIM)
        val exact1400 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 7.00, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.40, exact1400.pricePerKm, 0.001)
        assertEquals(OfferClassification.BAD, exact1400.classification)

        // 6,99 / 5,0 = 1,398 -> exato <= 1,40 (RUIM), embora arredondado exiba R$ 1,40
        val exact1398 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 6.99, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.40, exact1398.pricePerKm, 0.001)
        assertEquals(OfferClassification.BAD, exact1398.classification)

        // 9,02 / 5,0 = 1,804 -> exato > 1,80 (BOA), embora arredondado exiba R$ 1,80
        val exact1804 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.NINETY_NINE, 9.02, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.80, exact1804.pricePerKm, 0.001)
        assertEquals(OfferClassification.GOOD, exact1804.classification)

        // 9,00 / 5,0 = 1,800 -> exato <= 1,80 (MÉDIA)
        val exact1800 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.NINETY_NINE, 9.00, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.80, exact1800.pricePerKm, 0.001)
        assertEquals(OfferClassification.MEDIUM, exact1800.classification)

        // 8,99 / 5,0 = 1,798 -> exato <= 1,80 (MÉDIA), embora arredondado exiba R$ 1,80
        val exact1798 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.NINETY_NINE, 8.99, 1.0, 3, 4.0, 12),
            defaultSettings
        )!!
        assertEquals(1.80, exact1798.pricePerKm, 0.001)
        assertEquals(OfferClassification.MEDIUM, exact1798.classification)
    }

    @Test
    fun pickupDistanceBoundaries_200_300_400_areEvaluatedCorrectly() {
        val at200 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 10.00, 2.00, 5, 3.00, 10),
            defaultSettings
        )!!
        assertEquals(OfferClassification.GOOD, at200.classification)

        val above200 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 10.02, 2.01, 5, 3.00, 10),
            defaultSettings
        )!!
        assertEquals(OfferClassification.MEDIUM, above200.classification)

        val at300 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.NINETY_NINE, 12.00, 3.00, 8, 3.00, 10),
            defaultSettings
        )!!
        assertEquals(OfferClassification.MEDIUM, at300.classification)

        val above300 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.NINETY_NINE, 12.02, 3.01, 8, 3.00, 10),
            defaultSettings
        )!!
        assertEquals(OfferClassification.MEDIUM_WITH_ALERT, above300.classification)
        assertTrue(above300.justification.contains("relativamente distante", ignoreCase = true))

        val at400 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 16.00, 4.00, 10, 6.00, 15),
            defaultSettings
        )!!
        assertEquals(OfferClassification.MEDIUM_WITH_ALERT, at400.classification)

        val above400 = RideCalculator.analyzeOrNull(
            RideOfferInput(RidePlatform.UBER, 25.00, 4.01, 10, 5.99, 15),
            defaultSettings
        )!!
        assertEquals(OfferClassification.BAD, above400.classification)
    }

    @Test
    fun zeroDenominators_returnNullWithoutCrashing() {
        assertNull(
            RideCalculator.analyzeOrNull(
                RideOfferInput(RidePlatform.UBER, 10.0, 0.0, 4, 0.0, 6),
                defaultSettings
            )
        )
        assertNull(
            RideCalculator.analyzeOrNull(
                RideOfferInput(RidePlatform.UBER, 10.0, 1.0, 0, 3.0, 0),
                defaultSettings
            )
        )
        assertNull(
            RideCalculator.analyzeOrNull(
                RideOfferInput(RidePlatform.UBER, 10.0, 1.0, 4, 3.0, 6),
                defaultSettings.copy(fuelConsumptionKmPerLiter = 0.0)
            )
        )
    }
}
