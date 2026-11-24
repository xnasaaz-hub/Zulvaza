package com.example

import com.example.util.CnicMatchValidator
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun cnicFormattingAndExtraction_workCorrectly() {
        val rawText = """
            PAKISTAN NATIONAL IDENTITY CARD
            Name: Muhammad Ali Khan
            Identity Number 35202-1234567-1
            Date of Issue 12.05.2020
            Date of Expiry 12.05.2030
        """.trimIndent()

        assertEquals("3520212345671", CnicMatchValidator.extractCnicDigits(rawText))
        val dates = CnicMatchValidator.extractIsoDates(rawText)
        assertTrue(dates.contains("2020-05-12"))
        assertTrue(dates.contains("2030-05-12"))
    }

    @Test
    fun cnicPlausibility_rejectsBlankWallAndAcceptsRealCardText() {
        assertFalse(CnicMatchValidator.isPlausibleCnicFrame("Front", "", 0))
        assertFalse(CnicMatchValidator.isPlausibleCnicFrame("Front", "hello world", 1))
        assertTrue(
            CnicMatchValidator.isPlausibleCnicFrame(
                "Front",
                "ISLAMIC REPUBLIC OF PAKISTAN\n35202-1234567-1\nName Ali Raza",
                3
            )
        )
        assertTrue(
            CnicMatchValidator.isPlausibleCnicFrame(
                "Back",
                "Current Address Lahore\nRegistrar General of Pakistan\n35202-1234567-1",
                3
            )
        )
    }

    @Test
    fun cnicValidation_blocksMismatchingDetailsAndPassesMatchingDetails() {
        val frontText = "ISLAMIC REPUBLIC OF PAKISTAN Name: Ali Raza Identity Number 35202-1234567-1 Date of Birth 01/01/1995 Date of Issue 10/04/2021 Date of Expiry 10/04/2031"
        val backText = "Permanent Address House 12 Lahore 35202-1234567-1"

        val validResult = CnicMatchValidator.validateAgainstEnteredDetails(
            fullName = "Ali Raza",
            cnicDigits = "35202-1234567-1",
            issueDateIso = "2021-04-10",
            expiryDateIso = "2031-04-10",
            frontText = frontText,
            backText = backText
        )
        assertTrue(validResult.isMatch)
        assertTrue(validResult.mismatches.isEmpty())

        val mismatchResult = CnicMatchValidator.validateAgainstEnteredDetails(
            fullName = "Usman Tariq",
            cnicDigits = "42101-9999999-9",
            issueDateIso = "2022-01-01",
            expiryDateIso = "2035-01-01",
            frontText = frontText,
            backText = backText
        )
        assertFalse(mismatchResult.isMatch)
        assertTrue(mismatchResult.mismatches.any { it.contains("CNIC Number") })
        assertTrue(mismatchResult.mismatches.any { it.contains("Full Legal Name") })
        assertTrue(mismatchResult.mismatches.any { it.contains("Issue Date") || it.contains("Expiry Date") })
    }

    @Test
    fun formatCnicAndPkr_workCorrectly() {
        assertEquals("35202-1234567-1", Formatters.formatCnicDisplay("3520212345671"))
        assertTrue(Formatters.formatPkr(500.0).contains("500"))
    }

    @Test
    fun cnicDetailsExtraction_populatesNameDobIssueExpiryAndGender() {
        val frontText = """
            ISLAMIC REPUBLIC OF PAKISTAN
            National Identity Card
            Name: Ayesha Siddiqui
            Gender: Female
            Identity Number: 35202-7654321-2
            Date of Birth: 14.08.1997
            Date of Issue: 05.03.2020
            Date of Expiry: 05.03.2030
        """.trimIndent()
        val backText = "Permanent Address: Gulberg III, Lahore"

        val extracted = CnicMatchValidator.extractCnicDetails(frontText, backText)
        assertEquals("Ayesha Siddiqui", extracted.fullName)
        assertEquals("3520276543212", extracted.cnicDigits)
        assertEquals("1997-08-14", extracted.dateOfBirth)
        assertEquals("2020-03-05", extracted.issueDate)
        assertEquals("2030-03-05", extracted.expiryDate)
        assertEquals("female", extracted.gender)
    }

    @Test
    fun whatsappPrefixStrippingAndE164_workCorrectly() {
        assertEquals("3001234567", Formatters.stripPakistanPrefix("+923001234567"))
        assertEquals("3001234567", Formatters.stripPakistanPrefix("03001234567"))
        assertTrue(Formatters.isValidPakistanE164("+92" + Formatters.stripPakistanPrefix("03001234567")))
    }
}
