package com.example.util

import java.util.Locale
import kotlin.math.min

data class CnicMatchValidationResult(
    val isMatch: Boolean,
    val mismatches: List<String> = emptyList()
)

object CnicMatchValidator {

    private val FRONT_KEYWORDS = setOf(
        "PAKISTAN", "ISLAMIC", "REPUBLIC", "NATIONAL", "IDENTITY", "CARD",
        "NAME", "FATHER", "HUSBAND", "GENDER", "COUNTRY", "STAY", "BIRTH",
        "DATE", "ISSUE", "EXPIRY", "NICOP", "NADRA"
    )

    private val BACK_KEYWORDS = setOf(
        "REGISTRAR", "GENERAL", "PAKISTAN", "CURRENT", "PERMANENT",
        "ADDRESS", "SIGNATURE", "HOLDER", "IDENTITY", "CARD", "GOVT",
        "ISSUING", "AUTHORITY", "NADRA", "LOST", "POST"
    )

    private val CNIC_REGEX = Regex("\\b(\\d{5})[-\\s]?(\\d{7})[-\\s]?(\\d)\\b")
    private val DATE_DMY_REGEX = Regex("\\b(\\d{2})[./-](\\d{2})[./-](\\d{4})\\b")
    private val DATE_YMD_REGEX = Regex("\\b(\\d{4})[./-](\\d{2})[./-](\\d{2})\\b")

    fun extractCnicDigits(rawText: String): String? {
        val cleaned = rawText
            .replace('O', '0')
            .replace('o', '0')
        val directMatch = CNIC_REGEX.find(cleaned)
        if (directMatch != null) {
            return directMatch.groupValues[1] + directMatch.groupValues[2] + directMatch.groupValues[3]
        }
        val compact = Regex("\\b\\d{13}\\b").find(cleaned.replace("-", "").replace(" ", ""))
        return compact?.value
    }

    fun extractAllCnicDigits(rawText: String): List<String> {
        val cleaned = rawText
            .replace('O', '0')
            .replace('o', '0')
        return CNIC_REGEX.findAll(cleaned)
            .map { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .distinct()
            .toList()
    }

    fun extractIsoDates(rawText: String): List<String> {
        val found = linkedSetOf<String>()
        DATE_DMY_REGEX.findAll(rawText).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (day in 1..31 && month in 1..12 && year in 1920..2099) {
                found += String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
        }
        DATE_YMD_REGEX.findAll(rawText).forEach { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val day = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (day in 1..31 && month in 1..12 && year in 1920..2099) {
                found += String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
        }
        return found.toList()
    }

    fun isPlausibleCnicFrame(
        sideLabel: String,
        rawText: String,
        lineCount: Int
    ): Boolean {
        val upper = rawText.uppercase(Locale.US)
        val tokens = upper.split(Regex("[^A-Z0-9]+")).filter { it.length >= 3 }.toSet()
        val hasCnicNumber = extractCnicDigits(rawText) != null
        val dates = extractIsoDates(rawText)

        return if (sideLabel.equals("Front", ignoreCase = true)) {
            val frontMatches = tokens.count { it in FRONT_KEYWORDS }
            lineCount >= 2 && (hasCnicNumber || frontMatches >= 2 || (frontMatches >= 1 && dates.isNotEmpty()))
        } else {
            val backMatches = tokens.count { it in BACK_KEYWORDS }
            lineCount >= 2 && (hasCnicNumber || backMatches >= 1 || dates.isNotEmpty())
        }
    }

    fun validateAgainstEnteredDetails(
        fullName: String,
        cnicDigits: String,
        issueDateIso: String,
        expiryDateIso: String,
        frontText: String?,
        backText: String?
    ): CnicMatchValidationResult {
        val mismatches = mutableListOf<String>()
        val combinedText = listOfNotNull(frontText, backText)
            .joinToString("\n")
            .trim()

        if (combinedText.isBlank()) {
            return CnicMatchValidationResult(isMatch = true)
        }

        // 1. Check 13-digit CNIC number if detected by OCR on either side
        val detectedCnics = extractAllCnicDigits(combinedText)
        val normalizedInputCnic = Formatters.sanitizeCnicDigits(cnicDigits)
        if (detectedCnics.isNotEmpty() && normalizedInputCnic.length == 13 && normalizedInputCnic !in detectedCnics) {
            val firstDetected = Formatters.formatCnicDisplay(detectedCnics.first())
            mismatches += "CNIC Number does not match the scanned CNIC card ($firstDetected). Check digits or re-scan."
        }

        // 2. Check Full Legal Name when readable English text is present on the front card
        val frontUpper = (frontText ?: combinedText).uppercase(Locale.US)
        val alphaCharCount = frontUpper.count { it in 'A'..'Z' }
        val cardWords = frontUpper
            .split(Regex("[^A-Z]+"))
            .filter { it.length >= 2 }
        val nameTokens = fullName.uppercase(Locale.US)
            .split(Regex("[^A-Z]+"))
            .filter { it.length >= 3 && it !in setOf("BIN", "BINT", "MRS", "MR") }

        if (alphaCharCount >= 24 && cardWords.size >= 4 && nameTokens.isNotEmpty()) {
            val matchedTokens = nameTokens.count { token ->
                cardWords.any { word -> fuzzyWordMatches(token, word) }
            }
            val requiredMatches = if (nameTokens.size == 1) 1 else 1
            if (matchedTokens < requiredMatches) {
                mismatches += "Full Legal Name does not match the name detected on the scanned CNIC card. Check spelling against your CNIC."
            }
        }

        // 3. Check Issue & Expiry Dates when card issue/expiry dates (>= 2005) are clearly detected
        val cardDates = extractIsoDates(combinedText)
        val modernCardDates = cardDates.filter { date ->
            val year = date.take(4).toIntOrNull() ?: 0
            year >= 2005
        }
        if (modernCardDates.size >= 2) {
            val issueMatches = issueDateIso in modernCardDates
            val expiryMatches = expiryDateIso in modernCardDates
            if (!issueMatches && !expiryMatches) {
                mismatches += "Issue Date or Expiry Date does not match the dates scanned on your CNIC (${modernCardDates.joinToString(", ")})."
            } else if (!issueMatches && modernCardDates.size >= 3) {
                mismatches += "Issue Date ($issueDateIso) was not matched on the scanned CNIC dates (${modernCardDates.joinToString(", ")})."
            } else if (!expiryMatches && modernCardDates.size >= 3) {
                mismatches += "Expiry Date ($expiryDateIso) was not matched on the scanned CNIC dates (${modernCardDates.joinToString(", ")})."
            }
        }

        return CnicMatchValidationResult(
            isMatch = mismatches.isEmpty(),
            mismatches = mismatches
        )
    }

    private fun fuzzyWordMatches(target: String, candidate: String): Boolean {
        if (target == candidate) return true
        val normTarget = normalizeOcrLetters(target)
        val normCand = normalizeOcrLetters(candidate)
        if (normTarget == normCand) return true
        if (normTarget.length >= 4 && (normCand.contains(normTarget) || normTarget.contains(normCand))) {
            return true
        }
        val dist = levenshtein(normTarget, normCand)
        return when {
            normTarget.length <= 3 -> dist == 0
            normTarget.length <= 6 -> dist <= 1
            else -> dist <= 2
        }
    }

    private fun normalizeOcrLetters(input: String): String {
        return input
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
