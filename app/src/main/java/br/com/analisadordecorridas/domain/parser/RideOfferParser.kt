package br.com.analisadordecorridas.domain.parser

import br.com.analisadordecorridas.domain.model.RideOfferInput
import br.com.analisadordecorridas.domain.model.RidePlatform
import java.util.Locale
import kotlin.math.abs

/**
 * Analisador local de texto OCR para ofertas da Uber Motorista e 99 Motorista.
 * Suporta múltiplos layouts reais (inclusive linhas separadas, palavras intermediárias
 * e menções a bônus/dinâmico), filtra textos da própria janela flutuante e descarta
 * dados pessoais de passageiros.
 */
object RideOfferParser {

    private data class SegmentMetrics(
        val distanceKm: Double,
        val durationMinutes: Int,
        val lineIndex: Int
    )

    private data class DurationToken(
        val minutes: Int,
        val start: Int,
        val end: Int,
        val lineIndex: Int
    )

    private data class DistanceToken(
        val distanceKm: Double,
        val start: Int,
        val end: Int,
        val lineIndex: Int
    )

    private val ownOverlayMarkers = listOf(
        "analisador de corridas",
        "média com alerta",
        "media com alerta",
        "aguardando oferta",
        "modo simulação",
        "modo simulacao",
        "até pass.:",
        "ate pass.:"
    )

    private val ownOverlayLabels = setOf("boa", "média", "media", "ruim")

    private val standaloneOverlayRateRegex = Regex(
        pattern = """^(?:r\s*\$|rs)?\s*[0-9]{1,4}(?:[.,][0-9]{1,2})?\s*/\s*km$""",
        option = RegexOption.IGNORE_CASE
    )

    private val ratePerUnitTokenRegex = Regex(
        pattern = """(?:r\s*\$|rs)?\s*[0-9]{1,4}(?:[.,][0-9]{1,2})?\s*/\s*(?:km|h)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val priceRegex = Regex(
        pattern = """(?:R\s*\$|RS)\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    private val splitCurrencySymbolOnlyRegex = Regex(
        pattern = """^(?:R\s*\$|RS)$""",
        option = RegexOption.IGNORE_CASE
    )

    private val standaloneDecimalPriceRegex = Regex(
        pattern = """^([0-9]{1,4}[.,][0-9]{2})$"""
    )

    private val bonusOrContextKeywordRegex = Regex(
        pattern = """(b[ôo]nus|bonus|din[âa]mic[oa]|extra|adicional|inclui|incluindo|gorjeta|turbo|promo|incentivo|taxa|desconto)""",
        option = RegexOption.IGNORE_CASE
    )

    private val nonOfferTotalKeywordRegex = Regex(
        pattern = """(ganhos|saldo|carteira|faturamento|meta|resumo|hoje|semana)""",
        option = RegexOption.IGNORE_CASE
    )

    private val uberRegex = Regex(
        pattern = """\b(uber|uber\s*x|uberx|uber\s*comfort|uber\s*black|uber\s*flash|uber\s*moto|uber\s*bag|uber\s*pet|uber\s*auto|uber\s*select|uber\s*promo|uber\s*teen|prioridade|radar\s+de\s+viagens|viagem\s+exclusiva|oferta\s+exclusiva|correspond[êe]ncia)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val ninetyNineRegex = Regex(
        pattern = """\b(99|99\s*pop|99pop|99\s*moto|99moto|99\s*comfort|99\s*taxi|99\s*negocia|99\s*poupa|poupa|99\s*entrega|99\s*turbo|aceitar\s+corrida)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val uberCategoryCueRegex = Regex(
        pattern = """\b(comfort|black|flash|moto|bag|pet|green|xl|exclusiva|prioridade|radar)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val offerActionCueRegex = Regex(
        pattern = """(\baceitar\b|\brecusar\b|★|\*\s*\d[.,]\d{2}|passageiro|embarque|desembarque|din[âa]mic[oa]|b[ôo]nus|bonus|viagem|corrida)""",
        option = RegexOption.IGNORE_CASE
    )

    private val hoursAndMinutesRegex = Regex(
        pattern = """(\d{1,2})\s*h(?:oras?)?\s*(?:e\s*)?(?:(\d{1,2})\s*(?:min(?:uto)?s?\.?|mins\.?))?\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val minutesOnlyRegex = Regex(
        pattern = """(\d{1,3})\s*(?:min(?:uto)?s?\.?|mins\.?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val distanceRegex = Regex(
        pattern = """(\d+(?:[.,]\d+)?)\s*(km|m)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val pickupLabelRegex = Regex(
        pattern = """(até o passageiro|ate o passageiro|embarque|distância|distancia|para buscar|até você|ate voce|buscar)""",
        option = RegexOption.IGNORE_CASE
    )

    private val tripLabelRegex = Regex(
        pattern = """(corrida|viagem|destino|trajeto|desembarque)""",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): RideOfferInput? {
        if (rawText.isBlank()) return null

        val filteredLines = rawText
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && !isOwnOverlayLine(line)
            }

        if (filteredLines.isEmpty()) return null

        val sanitizedText = filteredLines.joinToString("\n")
        val detectedPlatform = detectPlatform(sanitizedText)

        val price = extractOfferedPrice(filteredLines, sanitizedText) ?: return null
        val segments = extractSegments(filteredLines)

        if (segments.size < 2) {
            return null
        }

        val resolvedPlatform = when {
            detectedPlatform != RidePlatform.UNKNOWN -> detectedPlatform
            offerActionCueRegex.containsMatchIn(sanitizedText) -> RidePlatform.UBER
            else -> return null
        }

        val pickupSegment = findLabeledSegment(filteredLines, segments, pickupLabelRegex) ?: segments[0]
        val tripSegment = findLabeledSegment(filteredLines, segments, tripLabelRegex)
            ?.takeIf { it != pickupSegment }
            ?: segments.firstOrNull { it != pickupSegment }
            ?: return null

        if (!isOfferMetricsValid(price, pickupSegment, tripSegment)) {
            return null
        }

        val signature = buildString {
            append(resolvedPlatform.name)
            append('|')
            append(String.format(Locale.US, "%.2f", price))
            append('|')
            append(String.format(Locale.US, "%.2f", pickupSegment.distanceKm))
            append('|')
            append(pickupSegment.durationMinutes)
            append('|')
            append(String.format(Locale.US, "%.2f", tripSegment.distanceKm))
            append('|')
            append(tripSegment.durationMinutes)
        }

        return RideOfferInput(
            platform = resolvedPlatform,
            offeredPriceBrl = price,
            pickupDistanceKm = pickupSegment.distanceKm,
            pickupDurationMinutes = pickupSegment.durationMinutes,
            tripDistanceKm = tripSegment.distanceKm,
            tripDurationMinutes = tripSegment.durationMinutes,
            signature = signature
        )
    }

    private fun isOwnOverlayLine(line: String): Boolean {
        val lower = line.lowercase(Locale("pt", "BR")).trim()
        if (ownOverlayMarkers.any { it in lower }) return true
        if (lower in ownOverlayLabels) return true
        if (standaloneOverlayRateRegex.matches(lower)) return true
        return false
    }

    private fun isOfferMetricsValid(
        price: Double,
        pickup: SegmentMetrics,
        trip: SegmentMetrics
    ): Boolean {
        if (price !in 3.0..2500.0) return false
        if (pickup.distanceKm !in 0.05..60.0) return false
        if (trip.distanceKm !in 0.1..350.0) return false
        if (pickup.durationMinutes !in 0..180) return false
        if (trip.durationMinutes !in 1..480) return false
        if ((pickup.distanceKm + trip.distanceKm) <= 0.0) return false
        if ((pickup.durationMinutes + trip.durationMinutes) <= 0) return false
        return true
    }

    fun detectPlatform(text: String): RidePlatform {
        val hasUber = uberRegex.containsMatchIn(text)
        val has99 = ninetyNineRegex.containsMatchIn(text)
        return when {
            hasUber && !has99 -> RidePlatform.UBER
            has99 && !hasUber -> RidePlatform.NINETY_NINE
            hasUber -> RidePlatform.UBER
            has99 -> RidePlatform.NINETY_NINE
            uberCategoryCueRegex.containsMatchIn(text) && offerActionCueRegex.containsMatchIn(text) ->
                RidePlatform.UBER
            else -> RidePlatform.UNKNOWN
        }
    }

    private fun extractOfferedPrice(lines: List<String>, fullText: String): Double? {
        val normalizedLines = mergeSplitCurrencyLines(lines)
        val primaryCandidates = mutableListOf<Double>()
        val secondaryCandidates = mutableListOf<Double>()
        val standaloneDecimalCandidates = mutableListOf<Double>()

        for (rawLine in normalizedLines) {
            val lower = rawLine.lowercase(Locale("pt", "BR"))
            if (nonOfferTotalKeywordRegex.containsMatchIn(lower)) continue

            // Remove menções de R$/km ou R$/h antes de procurar o valor total da corrida
            val cleanedLine = ratePerUnitTokenRegex.replace(rawLine, " ").trim()
            if (cleanedLine.isEmpty()) continue

            val isBonusLine = bonusOrContextKeywordRegex.containsMatchIn(cleanedLine)
            val matches = priceRegex.findAll(cleanedLine).toList()

            if (matches.isNotEmpty()) {
                matches.forEachIndexed { idx, match ->
                    val parsed = parseDecimal(match.groupValues[1])
                    if (parsed != null && parsed in 3.0..2500.0) {
                        val prefix = cleanedLine.substring(0, match.range.first).trimEnd()
                        val hasPlusPrefix = prefix.endsWith("+")
                        // Se a linha diz "R$ 18,50 · Inclui +R$ 4,50", o primeiro valor sem "+" é o total
                        val treatAsSecondary = hasPlusPrefix || (isBonusLine && (idx > 0 || matches.size == 1))
                        if (treatAsSecondary) {
                            secondaryCandidates += parsed
                        } else {
                            primaryCandidates += parsed
                        }
                    }
                }
            } else if (!isBonusLine) {
                val standalone = standaloneDecimalPriceRegex.matchEntire(cleanedLine)
                if (standalone != null) {
                    val parsed = parseDecimal(standalone.groupValues[1])
                    if (parsed != null && parsed in 3.0..2500.0) {
                        standaloneDecimalCandidates += parsed
                    }
                }
            }
        }

        primaryCandidates.firstOrNull()?.let { return it }
        standaloneDecimalCandidates.firstOrNull()?.let { return it }
        secondaryCandidates.firstOrNull()?.let { return it }

        val cleanedFullText = ratePerUnitTokenRegex.replace(fullText, " ")
        val fallback = priceRegex.find(cleanedFullText) ?: return null
        return parseDecimal(fallback.groupValues[1])?.takeIf { it in 3.0..2500.0 }
    }

    private fun mergeSplitCurrencyLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null &&
                splitCurrencySymbolOnlyRegex.matches(current) &&
                standaloneDecimalPriceRegex.matches(next)
            ) {
                merged += "$current $next"
                i += 2
            } else {
                merged += current
                i++
            }
        }
        return merged
    }

    private fun extractSegments(lines: List<String>): List<SegmentMetrics> {
        val segments = mutableListOf<SegmentMetrics>()
        val consumedLineIndices = mutableSetOf<Int>()

        // Passo 1: pares tempo + distância na mesma linha (mesmo com palavras intermediárias)
        lines.forEachIndexed { index, rawLine ->
            val cleaned = ratePerUnitTokenRegex.replace(rawLine, " ")
            val durations = extractDurationTokens(cleaned, index)
            val distances = extractDistanceTokens(cleaned, index)

            if (durations.isNotEmpty() && distances.isNotEmpty()) {
                if (durations.size == 1 && distances.size == 1) {
                    segments += SegmentMetrics(
                        distanceKm = distances[0].distanceKm,
                        durationMinutes = durations[0].minutes,
                        lineIndex = index
                    )
                    consumedLineIndices += index
                } else {
                    val usedDistIndices = mutableSetOf<Int>()
                    durations.forEach { dur ->
                        var bestIdx = -1
                        var bestGap = Int.MAX_VALUE
                        distances.forEachIndexed { dIdx, dist ->
                            if (dIdx !in usedDistIndices) {
                                val gap = abs(dur.start - dist.start)
                                if (gap < bestGap) {
                                    bestGap = gap
                                    bestIdx = dIdx
                                }
                            }
                        }
                        if (bestIdx >= 0) {
                            usedDistIndices += bestIdx
                            segments += SegmentMetrics(
                                distanceKm = distances[bestIdx].distanceKm,
                                durationMinutes = dur.minutes,
                                lineIndex = index
                            )
                        }
                    }
                    consumedLineIndices += index
                }
            }
        }

        // Passo 2: linhas restantes onde tempo e distância foram separados em linhas próximas pelo OCR
        if (segments.size < 2) {
            val looseDurations = mutableListOf<DurationToken>()
            val looseDistances = mutableListOf<DistanceToken>()

            lines.forEachIndexed { index, rawLine ->
                if (index in consumedLineIndices) return@forEachIndexed
                val cleaned = ratePerUnitTokenRegex.replace(rawLine, " ")
                looseDurations += extractDurationTokens(cleaned, index)
                looseDistances += extractDistanceTokens(cleaned, index)
            }

            if (looseDurations.isNotEmpty() && looseDistances.isNotEmpty()) {
                val paired = pairLooseTokens(looseDurations, looseDistances)
                segments += paired
            }
        }

        return segments.sortedBy { it.lineIndex }
    }

    private fun pairLooseTokens(
        durations: List<DurationToken>,
        distances: List<DistanceToken>
    ): List<SegmentMetrics> {
        if (durations.size == distances.size &&
            durations.indices.all { i ->
                abs(durations[i].lineIndex - distances[i].lineIndex) <= MAX_CROSS_LINE_DISTANCE
            }
        ) {
            return durations.indices.map { i ->
                val dur = durations[i]
                val dist = distances[i]
                SegmentMetrics(
                    distanceKm = dist.distanceKm,
                    durationMinutes = dur.minutes,
                    lineIndex = minOf(dur.lineIndex, dist.lineIndex)
                )
            }
        }

        val result = mutableListOf<SegmentMetrics>()
        val usedDistIndices = mutableSetOf<Int>()

        for (dur in durations) {
            var bestIdx = -1
            var bestLineGap = Int.MAX_VALUE
            distances.forEachIndexed { dIdx, dist ->
                if (dIdx !in usedDistIndices) {
                    val gap = abs(dur.lineIndex - dist.lineIndex)
                    if (gap <= MAX_CROSS_LINE_DISTANCE && gap < bestLineGap) {
                        bestLineGap = gap
                        bestIdx = dIdx
                    }
                }
            }
            if (bestIdx >= 0) {
                usedDistIndices += bestIdx
                val dist = distances[bestIdx]
                result += SegmentMetrics(
                    distanceKm = dist.distanceKm,
                    durationMinutes = dur.minutes,
                    lineIndex = minOf(dur.lineIndex, dist.lineIndex)
                )
            }
        }

        return result
    }

    private fun extractDurationTokens(line: String, lineIndex: Int): List<DurationToken> {
        val tokens = mutableListOf<DurationToken>()
        val occupiedRanges = mutableListOf<IntRange>()

        hoursAndMinutesRegex.findAll(line).forEach { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@forEach
            val mins = match.groupValues[2].toIntOrNull() ?: 0
            val totalMinutes = hours * 60 + mins
            if (totalMinutes > 0) {
                tokens += DurationToken(
                    minutes = totalMinutes,
                    start = match.range.first,
                    end = match.range.last,
                    lineIndex = lineIndex
                )
                occupiedRanges += match.range
            }
        }

        minutesOnlyRegex.findAll(line).forEach { match ->
            val overlapsHourMatch = occupiedRanges.any {
                match.range.first <= it.last && match.range.last >= it.first
            }
            if (overlapsHourMatch) return@forEach

            val minutes = match.groupValues[1].toIntOrNull() ?: return@forEach
            tokens += DurationToken(
                minutes = minutes,
                start = match.range.first,
                end = match.range.last,
                lineIndex = lineIndex
            )
        }

        return tokens.sortedBy { it.start }
    }

    private fun extractDistanceTokens(line: String, lineIndex: Int): List<DistanceToken> {
        return distanceRegex.findAll(line).mapNotNull { match ->
            val rawDist = parseDecimal(match.groupValues[1]) ?: return@mapNotNull null
            val unit = match.groupValues[2]
            DistanceToken(
                distanceKm = toKm(rawDist, unit),
                start = match.range.first,
                end = match.range.last,
                lineIndex = lineIndex
            )
        }.toList()
    }

    private fun findLabeledSegment(
        lines: List<String>,
        segments: List<SegmentMetrics>,
        labelRegex: Regex
    ): SegmentMetrics? {
        return segments.firstOrNull { seg ->
            val prev = lines.getOrNull(seg.lineIndex - 1).orEmpty()
            val current = lines.getOrNull(seg.lineIndex).orEmpty()
            val next = lines.getOrNull(seg.lineIndex + 1).orEmpty()
            labelRegex.containsMatchIn(current) ||
                labelRegex.containsMatchIn(prev) ||
                labelRegex.containsMatchIn(next)
        }
    }

    private fun toKm(value: Double, unit: String): Double {
        return if (unit.equals("m", ignoreCase = true)) value / 1000.0 else value
    }

    private fun parseDecimal(raw: String): Double? {
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private const val MAX_CROSS_LINE_DISTANCE = 4
}
