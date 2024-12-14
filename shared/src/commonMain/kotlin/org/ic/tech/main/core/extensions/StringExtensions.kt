package org.ic.tech.main.core.extensions

fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}