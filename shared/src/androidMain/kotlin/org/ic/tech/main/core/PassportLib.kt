package org.ic.tech.main.core

import net.sf.scuba.tlv.TLVInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKey

object PassportLib {
    fun padWithMRZ(data: ByteArray, blockSize: Int = 8): ByteArray {
        return padWithMRZ(data, 0, data.size, blockSize)
    }

    fun padWithMRZ(
        data: ByteArray,
        offset: Int = 0,
        length: Int,
        blockSize: Int = 8
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(data, offset, length)
        out.write(0x80.toByte().toInt())
        while (out.size() % blockSize != 0) {
            out.write(0x00.toByte().toInt())
        }
        return out.toByteArray()
    }

    @Throws(BadPaddingException::class)
    fun unpad(input: ByteArray): ByteArray {
        var i = input.size - 1
        while (i >= 0 && input[i].toInt() == 0x00) {
            i--
        }
        if (input[i].toInt() and 0xFF != 0x80) {
            throw BadPaddingException("Expected constant 0x80")
        }
        val out = ByteArray(i)
        System.arraycopy(input, 0, out, 0, i)
        return out
    }

    private fun convertToLengthDEFFormat(length: Int): ByteArray {
        val ous = ByteArrayOutputStream()
        if (length < 0x80) { // 0x80 = 128
            // Short form
            ous.write(length)
        } else {
            // Extended form
            val count = logarithm(length)
            ous.write(0x80 or count) // Mask with 0x80 show start of extended form
            for (i in 0 until count) {
                val pos = 8 * (count - i - 1)
                ous.write(length and (0xFF shl pos) shr pos)
            }
        }

        return ous.toByteArray()
    }

    private fun logarithm(value: Int, base: Int = 256): Int {
        var n = value
        var res = 0
        while (n > 0) {
            n /= base
            res++
        }

        return res
    }

    fun buildD097(apdu: NFCISO7816APDU): ByteArray {
        val do97 = ByteArray(0)
        val isMSE = apdu.ins == 0x22
        if (apdu.ne > 0 && (if (isMSE) apdu.ne < 256 else true)) {
            // Build DO97 command if expected response length is greater than zero
            // and the INS is MSE
            val le = apdu.ne
            val ous = ByteArrayOutputStream()
            ous.write(0x97.toByte().toInt())
            ous.write(0x01.toByte().toInt())
            ous.write(le.toByte().toInt())
            return ous.toByteArray()
        }

        return do97
    }

    fun build8E(mac: ByteArray): ByteArray {
        val ous = ByteArrayOutputStream()
        ous.write(0x8E.toByte().toInt())
        ous.write(mac.size)
        ous.write(mac, 0, mac.size)
        val response = ous.toByteArray()
        return response
    }

    fun buildPrefix(
        mask: ByteArray,
        do8587: ByteArray,
        do97: ByteArray,
        ssc: Long,
    ): ByteArray {
        val m = mask + do8587 + do97
        val ous = ByteArrayOutputStream()
        val dos = DataOutputStream(ous)
        dos.writeLong(ssc)
        dos.write(m)
        dos.flush()
        val n = PassportLib.padWithMRZ(ous.toByteArray())
        return n
    }

    fun maskAndPad(apdu: NFCISO7816APDU, padLength: Int): ByteArray {
        val bytes = byteArrayOf(
            (apdu.cla or 0x0C.toByte().toInt()).toByte(),
            apdu.ins.toByte(),
            apdu.p1.toByte(),
            apdu.p2.toByte()
        )

        return padWithMRZ(bytes, blockSize = padLength)
    }

    fun buildDO8587(
        apdu: NFCISO7816APDU,
        padLength: Int,
        ksEnc: SecretKey,
        sm: AndroidSecureMessaging,
    ): ByteArray {
        val do8587 = ByteArray(0)

        if (apdu.nc != null && apdu.nc!! > 0) {
            // Build DO8587 command if command data length is greater than zero
            val has85 = apdu.ins == 0xB1
            val data = padWithMRZ(apdu.data!!, blockSize = padLength)
            val encryptedData = sm.cipherEncrypt(ksEnc, data)

            val ous = ByteArrayOutputStream()
            ous.write((if (has85) 0x85.toByte() else 0x87.toByte()).toInt())
            ous.write(convertToLengthDEFFormat(encryptedData.size + if (has85) 0 else 1))
            if (!has85) ous.write(0x01)
            ous.write(encryptedData, 0, encryptedData.size)
            return ous.toByteArray()
        }

        return do8587
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun readDO8E(inputStream: DataInputStream): ByteArray {
        val length = inputStream.readUnsignedByte()
        check(length == 8) { "DO'8E wrong length" }
        val cc1 = ByteArray(8)
        inputStream.readFully(cc1)
        return cc1
    }

    @Throws(IOException::class)
    fun readDO99(inputStream: DataInputStream): Short {
        val length = inputStream.readUnsignedByte()
        check(length == 2) { "DO'99 wrong length" }
        val sw1 = inputStream.readByte()
        val sw2 = inputStream.readByte()
        return (sw1.toInt() and 0x000000FF shl 8 or (sw2.toInt() and 0x000000FF)).toShort()
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun readDO87(
        cipher: Cipher,
        inputStream: DataInputStream,
        do85: Boolean
    ): ByteArray {
        /* Read length... */
        var length = 0
        var buf = inputStream.readUnsignedByte()
        if (buf and 0x00000080 != 0x00000080) {
            /* Short form */
            length = buf
            if (!do85) {
                buf = inputStream.readUnsignedByte() /* should be 0x01... */
                check(buf == 0x01) {
                    "DO'87 expected 0x01 marker, found " + Integer.toHexString(
                        buf and 0xFF
                    )
                }
            }
        } else {
            /* Long form */
            val lengthBytesCount = buf and 0x0000007F
            for (i in 0 until lengthBytesCount) {
                length = length shl 8 or inputStream.readUnsignedByte()
            }
            if (!do85) {
                buf = inputStream.readUnsignedByte() /* should be 0x01... */
                check(buf == 0x01) { "DO'87 expected 0x01 marker" }
            }
        }
        if (!do85) {
            length-- /* takes care of the extra 0x01 marker... */
        }
        /* Read, decrypt, unpad the data... */
        val ciphertext = ByteArray(length)
        inputStream.readFully(ciphertext)
        val paddedData: ByteArray = cipher.doFinal(ciphertext)
        return unpad(paddedData)
    }

    fun getFileLength(fileInfo: ByteArray): Int {
        val baInputStream = ByteArrayInputStream(fileInfo)
        val tlvInputStream = TLVInputStream(baInputStream)
        val tag = tlvInputStream.readTag()
        return if (tag == 0x42.toByte().toInt()) {
            36
        } else {
            val vLength = tlvInputStream.readLength()
            val tlLength: Int =
                fileInfo.size - baInputStream.available()
            tlLength + vLength
        }
    }

    fun wrapDO(tag: Byte, data: ByteArray?): ByteArray {
        requireNotNull(data) { "Data to wrap is null" }
        val result = ByteArray(data.size + 2)
        result[0] = tag
        result[1] = data.size.toByte()
        System.arraycopy(data, 0, result, 2, data.size)
        return result
    }
}