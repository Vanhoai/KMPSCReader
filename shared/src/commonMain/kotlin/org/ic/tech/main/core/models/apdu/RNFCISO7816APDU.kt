package org.ic.tech.main.core.models.apdu

data class RNFCISO7816APDU(
    val data: ByteArray,
    val sw1: Byte,
    val sw2: Byte
) {
    constructor(apdu: ByteArray) : this(
        data = apdu.sliceArray(0 until apdu.size - 2),
        sw1 = apdu[apdu.size - 2],
        sw2 = apdu[apdu.size - 1]
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RNFCISO7816APDU

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
        fun fromByteArray(byteArray: ByteArray): RNFCISO7816APDU {
            require(byteArray.size >= 2) { "APDU must be at least 2 bytes long" }

            val sw1 = byteArray[byteArray.size - 2]
            val sw2 = byteArray[byteArray.size - 1]

            return RNFCISO7816APDU(
                data = byteArray.sliceArray(0 until byteArray.size - 2),
                sw1 = sw1,
                sw2 = sw2
            )
        }
    }
}