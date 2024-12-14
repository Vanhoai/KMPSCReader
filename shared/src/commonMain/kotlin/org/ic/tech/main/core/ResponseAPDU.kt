package org.ic.tech.main.core

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
        result = 31 * result + sw1
        result = 31 * result + sw2
        return result
    }

}