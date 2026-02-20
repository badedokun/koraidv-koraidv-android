package com.koraidv.sdk.processing

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Parsed MRZ data
 */
data class MrzData(
    val format: MrzFormat,
    val documentType: String,
    val issuingCountry: String,
    val lastName: String,
    val firstName: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: String,
    val sex: String,
    val expirationDate: String,
    val optionalData1: String? = null,
    val optionalData2: String? = null,
    val isValid: Boolean,
    val validationErrors: List<String>
)

/**
 * MRZ format type
 */
enum class MrzFormat {
    TD1,  // ID cards - 3 lines × 30 chars
    TD2,  // Some IDs - 2 lines × 36 chars
    TD3   // Passports - 2 lines × 44 chars
}

/**
 * MRZ Reader using ML Kit Text Recognition
 */
class MrzReader {

    private val textRecognizerDelegate = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val textRecognizer by textRecognizerDelegate

    /**
     * Read MRZ from bitmap
     */
    suspend fun readMrz(bitmap: Bitmap): MrzData? = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { text ->
                val mrzText = extractMrzText(text.text)
                val mrzData = parseMrz(mrzText)
                continuation.resume(mrzData)
            }
            .addOnFailureListener { e ->
                continuation.resume(null)
            }
    }

    /**
     * Release the underlying TextRecognizer. Safe to call even if readMrz was never called.
     */
    fun close() {
        if (textRecognizerDelegate.isInitialized()) {
            textRecognizer.close()
        }
    }

    /**
     * Extract MRZ text from OCR result
     */
    private fun extractMrzText(text: String): String {
        val lines = text.lines()
            .map { it.uppercase().replace(" ", "").filter { c -> c.isLetterOrDigit() || c == '<' } }
            .filter { it.length >= 20 && (it.contains("<") || looksLikeMrz(it)) }

        return lines.joinToString("")
    }

    private fun looksLikeMrz(text: String): Boolean {
        val mrzChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<".toSet()
        return text.all { it in mrzChars }
    }

    /**
     * Parse MRZ text
     */
    internal fun parseMrz(text: String): MrzData? {
        // Only strip whitespace and non-MRZ characters — do NOT replace O→0 globally
        // as that corrupts country codes (GBR→GB0) and names (OLIVER→0LIVER).
        // OCR O/0 confusion is handled per-field in digit-only positions during parsing.
        val cleaned = text
            .replace(" ", "")
            .replace("\n", "")
            .filter { it.isLetterOrDigit() || it == '<' }

        val format = detectFormat(cleaned) ?: return null

        return when (format) {
            MrzFormat.TD1 -> parseTd1(cleaned)
            MrzFormat.TD2 -> parseTd2(cleaned)
            MrzFormat.TD3 -> parseTd3(cleaned)
        }
    }

    /**
     * Fix OCR O/0 confusion in digit-only fields (document numbers, dates, check digits).
     * Only replaces O→0 in positions where digits are expected.
     */
    internal fun fixDigitField(field: String): String {
        return field.replace('O', '0').replace('o', '0')
    }

    internal fun detectFormat(text: String): MrzFormat? {
        val length = text.length
        // TD3 (passports): 2 lines × 44 = 88 chars. Check first — TD1 range must not overlap.
        // TD1 (ID cards):  3 lines × 30 = 90 chars.
        // TD2 (some IDs):  2 lines × 36 = 72 chars.
        // Use structural hints (line 1 prefix) to disambiguate overlapping lengths.
        return when {
            length in 70..74 -> MrzFormat.TD2
            length in 86..92 -> {
                // Disambiguate TD1 vs TD3 using document type prefix:
                // TD3 passports start with "P<" or "P" followed by issuing country
                // TD1 ID cards start with "I", "A", or "C"
                val prefix = text.substring(0, 1)
                if (prefix == "P" || (length == 88 && text.getOrNull(1) == '<')) {
                    MrzFormat.TD3
                } else if (length >= 90) {
                    MrzFormat.TD1
                } else {
                    // For ambiguous lengths (88-89), default to TD3 for passport-like prefixes
                    MrzFormat.TD3
                }
            }
            else -> null
        }
    }

    private fun parseTd1(text: String): MrzData? {
        if (text.length < 90) return null

        val validationErrors = mutableListOf<String>()

        // Line 1 (chars 0-29)
        val documentType = text.substring(0, 2).replace("<", "")
        val issuingCountry = text.substring(2, 5)
        val documentNumber = fixDigitField(text.substring(5, 14).replace("<", ""))
        val docNumCheck = fixDigitField(text[14].toString())
        val optionalData1 = text.substring(15, 30).replace("<", "").takeIf { it.isNotEmpty() }

        // Line 2 (chars 30-59)
        val dateOfBirth = fixDigitField(text.substring(30, 36))
        val dobCheck = fixDigitField(text[36].toString())
        val sex = text[37].toString()
        val expirationDate = fixDigitField(text.substring(38, 44))
        val expCheck = fixDigitField(text[44].toString())
        val nationality = text.substring(45, 48)
        val optionalData2 = text.substring(48, 59).replace("<", "").takeIf { it.isNotEmpty() }

        // Line 3 (chars 60-89) - Name
        val nameParts = parseName(text.substring(60, 90))

        // Validate check digits
        if (!validateCheckDigit(documentNumber, docNumCheck)) {
            validationErrors.add("Invalid document number check digit")
        }
        if (!validateCheckDigit(dateOfBirth, dobCheck)) {
            validationErrors.add("Invalid date of birth check digit")
        }
        if (!validateCheckDigit(expirationDate, expCheck)) {
            validationErrors.add("Invalid expiration date check digit")
        }

        return MrzData(
            format = MrzFormat.TD1,
            documentType = documentType,
            issuingCountry = issuingCountry,
            lastName = nameParts.first,
            firstName = nameParts.second,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = dateOfBirth,
            sex = sex,
            expirationDate = expirationDate,
            optionalData1 = optionalData1,
            optionalData2 = optionalData2,
            isValid = validationErrors.isEmpty(),
            validationErrors = validationErrors
        )
    }

    private fun parseTd2(text: String): MrzData? {
        if (text.length < 72) return null

        val validationErrors = mutableListOf<String>()

        // Line 1 (chars 0-35)
        val documentType = text.substring(0, 2).replace("<", "")
        val issuingCountry = text.substring(2, 5)
        val nameParts = parseName(text.substring(5, 36))

        // Line 2 (chars 36-71)
        val documentNumber = fixDigitField(text.substring(36, 45).replace("<", ""))
        val docNumCheck = fixDigitField(text[45].toString())
        val nationality = text.substring(46, 49)
        val dateOfBirth = fixDigitField(text.substring(49, 55))
        val dobCheck = fixDigitField(text[55].toString())
        val sex = text[56].toString()
        val expirationDate = fixDigitField(text.substring(57, 63))
        val expCheck = fixDigitField(text[63].toString())
        val optionalData1 = text.substring(64, 71).replace("<", "").takeIf { it.isNotEmpty() }

        // Validate check digits
        if (!validateCheckDigit(documentNumber, docNumCheck)) {
            validationErrors.add("Invalid document number check digit")
        }
        if (!validateCheckDigit(dateOfBirth, dobCheck)) {
            validationErrors.add("Invalid date of birth check digit")
        }
        if (!validateCheckDigit(expirationDate, expCheck)) {
            validationErrors.add("Invalid expiration date check digit")
        }

        return MrzData(
            format = MrzFormat.TD2,
            documentType = documentType,
            issuingCountry = issuingCountry,
            lastName = nameParts.first,
            firstName = nameParts.second,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = dateOfBirth,
            sex = sex,
            expirationDate = expirationDate,
            optionalData1 = optionalData1,
            isValid = validationErrors.isEmpty(),
            validationErrors = validationErrors
        )
    }

    private fun parseTd3(text: String): MrzData? {
        if (text.length < 88) return null

        val validationErrors = mutableListOf<String>()

        // Line 1 (chars 0-43)
        val documentType = text.substring(0, 2).replace("<", "")
        val issuingCountry = text.substring(2, 5)
        val nameParts = parseName(text.substring(5, 44))

        // Line 2 (chars 44-87)
        val documentNumber = fixDigitField(text.substring(44, 53).replace("<", ""))
        val docNumCheck = fixDigitField(text[53].toString())
        val nationality = text.substring(54, 57)
        val dateOfBirth = fixDigitField(text.substring(57, 63))
        val dobCheck = fixDigitField(text[63].toString())
        val sex = text[64].toString()
        val expirationDate = fixDigitField(text.substring(65, 71))
        val expCheck = fixDigitField(text[71].toString())
        val optionalData1 = text.substring(72, 86).replace("<", "").takeIf { it.isNotEmpty() }

        // Validate check digits
        if (!validateCheckDigit(documentNumber, docNumCheck)) {
            validationErrors.add("Invalid document number check digit")
        }
        if (!validateCheckDigit(dateOfBirth, dobCheck)) {
            validationErrors.add("Invalid date of birth check digit")
        }
        if (!validateCheckDigit(expirationDate, expCheck)) {
            validationErrors.add("Invalid expiration date check digit")
        }

        return MrzData(
            format = MrzFormat.TD3,
            documentType = documentType,
            issuingCountry = issuingCountry,
            lastName = nameParts.first,
            firstName = nameParts.second,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = dateOfBirth,
            sex = sex,
            expirationDate = expirationDate,
            optionalData1 = optionalData1,
            isValid = validationErrors.isEmpty(),
            validationErrors = validationErrors
        )
    }

    internal fun parseName(nameField: String): Pair<String, String> {
        val parts = nameField.split("<<")
        val lastName = parts.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
        val firstName = parts.getOrNull(1)?.replace("<", " ")?.trim() ?: ""
        return Pair(lastName, firstName)
    }

    internal fun validateCheckDigit(data: String, checkDigit: String): Boolean {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0

        for ((index, char) in data.withIndex()) {
            val value = when {
                char.isDigit() -> char.digitToInt()
                char.isLetter() -> char.code - 55 // A=10, B=11, etc.
                char == '<' -> 0
                else -> return false
            }
            sum += value * weights[index % 3]
        }

        val expected = sum % 10
        val actual = if (checkDigit == "<") 0 else checkDigit.toIntOrNull() ?: -1

        return expected == actual
    }

    companion object {
        /**
         * Format date from YYMMDD to human readable.
         *
         * @param yymmdd 6-digit date string from MRZ
         * @param isExpiration true for expiration dates (pivot: current year + 10),
         *                     false for birth dates (pivot: current year)
         */
        fun formatDate(yymmdd: String, isExpiration: Boolean = false): String? {
            if (yymmdd.length != 6) return null

            val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
            val mm = yymmdd.substring(2, 4)
            val dd = yymmdd.substring(4, 6)

            // Context-aware pivot: expiration dates can be up to ~10 years in the future,
            // birth dates should be in the past. Using the current year avoids the
            // hard-coded pivot problem (e.g., 2031 expiration dates parsed as 1931).
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val currentYY = currentYear % 100
            val pivot = if (isExpiration) {
                // Expiration dates: assume within the next 50 years
                (currentYY + 50) % 100
            } else {
                // Birth dates: assume within the last 100 years
                currentYY
            }

            val year = if (yy <= pivot) 2000 + yy else 1900 + yy

            return "$year-$mm-$dd"
        }
    }
}
