package org.ic.tech.main.core.extensions

fun ByteArray.toHexString(): String {
    return joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
}