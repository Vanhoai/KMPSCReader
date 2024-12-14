package org.ic.tech.main.core

import org.ic.tech.main.models.BacKey
import java.io.UnsupportedEncodingException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SecureMessagingSessionKeyGenerator {

    @Throws(Exception::class)
    fun computeKeySeedForBAC(bacKey: BacKey): ByteArray {
        val documentNumber = bacKey.documentNumber
        val dateOfBirth = bacKey.birthDate
        val dateOfExpiry = bacKey.expireDate

        require(dateOfBirth.length == 6) { "Wrong date format used for date of birth. Expected yyMMdd, found $dateOfBirth" }
        require(dateOfExpiry.length == 6) { "Wrong date format used for date of expiry. Expected yyMMdd, found $dateOfExpiry" }
        val fixDocumented = fixDocumentNumber(documentNumber)
        return computeKeySeed(fixDocumented, dateOfBirth, dateOfExpiry)
    }

    private fun fixDocumentNumber(documentNumber: String): String {
        val minDocumentNumber = documentNumber.replace('<', ' ').trim { it <= ' ' }
            .replace(' ', '<')

        var maxDocumentNumber = minDocumentNumber
        while (maxDocumentNumber.length < 9) {
            maxDocumentNumber += "<"
        }
        return maxDocumentNumber
    }

    @Throws(Exception::class)
    private fun computeKeySeed(
        documentNumber: String,
        dateOfBirth: String,
        dateOfExpiry: String,
        digestAlg: String = "SHA-1",
        doTruncate: Boolean = true,
    ): ByteArray {

        val documentNumberCheckDigit = byteArrayOf(checkDigit(documentNumber).code.toByte())
        val dateOfBirthCheckDigit = byteArrayOf(checkDigit(dateOfBirth).code.toByte())
        val dateOfExpiryCheckDigit = byteArrayOf(checkDigit(dateOfExpiry).code.toByte())

        val shaDigest = MessageDigest.getInstance(digestAlg)

        shaDigest.update(documentNumber.toByteArray())
        shaDigest.update(documentNumberCheckDigit)
        shaDigest.update(dateOfBirth.toByteArray())
        shaDigest.update(dateOfBirthCheckDigit)
        shaDigest.update(dateOfExpiry.toByteArray())
        shaDigest.update(dateOfExpiryCheckDigit)

        val hash = shaDigest.digest()

        return if (doTruncate) {
            val keySeed = ByteArray(16)
            System.arraycopy(hash, 0, keySeed, 0, 16)
            keySeed
        } else {
            hash
        }
    }

    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    private fun checkDigit(str: String?, preferFillerOverZero: Boolean = false): Char {
        return try {
            val chars = str?.toByteArray(charset("UTF-8")) ?: byteArrayOf()

            val weights = intArrayOf(7, 3, 1)
            var result = 0
            for (i in chars.indices) {
                result = (result + weights[i % 3] * decodeMRZDigit(chars[i])) % 10
            }

            val checkDigitString = result.toString()
            check(checkDigitString.length == 1) { "Error in computing check digit." /* NOTE: Never happens. */ }
            var checkDigit = Char(checkDigitString.toByteArray(charset("UTF-8"))[0].toUShort())
            if (preferFillerOverZero && checkDigit == '0') {
                checkDigit = '<'
            }

            checkDigit
        } catch (nfe: NumberFormatException) {
            throw IllegalStateException("Error in computing check digit.")
        } catch (use: UnsupportedEncodingException) {
            throw IllegalStateException("Error in computing check digit.")
        } catch (e: Exception) {
            throw IllegalArgumentException(e.toString())
        }
    }

    @Throws(NumberFormatException::class)
    private fun decodeMRZDigit(ch: Byte): Int {
        when (ch.toInt().toChar()) {
            '<', '0' -> return 0
            '1' -> return 1
            '2' -> return 2
            '3' -> return 3
            '4' -> return 4
            '5' -> return 5
            '6' -> return 6
            '7' -> return 7
            '8' -> return 8
            '9' -> return 9
            'a', 'A' -> return 10
            'b', 'B' -> return 11
            'c', 'C' -> return 12
            'd', 'D' -> return 13
            'e', 'E' -> return 14
            'f', 'F' -> return 15
            'g', 'G' -> return 16
            'h', 'H' -> return 17
            'i', 'I' -> return 18
            'j', 'J' -> return 19
            'k', 'K' -> return 20
            'l', 'L' -> return 21
            'm', 'M' -> return 22
            'n', 'N' -> return 23
            'o', 'O' -> return 24
            'p', 'P' -> return 25
            'q', 'Q' -> return 26
            'r', 'R' -> return 27
            's', 'S' -> return 28
            't', 'T' -> return 29
            'u', 'U' -> return 30
            'v', 'V' -> return 31
            'w', 'W' -> return 32
            'x', 'X' -> return 33
            'y', 'Y' -> return 34
            'z', 'Z' -> return 35
            else -> throw NumberFormatException(
                "Could not decode MRZ character "
                        + ch + " ('" + Char(ch.toUShort()).toString() + "')"
            )
        }
    }

    fun computeSendSequenceCounter(rndICC: ByteArray?, rndIFD: ByteArray?): Long {
        check(!(rndICC == null || rndICC.size != 8 || rndIFD == null || rndIFD.size != 8)) { "Wrong length input" }
        var ssc: Long = 0
        for (i in 4..7) {
            ssc = ssc shl 8
            ssc += (rndICC[i].toInt() and 0x000000FF).toLong()
        }
        for (i in 4..7) {
            ssc = ssc shl 8
            ssc += (rndIFD[i].toInt() and 0x000000FF).toLong()
        }
        return ssc
    }

    @Throws(GeneralSecurityException::class)
    fun deriveKey(
        keySeed: ByteArray,
        cipherAlg: String = "DESede",
        keyLength: Int = 128,
        nonce: ByteArray? = null,
        counter: Int
    ): SecretKey {
        val digestAlg =
            inferDigestAlgorithmFromCipherAlgorithmForKeyDerivation(cipherAlg, keyLength)

        val digest = MessageDigest.getInstance(digestAlg)
        digest.reset()

        digest.update(keySeed)
        if (nonce != null) digest.update(nonce)
        digest.update(byteArrayOf(0x00, 0x00, 0x00, counter.toByte()))

        val hashResult = digest.digest()
        var keyBytes: ByteArray? = null

        if ("DESede".equals(cipherAlg, ignoreCase = true) || "3DES".equals(
                cipherAlg,
                ignoreCase = true
            )
        ) {
            /* TR-SAC 1.01, 4.2.1. */
            when (keyLength) {
                112, 128 -> {
                    keyBytes = ByteArray(24)
                    System.arraycopy(hashResult, 0, keyBytes, 0, 8) /* E  (octets 1 to 8) */
                    System.arraycopy(hashResult, 8, keyBytes, 8, 8) /* D  (octets 9 to 16) */
                    System.arraycopy(
                        hashResult,
                        0,
                        keyBytes,
                        16,
                        8
                    ) /* E (again octets 1 to 8, i.e. 112-bit 3DES key) */
                }

                else -> throw java.lang.IllegalArgumentException("KDF can only use DESede with 128-bit key length")
            }
        } else if ("AES".equals(cipherAlg, ignoreCase = true) || cipherAlg.startsWith("AES")) {
            /* TR-SAC 1.01, 4.2.2. */
            when (keyLength) {
                128 -> {
                    keyBytes = ByteArray(16) /* NOTE: 128 = 16 * 8 */
                    System.arraycopy(hashResult, 0, keyBytes, 0, 16)
                }

                192 -> {
                    keyBytes = ByteArray(24) /* NOTE: 192 = 24 * 8 */
                    System.arraycopy(hashResult, 0, keyBytes, 0, 24)
                }

                256 -> {
                    keyBytes = ByteArray(32) /* NOTE: 256 = 32 * 8 */
                    System.arraycopy(hashResult, 0, keyBytes, 0, 32)
                }

                else -> throw java.lang.IllegalArgumentException("KDF can only use AES with 128-bit, 192-bit key or 256-bit length, found: $keyLength-bit key length")
            }
        }
        return SecretKeySpec(keyBytes, cipherAlg)
    }

    @Throws(IllegalArgumentException::class)
    private fun inferDigestAlgorithmFromCipherAlgorithmForKeyDerivation(
        cipherAlg: String?,
        keyLength: Int
    ): String {
        requireNotNull(cipherAlg)
        if ("DESede" == cipherAlg || "AES-128" == cipherAlg) {
            return "SHA-1"
        }
        if ("AES" == cipherAlg && keyLength == 128) {
            return "SHA-1"
        }
        if ("AES-256" == cipherAlg || "AES-192" == cipherAlg) {
            return "SHA-256"
        }
        if ("AES" == cipherAlg && (keyLength == 192 || keyLength == 256)) {
            return "SHA-256"
        }
        throw IllegalArgumentException("Unsupported cipher algorithm or key length \"$cipherAlg\", $keyLength")
    }
}