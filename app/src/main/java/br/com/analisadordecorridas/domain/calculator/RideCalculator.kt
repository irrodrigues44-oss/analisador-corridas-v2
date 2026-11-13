package br.com.analisadordecorridas.domain.calculator

import br.com.analisadordecorridas.domain.model.AnalyzerSettings
import br.com.analisadordecorridas.domain.model.KmClassification
import br.com.analisadordecorridas.domain.model.OfferClassification
import br.com.analisadordecorridas.domain.model.PickupDistanceClassification
import br.com.analisadordecorridas.domain.model.RideAnalysisResult
import br.com.analisadordecorridas.domain.model.RideOfferInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object RideCalculator {

    private val ptBrLocale = Locale("pt", "BR")

    /**
     * Calcula todos os indicadores da oferta considerando obrigatoriamente o deslocamento
     * até o passageiro. Classifica pelo valor exato de R$/km antes do arredondamento,
     * aplicando o arredondamento somente na apresentação.
     * Retorna null caso algum denominador seja zero ou os dados sejam inválidos.
     */
    fun analyzeOrNull(
        input: RideOfferInput,
        settings: AnalyzerSettings,
        isSimulation: Boolean = false,
        timestampMillis: Long = System.currentTimeMillis()
    ): RideAnalysisResult? {
        if (!isValidInput(input, settings)) {
            return null
        }

        val rawTotalDistanceKm = input.pickupDistanceKm + input.tripDistanceKm
        val totalDurationMinutes = input.pickupDurationMinutes + input.tripDurationMinutes

        if (rawTotalDistanceKm <= 0.0 || totalDurationMinutes <= 0 || settings.fuelConsumptionKmPerLiter <= 0.0) {
            return null
        }

        val rawPricePerKm = input.offeredPriceBrl / rawTotalDistanceKm
        val rawPricePerHour = (input.offeredPriceBrl / totalDurationMinutes.toDouble()) * 60.0
        val rawFuelCostPerKm = settings.fuelPricePerLiter / settings.fuelConsumptionKmPerLiter
        val rawEstimatedFuelCost = rawTotalDistanceKm * rawFuelCostPerKm
        val rawAfterFuel = input.offeredPriceBrl - rawEstimatedFuelCost

        // Classificação feita com os valores exatos antes do arredondamento
        val kmClassification = classifyByPricePerKm(rawPricePerKm, settings)
        val pickupClassification = classifyByPickupDistance(input.pickupDistanceKm, settings)

        // Arredondamento aplicado apenas para apresentação
        val offeredPrice = round2(input.offeredPriceBrl)
        val pickupKm = round2(input.pickupDistanceKm)
        val tripKm = round2(input.tripDistanceKm)
        val totalDistanceKm = round2(rawTotalDistanceKm)
        val pricePerKm = round2(rawPricePerKm)
        val pricePerHour = round2(rawPricePerHour)
        val fuelCostPerKm = round2(rawFuelCostPerKm)
        val estimatedFuelCostBrl = round2(rawEstimatedFuelCost)
        val afterFuelBrl = round2(rawAfterFuel)

        val (finalClassification, justification) = combineClassification(
            kmClassification = kmClassification,
            pickupClassification = pickupClassification,
            displayPricePerKm = pricePerKm,
            displayPickupDistanceKm = pickupKm,
            settings = settings
        )

        return RideAnalysisResult(
            platform = input.platform,
            offeredPriceBrl = offeredPrice,
            pickupDistanceKm = pickupKm,
            pickupDurationMinutes = input.pickupDurationMinutes,
            tripDistanceKm = tripKm,
            tripDurationMinutes = input.tripDurationMinutes,
            totalDistanceKm = totalDistanceKm,
            totalDurationMinutes = totalDurationMinutes,
            pricePerKm = pricePerKm,
            pricePerHour = pricePerHour,
            fuelCostPerKm = fuelCostPerKm,
            estimatedFuelCostBrl = estimatedFuelCostBrl,
            afterFuelBrl = afterFuelBrl,
            kmClassification = kmClassification,
            pickupDistanceClassification = pickupClassification,
            classification = finalClassification,
            justification = justification,
            timestampMillis = timestampMillis,
            isSimulation = isSimulation
        )
    }

    fun isValidInput(input: RideOfferInput, settings: AnalyzerSettings): Boolean {
        val values = listOf(
            input.offeredPriceBrl,
            input.pickupDistanceKm,
            input.tripDistanceKm,
            settings.fuelPricePerLiter,
            settings.fuelConsumptionKmPerLiter
        )
        if (values.any { it.isNaN() || it.isInfinite() }) return false
        if (input.offeredPriceBrl <= 0.0) return false
        if (input.pickupDistanceKm < 0.0 || input.tripDistanceKm < 0.0) return false
        if (input.pickupDurationMinutes < 0 || input.tripDurationMinutes < 0) return false
        if ((input.pickupDistanceKm + input.tripDistanceKm) <= 0.0) return false
        if ((input.pickupDurationMinutes + input.tripDurationMinutes) <= 0) return false
        if (settings.fuelPricePerLiter < 0.0 || settings.fuelConsumptionKmPerLiter <= 0.0) return false
        return true
    }

    fun classifyByPricePerKm(
        exactPricePerKm: Double,
        settings: AnalyzerSettings
    ): KmClassification {
        return when {
            exactPricePerKm <= settings.maxBadPricePerKm -> KmClassification.BAD
            exactPricePerKm <= settings.maxMediumPricePerKm -> KmClassification.MEDIUM
            else -> KmClassification.GOOD
        }
    }

    fun classifyByPickupDistance(
        exactPickupDistanceKm: Double,
        settings: AnalyzerSettings
    ): PickupDistanceClassification {
        return when {
            exactPickupDistanceKm <= settings.maxGoodPickupDistanceKm -> PickupDistanceClassification.GOOD
            exactPickupDistanceKm <= settings.maxMediumPickupDistanceKm -> PickupDistanceClassification.MEDIUM
            exactPickupDistanceKm <= settings.maxAlertPickupDistanceKm -> PickupDistanceClassification.MEDIUM_WITH_ALERT
            else -> PickupDistanceClassification.BAD
        }
    }

    fun combineClassification(
        kmClassification: KmClassification,
        pickupClassification: PickupDistanceClassification,
        displayPricePerKm: Double,
        displayPickupDistanceKm: Double,
        settings: AnalyzerSettings
    ): Pair<OfferClassification, String> {
        if (kmClassification == KmClassification.BAD) {
            return OfferClassification.BAD to
                "Valor por km ruim (até ${formatBrl(settings.maxBadPricePerKm)}/km)."
        }

        if (pickupClassification == PickupDistanceClassification.BAD) {
            return OfferClassification.BAD to
                "Passageiro muito distante (acima de ${formatKm(settings.maxAlertPickupDistanceKm)})."
        }

        if (kmClassification == KmClassification.GOOD &&
            pickupClassification == PickupDistanceClassification.GOOD
        ) {
            return OfferClassification.GOOD to
                "Valor bom por km (${formatBrl(displayPricePerKm)}/km) e passageiro próximo (${formatKm(displayPickupDistanceKm)})."
        }

        if (pickupClassification == PickupDistanceClassification.MEDIUM_WITH_ALERT) {
            val kmDesc = if (kmClassification == KmClassification.GOOD) "Valor bom por km" else "Valor médio por km"
            return OfferClassification.MEDIUM_WITH_ALERT to
                "$kmDesc, mas passageiro relativamente distante (${formatKm(displayPickupDistanceKm)})."
        }

        if (kmClassification == KmClassification.GOOD &&
            pickupClassification == PickupDistanceClassification.MEDIUM
        ) {
            return OfferClassification.MEDIUM to
                "Valor bom por km, mas passageiro a distância média (${formatKm(displayPickupDistanceKm)})."
        }

        val distanceText = if (pickupClassification == PickupDistanceClassification.GOOD) {
            "passageiro próximo"
        } else {
            "passageiro a distância média"
        }
        return OfferClassification.MEDIUM to "Valor médio por km (${formatBrl(displayPricePerKm)}/km) e $distanceText."
    }

    fun round2(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatBrl(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(ptBrLocale)
        return formatter.format(round2(value))
    }

    fun formatKm(value: Double): String {
        return String.format(ptBrLocale, "%.1f km", round2(value))
    }

    fun formatMinutes(minutes: Int): String {
        return "$minutes min"
    }
}
