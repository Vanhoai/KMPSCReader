package org.ic.tech.main.core

import android.nfc.Tag
import android.nfc.tech.IsoDep
import org.ic.tech.main.core.extensions.toHexString
import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus

class AndroidTagReader(
    private val tag: Tag
) : TagReader {

    private var isoDep: IsoDep? = null

    override suspend fun initialize(): ReadIdCardResponse {
        try {
            isoDep = IsoDep.get(tag)
            isoDep?.connect()
            return ReadIdCardResponse(
                status = ReadIdCardStatus.InitializeSuccess,
                message = "Initialized successfully ✅",
                data = mapOf()
            )
        } catch (exception: Exception) {
            return ReadIdCardResponse(
                status = ReadIdCardStatus.Failed,
                message = "Failed to initialize tag reader ⚠️",
                data = mapOf()
            )
        }
    }

    override suspend fun sendGetChallenge(): ByteArray {
        val command = NFCISO7816APDU(
            instructionClass = MISO7816.CLA_ISO7816,
            instructionCode = MISO7816.INS_GET_CHALLENGE,
            p1Parameter = 0x00u,
            p2Parameter = 0x00u,
            data = null,
            expectedResponseLength = 8
        )

        val response = isoDep?.transceive(command.toByteArray())
        return ADPUValidator.checkIsSuccessAndDropSW(response ?: byteArrayOf())
    }

    override suspend fun finalize(): Boolean {
        try {
            isoDep?.close()
            return true
        } catch (exception: Exception) {
            return false
        } finally {
            isoDep = null
        }
    }

    /**
     * Select Passport Application
     * @return Boolean
     *
     * Function send a command to select passport application on NFC tag
     * and return response after parsing and checking if success or not.
     *
     * Command: 00 A4 04 00 07 A0 00 00 02 47 10 0E
     *  - 0x00 -> Instruction Class
     *  - 0xA4 -> Instruction Code
     *  - 0x04 -> P1 Parameter
     *  - 0x00 -> P2 Parameter
     *  - 0x07 -> Data Length
     *  - A0 00 00 02 47 10 01 -> Data
     *  - 0x0E -> Expected Response Length
     */
    override suspend fun selectPassportApplication(): ReadIdCardResponse {
        requireNotNull(isoDep) { "IsoDep tag is null ⚠️" }

        val data = byteArrayOf(
            0xA0.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x47.toByte(),
            0x10.toByte(),
            0x01.toByte()
        )

        val command = NFCISO7816APDU(
            instructionClass = 0x00u,
            instructionCode = 0xA4u,
            p1Parameter = 0x04u,
            p2Parameter = 0x00u,
            data = data,
            expectedResponseLength = 16
        )

        val response = isoDep?.transceive(command.toByteArray())
        return ADPUValidator.parseResponse(response ?: byteArrayOf())
    }

    override suspend fun send(cmd: NFCISO7816APDU): ByteArray {
        val response = isoDep?.transceive(cmd.toByteArray())
        return response ?: byteArrayOf()
    }
}