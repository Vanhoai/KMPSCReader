package org.ic.tech.main.core

import org.ic.tech.main.models.ReadIdCardResponse
import org.ic.tech.main.models.ReadIdCardStatus

object ADPUValidator {

    /**
     * Status Error ADPU Map
     *
     * Inside a command response will contain a status byte (SW1) and a status word (SW2).
     * The status byte (SW1) is the first byte of the response and the status word (SW2) is the second byte.
     * The status byte (SW1) and status word (SW2) are encoded in two bytes.
     * From the status byte (SW1) the most significant byte (MSB) is used to indicate
     * the class of the command and the status word (SW2) is used to provide more detailed
     * information about the result of the command.
     */
    private val errors: Map<UByte, Map<UByte, String>> = mapOf(
        0x62.toUByte() to mapOf(
            0x00.toUByte() to "No information given",
            0x81.toUByte() to "Part of returned data may be corrupted",
            0x82.toUByte() to "End of file/record reached before reading Le bytes",
            0x83.toUByte() to "Selected file invalidated",
            0x84.toUByte() to "FCI not formatted according to ISO7816-4 section 5.1.5"
        ),
        0x63.toUByte() to mapOf(
            0x81.toUByte() to "File filled up by the last write",
            0x82.toUByte() to "Card Key not supported",
            0x83.toUByte() to "Reader Key not supported",
            0x84.toUByte() to "Plain transmission not supported",
            0x85.toUByte() to "Secured Transmission not supported",
            0x86.toUByte() to "Volatile memory not available",
            0x87.toUByte() to "Non Volatile memory not available",
            0x88.toUByte() to "Key number not valid",
            0x89.toUByte() to "Key length is not correct",
            0x0C.toUByte() to "Counter provided by X (valued from 0 to 15) (exact meaning depending on the command)"
        ),
        0x65.toUByte() to mapOf(
            0x00.toUByte() to "No information given",
            0x81.toUByte() to "Memory failure"
        ),
        0x67.toUByte() to mapOf(
            0x00.toUByte() to "Wrong length"
        ),
        0x68.toUByte() to mapOf(
            0x00.toUByte() to "No information given",
            0x81.toUByte() to "Logical channel not supported",
            0x82.toUByte() to "Secure messaging not supported",
            0x83.toUByte() to "Last command of the chain expected",
            0x84.toUByte() to "Command chaining not supported"
        ),
        0x69.toUByte() to mapOf(
            0x00.toUByte() to "No information given",
            0x81.toUByte() to "Command incompatible with file structure",
            0x82.toUByte() to "Security status not satisfied",
            0x83.toUByte() to "Authentication method blocked",
            0x84.toUByte() to "Referenced data invalidated",
            0x85.toUByte() to "Conditions of use not satisfied",
            0x86.toUByte() to "Command not allowed (no current EF)",
            0x87.toUByte() to "Expected SM data objects missing",
            0x88.toUByte() to "SM data objects incorrect"
        ),
        0x6A.toUByte() to mapOf(
            0x00.toUByte() to "No information given",
            0x80.toUByte() to "Incorrect parameters in the data field",
            0x81.toUByte() to "Function not supported",
            0x82.toUByte() to "File not found",
            0x83.toUByte() to "Record not found",
            0x84.toUByte() to "Not enough memory space in the file",
            0x85.toUByte() to "Lc inconsistent with TLV structure",
            0x86.toUByte() to "Incorrect parameters P1-P2",
            0x87.toUByte() to "Lc inconsistent with P1-P2",
            0x88.toUByte() to "Referenced data not found"
        ),
        0x6B.toUByte() to mapOf(
            0x00.toUByte() to "Wrong parameter(s) P1-P2"
        ),
        0x6D.toUByte() to mapOf(
            0x00.toUByte() to "Instruction code not supported or invalid"
        ),
        0x6E.toUByte() to mapOf(
            0x00.toUByte() to "Class not supported"
        ),
        0x6F.toUByte() to mapOf(
            0x00.toUByte() to "No precise diagnosis"
        ),
        0x90.toUByte() to mapOf(
            0x00.toUByte() to "Success" // No further qualification
        )
    )

    /**
     * Decode Status
     * @param sw1 Status byte
     * @param sw2 Status word
     * @return String
     *
     * Function decode status byte and status word
     * and return response if success or not.
     */
    private fun decodeStatus(sw1: UByte, sw2: UByte): String {
        // Special cases - where sw2 isn't an error but contains a value
        return when (sw1) {
            0x61.toUByte() -> "SW2 indicates the number of response bytes still available - ($sw2 bytes still available)"
            0x64.toUByte() -> "State of non-volatile memory unchanged (SW2=00, other values are RFU)"
            0x6C.toUByte() -> "Wrong length Le: SW2 indicates the exact length - (exact length :$sw2)"
            else -> {
                errors[sw1]?.get(sw2)
                    ?: "Unknown error - sw1: 0x${sw1.toString(16)}, sw2: 0x${sw2.toString(16)}"
            }
        }
    }

    fun decodeStatus(response: ByteArray): String {
        val sw1 = response[response.size - 2].toUByte()
        val sw2 = response[response.size - 1].toUByte()
        return decodeStatus(sw1, sw2)
    }

    private fun isSuccess(sw1: UByte, sw2: UByte): Boolean {
        return sw1 == 0x90.toUByte() && sw2 == 0x00.toUByte()
    }

    fun isSuccess(response: ResponseAPDU): Boolean {
        return isSuccess(response.sw1.toUByte(), response.sw2.toUByte())
    }

    fun isSuccess(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false

        val sw1 = bytes[bytes.size - 2].toUByte()
        val sw2 = bytes[bytes.size - 1].toUByte()
        return isSuccess(sw1, sw2)
    }

    fun checkIsSuccessAndDropSW(response: ByteArray): ByteArray {
        if (response.size < 2) return byteArrayOf()

        val sw1 = response[response.size - 2].toUByte()
        val sw2 = response[response.size - 1].toUByte()

        if (!isSuccess(sw1, sw2)) return byteArrayOf()

        return response.copyOfRange(0, response.size - 2)
    }

    fun parseResponse(response: ByteArray): ReadIdCardResponse {
        if (response.size < 2) return ReadIdCardResponse(
            status = ReadIdCardStatus.Failed,
            message = "Response from chip is empty",
            data = mapOf()
        )

        val sw1 = response[response.size - 2].toUByte()
        val sw2 = response[response.size - 1].toUByte()

        if (!isSuccess(sw1, sw2)) return ReadIdCardResponse(
            status = ReadIdCardStatus.Failed,
            message = decodeStatus(sw1, sw2),
            data = mapOf()
        )

        return ReadIdCardResponse(
            status = ReadIdCardStatus.Success,
            message = "Response from chip is success ✅",
            data = mapOf()
        )
    }
}