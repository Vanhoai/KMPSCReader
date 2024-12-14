package org.ic.tech.main.core

import android.annotation.SuppressLint
import java.io.ByteArrayOutputStream

object ParseMRZLib {
    fun padWithMRZ(data: ByteArray): ByteArray {
        return padWithMRZ(data, 0, data.size)
    }

    private fun padWithMRZ(data: ByteArray, offset: Int = 0, length: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(data, offset, length)
        out.write(0x80.toByte().toInt())
        while (out.size() % 8 != 0) {
            out.write(0x00.toByte().toInt())
        }
        return out.toByteArray()
    }
}