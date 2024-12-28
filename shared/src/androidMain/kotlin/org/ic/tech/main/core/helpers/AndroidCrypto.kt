package org.ic.tech.main.core.helpers

import android.annotation.SuppressLint
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class AndroidCrypto {
    private fun initializeMac(kMac: SecretKey): Mac {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(kMac)
        return mac
    }

    fun initializeCipher(kEnc: SecretKey, mode: Int): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            mode,
            kEnc,
            ZERO_IV_PARAM_SPEC,
        )

        return cipher
    }

    fun cipherEncrypt(kEnc: SecretKey, message: ByteArray): ByteArray {
        val cipher = initializeCipher(kEnc, Cipher.ENCRYPT_MODE)
        return cipher.doFinal(message)
    }

    fun cipherDecrypt(
        kEnc: SecretKey,
        message: ByteArray,
        offset: Int? = null,
        length: Int? = null
    ): ByteArray {
        val cipher = initializeCipher(kEnc, Cipher.DECRYPT_MODE)
        if (offset == null || length == null) return cipher.doFinal(message)
        return cipher.doFinal(message, offset, length)
    }

    fun macSign(kMac: SecretKey, message: ByteArray): ByteArray {
        val mac = initializeMac(kMac)
        return mac.doFinal(message)
    }

    companion object {
        @SuppressLint("InlinedApi")
        private const val ALGORITHMS = KeyProperties.KEY_ALGORITHM_3DES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHMS/$BLOCK_MODE/$PADDING"
        private const val MAC_ALGORITHM = "ISO9797Alg3Mac"
        private var ZERO_IV_PARAM_SPEC = IvParameterSpec(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            )
        )
    }
}