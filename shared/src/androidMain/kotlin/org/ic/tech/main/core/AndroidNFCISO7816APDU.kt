package org.ic.tech.main.core;

import org.ic.tech.main.core.extensions.isNull

// int cla, int ins, int p1, int p2, byte[] data,
// int dataOffset, int dataLength, int ne

class AndroidNFCISO7816APDU(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray?,
    private var offset: Int,
    private val dataLength: Int,
    val ne: Int,
) {
    var nc: Int? = null
    private var apdu: ByteArray? = null
    private var dataOffset: Int? = null

    // int cla, int ins, int p1, int p2, int ne
    // this(cla, ins, p1, p2, null, 0, 0, ne);
    constructor(cla: Int, ins: Int, p1: Int, p2: Int, ne: Int) : this(
        cla,
        ins,
        p1,
        p2,
        null,
        0,
        0,
        ne
    )

    // int cla, int ins, int p1, int p2, int ne
    // this(cla, ins, p1, p2, null, 0, 0, ne);

    // int cla, int ins, int p1, int p2, byte[] data
    // this(cla, ins, p1, p2, data, 0, arrayLength(data), 0);
    constructor(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray) : this(
        cla,
        ins,
        p1,
        p2,
        data,
        0,
        arrayLength(data),
        0
    )

    // int cla, int ins, int p1, int p2, byte[] data, int ne
    // this(cla, ins, p1, p2, data, 0, arrayLength(data), ne);
    constructor(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray, ne: Int) : this(
        cla,
        ins,
        p1,
        p2,
        data,
        0,
        arrayLength(data),
        ne
    )

    init {
        checkArrayBounds(data, offset, dataLength)
        if (dataLength > 65535) throw IllegalArgumentException("dataLength is too large")
        if (ne < 0) throw IllegalArgumentException("ne must not be negative")
        if (ne > 65536) throw IllegalArgumentException("ne is too large")
        this.nc = dataLength

        if (dataLength == 0) {
            if (ne == 0) {
                // case 1
                this.apdu = ByteArray(4)
                setHeader(cla, ins, p1, p2);
            } else {
                // case 2s or 2e
                if (ne <= 256) {
                    // case 2s
                    // 256 is encoded as 0x00
                    val len = if ((ne != 256)) ne.toByte() else 0
                    this.apdu = ByteArray(5)
                    setHeader(cla, ins, p1, p2)
                    apdu!![4] = len
                } else {
                    // case 2e
                    val l1: Byte
                    val l2: Byte

                    // 65536 is encoded as 0x00 0x00
                    if (ne == 65536) {
                        l1 = 0
                        l2 = 0
                    } else {
                        l1 = (ne shr 8).toByte()
                        l2 = ne.toByte()
                    }
                    this.apdu = ByteArray(7)
                    setHeader(cla, ins, p1, p2)
                    apdu!![5] = l1
                    apdu!![6] = l2
                }
            }
        } else {
            if (ne == 0) {
                // case 3s or 3e
                if (dataLength <= 255) {
                    // case 3s -> short form data length
                    // -> 4 byte header + 1 byte data length + data
                    apdu = ByteArray(4 + 1 + dataLength)
                    setHeader(cla, ins, p1, p2)
                    apdu!![4] = dataLength.toByte()
                    dataOffset = 5
                    System.arraycopy(data!!, offset, apdu!!, 5, dataLength)
                } else {
                    // case 3e -> extended form data length
                    // -> 4 byte header + 3 byte data length + data
                    apdu = ByteArray(4 + 3 + dataLength)
                    setHeader(cla, ins, p1, p2)
                    apdu!![4] = 0
                    apdu!![5] = (dataLength shr 8).toByte()
                    apdu!![6] = dataLength.toByte()
                    dataOffset = 7
                    System.arraycopy(data!!, offset, apdu!!, 7, dataLength)
                }
            } else {
                // case 4s or 4e
                if ((dataLength <= 255) && (ne <= 256)) {
                    // case 4s -> short form data length
                    // -> 4 byte header + 2 byte data length + data
                    apdu = ByteArray(4 + 2 + dataLength)
                    setHeader(cla, ins, p1, p2)
                    apdu!![4] = dataLength.toByte()
                    dataOffset = 5
                    System.arraycopy(data!!, offset, apdu!!, 5, dataLength)
                    apdu!![apdu!!.size - 1] = if ((ne != 256)) ne.toByte() else 0
                } else {
                    // case 4e -> extended form data length
                    // -> 4 byte header + 5 byte data length + data
                    apdu = ByteArray(4 + 5 + dataLength)
                    setHeader(cla, ins, p1, p2)
                    apdu!![4] = 0
                    apdu!![5] = (dataLength shr 8).toByte()
                    apdu!![6] = dataLength.toByte()
                    dataOffset = 7
                    System.arraycopy(data!!, offset, apdu!!, 7, dataLength)
                    if (ne != 65536) {
                        val leOfs: Int = apdu!!.size - 2
                        apdu!![leOfs] = (ne shr 8).toByte()
                        apdu!![leOfs + 1] = ne.toByte()
                    } // else le == 65536: no need to fill in, encoded as 0
                }
            }
        }
    }

    fun toByteArray(): ByteArray = apdu!!

    private fun checkArrayBounds(bytes: ByteArray?, ofs: Int, len: Int) {
        if ((ofs < 0) || (len < 0)) {
            throw IllegalArgumentException("Offset and length must not be negative")
        }

        if (bytes.isNull()) {
            if ((ofs != 0) && (len != 0)) {
                throw IllegalArgumentException("offset and length must be 0 if array is null")
            }
        } else {
            if (ofs > bytes!!.size - len) {
                throw IllegalArgumentException("Offset plus length exceed array size")
            }
        }
    }

    private fun setHeader(cla: Int, ins: Int, p1: Int, p2: Int) {
        apdu!![0] = (cla and 0xFF).toByte()
        apdu!![1] = (ins and 0xFF).toByte()
        apdu!![2] = (p1 and 0xFF).toByte()
        apdu!![3] = (p2 and 0xFF).toByte()
    }

    companion object {
        private const val MAX_APDU_SIZE = 65544
        private fun arrayLength(b: ByteArray?): Int {
            return if (b.isNull()) 0 else b!!.size
        }
    }
}