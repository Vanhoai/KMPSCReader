package org.ic.tech.main.core.extensions

fun <T> T?.isNull(): Boolean = this == null
fun <T> T?.isNotNull(): Boolean = this != null