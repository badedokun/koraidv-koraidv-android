package com.koraidv.sdk.processing

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MrzReaderTest {

    private lateinit var reader: MrzReader

    @Before
    fun setUp() {
        reader = MrzReader()
    }

    // =====================================================================
    // detectFormat
    // =====================================================================

    @Test
    fun `detectFormat returns TD2 for 72-char input`() {
        val text = "I<UTOD231458907<<<<<<<<<<<<<<<" +
                "7408122F1204159UTO<<<<<<<<<<<6" +
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<<<"
        // TD2 = 72 chars
        val td2Input = text.take(72)
        assertThat(reader.detectFormat(td2Input)).isEqualTo(MrzFormat.TD2)
    }

    @Test
    fun `detectFormat returns TD3 for 88-char passport starting with P`() {
        val text = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<" +
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        assertThat(text.length).isEqualTo(88)
        assertThat(reader.detectFormat(text)).isEqualTo(MrzFormat.TD3)
    }

    @Test
    fun `detectFormat returns TD1 for 90-char input starting with I`() {
        // TD1: 3 lines x 30 chars = 90
        val text = "I<UTOD231458907<<<<<<<<<<<<<<<" + // 30
                "7408122F1204159UTO<<<<<<<<<<<6" +     // 30
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"       // 30 = 90 total
        assertThat(text.length).isEqualTo(90)
        assertThat(reader.detectFormat(text)).isEqualTo(MrzFormat.TD1)
    }

    @Test
    fun `detectFormat returns null for short text`() {
        assertThat(reader.detectFormat("ABC")).isNull()
    }

    @Test
    fun `detectFormat returns null for 50-char text`() {
        assertThat(reader.detectFormat("A".repeat(50))).isNull()
    }

    // =====================================================================
    // fixDigitField
    // =====================================================================

    @Test
    fun `fixDigitField replaces O with 0`() {
        assertThat(reader.fixDigitField("12O456")).isEqualTo("120456")
    }

    @Test
    fun `fixDigitField replaces lowercase o with 0`() {
        assertThat(reader.fixDigitField("12o456")).isEqualTo("120456")
    }

    @Test
    fun `fixDigitField leaves digits unchanged`() {
        assertThat(reader.fixDigitField("123456")).isEqualTo("123456")
    }

    @Test
    fun `fixDigitField handles empty string`() {
        assertThat(reader.fixDigitField("")).isEqualTo("")
    }

    @Test
    fun `fixDigitField replaces multiple O chars`() {
        assertThat(reader.fixDigitField("OOO123")).isEqualTo("000123")
    }

    // =====================================================================
    // parseName
    // =====================================================================

    @Test
    fun `parseName splits last and first names`() {
        val result = reader.parseName("ERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<")
        assertThat(result.first).isEqualTo("ERIKSSON")
        assertThat(result.second).isEqualTo("ANNA MARIA")
    }

    @Test
    fun `parseName handles single name`() {
        val result = reader.parseName("SMITH<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
        assertThat(result.first).isEqualTo("SMITH")
        assertThat(result.second).isEmpty()
    }

    @Test
    fun `parseName handles double-barrelled last name`() {
        val result = reader.parseName("VAN<DEN<BERG<<JOHANNES<<<<<<<<<<<<<<<<")
        assertThat(result.first).isEqualTo("VAN DEN BERG")
        assertThat(result.second).isEqualTo("JOHANNES")
    }

    // =====================================================================
    // validateCheckDigit
    // =====================================================================

    @Test
    fun `validateCheckDigit correct for passport number L898902C3`() {
        // Known ICAO test: L898902C3 → check digit 6
        assertThat(reader.validateCheckDigit("L898902C3", "6")).isTrue()
    }

    @Test
    fun `validateCheckDigit correct for date 740812`() {
        // Known ICAO test: 740812 → check digit 2
        assertThat(reader.validateCheckDigit("740812", "2")).isTrue()
    }

    @Test
    fun `validateCheckDigit returns false for wrong digit`() {
        assertThat(reader.validateCheckDigit("740812", "5")).isFalse()
    }

    @Test
    fun `validateCheckDigit handles filler as zero`() {
        assertThat(reader.validateCheckDigit("<<<<<<", "<")).isTrue()
    }

    // =====================================================================
    // parseMrz - TD3 Passport
    // =====================================================================

    @Test
    fun `parseMrz parses valid TD3 passport`() {
        val line1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        val line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        val mrz = line1 + line2
        assertThat(mrz.length).isEqualTo(88)

        val result = reader.parseMrz(mrz)

        assertThat(result).isNotNull()
        assertThat(result!!.format).isEqualTo(MrzFormat.TD3)
        assertThat(result.documentType).isEqualTo("P")
        assertThat(result.issuingCountry).isEqualTo("UTO")
        assertThat(result.lastName).isEqualTo("ERIKSSON")
        assertThat(result.firstName).isEqualTo("ANNA MARIA")
        assertThat(result.documentNumber).isEqualTo("L898902C3")
        assertThat(result.nationality).isEqualTo("UTO")
        assertThat(result.dateOfBirth).isEqualTo("740812")
        assertThat(result.sex).isEqualTo("F")
        assertThat(result.expirationDate).isEqualTo("120415")
    }

    @Test
    fun `parseMrz returns null for too-short text`() {
        assertThat(reader.parseMrz("P<UTO")).isNull()
    }

    @Test
    fun `parseMrz returns null for empty string`() {
        assertThat(reader.parseMrz("")).isNull()
    }

    // =====================================================================
    // parseMrz - TD1 ID Card
    // =====================================================================

    @Test
    fun `parseMrz parses valid TD1 ID card`() {
        val line1 = "I<UTOD231458907<<<<<<<<<<<<<<"
        val line2 = "7408122F1204159UTO<<<<<<<<<<<6"
        val line3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<"
        // Pad to 90 characters
        val mrz = (line1 + line2 + line3).padEnd(90, '<')

        val result = reader.parseMrz(mrz)

        assertThat(result).isNotNull()
        assertThat(result!!.format).isEqualTo(MrzFormat.TD1)
        assertThat(result.issuingCountry).isEqualTo("UTO")
    }

    // =====================================================================
    // parseMrz - OCR O/0 confusion
    // =====================================================================

    @Test
    fun `parseMrz does not corrupt country codes with O-to-0 replacement`() {
        // Passport with GBR country code — O→0 replacement should NOT touch "GBR"
        val line1 = "P<GBRSMITH<<OLIVER<<<<<<<<<<<<<<<<<<<<<<<<<<"
        val line2 = "1234567896GBR8501011M2301012<<<<<<<<<<<<<<06"
        val mrz = line1 + line2
        assertThat(mrz.length).isEqualTo(88)

        val result = reader.parseMrz(mrz)

        assertThat(result).isNotNull()
        assertThat(result!!.issuingCountry).isEqualTo("GBR")
        assertThat(result.nationality).isEqualTo("GBR")
        assertThat(result.firstName).isEqualTo("OLIVER")
    }

    @Test
    fun `fixDigitField fixes O in document number`() {
        // Document number with OCR confusion: "12345678O" → "123456780"
        assertThat(reader.fixDigitField("12345678O")).isEqualTo("123456780")
    }

    // =====================================================================
    // formatDate
    // =====================================================================

    @Test
    fun `formatDate converts YYMMDD to year-MM-DD for 2000s`() {
        assertThat(MrzReader.formatDate("250101")).isEqualTo("2025-01-01")
    }

    @Test
    fun `formatDate converts YYMMDD to year-MM-DD for 1900s`() {
        assertThat(MrzReader.formatDate("850612")).isEqualTo("1985-06-12")
    }

    @Test
    fun `formatDate returns null for invalid input`() {
        assertThat(MrzReader.formatDate("abc")).isNull()
    }

    @Test
    fun `formatDate returns null for empty string`() {
        assertThat(MrzReader.formatDate("")).isNull()
    }

    @Test
    fun `formatDate boundary year 30 maps to 2030`() {
        assertThat(MrzReader.formatDate("300101")).isEqualTo("2030-01-01")
    }

    @Test
    fun `formatDate boundary year 31 maps to 1931`() {
        assertThat(MrzReader.formatDate("310101")).isEqualTo("1931-01-01")
    }
}
