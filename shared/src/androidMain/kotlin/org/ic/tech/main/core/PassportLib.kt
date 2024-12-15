package org.ic.tech.main.core

import org.ic.tech.main.core.extensions.isNotNull
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.crypto.SecretKey

object PassportLib {
    fun padWithMRZ(data: ByteArray, blockSize: Int = 8): ByteArray {
        return padWithMRZ(data, 0, data.size, blockSize)
    }

    private fun padWithMRZ(
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

    fun convertToLengthDEFFormat(length: Int): ByteArray {
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

    fun buildD097(apdu: AndroidNFCISO7816APDU): ByteArray {
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

    fun maskAndPad(apdu: AndroidNFCISO7816APDU, padLength: Int): ByteArray {
        val bytes = byteArrayOf(
            (apdu.cla or 0x0C.toByte().toInt()).toByte(),
            apdu.ins.toByte(),
            apdu.p1.toByte(),
            apdu.p2.toByte()
        )

        return padWithMRZ(bytes, blockSize = padLength)
    }

    fun buildDO8587(
        apdu: AndroidNFCISO7816APDU,
        padLength: Int,
        ksEnc: SecretKey,
        sm: AndroidSecureMessaging,
    ): ByteArray {
        val do8587 = ByteArray(0)

        if (apdu.nc.isNotNull() && apdu.nc!! > 0) {
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
}