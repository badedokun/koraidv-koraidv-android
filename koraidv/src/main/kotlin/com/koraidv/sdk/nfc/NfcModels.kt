package com.koraidv.sdk.nfc

import java.io.ByteArrayOutputStream

/**
 * APDU command builder following ISO/IEC 7816-4.
 *
 * @property cla Class byte
 * @property ins Instruction byte
 * @property p1 Parameter 1
 * @property p2 Parameter 2
 * @property data Command data (optional)
 * @property le Expected response length (optional)
 */
internal data class ApduCommand(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray? = null,
    val le: Int? = null
) {
    /**
     * Serialize the APDU command to a byte array for transmission.
     */
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(cla)
        out.write(ins)
        out.write(p1)
        out.write(p2)

        if (data != null && data.isNotEmpty()) {
            if (data.size <= 255) {
                out.write(data.size)
            } else {
                // Extended length encoding
                out.write(0x00)
                out.write((data.size shr 8) and 0xFF)
                out.write(data.size and 0xFF)
            }
            out.write(data)
        }

        if (le != null) {
            if (le <= 256) {
                out.write(if (le == 256) 0x00 else le)
            } else {
                // Extended Le
                if (data == null || data.isEmpty()) {
                    out.write(0x00)
                }
                out.write((le shr 8) and 0xFF)
                out.write(le and 0xFF)
            }
        }

        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApduCommand) return false
        return cla == other.cla && ins == other.ins && p1 == other.p1 &&
            p2 == other.p2 && data.contentEquals(other.data) && le == other.le
    }

    override fun hashCode(): Int {
        var result = cla
        result = 31 * result + ins
        result = 31 * result + p1
        result = 31 * result + p2
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + (le ?: 0)
        return result
    }
}

/**
 * Parsed APDU response from the ePassport chip.
 *
 * @property data Response data bytes
 * @property sw1 Status word 1
 * @property sw2 Status word 2
 */
internal data class ApduResponse(
    val data: ByteArray,
    val sw1: Int,
    val sw2: Int
) {
    /** Combined status word (SW1 << 8 | SW2) */
    val statusWord: Int get() = (sw1 shl 8) or sw2

    /** True if the command completed successfully (SW = 9000) */
    val isSuccess: Boolean get() = sw1 == 0x90 && sw2 == 0x00

    /** True if more data is available (SW1 = 61) */
    val hasMoreData: Boolean get() = sw1 == 0x61

    /** Number of remaining bytes when [hasMoreData] is true */
    val remainingBytes: Int get() = if (hasMoreData) sw2 else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApduResponse) return false
        return data.contentEquals(other.data) && sw1 == other.sw1 && sw2 == other.sw2
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1
        result = 31 * result + sw2
        return result
    }

    companion object {
        /**
         * Parse raw response bytes into an [ApduResponse].
         * The last two bytes are SW1 and SW2; everything before is data.
         */
        fun fromBytes(rawResponse: ByteArray): ApduResponse {
            require(rawResponse.size >= 2) { "APDU response must be at least 2 bytes" }
            val data = rawResponse.copyOfRange(0, rawResponse.size - 2)
            val sw1 = rawResponse[rawResponse.size - 2].toInt() and 0xFF
            val sw2 = rawResponse[rawResponse.size - 1].toInt() and 0xFF
            return ApduResponse(data, sw1, sw2)
        }
    }
}

/**
 * BER-TLV (Tag-Length-Value) node parsed from ASN.1 encoded data.
 *
 * Handles multi-byte tags and multi-byte length encodings as specified
 * in ISO/IEC 7816-4 and used throughout ICAO 9303 eMRTD data structures.
 *
 * @property tag The tag identifier (may be multi-byte, stored as Int)
 * @property length The length of the value field
 * @property value Raw value bytes
 * @property children Parsed child TLV nodes for constructed tags
 */
internal data class TlvNode(
    val tag: Int,
    val length: Int,
    val value: ByteArray,
    val children: List<TlvNode> = emptyList()
) {
    /** True if this is a constructed (container) tag */
    val isConstructed: Boolean get() = (tag and 0x20) != 0 || children.isNotEmpty()

    /**
     * Find the first child node with the given tag, recursively.
     */
    fun findTag(targetTag: Int): TlvNode? {
        if (tag == targetTag) return this
        for (child in children) {
            val found = child.findTag(targetTag)
            if (found != null) return found
        }
        return null
    }

    /**
     * Find all nodes with the given tag, recursively.
     */
    fun findAllTags(targetTag: Int): List<TlvNode> {
        val results = mutableListOf<TlvNode>()
        if (tag == targetTag) results.add(this)
        for (child in children) {
            results.addAll(child.findAllTags(targetTag))
        }
        return results
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TlvNode) return false
        return tag == other.tag && length == other.length && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = tag
        result = 31 * result + length
        result = 31 * result + value.contentHashCode()
        return result
    }
}

/**
 * BER-TLV parser for ASN.1 encoded data as used in ICAO 9303 eMRTDs.
 *
 * Supports:
 * - Single and multi-byte tags (up to 3 bytes)
 * - Short and long form length encoding (up to 3 bytes for length)
 * - Constructed (container) and primitive tags
 * - Recursive parsing of constructed tag contents
 */
internal object TlvParser {

    /**
     * Parse a byte array into a list of TLV nodes.
     *
     * @param data The raw bytes to parse
     * @return List of top-level TLV nodes
     */
    fun parse(data: ByteArray): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        var offset = 0

        while (offset < data.size) {
            // Skip padding bytes (0x00 or 0xFF)
            if (data[offset] == 0x00.toByte() || data[offset] == 0xFF.toByte()) {
                offset++
                continue
            }

            val tagResult = readTag(data, offset)
            offset = tagResult.second
            val tag = tagResult.first

            if (offset >= data.size) break

            val lengthResult = readLength(data, offset)
            offset = lengthResult.second
            val length = lengthResult.first

            if (length < 0 || offset + length > data.size) break

            val value = data.copyOfRange(offset, offset + length)
            offset += length

            // Attempt to parse children for constructed tags
            val isConstructed = isConstructedTag(tag)
            val children = if (isConstructed) {
                try {
                    parse(value)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            nodes.add(TlvNode(tag, length, value, children))
        }

        return nodes
    }

    /**
     * Parse a single TLV node from the start of the data.
     */
    fun parseFirst(data: ByteArray): TlvNode? {
        return parse(data).firstOrNull()
    }

    /**
     * Read a BER tag from the data at the given offset.
     *
     * @return Pair of (tag value, new offset after tag bytes)
     */
    private fun readTag(data: ByteArray, startOffset: Int): Pair<Int, Int> {
        var offset = startOffset
        var tag = data[offset].toInt() and 0xFF
        offset++

        // Multi-byte tag: if low 5 bits of first byte are all 1s
        if ((tag and 0x1F) == 0x1F) {
            var nextByte: Int
            do {
                if (offset >= data.size) break
                nextByte = data[offset].toInt() and 0xFF
                tag = (tag shl 8) or nextByte
                offset++
            } while ((nextByte and 0x80) != 0)
        }

        return Pair(tag, offset)
    }

    /**
     * Read a BER length from the data at the given offset.
     *
     * Supports:
     * - Short form: single byte, value 0-127
     * - Long form: first byte indicates number of length bytes (1-3)
     *
     * @return Pair of (length value, new offset after length bytes)
     */
    private fun readLength(data: ByteArray, startOffset: Int): Pair<Int, Int> {
        var offset = startOffset
        val firstByte = data[offset].toInt() and 0xFF
        offset++

        if (firstByte < 0x80) {
            // Short form
            return Pair(firstByte, offset)
        }

        if (firstByte == 0x80) {
            // Indefinite length (not typically used in eMRTD, treat as 0)
            return Pair(0, offset)
        }

        // Long form: number of subsequent bytes encoding the length
        val numBytes = firstByte and 0x7F
        if (numBytes > 3 || offset + numBytes > data.size) {
            return Pair(-1, offset)
        }

        var length = 0
        for (i in 0 until numBytes) {
            length = (length shl 8) or (data[offset].toInt() and 0xFF)
            offset++
        }

        return Pair(length, offset)
    }

    /**
     * Check if a tag represents a constructed (container) type.
     * Bit 6 of the first tag byte indicates constructed encoding.
     */
    private fun isConstructedTag(tag: Int): Boolean {
        // For multi-byte tags, check bit 6 of the first byte
        val firstByte = if (tag > 0xFF) {
            if (tag > 0xFFFF) (tag shr 16) and 0xFF
            else (tag shr 8) and 0xFF
        } else {
            tag
        }
        return (firstByte and 0x20) != 0
    }
}

/**
 * Secure messaging wrapper for encrypting and MACing APDUs after BAC.
 *
 * After Basic Access Control succeeds, all subsequent APDUs must be
 * wrapped with secure messaging using the negotiated session keys.
 *
 * @property ksEnc Session encryption key (3DES)
 * @property ksMac Session MAC key (3DES)
 * @property ssc Send Sequence Counter (incremented for each command/response)
 */
internal class SecureMessaging(
    private val ksEnc: ByteArray,
    private val ksMac: ByteArray,
    var ssc: ByteArray
) {
    /**
     * Wrap a plaintext APDU command with secure messaging.
     *
     * The wrapped APDU has:
     * - Tag 87: encrypted data (padded plaintext with 01 prefix)
     * - Tag 97: Le value (if present)
     * - Tag 8E: MAC over the DO87/DO97 data objects
     *
     * @param command The plaintext APDU command to wrap
     * @return The secure messaging wrapped APDU bytes
     */
    fun wrapApdu(command: ApduCommand): ByteArray {
        incrementSsc()

        val cmdHeader = byteArrayOf(
            (command.cla or 0x0C).toByte(),
            command.ins.toByte(),
            command.p1.toByte(),
            command.p2.toByte()
        )

        val do87: ByteArray
        val do97: ByteArray

        // Build DO87 (encrypted data)
        if (command.data != null && command.data.isNotEmpty()) {
            val paddedData = pad(command.data)
            val encryptedData = CryptoUtils.encryptDes3Cbc(ksEnc, paddedData, ByteArray(8))
            // DO87 = tag 87 + length + 01 (padding indicator) + encrypted data
            val do87Value = byteArrayOf(0x01) + encryptedData
            do87 = buildDo(0x87, do87Value)
        } else {
            do87 = byteArrayOf()
        }

        // Build DO97 (expected length)
        if (command.le != null) {
            val leValue = if (command.le == 256) 0x00 else command.le
            do97 = buildDo(0x97, byteArrayOf(leValue.toByte()))
        } else {
            do97 = byteArrayOf()
        }

        // Compute MAC
        val paddedHeader = pad(cmdHeader)
        val macInput = ssc + paddedHeader + do87 + do97
        val paddedMacInput = pad(macInput)
        val mac = CryptoUtils.computeRetailMac(ksMac, paddedMacInput)

        // Build DO8E (MAC)
        val do8e = buildDo(0x8E, mac)

        // Assemble wrapped APDU
        val wrappedData = do87 + do97 + do8e
        val wrappedCommand = ApduCommand(
            cla = command.cla or 0x0C,
            ins = command.ins,
            p1 = command.p1,
            p2 = command.p2,
            data = wrappedData,
            le = 0 // Request maximum available
        )

        return wrappedCommand.toBytes()
    }

    /**
     * Unwrap a secure messaging response and return the plaintext data.
     *
     * @param responseData The raw response bytes (excluding SW1/SW2)
     * @return Decrypted plaintext data
     * @throws SecurityException if MAC verification fails
     */
    fun unwrapResponse(responseData: ByteArray, sw1: Int, sw2: Int): ByteArray {
        incrementSsc()

        if (responseData.isEmpty()) return byteArrayOf()

        val nodes = TlvParser.parse(responseData)

        var encryptedData: ByteArray? = null
        var responseMac: ByteArray? = null
        var do99: ByteArray? = null
        var do87Bytes: ByteArray? = null
        var do99Bytes: ByteArray? = null

        for (node in nodes) {
            when (node.tag) {
                0x87 -> {
                    // Encrypted data with padding indicator
                    encryptedData = if (node.value.isNotEmpty() && node.value[0] == 0x01.toByte()) {
                        node.value.copyOfRange(1, node.value.size)
                    } else {
                        node.value
                    }
                    do87Bytes = buildDo(0x87, node.value)
                }
                0x99 -> {
                    do99 = node.value
                    do99Bytes = buildDo(0x99, node.value)
                }
                0x8E -> {
                    responseMac = node.value
                }
            }
        }

        // Verify MAC
        if (responseMac != null) {
            val macInputParts = mutableListOf<ByteArray>()
            macInputParts.add(ssc)
            if (do87Bytes != null) macInputParts.add(do87Bytes)
            if (do99Bytes != null) macInputParts.add(do99Bytes)

            val macInput = macInputParts.reduce { acc, bytes -> acc + bytes }
            val paddedMacInput = pad(macInput)
            val expectedMac = CryptoUtils.computeRetailMac(ksMac, paddedMacInput)

            if (!responseMac.contentEquals(expectedMac)) {
                throw SecurityException("Secure messaging MAC verification failed")
            }
        }

        // Decrypt data
        if (encryptedData != null && encryptedData.isNotEmpty()) {
            val decrypted = CryptoUtils.decryptDes3Cbc(ksEnc, encryptedData, ByteArray(8))
            return unpad(decrypted)
        }

        return byteArrayOf()
    }

    /**
     * Increment the Send Sequence Counter.
     */
    private fun incrementSsc() {
        for (i in ssc.size - 1 downTo 0) {
            ssc[i]++
            if (ssc[i] != 0x00.toByte()) break
        }
    }

    /**
     * Build a BER-TLV data object with the given tag and value.
     */
    private fun buildDo(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        if (value.size < 0x80) {
            out.write(value.size)
        } else if (value.size <= 0xFF) {
            out.write(0x81)
            out.write(value.size)
        } else {
            out.write(0x82)
            out.write((value.size shr 8) and 0xFF)
            out.write(value.size and 0xFF)
        }
        out.write(value)
        return out.toByteArray()
    }

    companion object {
        /**
         * Apply ISO 9797-1 padding method 2 (0x80 followed by 0x00 bytes).
         * Pads to a multiple of 8 bytes.
         */
        fun pad(data: ByteArray): ByteArray {
            val paddingLength = 8 - (data.size % 8)
            val padded = ByteArray(data.size + paddingLength)
            System.arraycopy(data, 0, padded, 0, data.size)
            padded[data.size] = 0x80.toByte()
            // Remaining bytes are already 0x00
            return padded
        }

        /**
         * Remove ISO 9797-1 padding method 2.
         */
        fun unpad(data: ByteArray): ByteArray {
            var i = data.size - 1
            while (i >= 0 && data[i] == 0x00.toByte()) {
                i--
            }
            if (i >= 0 && data[i] == 0x80.toByte()) {
                return data.copyOfRange(0, i)
            }
            return data
        }
    }
}

/**
 * Cryptographic utility functions for ICAO 9303 BAC and secure messaging.
 *
 * Uses javax.crypto (3DES/CBC) and java.security (SHA-1, DES MAC) which
 * are available in the Android SDK without external dependencies.
 */
internal object CryptoUtils {

    /**
     * Compute K_seed from MRZ information per ICAO 9303 Part 11.
     *
     * Concatenates document_number + check_digit + date_of_birth + check_digit +
     * date_of_expiry + check_digit, then SHA-1 hashes and takes the first 16 bytes.
     *
     * @param mrzInfo Concatenated MRZ info string (doc_no_cd + dob_cd + exp_cd)
     * @return 16-byte K_seed
     */
    fun computeKSeed(mrzInfo: String): ByteArray {
        val hash = sha1(mrzInfo.toByteArray(Charsets.UTF_8))
        return hash.copyOfRange(0, 16)
    }

    /**
     * Derive 3DES key from K_seed using KDF per ICAO 9303.
     *
     * @param kSeed The 16-byte key seed
     * @param counter Counter value: 1 for K_enc, 2 for K_mac
     * @return 24-byte 3DES key (with parity adjustment)
     */
    fun deriveKey(kSeed: ByteArray, counter: Int): ByteArray {
        val d = kSeed + byteArrayOf(0x00, 0x00, 0x00, counter.toByte())
        val hash = sha1(d)

        // Take first 16 bytes and adjust DES key parity
        val keyA = adjustParity(hash.copyOfRange(0, 8))
        val keyB = adjustParity(hash.copyOfRange(8, 16))

        // 3DES key = Ka + Kb + Ka (EDE with two keys)
        return keyA + keyB + keyA
    }

    /**
     * SHA-1 hash.
     */
    fun sha1(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        return digest.digest(data)
    }

    /**
     * SHA-256 hash.
     */
    fun sha256(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    /**
     * 3DES-CBC encryption.
     *
     * @param key 24-byte 3DES key
     * @param data Data to encrypt (must be padded to 8-byte boundary)
     * @param iv 8-byte initialization vector
     * @return Encrypted data
     */
    fun encryptDes3Cbc(key: ByteArray, data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("DESede/CBC/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "DESede")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * 3DES-CBC decryption.
     *
     * @param key 24-byte 3DES key
     * @param data Data to decrypt
     * @param iv 8-byte initialization vector
     * @return Decrypted data
     */
    fun decryptDes3Cbc(key: ByteArray, data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("DESede/CBC/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "DESede")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Single DES-CBC encryption (used in retail MAC).
     */
    fun encryptDesCbc(key: ByteArray, data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("DES/CBC/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "DES")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Compute ISO 9797-1 MAC Algorithm 3 (Retail MAC).
     *
     * Uses DES for all blocks except the last, then 3DES for the final block.
     * This is the MAC algorithm required by ICAO 9303 for BAC.
     *
     * @param key 24-byte 3DES key (Ka + Kb + Ka)
     * @param data Padded data to MAC (must be multiple of 8 bytes)
     * @return 8-byte MAC value
     */
    fun computeRetailMac(key: ByteArray, data: ByteArray): ByteArray {
        val ka = key.copyOfRange(0, 8)
        val kb = key.copyOfRange(8, 16)

        // Process all blocks with single DES using Ka
        var intermediate = ByteArray(8)
        val numBlocks = data.size / 8

        for (i in 0 until numBlocks) {
            val block = data.copyOfRange(i * 8, (i + 1) * 8)
            val xored = ByteArray(8)
            for (j in 0 until 8) {
                xored[j] = (intermediate[j].toInt() xor block[j].toInt()).toByte()
            }
            intermediate = encryptDesCbc(ka, xored, ByteArray(8))
        }

        // Final step: decrypt with Kb, then encrypt with Ka
        val decCipher = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding")
        val kbSpec = javax.crypto.spec.SecretKeySpec(kb, "DES")
        decCipher.init(javax.crypto.Cipher.DECRYPT_MODE, kbSpec)
        intermediate = decCipher.doFinal(intermediate)

        val encCipher = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding")
        val kaSpec = javax.crypto.spec.SecretKeySpec(ka, "DES")
        encCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, kaSpec)
        intermediate = encCipher.doFinal(intermediate)

        return intermediate
    }

    /**
     * Generate 8 random bytes for challenges/nonces.
     */
    fun generateRandom(length: Int): ByteArray {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }

    /**
     * Adjust DES key parity bits.
     * Each byte of a DES key must have odd parity (odd number of 1 bits).
     */
    private fun adjustParity(key: ByteArray): ByteArray {
        val adjusted = key.copyOf()
        for (i in adjusted.indices) {
            var b = adjusted[i].toInt() and 0xFE
            // Count bits
            var bits = 0
            var temp = b
            while (temp != 0) {
                bits += temp and 1
                temp = temp ushr 1
            }
            // Set parity bit (LSB) to make total number of 1s odd
            if (bits % 2 == 0) {
                b = b or 1
            }
            adjusted[i] = b.toByte()
        }
        return adjusted
    }

    /**
     * Compute MRZ check digit per ICAO 9303.
     */
    fun computeCheckDigit(data: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        for ((index, char) in data.withIndex()) {
            val value = when {
                char.isDigit() -> char.digitToInt()
                char.isLetter() -> char.uppercaseChar().code - 55
                char == '<' -> 0
                else -> 0
            }
            sum += value * weights[index % 3]
        }
        return sum % 10
    }
}
