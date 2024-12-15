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
        return ADPUValidator.checkIsSuccessAndDropSW(response ?: byteArrayOf())
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
    suspend fun selectPassportApplication(): ReadIdCardResponse {
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
        return ADPUValidator.parseResponse(response ?: byteArrayOf())
    }

    suspend fun send(cmd: AndroidNFCISO7816APDU): ByteArray {
        val response = isoDep?.transceive(cmd.toByteArray())
        return response ?: byteArrayOf()
    }

    suspend fun sendSelectFileAndReadDataGroup(dg: DataGroup) {
        val response = sendSelectFile(DataGroup.DG14.value)
    }

    private suspend fun sendSelectFile(fid: Short) {
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


//        val response = sendWithSecureMessaging(command)
//        println("Response send select file: $response")

        val message = secureMessaging!!.protect(command)

        // 00A4020C02010E00
//        Before wrap command: 00A4020C02010E
//        After wrap command: 0CA4020C1587090107244F3C0AADEF838E089DCF86AAE9F9A91B00
//        After transceive: 990290008E080809777498B4DB729000
//        Before Unwrap: 990290008E080809777498B4DB729000
//        ssc: 6278077974196692664

//        Send with message 1: 9701008e08015b8f431e51346200
//        Send with message 2: 0ca4020c15870901295c42b90bf68ca38e08511b5bfd9671d08100


        val response = isoDep?.transceive(message.toByteArray())

        println("Response send select file: ${response?.toHexString()}")
    }

//    private suspend fun sendWithSecureMessaging(apdu: NFCISO7816APDU): ResponseAPDU {
//        val message = secureMessaging!!.protect(apdu)
//
//        return ResponseAPDU(
//            data = byteArrayOf(),
//            sw1 = 0x90u,
//            sw2 = 0x00u
//        )
//    }
}