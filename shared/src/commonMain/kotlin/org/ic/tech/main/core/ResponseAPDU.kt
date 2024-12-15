package org.ic.tech.main.core

data class ResponseAPDU(
    val data: ByteArray,
    val sw1: UByte,
    val sw2: UByte
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

    companion object {
        fun fromByteArray(byteArray: ByteArray): ResponseAPDU {
            require(byteArray.size >= 2) { "APDU must be at least 2 bytes long" }

            val sw1 = byteArray[byteArray.size - 2].toUByte()
            val sw2 = byteArray[byteArray.size - 1].toUByte()

            return ResponseAPDU(
                data = byteArray.sliceArray(0 until byteArray.size - 2),
                sw1 = sw1,
                sw2 = sw2
            )
        }
    }
}