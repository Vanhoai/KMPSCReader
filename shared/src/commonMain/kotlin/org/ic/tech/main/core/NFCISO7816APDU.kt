package org.ic.tech.main.core

class NFCISO7816APDU(
    val instructionClass: UByte,
    val instructionCode: UByte,
    val p1Parameter: UByte,
    val p2Parameter: UByte,
    val data: ByteArray? = null,
    val expectedResponseLength: Int = -1
) {
    fun toByteArray(): ByteArray {
        val output = mutableListOf<Byte>()

        // Add instruction bytes
        output.add(instructionClass.toByte())
        output.add(instructionCode.toByte())
        output.add(p1Parameter.toByte())
        output.add(p2Parameter.toByte())

        // Handle data field
        data?.let {
            // Add data length
            output.add(it.size.toByte())
            // Add actual data
            output.addAll(it.toList())
        }

        // Handle expected response length
        when {
            expectedResponseLength == -1 -> {
                // No response expected
                output.add(0x00)
            }

            expectedResponseLength < 256 -> {
                // Short form Le
                output.add(expectedResponseLength.toByte())
            }

            else -> {
                // Extended form Le
                output.add(0x00)
                output.add((expectedResponseLength shr 8).toByte())
                output.add(expectedResponseLength.toByte())
            }
        }

        return output.toByteArray()
    }

    companion object {
        fun fromByteArray(byteArray: ByteArray): NFCISO7816APDU {
            require(byteArray.size >= 4) { "APDU must be at least 4 bytes long" }

            val instructionClass = byteArray[0].toUByte()
            val instructionCode = byteArray[1].toUByte()
            val p1Parameter = byteArray[2].toUByte()
            val p2Parameter = byteArray[3].toUByte()

            // Parse data and expected response length
            var data: ByteArray? = null
            var expectedResponseLength = -1

            if (byteArray.size > 4) {
                val dataLength = byteArray[4].toInt() and 0xFF
                if (dataLength > 0 && byteArray.size >= 5 + dataLength) {
                    data = byteArray.sliceArray(5 until 5 + dataLength)

                    // Parse expected response length
                    if (5 + dataLength < byteArray.size) {
                        expectedResponseLength = when (byteArray.size - (5 + dataLength)) {
                            1 -> byteArray[5 + dataLength].toInt() and 0xFF
                            2 -> {
                                ((byteArray[5 + dataLength].toInt() and 0xFF) shl 8) or
                                        (byteArray[6 + dataLength].toInt() and 0xFF)
                            }

                            else -> -1
                        }
                    }
                }
            }

            return NFCISO7816APDU(
                instructionClass = instructionClass,
                instructionCode = instructionCode,
                p1Parameter = p1Parameter,
                p2Parameter = p2Parameter,
                data = data,
                expectedResponseLength = expectedResponseLength
            )
        }

        fun selectApplication(aid: ByteArray): NFCISO7816APDU {
            return NFCISO7816APDU(
                instructionClass = 0x00u,
                instructionCode = 0xA4u,  // SELECT command
                p1Parameter = 0x04u,      // Select by AID
                p2Parameter = 0x00u,      // First or only occurrence
                data = aid,
                expectedResponseLength = 256  // Typical response length
            )
        }
    }

    // Debug and utility methods
    override fun toString(): String {
        return "APDU(CLA=${instructionClass.toString(16)}, " +
                "INS=${instructionCode.toString(16)}, " +
                "P1=${p1Parameter.toString(16)}, " +
                "P2=${p2Parameter.toString(16)}, " +
                "Data=${data?.size ?: 0} bytes, " +
                "Le=$expectedResponseLength)"
    }
}