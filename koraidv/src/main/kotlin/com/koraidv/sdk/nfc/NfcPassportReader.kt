package com.koraidv.sdk.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Data extracted from an ePassport NFC chip via ICAO 9303 protocols.
 *
 * @property documentNumber Document number from DG1 MRZ
 * @property firstName First (given) name from DG1 MRZ
 * @property lastName Last (surname) from DG1 MRZ
 * @property dateOfBirth Date of birth (YYMMDD) from DG1 MRZ
 * @property expirationDate Expiration date (YYMMDD) from DG1 MRZ
 * @property nationality 3-letter nationality code from DG1 MRZ
 * @property faceImageData JPEG or JPEG2000 face photo extracted from DG2 (may be null)
 * @property passiveAuthPassed True if DG hashes match the values in SOD
 * @property activeAuthPassed True if Active Authentication succeeded (null if not attempted)
 */
data class NfcPassportData(
    val documentNumber: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val expirationDate: String,
    val nationality: String,
    val faceImageData: ByteArray? = null,
    val passiveAuthPassed: Boolean,
    val activeAuthPassed: Boolean? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcPassportData) return false
        return documentNumber == other.documentNumber &&
            firstName == other.firstName &&
            lastName == other.lastName &&
            dateOfBirth == other.dateOfBirth &&
            expirationDate == other.expirationDate &&
            nationality == other.nationality &&
            faceImageData.contentEquals(other.faceImageData) &&
            passiveAuthPassed == other.passiveAuthPassed &&
            activeAuthPassed == other.activeAuthPassed
    }

    override fun hashCode(): Int {
        var result = documentNumber.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + expirationDate.hashCode()
        result = 31 * result + nationality.hashCode()
        result = 31 * result + (faceImageData?.contentHashCode() ?: 0)
        result = 31 * result + passiveAuthPassed.hashCode()
        result = 31 * result + (activeAuthPassed?.hashCode() ?: 0)
        return result
    }
}

/**
 * BAC (Basic Access Control) key material derived from MRZ data.
 *
 * The BAC key is computed from the MRZ-printed document number, date of birth,
 * and date of expiry (all including their check digits).
 *
 * @property documentNumber Document number from MRZ (up to 9 characters)
 * @property dateOfBirth Date of birth from MRZ in YYMMDD format
 * @property dateOfExpiry Date of expiry from MRZ in YYMMDD format
 */
data class BACKey(
    val documentNumber: String,
    val dateOfBirth: String,
    val dateOfExpiry: String
) {
    /**
     * Compute the MRZ information string used for key derivation.
     *
     * Format: documentNumber + checkDigit + dateOfBirth + checkDigit + dateOfExpiry + checkDigit
     */
    fun computeMrzInfo(): String {
        val docNumPadded = documentNumber.padEnd(9, '<')
        val docNumCheck = CryptoUtils.computeCheckDigit(docNumPadded)
        val dobCheck = CryptoUtils.computeCheckDigit(dateOfBirth)
        val expCheck = CryptoUtils.computeCheckDigit(dateOfExpiry)
        return "$docNumPadded$docNumCheck$dateOfBirth$dobCheck$dateOfExpiry$expCheck"
    }
}

/**
 * ICAO 9303 ePassport NFC chip reader.
 *
 * Implements the BAC (Basic Access Control) protocol and reads data groups
 * from eMRTD chips using Android's [IsoDep] NFC interface.
 *
 * Protocol flow:
 * 1. SELECT eMRTD application (AID: A0000002471001)
 * 2. Perform BAC mutual authentication
 * 3. Read DG1 (MRZ data), DG2 (face photo), SOD (security object)
 * 4. Verify passive authentication (hash integrity)
 *
 * @param isoDep The connected IsoDep tag
 * @param bacKey BAC key derived from MRZ data
 */
class NfcPassportReader(
    private val isoDep: IsoDep,
    private val bacKey: BACKey
) {
    private var secureMessaging: SecureMessaging? = null
    private var maxTransceiveLength: Int = 256

    /**
     * Read passport data from the NFC chip.
     *
     * Performs BAC authentication, reads DG1/DG2/SOD, and verifies
     * passive authentication hashes.
     *
     * @param onProgress Callback for progress updates (step description, 0.0-1.0)
     * @return [NfcPassportData] with extracted data and authentication results
     * @throws NfcReadException if any step fails
     */
    suspend fun readPassport(
        onProgress: ((String, Float) -> Unit)? = null
    ): NfcPassportData {
        try {
            maxTransceiveLength = isoDep.maxTransceiveLength

            // Step 1: Select eMRTD application
            onProgress?.invoke("Selecting ePassport application", 0.05f)
            selectApplication()

            // Step 2: Perform BAC
            onProgress?.invoke("Authenticating with chip", 0.15f)
            performBac()

            // Step 3: Read DG1 (MRZ data)
            onProgress?.invoke("Reading document data", 0.30f)
            val dg1Data = readDataGroup(DG1_FILE_ID)

            // Step 4: Read DG2 (face image)
            onProgress?.invoke("Reading face photo", 0.50f)
            val dg2Data = readDataGroup(DG2_FILE_ID)

            // Step 5: Read SOD (security object)
            onProgress?.invoke("Reading security data", 0.75f)
            val sodData = try {
                readDataGroup(SOD_FILE_ID)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read SOD: ${e.message}")
                null
            }

            // Step 6: Parse DG1
            onProgress?.invoke("Parsing document data", 0.85f)
            val mrzInfo = parseDg1(dg1Data)

            // Step 7: Extract face image from DG2
            val faceImage = try {
                extractFaceImage(dg2Data)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract face image: ${e.message}")
                null
            }

            // Step 8: Passive authentication
            onProgress?.invoke("Verifying chip authenticity", 0.95f)
            val passiveAuthResult = if (sodData != null) {
                verifyPassiveAuth(dg1Data, dg2Data, sodData)
            } else {
                false
            }

            onProgress?.invoke("Complete", 1.0f)

            return NfcPassportData(
                documentNumber = mrzInfo.documentNumber,
                firstName = mrzInfo.firstName,
                lastName = mrzInfo.lastName,
                dateOfBirth = mrzInfo.dateOfBirth,
                expirationDate = mrzInfo.expirationDate,
                nationality = mrzInfo.nationality,
                faceImageData = faceImage,
                passiveAuthPassed = passiveAuthResult,
                activeAuthPassed = null
            )
        } catch (e: NfcReadException) {
            throw e
        } catch (e: Exception) {
            throw NfcReadException("Failed to read passport: ${e.message}", e)
        }
    }

    // ===== SELECT eMRTD Application =====

    /**
     * SELECT the eMRTD application on the chip.
     * AID: A0 00 00 02 47 10 01
     */
    private fun selectApplication() {
        val aid = byteArrayOf(
            0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01
        )
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xA4, // SELECT
            p1 = 0x04,  // Select by DF name
            p2 = 0x0C,  // No FCI returned
            data = aid
        )
        val response = transceive(command)
        if (!response.isSuccess) {
            throw NfcReadException(
                "Failed to select eMRTD application: SW=${String.format("%04X", response.statusWord)}"
            )
        }
    }

    // ===== BAC (Basic Access Control) =====

    /**
     * Perform BAC mutual authentication per ICAO 9303 Part 11, Section 4.3.
     *
     * Steps:
     * 1. Derive K_enc and K_mac from MRZ info
     * 2. GET CHALLENGE to receive RND.IC from the chip
     * 3. Generate RND.IFD and K.IFD
     * 4. Compute and send EXTERNAL AUTHENTICATE
     * 5. Verify chip's response and establish session keys
     */
    private fun performBac() {
        // Derive BAC keys from MRZ
        val mrzInfo = bacKey.computeMrzInfo()
        val kSeed = CryptoUtils.computeKSeed(mrzInfo)
        val kEnc = CryptoUtils.deriveKey(kSeed, 1)
        val kMac = CryptoUtils.deriveKey(kSeed, 2)

        // GET CHALLENGE - receive 8-byte RND.IC
        val challengeCmd = ApduCommand(
            cla = 0x00,
            ins = 0x84, // GET CHALLENGE
            p1 = 0x00,
            p2 = 0x00,
            le = 8
        )
        val challengeResponse = transceive(challengeCmd)
        if (!challengeResponse.isSuccess || challengeResponse.data.size != 8) {
            throw NfcReadException("GET CHALLENGE failed: SW=${String.format("%04X", challengeResponse.statusWord)}")
        }
        val rndIc = challengeResponse.data

        // Generate RND.IFD (8 bytes) and K.IFD (16 bytes)
        val rndIfd = CryptoUtils.generateRandom(8)
        val kIfd = CryptoUtils.generateRandom(16)

        // Build S = RND.IFD || RND.IC || K.IFD
        val s = rndIfd + rndIc + kIfd

        // Encrypt S with K_enc
        val eifd = CryptoUtils.encryptDes3Cbc(kEnc, s, ByteArray(8))

        // Compute MAC over encrypted data
        val paddedEifd = SecureMessaging.pad(eifd)
        val mifd = CryptoUtils.computeRetailMac(kMac, paddedEifd)

        // EXTERNAL AUTHENTICATE
        val authData = eifd + mifd
        val authCmd = ApduCommand(
            cla = 0x00,
            ins = 0x82, // EXTERNAL AUTHENTICATE
            p1 = 0x00,
            p2 = 0x00,
            data = authData,
            le = 40 // Expect 32 bytes encrypted + 8 bytes MAC
        )
        val authResponse = transceive(authCmd)
        if (!authResponse.isSuccess || authResponse.data.size != 40) {
            throw NfcReadException(
                "EXTERNAL AUTHENTICATE failed: SW=${String.format("%04X", authResponse.statusWord)}, " +
                    "data length=${authResponse.data.size}"
            )
        }

        // Verify chip's response
        val eic = authResponse.data.copyOfRange(0, 32)
        val mic = authResponse.data.copyOfRange(32, 40)

        // Verify MAC
        val paddedEic = SecureMessaging.pad(eic)
        val expectedMic = CryptoUtils.computeRetailMac(kMac, paddedEic)
        if (!mic.contentEquals(expectedMic)) {
            throw NfcReadException("BAC: Chip response MAC verification failed")
        }

        // Decrypt chip's response
        val decrypted = CryptoUtils.decryptDes3Cbc(kEnc, eic, ByteArray(8))

        // Response should be: RND.IC || RND.IFD || K.IC (32 bytes)
        val rndIcResponse = decrypted.copyOfRange(0, 8)
        val rndIfdResponse = decrypted.copyOfRange(8, 16)
        val kIc = decrypted.copyOfRange(16, 32)

        // Verify RND.IFD matches what we sent
        if (!rndIfdResponse.contentEquals(rndIfd)) {
            throw NfcReadException("BAC: RND.IFD mismatch in chip response")
        }

        // Derive session keys from K_seed = K.IFD XOR K.IC
        val sessionKSeed = ByteArray(16)
        for (i in 0 until 16) {
            sessionKSeed[i] = (kIfd[i].toInt() xor kIc[i].toInt()).toByte()
        }

        val ksEnc = CryptoUtils.deriveKey(sessionKSeed, 1)
        val ksMac = CryptoUtils.deriveKey(sessionKSeed, 2)

        // Compute initial SSC = RND.IC[last 4] || RND.IFD[last 4]
        val ssc = rndIc.copyOfRange(4, 8) + rndIfd.copyOfRange(4, 8)

        // Establish secure messaging channel
        secureMessaging = SecureMessaging(ksEnc, ksMac, ssc)

        Log.d(TAG, "BAC authentication successful")
    }

    // ===== Data Group Reading =====

    /**
     * Read a data group file from the chip.
     *
     * Selects the file by EF (Elementary File) ID, then reads the complete
     * content using READ BINARY commands. Handles chunked reading for data
     * groups larger than the transceive buffer.
     *
     * @param fileId 2-byte file identifier (e.g., 0x0101 for DG1)
     * @return Complete file content
     */
    private fun readDataGroup(fileId: Int): ByteArray {
        // SELECT file
        val fileIdBytes = byteArrayOf(
            ((fileId shr 8) and 0xFF).toByte(),
            (fileId and 0xFF).toByte()
        )
        val selectCmd = ApduCommand(
            cla = 0x00,
            ins = 0xA4, // SELECT
            p1 = 0x02,  // Select EF by file ID
            p2 = 0x0C,  // No FCI
            data = fileIdBytes
        )
        val selectResponse = transceiveSecure(selectCmd)
        if (!selectResponse.isSuccess) {
            throw NfcReadException(
                "Failed to select file ${String.format("%04X", fileId)}: " +
                    "SW=${String.format("%04X", selectResponse.statusWord)}"
            )
        }

        // Read file header to determine total length
        val headerBytes = readBinary(0, 4)
        if (headerBytes.size < 2) {
            throw NfcReadException("Failed to read file header")
        }

        // Parse TLV header to get total length
        val totalLength = parseTlvFileLength(headerBytes)
        val headerSize = computeHeaderSize(headerBytes)

        // Read complete file
        val fileData = ByteArrayOutputStream()
        fileData.write(headerBytes)

        var offset = headerBytes.size
        val totalFileSize = headerSize + totalLength

        while (offset < totalFileSize) {
            val remaining = totalFileSize - offset
            // Read in chunks, respecting max transceive length
            // Leave room for secure messaging overhead (typically ~30 bytes)
            val chunkSize = minOf(remaining, maxOf(maxTransceiveLength - 60, 128))
            val chunk = readBinary(offset, chunkSize)
            if (chunk.isEmpty()) break
            fileData.write(chunk)
            offset += chunk.size
        }

        return fileData.toByteArray()
    }

    /**
     * READ BINARY command to read bytes from the currently selected file.
     *
     * @param offset Byte offset within the file
     * @param length Number of bytes to read
     * @return Read data bytes
     */
    private fun readBinary(offset: Int, length: Int): ByteArray {
        val p1: Int
        val p2: Int

        if (offset <= 0x7FFF) {
            // Short EF offset encoding
            p1 = (offset shr 8) and 0x7F
            p2 = offset and 0xFF
        } else {
            // For very large offsets, use odd INS READ BINARY with offset DO
            // This is rare for DG1 but may be needed for large DG2
            p1 = (offset shr 8) and 0x7F
            p2 = offset and 0xFF
        }

        val le = if (length > 256) 256 else length
        val readCmd = ApduCommand(
            cla = 0x00,
            ins = 0xB0, // READ BINARY
            p1 = p1,
            p2 = p2,
            le = le
        )

        val response = transceiveSecure(readCmd)
        if (!response.isSuccess && !response.hasMoreData) {
            // SW 6282 means end of file reached (still has data)
            if (response.statusWord == 0x6282) {
                return response.data
            }
            throw NfcReadException(
                "READ BINARY failed at offset $offset: SW=${String.format("%04X", response.statusWord)}"
            )
        }

        return response.data
    }

    /**
     * Parse the length field from a TLV file header.
     */
    private fun parseTlvFileLength(header: ByteArray): Int {
        if (header.size < 2) return 0

        // Skip tag byte(s)
        var offset = 1
        if ((header[0].toInt() and 0x1F) == 0x1F) {
            // Multi-byte tag
            while (offset < header.size && (header[offset].toInt() and 0x80) != 0) {
                offset++
            }
            offset++ // Skip last tag byte
        }

        if (offset >= header.size) return 0

        val firstLenByte = header[offset].toInt() and 0xFF
        return when {
            firstLenByte < 0x80 -> firstLenByte
            firstLenByte == 0x81 && offset + 1 < header.size -> {
                header[offset + 1].toInt() and 0xFF
            }
            firstLenByte == 0x82 && offset + 2 < header.size -> {
                ((header[offset + 1].toInt() and 0xFF) shl 8) or
                    (header[offset + 2].toInt() and 0xFF)
            }
            firstLenByte == 0x83 && offset + 3 < header.size -> {
                ((header[offset + 1].toInt() and 0xFF) shl 16) or
                    ((header[offset + 2].toInt() and 0xFF) shl 8) or
                    (header[offset + 3].toInt() and 0xFF)
            }
            else -> 0
        }
    }

    /**
     * Compute total header size (tag + length field bytes).
     */
    private fun computeHeaderSize(header: ByteArray): Int {
        if (header.size < 2) return header.size

        var offset = 1
        if ((header[0].toInt() and 0x1F) == 0x1F) {
            while (offset < header.size && (header[offset].toInt() and 0x80) != 0) {
                offset++
            }
            offset++
        }

        if (offset >= header.size) return offset

        val firstLenByte = header[offset].toInt() and 0xFF
        return when {
            firstLenByte < 0x80 -> offset + 1
            firstLenByte == 0x81 -> offset + 2
            firstLenByte == 0x82 -> offset + 3
            firstLenByte == 0x83 -> offset + 4
            else -> offset + 1
        }
    }

    // ===== DG1 Parsing (MRZ Data) =====

    /**
     * Parse DG1 (Data Group 1) to extract MRZ information.
     *
     * DG1 contains the MRZ data in a TLV structure:
     * Tag 61 -> Tag 5F1F -> MRZ string
     */
    private fun parseDg1(dg1Data: ByteArray): MrzInfo {
        val nodes = TlvParser.parse(dg1Data)

        // Find tag 5F1F (MRZ data)
        var mrzBytes: ByteArray? = null
        for (node in nodes) {
            val mrzNode = node.findTag(0x5F1F)
            if (mrzNode != null) {
                mrzBytes = mrzNode.value
                break
            }
        }

        if (mrzBytes == null) {
            throw NfcReadException("DG1: MRZ data (tag 5F1F) not found")
        }

        val mrzString = String(mrzBytes, Charsets.UTF_8).trim()
        return parseMrzString(mrzString)
    }

    /**
     * Parse a raw MRZ string (TD1/TD2/TD3 format) into structured data.
     */
    private fun parseMrzString(mrz: String): MrzInfo {
        val lines = mrz.chunked(if (mrz.length >= 88) 44 else if (mrz.length >= 72) 36 else 30)

        return when {
            // TD3 (passport): 2 lines x 44 chars
            mrz.length >= 88 -> {
                val line1 = lines[0]
                val line2 = lines[1]
                val nameParts = line1.substring(5).split("<<", limit = 2)
                val lastName = nameParts.getOrElse(0) { "" }.replace("<", " ").trim()
                val firstName = nameParts.getOrElse(1) { "" }.replace("<", " ").trim()

                MrzInfo(
                    documentNumber = line2.substring(0, 9).replace("<", "").trim(),
                    firstName = firstName,
                    lastName = lastName,
                    dateOfBirth = line2.substring(13, 19),
                    expirationDate = line2.substring(21, 27),
                    nationality = line2.substring(10, 13).replace("<", "")
                )
            }
            // TD2: 2 lines x 36 chars
            mrz.length >= 72 -> {
                val line1 = lines[0]
                val line2 = lines[1]
                val nameParts = line1.substring(5).split("<<", limit = 2)
                val lastName = nameParts.getOrElse(0) { "" }.replace("<", " ").trim()
                val firstName = nameParts.getOrElse(1) { "" }.replace("<", " ").trim()

                MrzInfo(
                    documentNumber = line2.substring(0, 9).replace("<", "").trim(),
                    firstName = firstName,
                    lastName = lastName,
                    dateOfBirth = line2.substring(13, 19),
                    expirationDate = line2.substring(21, 27),
                    nationality = line2.substring(10, 13).replace("<", "")
                )
            }
            // TD1 (ID card): 3 lines x 30 chars
            else -> {
                val line1 = lines[0]
                val line2 = lines[1]
                val line3 = lines.getOrElse(2) { "" }
                val nameParts = line3.split("<<", limit = 2)
                val lastName = nameParts.getOrElse(0) { "" }.replace("<", " ").trim()
                val firstName = nameParts.getOrElse(1) { "" }.replace("<", " ").trim()

                MrzInfo(
                    documentNumber = line1.substring(5, 14).replace("<", "").trim(),
                    firstName = firstName,
                    lastName = lastName,
                    dateOfBirth = line2.substring(0, 6),
                    expirationDate = line2.substring(8, 14),
                    nationality = line2.substring(15, 18).replace("<", "")
                )
            }
        }
    }

    // ===== DG2 Parsing (Face Image) =====

    /**
     * Extract the face image (JPEG or JPEG2000) from DG2 data.
     *
     * DG2 contains biometric data in CBEFF (Common Biometric Exchange Formats Framework)
     * format. The face image is embedded within a Biometric Information Template (BIT).
     *
     * Structure: Tag 75 -> Tag 7F61 -> Tag 7F2E (biometric data block)
     * The biometric data block contains a CBEFF header followed by the face image record,
     * which in turn contains the JPEG/JP2 face photo.
     */
    private fun extractFaceImage(dg2Data: ByteArray): ByteArray? {
        // Look for JPEG markers directly in the data
        val jpegStart = findMarker(dg2Data, JPEG_SOI_MARKER)
        if (jpegStart >= 0) {
            val jpegEnd = findMarker(dg2Data, JPEG_EOI_MARKER, jpegStart + 2)
            if (jpegEnd >= 0) {
                return dg2Data.copyOfRange(jpegStart, jpegEnd + 2)
            }
            // No EOI found, take everything from SOI to end
            return dg2Data.copyOfRange(jpegStart, dg2Data.size)
        }

        // Check for JPEG 2000
        val jp2Start = findMarker(dg2Data, JP2_MARKER)
        if (jp2Start >= 0) {
            return dg2Data.copyOfRange(jp2Start, dg2Data.size)
        }

        // Try TLV-based extraction
        val nodes = TlvParser.parse(dg2Data)
        for (node in nodes) {
            // Look for biometric data block (tag 7F2E or 5F2E)
            val bioNode = node.findTag(0x7F2E) ?: node.findTag(0x5F2E)
            if (bioNode != null) {
                val bioData = bioNode.value
                // Search for image markers within biometric data
                val jStart = findMarker(bioData, JPEG_SOI_MARKER)
                if (jStart >= 0) {
                    val jEnd = findMarker(bioData, JPEG_EOI_MARKER, jStart + 2)
                    return if (jEnd >= 0) {
                        bioData.copyOfRange(jStart, jEnd + 2)
                    } else {
                        bioData.copyOfRange(jStart, bioData.size)
                    }
                }
                val j2Start = findMarker(bioData, JP2_MARKER)
                if (j2Start >= 0) {
                    return bioData.copyOfRange(j2Start, bioData.size)
                }
            }
        }

        Log.w(TAG, "No face image found in DG2")
        return null
    }

    /**
     * Find a byte marker pattern in the data.
     *
     * @param data Data to search
     * @param marker Marker bytes to find
     * @param startOffset Offset to start searching from
     * @return Index of the marker, or -1 if not found
     */
    private fun findMarker(data: ByteArray, marker: ByteArray, startOffset: Int = 0): Int {
        if (marker.isEmpty() || data.size < marker.size) return -1
        outer@ for (i in startOffset..data.size - marker.size) {
            for (j in marker.indices) {
                if (data[i + j] != marker[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // ===== Passive Authentication =====

    /**
     * Verify passive authentication by checking DG hashes against SOD.
     *
     * SOD (Security Object of the Document) contains signed hashes of each
     * data group. Passive authentication verifies that the DG content has not
     * been modified since the document was issued.
     *
     * @param dg1Data Raw DG1 bytes
     * @param dg2Data Raw DG2 bytes
     * @param sodData Raw SOD bytes
     * @return True if all DG hashes match
     */
    private fun verifyPassiveAuth(
        dg1Data: ByteArray,
        dg2Data: ByteArray,
        sodData: ByteArray
    ): Boolean {
        try {
            val nodes = TlvParser.parse(sodData)
            if (nodes.isEmpty()) return false

            // SOD is a CMS SignedData structure wrapped in tag 77
            // Find the encapsulated content containing DG hashes
            val hashNodes = findDgHashesInSod(nodes)
            if (hashNodes.isEmpty()) {
                Log.w(TAG, "No DG hashes found in SOD")
                return false
            }

            // Determine hash algorithm (SHA-1 or SHA-256) from SOD
            val hashAlgorithm = detectHashAlgorithm(nodes)

            // Compute hashes of DG1 and DG2 and compare
            var dg1Match = false
            var dg2Match = false

            for ((dgNumber, expectedHash) in hashNodes) {
                val computedHash = when (hashAlgorithm) {
                    "SHA-256" -> when (dgNumber) {
                        1 -> CryptoUtils.sha256(dg1Data)
                        2 -> CryptoUtils.sha256(dg2Data)
                        else -> continue
                    }
                    else -> when (dgNumber) {
                        1 -> CryptoUtils.sha1(dg1Data)
                        2 -> CryptoUtils.sha1(dg2Data)
                        else -> continue
                    }
                }

                if (computedHash.contentEquals(expectedHash)) {
                    when (dgNumber) {
                        1 -> dg1Match = true
                        2 -> dg2Match = true
                    }
                } else {
                    Log.w(TAG, "DG$dgNumber hash mismatch")
                }
            }

            return dg1Match && dg2Match
        } catch (e: Exception) {
            Log.w(TAG, "Passive authentication failed: ${e.message}")
            return false
        }
    }

    /**
     * Extract DG hash mappings from SOD.
     *
     * The SOD contains a signed list of (DG number, hash) pairs.
     * This performs a best-effort extraction from the ASN.1 structure.
     *
     * @return Map of DG number to expected hash bytes
     */
    private fun findDgHashesInSod(nodes: List<TlvNode>): Map<Int, ByteArray> {
        val hashes = mutableMapOf<Int, ByteArray>()

        // SOD structure: tag 77 -> SEQUENCE -> SignedData content
        // The DG hashes are in the encapsulated content, structured as:
        // SEQUENCE { SEQUENCE { INTEGER (DG#), OCTET STRING (hash) } ... }

        // Recursively search for SEQUENCE nodes containing INTEGER + OCTET STRING pairs
        for (node in nodes) {
            extractHashPairs(node, hashes)
        }

        return hashes
    }

    /**
     * Recursively extract DG hash pairs from TLV nodes.
     */
    private fun extractHashPairs(node: TlvNode, hashes: MutableMap<Int, ByteArray>) {
        // Look for SEQUENCE (tag 30) containing INTEGER (tag 02) + OCTET STRING (tag 04)
        if (node.tag == 0x30 && node.children.size == 2) {
            val first = node.children[0]
            val second = node.children[1]

            if (first.tag == 0x02 && second.tag == 0x04) {
                // This looks like a DG hash entry
                val dgNumber = parseDgNumber(first.value)
                if (dgNumber in 1..16) {
                    hashes[dgNumber] = second.value
                }
            }
        }

        // Recurse into children
        for (child in node.children) {
            extractHashPairs(child, hashes)
        }
    }

    /**
     * Parse an ASN.1 INTEGER value to get the DG number.
     */
    private fun parseDgNumber(value: ByteArray): Int {
        if (value.isEmpty()) return -1
        var result = 0
        for (b in value) {
            result = (result shl 8) or (b.toInt() and 0xFF)
        }
        return result
    }

    /**
     * Detect the hash algorithm used in SOD.
     * Looks for OID of SHA-256 (2.16.840.1.101.3.4.2.1) or falls back to SHA-1.
     */
    private fun detectHashAlgorithm(nodes: List<TlvNode>): String {
        // SHA-256 OID: 60 86 48 01 65 03 04 02 01
        val sha256Oid = byteArrayOf(
            0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01
        )

        for (node in nodes) {
            if (containsOid(node, sha256Oid)) {
                return "SHA-256"
            }
        }

        return "SHA-1"
    }

    /**
     * Check if a TLV node tree contains a specific OID.
     */
    private fun containsOid(node: TlvNode, oid: ByteArray): Boolean {
        if (node.tag == 0x06 && node.value.contentEquals(oid)) return true
        for (child in node.children) {
            if (containsOid(child, oid)) return true
        }
        return false
    }

    // ===== APDU Transceive =====

    /**
     * Send an APDU command and receive the response.
     * Does NOT apply secure messaging.
     */
    private fun transceive(command: ApduCommand): ApduResponse {
        val rawResponse = isoDep.transceive(command.toBytes())
        val response = ApduResponse.fromBytes(rawResponse)

        // Handle GET RESPONSE for chained responses
        if (response.hasMoreData) {
            return handleChainedResponse(response)
        }

        return response
    }

    /**
     * Send an APDU command with secure messaging wrapping.
     */
    private fun transceiveSecure(command: ApduCommand): ApduResponse {
        val sm = secureMessaging
            ?: return transceive(command) // Fall back to plain if no SM established

        val wrappedBytes = sm.wrapApdu(command)
        val rawResponse = isoDep.transceive(wrappedBytes)
        val response = ApduResponse.fromBytes(rawResponse)

        // Handle chained response (GET RESPONSE)
        val fullResponse = if (response.hasMoreData) {
            handleChainedResponse(response)
        } else {
            response
        }

        if (!fullResponse.isSuccess) {
            return fullResponse
        }

        // Unwrap secure messaging response
        val plainData = sm.unwrapResponse(fullResponse.data, fullResponse.sw1, fullResponse.sw2)
        return ApduResponse(plainData, fullResponse.sw1, fullResponse.sw2)
    }

    /**
     * Handle chained responses by issuing GET RESPONSE commands.
     */
    private fun handleChainedResponse(initialResponse: ApduResponse): ApduResponse {
        val allData = ByteArrayOutputStream()
        allData.write(initialResponse.data)

        var response = initialResponse
        while (response.hasMoreData) {
            val getResponseCmd = ApduCommand(
                cla = 0x00,
                ins = 0xC0, // GET RESPONSE
                p1 = 0x00,
                p2 = 0x00,
                le = response.remainingBytes
            )
            val rawResponse = isoDep.transceive(getResponseCmd.toBytes())
            response = ApduResponse.fromBytes(rawResponse)
            allData.write(response.data)
        }

        return ApduResponse(allData.toByteArray(), response.sw1, response.sw2)
    }

    companion object {
        private const val TAG = "NfcPassportReader"

        // eMRTD file identifiers
        private const val DG1_FILE_ID = 0x0101  // MRZ data
        private const val DG2_FILE_ID = 0x0102  // Face image
        private const val SOD_FILE_ID = 0x011D  // Security Object

        // Image markers
        private val JPEG_SOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        private val JPEG_EOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        private val JP2_MARKER = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x6A, 0x50, 0x20, 0x20
        )

        /**
         * Create a [BACKey] from parsed MRZ data.
         *
         * @param documentNumber Document number from MRZ (will be padded to 9 chars)
         * @param dateOfBirth Date of birth in YYMMDD format
         * @param dateOfExpiry Date of expiry in YYMMDD format
         */
        fun createBacKey(
            documentNumber: String,
            dateOfBirth: String,
            dateOfExpiry: String
        ): BACKey {
            return BACKey(
                documentNumber = documentNumber.take(9),
                dateOfBirth = dateOfBirth.take(6),
                dateOfExpiry = dateOfExpiry.take(6)
            )
        }
    }
}

/**
 * Internal data class for parsed MRZ information from DG1.
 */
internal data class MrzInfo(
    val documentNumber: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val expirationDate: String,
    val nationality: String
)

/**
 * Exception thrown when NFC passport reading fails.
 */
class NfcReadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
