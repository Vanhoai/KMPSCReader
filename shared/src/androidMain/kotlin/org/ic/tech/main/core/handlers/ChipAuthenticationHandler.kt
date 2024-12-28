package org.ic.tech.main.core.handlers

import org.ic.tech.main.AndroidTagReader
import org.ic.tech.main.core.helpers.APDUValidator
import org.ic.tech.main.core.helpers.PassportLib
import org.ic.tech.main.core.helpers.SecureMessaging
import org.ic.tech.main.core.algorithms.SecureMessagingSessionKeyGenerator
import org.ic.tech.main.core.algorithms.SecureMessagingSupportedAlgorithms
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import javax.crypto.interfaces.DHPublicKey
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.KeyAgreement

class ChipAuthenticationHandler(private val secureMessagingSessionKeyGenerator: SecureMessagingSessionKeyGenerator) {
    fun doChipAuthentication(
        tagReader: AndroidTagReader,
        key: BigInteger,
        publicKey: PublicKey
    ): Boolean {
        val agreementAlg = inferKeyAgreementAlgorithm(publicKey)
        val keyPairGenerator = KeyPairGenerator.getInstance(agreementAlg)
        val params: AlgorithmParameterSpec = when (agreementAlg) {
            "DH" -> {
                val dhPublicKey = publicKey as DHPublicKey
                dhPublicKey.params
            }

            "ECDH" -> {
                val ecPublicKey = publicKey as ECPublicKey
                ecPublicKey.params
            }

            else -> {
                throw IllegalStateException("Unsupported algorithm \"$agreementAlg\"")
            }
        }

        keyPairGenerator.initialize(params)
        val keyPair = keyPairGenerator.generateKeyPair()

        val agreement = KeyAgreement.getInstance(agreementAlg)
        agreement.init(keyPair.private)
        agreement.doPhase(publicKey, true)

        val secret = agreement.generateSecret()

        var keyData: ByteArray? = null
        var idData: ByteArray? = null
        var keyHash: ByteArray? = ByteArray(0)

        if ("DH" == agreementAlg) {
            val dhPublicKey = keyPair.public as DHPublicKey
            keyData = dhPublicKey.y.toByteArray()
            val md: MessageDigest = MessageDigest.getInstance("SHA1")
            keyHash = md.digest(keyData!!)
        } else {
            val ecPublicKey = keyPair.public as org.spongycastle.jce.interfaces.ECPublicKey
            keyData = ecPublicKey.q.encoded
            val t = i2os(ecPublicKey.q.x.toBigInteger())
            keyHash = alignKeyDataToSize(t, ecPublicKey.parameters.curve.fieldSize / 8)
        }

        keyData = PassportLib.wrapDO(0x91.toByte(), keyData)

        if (key >= BigInteger.ZERO) {
            val keyIdBytes = key.toByteArray()
            idData = PassportLib.wrapDO(0x84.toByte(), keyIdBytes)
        }

        val resSendMSEKAT = tagReader.sendMSEKAT(keyData, idData)

        val ksEnc = secureMessagingSessionKeyGenerator.deriveKey(secret, counter = 1)
        val ksMac = secureMessagingSessionKeyGenerator.deriveKey(secret, counter = 2)
        val ssc = 0L

        val secureMessaging = SecureMessaging(
            ksEnc,
            ksMac,
            ssc,
            SecureMessagingSupportedAlgorithms.DES
        )
        tagReader.updateSecureMessaging(secureMessaging)
        return APDUValidator.isSuccess(resSendMSEKAT)
    }

    private fun alignKeyDataToSize(keyData: ByteArray, size: Int): ByteArray {
        var mutableSize = size
        val result = ByteArray(mutableSize)
        if (keyData.size < mutableSize) {
            mutableSize = keyData.size
        }
        System.arraycopy(
            keyData,
            keyData.size - mutableSize,
            result,
            result.size - mutableSize,
            mutableSize
        )
        return result
    }

    private fun i2os(value: BigInteger): ByteArray {
        var sizeInNibbles = value.toString(16).length
        if (sizeInNibbles % 2 != 0) {
            sizeInNibbles++
        }
        val length = sizeInNibbles / 2
        return i2os(value, length)
    }

    private fun i2os(value: BigInteger, length: Int): ByteArray {
        var mutableValue = value
        val base = BigInteger.valueOf(256)
        val result = ByteArray(length)
        for (i in 0 until length) {
            val remainder = mutableValue.mod(base)
            mutableValue = mutableValue.divide(base)
            result[length - 1 - i] = remainder.toInt().toByte()
        }
        return result
    }

    private fun inferKeyAgreementAlgorithm(publicKey: PublicKey): String {
        return when (publicKey) {
            is ECPublicKey -> "ECDH"
            is DHPublicKey -> "DH"
            else -> {
                throw IllegalArgumentException("Unsupported public key: $publicKey")
            }
        }
    }
}