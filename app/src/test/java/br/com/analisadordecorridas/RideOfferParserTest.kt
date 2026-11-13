package br.com.analisadordecorridas

import br.com.analisadordecorridas.domain.calculator.RideCalculator
import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.model.OfferClassification
import br.com.analisadordecorridas.domain.model.RidePlatform
import br.com.analisadordecorridas.domain.parser.RideOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideOfferParserTest {

    @Test
    fun parseUberOffer_matchesMandatoryCase() {
        val rawOcr = """
            UberX
            R$ 9,06
            4 min (1,3 km) de distância
            8 min (3,1 km) de viagem
            Aceitar
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        assertEquals(RidePlatform.UBER, parsed!!.platform)
        assertEquals(9.06, parsed.offeredPriceBrl, 0.001)
        assertEquals(1.3, parsed.pickupDistanceKm, 0.001)
        assertEquals(4, parsed.pickupDurationMinutes)
        assertEquals(3.1, parsed.tripDistanceKm, 0.001)
        assertEquals(8, parsed.tripDurationMinutes)

        val result = RideCalculator.analyzeOrNull(parsed, AnalyzerSettings())
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
    fun parseUberOffer_withWordsBetweenDurationAndDistance_parsesCorrectly() {
        val rawOcr = """
            Uber Comfort
            R$ 24,50
            5 min de distância · 1,8 km
            14 min de viagem · 8,2 km
            Aceitar
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        parsed!!
        assertEquals(RidePlatform.UBER, parsed.platform)
        assertEquals(24.50, parsed.offeredPriceBrl, 0.001)
        assertEquals(1.8, parsed.pickupDistanceKm, 0.001)
        assertEquals(5, parsed.pickupDurationMinutes)
        assertEquals(8.2, parsed.tripDistanceKm, 0.001)
        assertEquals(14, parsed.tripDurationMinutes)
    }

    @Test
    fun parseUberOffer_withBonusAndSplitLines_ignoresBonusAsMainFare() {
        val rawOcr = """
            UberX
            +R$ 4,50 dinâmico
            R$
            18,90
            4 min
            (1,4 km)
            Av. Paulista, 1000
            11 min
            5,6 km
            Aceitar
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        parsed!!
        assertEquals(RidePlatform.UBER, parsed.platform)
        assertEquals(18.90, parsed.offeredPriceBrl, 0.001)
        assertEquals(1.4, parsed.pickupDistanceKm, 0.001)
        assertEquals(4, parsed.pickupDurationMinutes)
        assertEquals(5.6, parsed.tripDistanceKm, 0.001)
        assertEquals(11, parsed.tripDurationMinutes)
    }

    @Test
    fun parseUberOffer_withInlinePricePerKmAndNoLiteralUberWord_succeedsViaOfferCues() {
        val rawOcr = """
            Viagem exclusiva · 4,94 ★
            R$ 16,20 (R$ 2,02/km)
            3 min até o embarque (900 m)
            13 min de viagem (7,1 km)
            Aceitar
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        parsed!!
        assertEquals(RidePlatform.UBER, parsed.platform)
        assertEquals(16.20, parsed.offeredPriceBrl, 0.001)
        assertEquals(0.9, parsed.pickupDistanceKm, 0.001)
        assertEquals(3, parsed.pickupDurationMinutes)
        assertEquals(7.1, parsed.tripDistanceKm, 0.001)
        assertEquals(13, parsed.tripDurationMinutes)
    }

    @Test
    fun parse99Offer_withMeters_convertsToKilometers() {
        val rawOcr = """
            99Pop
            R$ 15,00
            3 min · 800 m
            12 min · 4,2 km
            Aceitar corrida
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        assertEquals(RidePlatform.NINETY_NINE, parsed!!.platform)
        assertEquals(15.00, parsed.offeredPriceBrl, 0.001)
        assertEquals(0.8, parsed.pickupDistanceKm, 0.001)
        assertEquals(3, parsed.pickupDurationMinutes)
        assertEquals(4.2, parsed.tripDistanceKm, 0.001)
        assertEquals(12, parsed.tripDurationMinutes)
    }

    @Test
    fun parse99Offer_withSplitLinesAndBonus_parsesCorrectly() {
        val rawOcr = """
            99Pop
            R$ 21,40
            Inclui +R$ 3,00 bônus
            1,2 km
            Av. Brasil
            4 min
            6,8 km
            16 min
            Aceitar corrida
        """.trimIndent()

        val parsed = RideOfferParser.parse(rawOcr)
        assertNotNull(parsed)
        parsed!!
        assertEquals(RidePlatform.NINETY_NINE, parsed.platform)
        assertEquals(21.40, parsed.offeredPriceBrl, 0.001)
        assertEquals(1.2, parsed.pickupDistanceKm, 0.001)
        assertEquals(4, parsed.pickupDurationMinutes)
        assertEquals(6.8, parsed.tripDistanceKm, 0.001)
        assertEquals(16, parsed.tripDurationMinutes)
    }

    @Test
    fun parseSelfOverlayText_isIgnoredAndDoesNotTriggerOffer() {
        val overlayOnlyText = """
            Uber
            R$ 9,06
            BOA
            R$ 2,06/km
            Até pass.: 1,3 km
        """.trimIndent()

        assertNull(RideOfferParser.parse(overlayOnlyText))
    }

    @Test
    fun parseNonOfferScreen_returnsNull() {
        val homeScreenText = """
            Você está online
            Procurando viagens...
            Ganhos de hoje: R$ 142,00
        """.trimIndent()

        assertNull(RideOfferParser.parse(homeScreenText))
    }
}
