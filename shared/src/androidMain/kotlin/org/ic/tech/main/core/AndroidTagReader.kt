package org.ic.tech.main.core

import android.nfc.Tag
import android.nfc.tech.IsoDep
import org.ic.tech.main.core.extensions.toHexString
import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus

class AndroidTagReader(private val tag: Tag) {

    private var isoDep: IsoDep? = null
    private var secureMessaging: SecureMessaging? = null

    fun updateSecureMessaging(secureMessaging: SecureMessaging) {
        this.secureMessaging = secureMessaging
    }

    suspend fun initialize(): ReadIdCardResponse {
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

    suspend fun sendGetChallenge(): ByteArray {
        val command = NFCISO7816APDU(
            cla = MISO7816.CLA_ISO7816.toInt(),
            ins = MISO7816.INS_GET_CHALLENGE.toInt(),
            p1 = 0x00,
            p2 = 0x00,
            data = null,
            ne = 8
        )

        val response = isoDep?.transceive(command.toByteArray())
        return APDUValidator.checkIsSuccessAndDropSW(response ?: byteArrayOf())
    }

    suspend fun finalize(): Boolean {
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
    fun selectPassportApplication(): ReadIdCardResponse {
        requireNotNull(isoDep) { "IsoDep tag is null ⚠️" }

        // 07A00000024710010E
        val data = byteArrayOf(
            0xA0.toByte(), // 0xA0
            0x00.toByte(), // 0x00
            0x00.toByte(), // 0x00
            0x02.toByte(), // 0x02
            0x47.toByte(), // 0x47
            0x10.toByte(), // 0x10
            0x01.toByte() // 0x01
        )

        val command = AndroidNFCISO7816APDU(
            cla = 0x00,
            ins = 0xA4,
            p1 = 0x04,
            p2 = 0x00,
            data = data,
            ne = 14,
        )
        val response = isoDep?.transceive(command.toByteArray())
        return APDUValidator.parseResponse(response ?: byteArrayOf())
    }

    fun send(cmd: AndroidNFCISO7816APDU): ByteArray {
        val response = isoDep?.transceive(cmd.toByteArray())
        return response ?: byteArrayOf()
    }

    fun sendSelectFileAndReadDataGroup(dg: DataGroup): ByteArray {
        val response = sendSelectFile(dg.value)
        if (!APDUValidator.isSuccess(response)) return byteArrayOf()

        var data: ByteArray = byteArrayOf()
        val fileInfo: ByteArray = getFileInfo()
        data = data.plus(fileInfo)
        val fileLength = PassportLib.getFileLength(fileInfo)
        data = data.plus(getFileContent(fileLength))
        return data
    }

    private fun getFileContent(fileLength: Int): ByteArray {
        var data: ByteArray = byteArrayOf()
        var offset = 8
        while (offset < fileLength) {
            val le = DEFAULT_MAX_BLOCK_SIZE.coerceAtMost(fileLength - offset)
            data = data.plus(sendReadBinary(offset, le, false)!!)
            offset += le
        }

        return data
    }

    private fun getFileInfo(): ByteArray {
        return sendReadBinary(0, READ_AHEAD_LENGTH)!!
    }

    private fun sendReadBinary(
        offset: Int,
        le: Int, // 8
        isExtendedLength: Boolean = false
    ): ByteArray? {
        var mutableLe = le
        var commandAPDU: AndroidNFCISO7816APDU? = null
        if (mutableLe == 0) return null
        // In the case of long read 2/3 less bytes of the actual data will be returned,
        // because a tag and length will be sent along, here we need to account for this
        if (isExtendedLength) {
            if (mutableLe < 128) {
                mutableLe += 2
            } else if (mutableLe < 256) {
                mutableLe += 3
            }
            if (mutableLe > 256) {
                mutableLe = 256
            }
        }

        val offsetHi = (offset and 0xFF00 shr 8).toByte()
        val offsetLo = (offset and 0xFF).toByte()

        commandAPDU = if (isExtendedLength) {
            val data = byteArrayOf(0x54, 0x02, offsetHi, offsetLo)
            AndroidNFCISO7816APDU(
                MISO7816.CLA_ISO7816.toInt(),
                MISO7816.INS_READ_BINARY2.toInt(),
                0,
                0,
                data,
                mutableLe
            )
        } else {
            AndroidNFCISO7816APDU(
                MISO7816.CLA_ISO7816.toInt(),
                MISO7816.INS_READ_BINARY.toInt(),
                offsetHi.toInt(),
                offsetLo.toInt(),
                mutableLe
            )
        }

        val response = sendWithSecureMessaging(commandAPDU)
        return response.copyOfRange(0, response.size - 2)
    }

    private fun sendSelectFile(fid: Short): ByteArray {
        val fiddle = byteArrayOf(
            (fid.toInt() shr 8 and 0xFF).toByte(),
            (fid.toInt() and 0xFF).toByte()
        )

        val command = AndroidNFCISO7816APDU(
            0x00.toByte().toInt(),
            0xA4.toByte().toInt(),
            0x02.toByte().toInt(),
            0x0c.toByte().toInt(),
            fiddle,
            0
        )

        val response = sendWithSecureMessaging(command)
        return response
    }

    private fun sendWithSecureMessaging(apdu: AndroidNFCISO7816APDU): ByteArray {
        val message = secureMessaging!!.protect(apdu)
        val response = isoDep?.transceive(message.toByteArray())
        return secureMessaging!!.unprotect(response ?: byteArrayOf())
    }

    fun sendMSEKAT(keyData: ByteArray, idData: ByteArray?): ResponseAPDU {
        val data = ByteArray(keyData.size + (idData?.size ?: 0))
        System.arraycopy(keyData, 0, data, 0, keyData.size)

        if (idData != null) System.arraycopy(idData, 0, data, keyData.size, idData.size)

        val commandAPDU = AndroidNFCISO7816APDU(
            MISO7816.CLA_ISO7816.toInt(),
            MISO7816.INS_MSE.toInt(),
            0x41,
            0xA6,
            data
        )

        val response = sendWithSecureMessaging(commandAPDU)
        return ResponseAPDU(response)
    }

    companion object {
        private const val READ_AHEAD_LENGTH = 8
        private const val DEFAULT_MAX_BLOCK_SIZE = 224
    }
}