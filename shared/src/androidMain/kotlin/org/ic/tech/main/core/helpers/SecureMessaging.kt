package org.ic.tech.main.core.helpers

import org.ic.tech.main.core.algorithms.SecureMessagingSupportedAlgorithms
import org.ic.tech.main.core.models.apdu.NFCISO7816APDU
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Integrated circuit -> Chip ID Card
 * NFC -> Near Field Communication
 *
 * ID Card -> Multi Tech (Iso Dep, NfcA) -> *Iso Dep
 */

/**
 * SecureMessaging class is responsible for handling Secure Messaging operations.
 * It provides methods to protect and unprotect APDUs using Secure Messaging.
 *
 * @property ksEnc SecretKey for encryption
 * @property ksMac SecretKey for MAC
 * @property ssc Secure Channel Counter
 * @property algorithm Secure Messaging algorithm
 */
class SecureMessaging(
    var ksEnc: SecretKey,
    var ksMac: SecretKey,
    var ssc: Long,
    val algorithm: SecureMessagingSupportedAlgorithms
) {
    private val padLength = if (algorithm == SecureMessagingSupportedAlgorithms.DES) 8 else 16
    private val sm = AndroidCrypto()

    /**
     * Increment Secure Channel Counter (SCC)
     *
     * Function increments the Send Sequence Counter (SCC) for the Secure Channel.
     * When incrementing the SCC, This is necessary to ensure chip-to-chip communication is secure.
     */
    private fun incrementSCC() = ssc++

    /**
     * Protect APDU with Secure Messaging
     * @param apdu APDU to be protected
     * @return Protected APDU
     * @throws IllegalArgumentException if Secure Channel Counter (SCC) is not incremented
     *
     * Function protects the APDU with Secure Messaging.
     * It increments the Secure Channel Counter (SCC) and builds the DO8587 and DO97 commands.
     * The DO8587 command is used to encrypt the data of the APDU and the DO97 command is used to build the MAC of the protected APDU.
     * Following steps will be executed:
     * 1. Increment the Secure Channel Counter (SCC)
     * 2. Mask Header Command
     * 3. Build DO97 if expected response length is greater than zero and the INS is MSE
     * 4. Build DO8587 if command data length is greater than zero
     * 5. Build Prefix
     * 6. Build MAC
     * 7. Build actual command with DO8587, DO97, DO8E commands
     * 8. Return new protected APDU
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun protect(apdu: NFCISO7816APDU): NFCISO7816APDU {
        incrementSCC()
        val masked = PassportLib.maskAndPad(apdu, padLength)
        val do97 = PassportLib.buildD097(apdu = apdu)
        val do8587 = PassportLib.buildDO8587(apdu = apdu, padLength, ksEnc, sm)

        val prefix = PassportLib.buildPrefix(masked, do8587, do97, ssc)
        val mac = AndroidCrypto.macSign(ksMac, prefix)
        require(mac.size == 8) { "MAC must be 8 bytes long" }
        val do8E = PassportLib.build8E(mac)

        val dataToSend = do8587 + do97 + do8E
        val newApdu = NFCISO7816APDU(
            masked[0].toInt(),
            masked[1].toInt(),
            masked[2].toInt(),
            masked[3].toInt(),
            dataToSend,
            256
        )

        return newApdu
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun unprotect(apdu: ByteArray): ByteArray {
        incrementSCC()
        val inputStream = DataInputStream(ByteArrayInputStream(apdu))
        var data = ByteArray(0)
        var sw: Short = 0
        var finished = false
        var cc: ByteArray? = null

        val cipher = AndroidCrypto.initializeCipher(ksEnc, Cipher.DECRYPT_MODE)
        while (!finished) {
            val tag = inputStream.readByte().toInt()
            when (tag.toByte()) {
                0x87.toByte() -> data = PassportLib.readDO87(cipher, inputStream, false)
                0x85.toByte() -> data = PassportLib.readDO87(cipher, inputStream, true)
                0x99.toByte() -> sw = PassportLib.readDO99(inputStream)
                0x8E.toByte() -> {
                    cc = PassportLib.readDO8E(inputStream)
                    finished = true
                }
            }
        }

        check(!(cc != null && !checkMac(apdu, cc))) { "Mac not valid for APDU" }
        val ous = ByteArrayOutputStream()
        ous.write(data, 0, data.size)
        ous.write(sw.toInt() and 0xFF00 shr 8)
        ous.write(sw.toInt() and 0x00FF)
        return ous.toByteArray()
    }

    @Throws(GeneralSecurityException::class)
    private fun checkMac(rApdu: ByteArray, cc1: ByteArray): Boolean {
        return try {
            val bOut = ByteArrayOutputStream()
            val dataOut = DataOutputStream(bOut)
            dataOut.writeLong(ssc)
            val paddedData = PassportLib.padWithMRZ(rApdu, 0, rApdu.size - 2 - 8 - 2)
            dataOut.write(paddedData, 0, paddedData.size)
            dataOut.flush()
            dataOut.close()

            var cc2: ByteArray = AndroidCrypto.macSign(ksMac, bOut.toByteArray())
            if (cc2.size > 8 && cc1.size == 8) {
                val newCC2 = ByteArray(8)
                System.arraycopy(cc2, 0, newCC2, 0, newCC2.size)
                cc2 = newCC2
            }
            cc1.contentEquals(cc2)
        } catch (ioe: IOException) {
            false
        }
    }
}