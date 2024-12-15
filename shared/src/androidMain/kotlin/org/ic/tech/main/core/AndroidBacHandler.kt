package org.ic.tech.main.core

import arrow.core.Either
import arrow.core.getOrElse
import org.ic.tech.main.models.BacKey
import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus
import java.security.SecureRandom
import javax.crypto.SecretKey

class AndroidBacHandler {

    private val smk = SecureMessagingSessionKeyGenerator()
    private val sm = AndroidSecureMessaging()

    suspend fun doBACAuthentication(
        tagReader: AndroidTagReader,
        bacKey: BacKey
    ): ReadIdCardResponse {
        try {
            val keySeed = smk.computeKeySeedForBAC(bacKey)

            val kEnc = smk.deriveKey(keySeed, counter = 1)
            val kMac = smk.deriveKey(keySeed, counter = 2)

            return performBacAndGetSessionKey(tagReader, kEnc, kMac)
        } catch (exception: Exception) {
            return ReadIdCardResponse(
                status = ReadIdCardStatus.Failed,
                message = "Failed to Bac Authentication ⚠️with message: ${exception.message}",
                data = mapOf()
            )
        }
    }

    /**
     * Generate random key
     *
     * Function will generate random interface device key and random session key device
     * use secure random generator. Key random is byte array of 8 and 16 bytes.
     * 1. Random key from IFD (Interface Device) - 8 bytes
     * 2. Session Key from IFD (Interface Device) - 16 bytes
     */
    private fun randomKeyDevice(): Pair<ByteArray, ByteArray> {
        val random = SecureRandom()
        val rndIFD = ByteArray(8) // Random key from IFD (Interface Device)
        random.nextBytes(rndIFD)

        val kIFD = ByteArray(16) // Session Key from IFD (Interface Device)
        random.nextBytes(kIFD)

        return Pair(rndIFD, kIFD)
    }

    private suspend fun performBacAndGetSessionKey(
        tagReader: AndroidTagReader,
        kEnc: SecretKey,
        kMac: SecretKey,
    ): ReadIdCardResponse {
        val rndICC = tagReader.sendGetChallenge()
        if (rndICC.isEmpty()) return ReadIdCardResponse(
            status = ReadIdCardStatus.Failed,
            message = "Failed to get challenge ⚠️",
            data = mapOf()
        )

        val (rndIFD, kIFD) = randomKeyDevice()
        // Key ICC 16 bytes
        val kICC = sendMutualAuthentication(
            tagReader = tagReader,
            rndICC = rndICC,
            rndIFD = rndIFD,
            kIFD = kIFD,
            kEnc = kEnc,
            kMac = kMac
        ).getOrElse {
            return it
        }

        val response = generateSessionKey(kIFD, kICC, rndIFD, rndICC)
        val ksEnc = response["KSEnc"] as SecretKey
        val ksMac = response["KSMac"] as SecretKey
        val ssc = response["SSC"] as Long

        val secureMessaging = SecureMessaging(
            ksEnc = ksEnc,
            ksMac = ksMac,
            ssc = ssc,
            algorithm = SecureMessagingSupportedAlgorithms.DES
        )

        tagReader.updateSecureMessaging(secureMessaging)
        return ReadIdCardResponse(
            status = ReadIdCardStatus.PerformBacSuccess,
            message = "Perform BAC success and update session key ready for get data group ✅",
            data = mapOf()
        )
    }

    private fun generateSessionKey(
        kIFD: ByteArray,
        kICC: ByteArray,
        rndIFD: ByteArray,
        rndICC: ByteArray,
    ): Map<String, Any> {
        val keySeed = ByteArray(16)
        for (i in 0..15) {
            keySeed[i] = (kIFD[i].toInt() and 0xFF xor (kICC[i].toInt() and 0xFF)).toByte()
        }

        // Generate keys for encryption and mac
        val ksEnc = smk.deriveKey(keySeed, counter = 1)
        val ksMac = smk.deriveKey(keySeed, counter = 2)
        val ssc = smk.computeSendSequenceCounter(rndICC, rndIFD)

        return mapOf(
            "KSEnc" to ksEnc,
            "KSMac" to ksMac,
            "SSC" to ssc
        )
    }

    private suspend fun sendMutualAuthentication(
        tagReader: AndroidTagReader,
        rndICC: ByteArray,
        rndIFD: ByteArray,
        kIFD: ByteArray,
        kEnc: SecretKey,
        kMac: SecretKey
    ): Either<ReadIdCardResponse, ByteArray> {
        // When send challenge => Le (length Expected) = 08 => Return data with 8 bytes + 2 status in end response
        require(rndIFD.size == 8) { "rndIFD must be 8 bytes long" }
        require(kIFD.size == 16) { "kIFD must be 16 bytes long" }
        require(rndICC.size == 8) { "rndICC must be 8 bytes long" }

        var data = ByteArray(32)
        System.arraycopy(rndIFD, 0, data, 0, 8)
        System.arraycopy(rndICC, 0, data, 8, 8)
        System.arraycopy(kIFD, 0, data, 16, 16)

        val encryptedData = sm.cipherEncrypt(kEnc, data)
        require(encryptedData.size == 32) { "Encrypted data must be 32 bytes long" }

        val signature = sm.macSign(kMac, PassportLib.padWithMRZ(encryptedData))
        require(signature.size == 8) { "Signature must be 8 bytes long" }

        // Prepare APDU
        val p1: Byte = 0x00.toByte() // P1 Parameter
        val p2: Byte = 0x00.toByte() // P2 Parameter
        data = ByteArray(32 + 8) // Data 40 bytes = 32 byte encrypted data + 8 byte signature
        System.arraycopy(encryptedData, 0, data, 0, 32)
        System.arraycopy(signature, 0, data, 32, 8)
        val le = 40 // Response length

        val command = AndroidNFCISO7816APDU(
            cla = MISO7816.CLA_ISO7816.toInt(),
            ins = MISO7816.INS_EXTERNAL_AUTHENTICATE.toInt(),
            p1 = p1.toInt(),
            p2 = p2.toInt(),
            data = data,
            ne = le
        )

        val response = tagReader.send(command)
        // Response APDU -> 42 bytes = 32 byte expected data + 8 byte mac + 2 byte status
        val responseBytes = ADPUValidator.checkIsSuccessAndDropSW(response)
        // After check state and drop SW, we have the response with 40 bytes
        if (responseBytes.isEmpty()) {
            val message = ADPUValidator.decodeStatus(response)
            return Either.Left(
                ReadIdCardResponse(
                    status = ReadIdCardStatus.Failed,
                    message = "Failed to send Mutual Authentication ⚠️ with message: $message",
                    data = mapOf()
                )
            )
        }


        require(responseBytes.size == 40) { "Response bytes must be 40 bytes long" }
        // Decrypt data 32 byte first in response -> except 8 byte signature
        val decryptedData = sm.cipherDecrypt(
            kEnc,
            responseBytes,
            offset = 0,
            length = responseBytes.size - 8
        )

        require(decryptedData.size == 32) { "Decrypted data must be 32 bytes long" }
        // Slice 16 byte first and return
        val kICC = ByteArray(16)
        System.arraycopy(decryptedData, 16, kICC, 0, 16)
        return Either.Right(kICC)
    }
}