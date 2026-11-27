package com.example.util

import java.util.Locale
import kotlin.math.min

data class CnicMatchValidationResult(
    val isMatch: Boolean,
    val mismatches: List<String> = emptyList()
)

data class ExtractedCnicDetails(
    val fullName: String = "",
    val cnicDigits: String = "",
    val dateOfBirth: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val gender: String = "male"
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

    private val NON_NAME_WORDS = setOf(
        "PAKISTAN", "ISLAMIC", "REPUBLIC", "NATIONAL", "IDENTITY", "CARD",
        "NAME", "FATHER", "HUSBAND", "GENDER", "COUNTRY", "STAY", "BIRTH",
        "DATE", "ISSUE", "EXPIRY", "NICOP", "NADRA", "GOVT", "HOLDER",
        "SIGNATURE", "REGISTRAR", "GENERAL", "NUMBER", "MALE", "FEMALE",
        "PAK", "OF", "THE", "LIFETIME"
    )

    private val CNIC_REGEX = Regex("\\b(\\d{5})[-\\s]?(\\d{7})[-\\s]?(\\d)\\b")
    private val DATE_DMY_REGEX = Regex("\\b(\\d{2})\\s*[./-]\\s*(\\d{2})\\s*[./-]\\s*(\\d{4})\\b")
    private val DATE_YMD_REGEX = Regex("\\b(\\d{4})\\s*[./-]\\s*(\\d{2})\\s*[./-]\\s*(\\d{2})\\b")

    fun normalizeUrduDigits(raw: String): String {
        if (raw.isEmpty()) return raw
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun extractCnicDigits(rawText: String): String? {
        val cleaned = normalizeUrduDigits(rawText)
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
        val cleaned = normalizeUrduDigits(rawText)
            .replace('O', '0')
            .replace('o', '0')
        return CNIC_REGEX.findAll(cleaned)
            .map { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .distinct()
            .toList()
    }

    fun extractIsoDates(rawText: String): List<String> {
        val normalized = normalizeUrduDigits(rawText)
        val found = linkedSetOf<String>()
        DATE_DMY_REGEX.findAll(normalized).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (day in 1..31 && month in 1..12 && year in 1920..2099) {
                found += String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
        }
        DATE_YMD_REGEX.findAll(normalized).forEach { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val day = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (day in 1..31 && month in 1..12 && year in 1920..2099) {
                found += String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
        }
        return found.toList()
    }

    fun extractCnicDetails(frontText: String, backText: String): ExtractedCnicDetails {
        val combined = listOf(frontText, backText).filter { it.isNotBlank() }.joinToString("\n")
        val cnicDigits = extractCnicDigits(combined).orEmpty()

        // Sort dates chronologically; typical Pakistan CNIC carries [DOB, Issue, Expiry]
        val sortedDates = extractIsoDates(frontText.ifBlank { combined }).distinct().sorted()
        val dob: String
        val issueDate: String
        val expiryDate: String
        when {
            sortedDates.size >= 3 -> {
                dob = sortedDates.first()
                issueDate = sortedDates[1]
                expiryDate = sortedDates.last()
            }
            sortedDates.size == 2 -> {
                val firstYear = sortedDates[0].take(4).toIntOrNull() ?: 2000
                if (firstYear <= 2008) {
                    dob = sortedDates[0]
                    issueDate = sortedDates[1]
                    expiryDate = ""
                } else {
                    dob = ""
                    issueDate = sortedDates[0]
                    expiryDate = sortedDates[1]
                }
            }
            sortedDates.size == 1 -> {
                val yr = sortedDates[0].take(4).toIntOrNull() ?: 2000
                if (yr <= 2008) {
                    dob = sortedDates[0]
                    issueDate = ""
                    expiryDate = ""
                } else {
                    dob = ""
                    issueDate = sortedDates[0]
                    expiryDate = ""
                }
            }
            else -> {
                dob = ""
                issueDate = ""
                expiryDate = ""
            }
        }

        val gender = detectGenderFromOcr(combined, cnicDigits)
        val fullName = extractNameFromOcr(frontText.ifBlank { combined })

        return ExtractedCnicDetails(
            fullName = fullName,
            cnicDigits = cnicDigits,
            dateOfBirth = dob,
            issueDate = issueDate,
            expiryDate = expiryDate,
            gender = gender
        )
    }

    private fun detectGenderFromOcr(rawText: String, cnicDigits: String): String {
        val upper = rawText.uppercase(Locale.US)
        return when {
            Regex("\\bFEMALE\\b").containsMatchIn(upper) ||
                Regex("GENDER\\s*[:\\-]?\\s*F\\b").containsMatchIn(upper) ||
                upper.contains("W/O") ||
                upper.contains("D/O") -> "female"
            Regex("\\bMALE\\b").containsMatchIn(upper) ||
                Regex("GENDER\\s*[:\\-]?\\s*M\\b").containsMatchIn(upper) ||
                upper.contains("S/O") -> "male"
            cnicDigits.length == 13 -> {
                val lastDigit = cnicDigits.last().digitToIntOrNull()
                if (lastDigit != null && lastDigit % 2 == 0) "female" else "male"
            }
            else -> "male"
        }
    }

    private fun extractNameFromOcr(frontText: String): String {
        val lines = frontText.lines().map { it.trim() }.filter { it.isNotBlank() }
        // Pass 1: Look for explicit "Name" label (handles inline, next-line, or +2 line past Urdu text)
        for (i in lines.indices) {
            val line = lines[i]
            val upper = line.uppercase(Locale.US)
            val isNameHeader = Regex("^N[A4]ME\\b.*").matches(upper) &&
                !upper.contains("FATHER") &&
                !upper.contains("HUSBAND")
            if (isNameHeader) {
                val inline = line.substringAfter(':', "").trim()
                if (inline.length >= 3 && isLikelyPersonName(inline)) {
                    return formatDisplayName(inline)
                }
                val afterWord = line.replace(Regex("(?i)^N[A4]ME\\b[:\\s-]*"), "").trim()
                if (afterWord.length >= 3 && isLikelyPersonName(afterWord)) {
                    return formatDisplayName(afterWord)
                }
                for (offset in 1..2) {
                    val candidate = lines.getOrNull(i + offset).orEmpty()
                    val candUpper = candidate.uppercase(Locale.US)
                    if (candUpper.contains("FATHER") || candUpper.contains("HUSBAND")) break
                    if (isLikelyPersonName(candidate)) {
                        return formatDisplayName(candidate)
                    }
                }
            }
        }
        // Pass 2: Fallback heuristic when ML Kit omits or misreads the "Name" label
        for (i in lines.indices) {
            val line = lines[i]
            val upper = line.uppercase(Locale.US)
            if (upper.contains("FATHER") || upper.contains("HUSBAND")) break
            if (isLikelyPersonName(line)) {
                val words = line.uppercase(Locale.US)
                    .split(Regex("[^A-Z]+"))
                    .filter { it.length >= 2 }
                if (words.size in 2..4 && words.all { it !in NON_NAME_WORDS }) {
                    return formatDisplayName(line)
                }
            }
        }
        return ""
    }

    private fun isLikelyPersonName(candidate: String): Boolean {
        if (candidate.any { it.isDigit() }) return false
        val words = candidate.uppercase(Locale.US)
            .split(Regex("[^A-Z]+"))
            .filter { it.length >= 2 }
        if (words.isEmpty() || words.size > 5) return false
        return words.any { it !in NON_NAME_WORDS }
    }

    private fun formatDisplayName(raw: String): String {
        return raw.replace(Regex("[^A-Za-z\\s.-]"), "")
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.lowercase(Locale.US).replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString()
                }
            }
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

    /**
     * Returns true when OCR strongly indicates the user is holding up the opposite side of the CNIC
     * (e.g., Back side shown during Front scan or Front side shown during Back scan).
     */
    fun isWrongCnicSide(expectedSide: String, rawText: String, hasFaceDetected: Boolean): Boolean {
        val upper = rawText.uppercase(Locale.US)
        val tokens = upper.split(Regex("[^A-Z0-9]+")).filter { it.length >= 3 }.toSet()
        return if (expectedSide.equals("Front", ignoreCase = true)) {
            val hasBackCues = tokens.contains("REGISTRAR") ||
                tokens.contains("ADDRESS") ||
                tokens.contains("CURRENT") ||
                tokens.contains("PERMANENT")
            val hasFrontIdentityCues = tokens.contains("NAME") ||
                tokens.contains("FATHER") ||
                tokens.contains("HUSBAND") ||
                tokens.contains("BIRTH")
            hasBackCues && !hasFrontIdentityCues && !hasFaceDetected
        } else {
            val hasFrontCues = (tokens.contains("FATHER") || tokens.contains("HUSBAND") || tokens.contains("BIRTH")) &&
                hasFaceDetected
            val hasBackCues = tokens.contains("ADDRESS") ||
                tokens.contains("REGISTRAR") ||
                tokens.contains("CURRENT") ||
                tokens.contains("PERMANENT")
            hasFrontCues && !hasBackCues
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

        val detectedCnics = extractAllCnicDigits(combinedText)
        val normalizedInputCnic = Formatters.sanitizeCnicDigits(cnicDigits)
        if (detectedCnics.isNotEmpty() && normalizedInputCnic.length == 13 && normalizedInputCnic !in detectedCnics) {
            val firstDetected = Formatters.formatCnicDisplay(detectedCnics.first())
            mismatches += "CNIC Number does not match the scanned CNIC card ($firstDetected). Check digits or re-scan."
        }

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
            if (matchedTokens < 1) {
                mismatches += "Full Legal Name does not match the name detected on the scanned CNIC card. Check spelling against your CNIC."
            }
        }

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

    fun buildMatchSummary(
        extracted: ExtractedCnicDetails,
        validation: CnicMatchValidationResult,
        faceDetectedOnFront: Boolean
    ): String {
        val parts = mutableListOf<String>()
        parts += if (faceDetectedOnFront) "front_face_ok" else "front_face_unconfirmed"
        if (extracted.cnicDigits.length == 13) parts += "ocr_cnic_found"
        if (extracted.fullName.isNotBlank()) parts += "ocr_name_found"
        parts += if (validation.isMatch) "match_passed" else "match_failed(${validation.mismatches.size})"
        return parts.joinToString("|")
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
