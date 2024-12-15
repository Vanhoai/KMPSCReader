package org.ic.tech.main.core

import java.io.ByteArrayOutputStream

data class ResponseAPDU(
    val data: ByteArray,
    val sw1: Byte,
    val sw2: Byte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ResponseAPDU

        if (!data.contentEquals(other.data)) return false
        if (sw1 != other.sw1) return false
        if (sw2 != other.sw2) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1.hashCode()
        result = 31 * result + sw2.hashCode()
        return result
    }

    fun toByteArray(): ByteArray {
        val ous = ByteArrayOutputStream()
        ous.write(data, 0, data.size)
        ous.write(sw1.toInt())
        ous.write(sw2.toInt())
        return ous.toByteArray()
    }

    companion object {
        fun fromByteArray(byteArray: ByteArray): ResponseAPDU {
            require(byteArray.size >= 2) { "APDU must be at least 2 bytes long" }

            val sw1 = byteArray[byteArray.size - 2]
            val sw2 = byteArray[byteArray.size - 1]

            return ResponseAPDU(
                data = byteArray.sliceArray(0 until byteArray.size - 2),
                sw1 = sw1,
                sw2 = sw2
            )
        }
    }
}