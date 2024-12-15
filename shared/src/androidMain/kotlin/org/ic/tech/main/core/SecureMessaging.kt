package org.ic.tech.main.core

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

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
    private val sm = AndroidSecureMessaging()

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
    fun protect(apdu: AndroidNFCISO7816APDU): AndroidNFCISO7816APDU {
        incrementSCC()
        val masked = PassportLib.maskAndPad(apdu, padLength)
        val do97 = PassportLib.buildD097(apdu = apdu)
        val do8587 = PassportLib.buildDO8587(apdu = apdu, padLength, ksEnc, sm)

        val prefix = PassportLib.buildPrefix(masked, do8587, do97, ssc)
        val mac = sm.macSign(ksMac, prefix)
        require(mac.size == 8) { "MAC must be 8 bytes long" }
        val do8E = PassportLib.build8E(mac)

        val dataToSend = do8587 + do97 + do8E
        val newAdpu = AndroidNFCISO7816APDU(
            masked[0].toInt(),
            masked[1].toInt(),
            masked[2].toInt(),
            masked[3].toInt(),
            dataToSend,
            256
        )

        return newAdpu
    }
}